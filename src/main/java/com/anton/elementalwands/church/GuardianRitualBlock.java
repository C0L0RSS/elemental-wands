package com.anton.elementalwands.church;

import com.anton.elementalwands.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

/** Native modeled pedestal support and matching stone chest rune. No ticking block entities. */
public final class GuardianRitualBlock extends Block {
    public static final MapCodec<GuardianRitualBlock> CODEC=createCodec(GuardianRitualBlock::new);
    private static final VoxelShape SUPPORT=VoxelShapes.union(
            Block.createCuboidShape(-3.2,0,-3.2,19.2,3.8,19.2),
            Block.createCuboidShape(2,3.8,2,14,15,14),
            Block.createCuboidShape(1.2,15,1.2,14.8,16,14.8));
    public GuardianRitualBlock(Settings settings) {
        super(settings);setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.LIT,false));
    }
    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(Properties.HORIZONTAL_FACING,Properties.LIT); }
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation) { return state.with(Properties.HORIZONTAL_FACING,rotation.rotate(state.get(Properties.HORIZONTAL_FACING))); }
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING))); }
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return state.isOf(ModBlocks.GUARDIAN_PEDESTAL)?SUPPORT:VoxelShapes.fullCube();
    }
}
