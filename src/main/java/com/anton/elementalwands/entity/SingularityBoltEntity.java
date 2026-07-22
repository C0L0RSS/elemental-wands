package com.anton.elementalwands.entity;

import java.util.List;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.MovementDisruptManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SingularityBoltEntity extends ProjectileEntity {

    private static final float DIRECT_DAMAGE = 7.0f;
    private static final float SPLASH_DAMAGE = 2.5f;
    private static final double PROJECTILE_SPEED = 0.9;
    private static final int MAX_TRAVEL_DISTANCE = 24;
    private static final double IMPACT_RADIUS = 3.0;
    private static final int SPRINT_LOCK_TICKS = 15;

    private Vec3d startPos;

    public SingularityBoltEntity(EntityType<? extends SingularityBoltEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public SingularityBoltEntity(World world, LivingEntity owner) {
        super(ModEntities.SINGULARITY_BOLT, world);
        setOwner(owner);
        setNoGravity(true);

        Vec3d spawnPos = owner.getEyePos();
        setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        startPos = spawnPos;

        Vec3d direction = owner.getRotationVec(1.0f).normalize();
        setVelocity(direction.multiply(PROJECTILE_SPEED));
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        // No tracked data needed
    }

    @Override
    public void tick() {
        super.tick();

        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (startPos == null) {
            startPos = getEntityPos();
        }

        if (getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
            spawnCollapsedMiss(serverWorld);
            discard();
            return;
        }

        spawnTravelVisuals(serverWorld);

        HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
        if (hitResult.getType() != HitResult.Type.MISS) {
            onCollision(hitResult);
            return;
        }

        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        applyImpact(serverWorld, entityHitResult.getPos(), entityHitResult.getEntity());
        discard();
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult blockHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        applyImpact(serverWorld, blockHitResult.getPos(), null);
        discard();
    }

    private void applyImpact(ServerWorld world, Vec3d impactPos, Entity directHit) {
        Entity owner = getOwner();

        if (directHit instanceof LivingEntity living && !directHit.equals(owner)) {
            DamageSource source = owner instanceof LivingEntity livingOwner
                    ? world.getDamageSources().thrown(this, livingOwner)
                    : world.getDamageSources().magic();
            boolean damaged = living.damage(world, source, DIRECT_DAMAGE);
            if (damaged) {
                com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, DIRECT_DAMAGE);
            }
        }

        Box box = Box.of(impactPos, IMPACT_RADIUS * 2.0, IMPACT_RADIUS * 2.0, IMPACT_RADIUS * 2.0);
        List<LivingEntity> affected = world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.equals(owner));

        for (LivingEntity living : affected) {
            if (living.squaredDistanceTo(impactPos) > IMPACT_RADIUS * IMPACT_RADIUS) {
                continue;
            }

            if (living != directHit) {
                DamageSource splashSource = owner instanceof LivingEntity livingOwner
                        ? world.getDamageSources().thrown(this, livingOwner)
                        : world.getDamageSources().magic();
                boolean damaged = living.damage(world, splashSource, SPLASH_DAMAGE);
                if (damaged) {
                    com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, SPLASH_DAMAGE);
                }
            }

            Vec3d pullOrigin = living.getEntityPos().add(0.0, living.getHeight() * 0.5, 0.0);
            pullTowardImpact(world, living, impactPos);
            MovementDisruptManager.applySprintLock(world, living, SPRINT_LOCK_TICKS);
            MovementDisruptManager.disruptMobility(living);
            spawnPullTether(world, pullOrigin, impactPos);
        }

        world.spawnParticles(ModParticles.SPACE_SINGULARITY,
                impactPos.x, impactPos.y, impactPos.z, 2, 0.05, 0.05, 0.05, 0.0);
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                impactPos.x, impactPos.y, impactPos.z, 3, 0.08, 0.08, 0.08, 0.0);
        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2.0 / 24.0);
            double radius = IMPACT_RADIUS * (0.72 + (i % 3) * 0.11);
            Vec3d point = impactPos.add(Math.cos(angle) * radius,
                    ((i % 5) - 2) * 0.18,
                    Math.sin(angle) * radius);
            Vec3d inward = impactPos.subtract(point).normalize().multiply(0.16);
            spawnDirected(world, ModParticles.SPACE_CONSUMPTION, point, inward);
        }
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.85f, 1.65f);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.55f, 0.65f);
    }

    private void spawnTravelVisuals(ServerWorld world) {
        Vec3d center = getEntityPos();
        Vec3d direction = getVelocity().lengthSquared() > 0.0001
                ? getVelocity().normalize()
                : new Vec3d(0.0, 0.0, 1.0);

        Vec3d velocity = getVelocity();
        spawnDirected(world, ModParticles.SPACE_SINGULARITY, center, velocity);
        if (age % 2 == 0) {
            spawnDirected(world, ModParticles.SPACE_BROKEN_ORBIT, center, velocity);
        }

        for (int i = 0; i < 5; i++) {
            double distance = 0.75 + i * 0.38;
            double phase = age * 0.7 + i * 2.19;
            Vec3d point = center.subtract(direction.multiply(distance))
                    .add(Math.cos(phase) * 0.20, Math.sin(phase * 1.3) * 0.20, Math.sin(phase) * 0.20);
            Vec3d inward = center.subtract(point).normalize().multiply(0.13 + i * 0.012);
            spawnDirected(world, i % 2 == 0 ? ModParticles.SPACE_CONSUMPTION : ModParticles.SPACE_MOTE,
                    point, inward);
        }
    }

    private static void spawnPullTether(ServerWorld world, Vec3d from, Vec3d impactPos) {
        Vec3d delta = impactPos.subtract(from);
        int steps = Math.max(3, Math.min(9, (int) Math.ceil(delta.length() * 2.0)));
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Vec3d point = from.add(delta.multiply(progress));
            Vec3d inward = impactPos.subtract(point);
            if (inward.lengthSquared() > 0.0001) {
                inward = inward.normalize().multiply(0.14);
            }
            spawnDirected(world, ModParticles.SPACE_CONSUMPTION, point, inward);
        }
    }

    private void spawnCollapsedMiss(ServerWorld world) {
        Vec3d center = getEntityPos();
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_PINPOINT,
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void spawnDirected(ServerWorld world, net.minecraft.particle.SimpleParticleType particle,
            Vec3d position, Vec3d velocity) {
        world.spawnParticles(particle,
                position.x, position.y, position.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0);
    }

    private void pullTowardImpact(ServerWorld world, LivingEntity living, Vec3d impactPos) {
        Vec3d toCenter = impactPos.subtract(living.getEntityPos());
        if (toCenter.lengthSquared() < 0.0001) {
            return;
        }

        double distance = toCenter.length();
        double pullDistance = Math.min(2.5, distance);
        Vec3d pullStep = toCenter.normalize().multiply(pullDistance);
        Vec3d from = living.getEntityPos();
        Vec3d to = from.add(pullStep);
        Box movedBox = living.getBoundingBox().offset(to.subtract(from));

        if (world.isSpaceEmpty(living, movedBox)) {
            living.requestTeleport(to.x, to.y, to.z);
            living.setVelocity(0.0, Math.max(living.getVelocity().y, 0.08), 0.0);
            living.velocityModified = true;
            living.fallDistance = 0.0f;
            return;
        }

        Vec3d pullDirection = toCenter.normalize();
        Vec3d current = living.getVelocity().multiply(0.20);
        Vec3d pullVelocity = pullDirection.multiply(1.65);
        living.setVelocity(current.x + pullVelocity.x, Math.max(current.y, 0.0) + 0.12, current.z + pullVelocity.z);
        living.velocityModified = true;
        living.fallDistance = 0.0f;
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !entity.equals(getOwner());
    }
}
