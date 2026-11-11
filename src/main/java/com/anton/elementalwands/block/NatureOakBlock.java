package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Canopy of the Overgrowth oak. The leaves are scenery: nothing collides with them, and with no
 * outline they cannot be aimed at, mined or used as cover. The trunk under them is solid.
 */
public final class NatureOakBlock extends Block {
    public static final MapCodec<NatureOakBlock> CODEC=createCodec(NatureOakBlock::new);
    public NatureOakBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.empty();}
}
