package com.anton.elementalwands.entity;

import java.util.List;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.MovementDisruptManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SingularityBoltEntity extends ProjectileEntity {

    private static final float DIRECT_DAMAGE = 4.0f;
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
            discard();
            return;
        }

        serverWorld.spawnParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 4, 0.08, 0.08, 0.08, 0.03);
        serverWorld.spawnParticles(ParticleTypes.WITCH, getX(), getY(), getZ(), 1, 0.04, 0.04, 0.04, 0.0);

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
            living.damage(world, source, DIRECT_DAMAGE);
        }

        Box box = Box.of(impactPos, IMPACT_RADIUS * 2.0, IMPACT_RADIUS * 2.0, IMPACT_RADIUS * 2.0);
        List<LivingEntity> affected = world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.equals(owner));

        for (LivingEntity living : affected) {
            if (living.squaredDistanceTo(impactPos) > IMPACT_RADIUS * IMPACT_RADIUS) {
                continue;
            }

            pullTowardImpact(world, living, impactPos);
            MovementDisruptManager.applySprintLock(world, living, SPRINT_LOCK_TICKS);
            MovementDisruptManager.disruptMobility(living);

            world.spawnParticles(ParticleTypes.PORTAL, living.getX(), living.getBodyY(0.5), living.getZ(), 10, 0.25,
                    0.4, 0.25, 0.06);
        }

        world.spawnParticles(ParticleTypes.EXPLOSION, impactPos.x, impactPos.y, impactPos.z, 2, 0.15, 0.15, 0.15, 0.0);
        world.spawnParticles(ParticleTypes.PORTAL, impactPos.x, impactPos.y, impactPos.z, 24, 0.4, 0.4, 0.4, 0.15);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.4f);
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
