package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.PyreSeaHaze;
import com.anton.elementalwands.hall.PyreHall;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hands the Burned Throne Hall's sea haze this frame's final fog colour, so the haze matches the sky exactly. */
@Mixin(FogRenderer.class)
public class PyreSeaFogColorMixin {
    @Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At("RETURN"))
    private void elementalwands$seaHazeColor(Camera camera, int viewDistance, boolean thickFog, RenderTickCounter tickCounter,
            float skyDarkness, ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        if (world != null && world.getRegistryKey() == PyreHall.WORLD) PyreSeaHaze.fogColor(cir.getReturnValue());
    }
}
