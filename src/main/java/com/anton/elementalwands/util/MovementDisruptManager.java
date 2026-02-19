package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class MovementDisruptManager {

    private static final Map<RegistryKey<World>, Map<UUID, Integer>> SPRINT_LOCKS = new HashMap<>();

    private static final double LOCK_DRAG_MULTIPLIER = 0.82;

    private MovementDisruptManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(MovementDisruptManager::tickWorld);
    }

    public static void applySprintLock(ServerWorld world, LivingEntity target, int durationTicks) {
        if (!target.isAlive() || target.isSpectator()) {
            return;
        }

        int now = world.getServer().getTicks();
        int newExpiry = now + durationTicks;

        Map<UUID, Integer> byEntity = SPRINT_LOCKS.computeIfAbsent(world.getRegistryKey(), _k -> new HashMap<>());
        byEntity.merge(target.getUuid(), newExpiry, Math::max);

        forceSprintOffAndDrag(target);
    }

    public static void disruptMobility(LivingEntity target) {
        stripDashGlide(target);
        suppressFireSurf(target);

        Vec3d velocity = target.getVelocity();
        target.setVelocity(velocity.x * 0.55, Math.min(velocity.y, 0.18), velocity.z * 0.55);
        target.velocityModified = true;
    }

    public static void stripDashGlide(LivingEntity target) {
        target.removeStatusEffect(StatusEffects.SLOW_FALLING);
    }

    public static void suppressFireSurf(LivingEntity target) {
        StatusEffectInstance speed = target.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && speed.getAmplifier() >= 1) {
            target.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    private static void tickWorld(ServerWorld world) {
        Map<UUID, Integer> byEntity = SPRINT_LOCKS.get(world.getRegistryKey());
        if (byEntity == null || byEntity.isEmpty()) {
            return;
        }

        int now = world.getServer().getTicks();

        Iterator<Map.Entry<UUID, Integer>> it = byEntity.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();

            if (now >= entry.getValue()) {
                it.remove();
                continue;
            }

            Entity entity = world.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                it.remove();
                continue;
            }

            forceSprintOffAndDrag(living);
            stripDashGlide(living);
            suppressFireSurf(living);
        }

        if (byEntity.isEmpty()) {
            SPRINT_LOCKS.remove(world.getRegistryKey());
        }
    }

    private static void forceSprintOffAndDrag(LivingEntity living) {
        if (living instanceof PlayerEntity player) {
            player.setSprinting(false);
        }

        Vec3d velocity = living.getVelocity();
        living.setVelocity(velocity.x * LOCK_DRAG_MULTIPLIER, velocity.y, velocity.z * LOCK_DRAG_MULTIPLIER);
        living.velocityModified = true;
    }
}
