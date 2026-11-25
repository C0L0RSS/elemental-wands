package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.TitanDomeManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class TitanGearDropMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void elementalWands$keepTitanGear(boolean entireStack, CallbackInfoReturnable<Boolean> result) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (TitanDomeManager.isMarkedTitanGear(player.getMainHandStack())) {
            player.currentScreenHandler.syncState();
            result.setReturnValue(false);
        }
    }
}
