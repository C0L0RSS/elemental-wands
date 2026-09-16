package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.WandControls;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class WandKeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void wandInput(long window, int action, KeyInput input, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && WandControls.input(false, input.key(), action)) ci.cancel();
    }
}
