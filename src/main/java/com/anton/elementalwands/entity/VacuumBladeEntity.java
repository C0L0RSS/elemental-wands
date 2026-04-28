package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModEntities;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class VacuumBladeEntity extends ProjectileEntity {

    private static final float DAMAGE = 5.0f;
    private static final double PROJECTILE_SPEED = 2.5;
    private static final int MAX_TRAVEL_DISTANCE = 5;

    private Vec3d startPos;

    public VacuumBladeEntity(EntityType<? extends VacuumBladeEntity> type, World world) {
        super(type, world);
    }

    public VacuumBladeEntity(World world, LivingEntity owner, Vec3d offset) {
        super(ModEntities.VACUUM_BLADE, world);
        setOwner(owner);

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
        // Required override - no custom data to track
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

            // Spawn wind particles
            serverWorld.spawnParticles(
                    ParticleTypes.CLOUD,
                    getX(), getY(), getZ(),
                    4, 0.2, 0.2, 0.2, 0.01);
            serverWorld.spawnParticles(
                    ParticleTypes.GUST,
                    getX(), getY(), getZ(),
                    2, 0.1, 0.1, 0.1, 0.0);

            // Check for entity collisions
            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);

            if (hitResult.getType() != HitResult.Type.MISS) {
                onCollision(hitResult);
            }
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

        // Deal damage
        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? serverWorld.getDamageSources().thrown(this, livingOwner)
                : serverWorld.getDamageSources().generic();

        boolean damaged = target.damage(serverWorld, source, DAMAGE);
        if (damaged) {
            com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, DAMAGE);
        }

        // Apply weak knockback away from caster/projectile direction
        Vec3d knockbackDir = this.getVelocity().normalize();
        target.addVelocity(
                knockbackDir.x * 0.5,
                0.2,
                knockbackDir.z * 0.5);
        target.velocityModified = true;

        // Spawn impact particles
        serverWorld.spawnParticles(
                ParticleTypes.CLOUD,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                12, 0.3, 0.3, 0.3, 0.05);

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
            serverWorld.spawnParticles(
                    ParticleTypes.CLOUD,
                    getX(), getY(), getZ(),
                    8, 0.2, 0.2, 0.2, 0.03);
        }
        discard();
    }
}
