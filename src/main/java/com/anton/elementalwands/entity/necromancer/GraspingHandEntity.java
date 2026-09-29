package com.anton.elementalwands.entity.necromancer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Harmless, unsaved visual. The encounter controller owns the marked area and damage. */
public final class GraspingHandEntity extends ProjectileEntity implements GeoEntity {
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(GraspingHandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> SCALE = DataTracker.registerData(GraspingHandEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> MIRROR = DataTracker.registerData(GraspingHandEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation GRASP = RawAnimation.begin().thenPlayAndHold("animation.grasping_hand.grasp");
    public GraspingHandEntity(EntityType<? extends GraspingHandEntity> type, World world) { super(type, world); }
    @Override protected void initDataTracker(DataTracker.Builder b) { b.add(VARIANT, 0); b.add(SCALE, 1f); b.add(MIRROR, false); }
    void setup(NecromancerEntity owner, Vec3d at, float yaw, int variant, float scale, boolean mirror) {
        setOwner(owner); setPosition(at); setYaw(yaw); setNoGravity(true);
        dataTracker.set(VARIANT, variant); dataTracker.set(SCALE, scale); dataTracker.set(MIRROR, mirror);
    }
    public int variant() { return dataTracker.get(VARIANT); }
    public float scale() { return dataTracker.get(SCALE); }
    public boolean mirror() { return dataTracker.get(MIRROR); }
    @Override public void tick() {
        super.tick();
        if (getEntityWorld() instanceof ServerWorld && (age >= 84 || getOwner() == null || !getOwner().isAlive())) discard();
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<GraspingHandEntity>("grasp", 0, state -> state.setAndContinue(GRASP)));
    }
}
