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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The zombie in the Guardian's intro: a vanilla zombie, split at the waist, that the Guardian
 * smashes, tears in two and throws away. It stands at the Guardian's seat and faces the way the
 * Guardian does, so its clip (authored with the Guardian's, in the same frame) walks it, lays it
 * under the fists and puts each half in a fist. It is only drawn from the scene's first tick, when
 * its clip and the Guardian's both start, and it is never saved.
 */
public class GuardianIntroZombieEntity extends Entity implements GeoEntity {
    private static final RawAnimation INTRO = RawAnimation.begin().thenPlayAndHold("animation.guardian_intro_zombie.intro");
    private static final TrackedData<Long> START = DataTracker.registerData(GuardianIntroZombieEntity.class, TrackedDataHandlerRegistry.LONG);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GuardianIntroZombieEntity(EntityType<? extends GuardianIntroZombieEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(START, -1L); }

    /** Stands it at the seat, facing as the Guardian does, for a scene that starts at world time {@code start}. */
    void script(Vec3d seat, float yaw, long start) {
        refreshPositionAndAngles(seat.x, seat.y, seat.z, yaw, 0);
        dataTracker.set(START, start);
    }

    void syncStart(long start) { dataTracker.set(START, start); }

    /** Ticks into the scene, negative before it starts. */
    public double sceneTime(float partialTick) {
        long start = dataTracker.get(START);
        return start < 0 ? -1 : getEntityWorld().getTime() - start + partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        // The scene discards it; this only guards against an intro that vanished without cleaning up.
        if (getEntityWorld() instanceof ServerWorld && sceneTime(0) > GuardianIntro.LENGTH + 40) discard();
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<GuardianIntroZombieEntity>("intro", 0, state -> state.setAndContinue(INTRO)));
    }

    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
    @Override protected void readCustomData(ReadView view) {}
    @Override protected void writeCustomData(WriteView view) {}
}
