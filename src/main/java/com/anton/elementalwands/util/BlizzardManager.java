package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.registry.ModEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Box;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;

public final class BlizzardManager {

    private static final class Blizzard {
        private final Vec3d center;
        private final int radius;
        private final UUID casterUuid;
        private final int expiryTick;
        private int nextWaveTick;

        private Blizzard(Vec3d center, int radius, UUID casterUuid, int expiryTick, int nextWaveTick) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.expiryTick = expiryTick;
            this.nextWaveTick = nextWaveTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Blizzard>> BLIZZARDS = new HashMap<>();

    private static final int DURATION_TICKS = 160;
    private static final int RADIUS = 25;
    private static final int WAVE_INTERVAL_TICKS = 5;
    private static final int SNOWBALLS_PER_WAVE = 3;

    private BlizzardManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(BlizzardManager::tickWorld);
    }

    public static void startBlizzard(ServerWorld world, PlayerEntity caster, Vec3d center) {
        int now = world.getServer().getTicks();

        BLIZZARDS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Blizzard(center, RADIUS, caster.getUuid(), now + DURATION_TICKS, now));

        TemporarySnowManager.createSnowField(world, BlockPos.ofFloored(center), RADIUS, DURATION_TICKS + 60);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 1.0, center.z, 200, 8.0, 4.0, 8.0, 0.02);
    }

    private static void tickWorld(ServerWorld world) {
        List<Blizzard> blizzards = BLIZZARDS.get(world.getRegistryKey());
        if (blizzards == null || blizzards.isEmpty())
            return;

        int now = world.getServer().getTicks();

        Iterator<Blizzard> it = blizzards.iterator();
        while (it.hasNext()) {
            Blizzard blizzard = it.next();

            // Apply Area Effects (Blindness + Freeze)
            applyBlizzardEffects(world, blizzard);

            if (now >= blizzard.expiryTick) {
                triggerFinale(world, blizzard);
                it.remove();
                continue;
            }

            if (now >= blizzard.nextWaveTick) {
                spawnWave(world, blizzard);
                blizzard.nextWaveTick = now + WAVE_INTERVAL_TICKS;
            }
        }

        if (blizzards.isEmpty()) {
            BLIZZARDS.remove(world.getRegistryKey());
        }
    }

    private static void applyBlizzardEffects(ServerWorld world, Blizzard blizzard) {
        Box box = Box.of(blizzard.center, blizzard.radius * 2, 24.0, blizzard.radius * 2);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.getUuid() != blizzard.casterUuid);

        for (LivingEntity target : targets) {
            // Whiteout Effect
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false));

            // Aggressive Freezing (3x faster -> +5 ticks per tick (since normal is
            // slow-ish? or just +3))
            // Vanilla powder snow adds some ticks. We'll add +3 ticks directly.
            target.setFrozenTicks(Math.min(target.getFrozenTicks() + 3, target.getMinFreezeDamageTicks() + 100));
        }
    }

    private static void triggerFinale(ServerWorld world, Blizzard blizzard) {
        Box box = Box.of(blizzard.center, blizzard.radius * 2, 24.0, blizzard.radius * 2);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.getUuid() != blizzard.casterUuid);

        for (LivingEntity target : targets) {
            placeIceRing(world, target.getBlockPos());
        }
    }

    private static void placeIceRing(ServerWorld world, BlockPos center) {
        List<BlockPos> validPositions = new ArrayList<>();

        int[][] card = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        for (int[] off : card) {
            validPositions.add(center.add(off[0], 0, off[1]));
            validPositions.add(center.add(off[0], 1, off[1]));
        }

        TemporaryBlockManager.placeTemporaryBlocks(
                world,
                validPositions,
                Blocks.PACKED_ICE.getDefaultState(),
                100, // 5 seconds entrapment
                state -> state.isAir() || state.isReplaceable());
    }

    private static void spawnWave(ServerWorld world, Blizzard blizzard) {
        PlayerEntity caster = world.getPlayerByUuid(blizzard.casterUuid);
        if (caster == null)
            return;

        for (int i = 0; i < SNOWBALLS_PER_WAVE; i++) {
            double angle = world.random.nextDouble() * MathHelper.TAU;
            double dist = Math.sqrt(world.random.nextDouble()) * blizzard.radius;
            double x = blizzard.center.x + Math.cos(angle) * dist;
            double z = blizzard.center.z + Math.sin(angle) * dist;

            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
            double y = topY + 12.0 + world.random.nextDouble() * 3.0;

            com.anton.elementalwands.entity.ChillSnowballEntity snowball = new com.anton.elementalwands.entity.ChillSnowballEntity(
                    ModEntities.CHILL_SNOWBALL,
                    world);
            snowball.setOwner(caster);
            snowball.setPosition(x, y, z);
            snowball.setVelocity(0.0, -1.25 - world.random.nextDouble() * 0.35, 0.0);
            world.spawnEntity(snowball);
        }

        world.spawnParticles(ParticleTypes.SNOWFLAKE, blizzard.center.x, blizzard.center.y + 1.0, blizzard.center.z, 40,
                blizzard.radius * 0.8, 1.2, blizzard.radius * 0.8, 0.02);
    }
}
