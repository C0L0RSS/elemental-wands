package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.WandControls;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class WandMouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void wandInput(long window, MouseInput input, int action, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && WandControls.input(true, input.button(), action)) ci.cancel();
    }
}
