package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.GuardianPulseRules;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Applies the core pulse's pull on top of the local player's own movement. */
public final class GuardianPullClient {
    private GuardianPullClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.GuardianPullPayload.ID, (payload, context) -> {
            var player = context.client().player;
            if (player == null || !player.isAlive() || player.getAbilities().flying) return;
            double share = player.isOnGround() ? 1 : GuardianPulseRules.AIR_SHARE;
            player.addVelocity(payload.x() * share, 0, payload.z() * share);
        });
    }
}
