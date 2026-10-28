package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.StormRocIntroClient;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Storm Roc's intro carries the player into the sky realm behind its own storm cloud, so the
 * loading screen is never opened for that one move. Vanilla still tracks the new world's loading
 * (the network handler ticks it and tells the server when the player has loaded); only the screen
 * that shows it is skipped, along with the black frame it forces.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class StormRocIntroLoadingMixin {
    @WrapWithCondition(method = "startWorldLoading", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;setScreenAndRender(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private boolean elementalwands$introCloud(MinecraftClient client, Screen screen, @Local(argsOnly = true) ClientWorld world) {
        if (!StormRocIntroClient.coversLoading(world.getRegistryKey())) return true;
        StormRocIntroClient.loadingSkipped();
        return false;
    }
}
