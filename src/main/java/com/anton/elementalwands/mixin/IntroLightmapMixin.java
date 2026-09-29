package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.NecromancerIntroClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The crypt's ambient fill returns as the intro's soul-fire front lights its braziers. */
@Mixin(LightmapTextureManager.class)
public class IntroLightmapMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;ambientLight()F"))
    private float introAmbient(DimensionType dimension, float tickProgress) {
        return NecromancerIntroClient.ambientLight(dimension.ambientLight(), tickProgress);
    }
}
