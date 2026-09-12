package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class TemporaryBlockManager {

    public record TemporaryPlacement(UUID id, int placedCount) {
        public boolean isEmpty() {
            return placedCount <= 0;
        }
    }

    private record TempBlocks(UUID id, Long2ObjectMap<BlockState> originalByPos, BlockState placedState,
            int expiryTick) {
    }

    private static final Map<RegistryKey<World>, List<TempBlocks>> TEMP = new HashMap<>();

    private TemporaryBlockManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TemporaryBlockManager::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                restoreAll(world);
            }
            TEMP.clear();
        });
    }

    public static int placeTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions, BlockState placedState,
            int durationTicks, Predicate<BlockState> canReplace) {
        return placeTrackedTemporaryBlocks(world, positions, placedState, durationTicks, canReplace).placedCount();
    }

    public static TemporaryPlacement placeTrackedTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions,
            BlockState placedState, int durationTicks, Predicate<BlockState> canReplace) {
        int now = world.getServer().getTicks();
        int expiryTick = now + durationTicks;

        Long2ObjectOpenHashMap<BlockState> originalByPos = new Long2ObjectOpenHashMap<>();
        for (BlockPos pos : positions) {
            BlockState existing = world.getBlockState(pos);
            if (!canReplace.test(existing)) continue;

            // A cell already owned by another tracked placement inherits that placement's
            // original, so the covered temporary block can never be written back as terrain.
            if (com.anton.elementalwands.arena.GuardianArenaManager.setTemporarySpellBlock(world, pos, placedState))
                originalByPos.put(pos.asLong(), claimTrackedOriginal(world, pos, existing));
        }

        if (originalByPos.isEmpty()) {
            return new TemporaryPlacement(new UUID(0L, 0L), 0);
        }

        RegistryKey<World> key = world.getRegistryKey();
        UUID id = UUID.randomUUID();
        TEMP.computeIfAbsent(key, _k -> new ArrayList<>())
                .add(new TempBlocks(id, originalByPos, placedState, expiryTick));
        return new TemporaryPlacement(id, originalByPos.size());
    }

    /** Transfer the underlying terrain to a new owner, only after its block write succeeds. */
    public static BlockState claimTrackedOriginal(ServerWorld world, BlockPos pos, BlockState existing) {
        List<TempBlocks> temp = TEMP.get(world.getRegistryKey());
        if (temp == null) return existing;
        long key = pos.asLong();
        for (TempBlocks batch : temp) {
            if (!existing.isOf(batch.placedState.getBlock())) continue;
            BlockState original = batch.originalByPos.remove(key);
            if (original != null) return original;
        }
        return existing;
    }

    /** Restore one cell only if this placement still owns it. Stale Nature cleanup is a no-op. */
    public static void restoreTemporaryBlock(ServerWorld world, TemporaryPlacement placement, BlockPos pos) {
        List<TempBlocks> batches = TEMP.get(world.getRegistryKey());
        if (batches == null || placement == null) return;
        for (TempBlocks batch : batches) {
            if (!batch.id().equals(placement.id())) continue;
            BlockState original = batch.originalByPos.get(pos.asLong());
            if (original == null) return;
            BlockState current = world.getBlockState(pos);
            if (!current.isOf(batch.placedState.getBlock())
                    || com.anton.elementalwands.arena.GuardianArenaManager.setTemporarySpellBlock(world, pos, original)) {
                batch.originalByPos.remove(pos.asLong());
            }
            return;
        }
    }

    public static List<BlockPos> placementPositions(ServerWorld world, TemporaryPlacement placement) {
        List<TempBlocks> batches=TEMP.get(world.getRegistryKey());
        if(batches==null || placement==null)return List.of();
        for(TempBlocks batch:batches)if(batch.id().equals(placement.id())) {
            List<BlockPos> positions=new ArrayList<>();
            for(long key:batch.originalByPos.keySet())positions.add(BlockPos.fromLong(key));
            return List.copyOf(positions);
        }
        return List.of();
    }

    public static void restoreTemporaryBlocks(ServerWorld world, TemporaryPlacement placement) {
        if (placement == null || placement.isEmpty()) return;

        List<TempBlocks> temp = TEMP.get(world.getRegistryKey());
        if (temp == null || temp.isEmpty()) return;

        Iterator<TempBlocks> it = temp.iterator();
        while (it.hasNext()) {
            TempBlocks blocks = it.next();
            if (!blocks.id().equals(placement.id())) continue;

            restore(world, blocks);
            it.remove();
            break;
        }

        if (temp.isEmpty()) {
            TEMP.remove(world.getRegistryKey());
        }
    }

    public static void forgetNaturePosition(ServerWorld world, BlockPos pos) {
        List<TempBlocks> temp = TEMP.get(world.getRegistryKey());
        if (temp == null) return;
        for (TempBlocks blocks : temp) {
            if (com.anton.elementalwands.registry.ModSpellBlocks.isNatureGrowth(blocks.placedState))
                blocks.originalByPos.remove(pos.asLong());
        }
    }

    private static void tickWorld(ServerWorld world) {
        List<TempBlocks> temp = TEMP.get(world.getRegistryKey());
        if (temp == null || temp.isEmpty()) return;

        int now = world.getServer().getTicks();

        Iterator<TempBlocks> it = temp.iterator();
        while (it.hasNext()) {
            TempBlocks blocks = it.next();
            if (now < blocks.expiryTick) continue;

            restore(world, blocks);
            it.remove();
        }

        if (temp.isEmpty()) {
            TEMP.remove(world.getRegistryKey());
        }
    }

    private static void restoreAll(ServerWorld world) {
        List<TempBlocks> temp = TEMP.remove(world.getRegistryKey());
        if (temp == null || temp.isEmpty()) return;

        // Later placements may temporarily cover earlier ones. Reverse order
        // reconstructs that stack before the oldest placement restores terrain.
        for (int index = temp.size() - 1; index >= 0; index--) {
            restore(world, temp.get(index));
        }
    }

    private static void restore(ServerWorld world, TempBlocks blocks) {
        for (Long2ObjectMap.Entry<BlockState> entry : blocks.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            BlockState current = world.getBlockState(pos);

            if (current.isOf(blocks.placedState.getBlock())) {
                com.anton.elementalwands.arena.GuardianArenaManager.setTemporarySpellBlock(world, pos, entry.getValue());
            }
        }
    }
}
