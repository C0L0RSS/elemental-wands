package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BlinkRiftManager {

    private record Rift(Vec3d position, int expiryTick) {
    }

    public enum SwapResult {
        NO_RIFT,
        SWAPPED,
        BLOCKED
    }

    private static final Map<RegistryKey<World>, Map<UUID, Rift>> RIFTS = new HashMap<>();

    private BlinkRiftManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(BlinkRiftManager::tickWorld);
    }

    public static void createRift(ServerWorld world, PlayerEntity caster, Vec3d position, int durationTicks) {
        int now = world.getServer().getTicks();
        int expiryTick = now + durationTicks;

        RIFTS.computeIfAbsent(world.getRegistryKey(), _k -> new HashMap<>())
                .put(caster.getUuid(), new Rift(position, expiryTick));

        world.spawnParticles(ParticleTypes.PORTAL, position.x, position.y + 1.0, position.z, 30, 0.4, 0.6, 0.4, 0.15);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9f,
                1.4f);
    }

    public static boolean hasActiveRift(ServerWorld world, PlayerEntity caster) {
        Map<UUID, Rift> byCaster = RIFTS.get(world.getRegistryKey());
        if (byCaster == null) {
            return false;
        }

        Rift rift = byCaster.get(caster.getUuid());
        if (rift == null) {
            return false;
        }

        return world.getServer().getTicks() < rift.expiryTick;
    }

    public static SwapResult trySwapWithRift(ServerWorld world, PlayerEntity caster) {
        Map<UUID, Rift> byCaster = RIFTS.get(world.getRegistryKey());
        if (byCaster == null) {
            return SwapResult.NO_RIFT;
        }

        Rift rift = byCaster.get(caster.getUuid());
        if (rift == null) {
            return SwapResult.NO_RIFT;
        }

        int now = world.getServer().getTicks();
        if (now >= rift.expiryTick) {
            byCaster.remove(caster.getUuid());
            return SwapResult.NO_RIFT;
        }

        Vec3d targetPos = rift.position;
        if (!isSafeForPlayer(world, caster, targetPos)) {
            return SwapResult.BLOCKED;
        }

        Vec3d from = caster.getEntityPos();
        caster.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
        caster.fallDistance = 0.0f;
        caster.velocityModified = true;

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 20, 0.4, 0.6, 0.4, 0.1);
        world.spawnParticles(ParticleTypes.PORTAL, targetPos.x, targetPos.y + 1.0, targetPos.z, 40, 0.45, 0.8, 0.45,
                0.2);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f,
                1.1f);
        return SwapResult.SWAPPED;
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, Rift> byCaster = RIFTS.get(world.getRegistryKey());
        if (byCaster == null || byCaster.isEmpty()) {
            return;
        }

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, Rift>> it = byCaster.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Rift> entry = it.next();
            UUID casterUuid = entry.getKey();
            Rift rift = entry.getValue();

            PlayerEntity caster = world.getPlayerByUuid(casterUuid);
            if (caster == null || !caster.isAlive() || caster.isSpectator() || now >= rift.expiryTick) {
                it.remove();
                continue;
            }

            if (now % 2 == 0) {
                Vec3d pos = rift.position;
                world.spawnParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 8, 0.2, 0.5, 0.2, 0.04);
                world.spawnParticles(ParticleTypes.WITCH, pos.x, pos.y + 0.7, pos.z, 1, 0.2, 0.2, 0.2, 0.0);
            }
        }

        if (byCaster.isEmpty()) {
            RIFTS.remove(world.getRegistryKey());
        }
    }

    private static boolean isSafeForPlayer(ServerWorld world, PlayerEntity player, Vec3d feetPos) {
        if (feetPos.y < world.getBottomY() + 1 || feetPos.y > world.getTopYInclusive() - 2) {
            return false;
        }

        Box translated = player.getBoundingBox().offset(feetPos.subtract(player.getEntityPos()));
        return world.isSpaceEmpty(player, translated);
    }
}
