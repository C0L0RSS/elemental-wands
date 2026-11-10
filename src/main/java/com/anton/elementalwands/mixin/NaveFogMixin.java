package com.anton.elementalwands.mixin;

import com.anton.elementalwands.arena.ShatteredNave;
import com.anton.elementalwands.client.PyreSeaHaze;
import com.anton.elementalwands.hall.PyreHall;
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
 * The Shattered Nave and the Burned Throne Hall borrow the Nether's thick fog so they have no sky,
 * then set the haze at a fixed distance, whatever the render distance. In the nave the pillar
 * ranks fade out before the authored hall ends, so it reads as endless. In the throne hall the
 * whole 81-block floor stays readable from the arrival and the lava sea fades into haze beyond it
 * ({@link PyreSeaHaze} softens where the sea meets the fog's end).
 */
@Mixin(DimensionOrBossFogModifier.class)
public abstract class NaveFogMixin {
    private static final float NAVE_FOG_START = 16, NAVE_FOG_END = 150;

    @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
    private void elementalwands$naveHaze(FogData data, Entity entity, BlockPos pos, ClientWorld world, float viewDistance,
            RenderTickCounter tickCounter, CallbackInfo ci) {
        float start, end;
        if (world.getRegistryKey() == ShatteredNave.WORLD) { start = NAVE_FOG_START; end = Math.min(NAVE_FOG_END, viewDistance * .95f); }
        else if (world.getRegistryKey() == PyreHall.WORLD) { start = PyreSeaHaze.FOG_START; end = PyreSeaHaze.fogEnd(viewDistance); }
        else return;
        data.environmentalStart = Math.min(start, end * .5f);
        data.environmentalEnd = end;
        data.skyEnd = end;
        data.cloudEnd = end;
    }
}
