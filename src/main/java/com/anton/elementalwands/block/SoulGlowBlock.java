package com.anton.elementalwands.block;

import com.anton.elementalwands.util.SoulGlow;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Invisible light carried by the Necromancer's soul spells ({@link SoulGlow} places it). It never
 * outlives its spell: every placement schedules a check, and that tick (saved with the chunk)
 * clears any glow nothing refreshed, so none survives a crash or a reload.
 */
public final class SoulGlowBlock extends Block {
    public static final MapCodec<SoulGlowBlock> CODEC = createCodec(SoulGlowBlock::new);
    public static final IntProperty LEVEL = Properties.LEVEL_15;

    public SoulGlowBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(LEVEL, 15));
    }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return VoxelShapes.empty(); }

    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.isOf(this)) world.scheduleBlockTick(pos, this, SoulGlow.CHECK);
    }

    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) { SoulGlow.check(world, pos, true); }

    /** Fallback for a glow whose scheduled check was somehow lost. */
    @Override protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) { SoulGlow.check(world, pos, false); }
}
