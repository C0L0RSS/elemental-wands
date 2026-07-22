package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockParticleEffect;
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
        private Vec3d lastPos;
        private final float explosionPower;
        private final int expiryTick;

        private Meteor(int entityId, UUID casterUuid, Vec3d landingBrandPos, Vec3d lastPos, float explosionPower,
                int expiryTick) {
            this.entityId = entityId;
            this.casterUuid = casterUuid;
            this.landingBrandPos = landingBrandPos;
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
                .add(new Meteor(meteor.getId(), caster.getUuid(), landingBrandPos, meteor.getEntityPos(),
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

        world.spawnParticles(ModParticles.FIRE_METEOR,
                meteor.getX(), meteor.getY(), meteor.getZ(), 12, 0.8, 0.8, 0.8, 0.05);
        world.spawnParticles(ModParticles.FIRE_ASH,
                meteor.getX(), meteor.getY(), meteor.getZ(), 18, 0.9, 0.9, 0.9, 0.035);
        spawnLandingBrand(world, landingBrandPos);
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

            // Re-pulse the visual-only landing brand while the core descends. Its
            // projected surface point never affects landing, damage, power, or terrain.
            if (now % 8 == 0) {
                spawnLandingBrand(world, meteor.landingBrandPos);
            }

            world.spawnParticles(ModParticles.FIRE_METEOR,
                    meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                    6, 0.45, 0.45, 0.45, 0.025);
            world.spawnParticles(ModParticles.FIRE_ASH,
                    meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                    4, 0.4, 0.4, 0.4, 0.018);

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

        // Use the full explosion API so the vanilla explosion/emitter textures and
        // destroyed-block debris are not authored into this spell. ExplosionImpl
        // still performs the same damage, knockback, fire, terrain, and gamerule
        // handling as the old convenience overload.
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
                ModParticles.FIRE_IMPACT_RING,
                ModParticles.FIRE_IMPACT_RING,
                Pool.<BlockParticleEffect>empty(),
                SoundEvents.ENTITY_GENERIC_EXPLODE);
        world.playSound(null, BlockPos.ofFloored(meteor.lastPos), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS,
                1.8f, 0.9f);
        world.spawnParticles(ModParticles.FIRE_IMPACT_RING,
                meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                4, 0.8, 0.2, 0.8, 0.0);
        world.spawnParticles(ModParticles.FIRE_METEOR_IMPACT,
                meteor.lastPos.x, meteor.lastPos.y + 0.08, meteor.lastPos.z,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.FIRE_METEOR,
                meteor.lastPos.x, meteor.lastPos.y + 0.35, meteor.lastPos.z,
                28, 1.35, 0.85, 1.35, 0.18);
        world.spawnParticles(ModParticles.FIRE_EMBER,
                meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                48, 2.0, 1.4, 2.0, 0.14);
        world.spawnParticles(ModParticles.FIRE_ASH,
                meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z,
                20, 1.7, 1.0, 1.7, 0.06);
    }

    private static void spawnLandingBrand(ServerWorld world, Vec3d targetPos) {
        world.spawnParticles(ModParticles.FIRE_METEOR_WARNING,
                targetPos.x, targetPos.y + 0.08, targetPos.z,
                1, 0.0, 0.0, 0.0, 0.0);
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
