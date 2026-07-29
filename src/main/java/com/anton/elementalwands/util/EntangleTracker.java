package com.anton.elementalwands.util;

import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModParticles;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
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
    private static final int ROOT_DURATION_TICKS = 40;

    private EntangleTracker() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(EntangleTracker::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ENTANGLED.clear());
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
                    ModNetworking.syncEntangleStacks(player, living, stacks,
                            getRootVisualTicksRemaining(living));
                }
            }
        });
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (entity instanceof LivingEntity living) {
                ModNetworking.syncEntangleStacks(player, living, 0, 0);
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
        if (newStacks > currentStacks) {
            spawnVineParticles(world, target, newStacks);
        }
        ModNetworking.syncEntangleStacks(target, newStacks,
                newStacks >= MAX_STACKS ? ROOT_DURATION_TICKS : 0);
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

    private static int getRootVisualTicksRemaining(LivingEntity entity) {
        World world = entity.getEntityWorld();
        if (!(world instanceof ServerWorld serverWorld)) return 0;

        Map<UUID, EntangleData> map = ENTANGLED.get(serverWorld.getRegistryKey());
        EntangleData data = map != null ? map.get(entity.getUuid()) : null;
        if (data == null || data.stacks < MAX_STACKS) return 0;

        int age = serverWorld.getServer().getTicks() - data.lastHitTick;
        return Math.max(0, ROOT_DURATION_TICKS - age);
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
            ModNetworking.syncEntangleStacks(entity, 0, 0);
        }
    }

    public static void syncPlayer(ServerPlayerEntity player) {
        ModNetworking.syncEntangleStacks(player, player, getStacks(player),
                getRootVisualTicksRemaining(player));
    }

    private static void applyEffects(LivingEntity target, int stacks) {
        // Stacks 1-4 ramp Slowness I..IV (amplifier = stacks - 1). At the cap the vines pin
        // the target in place: Slowness VII (amplifier 6) reduces movement speed to zero.
        if (stacks >= MAX_STACKS) {
            // Refresh on a shorter window so the root holds only while vines keep biting.
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, ROOT_DURATION_TICKS, 6, false, false, true));
        } else {
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 60, stacks - 1, false, false, true));
        }
    }

    private static void spawnVineParticles(ServerWorld world, LivingEntity target, int stacks) {
        int count = 3 + stacks * 2;
        double spread = 0.26 + stacks * 0.055;
        world.spawnParticles(ModParticles.NATURE_VINE,
                target.getX(), target.getBodyY(0.4), target.getZ(),
                count, spread, target.getHeight() * (0.16 + stacks * 0.055), spread, 0.008);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                target.getX(), target.getBodyY(0.48), target.getZ(),
                3 + stacks, spread, target.getHeight() * 0.32, spread, 0.012);
        if (stacks >= 3) {
            world.spawnParticles(ModParticles.NATURE_BLOOM,
                    target.getX(), target.getBodyY(stacks >= 5 ? 0.95 : 0.62), target.getZ(),
                    stacks >= 5 ? 3 : 1, spread * 0.72, 0.1, spread * 0.72, 0.0);
        }
        if (stacks >= MAX_STACKS) {
            // A flower crown above and a locked root ring below make the cap readable from
            // either first-person or across a fight.
            Vec3d base = target.getEntityPos().add(0.0, 0.06, 0.0);
            Vec3d crown = target.getEntityPos().add(0.0, target.getHeight() + 0.18, 0.0);
            NatureVfx.ring(world, ModParticles.NATURE_VINE, base,
                    Math.max(0.42, target.getWidth() * 0.72), 10, 0.0, 0.0);
            NatureVfx.ring(world, ModParticles.NATURE_BLOOM, crown,
                    Math.max(0.34, target.getWidth() * 0.58), 5, 0.0, Math.PI / 2.0);
            world.spawnParticles(ModParticles.NATURE_HEART,
                    crown.x, crown.y, crown.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ModParticles.NATURE_LEAF,
                    target.getX(), target.getY() + 0.1, target.getZ(),
                    10, 0.34, 0.08, 0.34, 0.012);
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
                    ModNetworking.syncEntangleStacks(living, 0, 0);
                }
                it.remove();
            }
        }

        if (map.isEmpty()) {
            ENTANGLED.remove(world.getRegistryKey());
        }
    }
}
