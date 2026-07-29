package com.anton.elementalwands.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BlazeTrailManager {

    private static final int FIRE_DURATION_TICKS = 40;
    private static final Map<UUID, Integer> PENDING_TRAILS = new HashMap<>();

    private BlazeTrailManager() {
    }

    public static void init() {
        // Server tick, not world tick: END_WORLD_TICK fires once per loaded dimension,
        // which burned a trail's remaining ticks down several times per game tick.
        ServerTickEvents.END_SERVER_TICK.register(BlazeTrailManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING_TRAILS.clear());
    }

    public static void addTrail(ServerPlayerEntity player, int durationTicks) {
        PENDING_TRAILS.put(player.getUuid(), durationTicks);
    }

    private static void tick(MinecraftServer server) {
        if (PENDING_TRAILS.isEmpty())
            return;

        Iterator<Map.Entry<UUID, Integer>> it = PENDING_TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int ticksLeft = entry.getValue();

            if (ticksLeft <= 0) {
                it.remove();
                continue;
            }

            entry.setValue(ticksLeft - 1);

            // An offline player keeps counting down so the trail expires on its own
            // schedule rather than resuming when they reconnect.
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            if (ticksLeft % 2 == 0) {
                ServerWorld world = player.getEntityWorld();
                BlockPos pos = player.getBlockPos();
                if (world.getBlockState(pos).isAir()) {
                    TemporaryBlockManager.placeTemporaryBlocks(
                            world,
                            List.of(pos),
                            Blocks.FIRE.getDefaultState(),
                            FIRE_DURATION_TICKS,
                            state -> state.isAir() || state.isReplaceable());
                }
            }
        }
    }
}
