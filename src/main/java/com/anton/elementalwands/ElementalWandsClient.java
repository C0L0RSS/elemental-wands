package com.anton.elementalwands;

import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.client.renderer.EmptyEntityRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.lwjgl.glfw.GLFW;

public class ElementalWandsClient implements ClientModInitializer {

    private static KeyBinding ultimateKey;

    @Override
    public void onInitializeClient() {
        ultimateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elementalwands.ultimate",
                GLFW.GLFW_KEY_X,
                new KeyBinding.Category(Identifier.of("elementalwands", "general"))));
        EntityRendererRegistry.register(ModEntities.BOULDER_PROJECTILE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CHILL_SNOWBALL, FlyingItemEntityRenderer::new);

        // Register empty renderers for particle-based entities
        EntityRendererRegistry.register(ModEntities.VACUUM_BLADE, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CALAMITY_TORNADO, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.INFERNO_WAVE, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SINGULARITY_BOLT, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOLLOW_PURPLE_ORB, EmptyEntityRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(ElementalWandsClient::tickClient);

        // Register HUD Overlay
        HudRenderCallback.EVENT.register(new com.anton.elementalwands.client.overlay.WandHudOverlay());
    }

    private static void tickClient(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null)
            return;
        if (client.currentScreen != null)
            return;

        if (!(client.player.getMainHandStack().getItem() instanceof AbstractWandItem))
            return;

        while (ultimateKey.wasPressed()) {
            ClientPlayNetworking.send(ModNetworking.CastUltimatePayload.INSTANCE);
        }
    }
}
