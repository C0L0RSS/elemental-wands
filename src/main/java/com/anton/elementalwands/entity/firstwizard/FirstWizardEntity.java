package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.end.EndFighter;
import com.anton.elementalwands.entity.WandBoss;
import com.anton.elementalwands.registry.ModItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * The First Wizard: a copy of the first player to enter, player-sized, wearing their skin and holding the Wizard's
 * Wand, made of living ink. At half health its left arm falls off and its left flank swells and bursts into tentacles
 * (the change's cinematic, then phase two): it floats and strikes with them, and its box covers the raised body. The
 * wand is in its right hand from the change on, whichever hand the skin's owner uses.
 * {@link FirstWizardCombat} owns every decision; this class owns its state, the damage rules and what clients need to
 * draw it.
 *
 * <p>It fights for the End's encounter through {@link EndFighter}: {@link #startFight} gives it its floor, its
 * roster and whose skin it wears. Summoned with {@code /summon} it fights on a floor round where it stands, any
 * Survival or Adventure player nearby, and wears the nearest one's skin.</p>
 *
 * <p>The skin is carried as a profile in synced data (as the vanilla mannequin carries its own), not looked up from
 * the tab list: it stays for the whole fight even when its owner dies or logs off, and keeps slim arms and the outer
 * layers. Clients resolve it through the game's skin cache.</p>
 */
public class FirstWizardEntity extends PathAwareEntity implements WandBoss, EndFighter {
    private static final String PASSIVE_TAG = "ew_first_wizard_passive";
    private static final TrackedData<ProfileComponent> PROFILE = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.PROFILE);
    /** The spell in hand ("" between spells), the tick it began, and its release and free ticks from then. */
    private static final TrackedData<String> CAST = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Long> CAST_START = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> CAST_RELEASE = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> CAST_FREE = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Where ink gathers while a spell forms; NaN for nowhere. */
    private static final TrackedData<Vector3f> FOCUS = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final Vector3f NO_FOCUS = new Vector3f(Float.NaN, Float.NaN, Float.NaN);
    /** Where its last blink set it down, and how many blinks there have been: clients snap there instead of sliding. */
    private static final TrackedData<Vector3f> ARRIVAL = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Integer> ARRIVALS = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Its phase ({@link #PHASE_ONE}, {@link #CHANGING}, {@link #PHASE_TWO}) and the tick the change began. */
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> PHASE_AT = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.LONG);
    /** Where its free tendril reaches for during an ink move (a slam's line end, a sweep's start); NaN for nowhere. */
    private static final TrackedData<Vector3f> REACH = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    /** How far the intro has poured it into shape, 0 (nothing yet) to 1 (whole); set by end/EndIntro, drawn by client/blockink/CopyForming. */
    private static final TrackedData<Float> FORMED = DataTracker.registerData(FirstWizardEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final int PHASE_ONE = 0, CHANGING = 1, PHASE_TWO = 2;
    /** Phase two's box: the raised body and the ink of its wound, so players can hit what they see. */
    private static final net.minecraft.entity.EntityDimensions RAISED = net.minecraft.entity.EntityDimensions.changing(.8f, 1.8f + (float) FirstWizardRules.LIFT).withEyeHeight(1.62f + (float) FirstWizardRules.LIFT);
    private static final UUID ENVIRONMENT_DAMAGE = new UUID(0, 0);

    private final FirstWizardCombat combat = new FirstWizardCombat(this);
    private record HurtWindow(long until, float damage) {}
    private final Map<UUID, HurtWindow> hurtWindows = new HashMap<>();
    /** The roster beat it: set when it dies while fighting its roster. */
    private boolean defeated;
    /** Whose element it opens with. */
    private WizardAffinity affinity = WizardAffinity.NONE;
    private UUID skinOwner;
    /** The skin's owner holds things in their left hand: so does the copy in phase one (never after the change). */
    private boolean ownerLeftHanded;

    public FirstWizardEntity(EntityType<? extends FirstWizardEntity> type, World world) {
        super(type, world);
        setPersistent();
        experiencePoints = 0;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.FRACTURED_WAND));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, FirstWizardRules.health(1))
                .add(EntityAttributes.MOVEMENT_SPEED, 0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(EntityAttributes.FOLLOW_RANGE, 96);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(PROFILE, ProfileComponent.ofDynamic(new UUID(0, 0)));
        builder.add(CAST, "");
        builder.add(CAST_START, 0L);
        builder.add(CAST_RELEASE, 0);
        builder.add(CAST_FREE, 0);
        builder.add(FOCUS, NO_FOCUS);
        builder.add(ARRIVAL, new Vector3f());
        builder.add(ARRIVALS, 0);
        builder.add(PHASE, PHASE_ONE);
        builder.add(PHASE_AT, 0L);
        builder.add(REACH, NO_FOCUS);
        builder.add(FORMED, 1f);
    }

    // ── Its phase ────────────────────────────────────────────────────────────

    /** {@link #PHASE_ONE}, {@link #CHANGING} (the set piece at half health) or {@link #PHASE_TWO}. */
    public int phase() { return dataTracker.get(PHASE); }
    /** Torn: changing or in phase two (the off arm is gone and the tendrils are out). */
    public boolean torn() { return phase() != PHASE_ONE; }
    public boolean inPhaseTwo() { return phase() == PHASE_TWO; }
    /** Ticks since the change began, with the partial tick; meaningful once torn. */
    public double phaseAge(float partial) { return getEntityWorld().getTime() - dataTracker.get(PHASE_AT) + partial; }

    void setPhase(int phase) {
        if (phase != PHASE_ONE && phase() == PHASE_ONE || phase == CHANGING) dataTracker.set(PHASE_AT, getEntityWorld().getTime());
        dataTracker.set(PHASE, phase);
        // Its left arm comes off in the change, whichever hand the skin's owner uses: from then the wand is in its right.
        setLeftHanded(phase == PHASE_ONE && ownerLeftHanded);
    }

    /** The change's clock jumps on by this many ticks (its cinematic skipped): clients draw the body from there. */
    void shiftPhase(int ticks) { dataTracker.set(PHASE_AT, dataTracker.get(PHASE_AT) - ticks); }

    void showReach(Vec3d point) { dataTracker.set(REACH, point == null ? NO_FOCUS : new Vector3f((float) point.x, (float) point.y, (float) point.z)); }
    /** Where its free tendril reaches during an ink move, or null. */
    public Vec3d reach() {
        Vector3f v = dataTracker.get(REACH);
        return Float.isNaN(v.x) ? null : new Vec3d(v.x, v.y, v.z);
    }

    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (PHASE.equals(data)) calculateDimensions();
    }

    @Override public net.minecraft.entity.EntityDimensions getBaseDimensions(net.minecraft.entity.EntityPose pose) {
        // From the start of the change, which it cannot be hurt during, so the box is right once it can be.
        return torn() ? RAISED : super.getBaseDimensions(pose);
    }

    /** Server: sets it down here at once (Blink Rift), and has every client snap it there rather than slide. */
    void blinkTo(Vec3d feet) {
        setVelocity(Vec3d.ZERO);
        refreshPositionAndAngles(feet.x, feet.y, feet.z, getYaw(), getPitch());
        dataTracker.set(ARRIVAL, new Vector3f((float) feet.x, (float) feet.y, (float) feet.z));
        dataTracker.set(ARRIVALS, dataTracker.get(ARRIVALS) + 1);
    }

    /** Client: the blinks already snapped to. */
    private int arrivalsSeen = -1;

    // ── The End's encounter ──────────────────────────────────────────────────

    /** The End encounter's factory: a copy that stands passive until {@link #startFight} gives it its floor and roster. */
    public static FirstWizardEntity forEncounter(ServerWorld world) {
        FirstWizardEntity copy = new FirstWizardEntity(com.anton.elementalwands.registry.ModEntities.FIRST_WIZARD, world);
        copy.addCommandTag(PASSIVE_TAG);
        return copy;
    }

    @Override
    public void startFight(BlockPos centre, double floorRadius, List<ServerPlayerEntity> roster, UUID skinOwner) {
        removeCommandTag(PASSIVE_TAG);
        Arena arena = new Arena(new Vec3d(centre.getX() + .5, centre.getY() + 1, centre.getZ() + .5), floorRadius);
        ServerPlayerEntity owner = null;
        for (ServerPlayerEntity player : roster) if (player.getUuid().equals(skinOwner)) owner = player;
        if (owner == null && getEntityWorld() instanceof ServerWorld world) owner = world.getServer().getPlayerManager().getPlayer(skinOwner);
        if (owner != null) wear(owner);
        else this.skinOwner = skinOwner;
        combat.startEncounter(arena, roster);
    }

    @Override public void addParticipant(ServerPlayerEntity player) { combat.addParticipant(player); }
    @Override public void removeParticipant(UUID player) { combat.removeParticipant(player); }

    @Override public void stopFight() {
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        combat.forgetEncounter();
    }

    @Override public boolean fightComplete() { return defeated; }
    @Override public Entity asEntity() { return this; }

    // ── Who it is ────────────────────────────────────────────────────────────

    /** Takes this player's skin, arm and element for the whole fight. */
    public void wear(ServerPlayerEntity player) {
        skinOwner = player.getUuid();
        dataTracker.set(PROFILE, ProfileComponent.ofStatic(player.getGameProfile()));
        ownerLeftHanded = player.getMainArm() == Arm.LEFT;
        setLeftHanded(!torn() && ownerLeftHanded);
        affinity = EWAttachments.getAffinity(player);
        setCustomName(null);
    }

    public ProfileComponent profile() { return dataTracker.get(PROFILE); }
    public UUID skinOwner() { return skinOwner; }
    public boolean wearsSomeone() { return skinOwner != null; }
    public WizardAffinity affinity() { return affinity; }
    /** How far the intro has formed it (end/EndIntro): 1 once whole, and for any copy not in an intro. */
    public float formed() { return dataTracker.get(FORMED); }
    public void setFormed(float formed) { dataTracker.set(FORMED, Math.clamp(formed, 0f, 1f)); }

    // ── The spell in hand, as clients follow it ──────────────────────────────

    void showCast(String move, long start, int release, int free) {
        dataTracker.set(CAST, move);
        dataTracker.set(CAST_START, start);
        dataTracker.set(CAST_RELEASE, release);
        dataTracker.set(CAST_FREE, free);
    }
    void showFocus(Vec3d point) { dataTracker.set(FOCUS, point == null ? NO_FOCUS : new Vector3f((float) point.x, (float) point.y, (float) point.z)); }

    /** The spell in hand, "" when free. */
    public String castMove() { return dataTracker.get(CAST); }
    /** Ticks since the spell in hand began, with the partial tick; negative when none. */
    public double castAge(float partial) { return castMove().isEmpty() ? -1 : getEntityWorld().getTime() - dataTracker.get(CAST_START) + partial; }
    public int castRelease() { return dataTracker.get(CAST_RELEASE); }
    public int castFree() { return dataTracker.get(CAST_FREE); }
    /** Where ink gathers while a spell forms, or null. */
    public Vec3d focus() {
        Vector3f v = dataTracker.get(FOCUS);
        return Float.isNaN(v.x) ? null : new Vec3d(v.x, v.y, v.z);
    }

    // ── Operator controls ────────────────────────────────────────────────────

    @Override public boolean isBossAggressive() { return !getCommandTags().contains(PASSIVE_TAG); }
    /** /ew wizard fight: fights whoever is nearby (or its roster, in the End). */
    public void fight() { removeCommandTag(PASSIVE_TAG); combat.start(); }
    /** /ew wizard calm: stands passive; what it already cast burns out. */
    public void calm() { addCommandTag(PASSIVE_TAG); combat.calm(); }
    /** /ew wizard stop: stands passive and clears everything it left. */
    public void stop() { addCommandTag(PASSIVE_TAG); combat.cancel(); }
    /** /ew wizard move: that one spell at this player, then passive. False when the spell is not built or cannot be cast. */
    public boolean testMove(ServerPlayerEntity player, FirstWizardRules.Move move) {
        addCommandTag(PASSIVE_TAG);
        return combat.testMove(player, move);
    }
    /**
     * /ew wizard phase two: the change starts as soon as the move in hand allows, fighting or passive (a passive copy
     * plays it and stands passive in phase two). Its health is brought down to half if it is above. False when it is
     * already changing or in phase two.
     */
    public boolean requestPhaseTwo() { return combat.requestPhaseTwo(); }
    /** For fixtures and a reload mid-change: phase two at once, without the set piece. */
    public void phaseTwoNow() { combat.phaseTwoNow(); }
    /** For fixtures: a move and then, as the fight casts it, its scripted follow-up (always), then passive. */
    public boolean testCombination(ServerPlayerEntity player, FirstWizardRules.Move move) {
        addCommandTag(PASSIVE_TAG);
        return combat.testCombination(player, move);
    }
    public String status() { return combat.status(); }
    /** For fixtures: the spell in hand, lower case; "free" between spells. */
    public String moveName() { return combat.moveName(); }
    /** For fixtures: the floor it fights on. */
    public Arena arena() { return combat.arena(); }
    /** For fixtures: lingering effects still running. */
    public int lingering() { return combat.lingeringCount(); }

    // ── Damage ───────────────────────────────────────────────────────────────

    @Override public boolean eligible(ServerPlayerEntity player) { return combat.eligible(player); }

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
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            killing = true;
            try { return super.damage(world, source, amount); } finally { killing = false; }
        }
        // The change is the set piece: nothing hurts it while it plays.
        if (phase() == CHANGING) return false;
        Entity attacker = source.getAttacker();
        if (attacker == this) return false;
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.CRAMMING) || source.isOf(DamageTypes.FALL)) return false;
        // Damage only from players the encounter admits.
        if (attacker instanceof ServerPlayerEntity player && !eligible(player)) return false;
        // Per-attacker hurt windows: one teammate's hit must not swallow another's simultaneous spell.
        long now = world.getTime();
        hurtWindows.values().removeIf(window -> window.until() <= now);
        UUID key = attacker == null ? ENVIRONMENT_DAMAGE : attacker.getUuid();
        HurtWindow previous = hurtWindows.get(key);
        timeUntilRegen = previous == null ? 0 : 10 + (int) (previous.until() - now);
        lastDamageTaken = previous == null ? 0 : previous.damage();
        boolean accepted = super.damage(world, source, amount);
        if (accepted) hurtWindows.put(key, new HurtWindow(previous == null ? now + 10 : previous.until(), lastDamageTaken));
        return accepted;
    }

    /** A /kill (damage that bypasses invulnerability) is in hand: it is not held at half health. */
    private boolean killing;

    /**
     * Half health starts phase two: in phase one it is held at half, however hard the blow, until the change begins
     * (the spell in hand finishes first). A /kill is not held.
     */
    @Override
    protected void applyDamage(ServerWorld world, DamageSource source, float amount) {
        super.applyDamage(world, source, amount);
        float half = getMaxHealth() * FirstWizardRules.PHASE_TWO_AT;
        if (phase() == PHASE_ONE && !killing && getHealth() <= half) {
            setHealth(half);
            combat.enterPhaseTwo();
        }
    }

    /** A shield that stops one of its blows is knocked aside for this long. */
    @Override public float getWeaponDisableBlockingForSeconds() { return 3; }

    @Override public void addVelocity(double x, double y, double z) {
        // Its own steps only: nothing staggers or shoves it (the WandBoss rule).
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) {}
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean isFireImmune() { return true; }
    @Override protected void initGoals() {
        // The fight chooses its spells and its steps.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) {
            int arrivals = dataTracker.get(ARRIVALS);
            if (arrivalsSeen >= 0 && arrivals != arrivalsSeen) {
                // A blink: it vanishes here and rises there, never sliding across the floor between.
                Vector3f at = dataTracker.get(ARRIVAL);
                getInterpolator().clear();
                setPosition(at.x, at.y, at.z);
                resetPosition();
            }
            arrivalsSeen = arrivals;
            // With a spell in hand it moves only by Cast.step (a dash): follow each step within the tick, not three behind.
            getInterpolator().setLerpDuration(castMove().isEmpty() ? 3 : 1);
            return;
        }
        if (isAlive()) {
            if (isBossAggressive()) combat.tick(world);
            else combat.tickPassive(world);
        } else combat.cancel();
    }

    @Override
    public void onDeath(DamageSource source) {
        if (getEntityWorld() instanceof ServerWorld world) defeated = combat.died(world);
        super.onDeath(source);
    }

    /** Every removal passes here, including a chunk unload or the server stopping, which skip {@link #remove}. */
    @Override
    public void onRemove(Entity.RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld) combat.cancel();
        super.onRemove(reason);
    }

    @Override
    protected void readCustomData(net.minecraft.storage.ReadView view) {
        super.readCustomData(view);
        view.read("FirstWizardProfile", ProfileComponent.CODEC).ifPresent(profile -> dataTracker.set(PROFILE, profile));
        view.read("FirstWizardSkinOwner", net.minecraft.util.Uuids.INT_STREAM_CODEC).ifPresent(id -> skinOwner = id);
        try { affinity = WizardAffinity.valueOf(view.getString("FirstWizardAffinity", "NONE")); } catch (IllegalArgumentException e) { affinity = WizardAffinity.NONE; }
        ownerLeftHanded = view.getBoolean("FirstWizardOwnerLeftHanded", isLeftHanded());
        // A copy that fought for the End waits passive until its encounter starts it again; a summoned one keeps its floor.
        if (view.getBoolean("FirstWizardEnd", false)) addCommandTag(PASSIVE_TAG);
        if (view.getBoolean("FirstWizardHasArena", false))
            combat.restoreArena(new Arena(new Vec3d(view.getDouble("FirstWizardX", 0), view.getDouble("FirstWizardY", 0), view.getDouble("FirstWizardZ", 0)),
                    view.getDouble("FirstWizardRadius", FirstWizardRules.FLOOR_RADIUS)));
        // Saved in phase two or during the change: phase two (a change cut short ends torn, never between). Saved held at
        // half health, waiting for the change: it waits again.
        String phase = view.getString("FirstWizardPhase", "one");
        if (phase.equals("two")) combat.phaseTwoNow();
        else if (phase.equals("pending")) combat.enterPhaseTwo();
        setNoGravity(false);
        noClip = false;
    }

    @Override
    protected void writeCustomData(net.minecraft.storage.WriteView view) {
        super.writeCustomData(view);
        view.put("FirstWizardProfile", ProfileComponent.CODEC, profile());
        if (skinOwner != null) view.put("FirstWizardSkinOwner", net.minecraft.util.Uuids.INT_STREAM_CODEC, skinOwner);
        view.putString("FirstWizardAffinity", affinity.name());
        view.putBoolean("FirstWizardOwnerLeftHanded", ownerLeftHanded);
        view.putBoolean("FirstWizardEnd", combat.inEncounter());
        view.putString("FirstWizardPhase", torn() ? "two" : combat.changePending() ? "pending" : "one");
        Arena arena = combat.arena();
        view.putBoolean("FirstWizardHasArena", arena != null);
        if (arena != null) {
            view.putDouble("FirstWizardX", arena.centre().x);
            view.putDouble("FirstWizardY", arena.centre().y);
            view.putDouble("FirstWizardZ", arena.centre().z);
            view.putDouble("FirstWizardRadius", arena.radius());
        }
    }
}
