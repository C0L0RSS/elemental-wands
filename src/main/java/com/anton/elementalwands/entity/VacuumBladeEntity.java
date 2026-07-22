package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class VacuumBladeEntity extends ProjectileEntity {

    private static final TrackedData<Boolean> MIRRORED = DataTracker.registerData(
            VacuumBladeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final float MAX_DAMAGE = 7.0f;
    private static final float MIN_DAMAGE = 4.0f;
    private static final double PROJECTILE_SPEED = 2.5;
    private static final int MAX_TRAVEL_DISTANCE = 20;

    private Vec3d startPos;

    public VacuumBladeEntity(EntityType<? extends VacuumBladeEntity> type, World world) {
        super(type, world);
    }

    public VacuumBladeEntity(World world, LivingEntity owner, Vec3d offset, boolean mirrored) {
        super(ModEntities.VACUUM_BLADE, world);
        setOwner(owner);
        dataTracker.set(MIRRORED, mirrored);

        // Start position with offset (for dual blades)
        Vec3d spawnPos = owner.getEyePos().add(offset);
        setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        this.startPos = spawnPos;

        // Set velocity in owner's facing direction
        Vec3d direction = owner.getRotationVec(1.0f).normalize();
        setVelocity(direction.multiply(PROJECTILE_SPEED));
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        builder.add(MIRRORED, false);
    }

    public boolean isMirrored() {
        return dataTracker.get(MIRRORED);
    }

    @Override
    public void tick() {
        super.tick();

        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            // Check if traveled too far
            if (startPos != null && getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
                discard();
                return;
            }

            Vec3d segmentStart = getEntityPos();
            Vec3d segmentEnd = segmentStart.add(getVelocity());
            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            if (hitResult.getType() != HitResult.Type.MISS) {
                spawnInterpolatedWake(serverWorld, segmentStart, hitResult.getPos());
                onCollision(hitResult);
                return;
            }

            // At 2.5 blocks per tick, spawning only at the entity position leaves
            // obvious gaps. Sample the complete motion segment so each talon has a
            // continuous, velocity-readable wake without changing its movement.
            spawnInterpolatedWake(serverWorld, segmentStart, segmentEnd);
        }

        // Update position
        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Entity target = entityHitResult.getEntity();
        Entity owner = getOwner();

        // Deal damage with linear falloff: MAX_DAMAGE at point-blank, MIN_DAMAGE at MAX_TRAVEL_DISTANCE
        double traveled = startPos != null ? getEntityPos().distanceTo(startPos) : 0.0;
        float t = (float) Math.min(1.0, traveled / MAX_TRAVEL_DISTANCE);
        float damage = MAX_DAMAGE + (MIN_DAMAGE - MAX_DAMAGE) * t;

        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? serverWorld.getDamageSources().thrown(this, livingOwner)
                : serverWorld.getDamageSources().generic();

        boolean damaged = target.damage(serverWorld, source, damage);
        if (damaged) {
            com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, damage);
        }

        // Apply weak knockback away from caster/projectile direction
        Vec3d knockbackDir = this.getVelocity().normalize();
        target.addVelocity(
                knockbackDir.x * 0.5,
                0.2,
                knockbackDir.z * 0.5);
        target.velocityModified = true;

        spawnImpact(serverWorld, entityHitResult.getPos());

        serverWorld.playSound(
                null,
                target.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS,
                0.5f, 1.5f);

        // Despawn after hitting entity (doesn't pierce)
        discard();
    }

    @Override
    protected boolean canHit(Entity entity) {
        // Don't hit owner
        return super.canHit(entity) && !entity.equals(getOwner());
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult blockHitResult) {
        // Despawn on block collision
        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            spawnImpact(serverWorld, blockHitResult.getPos());
        }
        discard();
    }

    private void spawnInterpolatedWake(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 1.0e-4) {
            return;
        }

        Vec3d direction = delta.normalize();
        Vec3d lateral = horizontalPerpendicular(direction);
        int samples = Math.max(1, Math.min(12, (int) Math.ceil(distance / 0.32)));
        double side = isMirrored() ? -1.0 : 1.0;

        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            Vec3d point = from.add(delta.multiply(progress));
            Vec3d wakeVelocity = direction.multiply(-0.055);
            spawnDirected(world, ModParticles.WIND_SLIPSTREAM, point, wakeVelocity);

            if (((sample + age) & 1) == 0) {
                Vec3d featherPoint = point.add(lateral.multiply(side * 0.11));
                Vec3d featherVelocity = wakeVelocity.add(lateral.multiply(side * 0.035));
                spawnDirected(world, ModParticles.WIND_SHEAR_FEATHER, featherPoint, featherVelocity);
            }
            if ((sample + age) % 3 == 0) {
                spawnDirected(world, ModParticles.WIND_MOTE, point, wakeVelocity.multiply(0.45));
            }
        }
    }

    private void spawnImpact(ServerWorld world, Vec3d impactPos) {
        world.spawnParticles(ModParticles.WIND_BURST_RING,
                impactPos.x, impactPos.y, impactPos.z,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.WIND_MOTE,
                impactPos.x, impactPos.y, impactPos.z,
                8, 0.28, 0.28, 0.28, 0.06);

        Vec3d forward = getVelocity().lengthSquared() > 1.0e-4
                ? getVelocity().normalize()
                : new Vec3d(0.0, 0.0, 1.0);
        Vec3d right = horizontalPerpendicular(forward);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            Vec3d radial = right.multiply(Math.cos(angle))
                    .add(new Vec3d(0.0, Math.sin(angle), 0.0))
                    .normalize();
            spawnDirected(world, ModParticles.WIND_SHEAR_FEATHER,
                    impactPos.add(radial.multiply(0.08)),
                    radial.multiply(0.10).add(forward.multiply(0.06)));
        }
    }

    private static Vec3d horizontalPerpendicular(Vec3d direction) {
        Vec3d lateral = new Vec3d(-direction.z, 0.0, direction.x);
        return lateral.lengthSquared() > 1.0e-4
                ? lateral.normalize()
                : new Vec3d(1.0, 0.0, 0.0);
    }

    private static void spawnDirected(ServerWorld world,
            net.minecraft.particle.SimpleParticleType particle, Vec3d position, Vec3d velocity) {
        world.spawnParticles(particle,
                position.x, position.y, position.z,
                0, velocity.x, velocity.y, velocity.z, 1.0);
    }
}
