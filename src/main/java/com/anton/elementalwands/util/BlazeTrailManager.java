package com.anton.elementalwands.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class BlazeTrailManager {

    private static final Map<UUID, Integer> PENDING_TRAILS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(BlazeTrailManager::tick);
    }

    public static void addTrail(ServerPlayerEntity player, int durationTicks) {
        PENDING_TRAILS.put(player.getUuid(), durationTicks);
    }

    private static void tick(ServerWorld world) {
        if (PENDING_TRAILS.isEmpty())
            return;

        Iterator<Map.Entry<UUID, Integer>> it = PENDING_TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID uuid = entry.getKey();
            int ticksLeft = entry.getValue();

            if (ticksLeft <= 0) {
                it.remove();
                continue;
            }

            // Decrement
            entry.setValue(ticksLeft - 1);

            // Only process logic if the player is in *this* world
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getEntityWorld() != world) {
                // If player is offline or in another dimension, we can either
                // skip or remove. For simplicity, just skip this tick.
                // If they disconnected, they effectively lose the buff.
                // We'll just continue decrementing so it expires eventually.
                continue;
            }

            // Place fire every 2 ticks
            if (ticksLeft % 2 == 0) {
                BlockPos pos = player.getBlockPos();
                // Check if block at feet is air (or replaceable) to place fire
                if (world.getBlockState(pos).isAir()) {
                    // Use TemporaryBlockManager to place fire that lasts 40 ticks (2 seconds)
                    // We need a canReplace predicate? Standard is usually just air checking.
                    // TemporaryBlockManager needs a list of positions.
                    TemporaryBlockManager.placeTemporaryBlocks(
                            world,
                            java.util.Collections.singletonList(pos),
                            Blocks.FIRE.getDefaultState(),
                            40,
                            state -> state.isAir() || state.isReplaceable());
                }
            }
        }
    }
}
