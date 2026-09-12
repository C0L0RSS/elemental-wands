package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Low brambles and floating leaves share the old carpet/pad's one-pixel walking surface. */
public final class NatureGrowthBlock extends Block {
    public static final MapCodec<NatureGrowthBlock> CODEC=createCodec(NatureGrowthBlock::new);
    private static final VoxelShape SHAPE=Block.createCuboidShape(0,0,0,16,1,16);
    public NatureGrowthBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPE;}
}
