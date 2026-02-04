package com.anton.elementalwands.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import com.anton.elementalwands.util.BlizzardManager;
import com.anton.elementalwands.util.TemporaryBlockManager;

public class IceWandItem extends AbstractWandItem {

    private static final float ICE_SHARD_DAMAGE = 4.0f;
    private static final int ICE_SHARD_SLOW_TICKS = 80;

    private static final int WALL_DURATION_TICKS = 160;

    public IceWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS)) return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d start = caster.getEyePos();
        Vec3d end = hit.getPos();

        spawnParticleLine(world, start, end, ParticleTypes.SNOWFLAKE);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.3f);

        if (hit.getType() == HitResult.Type.ENTITY) {
            if (((EntityHitResult) hit).getEntity() instanceof LivingEntity target) {
                applyDamage(world, caster, target, ICE_SHARD_DAMAGE);
                target.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, ICE_SHARD_SLOW_TICKS, 1, false, true, true));
                target.setFrozenTicks(Math.min(target.getFrozenTicks() + 60, 240));
            }
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos top = ((BlockHitResult) hit).getBlockPos().up();
            if (world.getBlockState(top).isAir()) {
                var snow = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1);
                if (snow.canPlaceAt(world, top)) {
                    world.setBlockState(top, snow, 3);
                }
            }
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS)) return;

        List<BlockPos> positions = wallPositions(caster, 5, 3, 2);
        int placed = TemporaryBlockManager.placeTemporaryBlocks(
                world,
                positions,
                Blocks.PACKED_ICE.getDefaultState(),
                WALL_DURATION_TICKS,
                state -> (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty());

        if (placed > 0) {
            world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 0.9f, 1.2f);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, caster.getX(), caster.getBodyY(0.6), caster.getZ(), 35, 1.0, 0.6,
                    1.0, 0.03);
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS)) return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d center = hit.getType() == HitResult.Type.MISS ? caster.getEntityPos() : hit.getPos();

        BlizzardManager.startBlizzard(world, caster, center);
        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 0.8f, 0.6f);
    }

    private static List<BlockPos> wallPositions(PlayerEntity caster, int width, int height, int forward) {
        var facing = caster.getHorizontalFacing();
        var left = facing.rotateYCounterclockwise();

        BlockPos origin = caster.getBlockPos().offset(facing, forward);
        int half = width / 2;

        List<BlockPos> positions = new ArrayList<>();
        for (int dx = -half; dx <= half; dx++) {
            for (int dy = 0; dy < height; dy++) {
                positions.add(origin.offset(left, dx).up(dy));
            }
        }
        return positions;
    }
}
