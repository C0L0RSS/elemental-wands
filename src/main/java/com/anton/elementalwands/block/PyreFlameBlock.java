package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Visual-only fire placed over Dragon's Pyre.
 *
 * <p>The block deliberately has no entity-collision callback: the authoritative
 * pyre front and the existing magma-backed runway remain the only gameplay
 * sources. Its model is assembled entirely from Minecraft's vanilla fire
 * models.</p>
 */
public final class PyreFlameBlock extends Block {

    public static final MapCodec<PyreFlameBlock> CODEC = createCodec(PyreFlameBlock::new);
    private static final VoxelShape OUTLINE_SHAPE = Block.createColumnShape(16.0, 0.0, 1.0);

    public PyreFlameBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPE;
    }
}
