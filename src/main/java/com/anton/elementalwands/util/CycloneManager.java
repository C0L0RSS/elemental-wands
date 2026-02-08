package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class CycloneManager {

    private static final class Cyclone {
        private final Vec3d center;
        private final int radius;
        private final UUID casterUuid;
        private final int expiryTick;

        private Cyclone(Vec3d center, int radius, UUID casterUuid, int expiryTick) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Cyclone>> CYCLONES = new HashMap<>();

    private static final int DURATION_TICKS = 100;
    private static final int RADIUS = 8;

    private CycloneManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(CycloneManager::tickWorld);
    }

    @Deprecated(since = "2.2.0", forRemoval = false)
    public static void startCyclone(ServerWorld world, PlayerEntity caster, Vec3d center) {
        int now = world.getServer().getTicks();
        CYCLONES.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Cyclone(center, RADIUS, caster.getUuid(), now + DURATION_TICKS));

        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS,
                1.2f, 0.9f);
        world.spawnParticles(ParticleTypes.CLOUD, center.x, center.y + 0.5, center.z, 80, 1.8, 0.6, 1.8, 0.04);
    }

    private static void tickWorld(ServerWorld world) {
        List<Cyclone> cyclones = CYCLONES.get(world.getRegistryKey());
        if (cyclones == null || cyclones.isEmpty()) return;

        int now = world.getServer().getTicks();

        Iterator<Cyclone> it = cyclones.iterator();
        while (it.hasNext()) {
            Cyclone cyclone = it.next();

            if (now >= cyclone.expiryTick) {
                endBlast(world, cyclone);
                it.remove();
                continue;
            }

            tickCyclone(world, cyclone, now);
        }

        if (cyclones.isEmpty()) {
            CYCLONES.remove(world.getRegistryKey());
        }
    }

    private static void tickCyclone(ServerWorld world, Cyclone cyclone, int now) {
        Box box = new Box(
                cyclone.center.x - cyclone.radius,
                cyclone.center.y - cyclone.radius,
                cyclone.center.z - cyclone.radius,
                cyclone.center.x + cyclone.radius,
                cyclone.center.y + cyclone.radius,
                cyclone.center.z + cyclone.radius);

        PlayerEntity caster = world.getPlayerByUuid(cyclone.casterUuid);
        DamageSource damageSource = (caster == null)
                ? world.getDamageSources().magic()
                : world.getDamageSources().playerAttack(caster);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity living : entities) {
            if (caster != null && living == caster) continue;

            double dx = cyclone.center.x - living.getX();
            double dz = cyclone.center.z - living.getZ();
            double distSq = dx * dx + dz * dz;

            if (distSq > cyclone.radius * cyclone.radius) continue;

            double dist = Math.sqrt(distSq);
            if (dist < 0.001) continue;

            double strength = (cyclone.radius - dist) / cyclone.radius;
            Vec3d pull = new Vec3d(dx / dist, 0.0, dz / dist)
                    .multiply(0.10 + strength * 0.18);

            living.addVelocity(pull.x, 0.05 + strength * 0.08, pull.z);
            living.velocityModified = true;
            living.fallDistance = 0.0f;

            if (now % 20 == 0) {
                living.damage(world, damageSource, 1.0f);
            }
        }

        if (now % 2 == 0) {
            world.spawnParticles(ParticleTypes.CLOUD, cyclone.center.x, cyclone.center.y + 0.25, cyclone.center.z, 18,
                    cyclone.radius * 0.45, 0.35, cyclone.radius * 0.45, 0.02);
        }
    }

    private static void endBlast(ServerWorld world, Cyclone cyclone) {
        Box box = new Box(
                cyclone.center.x - cyclone.radius,
                cyclone.center.y - cyclone.radius,
                cyclone.center.z - cyclone.radius,
                cyclone.center.x + cyclone.radius,
                cyclone.center.y + cyclone.radius,
                cyclone.center.z + cyclone.radius);

        PlayerEntity caster = world.getPlayerByUuid(cyclone.casterUuid);

        for (Entity entity : world.getOtherEntities(caster, box, e -> e.isAlive() && !e.isSpectator())) {
            double dx = entity.getX() - cyclone.center.x;
            double dz = entity.getZ() - cyclone.center.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > cyclone.radius * cyclone.radius) continue;

            double dist = Math.sqrt(Math.max(distSq, 0.001));
            double strength = MathHelper.clamp((cyclone.radius - dist) / cyclone.radius, 0.0, 1.0);
            Vec3d push = new Vec3d(dx / dist, 0.0, dz / dist).multiply(1.4 + strength * 1.2);
            entity.addVelocity(push.x, 0.65 + strength * 0.35, push.z);
            entity.velocityModified = true;
        }

        world.playSound(null, BlockPos.ofFloored(cyclone.center), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(),
                SoundCategory.PLAYERS, 1.4f, 1.1f);
        world.spawnParticles(ParticleTypes.CLOUD, cyclone.center.x, cyclone.center.y + 0.25, cyclone.center.z, 140,
                cyclone.radius * 0.7, 0.6, cyclone.radius * 0.7, 0.08);
    }
}
