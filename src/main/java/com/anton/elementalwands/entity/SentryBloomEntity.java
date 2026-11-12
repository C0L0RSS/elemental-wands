package com.anton.elementalwands.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The head of a Sentry Bloom flower: the part that turns. The flower's base is a block; this
 * entity only tells clients what the flower is looking at, when it fired and when it is
 * wilting, so they can draw the bloom turned toward its target. It cannot be hit, pushed or
 * damaged, and it is never saved: {@code FlowerTurretManager} owns it with the flower.
 */
public final class SentryBloomEntity extends Entity {
    /** Top of the stem, where the head turns, in blocks above the flower's ground. */
    public static final float PIVOT_HEIGHT = 0.6875f;
    /** How far along the head's axis its loaded seed sits; seeds leave from here. */
    public static final float MUZZLE_OFFSET = 0.53125f;
    /** The head never bends further than this from straight up: 105 degrees, where it still clears its own leaves. */
    public static final float MAX_TILT = 1.83f;
    private static final float WILT_TILT = 1.8f;
    /**
     * A grown flower: the main stem carries the first head high, and a thinner branch off it
     * carries a smaller second head lower and to one side. Each is the head's pivot relative
     * to the flower's bottom centre, in the block model's frame, and the head's size there.
     */
    public static final Vec3d MAIN_STEM_TOP = new Vec3d(0.3125, 1.5, 0), BRANCH_TOP = new Vec3d(-0.6875, 1.125, 0.125);
    public static final float MAIN_SCALE = 1.0f, BRANCH_SCALE = 0.75f;
    private static final int RECOIL_TICKS = 5;
    private static final int GROW_TICKS = 12;

    private static final TrackedData<Integer> TARGET = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> SHOTS = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WILTING = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    // Where on its flower this head is mounted: one head sits on the stem, a grown flower's on its stem and its branch.
    private static final TrackedData<Float> OFFSET = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> PIVOT = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> OFFSET_Z = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SCALE = DataTracker.registerData(SentryBloomEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // Client pose, eased toward the target each tick.
    private float headYaw, headTilt, lastHeadYaw, lastHeadTilt;
    private int seenShots, shotAge = -100;

    public SentryBloomEntity(EntityType<? extends SentryBloomEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
    }

    public void aim(Entity target) { dataTracker.set(TARGET, target == null ? -1 : target.getId()); }
    public void fired() { dataTracker.set(SHOTS, dataTracker.get(SHOTS) + 1); }
    public void wilt() { dataTracker.set(WILTING, true); }
    public int targetId() { return dataTracker.get(TARGET); }
    public int shots() { return dataTracker.get(SHOTS); }
    public boolean wilting() { return dataTracker.get(WILTING); }

    /** Moves this head's pivot (relative to the flower's bottom centre) and sets its size. */
    public void mount(Vec3d at, float scale) {
        dataTracker.set(OFFSET, (float) at.x); dataTracker.set(PIVOT, (float) at.y); dataTracker.set(OFFSET_Z, (float) at.z);
        dataTracker.set(SCALE, scale);
    }
    /** Back on the single stem top, at full size. */
    public void mountSingle() { mount(new Vec3d(0, PIVOT_HEIGHT, 0), 1); }
    public float offsetX() { return dataTracker.get(OFFSET); }
    public float offsetZ() { return dataTracker.get(OFFSET_Z); }
    public float pivotHeight() { return dataTracker.get(PIVOT); }
    public float scale() { return dataTracker.get(SCALE); }
    public Vec3d mountPoint() { return new Vec3d(offsetX(), pivotHeight(), offsetZ()); }

    public Vec3d pivot() { return getEntityPos().add(offsetX(), pivotHeight(), offsetZ()); }

    /** Yaw and tilt (radians from straight up) that point the head's axis along this direction. */
    public static float yawOf(Vec3d direction) { return (float) Math.atan2(direction.x, direction.z); }
    public static float tiltOf(Vec3d direction) {
        double length = direction.length();
        return length < 1.0e-6 ? 0 : MathHelper.clamp((float) Math.acos(MathHelper.clamp(direction.y / length, -1, 1)), 0, MAX_TILT);
    }

    public float headYaw(float delta) { return lastHeadYaw + MathHelper.wrapDegrees((headYaw - lastHeadYaw) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE * delta; }
    public float headTilt(float delta) { return MathHelper.lerp(delta, lastHeadTilt, headTilt); }
    /** 1 at the moment of a shot, easing to 0. */
    public float recoil(float delta) { return MathHelper.clamp(1 - (age + delta - shotAge) / RECOIL_TICKS, 0, 1); }
    /** 0 to 1 over the half second the flower takes to unfurl. */
    public float grown(float delta) { return MathHelper.clamp((age + delta) / GROW_TICKS, 0, 1); }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TARGET, -1); builder.add(SHOTS, 0); builder.add(WILTING, false);
        builder.add(OFFSET, 0f); builder.add(PIVOT, PIVOT_HEIGHT); builder.add(OFFSET_Z, 0f); builder.add(SCALE, 1f);
    }

    @Override public void tick() {
        super.tick();
        if (!getEntityWorld().isClient()) return;
        lastHeadYaw = headYaw; lastHeadTilt = headTilt;
        float wantYaw = headYaw, wantTilt = 0, ease = 0.35f;
        Entity target = getEntityWorld().getEntityById(targetId());
        if (wilting()) { wantTilt = WILT_TILT; ease = 0.07f; }
        else if (target != null && target.isAlive()) {
            Vec3d direction = target.getBoundingBox().getCenter().subtract(pivot());
            wantYaw = yawOf(direction); wantTilt = tiltOf(direction);
        }
        headYaw += MathHelper.wrapDegrees((wantYaw - headYaw) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE * ease;
        headTilt += (wantTilt - headTilt) * ease;
        if (shots() != seenShots) { seenShots = shots(); shotAge = age; }
    }

    @Override protected void readCustomData(ReadView view) {}
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean isCollidable(Entity other) { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
