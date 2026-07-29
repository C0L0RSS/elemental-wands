package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.Explosion;

import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;

public final class MeteorManager {

    private static final class Meteor {
        private final int entityId;
        private final UUID casterUuid;
        private final Vec3d landingBrandPos;
        private final double spawnY;
        private Vec3d lastPos;
        private final float explosionPower;
        private final int expiryTick;

        private Meteor(int entityId, UUID casterUuid, Vec3d landingBrandPos, double spawnY, Vec3d lastPos,
                float explosionPower, int expiryTick) {
            this.entityId = entityId;
            this.casterUuid = casterUuid;
            this.landingBrandPos = landingBrandPos;
            this.spawnY = spawnY;
            this.lastPos = lastPos;
            this.explosionPower = explosionPower;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Meteor>> METEORS = new HashMap<>();
    private static final int WARNING_RADIUS = 60; // blocks

    private MeteorManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(MeteorManager::tickWorld);
        // World keys repeat across saves and the tick counter restarts at zero, so a
        // meteor left in flight would otherwise detonate in the next world loaded.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> METEORS.clear());
    }

    public static void spawnMeteor(ServerWorld world, PlayerEntity caster, Vec3d targetPos, int spawnHeight,
            float explosionPower) {
        BlockState meteorState = ModSpellBlocks.METEOR_CORE.getDefaultState();

        BlockPos spawnBlockPos = BlockPos.ofFloored(targetPos).add(0, spawnHeight, 0);
        FallingBlockEntity meteor = FallingBlockEntity.spawnFromBlock(world, spawnBlockPos, meteorState);
        meteor.setPosition(targetPos.x, spawnBlockPos.getY(), targetPos.z);
        meteor.setVelocity(0.0, -0.2, 0.0); // BUFFED: Much slower fall (was -0.3)
        meteor.setHurtEntities(10.0f, 40);
        meteor.setDestroyedOnLanding();
        Vec3d landingBrandPos = findLandingBrandPos(world, meteor, targetPos, spawnBlockPos);

        int now = world.getServer().getTicks();
        METEORS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Meteor(meteor.getId(), caster.getUuid(), landingBrandPos, meteor.getY(),
                        meteor.getEntityPos(),
                        explosionPower, now + 240));

        // BUFFED: Play global Wither spawn sound for dramatic effect
        world.playSound(null, spawnBlockPos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 2.0f, 1.0f);
        world.playSound(null, spawnBlockPos, SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.4f, 0.6f);

        // BUFFED: Display HUD title to all players within 60-block radius
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(targetPos) <= WARNING_RADIUS * WARNING_RADIUS) {
                player.sendMessage(
                        net.minecraft.text.Text.literal("⚠ MAXIMUM METEOR DETECTED ⚠")
                                .formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD),
                        true); // true = actionbar
            }
        }

        world.spawnParticles(ModParticles.FIRE_INFERNO_FLAME,
                meteor.getX(), meteor.getY(), meteor.getZ(), 16, 0.8, 0.8, 0.8, 0.05);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                meteor.getX(), meteor.getY(), meteor.getZ(), 4, 0.65, 0.65, 0.65, 0.025);
        spawnLandingRing(world, landingBrandPos, 0.0);
    }

    private static void tickWorld(ServerWorld world) {
        List<Meteor> meteors = METEORS.get(world.getRegistryKey());
        if (meteors == null || meteors.isEmpty())
            return;

        int now = world.getServer().getTicks();

        Iterator<Meteor> it = meteors.iterator();
        while (it.hasNext()) {
            Meteor meteor = it.next();

            if (now >= meteor.expiryTick) {
                it.remove();
                continue;
            }

            Entity entity = world.getEntityById(meteor.entityId);
            if (!(entity instanceof FallingBlockEntity falling) || entity.isRemoved()) {
                explode(world, meteor);
                it.remove();
                continue;
            }

            meteor.lastPos = falling.getEntityPos();

            // The visual-only ring closes around the stored projected landing
            // surface as the real falling block approaches it.
            if (now % 4 == 0) {
                double descentDistance = Math.max(1.0, meteor.spawnY - meteor.landingBrandPos.y);
                double descentProgress = Math.max(0.0, Math.min(1.0,
                        (meteor.spawnY - meteor.lastPos.y) / descentDistance));
                spawnLandingRing(world, meteor.landingBrandPos, descentProgress);
            }

            world.spawnParticles(ModParticles.FIRE_INFERNO_FLAME,
                    meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                    8, 0.55, 0.55, 0.55, 0.035);
            if (now % 2 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                        1, 0.3, 0.3, 0.3, 0.018);
            }
            if (now % 3 == 0) {
                world.spawnParticles(ParticleTypes.LAVA,
                        meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                        2, 0.4, 0.4, 0.4, 0.04);
            }

            if (falling.isOnGround()) {
                explode(world, meteor);
                falling.discard();
                it.remove();
            }
        }

        if (meteors.isEmpty()) {
            METEORS.remove(world.getRegistryKey());
        }
    }

    private static void explode(ServerWorld world, Meteor meteor) {
        PlayerEntity caster = world.getPlayerByUuid(meteor.casterUuid);

        world.createExplosion(
                caster,
                Explosion.createDamageSource(world, caster),
                null,
                meteor.lastPos.x,
                meteor.lastPos.y,
                meteor.lastPos.z,
                meteor.explosionPower,
                true,
                World.ExplosionSourceType.MOB,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER,
                Pool.<BlockParticleEffect>empty(),
                SoundEvents.ENTITY_GENERIC_EXPLODE);
        world.playSound(null, BlockPos.ofFloored(meteor.lastPos), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS,
                1.8f, 0.9f);
        world.spawnParticles(ModParticles.FIRE_INFERNO_FLAME,
                meteor.lastPos.x, meteor.lastPos.y + 0.35, meteor.lastPos.z,
                64, 2.0, 1.4, 2.0, 0.18);
        world.spawnParticles(ParticleTypes.LAVA,
                meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                24, 1.8, 1.25, 1.8, 0.14);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                12, 1.7, 1.0, 1.7, 0.06);
    }

    private static void spawnLandingRing(ServerWorld world, Vec3d targetPos, double descentProgress) {
        double radius = 7.0 + (2.25 - 7.0) * descentProgress;
        for (int index = 0; index < 24; index++) {
            double angle = Math.PI * 2.0 * index / 24.0;
            double x = targetPos.x + Math.cos(angle) * radius;
            double z = targetPos.z + Math.sin(angle) * radius;
            world.spawnParticles(ModParticles.FIRE_INFERNO_FLAME,
                    x, targetPos.y + 0.08, z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static Vec3d findLandingBrandPos(ServerWorld world, Entity meteor,
            Vec3d targetPos, BlockPos spawnBlockPos) {
        Vec3d start = new Vec3d(targetPos.x, spawnBlockPos.getY() + 0.5, targetPos.z);
        Vec3d end = new Vec3d(targetPos.x, world.getBottomY(), targetPos.z);
        BlockHitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                meteor));
        return hit.getType() == HitResult.Type.MISS
                ? targetPos
                : hit.getPos().add(0.0, 0.02, 0.0);
    }
}
