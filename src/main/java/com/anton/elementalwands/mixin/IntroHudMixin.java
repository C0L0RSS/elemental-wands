package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.BossIntroCamera;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The intro draws its letterbox in place of the HUD while it owns the camera. */
@Mixin(InGameHud.class)
public class IntroHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void introLetterbox(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        if (!BossIntroCamera.cinematic()) return;
        BossIntroCamera.drawCinematic(context, counter);
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void introWaiting(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        BossIntroCamera.drawWaiting(context);
    }
}
