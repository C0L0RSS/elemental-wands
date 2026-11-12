package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * The base of a Sentry Bloom flower: roots, leaves and stem. It is scenery: no collision and no
 * outline, so nothing stands on it, aims at it or breaks it by hand. It unfurls through four
 * stages, and while its owner's Full Bloom lasts it is grown: a bigger rosette, a taller stem and a branch for a second head.
 * {@code FlowerTurretManager} owns its lifetime, its heads and its restoration.
 */
public final class NatureSeedlingBlock extends Block {
    public static final MapCodec<NatureSeedlingBlock> CODEC=createCodec(NatureSeedlingBlock::new);
    public static final IntProperty STAGE=IntProperty.of("stage",0,3);
    public static final BooleanProperty GROWN=BooleanProperty.of("grown");
    public NatureSeedlingBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(STAGE,0).with(GROWN,false));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(STAGE,GROWN);}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        return VoxelShapes.empty();
    }
}
