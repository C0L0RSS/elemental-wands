package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell;
import com.anton.elementalwands.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The First Wizard's Sentry Bloom: one towering flower that stores healing for the copy until it is broken. Its rules
 * live in {@link SentryBloomSpell}; this is the body players hit and what clients need to draw it.
 *
 * <p>It is a hostile mob to the players' spells and weapons (a {@link Monster}, so their own Sentry Bloom flowers shoot
 * it too), not a {@code WandBoss}: it has health of its own and takes their damage like any monster, but only from
 * players the copy's fight admits, never from the copy. It cannot be moved, pushed or knocked back, has no AI, and is
 * never saved: the fight clears it with everything else.</p>
 */
public final class InkBloomEntity extends MobEntity implements Monster {
    /** The tick it began to unfurl. */
    private static final TrackedData<Long> OPENED = DataTracker.registerData(InkBloomEntity.class, TrackedDataHandlerRegistry.LONG);
    /** What it holds, as a share of what it can hold (0 to 1). */
    private static final TrackedData<Float> STORE = DataTracker.registerData(InkBloomEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** 0 standing, 1 delivering, 2 wilting; and the tick that began. */
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(InkBloomEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> STAGE_AT = DataTracker.registerData(InkBloomEntity.class, TrackedDataHandlerRegistry.LONG);
    /** Whom its head is turned to (an entity id, -1 for no one) and how many seeds it has spat. */
    private static final TrackedData<Integer> TARGET = DataTracker.registerData(InkBloomEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> SHOTS = DataTracker.registerData(InkBloomEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final int STANDING = 0, DELIVERING = 1, WILTING = 2;

    /** Server: the copy it stores for. */
    private FirstWizardEntity owner;
    private Vec3d root;

    // Client: the head's pose, eased toward its target each tick; when a shot and a stored hit last happened.
    private float headYaw, headTilt = .35f, lastHeadYaw, lastHeadTilt = .35f;
    private int seenShots, shotAge = -100, storeAge = -100;
    private float seenStore;

    public InkBloomEntity(EntityType<? extends InkBloomEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        setAiDisabled(true);
        setPersistent();
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, SentryBloomSpell.health(1))
                .add(EntityAttributes.MOVEMENT_SPEED, 0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Server: plants one for the copy here, with this much health, unfurling from now. */
    public static InkBloomEntity plant(FirstWizardEntity owner, Vec3d at, float health) {
        ServerWorld world = (ServerWorld) owner.getEntityWorld();
        InkBloomEntity bloom = new InkBloomEntity(ModEntities.FIRST_WIZARD_BLOOM, world);
        bloom.owner = owner;
        bloom.root = at;
        bloom.refreshPositionAndAngles(at.x, at.y, at.z, owner.getRandom().nextFloat() * 360, 0);
        bloom.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
        bloom.setHealth(health);
        bloom.dataTracker.set(OPENED, world.getTime());
        bloom.dataTracker.set(STAGE_AT, world.getTime());
        world.spawnEntity(bloom);
        return bloom;
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OPENED, 0L);
        builder.add(STORE, 0f);
        builder.add(STAGE, STANDING);
        builder.add(STAGE_AT, 0L);
        builder.add(TARGET, -1);
        builder.add(SHOTS, 0);
    }

    // ── Server: set by the spell ─────────────────────────────────────────────

    public FirstWizardEntity owner() { return owner; }
    public void store(float share) { dataTracker.set(STORE, MathHelper.clamp(share, 0, 1)); }
    public void aim(Entity target) { dataTracker.set(TARGET, target == null ? -1 : target.getId()); }
    public void fired() { dataTracker.set(SHOTS, dataTracker.get(SHOTS) + 1); }
    public void stage(int stage) {
        dataTracker.set(STAGE, stage);
        dataTracker.set(STAGE_AT, getEntityWorld().getTime());
        if (stage != STANDING) aim(null);
    }

    /** Where a seed leaves: the head's pivot. */
    public Vec3d head() { return getEntityPos().add(0, SentryBloomSpell.HEAD_PIVOT, 0); }

    // ── Both sides ───────────────────────────────────────────────────────────

    public float storeShare() { return dataTracker.get(STORE); }
    public int bloomStage() { return dataTracker.get(STAGE); }
    public int targetId() { return dataTracker.get(TARGET); }
    /** Ticks since it began to unfurl, and since its stage began, with the partial tick. */
    public double openAge(float partial) { return getEntityWorld().getTime() - dataTracker.get(OPENED) + partial; }
    public double stageAge(float partial) { return getEntityWorld().getTime() - dataTracker.get(STAGE_AT) + partial; }
    /** 0 to 1 as it unfurls. */
    public double grown(float partial) { return Math.clamp(openAge(partial) / SentryBloomSpell.UNFURL, 0, 1); }

    public float headYaw(float delta) { return lastHeadYaw + MathHelper.wrapDegrees((headYaw - lastHeadYaw) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE * delta; }
    public float headTilt(float delta) { return MathHelper.lerp(delta, lastHeadTilt, headTilt); }
    /** 1 at the moment of a shot, easing to 0. */
    public float recoil(float delta) { return MathHelper.clamp(1 - (age + delta - shotAge) / 6f, 0, 1); }
    /** Ticks since its store last grew, with the partial tick. */
    public float sinceStored(float delta) { return age + delta - storeAge; }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld)) {
            lastHeadYaw = headYaw; lastHeadTilt = headTilt;
            float wantYaw = headYaw, wantTilt = .35f, ease = .3f;
            Entity target = getEntityWorld().getEntityById(targetId());
            if (bloomStage() == WILTING) { wantTilt = 1.75f; ease = .06f; }
            else if (target != null && target.isAlive()) {
                Vec3d to = target.getBoundingBox().getCenter().subtract(head());
                wantYaw = (float) Math.atan2(to.x, to.z);
                double length = to.length();
                // At this size it nods toward its target rather than folding over: at most about 65 degrees.
                wantTilt = length < 1e-6 ? 0 : MathHelper.clamp((float) Math.acos(MathHelper.clamp(to.y / length, -1, 1)), 0, 1.15f);
            }
            headYaw += MathHelper.wrapDegrees((wantYaw - headYaw) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE * ease;
            headTilt += (wantTilt - headTilt) * ease;
            int shots = dataTracker.get(SHOTS);
            if (shots != seenShots) { seenShots = shots; shotAge = age; }
            float store = storeShare();
            if (store > seenStore + 1e-4) storeAge = age;
            seenStore = store;
            return;
        }
        // It cannot be moved: whatever shoved it, it stands where it was planted.
        if (root != null && getEntityPos().squaredDistanceTo(root) > 1e-6) refreshPositionAndAngles(root.x, root.y, root.z, getYaw(), 0);
        setVelocity(Vec3d.ZERO);
        // Planted by nothing (a stray summon, a copy gone): it does not stand alone.
        if (owner == null || owner.isRemoved()) discard();
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(world, source, amount);
        if (bloomStage() == WILTING) return false;
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.CRAMMING)
                || source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.FLY_INTO_WALL)) return false;
        Entity attacker = source.getAttacker();
        if (attacker instanceof FirstWizardEntity || attacker == this || source.getSource() instanceof FirstWizardEffectEntity) return false;
        // Only the players the copy's fight admits.
        if (attacker instanceof ServerPlayerEntity player && owner != null && !owner.eligible(player)) return false;
        return super.damage(world, source, amount);
    }

    /** It breaks at once; the spell shows the break. */
    @Override protected void updatePostDeath() {
        if (getEntityWorld() instanceof ServerWorld && !isRemoved()) remove(RemovalReason.KILLED);
    }

    @Override public void takeKnockback(double strength, double x, double z) {}
    @Override public void addVelocity(double x, double y, double z) {}
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(Entity entity) {}
    @Override public void checkDespawn() {}
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean shouldSave() { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.BLOCK_AZALEA_LEAVES_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.BLOCK_AZALEA_LEAVES_BREAK; }
    /** Effects are as tall as the arena's eye line; a far camera must still draw it. */
    @Override public boolean shouldRender(double squaredDistance) { return squaredDistance < 160.0 * 160.0; }
}
