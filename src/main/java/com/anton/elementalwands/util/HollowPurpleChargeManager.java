package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
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
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CHARGES.clear());
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
        Vec3d openingCenter = caster.getEntityPos().add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_GRAVITY_LENS,
                openingCenter.x, openingCenter.y, openingCenter.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_MOTE,
                openingCenter.x, openingCenter.y, openingCenter.z, 24, 0.9, 1.2, 0.9, 0.035);
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
                if (caster != null) {
                    Vec3d center = caster.getEntityPos().add(0.0, 1.0, 0.0);
                    world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                            center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
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

            if (age > 0 && age < ASCENT_TICKS && age % 20 == 0) {
                world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.32f, 0.72f + age * 0.006f);
            }
            if (age == ASCENT_TICKS) {
                world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS,
                        0.48f, 1.75f);
            }
        }

        if (byCaster.isEmpty()) {
            CHARGES.remove(world.getRegistryKey());
        }
    }

    private static boolean isHoldingSpaceWand(PlayerEntity caster) {
        return caster.getMainHandStack().getItem() instanceof AbstractWandItem
                && EWAttachments.getAffinity(caster) == WizardAffinity.SPACE;
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

        Vec3d lookDir = caster.getRotationVec(1.0f);
        Vec3d horizontalLook = new Vec3d(lookDir.x, 0.0, lookDir.z);
        if (horizontalLook.lengthSquared() < 0.0001) {
            horizontalLook = new Vec3d(0.0, 0.0, 1.0);
        }
        horizontalLook = horizontalLook.normalize();
        Vec3d rightDir = horizontalLook.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();

        float holdProgress = holdPhase
                ? MathHelper.clamp((age - ASCENT_TICKS) / (float) HOLD_TICKS, 0.0f, 1.0f)
                : 0.0f;
        double spread = holdPhase
                ? MathHelper.lerp(holdProgress, 2.15, 0.0)
                : MathHelper.lerp(progress, 3.35, 2.15);
        double orbitAngle = age * 0.12;
        Vec3d orbitOffset = rightDir.multiply(Math.cos(orbitAngle) * spread)
                .add(0.0, Math.sin(orbitAngle) * spread * 0.34, 0.0);

        Vec3d cyanPos = anchor.add(orbitOffset);
        Vec3d magentaPos = anchor.subtract(orbitOffset);
        world.spawnParticles(ModParticles.SPACE_DYING_STAR_CYAN,
                cyanPos.x, cyanPos.y, cyanPos.z, 1, 0.025, 0.025, 0.025, 0.0);
        world.spawnParticles(ModParticles.SPACE_DYING_STAR_MAGENTA,
                magentaPos.x, magentaPos.y, magentaPos.z, 1, 0.025, 0.025, 0.025, 0.0);

        if (age % 2 == 0) {
            spawnStarWake(world, cyanPos, anchor, 0.28 + progress * 0.26);
            spawnStarWake(world, magentaPos, anchor, 0.28 + progress * 0.26);
        }
        if (age % 3 == 0) {
            world.spawnParticles(ModParticles.SPACE_BROKEN_ORBIT,
                    anchor.x, anchor.y, anchor.z, 1, 0.04, 0.04, 0.04, 0.0);
        }

        int driftingMotes = 2 + (int) (progress * 3.0f);
        for (int i = 0; i < driftingMotes; i++) {
            double angle = orbitAngle * 0.6 + i * (Math.PI * 2.0 / driftingMotes);
            double radius = 1.2 + (i % 3) * 0.52 + (1.0 - progress) * 0.8;
            Vec3d point = anchor.add(Math.cos(angle) * radius,
                    Math.sin(angle * 1.6) * 0.9,
                    Math.sin(angle) * radius);
            Vec3d inward = anchor.subtract(point).normalize().multiply(0.045 + progress * 0.045);
            spawnDirected(world, ModParticles.SPACE_MOTE, point, inward);
        }

        if (holdPhase && holdProgress >= 0.70f) {
            world.spawnParticles(ModParticles.SPACE_PINPOINT,
                    anchor.x, anchor.y, anchor.z, 1, 0.01, 0.01, 0.01, 0.0);
            if (age == TOTAL_TICKS - 1) {
                world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                        anchor.x, anchor.y, anchor.z, 2, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void spawnStarWake(ServerWorld world, Vec3d starPos, Vec3d anchor, double radius) {
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI * 2.0 / 4.0) + starPos.y;
            Vec3d point = starPos.add(Math.cos(angle) * radius,
                    ((i & 1) == 0 ? radius : -radius) * 0.5,
                    Math.sin(angle) * radius);
            Vec3d inward = anchor.subtract(point).normalize().multiply(0.10);
            spawnDirected(world, ModParticles.SPACE_CONSUMPTION, point, inward);
        }
    }

    private static void spawnDirected(ServerWorld world, net.minecraft.particle.SimpleParticleType particle,
            Vec3d position, Vec3d velocity) {
        world.spawnParticles(particle,
                position.x, position.y, position.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0);
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

        world.spawnParticles(ModParticles.SPACE_PINPOINT,
                anchor.x, anchor.y, anchor.z, 4, 0.08, 0.08, 0.08, 0.0);
        world.spawnParticles(ModParticles.SPACE_GRAVITY_LENS,
                anchor.x, anchor.y, anchor.z, 2, 0.04, 0.04, 0.04, 0.0);
        world.spawnParticles(ModParticles.SPACE_ECLIPSE,
                anchor.x, anchor.y, anchor.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_CONSUMPTION,
                anchor.x, anchor.y, anchor.z, 36, 1.4, 1.4, 1.4, 0.14);

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5f,
                0.75f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
                SoundCategory.PLAYERS, 1.3f, 0.7f);
    }
}
