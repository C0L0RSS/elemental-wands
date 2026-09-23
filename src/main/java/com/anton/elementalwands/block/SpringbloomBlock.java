package com.anton.elementalwands.block;

import com.anton.elementalwands.util.SpringbloomManager;
import com.anton.elementalwands.util.SpringbloomFootprint;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/** Raised center; low petals are decorative. The exact authored mesh is drawn by its visual entity. */
public final class SpringbloomBlock extends Block {
    public static final MapCodec<SpringbloomBlock> CODEC = createCodec(SpringbloomBlock::new);
    public static final IntProperty OPEN_CELLS=IntProperty.of("open_cells",0,SpringbloomFootprint.FULL);
    public SpringbloomBlock(Settings settings) { super(settings);setDefaultState(getStateManager().getDefaultState().with(OPEN_CELLS,SpringbloomFootprint.FULL)); }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(OPEN_CELLS);}
    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }
    @Override protected VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SpringbloomFootprint.shape(s.get(OPEN_CELLS)); }
    @Override public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, double distance) {
        if (entity instanceof PlayerEntity
                && Math.abs(entity.getY()-pos.getY()-com.anton.elementalwands.util.SpringbloomRules.HEIGHT)<.035
                && SpringbloomFootprint.overlaps(state.get(OPEN_CELLS),pos,entity.getBoundingBox())) entity.fallDistance = 0;
        else super.onLandedUpon(world, state, pos, entity, distance);
    }
    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old, boolean notify) {
        if (world instanceof ServerWorld server) server.scheduleBlockTick(pos, this, 1);
    }
    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!SpringbloomManager.hasPad(world, pos)) world.removeBlock(pos, false);
        else world.scheduleBlockTick(pos, this, 1);
    }
}
