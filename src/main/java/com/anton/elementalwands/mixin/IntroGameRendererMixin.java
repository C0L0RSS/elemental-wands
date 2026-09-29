package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.NecromancerIntroClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No hand, bobbing, hurt tilt or block outline in the intro's shots, and a narrower lens. */
@Mixin(GameRenderer.class)
public class IntroGameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void introHand(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        if (NecromancerIntroClient.cinematic()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void introBob(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NecromancerIntroClient.cinematic()) ci.cancel();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void introTilt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NecromancerIntroClient.cinematic()) ci.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void introOutline(CallbackInfoReturnable<Boolean> cir) {
        if (NecromancerIntroClient.cinematic()) cir.setReturnValue(false);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void introLens(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (NecromancerIntroClient.frozen()) cir.setReturnValue(NecromancerIntroClient.fov(cir.getReturnValueF(), tickProgress));
    }
}
