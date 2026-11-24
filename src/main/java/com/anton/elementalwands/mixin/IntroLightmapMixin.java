package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.BossIntroCamera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** The crypt's ambient fill returns as the intro's soul-fire front lights its braziers. */
@Mixin(LightmapTextureManager.class)
public class IntroLightmapMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/DimensionType;ambientLight()F"))
    private float introAmbient(DimensionType dimension, Operation<Float> original, float tickProgress) {
        return BossIntroCamera.ambientLight(original.call(dimension), tickProgress);
    }
}
