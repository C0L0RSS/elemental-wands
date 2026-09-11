package com.anton.elementalwands.mixin;

import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.block.AzaleaBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spell seedlings must survive neighbor updates on the arena's artificial floor. */
@Mixin(AzaleaBlock.class)
public abstract class GuardianSeedlingSupportMixin {
    @Inject(method="canPlantOnTop", at=@At("HEAD"), cancellable=true)
    private void supportSpellSeedlings(BlockState floor, BlockView world, BlockPos pos,
            CallbackInfoReturnable<Boolean> ci) {
        if (floor.isOf(ModBlocks.ARENA_STONE) || floor.isOf(ModBlocks.ARENA_DARK)
                || floor.isOf(ModBlocks.ARENA_LIGHT) || floor.isOf(ModSpellBlocks.PYRE_COALS)) ci.setReturnValue(true);
    }
}
