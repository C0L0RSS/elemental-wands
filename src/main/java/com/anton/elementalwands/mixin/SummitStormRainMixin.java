package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.SummitStormClient;
import com.anton.elementalwands.summit.StormSummit;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The summit's biome has no precipitation, so real weather never rains on the fight or puts out
 * fire spells. During phase two's storm the client alone draws and plays rain there.
 */
@Mixin(WeatherRendering.class)
public abstract class SummitStormRainMixin {
    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void elementalwands$stormRain(World world, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        // Like vanilla, no rain over chunks that aren't loaded.
        if (world.getRegistryKey() == StormSummit.WORLD && SummitStormClient.level(1) > 0
                && world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) cir.setReturnValue(Biome.Precipitation.RAIN);
    }
}
