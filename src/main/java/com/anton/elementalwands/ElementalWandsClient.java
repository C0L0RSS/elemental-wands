package com.anton.elementalwands;

import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class ElementalWandsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.BOULDER_PROJECTILE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CHILL_SNOWBALL, FlyingItemEntityRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(ElementalWandsClient::tickClient);
    }

    private static void tickClient(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        if (client.currentScreen != null) return;

        if (!(client.player.getMainHandStack().getItem() instanceof AbstractWandItem)) return;

        while (client.options.attackKey.wasPressed()) {
            ClientPlayNetworking.send(ModNetworking.CastPrimaryPayload.INSTANCE);
        }
    }
}
