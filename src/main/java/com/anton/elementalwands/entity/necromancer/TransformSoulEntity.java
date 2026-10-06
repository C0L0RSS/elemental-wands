package com.anton.elementalwands.entity.necromancer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * One ghost of the transformation's storm, drawn with the Soul Harvest model: it breaks out of the
 * snapped staff, whirls about him and dives into his face. Its flight is baked into the scene's
 * track, read on both sides by scene time, so it stays in step with every watcher's camera.
 */
public class TransformSoulEntity extends MobEntity implements GeoEntity {
    private static final RawAnimation DRIFT = RawAnimation.begin().thenLoop("animation.harvest_soul.drift");
    private static final TrackedData<Integer> SOUL = DataTracker.registerData(TransformSoulEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> START = DataTracker.registerData(TransformSoulEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Vector3f> CENTRE = DataTracker.registerData(TransformSoulEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Float> FRAME = DataTracker.registerData(TransformSoulEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TransformSoulEntity(EntityType<? extends TransformSoulEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() { return MobEntity.createMobAttributes(); }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SOUL, -1);
        builder.add(START, 0L);
        builder.add(CENTRE, new Vector3f());
        builder.add(FRAME, 0f);
    }

    void script(int soul, long start, Vec3d centre, float yaw) {
        dataTracker.set(SOUL, soul);
        dataTracker.set(START, start);
        dataTracker.set(CENTRE, centre.toVector3f());
        dataTracker.set(FRAME, yaw);
        Vec3d at = at(time(0));
        refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    }

    /** Its flight in the track, or null before the server has scripted it. */
    public NecromancerTransformTrack.Soul soul() {
        int index = dataTracker.get(SOUL);
        var souls = NecromancerTransformTrack.get().souls();
        return index < 0 || index >= souls.size() ? null : souls.get(index);
    }

    /** Ticks into the transformation, on the scene's clock. */
    public double time(float partialTick) { return getEntityWorld().getTime() - dataTracker.get(START) + partialTick; }
    public Vec3d centre() { return new Vec3d(dataTracker.get(CENTRE)); }
    public float frame() { return dataTracker.get(FRAME); }

    /** Its root in the world at scene time t. */
    public Vec3d at(double t) {
        NecromancerTransformTrack.Soul soul = soul();
        return soul == null ? getEntityPos() : NecromancerTransformScene.place(centre(), frame(), NecromancerTransformTrack.soulAt(soul, t));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TransformSoulEntity>("soul", 2, state -> state.setAndContinue(DRIFT)));
    }

    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(net.minecraft.entity.Entity entity) {}
    @Override public boolean isInvulnerableTo(ServerWorld world, DamageSource source) { return true; }
    @Override public boolean canHit() { return false; }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        NecromancerTransformTrack.Soul soul = soul();
        if (soul == null) return;
        double t = time(0);
        Vec3d at = at(t);
        setPosition(at.x, at.y, at.z);
    }
}
