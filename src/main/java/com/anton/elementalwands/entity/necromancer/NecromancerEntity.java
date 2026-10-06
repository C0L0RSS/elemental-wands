package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.entity.WandBoss;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Summon-only caster boss. {@link NecromancerCombat} owns every decision; this class owns state. */
public class NecromancerEntity extends PathAwareEntity implements GeoEntity, WandBoss {
    public static final String CONTROLLER = "necromancer";
    private static final String PASSIVE_TAG = "ew_necromancer_passive";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.hollow_necromancer.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.hollow_necromancer.walk");
    private static final RawAnimation COLOSSUS_IDLE = RawAnimation.begin().thenLoop("animation.hollow_necromancer.colossus_idle");
    private static final RawAnimation COLOSSUS_WALK = RawAnimation.begin().thenLoop("animation.hollow_necromancer.colossus_walk");
    private static final TrackedData<Integer> DRAIN_TARGET = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> DRAIN_START = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Boolean> COLOSSUS = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> TRANSFORM_START = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> GRABBED = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SPLIT = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> BURIED = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> INTRO_START = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final EntityDimensions COLOSSUS_SIZE = EntityDimensions.fixed(NecromancerRules.COLOSSUS_WIDTH, NecromancerRules.COLOSSUS_HEIGHT);
    private boolean phasePending, soulFreed, soulHitting;
    /** Saved mid-transformation: its storm may have left the crypt's braziers out. */
    private boolean relightBraziers;
    private NecromancerRules.Stage stage = NecromancerRules.Stage.DUEL_A;
    private record HurtWindow(long until, float damage) {}
    private final Map<UUID, HurtWindow> hurtWindows = new HashMap<>();
    private static final UUID ENVIRONMENT_DAMAGE = new UUID(0, 0);
    private final NecromancerCombat combat = new NecromancerCombat(this);
    private NecromancerIntro intro;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NecromancerEntity(EntityType<? extends NecromancerEntity> type, World world) {
        super(type, world);
        setPersistent();
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, NecromancerRules.health(1))
                .add(EntityAttributes.MOVEMENT_SPEED, .26)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(EntityAttributes.FOLLOW_RANGE, NecromancerRules.ENCOUNTER_RANGE);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(DRAIN_TARGET, -1);
        builder.add(DRAIN_START, -1L);
        builder.add(COLOSSUS, false);
        builder.add(TRANSFORM_START, -1L);
        builder.add(GRABBED, -1);
        builder.add(SPLIT, false);
        builder.add(BURIED, false);
        builder.add(INTRO_START, -1L);
    }

    /** The giant skeleton: set partway through the transformation, when its body has grown out. */
    public boolean isColossus() { return dataTracker.get(COLOSSUS); }
    void setColossus(boolean colossus) { dataTracker.set(COLOSSUS, colossus); }
    public boolean isTransforming() { return dataTracker.get(TRANSFORM_START) >= 0; }
    public float getTransformTime(float partialTick) {
        long start = dataTracker.get(TRANSFORM_START);
        return start < 0 ? -1 : getEntityWorld().getTime() - start + partialTick;
    }
    void startTransform() { phasePending = false; stage = NecromancerRules.Stage.DONE; dataTracker.set(TRANSFORM_START, getEntityWorld().getTime()); }
    void finishTransform() { dataTracker.set(TRANSFORM_START, -1L); setColossus(true); }
    boolean phasePending() { return phasePending; }
    void schedulePhaseTwo() { if (!isColossus() && !isTransforming()) phasePending = true; }
    /** Where the robed fight stands: a duel, a siege, or done (transformation pending or colossus). */
    public NecromancerRules.Stage stage() { return stage; }
    void setStage(NecromancerRules.Stage stage) { this.stage = stage; }
    /** The encounter's home: bound minions stay within reach of it, not of a perched caster. */
    net.minecraft.util.math.Vec3d anchor() { return combat.anchor(); }
    /** Entity id of the player held by the colossus hand; -1 when empty. */
    public int getGrabbed() { return dataTracker.get(GRABBED); }
    void setGrabbed(int id) { dataTracker.set(GRABBED, id); }
    /** The soul is out of the ribcage: the client hides the colossus's soul core. */
    public boolean isSplit() { return dataTracker.get(SPLIT); }
    void setSplit(boolean split) { dataTracker.set(SPLIT, split); }
    /** Tunnelling during a Grave Dive: nothing to draw, nothing to hit. */
    public boolean isBuried() { return dataTracker.get(BURIED); }
    void setBuried(boolean buried) { dataTracker.set(BURIED, buried); }
    /** The soul has torn free; the split happens once. */
    void setSoulFreed(boolean freed) { soulFreed = freed; }

    @Override
    protected EntityDimensions getBaseDimensions(EntityPose pose) {
        return isColossus() ? COLOSSUS_SIZE : super.getBaseDimensions(pose);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (COLOSSUS.equals(data)) calculateDimensions();
    }

    /** Entity id of the drained player, so a client tether can follow it; -1 when idle. */
    public int getDrainTarget() { return dataTracker.get(DRAIN_TARGET); }
    public long getDrainStart() { return dataTracker.get(DRAIN_START); }
    public float getDrainTime(float partialTick) {
        long start = getDrainStart();
        return start < 0 ? -1 : getEntityWorld().getTime() - start + partialTick;
    }
    void setDrainTarget(int id) {
        dataTracker.set(DRAIN_START, id < 0 ? -1L : getEntityWorld().getTime());
        dataTracker.set(DRAIN_TARGET, id);
    }

    @Override public boolean isBossAggressive() { return !getCommandTags().contains(PASSIVE_TAG); }

    public void startFight() { cancelIntro(); removeCommandTag(PASSIVE_TAG); combat.start(); }
    /**
     * Opens the fight with the intro cinematic: the boss stands passive and untouchable while the
     * watchers see it form, then the fight starts on its own (or early, once everyone skips).
     */
    public void beginIntro(java.util.List<ServerPlayerEntity> watchers) {
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        cancelIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        setAiDisabled(true);
        intro = new NecromancerIntro(this, world, watchers);
        dataTracker.set(INTRO_START, world.getTime() + 1);
        triggerAnim(CONTROLLER, "intro");
    }
    void endIntro(boolean early) {
        intro = null;
        dataTracker.set(INTRO_START, -1L);
        if (early) stopTriggeredAnim(CONTROLLER, "intro");
        startFight();
    }
    /** An operator control takes over mid-scene: the watchers go free and the scene never starts the fight. */
    private void cancelIntro() {
        if (intro == null) return;
        intro.cancel();
        intro = null;
        dataTracker.set(INTRO_START, -1L);
        stopTriggeredAnim(CONTROLLER, "intro");
        setAiDisabled(false);
    }
    public boolean inIntro() { return dataTracker.get(INTRO_START) >= 0; }
    NecromancerIntro intro() { return intro; }
    public void stopFight() { cancelIntro(); addCommandTag(PASSIVE_TAG); combat.cancel(); }
    public void testAction(ServerPlayerEntity player, NecromancerRules.Action action) {
        cancelIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        combat.testAction(player, action);
    }
    /** Raises one siege wave for the nearby party and stays passive; used by the operator rehearsal command. */
    public int testWave(ServerPlayerEntity player, int number) {
        cancelIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        return combat.testWave(player, number);
    }
    /** Starts the second phase now, whatever the health; used by the operator rehearsal command. */
    public void requestTransform() { combat.skipSieges(); schedulePhaseTwo(); }
    /** Starts the current duel's siege now, whatever the health; used by the operator rehearsal command. */
    public boolean requestSiege() { return combat.requestSiege(); }
    /** Starts the colossus's soul split now, whatever the health; used by the operator rehearsal command. */
    public boolean requestSplit() { return combat.requestSplit(); }
    /** Fires one Soul Fire Rain volley at the nearby party and stays passive; used by the operator rehearsal command. */
    public int testRain(ServerPlayerEntity player) {
        cancelIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        return combat.testRain(player);
    }
    public String status() { return combat.status(); }
    NecromancerCombat combat() { return combat; }

    @Override
    public double getAttributeValue(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) {
        double value = super.getAttributeValue(attribute);
        // Vanilla caps max health at 1024; large parties need more without changing the shared attribute.
        if (attribute.equals(EntityAttributes.MAX_HEALTH) && getAttributeInstance(attribute).getBaseValue() > 1024)
            return Math.max(value, getAttributeInstance(attribute).getBaseValue());
        return value;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(world, source, amount);
        if (intro != null) return false; // Still forming in the intro cinematic.
        Entity attacker = source.getAttacker();
        // Its own army and spells never wear it down.
        if (attacker == this || NecromancerMinion.belongsTo(attacker, this)) return false;
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN)) return false;
        if (isTransforming()) return false; // The emergence is a cinematic; it cannot be burst down.
        if (combat.buried() && !soulHitting) return false; // Underground mid-dive.
        // Out of reach on a siege perch, or with its soul out of the colossus, the body is shielded:
        // the waves, or the soul itself, come first. Hits on the freed soul arrive through soulHit.
        if (combat.shielded() && !soulHitting) { combat.deflect(world); return false; }
        amount *= combat.damageMultiplier(world.getTime()); // Exposed after a siege crash.
        // Per-attacker hurt windows: one teammate's hit must not swallow another's simultaneous spell.
        long now = world.getTime();
        hurtWindows.values().removeIf(window -> window.until() <= now);
        UUID key = attacker == null ? ENVIRONMENT_DAMAGE : attacker.getUuid();
        HurtWindow previous = hurtWindows.get(key);
        timeUntilRegen = previous == null ? 0 : 10 + (int)(previous.until() - now);
        lastDamageTaken = previous == null ? 0 : previous.damage();
        float before = getHealth();
        boolean accepted = super.damage(world, source, amount);
        if (accepted) {
            hurtWindows.put(key, new HurtWindow(previous == null ? now + 10 : previous.until(), lastDamageTaken));
            combat.damaged(attacker, before - getHealth(), soulHitting);
        }
        return accepted;
    }

    /** A hit on the freed soul: it wounds the boss through the body's shield. */
    boolean soulHit(ServerWorld world, DamageSource source, float amount) {
        soulHitting = true;
        try { return damage(world, source, amount); } finally { soulHitting = false; }
    }

    @Override
    protected void applyDamage(ServerWorld world, DamageSource source, float amount) {
        float before = getHealth();
        super.applyDamage(world, source, amount);
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (isColossus()) {
            // The first time it reaches a quarter health the colossus holds there until its soul tears free.
            float floor = Math.min(before, getMaxHealth() * NecromancerRules.SPLIT_GATE);
            if (!soulFreed && getHealth() <= floor) {
                setHealth(Math.max(getHealth(), floor));
                combat.splitReached();
            }
            return;
        }
        // Burst damage cannot skip a siege or the second phase: each duel holds at its gate
        // (75%, then half health) until the siege or the transformation takes over. Never a heal.
        float floor = Math.min(before, getMaxHealth() * NecromancerRules.gate(stage));
        if (getHealth() <= floor) {
            setHealth(Math.max(getHealth(), floor));
            if (stage == NecromancerRules.Stage.DUEL_A || stage == NecromancerRules.Stage.DUEL_B) combat.gateReached();
            else if (stage == NecromancerRules.Stage.DONE) phasePending = true;
        }
    }

    @Override protected boolean isInSameTeam(Entity other) {
        return NecromancerMinion.belongsTo(other, this) || super.isInSameTeam(other);
    }

    @Override public void addVelocity(double x, double y, double z) {
        // Authored blinks and steps only; spell impulses do not shove the boss.
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canHit() { return !isBuried() && super.canHit(); }
    @Override public boolean canBeHitByProjectile() { return !isBuried() && super.canBeHitByProjectile(); }
    @Override public boolean isAttackable() { return !isBuried() && super.isAttackable(); }
    @Override protected void initGoals() {
        // The encounter controller chooses spells and movement.
    }

    /**
     * Vanilla stretches one fire sheet over the whole hitbox, which hides the colossus. It still
     * burns and takes fire damage; {@link #tick} shows that with small flames on its frame instead.
     */
    @Override
    public boolean doesRenderOnFire() {
        return !isColossus() && super.doesRenderOnFire();
    }

    @Override
    public void tick() {
        super.tick();
        if (getEntityWorld().isClient() && isColossus() && isOnFire() && !isInvisible() && !isBuried()) {
            var random = getRandom();
            for (int i = 0; i < 2; i++)
                getEntityWorld().addParticleClient(random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME,
                        getParticleX(.8), getY() + random.nextDouble() * getHeight(), getParticleZ(.8),
                        0, .02 + random.nextDouble() * .03, 0);
        }
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (!isAlive()) { combat.cancel(); return; }
        if (relightBraziers) { relightBraziers = false; NecromancerTransformScene.relightCrypt(world, getEntityPos()); }
        if (intro != null) { if (!intro.tick(world)) intro = null; return; }
        if (isBossAggressive()) combat.tick(world);
        else combat.tickReview(world);
    }

    /** Every removal passes here, including a chunk unload or a portal, which skip {@link #remove}. */
    @Override
    public void onRemove(Entity.RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld) {
            if (intro != null) intro.cancel();
            intro = null;
            combat.cancel();
        }
        super.onRemove(reason);
    }

    @Override
    protected void readCustomData(net.minecraft.storage.ReadView view) {
        super.readCustomData(view);
        // An interrupted transformation resumes as the finished colossus rather than replaying.
        setColossus(view.getBoolean("NecromancerColossus", false) || view.getBoolean("NecromancerTransforming", false));
        relightBraziers = view.getBoolean("NecromancerTransforming", false);
        phasePending = !isColossus() && view.getBoolean("NecromancerPhasePending", false);
        soulFreed = isColossus() && view.getBoolean("NecromancerSoulFreed", false);
        // A siege saved mid-way restarts from its first wave; the army itself never saves.
        var stages = NecromancerRules.Stage.values();
        stage = isColossus() ? NecromancerRules.Stage.DONE : stages[Math.clamp(view.getInt("NecromancerStage", 0), 0, stages.length - 1)];
        if (view.getBoolean("NecromancerHasHome", false))
            combat.restoreHome(new net.minecraft.util.math.Vec3d(view.getDouble("NecromancerHomeX", 0), view.getDouble("NecromancerHomeY", 0), view.getDouble("NecromancerHomeZ", 0)));
        dataTracker.set(TRANSFORM_START, -1L);
        calculateDimensions();
        // A scene saved mid-way has lost its watchers: come back fighting, as the scene would have
        // ended, rather than keep its passive tag and NoAI and stand stuck at the first gate.
        if (view.getBoolean("NecromancerIntro", false)) removeCommandTag(PASSIVE_TAG);
        // A saved lunge, dive or mid-cast NoAI flag must not strand the next encounter.
        setNoGravity(false);
        noClip = false;
        if (isBossAggressive()) setAiDisabled(false);
    }

    @Override
    protected void writeCustomData(net.minecraft.storage.WriteView view) {
        super.writeCustomData(view);
        view.putBoolean("NecromancerColossus", isColossus());
        view.putBoolean("NecromancerTransforming", isTransforming());
        view.putBoolean("NecromancerPhasePending", phasePending);
        // The split happens once; one saved before its collapse plays again on the next hit.
        view.putBoolean("NecromancerSoulFreed", soulFreed && !combat.splitUnfinished());
        view.putInt("NecromancerStage", stage.ordinal());
        view.putBoolean("NecromancerIntro", intro != null);
        var home = combat.home();
        view.putBoolean("NecromancerHasHome", home != null);
        if (home != null) {
            view.putDouble("NecromancerHomeX", home.x);
            view.putDouble("NecromancerHomeY", home.y);
            view.putDouble("NecromancerHomeZ", home.z);
        }
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        if (isColossus()) playSound(net.minecraft.sound.SoundEvents.ENTITY_SKELETON_STEP, 1.2f, .45f);
        else super.playStepSound(pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<NecromancerEntity>(CONTROLLER, 3, state -> {
                    if (state.controller().getTriggeredAnimation() != null) return PlayState.CONTINUE;
                    if (state.animatable().isColossus()) return state.setAndContinue(state.isMoving() ? COLOSSUS_WALK : COLOSSUS_IDLE);
                    return state.setAndContinue(state.isMoving() ? WALK : IDLE);
                }).receiveTriggeredAnimations()
                .triggerableAnim("intro", RawAnimation.begin().thenPlay("animation.hollow_necromancer.intro"))
                .triggerableAnim("bolt", RawAnimation.begin().thenPlay("animation.hollow_necromancer.cast_bolt"))
                .triggerableAnim("hands", RawAnimation.begin().thenPlay("animation.hollow_necromancer.cast_hands"))
                .triggerableAnim("drain", RawAnimation.begin().thenPlay("animation.hollow_necromancer.drain"))
                .triggerableAnim("blink", RawAnimation.begin().thenPlay("animation.hollow_necromancer.blink"))
                .triggerableAnim("transform", RawAnimation.begin().thenPlay("animation.hollow_necromancer.transform"))
                .triggerableAnim("swipe", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_swipe"))
                .triggerableAnim("grab", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_grab"))
                .triggerableAnim("rush", RawAnimation.begin().thenLoop("animation.hollow_necromancer.colossus_rush"))
                .triggerableAnim("bite_throw", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_bite_throw"))
                // Siege, ambush and phase-two tells added after the first co-op playtest.
                .triggerableAnim("perch_channel", RawAnimation.begin().thenLoop("animation.hollow_necromancer.perch_channel"))
                .triggerableAnim("crash", RawAnimation.begin().thenPlay("animation.hollow_necromancer.crash"))
                .triggerableAnim("ambush_burst", RawAnimation.begin().thenPlay("animation.hollow_necromancer.ambush_burst"))
                // Second playtest: stealthier charge, Grave Dive, the soul split, Soul Harvest and the perch volley.
                .triggerableAnim("rush_windup", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_rush_crouch")
                        .thenLoop("animation.hollow_necromancer.colossus_rush_coil"))
                .triggerableAnim("rush_set", RawAnimation.begin().thenPlayAndHold("animation.hollow_necromancer.colossus_rush_set"))
                .triggerableAnim("dive", RawAnimation.begin().thenPlayAndHold("animation.hollow_necromancer.colossus_dive"))
                .triggerableAnim("erupt", RawAnimation.begin().thenPlayAndHold("animation.hollow_necromancer.colossus_erupt"))
                .triggerableAnim("stuck", RawAnimation.begin().thenLoop("animation.hollow_necromancer.colossus_stuck"))
                .triggerableAnim("haul", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_haul"))
                .triggerableAnim("split", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_split"))
                .triggerableAnim("collapse", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_collapse"))
                .triggerableAnim("harvest", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_harvest"))
                .triggerableAnim("perch_cast", RawAnimation.begin().thenPlay("animation.hollow_necromancer.perch_cast")
                        .thenLoop("animation.hollow_necromancer.perch_channel"))
                .triggerableAnim("colossus_bolt", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_cast_bolt"))
                .triggerableAnim("colossus_hands", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_cast_hands"))
                .triggerableAnim("colossus_drain", RawAnimation.begin().thenPlay("animation.hollow_necromancer.colossus_cast_drain")));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
