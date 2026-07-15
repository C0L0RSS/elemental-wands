package com.anton.elementalwands.util;

import com.anton.elementalwands.network.ModNetworking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Tracks the Nature wand's "Entangle" debuff. Vines wrap a target with each application;
 * the more they stack, the more the prey is slowed, until at the cap they are fully rooted.
 *
 * <p>Stacks also linger on the <em>caster</em>: while entangled, a wizard's own spells recover
 * twice as slowly (see {@code AbstractWandItem} and {@code WandHudOverlay}).
 */
public final class EntangleTracker {

    /** Stack count at which the target is fully rooted (cannot walk). */
    public static final int MAX_STACKS = 5;

    private record EntangleData(int stacks, int lastHitTick) {
    }

    private static final Map<RegistryKey<World>, Map<UUID, EntangleData>> ENTANGLED = new HashMap<>();
    private static final int CLEAR_DELAY = 100;

    private EntangleTracker() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(EntangleTracker::tickWorld);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
                clearStacks(world, entity);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer.getEntityWorld() instanceof ServerWorld world) {
                clearStacks(world, oldPlayer);
            }
            syncPlayer(newPlayer);
        });
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living) {
                int stacks = getStacks(living);
                if (stacks > 0) {
                    ModNetworking.syncEntangleStacks(player, living, stacks);
                }
            }
        });
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living) {
                ModNetworking.syncEntangleStacks(player, living, 0);
            }
        });
    }

    public static void addStack(ServerWorld world, LivingEntity target) {
        if (!target.isAlive())
            return;

        int now = world.getServer().getTicks();
        RegistryKey<World> key = world.getRegistryKey();
        Map<UUID, EntangleData> map = ENTANGLED.computeIfAbsent(key, _k -> new HashMap<>());

        EntangleData existing = map.get(target.getUuid());
        int currentStacks = 0;

        if (existing != null) {
            // If we are within the clear window, keep stacks, otherwise reset
            if (now - existing.lastHitTick < CLEAR_DELAY) {
                currentStacks = existing.stacks;
            }
        }

        int newStacks = Math.min(currentStacks + 1, MAX_STACKS);
        map.put(target.getUuid(), new EntangleData(newStacks, now));

        applyEffects(target, newStacks);
        spawnVineParticles(world, target, newStacks);
        ModNetworking.syncEntangleStacks(target, newStacks);
    }

    public static int getStacks(LivingEntity entity) {
        World world = entity.getEntityWorld();
        if (!(world instanceof ServerWorld sw))
            return 0;

        Map<UUID, EntangleData> map = ENTANGLED.get(sw.getRegistryKey());
        if (map == null)
            return 0;

        EntangleData data = map.get(entity.getUuid());
        // If data exists but is stale (expired), return 0
        if (data != null && (sw.getServer().getTicks() - data.lastHitTick >= CLEAR_DELAY)) {
            return 0;
        }

        return data != null ? data.stacks : 0;
    }

    public static void clearStacks(ServerWorld world, LivingEntity entity) {
        Map<UUID, EntangleData> map = ENTANGLED.get(world.getRegistryKey());
        boolean removed = false;
        if (map != null) {
            removed = map.remove(entity.getUuid()) != null;
            if (map.isEmpty()) {
                ENTANGLED.remove(world.getRegistryKey());
            }
        }
        if (removed) {
            ModNetworking.syncEntangleStacks(entity, 0);
        }
    }

    public static void syncPlayer(ServerPlayerEntity player) {
        ModNetworking.syncEntangleStacks(player, player, getStacks(player));
    }

    private static void applyEffects(LivingEntity target, int stacks) {
        // Stacks 1-4 ramp Slowness I..IV (amplifier = stacks - 1). At the cap the vines pin
        // the target in place: Slowness VII (amplifier 6) reduces movement speed to zero.
        if (stacks >= MAX_STACKS) {
            // Refresh on a shorter window so the root holds only while vines keep biting.
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 6, false, true, true));
        } else {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, stacks - 1, false, true, true));
        }
    }

    private static void spawnVineParticles(ServerWorld world, LivingEntity target, int stacks) {
        int count = 4 + stacks * 2;
        double spread = 0.3 + stacks * 0.05;
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getBodyY(0.4), target.getZ(),
                count, spread, target.getHeight() * 0.4, spread, 0.01);
        if (stacks >= MAX_STACKS) {
            // Roots erupting from the ground when fully pinned.
            world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    target.getX(), target.getY() + 0.1, target.getZ(),
                    8, 0.3, 0.05, 0.3, 0.0);
        }
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, EntangleData> map = ENTANGLED.get(world.getRegistryKey());
        if (map == null || map.isEmpty())
            return;

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, EntangleData>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, EntangleData> entry = it.next();
            EntangleData data = entry.getValue();

            // Auto-clear if no hits for 100 ticks
            if (now - data.lastHitTick >= CLEAR_DELAY) {
                Entity entity = world.getEntityAnyDimension(entry.getKey());
                if (entity instanceof LivingEntity living) {
                    ModNetworking.syncEntangleStacks(living, 0);
                }
                it.remove();
            }
        }

        if (map.isEmpty()) {
            ENTANGLED.remove(world.getRegistryKey());
        }
    }
}
