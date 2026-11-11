package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;

/**
 * Heartwood of the Overgrowth oak. With {@link NatureOakLogBlock} it is the tree's trunk: an
 * ordinary solid block for the tree's fifteen seconds, so it stops movement and shots and gives
 * cover under the same shared rules as terrain or a Stone Wall. It cannot be mined.
 */
public final class NatureOakTrunkBlock extends Block {
    public static final MapCodec<NatureOakTrunkBlock> CODEC=createCodec(NatureOakTrunkBlock::new);
    public NatureOakTrunkBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
}
