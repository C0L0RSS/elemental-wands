package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Nature wand's secondary: from each planted seedling a strangling vine (the tendril) snakes
 * along the ground to the nearest enemy, then erupts into a spreading bloom of moss that slows,
 * entangles and thorn-damages anything standing in it.
 */
public final class TendrilBloomManager {

    private static final int TENDRIL_MAX_TICKS = 24;
    private static final double TENDRIL_SPEED_PER_TICK = 0.625;
    private static final int TENDRIL_GROWTH_LIFESPAN = 260;

    private static final int BLOOM_LIFESPAN = 260;
    private static final int BLOOM_GROWTH_INTERVAL = 20;
    private static final int BLOOM_MAX_RADIUS = 3;
    private static final int BLOOM_ENTANGLE_INTERVAL = 20;

    private static final class Tendril {
        final UUID seedlingId;
        final UUID casterUuid;
        final UUID targetUuid;
        Vec3d currentHead;
        Vec3d lastKnownTarget;
        final int startTick;
        final Set<BlockPos> placedPositions = new HashSet<>();
        final Map<BlockPos, BlockState> originals = new HashMap<>();

        Tendril(UUID seedlingId, UUID casterUuid, UUID targetUuid, Vec3d start, Vec3d lastKnown, int startTick) {
            this.seedlingId = seedlingId;
            this.casterUuid = casterUuid;
            this.targetUuid = targetUuid;
            this.currentHead = start;
            this.lastKnownTarget = lastKnown;
            this.startTick = startTick;
        }
    }

    private static final class Bloom {
        final UUID casterUuid;
        final BlockPos center;
        final int startTick;
        int currentRadius;
        int lastGrowthTick;
        final Set<BlockPos> placedPositions = new HashSet<>();
        final Map<BlockPos, BlockState> originals = new HashMap<>();
        final Map<UUID, Integer> lastEntangleTickByEntity = new HashMap<>();

        Bloom(UUID casterUuid, BlockPos center, int startTick) {
            this.casterUuid = casterUuid;
            this.center = center;
            this.startTick = startTick;
            this.currentRadius = 0;
            this.lastGrowthTick = startTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Tendril>> TENDRILS = new HashMap<>();
    private static final Map<RegistryKey<World>, List<Bloom>> BLOOMS = new HashMap<>();

    private TendrilBloomManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TendrilBloomManager::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TENDRILS.clear();
            BLOOMS.clear();
        });
    }

    public static void startTendril(ServerWorld world, PlayerEntity caster, UUID seedlingId,
                                     Vec3d seedlingAnchor, LivingEntity target) {
        int now = world.getServer().getTicks();
        Vec3d targetPos = target.getEntityPos().add(0, target.getHeight() * 0.5, 0);
        Tendril t = new Tendril(seedlingId, caster.getUuid(), target.getUuid(), seedlingAnchor, targetPos, now);
        TENDRILS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>()).add(t);

        world.spawnParticles(ModParticles.NATURE_BLOOM,
                seedlingAnchor.x, seedlingAnchor.y + 0.52, seedlingAnchor.z,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                seedlingAnchor.x, seedlingAnchor.y + 0.42, seedlingAnchor.z,
                10, 0.3, 0.26, 0.3, 0.018);

        world.playSound(null, BlockPos.ofFloored(seedlingAnchor), SoundEvents.ITEM_BONE_MEAL_USE,
                SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    private static void tickWorld(ServerWorld world) {
        int now = world.getServer().getTicks();

        List<Tendril> tendrils = TENDRILS.get(world.getRegistryKey());
        if (tendrils != null && !tendrils.isEmpty()) {
            Iterator<Tendril> it = tendrils.iterator();
            while (it.hasNext()) {
                Tendril t = it.next();
                boolean done = tickTendril(world, t, now);
                if (done) it.remove();
            }
            if (tendrils.isEmpty()) TENDRILS.remove(world.getRegistryKey());
        }

        List<Bloom> blooms = BLOOMS.get(world.getRegistryKey());
        if (blooms != null && !blooms.isEmpty()) {
            Iterator<Bloom> it = blooms.iterator();
            while (it.hasNext()) {
                Bloom b = it.next();
                boolean done = tickBloom(world, b, now);
                if (done) it.remove();
            }
            if (blooms.isEmpty()) BLOOMS.remove(world.getRegistryKey());
        }
    }

    private static boolean tickTendril(ServerWorld world, Tendril t, int now) {
        if (!SeedlingManager.isSeedlingAlive(world, t.seedlingId)) {
            return true;
        }

        int age = now - t.startTick;

        Vec3d targetPos = t.lastKnownTarget;
        Entity target = world.getEntity(t.targetUuid);
        if (target instanceof LivingEntity living && living.isAlive()) {
            targetPos = living.getEntityPos().add(0, living.getHeight() * 0.5, 0);
            t.lastKnownTarget = targetPos;
        }

        Vec3d toTarget = targetPos.subtract(t.currentHead);
        double dist = toTarget.length();

        if (dist <= TENDRIL_SPEED_PER_TICK || age >= TENDRIL_MAX_TICKS) {
            Vec3d arrival = dist <= TENDRIL_SPEED_PER_TICK ? targetPos : t.currentHead;
            spawnBloom(world, t.casterUuid, BlockPos.ofFloored(arrival), now);
            return true;
        }

        Vec3d prevHead = t.currentHead;
        Vec3d step = toTarget.normalize().multiply(TENDRIL_SPEED_PER_TICK);
        t.currentHead = t.currentHead.add(step);

        Set<BlockPos> newColumns = new HashSet<>();
        int substeps = Math.max(1, (int) Math.ceil(TENDRIL_SPEED_PER_TICK / 0.25));
        for (int i = 1; i <= substeps; i++) {
            double frac = i / (double) substeps;
            Vec3d p = prevHead.lerp(t.currentHead, frac);
            newColumns.add(BlockPos.ofFloored(p));
        }

        SeedlingManager.PlacementResult result = SeedlingManager.placeVerdantGrowth(
                world, newColumns, t.placedPositions, TENDRIL_GROWTH_LIFESPAN);
        t.placedPositions.addAll(result.placed());
        for (Map.Entry<BlockPos, BlockState> e : result.originals().entrySet()) {
            t.originals.putIfAbsent(e.getKey(), e.getValue());
        }

        NatureVfx.pairedTendril(world, prevHead, t.currentHead, age);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                t.currentHead.x, t.currentHead.y + 0.14, t.currentHead.z,
                3, 0.13, 0.08, 0.13, 0.01);
        return false;
    }

    private static void spawnBloom(ServerWorld world, UUID casterUuid, BlockPos center, int now) {
        Bloom b = new Bloom(casterUuid, center, now);
        b.currentRadius = 1;
        b.lastGrowthTick = now;

        List<BlockPos> diskCols = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                diskCols.add(new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz));
            }
        }

        SeedlingManager.PlacementResult result = SeedlingManager.placeVerdantGrowth(
                world, diskCols, b.placedPositions, BLOOM_LIFESPAN);
        b.placedPositions.addAll(result.placed());
        b.originals.putAll(result.originals());

        BLOOMS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>()).add(b);

        world.spawnParticles(ModParticles.NATURE_BLOOM,
                center.getX() + 0.5, center.getY() + 0.55, center.getZ() + 0.5,
                3, 0.35, 0.08, 0.35, 0.0);
        world.spawnParticles(ModParticles.NATURE_PETAL,
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                26, 0.8, 0.4, 0.8, 0.05);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                center.getX() + 0.5, center.getY() + 0.65, center.getZ() + 0.5,
                30, 0.8, 0.52, 0.8, 0.04);
        NatureVfx.growthRing(world, center, 1, now);
        world.playSound(null, center, SoundEvents.ITEM_BONE_MEAL_USE,
                SoundCategory.PLAYERS, 1.0f, 1.1f);
    }

    private static boolean tickBloom(ServerWorld world, Bloom b, int now) {
        int age = now - b.startTick;
        if (age >= BLOOM_LIFESPAN) {
            SeedlingManager.restoreBlocks(world, b.originals);
            Vec3d center = Vec3d.ofCenter(b.center);
            world.spawnParticles(ModParticles.NATURE_PETAL,
                    center.x, center.y + 0.4, center.z,
                    18, b.currentRadius * 0.5, 0.24, b.currentRadius * 0.5, 0.025);
            return true;
        }

        if (b.currentRadius < BLOOM_MAX_RADIUS && now >= b.lastGrowthTick + BLOOM_GROWTH_INTERVAL) {
            b.currentRadius++;
            b.lastGrowthTick = now;

            List<BlockPos> ringCols = SeedlingManager.chebyshevRingColumns(b.center, b.currentRadius);
            int remainingLife = Math.max(20, BLOOM_LIFESPAN - age);
            SeedlingManager.PlacementResult result = SeedlingManager.placeVerdantGrowth(
                    world, ringCols, b.placedPositions, remainingLife);
            b.placedPositions.addAll(result.placed());
            for (Map.Entry<BlockPos, BlockState> e : result.originals().entrySet()) {
                b.originals.putIfAbsent(e.getKey(), e.getValue());
            }

            NatureVfx.growthRing(world, b.center, b.currentRadius, now);
            world.playSound(null, b.center, SoundEvents.BLOCK_FLOWERING_AZALEA_BREAK,
                    SoundCategory.PLAYERS, 0.38f, 1.25f + b.currentRadius * 0.05f);
        }

        if ((now + b.center.getX() * 3 + b.center.getZ() * 5) % 10 == 0) {
            Vec3d center = Vec3d.ofCenter(b.center);
            double radius = Math.max(1.0, b.currentRadius - 0.15);
            NatureVfx.ring(world, ModParticles.NATURE_VINE, center,
                    radius, 6, -0.36, now * 0.025);
            NatureVfx.ring(world, ModParticles.NATURE_BLOOM, center,
                    radius * 0.78, 4, -0.24, now * -0.035);
            world.spawnParticles(ModParticles.NATURE_POLLEN,
                    center.x, center.y + 0.12, center.z,
                    5, radius * 0.45, 0.18, radius * 0.45, 0.008);
        }

        applyBloomEffects(world, b, now);
        return false;
    }

    private static void applyBloomEffects(ServerWorld world, Bloom b, int now) {
        if (b.placedPositions.isEmpty()) return;

        int r = BLOOM_MAX_RADIUS + 1;
        Box box = new Box(
                b.center.getX() - r, b.center.getY() - 0.5, b.center.getZ() - r,
                b.center.getX() + r + 1, b.center.getY() + 3.5, b.center.getZ() + r + 1);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity e : entities) {
            BlockPos feet = e.getBlockPos();
            boolean inZone = b.placedPositions.contains(feet) || b.placedPositions.contains(feet.down());
            if (!inZone) continue;

            if (e.getUuid().equals(b.casterUuid)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 0, false, false, true));
                continue;
            }

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, false, true));

            Integer lastTick = b.lastEntangleTickByEntity.get(e.getUuid());
            if (lastTick == null || now - lastTick >= BLOOM_ENTANGLE_INTERVAL) {
                EntangleTracker.addStack(world, e);
                SeedlingManager.applyThorns(world, e, b.casterUuid);
                b.lastEntangleTickByEntity.put(e.getUuid(), now);
            }
        }
    }
}
