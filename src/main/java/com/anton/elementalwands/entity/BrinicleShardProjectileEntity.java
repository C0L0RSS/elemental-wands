package com.anton.elementalwands.entity;

import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.BrinicleShardManager;
import com.anton.elementalwands.util.ChillTracker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BrinicleShardProjectileEntity extends ProjectileEntity {

    private static final float BASE_DAMAGE = 4.0f;
    private static final float DAMAGE_PER_FROST_STACK = 1.0f;
    private static final float MAX_DAMAGE = 10.0f;
    private static final double INITIAL_SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final int MAX_LIFETIME_TICKS = 100;

    private int ticksAlive;

    public BrinicleShardProjectileEntity(EntityType<? extends BrinicleShardProjectileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public BrinicleShardProjectileEntity(ServerWorld world, LivingEntity owner) {
        super(ModEntities.BRINICLE_SHARD_PROJECTILE, world);
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

        sw.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 3, 0.08, 0.08, 0.08, 0.01);
        sw.spawnParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.01);

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
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
            int stacks = ChillTracker.getStacks(living);
            float damage = Math.min(MAX_DAMAGE, BASE_DAMAGE + DAMAGE_PER_FROST_STACK * stacks);

            DamageSource source = (owner instanceof PlayerEntity caster)
                    ? sw.getDamageSources().playerAttack(caster)
                    : (owner instanceof LivingEntity livingOwner
                            ? sw.getDamageSources().thrown(this, livingOwner)
                            : sw.getDamageSources().magic());

            boolean damaged = living.damage(sw, source, damage);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(owner, damage);
            }

            sw.spawnParticles(ParticleTypes.SNOWFLAKE,
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
            if (!BrinicleShardManager.destroyShardAtAnchor(sw, blockHitResult.getBlockPos())) {
                BrinicleShardManager.tryPlantShard(sw, caster, blockHitResult);
            }
        } else {
            Vec3d p = blockHitResult.getPos();
            sw.spawnParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.01);
        }

        discard();
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !entity.equals(getOwner());
    }
}
