package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Nature wand's ultimate: Overgrowth. A roiling cloud of pollen and spores erupts around the
 * caster, choking enemies with poison and slowing them, while any {@link SeedlingManager} seedlings
 * caught inside spread their thickets faster and farther.
 */
public final class OvergrowthManager {

    private static final int DURATION_TICKS = 240;
    private static final double RADIUS = 12.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    private static final double Y_BELOW = 8.0;
    private static final double Y_ABOVE = 16.0;
    private static final int POLLEN_PARTICLES_PER_TICK = 40;
    private static final int ENTANGLE_INTERVAL = 40;

    private static final class Overgrowth {
        final UUID casterUuid;
        final Vec3d center;
        final int castTick;
        final int expiryTick;
        final Set<UUID> recentlyPresent = new HashSet<>();
        final Map<UUID, Integer> firstSeenTick = new HashMap<>();

        Overgrowth(UUID casterUuid, Vec3d center, int castTick, int expiryTick) {
            this.casterUuid = casterUuid;
            this.center = center;
            this.castTick = castTick;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Overgrowth>> ACTIVE = new HashMap<>();

    private OvergrowthManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(OvergrowthManager::tickWorld);
    }

    public static void startOvergrowth(ServerWorld world, PlayerEntity caster) {
        int now = world.getServer().getTicks();
        Vec3d center = caster.getEntityPos();
        Overgrowth w = new Overgrowth(caster.getUuid(), center, now, now + DURATION_TICKS);
        ACTIVE.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>()).add(w);

        BlockPos soundPos = BlockPos.ofFloored(center);
        world.playSound(null, soundPos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2f, 0.55f);
        world.playSound(null, soundPos, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    public static boolean isInOvergrowth(ServerWorld world, BlockPos pos) {
        List<Overgrowth> list = ACTIVE.get(world.getRegistryKey());
        if (list == null || list.isEmpty()) return false;
        for (Overgrowth w : list) {
            if (containsPos(w, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPos(Overgrowth w, double x, double y, double z) {
        double dx = x - w.center.x;
        double dz = z - w.center.z;
        if (dx * dx + dz * dz > RADIUS_SQ) return false;
        double dy = y - w.center.y;
        return dy >= -Y_BELOW && dy < Y_ABOVE;
    }

    private static void tickWorld(ServerWorld world) {
        List<Overgrowth> list = ACTIVE.get(world.getRegistryKey());
        if (list == null || list.isEmpty()) return;

        int now = world.getServer().getTicks();
        Iterator<Overgrowth> it = list.iterator();
        while (it.hasNext()) {
            Overgrowth w = it.next();
            if (now >= w.expiryTick) {
                it.remove();
                continue;
            }
            tickOvergrowth(world, w, now);
        }

        if (list.isEmpty()) {
            ACTIVE.remove(world.getRegistryKey());
        }
    }

    private static void tickOvergrowth(ServerWorld world, Overgrowth w, int now) {
        spawnPollenParticles(world, w);

        Box box = new Box(
                w.center.x - RADIUS, w.center.y - Y_BELOW, w.center.z - RADIUS,
                w.center.x + RADIUS, w.center.y + Y_ABOVE, w.center.z + RADIUS);
        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator());

        Set<UUID> nowPresent = new HashSet<>();
        for (LivingEntity entity : entities) {
            if (!containsPos(w, entity.getX(), entity.getY() + 0.1, entity.getZ())) continue;
            nowPresent.add(entity.getUuid());

            boolean isCaster = entity.getUuid().equals(w.casterUuid);
            if (isCaster) {
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 0, false, false, true));
                continue;
            }

            // Choking spores: poison + slow while inside the cloud.
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 0, false, false, true));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, false, true));

            Integer firstSeen = w.firstSeenTick.get(entity.getUuid());
            if (firstSeen == null) {
                w.firstSeenTick.put(entity.getUuid(), now);
            } else {
                int age = now - firstSeen;
                if (age > 0 && age % ENTANGLE_INTERVAL == 0) {
                    EntangleTracker.addStack(world, entity);
                    SeedlingManager.applyThorns(world, entity, w.casterUuid);
                }
            }
        }

        for (UUID prev : w.recentlyPresent) {
            if (nowPresent.contains(prev)) continue;
            if (prev.equals(w.casterUuid)) continue;
            LivingEntity exited = findLivingByUuid(world, prev);
            if (exited != null && exited.isAlive()) {
                // Lingering spores in the lungs after stumbling out of the cloud.
                exited.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0, false, false, true));
            }
            w.firstSeenTick.remove(prev);
        }

        w.recentlyPresent.clear();
        w.recentlyPresent.addAll(nowPresent);
    }

    private static LivingEntity findLivingByUuid(ServerWorld world, UUID uuid) {
        return world.getEntity(uuid) instanceof LivingEntity living ? living : null;
    }

    private static void spawnPollenParticles(ServerWorld world, Overgrowth w) {
        for (int i = 0; i < POLLEN_PARTICLES_PER_TICK; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(world.random.nextDouble()) * RADIUS;
            double px = w.center.x + Math.cos(angle) * dist;
            double pz = w.center.z + Math.sin(angle) * dist;
            double py = w.center.y + (world.random.nextDouble() * (Y_BELOW + Y_ABOVE) - Y_BELOW);
            int roll = i % 3;
            switch (roll) {
                case 0 -> world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, px, py, pz, 1, 0.15, 0.15, 0.15, 0.005);
                case 1 -> world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 1, 0.2, 0.2, 0.2, 0.01);
                default -> world.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, px, py, pz, 1, 0.15, 0.15, 0.15, 0.01);
            }
        }
    }
}
