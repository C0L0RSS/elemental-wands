package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.TemporaryBlockManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class TemporaryTerrainChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void releaseChangedCell(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> result) {
        WorldChunk chunk = (WorldChunk) (Object) this;
        if (result.getReturnValue() != null && chunk.getWorld() instanceof ServerWorld) {
            TemporaryBlockManager.onBlockChanged(chunk, pos);
        }
    }
}
