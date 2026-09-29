package com.anton.elementalwands.entity.necromancer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import com.anton.elementalwands.util.SoulGlow;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
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
 * The zombie's soul in the intro cinematic, drawn with the Soul Harvest model: torn out of the
 * body and dragged into the Necromancer's staff. Its path is a function of world time on both
 * sides, so it stays in step with every watcher's camera.
 */
public class IntroSoulEntity extends MobEntity implements GeoEntity {
    private static final RawAnimation TORN_OUT = RawAnimation.begin().thenPlayAndHold("animation.harvest_soul.torn_out");
    private static final RawAnimation DRAGGED = RawAnimation.begin().thenLoop("animation.harvest_soul.dragged");
    /** Ticks it spends coming out of the chest, and turning back to look at the body once it rips free. */
    public static final double TEAR = NecromancerIntro.FREE - NecromancerIntro.PULL, LOOK_BACK = 6;
    /** Its model's middle sits this far above its feet; the path traces the middle. */
    private static final double MIDDLE = .45;
    private static final TrackedData<Vector3f> ORIGIN = DataTracker.registerData(IntroSoulEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Vector3f> TARGET = DataTracker.registerData(IntroSoulEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Long> START = DataTracker.registerData(IntroSoulEntity.class, TrackedDataHandlerRegistry.LONG);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public IntroSoulEntity(EntityType<? extends IntroSoulEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() { return MobEntity.createMobAttributes(); }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ORIGIN, new Vector3f());
        builder.add(TARGET, new Vector3f());
        builder.add(START, 0L);
    }

    void script(Vec3d from, Vec3d to, long start) {
        dataTracker.set(ORIGIN, from.toVector3f());
        dataTracker.set(TARGET, to.toVector3f());
        dataTracker.set(START, start);
        Vec3d at = at(0);
        refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    }

    /** Where the soul is {@code age} ticks into its script. */
    public Vec3d at(double age) { return NecromancerIntro.tornAt(new Vec3d(dataTracker.get(ORIGIN)), new Vec3d(dataTracker.get(TARGET)), age); }

    /** Ticks since it started to come out, on the scene's clock. */
    public double age(float partialTick) { return getEntityWorld().getTime() - dataTracker.get(START) + partialTick; }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<IntroSoulEntity>("soul", 2,
                state -> state.setAndContinue(state.animatable().age(0) < TEAR ? TORN_OUT : DRAGGED)));
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
        if (dataTracker.get(TARGET).lengthSquared() == 0) return;
        double age = age(0);
        Vec3d at = at(age), ahead = at(age + 1), pull = new Vec3d(dataTracker.get(TARGET)).subtract(new Vec3d(dataTracker.get(ORIGIN)));
        setPosition(at.x, at.y - MIDDLE, at.z);
        // Face first out of the chest; once it rips free it turns to look back at its body and is
        // dragged tail first into the staff.
        float out = (float)Math.toDegrees(Math.atan2(-pull.x, pull.z)), back = out + 180;
        Vec3d flight = at.subtract(ahead);
        if (flight.horizontalLengthSquared() > 1e-6 && age >= TEAR) back = (float)Math.toDegrees(Math.atan2(-flight.x, flight.z));
        float yaw = MathHelper.lerpAngleDegrees((float)NecromancerIntro.smooth((age - TEAR) / LOOK_BACK), out, back);
        setYaw(yaw); setBodyYaw(yaw); setHeadYaw(yaw);
        // Real light as it passes, all the way to the staff.
        if (getEntityWorld() instanceof ServerWorld world)
            SoulGlow.light(world, getBoundingBox().getCenter(), NecromancerRules.GLOW_HARVEST, NecromancerRules.GLOW_LINGER);
        if (getEntityWorld().isClient() && age % 2 == 0)
            getEntityWorld().addParticleClient(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + .35, getZ(), 0, .01, 0);
    }
}
