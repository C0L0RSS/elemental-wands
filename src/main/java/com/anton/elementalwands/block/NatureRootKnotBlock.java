package com.anton.elementalwands.block;

import com.anton.elementalwands.util.TendrilBloomManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A stationary, breakable source. Scheduled checks also remove orphan knots after reload. */
public final class NatureRootKnotBlock extends Block {
    public static final MapCodec<NatureRootKnotBlock> CODEC = createCodec(NatureRootKnotBlock::new);
    public NatureRootKnotBlock(Settings settings) { super(settings); }
    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return Block.createCuboidShape(1, 0, 1, 15, 13, 15);
    }
    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world instanceof ServerWorld server) server.scheduleBlockTick(pos, this, 20);
    }
    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!TendrilBloomManager.hasKnot(world, pos)) world.removeBlock(pos, false);
        else world.scheduleBlockTick(pos, this, 20);
    }
}
