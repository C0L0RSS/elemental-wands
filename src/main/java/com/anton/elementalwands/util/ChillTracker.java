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

    private record ChillData(int stacks, int expiryTick) {
    }

    private static final Map<RegistryKey<World>, Map<UUID, ChillData>> CHILL = new HashMap<>();

    private ChillTracker() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(ChillTracker::tickWorld);
    }

    public static void addStack(ServerWorld world, LivingEntity target) {
        if (!target.isAlive()) return;

        int now = world.getServer().getTicks();
        RegistryKey<World> key = world.getRegistryKey();
        Map<UUID, ChillData> map = CHILL.computeIfAbsent(key, _k -> new HashMap<>());

        ChillData existing = map.get(target.getUuid());
        int stacks = (existing != null && now < existing.expiryTick) ? existing.stacks : 0;

        stacks = Math.min(stacks + 1, 6);
        map.put(target.getUuid(), new ChillData(stacks, now + 100));

        if (stacks >= 6) {
            target.setFrozenTicks(Math.min(target.getFrozenTicks() + 200, 320));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3, false, true, true));
        } else {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, stacks - 1, false, true, true));
            target.setFrozenTicks(Math.min(target.getFrozenTicks() + 20, 200));
        }
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, ChillData> map = CHILL.get(world.getRegistryKey());
        if (map == null || map.isEmpty()) return;

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, ChillData>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChillData> entry = it.next();
            ChillData data = entry.getValue();
            if (now < data.expiryTick) continue;
            it.remove();
        }

        if (map.isEmpty()) {
            CHILL.remove(world.getRegistryKey());
        }
    }
}

