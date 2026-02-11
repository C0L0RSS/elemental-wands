package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
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

public final class BlizzardManager {

    private static final class Blizzard {
        private final Vec3d center;
        private final int radius;
        private final UUID casterUuid;
        private final int startTick;
        private final int expiryTick;

        private Blizzard(Vec3d center, int radius, UUID casterUuid, int startTick, int expiryTick) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.startTick = startTick;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Blizzard>> BLIZZARDS = new HashMap<>();

    // 3-Phase Rime Awakening
    private static final int PHASE_1_DURATION_TICKS = 60; // 3 seconds
    private static final int PHASE_2_DURATION_TICKS = 60; // 3 seconds
    private static final int PHASE_3_CAGE_DURATION_TICKS = 80; // 4 seconds - APPROVED
    private static final int TOTAL_DURATION_TICKS = PHASE_1_DURATION_TICKS + PHASE_2_DURATION_TICKS;

    // Phase 1 - Build-up
    private static final int PHASE_1_PARTICLE_RATE = 20;

    // Phase 2 - Storm
    private static final int PHASE_2_PARTICLE_RATE = 100;
    private static final float PHASE_2_DAMAGE_PER_SECOND = 2.0f;
    private static final int PHASE_2_FROST_STACK_INTERVAL_TICKS = 20;

    // Phase 3 - Finale (ice cage)
    private static final int ICE_CAGE_SIZE = 3; // 3x3x3 blocks

    private static final int RADIUS = 25; // Large blizzard radius

    private BlizzardManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(BlizzardManager::tickWorld);
    }

    public static void startBlizzard(ServerWorld world, PlayerEntity caster, Vec3d center) {
        int now = world.getServer().getTicks();

        BLIZZARDS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Blizzard(center, RADIUS, caster.getUuid(), now, now + TOTAL_DURATION_TICKS));

        // Initial snow field
        TemporarySnowManager.createSnowField(world, BlockPos.ofFloored(center), RADIUS,
                TOTAL_DURATION_TICKS + PHASE_3_CAGE_DURATION_TICKS + 60);
    }

    private static void tickWorld(ServerWorld world) {
        List<Blizzard> blizzards = BLIZZARDS.get(world.getRegistryKey());
        if (blizzards == null || blizzards.isEmpty())
            return;

        int now = world.getServer().getTicks();

        Iterator<Blizzard> it = blizzards.iterator();
        while (it.hasNext()) {
            Blizzard blizzard = it.next();
            int age = now - blizzard.startTick;

            if (now >= blizzard.expiryTick) {
                // Trigger Phase 3 Finale
                triggerPhase3Finale(world, blizzard);
                it.remove();
                continue;
            }

            // Determine current phase
            if (age <= PHASE_1_DURATION_TICKS) {
                tickPhase1BuildUp(world, blizzard, age);
            } else {
                tickPhase2Storm(world, blizzard, age);
            }
        }

        if (blizzards.isEmpty()) {
            BLIZZARDS.remove(world.getRegistryKey());
        }
    }

    private static void tickPhase1BuildUp(ServerWorld world, Blizzard blizzard, int age) {
        // Phase 1: Build-up with light snow and wind sounds
        Box box = Box.of(blizzard.center, blizzard.radius * 2, 24.0, blizzard.radius * 2);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.getUuid() != blizzard.casterUuid);

        for (LivingEntity target : targets) {
            // Apply Slowness I
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 0, false, false, false));
        }

        // Light snow particles
        if (age % 2 == 0) { // Every other tick
            for (int i = 0; i < PHASE_1_PARTICLE_RATE / 10; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double radius = world.random.nextDouble() * blizzard.radius;
                double px = blizzard.center.x + Math.cos(angle) * radius;
                double pz = blizzard.center.z + Math.sin(angle) * radius;
                double py = blizzard.center.y + 5 + world.random.nextDouble() * 8;

                world.spawnParticles(ParticleTypes.SNOWFLAKE, px, py, pz, 1, 0.2, 0.2, 0.2, 0.01);
            }
        }

        // Wind sounds periodically
        if (age % 40 == 0) {
            world.playSound(null, BlockPos.ofFloored(blizzard.center), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(),
                    SoundCategory.PLAYERS, 0.8f, 0.7f);
        }
    }

    private static void tickPhase2Storm(ServerWorld world, Blizzard blizzard, int age) {
        // Phase 2: Intense storm with thick fog and rapid freezing
        Box box = Box.of(blizzard.center, blizzard.radius * 2, 24.0, blizzard.radius * 2);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.getUuid() != blizzard.casterUuid);

        for (LivingEntity target : targets) {
            // Apply Blindness (whiteout effect)
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false));

            // Deal damage every 10 ticks (0.5 seconds) for 2 damage/second
            if (age % 10 == 0) {
                target.damage(world, world.getDamageSources().freeze(), PHASE_2_DAMAGE_PER_SECOND / 2);
            }

            // Add Frost stack every 20 ticks
            if (age % PHASE_2_FROST_STACK_INTERVAL_TICKS == 0) {
                ChillTracker.addStack(world, target);
            }

            // Aggressive freezing
            target.setFrozenTicks(Math.min(target.getFrozenTicks() + 5, target.getMinFreezeDamageTicks() + 100));
        }

        // Thick snow particles
        if (age % 1 == 0) { // Every tick
            for (int i = 0; i < PHASE_2_PARTICLE_RATE / 20; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double radius = world.random.nextDouble() * blizzard.radius;
                double px = blizzard.center.x + Math.cos(angle) * radius;
                double pz = blizzard.center.z + Math.sin(angle) * radius;
                double py = blizzard.center.y + 5 + world.random.nextDouble() * 10;

                world.spawnParticles(ParticleTypes.SNOWFLAKE, px, py, pz, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }

        // Louder blizzard sounds
        if (age % 30 == 0) {
            world.playSound(null, BlockPos.ofFloored(blizzard.center), SoundEvents.ENTITY_PLAYER_HURT_FREEZE,
                    SoundCategory.PLAYERS, 1.5f, 0.5f);
        }
    }

    private static void triggerPhase3Finale(ServerWorld world, Blizzard blizzard) {
        // Phase 3: Ice cage finale - encase all entities still in radius
        Box box = Box.of(blizzard.center, blizzard.radius * 2, 24.0, blizzard.radius * 2);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.getUuid() != blizzard.casterUuid);

        for (LivingEntity target : targets) {
            createIceCage(world, target.getBlockPos());

            // Apply immobilization during cage
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, PHASE_3_CAGE_DURATION_TICKS, 255,
                    false, true, true));
        }

        // Finale particles and sound
        world.spawnParticles(ParticleTypes.SNOWFLAKE, blizzard.center.x, blizzard.center.y + 1.0, blizzard.center.z,
                300, blizzard.radius * 0.9, 4.0, blizzard.radius * 0.9, 0.05);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, blizzard.center.x, blizzard.center.y + 1.0,
                blizzard.center.z, 5, 3.0, 2.0, 3.0, 0.0);
        world.playSound(null, BlockPos.ofFloored(blizzard.center), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 2.0f, 0.3f);
    }

    private static void createIceCage(ServerWorld world, BlockPos center) {
        // Create 3x3x3 ice cage around entity
        List<BlockPos> cagePositions = new ArrayList<>();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    // Don't place block at exact center (entity position)
                    if (x == 0 && y == 0 && z == 0)
                        continue;

                    cagePositions.add(center.add(x, y, z));
                }
            }
        }

        // Place temporary ice cage
        TemporaryBlockManager.placeTemporaryBlocks(
                world,
                cagePositions,
                Blocks.PACKED_ICE.getDefaultState(),
                PHASE_3_CAGE_DURATION_TICKS,
                state -> state.isAir() || state.isReplaceable());

        // Spawn particles
        world.spawnParticles(ParticleTypes.SNOWFLAKE, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                50, 1.5, 1.5, 1.5, 0.1);
    }
}
