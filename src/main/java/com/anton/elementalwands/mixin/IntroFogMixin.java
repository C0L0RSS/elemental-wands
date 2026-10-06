package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.BossIntroCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Darken the distant crypt with its ambient fill, preserving the usual fog after ignition. */
@Mixin(FogRenderer.class)
public class IntroFogMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void introFog(Camera camera, float tickProgress, ClientWorld world, int viewDistance,
            float skyDarkness, boolean thickFog, CallbackInfoReturnable<Vector4f> cir) {
        float light = BossIntroCamera.arenaLight(tickProgress);
        if (light >= 1) return;
        float scale = .22f + .78f * light;
        Vector4f color = cir.getReturnValue();
        cir.setReturnValue(new Vector4f(color.x * scale, color.y * scale, color.z * scale, color.w));
    }
}
