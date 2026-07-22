package com.anton.elementalwands.entity;

import java.util.List;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class SingularityBoltEntity extends ProjectileEntity {

    private static final float DIRECT_DAMAGE = 7.0f;
    private static final float SPLASH_DAMAGE = 2.5f;
    private static final double PROJECTILE_SPEED = 0.9;
    private static final int MAX_TRAVEL_DISTANCE = 24;
    private static final double IMPACT_RADIUS = 3.0;

    private static final double GUIDANCE_ACQUISITION_RANGE = 16.0;
    private static final double GUIDANCE_ACQUISITION_ALIGNMENT = Math.cos(Math.toRadians(12.0));
    private static final double GUIDANCE_LEASH_ALIGNMENT = Math.cos(Math.toRadians(30.0));
    private static final double GUIDANCE_TURN_RADIANS = Math.toRadians(1.0);
    private static final double GUIDANCE_TOTAL_RADIANS = Math.toRadians(16.0);

    private Vec3d startPos;
    private Vec3d launchDirection;
    private LivingEntity guidanceTarget;
    private boolean guidanceAttempted;
    private boolean guidanceFinished;

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
        launchDirection = direction;
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
        if (launchDirection == null || launchDirection.lengthSquared() < 0.0001) {
            launchDirection = getVelocity().lengthSquared() > 0.0001
                    ? getVelocity().normalize()
                    : new Vec3d(0.0, 0.0, 1.0);
        }

        if (getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
            spawnCollapsedMiss(serverWorld);
            discard();
            return;
        }

        applyCappedGuidance(serverWorld);
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
            boolean damaged = damageWithoutKnockback(world, living, source, DIRECT_DAMAGE);
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
                boolean damaged = damageWithoutKnockback(world, living, splashSource, SPLASH_DAMAGE);
                if (damaged) {
                    com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, SPLASH_DAMAGE);
                    world.spawnParticles(ModParticles.SPACE_PINPOINT,
                            living.getX(), living.getBodyY(0.5), living.getZ(),
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }

        world.spawnParticles(ModParticles.SPACE_SINGULARITY,
                impactPos.x, impactPos.y, impactPos.z, 1, 0.02, 0.02, 0.02, 0.0);
        world.spawnParticles(ModParticles.SPACE_PINPOINT,
                impactPos.x, impactPos.y, impactPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_EXPANSION_RING,
                impactPos.x, impactPos.y, impactPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0 / 16.0);
            double vertical = ((i % 5) - 2) * 0.12;
            Vec3d outward = new Vec3d(Math.cos(angle), vertical, Math.sin(angle)).normalize();
            Vec3d point = impactPos.add(outward.multiply(0.18));
            double speed = 0.18 + (i % 4) * 0.025;
            spawnDirected(world, ModParticles.SPACE_MOTE, point, outward.multiply(speed));
        }
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.82f, 1.35f);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                SoundEvents.ENTITY_SHULKER_BULLET_HIT, SoundCategory.PLAYERS, 0.72f, 1.12f);
    }

    private static boolean damageWithoutKnockback(
            ServerWorld world, LivingEntity target, DamageSource source, float amount) {
        Vec3d velocityBeforeDamage = target.getVelocity();
        boolean damaged = target.damage(world, source, amount);
        if (target.getVelocity().squaredDistanceTo(velocityBeforeDamage) > 1.0e-12) {
            // Projectile damage can apply vanilla knockback even when a shield blocks
            // all damage. Restore the exact incoming motion while preserving attribution.
            target.setVelocity(velocityBeforeDamage);
            target.velocityModified = true;
        }
        return damaged;
    }

    private void applyCappedGuidance(ServerWorld world) {
        if (!guidanceAttempted) {
            guidanceAttempted = true;
            guidanceTarget = acquireGuidanceTarget(world);
            guidanceFinished = guidanceTarget == null;
        }
        if (guidanceFinished || guidanceTarget == null) {
            return;
        }

        if (!isCombatTarget(world, guidanceTarget)) {
            finishGuidance();
            return;
        }

        Vec3d targetCenter = bodyCenter(guidanceTarget);
        Vec3d toTarget = targetCenter.subtract(getEntityPos());
        if (toTarget.lengthSquared() < 0.0001) {
            return;
        }

        Vec3d desiredDirection = toTarget.normalize();
        Vec3d currentDirection = getVelocity().lengthSquared() > 0.0001
                ? getVelocity().normalize()
                : launchDirection;
        if (currentDirection.dotProduct(desiredDirection) <= 0.0
                || launchDirection.dotProduct(desiredDirection) < GUIDANCE_LEASH_ALIGNMENT
                || !hasClearLineOfSight(world, targetCenter)) {
            finishGuidance();
            return;
        }

        Vec3d steeredDirection = rotateToward(currentDirection, desiredDirection, GUIDANCE_TURN_RADIANS);
        double totalAngle = angleBetween(launchDirection, steeredDirection);
        if (totalAngle > GUIDANCE_TOTAL_RADIANS) {
            steeredDirection = rotateToward(launchDirection, steeredDirection, GUIDANCE_TOTAL_RADIANS);
        }
        setVelocity(steeredDirection.multiply(PROJECTILE_SPEED));
    }

    private LivingEntity acquireGuidanceTarget(ServerWorld world) {
        Entity owner = getOwner();
        Box searchBox = getBoundingBox().expand(GUIDANCE_ACQUISITION_RANGE);
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
                target -> target != owner && isCombatTarget(world, target) && canHit(target));

        LivingEntity best = null;
        double bestAlignment = -1.0;
        double bestDistanceSquared = Double.MAX_VALUE;
        int bestEntityId = Integer.MAX_VALUE;
        double rangeSquared = GUIDANCE_ACQUISITION_RANGE * GUIDANCE_ACQUISITION_RANGE;

        for (LivingEntity candidate : candidates) {
            Vec3d targetCenter = bodyCenter(candidate);
            Vec3d toTarget = targetCenter.subtract(getEntityPos());
            double distanceSquared = toTarget.lengthSquared();
            if (distanceSquared < 0.0001 || distanceSquared > rangeSquared) {
                continue;
            }

            double alignment = launchDirection.dotProduct(toTarget.normalize());
            if (alignment < GUIDANCE_ACQUISITION_ALIGNMENT || !hasClearLineOfSight(world, targetCenter)) {
                continue;
            }

            boolean betterAngle = alignment > bestAlignment + 1.0e-7;
            boolean sameAngle = Math.abs(alignment - bestAlignment) <= 1.0e-7;
            boolean betterDistance = distanceSquared < bestDistanceSquared - 1.0e-7;
            boolean sameDistance = Math.abs(distanceSquared - bestDistanceSquared) <= 1.0e-7;
            if (betterAngle
                    || (sameAngle && betterDistance)
                    || (sameAngle && sameDistance && candidate.getId() < bestEntityId)) {
                best = candidate;
                bestAlignment = alignment;
                bestDistanceSquared = distanceSquared;
                bestEntityId = candidate.getId();
            }
        }

        return best;
    }

    private boolean isCombatTarget(ServerWorld world, LivingEntity target) {
        if (target.getEntityWorld() != world || target.isRemoved()
                || !target.isAlive() || target.isSpectator() || target == getOwner()) {
            return false;
        }

        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            return false;
        }

        if (target instanceof TameableEntity tameable && tameable.isOwner(livingOwner)) {
            return false;
        }
        if (target instanceof PlayerEntity player) {
            if (!(livingOwner instanceof PlayerEntity caster)) {
                return false;
            }
            return world.getServer().isPvpEnabled()
                    && !caster.isTeammate(player)
                    && caster.shouldDamagePlayer(player);
        }
        if (target instanceof HostileEntity) {
            return true;
        }
        return target instanceof MobEntity mob && mob.getTarget() == livingOwner;
    }

    private boolean hasClearLineOfSight(ServerWorld world, Vec3d targetCenter) {
        return world.raycast(new RaycastContext(
                getEntityPos(), targetCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this)).getType() == HitResult.Type.MISS;
    }

    private void finishGuidance() {
        guidanceFinished = true;
        guidanceTarget = null;
    }

    private static Vec3d bodyCenter(LivingEntity target) {
        return new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());
    }

    private static Vec3d rotateToward(Vec3d from, Vec3d to, double maxAngle) {
        Vec3d start = from.normalize();
        Vec3d end = to.normalize();
        double angle = angleBetween(start, end);
        if (angle <= maxAngle) {
            return end;
        }

        double sinAngle = Math.sin(angle);
        if (Math.abs(sinAngle) < 1.0e-7) {
            return start;
        }

        double progress = maxAngle / angle;
        double startWeight = Math.sin((1.0 - progress) * angle) / sinAngle;
        double endWeight = Math.sin(progress * angle) / sinAngle;
        return start.multiply(startWeight).add(end.multiply(endWeight)).normalize();
    }

    private static double angleBetween(Vec3d first, Vec3d second) {
        double dot = first.normalize().dotProduct(second.normalize());
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
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

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !entity.equals(getOwner());
    }

    @Override
    public boolean shouldSave() {
        // Guidance and range state are intentionally transient for this short-lived spell.
        return false;
    }
}
