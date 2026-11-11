package com.anton.elementalwands.entity;

import com.anton.elementalwands.party.WandAllies;

import java.util.List;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModSounds;

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

    /** The Astral Double's echo hits at 70% of a cast bolt, splash included. */
    public static final float ECHO_STRENGTH = 0.7f;

    private boolean astralPair;
    private float strength = 1.0f;
    private static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> ASTRAL_DAMAGE =
            net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE, net.minecraft.util.Identifier.of("elementalwands","astral_bolt"));
    public void setAstralPair() { astralPair=true; }
    public void setEcho() { strength=ECHO_STRENGTH; }
    public SingularityBoltEntity(ServerWorld world, LivingEntity owner, Vec3d origin, Vec3d direction) {
        this(world,owner);setPosition(origin);startPos=origin;launchDirection=direction;
        setVelocity(direction.multiply(PROJECTILE_SPEED));
    }
    private DamageSource impactSource(ServerWorld world) {
        if(astralPair)return new DamageSource(world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getOrThrow(ASTRAL_DAMAGE),this,getOwner());
        return getOwner() instanceof LivingEntity owner ? world.getDamageSources().thrown(this,owner) : world.getDamageSources().magic();
    }
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

    private boolean protectsAlly(Entity target) {
        return WandAllies.protectedFrom(getOwner(), target)
                || (getEntityWorld() instanceof ServerWorld world && owner != null
                    && WandAllies.protectedFrom(world, owner.getUuid(), target));
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

        if (astralPair && (!(getOwner() instanceof net.minecraft.server.network.ServerPlayerEntity p) || !p.isAlive() || p.isRemoved()
                || p.getEntityWorld()!=serverWorld || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(p))) { discard(); return; }
        if (startPos == null) {
            startPos = getEntityPos();
        }
        if (launchDirection == null || launchDirection.lengthSquared() < 0.0001) {
            launchDirection = getVelocity().lengthSquared() > 0.0001
                    ? getVelocity().normalize()
                    : new Vec3d(0.0, 0.0, 1.0);
        }

        if (getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
            discard();
            return;
        }

        applyCappedGuidance(serverWorld);

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
        float directDamage = DIRECT_DAMAGE * strength, splashDamage = SPLASH_DAMAGE * strength;

        if (directHit instanceof LivingEntity living && !protectsAlly(directHit)) {
            DamageSource source = impactSource(world);
            boolean damaged = damageWithoutKnockback(world, living, source, directDamage);
            if (damaged) {
                com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, directDamage, com.anton.elementalwands.data.WizardAffinity.SPACE);
            }
        }

        Box box = Box.of(impactPos, IMPACT_RADIUS * 2.0, IMPACT_RADIUS * 2.0, IMPACT_RADIUS * 2.0);
        List<LivingEntity> affected = world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !entity.isSpectator() && !protectsAlly(entity));

        for (LivingEntity living : affected) {
            if (living.squaredDistanceTo(impactPos) > IMPACT_RADIUS * IMPACT_RADIUS) {
                continue;
            }

            if (living != directHit) {
                DamageSource splashSource = impactSource(world);
                boolean damaged = damageWithoutKnockback(world, living, splashSource, splashDamage);
                if (damaged) {
                    com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, splashDamage, com.anton.elementalwands.data.WizardAffinity.SPACE);
                }
            }
        }

        // Clients draw the impact where the bolt struck; a bolt removed without one simply ran out of range.
        com.anton.elementalwands.util.BlockSpaceEvents.send(world, com.anton.elementalwands.util.BlockSpaceEvents.BOLT_HIT, getId(), impactPos,
                launchDirection != null ? launchDirection : getVelocity(), strength);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                ModSounds.SPELL_SPACE_SINGULARITY_BOLT_IMPACT_BURST, SoundCategory.PLAYERS, 0.82f, 1.35f);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(impactPos),
                ModSounds.SPELL_SPACE_SINGULARITY_BOLT_IMPACT_POP, SoundCategory.PLAYERS, 0.72f, 1.12f);
    }

    private static boolean damageWithoutKnockback(
            ServerWorld world, LivingEntity target, DamageSource source, float amount) {
        Vec3d velocityBeforeDamage = target.getVelocity();
        boolean damaged = com.anton.elementalwands.util.SpellCombat.damage(target,world,source,amount,source.getAttacker(),com.anton.elementalwands.data.WizardAffinity.SPACE);
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
                || !target.isAlive() || target.isSpectator() || protectsAlly(target)) {
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
        if (target instanceof WandBoss boss) {
            return boss.isBossAggressive() && !target.isTeammate(livingOwner);
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

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !protectsAlly(entity);
    }

    @Override
    public boolean shouldSave() {
        // Guidance and range state are intentionally transient for this short-lived spell.
        return false;
    }
}
