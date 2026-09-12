package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** A targetable flowering anchor. The spell manager owns support, lifetime and restoration. */
public final class NatureSeedlingBlock extends Block {
    public static final MapCodec<NatureSeedlingBlock> CODEC=createCodec(NatureSeedlingBlock::new);
    public static final IntProperty STAGE=IntProperty.of("stage",0,3);
    public NatureSeedlingBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(STAGE,0));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(STAGE);}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        return Block.createCuboidShape(0,0,0,16,Math.min(16,8+state.get(STAGE)*3),16);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        return Blocks.FLOWERING_AZALEA.getDefaultState().getCollisionShape(world,pos,context);
    }
}
