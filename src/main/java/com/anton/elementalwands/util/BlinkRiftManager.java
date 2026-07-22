package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
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
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RIFTS.clear());
    }

    public static void createRift(ServerWorld world, PlayerEntity caster, Vec3d position, int durationTicks) {
        int now = world.getServer().getTicks();
        int expiryTick = now + durationTicks;

        RIFTS.computeIfAbsent(world.getRegistryKey(), _k -> new HashMap<>())
                .put(caster.getUuid(), new Rift(position, expiryTick));

        spawnRiftOpen(world, position);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(position),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9f, 0.65f);
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
            spawnRiftClosure(world, rift.position);
            byCaster.remove(caster.getUuid());
            return SwapResult.NO_RIFT;
        }

        Vec3d targetPos = rift.position;
        if (!isSafeForPlayer(world, caster, targetPos)) {
            spawnBlockedFeedback(world, caster.getEntityPos(), targetPos);
            return SwapResult.BLOCKED;
        }

        Vec3d from = caster.getEntityPos();
        caster.requestTeleport(targetPos.x, targetPos.y, targetPos.z);
        caster.fallDistance = 0.0f;
        caster.velocityModified = true;

        spawnSwapFold(world, from, false);
        spawnSwapFold(world, targetPos, true);
        spawnRiftClosure(world, targetPos);
        byCaster.remove(caster.getUuid());
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
            if (caster == null || !caster.isAlive() || caster.isSpectator()) {
                spawnRiftClosure(world, rift.position);
                it.remove();
                continue;
            }
            if (now >= rift.expiryTick) {
                spawnRiftClosure(world, rift.position);
                it.remove();
                continue;
            }

            if (now % 2 == 0) {
                spawnActiveRift(world, rift, now);
            }
        }

        if (byCaster.isEmpty()) {
            RIFTS.remove(world.getRegistryKey());
        }
    }

    private static void spawnRiftOpen(ServerWorld world, Vec3d feetPos) {
        Vec3d center = feetPos.add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_RIFT,
                center.x, center.y, center.z, 3, 0.04, 0.14, 0.04, 0.0);
        world.spawnParticles(ModParticles.SPACE_GRAVITY_LENS,
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_MOTE,
                center.x, center.y, center.z, 16, 0.65, 0.9, 0.65, 0.025);
    }

    private static void spawnActiveRift(ServerWorld world, Rift rift, int now) {
        Vec3d center = rift.position.add(0.0, 1.0, 0.0);
        int remainingTicks = Math.max(1, rift.expiryTick - now);
        int visibleFragments = Math.min(6, (remainingTicks + 19) / 20);

        world.spawnParticles(ModParticles.SPACE_RIFT,
                center.x, center.y, center.z, 1, 0.015, 0.06, 0.015, 0.0);
        for (int i = 0; i < visibleFragments; i++) {
            double angle = now * 0.075 + i * (Math.PI * 2.0 / 6.0);
            double radius = 0.72 + (i % 2) * 0.10;
            Vec3d fragment = center.add(
                    Math.cos(angle) * radius,
                    Math.sin(angle * 1.7 + i) * 0.34,
                    Math.sin(angle) * radius);
            Vec3d inward = center.subtract(fragment).normalize().multiply(0.035);
            world.spawnParticles(ModParticles.SPACE_PINPOINT,
                    fragment.x, fragment.y, fragment.z, 0,
                    inward.x, inward.y, inward.z, 1.0);
        }

        if (remainingTicks <= 20 && now % 4 == 0) {
            world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                    center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnSwapFold(ServerWorld world, Vec3d feetPos, boolean unfolding) {
        Vec3d center = feetPos.add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_RIFT,
                center.x, center.y, center.z, 2, 0.03, 0.12, 0.03, 0.0);
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                center.x, center.y, center.z, unfolding ? 1 : 2, 0.04, 0.04, 0.04, 0.0);
        world.spawnParticles(ModParticles.SPACE_CONSUMPTION,
                center.x, center.y, center.z, 18, 0.48, 0.76, 0.48, unfolding ? 0.08 : 0.02);
    }

    private static void spawnBlockedFeedback(ServerWorld world, Vec3d casterPos, Vec3d riftPos) {
        Vec3d casterCenter = casterPos.add(0.0, 1.0, 0.0);
        Vec3d riftCenter = riftPos.add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                casterCenter.x, casterCenter.y, casterCenter.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_RIFT,
                riftCenter.x, riftCenter.y, riftCenter.z, 2, 0.02, 0.08, 0.02, 0.0);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(riftPos),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.65f, 0.45f);
    }

    private static void spawnRiftClosure(ServerWorld world, Vec3d feetPos) {
        Vec3d center = feetPos.add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_RIFT,
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                center.x, center.y, center.z, 2, 0.025, 0.025, 0.025, 0.0);
        world.spawnParticles(ModParticles.SPACE_PINPOINT,
                center.x, center.y, center.z, 3, 0.05, 0.10, 0.05, 0.0);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(feetPos),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.55f, 1.75f);
    }

    private static boolean isSafeForPlayer(ServerWorld world, PlayerEntity player, Vec3d feetPos) {
        if (feetPos.y < world.getBottomY() + 1 || feetPos.y > world.getTopYInclusive() - 2) {
            return false;
        }

        Box translated = player.getBoundingBox().offset(feetPos.subtract(player.getEntityPos()));
        return world.isSpaceEmpty(player, translated);
    }
}
