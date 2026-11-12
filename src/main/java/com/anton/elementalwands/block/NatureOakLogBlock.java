package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.PillarBlock;

/** Trunk and branches of the Overgrowth oak: solid, like {@link NatureOakTrunkBlock}. */
public final class NatureOakLogBlock extends PillarBlock {
    public static final MapCodec<NatureOakLogBlock> CODEC=createCodec(NatureOakLogBlock::new);
    public NatureOakLogBlock(Settings settings){super(settings);}
    @Override public MapCodec<? extends PillarBlock> getCodec(){return CODEC;}
}
