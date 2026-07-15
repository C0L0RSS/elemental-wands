package com.anton.elementalwands.entity;

import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SeedlingManager;
import com.anton.elementalwands.util.EntangleTracker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The Nature wand's primary projectile: a thrown seed that sprouts a {@link SeedlingManager}
 * anchor where it lands, or bites into an enemy. Thorns dig harder into entangled prey, so the
 * impact damage scales with the target's current {@link EntangleTracker} stacks.
 */
public class SeedProjectileEntity extends ProjectileEntity {

    private static final float BASE_DAMAGE = 5.5f;
    private static final float DAMAGE_PER_ENTANGLE_STACK = 1.25f;
    private static final float MAX_DAMAGE = 11.0f;
    private static final double INITIAL_SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final int MAX_LIFETIME_TICKS = 100;

    private int ticksAlive;

    public SeedProjectileEntity(EntityType<? extends SeedProjectileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public SeedProjectileEntity(ServerWorld world, LivingEntity owner) {
        super(ModEntities.SEED_PROJECTILE, world);
        setOwner(owner);
        setNoGravity(true);

        Vec3d eye = owner.getEyePos();
        setPosition(eye.x, eye.y, eye.z);

        Vec3d dir = owner.getRotationVec(1.0f).normalize();
        setVelocity(dir.multiply(INITIAL_SPEED));
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }

        ticksAlive++;
        if (ticksAlive > MAX_LIFETIME_TICKS) {
            discard();
            return;
        }

        sw.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY(), getZ(), 3, 0.08, 0.08, 0.08, 0.01);
        sw.spawnParticles(ParticleTypes.COMPOSTER, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.01);

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        // ProjectileUtil uses COLLIDER raycasts, which skip non-collision blocks like
        // flowers, tall grass, and saplings. Do a secondary OUTLINE raycast so the seed
        // also registers a hit on those blocks (and uses whichever is closer to the start).
        if (hit.getType() != HitResult.Type.ENTITY) {
            Vec3d start = getEntityPos();
            Vec3d end = start.add(getVelocity());
            BlockHitResult outlineHit = sw.raycast(new RaycastContext(start, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, this));
            if (outlineHit.getType() == HitResult.Type.BLOCK) {
                BlockState outlineState = sw.getBlockState(outlineHit.getBlockPos());
                if (!outlineState.isAir() && outlineState.getFluidState().isEmpty()) {
                    if (hit.getType() == HitResult.Type.MISS
                            || outlineHit.getPos().squaredDistanceTo(start)
                                    < hit.getPos().squaredDistanceTo(start)) {
                        hit = outlineHit;
                    }
                }
            }
        }
        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            return;
        }

        Vec3d v = getVelocity();
        Vec3d next = new Vec3d(v.x * DRAG, (v.y - GRAVITY) * DRAG, v.z * DRAG);
        setVelocity(next);
        setPosition(getX() + next.x, getY() + next.y, getZ() + next.z);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }

        Entity target = entityHitResult.getEntity();
        Entity owner = getOwner();

        if (target instanceof LivingEntity living) {
            EntangleTracker.addStack(sw, living);
            int stacks = EntangleTracker.getStacks(living);
            float damage = Math.min(MAX_DAMAGE, BASE_DAMAGE + DAMAGE_PER_ENTANGLE_STACK * stacks);

            DamageSource source = (owner instanceof PlayerEntity caster)
                    ? sw.getDamageSources().playerAttack(caster)
                    : (owner instanceof LivingEntity livingOwner
                            ? sw.getDamageSources().thrown(this, livingOwner)
                            : sw.getDamageSources().magic());

            boolean damaged = living.damage(sw, source, damage);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(owner, damage);
            }

            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    living.getX(), living.getBodyY(0.5), living.getZ(),
                    15, 0.4, 0.4, 0.4, 0.03);
        }

        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }

        Entity owner = getOwner();
        if (owner instanceof PlayerEntity caster) {
            if (!SeedlingManager.destroySeedlingAtAnchor(sw, blockHitResult.getBlockPos())) {
                SeedlingManager.tryPlantSeedling(sw, caster, blockHitResult);
            }
        } else {
            Vec3d p = blockHitResult.getPos();
            sw.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.01);
        }

        discard();
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !entity.equals(getOwner());
    }
}
