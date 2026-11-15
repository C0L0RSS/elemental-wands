package com.anton.elementalwands.entity.pyreking;

import com.anton.elementalwands.entity.WandBoss;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Pyre King: a swordsman with a chained sword. {@link PyreKingCombat} owns every decision;
 * this class owns his state and what clients need to draw him.
 *
 * <p>The server picks his clip and clients follow it from synced data, so everyone sees the same
 * clip start at the same moment. Three more things are synced for the clips that carry him or
 * his sword somewhere real: where he stood when the clip began (Zip, Lunge and the Turning Cut
 * are drawn from there, since they move and turn him inside the clip), and how far the thrown
 * sword's real target is from the clip's authored one ({@link PyreKingAim}).</p>
 */
public class PyreKingEntity extends PathAwareEntity implements GeoEntity, WandBoss {
    public static final String CONTROLLER = "main";
    private static final String PASSIVE_TAG = "ew_pyre_king_passive";
    private static final TrackedData<String> CLIP = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> SEQUENCE = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> BLEND = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> CLIP_START = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<BlockPos> ORIGIN_BLOCK = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Vector3f> ORIGIN_WITHIN = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Float> ORIGIN_YAW = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Vector3f> AIM = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Boolean> SECOND = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> FACING = DataTracker.registerData(PyreKingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** The server turns him by this clip's end turn once its blow is done; before that an interrupted clip has turned nothing. */
    private static final Map<String, Double> TURNED_AT = Map.of("turning_cut", .86);
    private static final Map<String, RawAnimation> ANIMATIONS = new HashMap<>();
    private static final UUID ENVIRONMENT_DAMAGE = new UUID(0, 0);

    static {
        for (PyreKingClips.Clip clip : PyreKingClips.ALL) {
            var animation = RawAnimation.begin();
            // A finished clip holds its last frame, his guard, until the server names the next one.
            ANIMATIONS.put(clip.name(), clip.loop() ? animation.thenLoop(clip.animation()) : animation.thenPlayAndHold(clip.animation()));
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final PyreKingCombat combat = new PyreKingCombat(this);
    private record HurtWindow(long until, float damage) {}
    private final Map<UUID, HurtWindow> hurtWindows = new HashMap<>();
    private boolean unboundDone;
    /** Server: the cinematic that opens his fight, while it plays. */
    private PyreKingIntro intro;
    /** Client: the clip start already handed to the animation controller, and the clip before it. */
    private int shownSequence = -1;
    private String shownClip = "idle";
    /** Client: a clip has been started and must still be set to the world's clock (see the controller). */
    private boolean setClock;
    /** Client: the way he faces, this tick and last. */
    private float facing, lastFacing;
    private boolean facingSeen;

    public PyreKingEntity(EntityType<? extends PyreKingEntity> type, World world) {
        super(type, world);
        setPersistent();
        experiencePoints = 0;
        // His steps and hauls are fast. A client that eased each move over three ticks, as it does
        // for other mobs, would draw him up to a block behind where his blade is being swept.
        interpolator.setLerpDuration(1);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, PyreKingRules.health(1))
                .add(EntityAttributes.MOVEMENT_SPEED, 0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(EntityAttributes.FOLLOW_RANGE, PyreKingRules.ENCOUNTER_RANGE);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(CLIP, "idle");
        builder.add(SEQUENCE, 0);
        builder.add(BLEND, 0);
        builder.add(CLIP_START, 0L);
        builder.add(ORIGIN_BLOCK, BlockPos.ORIGIN);
        builder.add(ORIGIN_WITHIN, new Vector3f());
        builder.add(ORIGIN_YAW, 0f);
        builder.add(AIM, new Vector3f());
        builder.add(SECOND, false);
        builder.add(FACING, 0f);
    }

    // ── His clip, as clients follow it ───────────────────────────────────────

    /**
     * Start a clip. With a blend, clients ease into its first frame over that many ticks and the
     * clip itself starts after them; {@link #clipStart()} is the tick its first frame plays.
     */
    public void play(String clip, int blend) {
        if (PyreKingClips.get(clip) == null) throw new IllegalArgumentException("No such Pyre King clip: " + clip);
        // A clip that carries him, cut off before the game has made its travel or turn real, cannot be blended out of.
        if (clipCarriesHim() && clipSeconds(0) < carriedBy(clip())) blend = 0;
        Vec3d at = getEntityPos();
        BlockPos block = BlockPos.ofFloored(at);
        dataTracker.set(CLIP, clip);
        dataTracker.set(BLEND, blend);
        dataTracker.set(CLIP_START, getEntityWorld().getTime() + blend);
        dataTracker.set(ORIGIN_BLOCK, block);
        dataTracker.set(ORIGIN_WITHIN, new Vector3f((float) (at.x - block.getX()), (float) (at.y - block.getY()), (float) (at.z - block.getZ())));
        dataTracker.set(ORIGIN_YAW, getBodyYaw());
        dataTracker.set(AIM, new Vector3f());
        dataTracker.set(SEQUENCE, dataTracker.get(SEQUENCE) + 1);
    }

    public String clip() { return dataTracker.get(CLIP); }
    public long clipStart() { return dataTracker.get(CLIP_START); }
    /** Ticks clients blend into the current clip before its first frame. */
    public int clipBlend() { return dataTracker.get(BLEND); }
    /**
     * The moment of the current clip that shows now, by the world's clock and the clip's tempo; negative while clients
     * are still blending into it.
     */
    public double clipSeconds(float partialTick) {
        return PyreKingRules.tempo(clip()).clip((getEntityWorld().getTime() - clipStart() + partialTick) / 20.0);
    }
    /** Where he stood, and the way he faced, when the current clip began. */
    public Vec3d clipOrigin() {
        BlockPos block = dataTracker.get(ORIGIN_BLOCK);
        Vector3f within = dataTracker.get(ORIGIN_WITHIN);
        return new Vec3d(block.getX() + within.x, block.getY() + within.y, block.getZ() + within.z);
    }
    public float clipYaw() { return dataTracker.get(ORIGIN_YAW); }
    /** Clips that move or turn him inside the clip: he is drawn from where the clip began until it ends. */
    public boolean clipCarriesHim() {
        PyreKingClips.Clip clip = PyreKingClips.get(clip());
        return clip != null && (clip.endTurnDegrees() != 0 || clip.endZ() != 0 || clip.endX() != 0);
    }
    /** Seconds into a clip that carries him by which its whole travel or turn has been made real. */
    private static double carriedBy(String clip) {
        PyreKingTracks.Track track = PyreKingTracks.get(clip);
        if (track != null && track.travelEnd() > 0) {
            double t = 0;
            while (t < track.seconds && track.travel(t) < track.travelEnd() - 1e-3) t += .05;
            return t;
        }
        return TURNED_AT.getOrDefault(clip, 0.0);
    }

    /**
     * The way his body faces, exactly. Vanilla syncs a mob's yaw in 1.4 degree steps and lets each
     * client swing its body round after its head in its own time, up to 75 degrees behind; his
     * blows are swept along this facing, so clients must draw him on it.
     */
    public float facing(float partialTick) {
        return facingSeen ? lastFacing + MathHelper.wrapDegrees(facing - lastFacing) * partialTick : getBodyYaw();
    }

    /** The thrown sword's real target less the clip's authored one (blocks: right, up, ahead); zero when not aimed. */
    public Vector3f aim() { return dataTracker.get(AIM); }
    void setAim(double right, double up, double ahead) { dataTracker.set(AIM, new Vector3f((float) right, (float) up, (float) ahead)); }
    /** Phase two: Unbound has torn his chains loose. */
    public boolean isSecondPhase() { return dataTracker.get(SECOND); }
    void setSecondPhase(boolean second) { dataTracker.set(SECOND, second); }
    boolean unboundDone() { return unboundDone; }
    void setUnboundDone(boolean done) { unboundDone = done; }

    // ── Operator controls ────────────────────────────────────────────────────

    @Override public boolean isBossAggressive() { return !getCommandTags().contains(PASSIVE_TAG); }
    public void startFight() { dropIntro(); removeCommandTag(PASSIVE_TAG); combat.start(); }
    public void stopFight() { dropIntro(); addCommandTag(PASSIVE_TAG); combat.cancel(); play("idle", 3); }

    /**
     * His intro: he is put on his throne in the hall whose centre this is, and the scene plays for these players.
     * The fight starts when it ends, with him on his mark.
     */
    public void beginIntro(ServerWorld world, net.minecraft.util.math.BlockPos centre, java.util.List<ServerPlayerEntity> watchers) {
        dropIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        intro = new PyreKingIntro(this, world, centre, watchers, world.getTime() + 1);
    }
    public boolean inIntro() { return intro != null; }
    /** For fixtures: the intro's tick, or -1 when none plays. */
    public int introClock() { return intro == null ? -1 : intro.clock(); }
    /** The scene has run out, or everyone skipped it: the fight. */
    void endIntro() { intro = null; startFight(); }
    /** An operator took over, or he is gone: the scene lets everyone go and the hall is lit again. */
    private void dropIntro() {
        if (intro == null) return;
        PyreKingIntro running = intro;
        intro = null;
        running.cancel();
    }
    /** Play one clip from the start and stand passive; a looping clip plays until {@link #stopRehearsal()}. */
    public boolean rehearse(String clip) {
        if (PyreKingClips.get(clip) == null) return false;
        dropIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        play(clip, 0);
        return true;
    }
    /** Back to his guard. Whatever move was in hand is dropped with it, so nothing unseen goes on hitting. */
    public void stopRehearsal() { dropIntro(); combat.cancel(); play("idle", 0); }
    /** One move at the nearest eligible player, then passive again; used by the operator rehearsal command. */
    public boolean testMove(ServerPlayerEntity player, PyreKingRules.Move move) {
        dropIntro();
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        return combat.testMove(player, move);
    }
    /** Starts Unbound now, whatever his health; used by the operator rehearsal command. */
    public boolean requestUnbound() { return combat.requestUnbound(); }
    public String status() { return combat.status(); }
    /** For fixtures: the blade's pommel and tip now, in the world, as the server's sweep reads them; null when no move is in hand. */
    public Vec3d[] sweptBlade() { return combat.blade(); }
    /** For fixtures: the move in hand, lower case; "free" between moves. */
    public String moveName() { return combat.moveName(); }
    PyreKingCombat combat() { return combat; }

    // ── Damage ───────────────────────────────────────────────────────────────

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
        Entity attacker = source.getAttacker();
        if (attacker == this || intro != null) return false;       // nothing reaches him while his intro plays
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.CRAMMING)) return false;
        if (combat.untouchable()) { combat.deflect(world); return false; }   // Unbound is a set piece; it cannot be burst down.
        // Per-attacker hurt windows: one teammate's hit must not swallow another's simultaneous spell.
        long now = world.getTime();
        hurtWindows.values().removeIf(window -> window.until() <= now);
        UUID key = attacker == null ? ENVIRONMENT_DAMAGE : attacker.getUuid();
        HurtWindow previous = hurtWindows.get(key);
        timeUntilRegen = previous == null ? 0 : 10 + (int) (previous.until() - now);
        lastDamageTaken = previous == null ? 0 : previous.damage();
        float before = getHealth();
        boolean accepted = super.damage(world, source, amount);
        if (accepted) {
            hurtWindows.put(key, new HurtWindow(previous == null ? now + 10 : previous.until(), lastDamageTaken));
            combat.damaged(attacker, before - getHealth());
        }
        return accepted;
    }

    @Override
    protected void applyDamage(ServerWorld world, DamageSource source, float amount) {
        float before = getHealth();
        super.applyDamage(world, source, amount);
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || unboundDone) return;
        // Burst damage cannot skip the phase change: he holds at half health until Unbound has played.
        float floor = Math.min(before, getMaxHealth() * PyreKingRules.PHASE_GATE);
        if (getHealth() <= floor) {
            setHealth(Math.max(getHealth(), floor));
            combat.gateReached();
        }
    }

    /** A shield stops one of his blows and is knocked aside for this long, as by an axe: you block once, then you must move. */
    @Override public float getWeaponDisableBlockingForSeconds() { return 5; }

    @Override public void addVelocity(double x, double y, double z) {
        // His own steps and hauls only; spell impulses do not shove him.
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) {}
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean isFireImmune() { return true; }
    @Override protected void initGoals() {
        // The encounter controller chooses his moves and his movement.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) {
            float now = dataTracker.get(FACING);
            lastFacing = facingSeen ? facing : now;
            facing = now;
            facingSeen = true;
            return;
        }
        if (isAlive()) {
            if (intro != null) {
                // The scene first, then the review tick, which returns him to his idle when the scene's last clip ends.
                if (!intro.tick()) intro = null;
                else combat.tickReview(world);
            } else if (isBossAggressive()) combat.tick(world);
            else combat.tickReview(world);
        } else { dropIntro(); combat.cancel(); }
        dataTracker.set(FACING, getBodyYaw());
    }

    @Override
    public void onDeath(DamageSource source) {
        if (getEntityWorld() instanceof ServerWorld world) combat.died(world);
        super.onDeath(source);
    }

    /** Every removal passes here, including a chunk unload or a portal, which skip {@link #remove}. */
    @Override
    public void onRemove(Entity.RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld) { dropIntro(); combat.cancel(); }
        super.onRemove(reason);
    }

    @Override
    protected void readCustomData(net.minecraft.storage.ReadView view) {
        super.readCustomData(view);
        // A saved fight resumes in its phase, from his guard: a clip saved mid-way is not replayed.
        unboundDone = view.getBoolean("PyreKingUnbound", false);
        setSecondPhase(unboundDone);
        if (view.getBoolean("PyreKingHasHome", false))
            combat.restoreHome(new Vec3d(view.getDouble("PyreKingHomeX", 0), view.getDouble("PyreKingHomeY", 0), view.getDouble("PyreKingHomeZ", 0)));
        setNoGravity(false);
        noClip = false;
        setAiDisabled(false);
    }

    @Override
    protected void writeCustomData(net.minecraft.storage.WriteView view) {
        super.writeCustomData(view);
        view.putBoolean("PyreKingUnbound", unboundDone);
        Vec3d home = combat.home();
        view.putBoolean("PyreKingHasHome", home != null);
        if (home != null) {
            view.putDouble("PyreKingHomeX", home.x);
            view.putDouble("PyreKingHomeY", home.y);
            view.putDouble("PyreKingHomeZ", home.z);
        }
    }

    // ── Client: following the server's clip ──────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<PyreKingEntity>(CONTROLLER, 0, state -> {
            PyreKingEntity king = state.animatable();
            int sequence = king.dataTracker.get(SEQUENCE);
            if (sequence != king.shownSequence) {
                rebase(state.manager().getBoneSnapshotCollection(), PyreKingClips.get(king.shownClip));
                king.shownSequence = sequence;
                king.shownClip = king.clip();
                king.setClock = true;
                state.controller().transitionLength(king.dataTracker.get(BLEND));
                state.controller().forceAnimationReset();
            }
            return state.setAndContinue(ANIMATIONS.getOrDefault(king.clip(), ANIMATIONS.get("idle")));
        }) {
            /**
             * A clip starts on a client when that client first draws him after the server names
             * it. That is a tick late at best, and far later for a client that was looking away
             * or has only just come near; the clip would then play from its first frame while
             * the server is well into it. So once a one-shot clip is running, its time is set
             * from the tick the server said its first frame plays on.
             */
            @Override
            protected double adjustTick(double tick) {
                double adjusted = super.adjustTick(tick);
                PyreKingEntity king = PyreKingEntity.this;
                if (getAnimationState() != State.RUNNING) return adjusted;
                PyreKingClips.Clip clip = PyreKingClips.get(king.clip());
                if (clip == null) return adjusted;
                if (king.setClock) {
                    king.setClock = false;
                    if (!clip.loop()) {
                        double into = Math.max(0, king.getEntityWorld().getTime() - king.clipStart() - .5);      // mid-tick: the tick's fraction is not known here
                        this.tickOffset = tick - into;
                        adjusted = this.animationSpeed * into;
                    }
                }
                // The clip's tempo: which moment of it shows this long after it began. The server sweeps his blade by the same map.
                return PyreKingRules.tempo(clip.name()).clip(adjusted / 20) * 20;
            }
        });
    }

    /**
     * Makes the pose a clip leaves blendable into the next. Some clips end a whole turn round
     * (a spin leaves the pelvis at 360 degrees), and three end with him moved or turned, which
     * the game has by then made real; without this a blend would unwind the turn or slide him
     * back from where the clip began.
     */
    private static void rebase(Map<String, BoneSnapshot> snapshots, PyreKingClips.Clip left) {
        for (BoneSnapshot snapshot : snapshots.values()) {
            float x = wrap(snapshot.getRotX()), y = wrap(snapshot.getRotY()), z = wrap(snapshot.getRotZ());
            // The same turn has a second spelling: (x + 180, 180 - y, z + 180). A clip can end on
            // the far one (a hand at -180, -180, 180 is hardly turned at all), and a blend, which
            // eases each angle on its own, would then spin the bone half round on every axis.
            float x2 = wrap(x + MathHelper.PI), y2 = wrap(MathHelper.PI - y), z2 = wrap(z + MathHelper.PI);
            BoneSnapshot rest = snapshot.getBone().getInitialSnapshot();
            if (apart(x2, y2, z2, rest) + .2f < apart(x, y, z, rest)) { x = x2; y = y2; z = z2; }
            snapshot.updateRotation(x, y, z);
        }
        BoneSnapshot pelvis = snapshots.get("pelvis");
        if (pelvis == null || left == null) return;
        if (left.endTurnDegrees() != 0) {
            // His pelvis carried the turn about his own centre; the game now faces him that way instead.
            float turn = left.endTurnDegrees() * MathHelper.RADIANS_PER_DEGREE;
            float cos = MathHelper.cos(turn), sin = MathHelper.sin(turn);
            float x = pelvis.getOffsetX(), z = pelvis.getOffsetZ();
            pelvis.updateOffset(x * cos - z * sin, pelvis.getOffsetY(), x * sin + z * cos);
            // A turn to his left is a rising Y on this bone; taking it back out is a subtraction.
            pelvis.updateRotation(pelvis.getRotX(), wrap(pelvis.getRotY() - turn), pelvis.getRotZ());
        }
        // The clip's bone units: pixels, x mirrored.
        pelvis.updateOffset(pelvis.getOffsetX() + left.endX() * 16, pelvis.getOffsetY() - left.endY() * 16, pelvis.getOffsetZ() - left.endZ() * 16);
    }

    private static float wrap(float radians) { return MathHelper.wrapDegrees(radians * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE; }

    private static float apart(float x, float y, float z, BoneSnapshot rest) {
        return Math.abs(wrap(x - rest.getRotX())) + Math.abs(wrap(y - rest.getRotY())) + Math.abs(wrap(z - rest.getRotZ()));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
