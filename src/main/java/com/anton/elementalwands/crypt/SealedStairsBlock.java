package com.anton.elementalwands.crypt;

import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;

/** Unbreakable look-alike stairs for the mausoleum; vanilla's stairs constructor is not public. */
public final class SealedStairsBlock extends StairsBlock {
    public SealedStairsBlock(BlockState base, Settings settings) {
        super(base, settings);
    }
}
