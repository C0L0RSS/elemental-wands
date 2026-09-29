package com.anton.elementalwands.mixin;

import com.anton.elementalwands.arena.ShatteredNave;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.DimensionOrBossFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Shattered Nave borrows the Nether's thick fog so it has no sky, then sets the haze at a fixed
 * distance: the pillar ranks fade out before the authored hall ends, whatever the render distance,
 * so the hall reads as endless.
 */
@Mixin(DimensionOrBossFogModifier.class)
public abstract class NaveFogMixin {
    private static final float NAVE_FOG_START = 16, NAVE_FOG_END = 150;

    @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
    private void elementalwands$naveHaze(FogData data, Entity entity, BlockPos pos, ClientWorld world, float viewDistance,
            RenderTickCounter tickCounter, CallbackInfo ci) {
        if (world.getRegistryKey() != ShatteredNave.WORLD) return;
        float end = Math.min(NAVE_FOG_END, viewDistance * .95f);
        data.environmentalStart = Math.min(NAVE_FOG_START, end * .5f);
        data.environmentalEnd = end;
        data.skyEnd = end;
        data.cloudEnd = end;
    }
}
