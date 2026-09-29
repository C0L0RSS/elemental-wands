package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * The Guardian's heart in the intro cinematic: it trembles in the caller's hand, floats up and
 * flies into the kneeling Guardian's core. Its path is a function of world time on both sides, so
 * it stays in step with every watcher's camera.
 */
public class IntroHeartEntity extends Entity {
    public static final ItemStack LOOK = new ItemStack(ModItems.GUARDIAN_HEART);
    private static final DustParticleEffect TRAIL = new DustParticleEffect(0x6AF2FF, 1.1f);
    private static final TrackedData<Vector3f> ORIGIN = DataTracker.registerData(IntroHeartEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Vector3f> TARGET = DataTracker.registerData(IntroHeartEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Long> START = DataTracker.registerData(IntroHeartEntity.class, TrackedDataHandlerRegistry.LONG);

    public IntroHeartEntity(EntityType<? extends IntroHeartEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(ORIGIN, new Vector3f());
        builder.add(TARGET, new Vector3f());
        builder.add(START, 0L);
    }

    void script(Vec3d hand, Vec3d core, long start) {
        dataTracker.set(ORIGIN, hand.toVector3f());
        dataTracker.set(TARGET, core.toVector3f());
        dataTracker.set(START, start);
        Vec3d at = at(0);
        refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    }

    /** Where the heart's middle is {@code age} ticks into the scene. */
    public Vec3d at(double age) {
        return GuardianIntro.heartAt(new Vec3d(dataTracker.get(ORIGIN)), new Vec3d(dataTracker.get(TARGET)), age);
    }

    /** Ticks since the scene began, on its clock. */
    public double age(float partialTick) { return getEntityWorld().getTime() - dataTracker.get(START) + partialTick; }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (dataTracker.get(TARGET).lengthSquared() == 0) return;
        double age = age(0);
        Vec3d at = at(age);
        setPosition(at.x, at.y - getHeight() / 2, at.z);
        if (!getEntityWorld().isClient()) return;
        // A glittering wake behind it in flight; a few sparks while it shakes in the hand.
        if (age >= GuardianIntro.LAUNCH) {
            Vec3d back = at(age - .5);
            for (int i = 0; i < 3; i++) {
                Vec3d p = back.lerp(at, i / 3.0);
                getEntityWorld().addParticleClient(TRAIL, p.x, p.y, p.z, 0, 0, 0);
            }
            if (age % 2 == 0) getEntityWorld().addParticleClient(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, 0, 0);
        } else if (random.nextDouble() < .15 + .5 * GuardianIntro.vibration(age)) {
            getEntityWorld().addParticleClient(ParticleTypes.GLOW, at.x + random.nextGaussian() * .08, at.y + .1, at.z + random.nextGaussian() * .08,
                    0, .02, 0);
        }
    }

    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canHit() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
    @Override protected void readCustomData(ReadView view) {}
    @Override protected void writeCustomData(WriteView view) {}
}
