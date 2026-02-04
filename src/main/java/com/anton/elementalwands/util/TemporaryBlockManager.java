package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class TemporaryBlockManager {

    private record TempBlocks(Long2ObjectMap<BlockState> originalByPos, BlockState placedState, int expiryTick) {
    }

    private static final Map<RegistryKey<World>, List<TempBlocks>> TEMP = new HashMap<>();

    private TemporaryBlockManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TemporaryBlockManager::tickWorld);
    }

    public static int placeTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions, BlockState placedState,
            int durationTicks, Predicate<BlockState> canReplace) {
        int now = world.getServer().getTicks();
        int expiryTick = now + durationTicks;

        Long2ObjectOpenHashMap<BlockState> originalByPos = new Long2ObjectOpenHashMap<>();
        for (BlockPos pos : positions) {
            BlockState existing = world.getBlockState(pos);
            if (!canReplace.test(existing)) continue;

            originalByPos.put(pos.asLong(), existing);
            world.setBlockState(pos, placedState, 3);
        }

        if (originalByPos.isEmpty()) return 0;

        RegistryKey<World> key = world.getRegistryKey();
        TEMP.computeIfAbsent(key, _k -> new ArrayList<>())
                .add(new TempBlocks(originalByPos, placedState, expiryTick));
        return originalByPos.size();
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

    private static void restore(ServerWorld world, TempBlocks blocks) {
        for (Long2ObjectMap.Entry<BlockState> entry : blocks.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            BlockState current = world.getBlockState(pos);

            if (current.isOf(blocks.placedState.getBlock())) {
                world.setBlockState(pos, entry.getValue(), 3);
            }
        }
    }
}

