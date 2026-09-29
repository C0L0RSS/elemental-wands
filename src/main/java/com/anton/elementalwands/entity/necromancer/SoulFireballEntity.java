package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.util.SoulGlow;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
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
 * Soul Fire Rain: a blue fireball lobbed from the siege perch. It follows a scripted arc from the
 * staff to its marker, landing exactly as the marker fills, so it passes the crypt's lid and
 * boughs. The server draws the marker; the encounter resolves the impact.
 */
public class SoulFireballEntity extends ProjectileEntity implements GeoEntity {
    private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("animation.soul_fireball.flight");
    private static final TrackedData<Vector3f> ORIGIN = DataTracker.registerData(SoulFireballEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Vector3f> TARGET = DataTracker.registerData(SoulFireballEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean loadedFromSave;

    public SoulFireballEntity(EntityType<? extends SoulFireballEntity> type, World world) { super(type, world); }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<SoulFireballEntity>("flame", 0, state -> state.setAndContinue(FLIGHT)));
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ORIGIN, new Vector3f());
        builder.add(TARGET, new Vector3f());
    }

    @Override protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        loadedFromSave = true; // A reloaded fireball must not land in a stale fight.
    }

    void launch(NecromancerEntity boss, Vec3d from, Vec3d target) {
        setOwner(boss);
        dataTracker.set(ORIGIN, from.toVector3f());
        dataTracker.set(TARGET, target.toVector3f());
        setPosition(from);
    }

    public Vec3d target() { return new Vec3d(dataTracker.get(TARGET)); }

    /** Where the ball is {@code ticks} after launch: a shallow lob that lands on the marker. */
    Vec3d at(double ticks) {
        double s = Math.clamp(ticks / NecromancerRules.RAIN_WARNING, 0, 1);
        Vec3d from = new Vec3d(dataTracker.get(ORIGIN)), to = target();
        return from.lerp(to, s).add(0, NecromancerRules.RAIN_ARC * 4 * s * (1 - s), 0);
    }

    @Override public void tick() {
        super.tick();
        // Same scripted path on both sides; only the server lands it. The path arrives with the spawn.
        if (dataTracker.get(TARGET).lengthSquared() > 0) {
            setPosition(at(age));
            // Nose first along the arc, so the flames stream behind it.
            Vec3d heading = at(age + 1).subtract(at(age));
            if (heading.lengthSquared() > 1e-8) {
                setYaw((float)Math.toDegrees(Math.atan2(-heading.x, heading.z)));
                setPitch((float)-Math.toDegrees(Math.atan2(heading.y, heading.horizontalLength())));
            }
        }
        if (!(getEntityWorld() instanceof ServerWorld world)) { trail(); return; }
        if (loadedFromSave || !(getOwner() instanceof NecromancerEntity boss) || !boss.isAlive()) { discard(); return; }
        Vec3d target = target();
        if (age % 2 == 0) marker(world, target);
        // Real light for the ball and its marker; the marker brightens in steps as the ball closes in.
        SoulGlow.light(world, getEntityPos().add(0, .3, 0), NecromancerRules.GLOW_FIREBALL, NecromancerRules.GLOW_LINGER);
        SoulGlow.light(world, target, NecromancerRules.rainGlow(age), NecromancerRules.GLOW_LINGER);
        // A thin trail sent to everyone in the clearing; each client adds the dense flames up close.
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, getX(), getY() + .3, getZ(), 1, .15, .15, .15, .01);
        if (age >= NecromancerRules.RAIN_WARNING) {
            boss.combat().rainLanded(world, this, target);
            discard();
        }
    }

    /** Client flames licking off the ball and streaming behind it, with smoke and loose souls. */
    private void trail() {
        if (dataTracker.get(TARGET).lengthSquared() == 0) return;
        var random = getRandom();
        Vec3d centre = getEntityPos().add(0, .3, 0), back = at(age).subtract(at(age + 1)).normalize().multiply(.08);
        for (int i = 0; i < 4; i++)
            getEntityWorld().addParticleClient(ParticleTypes.SOUL_FIRE_FLAME,
                    centre.x + (random.nextDouble() - .5) * .6, centre.y + (random.nextDouble() - .5) * .6, centre.z + (random.nextDouble() - .5) * .6,
                    back.x + (random.nextDouble() - .5) * .03, back.y + .015, back.z + (random.nextDouble() - .5) * .03);
        if (age % 2 == 0)
            getEntityWorld().addParticleClient(ParticleTypes.LARGE_SMOKE, centre.x + back.x * 4, centre.y + back.y * 4 + .1, centre.z + back.z * 4, back.x * .3, .02, back.z * .3);
        if (random.nextInt(3) == 0)
            getEntityWorld().addParticleClient(ParticleTypes.SOUL, centre.x, centre.y, centre.z, back.x * .5, .03, back.z * .5);
    }

    /** A soul-fire ring the size of the blast, with an inner ring that fills it as the ball falls. */
    private void marker(ServerWorld world, Vec3d center) {
        double fill = Math.min(1, age / (double)NecromancerRules.RAIN_WARNING), radius = NecromancerRules.RAIN_RADIUS;
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, center.x + Math.cos(angle) * radius, center.y + .08,
                    center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
        int inner = Math.max(4, (int)Math.round(14 * fill));
        for (int i = 0; i < inner; i++) {
            double angle = i * Math.PI * 2 / inner + age * .2;
            world.spawnParticles(ParticleTypes.SOUL, true, false, center.x + Math.cos(angle) * radius * fill, center.y + .1,
                    center.z + Math.sin(angle) * radius * fill, 1, 0, 0, 0, 0);
        }
    }
}
