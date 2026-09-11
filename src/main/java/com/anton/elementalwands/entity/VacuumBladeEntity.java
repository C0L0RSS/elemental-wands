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

    private static final double PROJECTILE_SPEED = 2.5;
    private static final double MAX_TRAVEL_DISTANCE = WindFanRules.RANGE;
    private java.util.Set<java.util.UUID> castHits = new java.util.HashSet<>();

    private Vec3d startPos;

    public VacuumBladeEntity(EntityType<? extends VacuumBladeEntity> type, World world) {
        super(type, world);
    }

    public VacuumBladeEntity(World world, LivingEntity owner, Vec3d direction, boolean mirrored,
            java.util.Set<java.util.UUID> castHits) {
        super(ModEntities.VACUUM_BLADE, world);
        setOwner(owner); this.castHits=castHits;
        dataTracker.set(MIRRORED, mirrored);
        startPos = owner.getEyePos(); setPosition(startPos);
        setVelocity(direction.normalize().multiply(PROJECTILE_SPEED));
    }

    @Override public boolean shouldSave() { return false; }

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
        if (startPos == null) startPos = getEntityPos();
        double remaining = MAX_TRAVEL_DISTANCE-startPos.distanceTo(getEntityPos());
        if (remaining <= 1e-6 || age>20) { discard(); return; }
        Vec3d step=getVelocity();
        if (step.length()>remaining) step=step.normalize().multiply(remaining);
        setVelocity(step); // Collision and movement share the clipped final segment.
        if (getEntityWorld() instanceof ServerWorld world) {
            Vec3d start=getEntityPos();
            HitResult hit=ProjectileUtil.getCollision(this,this::canHit);
            if (hit.getType()!=HitResult.Type.MISS) {
                setPosition(hit.getPos());
                spawnInterpolatedWake(world,start,hit.getPos()); onCollision(hit); return;
            }
            spawnInterpolatedWake(world,start,start.add(step));
        }
        setPosition(getEntityPos().add(step));
        if (remaining <= step.length()+1e-6) discard();
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
        if (!castHits.add(target.getUuid())) { discard(); return; }
        float damage = WindFanRules.damage(traveled);

        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? serverWorld.getDamageSources().thrown(this, livingOwner)
                : serverWorld.getDamageSources().generic();

        boolean damaged = target.damage(serverWorld, source, damage);
        if (damaged) {
            com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, damage);
        }

        // Respect resistance and rejected damage. A resistant boss must never bank upward impulses.
        if (damaged && target instanceof LivingEntity living) {
            Vec3d direction=getVelocity().normalize();
            living.takeKnockback(.5,-direction.x,-direction.z);
        }

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
        int samples = Math.max(1, Math.min(4, (int) Math.ceil(distance / .7)));
        double side = isMirrored() ? -1.0 : 1.0;

        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            Vec3d point = from.add(delta.multiply(progress));
            Vec3d wakeVelocity = direction.multiply(-0.025);
            spawnDirected(world, ModParticles.WIND_SLIPSTREAM, point, wakeVelocity);

            if (sample == samples && age % 2 == 0) {
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
