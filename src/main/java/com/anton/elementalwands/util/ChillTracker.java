package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class ChillTracker {

    private record ChillData(int stacks, int lastHitTick) {
    }

    private static final Map<RegistryKey<World>, Map<UUID, ChillData>> CHILL = new HashMap<>();
    private static final int CLEAR_DELAY = 100;

    private ChillTracker() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(ChillTracker::tickWorld);
    }

    public static void addStack(ServerWorld world, LivingEntity target) {
        if (!target.isAlive())
            return;

        int now = world.getServer().getTicks();
        RegistryKey<World> key = world.getRegistryKey();
        Map<UUID, ChillData> map = CHILL.computeIfAbsent(key, _k -> new HashMap<>());

        ChillData existing = map.get(target.getUuid());
        int currentStacks = 0;

        if (existing != null) {
            // If we are within the clear window, keep stacks, otherwise reset
            if (now - existing.lastHitTick < CLEAR_DELAY) {
                currentStacks = existing.stacks;
            }
        }

        int newStacks = Math.min(currentStacks + 1, 5);
        map.put(target.getUuid(), new ChillData(newStacks, now));

        applyEffects(target, newStacks);
    }

    public static int getStacks(LivingEntity entity) {
        World world = entity.getEntityWorld();
        if (!(world instanceof ServerWorld sw))
            return 0;

        Map<UUID, ChillData> map = CHILL.get(sw.getRegistryKey());
        if (map == null)
            return 0;

        ChillData data = map.get(entity.getUuid());
        // If data exists but is stale (expired), return 0
        if (data != null && (sw.getServer().getTicks() - data.lastHitTick >= CLEAR_DELAY)) {
            return 0;
        }

        return data != null ? data.stacks : 0;
    }

    public static void clearFrostStacks(ServerWorld world, LivingEntity entity) {
        Map<UUID, ChillData> map = CHILL.get(world.getRegistryKey());
        if (map != null) {
            map.remove(entity.getUuid());
        }
    }

    private static void applyEffects(LivingEntity target, int stacks) {
        // 1 stack = Slowness I (amplifier 0), 2 stacks = Slowness II (amplifier 1), up to 5 = Slowness V.
        // Duration refreshes to 60 ticks (3s)
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, stacks - 1, false, true, true));
        target.setFrozenTicks(Math.min(target.getFrozenTicks() + 40, 300));
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, ChillData> map = CHILL.get(world.getRegistryKey());
        if (map == null || map.isEmpty())
            return;

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, ChillData>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChillData> entry = it.next();
            ChillData data = entry.getValue();

            // Auto-clear if no hits for 100 ticks
            if (now - data.lastHitTick >= CLEAR_DELAY) {
                it.remove();
            }
        }

        if (map.isEmpty()) {
            CHILL.remove(world.getRegistryKey());
        }
    }
}
