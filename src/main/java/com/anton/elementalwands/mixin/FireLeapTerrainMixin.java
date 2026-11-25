package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.FireLeapPreview;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class FireLeapTerrainMixin {
    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("HEAD"))
    private void elementalwands$invalidateRing(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        FireLeapPreview.blockChanged((ClientWorld) (Object) this, pos);
    }
}
