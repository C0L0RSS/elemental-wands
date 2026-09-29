package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.NecromancerIntroClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse can't turn the player during the intro's shots, so the hand-back lands where it began. */
@Mixin(Entity.class)
public class IntroLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void introLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object)this == MinecraftClient.getInstance().player && NecromancerIntroClient.cinematic()) ci.cancel();
    }
}
