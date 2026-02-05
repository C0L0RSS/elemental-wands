package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class FrostZoneManager {

    private record FrostZone(BlockPos center, int radius, int expiryTick) {
    }

    private static final Map<RegistryKey<World>, List<FrostZone>> ZONES = new HashMap<>();

    private FrostZoneManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(FrostZoneManager::tickWorld);
    }

    public static void createZone(ServerWorld world, BlockPos center, int radius, int durationTicks) {
        int now = world.getServer().getTicks();
        ZONES.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new FrostZone(center, radius, now + durationTicks));

        // Visuals / Floor changes handled via TemporaryBlockManager call in Item class,
        // or we could double-up here. The Item class will handle the block placement to
        // keep this class focused on entity logic if desired.
        // But for "Create a 7x7 area", it's cleaner if this manager handles it or the
        // Item calls both.
        // Prompt said "Replace the ice wall with a Frost Zone."
        // I will let the Item class call TemporaryBlockManager for the blocks to keep
        // managers decoupled,
        // OR I can call it here. Calling it here ensures sync.

        List<BlockPos> floor = new ArrayList<>();
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                floor.add(center.add(i, -1, j)); // Floor layer
            }
        }

        TemporaryBlockManager.placeTemporaryBlocks(
                world,
                floor,
                Blocks.BLUE_ICE.getDefaultState(),
                durationTicks,
                state -> !state.isAir() && state.isSolidBlock(world, center) // Only replace solid blocks
        );
    }

    private static void tickWorld(ServerWorld world) {
        List<FrostZone> zones = ZONES.get(world.getRegistryKey());
        if (zones == null || zones.isEmpty())
            return;

        int now = world.getServer().getTicks();

        Iterator<FrostZone> it = zones.iterator();
        while (it.hasNext()) {
            FrostZone zone = it.next();
            if (now >= zone.expiryTick) {
                it.remove();
                continue;
            }

            applyEffects(world, zone);
        }

        if (zones.isEmpty()) {
            ZONES.remove(world.getRegistryKey());
        }
    }

    private static void applyEffects(ServerWorld world, FrostZone zone) {
        // Expand box: 7x7 area (radius 3) -> min = center - 3, max = center + 3.
        // Height: check reasonably high (e.g. 3 blocks)
        Box box = new Box(zone.center).expand(zone.radius, 2.0, zone.radius);

        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box, e -> true)) {
            boolean suppressed = false;

            // Check upward velocity (Jump)
            if (entity.getVelocity().y > 0.1) {
                suppressed = true;
            }

            // Apply logic
            if (suppressed) {
                // Reset velocity
                entity.setVelocity(entity.getVelocity().multiply(1, 0, 1));
                entity.velocityModified = true;

                // Apply Frost Stack
                ChillTracker.addStack(world, entity);
            }
        }
    }
}
