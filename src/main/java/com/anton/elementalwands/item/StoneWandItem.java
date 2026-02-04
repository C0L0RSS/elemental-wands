package com.anton.elementalwands.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import com.anton.elementalwands.entity.BoulderProjectileEntity;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TitanDomeManager;

public class StoneWandItem extends AbstractWandItem {

    private static final int WALL_DURATION_TICKS = 200;

    public StoneWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS)) return;

        BoulderProjectileEntity boulder = new BoulderProjectileEntity(world, caster);
        boulder.setPosition(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
        boulder.setVelocity(caster, caster.getPitch(), caster.getYaw(), 0.0f, 1.7f, 0.1f);
        world.spawnEntity(boulder);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.9f, 0.8f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                caster.getX(), caster.getBodyY(0.6), caster.getZ(), 10, 0.3, 0.3, 0.3, 0.10);
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS)) return;

        List<BlockPos> positions = wallPositions(caster, 5, 3, 2);

        int placed = TemporaryBlockManager.placeTemporaryBlocks(
                world,
                positions,
                Blocks.STONE.getDefaultState(),
                WALL_DURATION_TICKS,
                state -> (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty());

        if (placed > 0) {
            world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.CLOUD, caster.getX(), caster.getBodyY(0.5), caster.getZ(), 25, 1.0, 0.6, 1.0,
                    0.02);
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS)) return;

        TitanDomeManager.startDome(world, caster);
    }

    private static List<BlockPos> wallPositions(PlayerEntity caster, int width, int height, int forward) {
        Direction facing = caster.getHorizontalFacing();
        Direction left = facing.rotateYCounterclockwise();

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
