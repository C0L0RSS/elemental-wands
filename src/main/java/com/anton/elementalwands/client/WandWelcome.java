package com.anton.elementalwands.client;

import com.anton.elementalwands.client.screen.WandHubScreen;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Wait for loading and other screens to finish; acknowledge only after showing the scroll. */
public final class WandWelcome {
    private static boolean pending;
    public static void queue() { pending = true; }
    public static void reset() { pending = false; }
    public static boolean canShow(boolean queued, boolean hasPlayer, boolean alive, boolean spectator,
            boolean screenOpen, boolean overlayOpen, boolean editable) {
        return queued && hasPlayer && alive && !spectator && !screenOpen && !overlayOpen && editable;
    }
    public static void tick(MinecraftClient client) {
        if (!canShow(pending, client.player != null, client.player != null && client.player.isAlive(),
                client.player != null && client.player.isSpectator(), client.currentScreen != null,
                client.getOverlay() != null, ClientPlayerData.canEdit())) return;
        client.setScreen(WandHubScreen.guide());
        pending = false;
        ClientPlayNetworking.send(new ModNetworking.HubActionPayload("welcome_seen", ClientPlayerData.getAffinity().name(), 0, ""));
    }
    private WandWelcome() {}
}
