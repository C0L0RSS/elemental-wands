package com.anton.elementalwands.entity;

import com.anton.elementalwands.util.ChillTracker;

import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ChillSnowballEntity extends SnowballEntity {

    public ChillSnowballEntity(EntityType<? extends ChillSnowballEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!(getEntityWorld() instanceof ServerWorld sw)) return;

        Entity entity = entityHitResult.getEntity();
        if (entity instanceof LivingEntity living) {
            ChillTracker.addStack(sw, living);
            sw.spawnParticles(ParticleTypes.SNOWFLAKE, living.getX(), living.getBodyY(0.5), living.getZ(), 12, 0.35, 0.35,
                    0.35, 0.02);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!(getEntityWorld() instanceof ServerWorld sw)) return;

        BlockPos top = blockHitResult.getBlockPos().up();
        if (sw.getBlockState(top).isAir()) {
            var snow = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1);
            if (snow.canPlaceAt(sw, top)) {
                sw.setBlockState(top, snow, 3);
            }
        }

        sw.spawnParticles(ParticleTypes.SNOWFLAKE, blockHitResult.getPos().x, blockHitResult.getPos().y, blockHitResult.getPos().z,
                10, 0.25, 0.25, 0.25, 0.02);
    }
}
