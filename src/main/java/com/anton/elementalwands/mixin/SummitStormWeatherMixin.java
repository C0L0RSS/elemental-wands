package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.SummitStormClient;
import com.anton.elementalwands.summit.StormSummit;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the client, the summit's weather is the Roc's storm and nothing else: phase one keeps its
 * clear sky whatever the Overworld's weather, and phase two's thunderstorm darkens it. Server
 * worlds, including the integrated server's, keep their real weather.
 */
@Mixin(World.class)
public abstract class SummitStormWeatherMixin {
    @Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
    private void elementalwands$stormRain(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (summit()) cir.setReturnValue(SummitStormClient.level(tickProgress));
    }

    @Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
    private void elementalwands$stormThunder(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (summit()) cir.setReturnValue(SummitStormClient.level(tickProgress));
    }

    private boolean summit() {
        World world = (World) (Object) this;
        return world.isClient() && world.getRegistryKey() == StormSummit.WORLD;
    }
}
