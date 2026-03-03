package com.anton.elementalwands.block;

import com.anton.elementalwands.registry.ModBlocks;
import com.mojang.serialization.MapCodec;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.network.ModNetworking;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class MagicCrystalOreBlock extends Block {
    public static final MapCodec<MagicCrystalOreBlock> CODEC = createCodec(MagicCrystalOreBlock::new);

    public MagicCrystalOreBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state,
            @SuppressWarnings("deprecation") BlockEntity blockEntity, net.minecraft.item.ItemStack tool) {
        super.afterBreak(world, player, pos, state, blockEntity, tool);
        if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            long current = serverPlayer.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L);
            serverPlayer.setAttached(EWAttachments.ARCANE_FLUX, current + 25L);
            ModNetworking.syncPlayerData(serverPlayer);
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(6) != 0) {
            return;
        }

        int color = getParticleColor();
        for (Direction direction : Direction.values()) {
            if (world.getBlockState(pos.offset(direction)).isOpaqueFullCube()) {
                continue;
            }

            double x = direction.getAxis() == Direction.Axis.X
                    ? 0.5 + 0.5625 * direction.getOffsetX()
                    : random.nextFloat();
            double y = direction.getAxis() == Direction.Axis.Y
                    ? 0.5 + 0.5625 * direction.getOffsetY()
                    : random.nextFloat();
            double z = direction.getAxis() == Direction.Axis.Z
                    ? 0.5 + 0.5625 * direction.getOffsetZ()
                    : random.nextFloat();

            world.addParticleClient(
                    new DustParticleEffect(color, 1.0f),
                    pos.getX() + x,
                    pos.getY() + y,
                    pos.getZ() + z,
                    0.0,
                    0.0,
                    0.0);
        }
    }

    private int getParticleColor() {
        if (this == ModBlocks.FIRE_CRYSTAL_ORE || this == ModBlocks.DEEPSLATE_FIRE_CRYSTAL_ORE) {
            return 0xFF7A1A;
        }
        if (this == ModBlocks.WIND_CRYSTAL_ORE || this == ModBlocks.DEEPSLATE_WIND_CRYSTAL_ORE) {
            return 0x86FFD5;
        }
        if (this == ModBlocks.STONE_CRYSTAL_ORE || this == ModBlocks.DEEPSLATE_STONE_CRYSTAL_ORE) {
            return 0xD8A04C;
        }
        if (this == ModBlocks.ICE_CRYSTAL_ORE || this == ModBlocks.DEEPSLATE_ICE_CRYSTAL_ORE) {
            return 0x7DE9FF;
        }
        return 0x8E69FF;
    }
}
