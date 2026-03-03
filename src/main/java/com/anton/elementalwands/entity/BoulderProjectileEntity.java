package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModEntities;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BoulderProjectileEntity extends ThrownItemEntity {

    private static final float DAMAGE = 6.0f;

    public BoulderProjectileEntity(EntityType<? extends BoulderProjectileEntity> type, World world) {
        super(type, world);
    }

    public BoulderProjectileEntity(World world, LivingEntity owner) {
        super(ModEntities.BOULDER_PROJECTILE, owner, world, new ItemStack(Blocks.COBBLESTONE));
    }

    @Override
    protected Item getDefaultItem() {
        return Blocks.COBBLESTONE.asItem();
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        Entity target = entityHitResult.getEntity();

        Entity owner = getOwner();
        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? getEntityWorld().getDamageSources().thrown(this, livingOwner)
                : getEntityWorld().getDamageSources().generic();

        if (getEntityWorld() instanceof ServerWorld sw) {
            boolean damaged = target.damage(sw, source, DAMAGE);
            if (damaged) {
                com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, DAMAGE);
            }
        }

        if (target instanceof LivingEntity living) {
            Vec3d v = getVelocity();
            Vec3d dir = v.lengthSquared() > 0.0001 ? v.normalize() : Vec3d.ZERO;
            living.addVelocity(dir.x * 1.2, 0.25, dir.z * 1.2);
            living.velocityModified = true;
        }

        if (getEntityWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    getX(), getY(), getZ(), 18, 0.25, 0.25, 0.25, 0.06);
            sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.9f);
        }

        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            discard();
            return;
        }

        Vec3d hitPos = blockHitResult.getPos();
        Entity owner = getOwner();

        sw.createExplosion(owner, hitPos.x, hitPos.y, hitPos.z, 1.5f, false, World.ExplosionSourceType.MOB);
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                hitPos.x, hitPos.y, hitPos.z, 30, 0.35, 0.25, 0.35, 0.10);
        sw.playSound(null, blockHitResult.getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);

        discard();
    }
}
