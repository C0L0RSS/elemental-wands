package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.item.SpaceWandItem;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class HollowPurpleChargeManager {

    private record ChargeState(int startTick, double startY, double targetY) {
    }

    private static final Map<RegistryKey<World>, Map<UUID, ChargeState>> CHARGES = new HashMap<>();

    private static final int ASCENT_TICKS = 60;
    private static final int HOLD_TICKS = 10;
    private static final int TOTAL_TICKS = ASCENT_TICKS + HOLD_TICKS;
    private static final double ASCENT_HEIGHT = 10.0;
    private static final double ORB_ANCHOR_OFFSET_Y = 3.0;

    private HollowPurpleChargeManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(HollowPurpleChargeManager::tickWorld);
    }

    public static boolean startCharge(ServerWorld world, PlayerEntity caster) {
        if (!caster.isAlive() || caster.isSpectator()) {
            return false;
        }

        Map<UUID, ChargeState> byCaster = CHARGES.computeIfAbsent(world.getRegistryKey(), _k -> new HashMap<>());
        if (byCaster.containsKey(caster.getUuid())) {
            return false;
        }

        double bottomLimit = world.getBottomY() + 1.0;
        double topLimit = world.getTopYInclusive() - 3.0;
        double startY = MathHelper.clamp(caster.getY(), bottomLimit, topLimit);
        double targetY = MathHelper.clamp(startY + ASCENT_HEIGHT, bottomLimit, topLimit);

        int now = world.getServer().getTicks();
        byCaster.put(caster.getUuid(), new ChargeState(now, startY, targetY));

        caster.setSprinting(false);
        caster.fallDistance = 0.0f;
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 8, 0, false, false, false));

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS,
                1.0f, 0.75f);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, caster.getX(), caster.getY() + 1.0, caster.getZ(), 18, 0.5,
                1.0, 0.6, 0.06);
        return true;
    }

    public static boolean isCharging(ServerWorld world, PlayerEntity caster) {
        Map<UUID, ChargeState> byCaster = CHARGES.get(world.getRegistryKey());
        return byCaster != null && byCaster.containsKey(caster.getUuid());
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, ChargeState> byCaster = CHARGES.get(world.getRegistryKey());
        if (byCaster == null || byCaster.isEmpty()) {
            return;
        }

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, ChargeState>> it = byCaster.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChargeState> entry = it.next();
            UUID casterUuid = entry.getKey();
            ChargeState state = entry.getValue();

            PlayerEntity caster = world.getPlayerByUuid(casterUuid);
            if (caster == null || !caster.isAlive() || caster.isSpectator() || !isHoldingSpaceWand(caster)) {
                it.remove();
                continue;
            }

            int age = now - state.startTick;
            if (age >= TOTAL_TICKS) {
                launchOrb(world, caster);
                it.remove();
                continue;
            }

            applyChargeMovementLock(world, caster, state, age);
            spawnChargeVisuals(world, caster, age);

            if (age > 0 && age % 20 == 0) {
                world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.28f, 1.15f);
            }
            if (age == ASCENT_TICKS) {
                world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS,
                        0.75f, 1.35f);
            }
        }

        if (byCaster.isEmpty()) {
            CHARGES.remove(world.getRegistryKey());
        }
    }

    private static boolean isHoldingSpaceWand(PlayerEntity caster) {
        return caster.getMainHandStack().getItem() instanceof SpaceWandItem;
    }

    private static void applyChargeMovementLock(ServerWorld world, PlayerEntity caster, ChargeState state, int age) {
        caster.setSprinting(false);
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 6, 0, false, false, false));

        double progress = age < ASCENT_TICKS
                ? MathHelper.clamp((age + 1) / (double) ASCENT_TICKS, 0.0, 1.0)
                : 1.0;
        double desiredY = MathHelper.lerp(progress, state.startY, state.targetY);
        double yError = desiredY - caster.getY();

        Vec3d currentVelocity = caster.getVelocity();
        double correction = age < ASCENT_TICKS
                ? MathHelper.clamp(yError * 0.25, -0.06, 0.16)
                : MathHelper.clamp(yError * 0.35, -0.08, 0.08);
        double yVelocity = MathHelper.clamp(currentVelocity.y + correction, -0.12, 0.24);

        caster.setVelocity(0.0, yVelocity, 0.0);
        caster.velocityModified = true;
        caster.fallDistance = 0.0f;
    }

    private static Vec3d orbAnchor(PlayerEntity caster) {
        return new Vec3d(caster.getX(), caster.getY() + ORB_ANCHOR_OFFSET_Y, caster.getZ());
    }

    private static void spawnChargeVisuals(ServerWorld world, PlayerEntity caster, int age) {
        Vec3d anchor = orbAnchor(caster);
        float progress = MathHelper.clamp(age / (float) ASCENT_TICKS, 0.0f, 1.0f);
        boolean holdPhase = age >= ASCENT_TICKS;

        spawnSnakeLines(world, caster, anchor, progress, age, holdPhase);

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, anchor.x, anchor.y, anchor.z, holdPhase ? 3 : 1, 0.08, 0.08,
                0.08, holdPhase ? 0.01 : 0.0);
        if (holdPhase) {
            world.spawnParticles(ParticleTypes.PORTAL, anchor.x, anchor.y, anchor.z, 10, 0.16, 0.16, 0.16, 0.008);
            world.spawnParticles(ParticleTypes.WITCH, anchor.x, anchor.y, anchor.z, 4, 0.12, 0.12, 0.12, 0.0);
        }
    }

    private static void spawnSnakeLines(ServerWorld world, PlayerEntity caster, Vec3d anchor, float progress, int age,
            boolean holdPhase) {
        int lines = 4; // 2 red, 2 blue
        Vec3d feet = new Vec3d(caster.getX(), caster.getY() + 0.10, caster.getZ());
        double baseRadius = 2.0;

        // Colors
        int redColor = 0xFF1919; // Vibrant Red
        int blueColor = 0x194CFF; // Vibrant Blue
        float size = 1.8f; // Slightly larger, distinct particles

        // Calculate how much of the path to cover this tick based on progress (0.0 to
        // 1.0)
        double currentT = holdPhase ? 1.0 : progress;
        double previousT = holdPhase ? 1.0 : Math.max(0.0, (age - 1) / (float) ASCENT_TICKS);

        // We interpolate a few points between last tick and this tick to avoid gaps in
        // the trail
        int subSteps = holdPhase ? 1 : 3;

        for (int line = 0; line < lines; line++) {
            boolean isRedLine = line % 2 == 0;
            DustParticleEffect dust = new DustParticleEffect(isRedLine ? redColor : blueColor, size);

            for (int step = 0; step < subSteps; step++) {
                double interp = subSteps == 1 ? 1.0 : (step + 1) / (double) subSteps;
                double t = previousT + (currentT - previousT) * interp;

                // Calculate age at this exact interpolated sub-tick for smooth rotation
                double exactAge = (age - 1) + interp;

                double startAngle = (line * (Math.PI * 2.0 / lines)) + (exactAge * 0.15);
                Vec3d start = new Vec3d(
                        feet.x + Math.cos(startAngle) * baseRadius,
                        feet.y,
                        feet.z + Math.sin(startAngle) * baseRadius);

                Vec3d pathPoint = start.lerp(anchor, t);

                // Spiral around the lerped center
                double snakeAngle = startAngle + (t * 12.0);
                double radius = baseRadius * (1.0 - t); // Shrinks to 0 at the anchor

                double px = pathPoint.x + (Math.cos(snakeAngle) * radius);
                // Add subtle wobble
                double py = pathPoint.y + (Math.sin(exactAge * 0.2 + line) * 0.1);
                double pz = pathPoint.z + (Math.sin(snakeAngle) * radius);

                world.spawnParticles(dust, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void launchOrb(ServerWorld world, PlayerEntity caster) {
        Vec3d anchor = orbAnchor(caster);
        Vec3d direction = caster.getRotationVec(1.0f);
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vec3d(0.0, 0.0, 1.0);
        }

        HollowPurpleOrbEntity orb = new HollowPurpleOrbEntity(world, caster, anchor, direction.normalize());
        world.spawnEntity(orb);

        caster.fallDistance = 0.0f;
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false, true));

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, anchor.x, anchor.y, anchor.z, 90, 1.3, 1.3, 1.3, 0.12);
        world.spawnParticles(ParticleTypes.WITCH, anchor.x, anchor.y, anchor.z, 70, 1.2, 1.2, 1.2, 0.04);

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f,
                0.75f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
                SoundCategory.PLAYERS, 1.3f, 0.7f);
    }
}
