package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;

/**
 * Internal Stone Wall block with a visual state for the recast/shatter read.
 * Both states remain mechanically shatterable; the property only swaps the
 * material to the stress-fractured model once the slabs have settled.
 */
public final class StoneWallBlock extends Block {

    public static final MapCodec<StoneWallBlock> CODEC = createCodec(StoneWallBlock::new);
    public static final BooleanProperty SHATTER_READY = BooleanProperty.of("shatter_ready");

    public StoneWallBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(SHATTER_READY, false));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(SHATTER_READY);
    }
}
