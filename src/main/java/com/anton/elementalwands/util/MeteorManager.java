package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class MeteorManager {

    private static final class Meteor {
        private final int entityId;
        private final UUID casterUuid;
        private Vec3d lastPos;
        private final float explosionPower;
        private final int expiryTick;

        private Meteor(int entityId, UUID casterUuid, Vec3d lastPos, float explosionPower, int expiryTick) {
            this.entityId = entityId;
            this.casterUuid = casterUuid;
            this.lastPos = lastPos;
            this.explosionPower = explosionPower;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Meteor>> METEORS = new HashMap<>();

    private MeteorManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(MeteorManager::tickWorld);
    }

    public static void spawnMeteor(ServerWorld world, PlayerEntity caster, Vec3d targetPos, int spawnHeight,
            float explosionPower) {
        BlockState meteorState = world.random.nextBoolean() ? Blocks.OBSIDIAN.getDefaultState()
                : Blocks.MAGMA_BLOCK.getDefaultState();

        BlockPos spawnBlockPos = BlockPos.ofFloored(targetPos).add(0, spawnHeight, 0);
        FallingBlockEntity meteor = FallingBlockEntity.spawnFromBlock(world, spawnBlockPos, meteorState);
        meteor.setPosition(targetPos.x, spawnBlockPos.getY(), targetPos.z);
        meteor.setVelocity(0.0, -0.85, 0.0);
        meteor.setHurtEntities(10.0f, 40);
        meteor.setDestroyedOnLanding();

        int now = world.getServer().getTicks();
        METEORS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Meteor(meteor.getId(), caster.getUuid(), meteor.getEntityPos(), explosionPower, now + 240));

        world.playSound(null, spawnBlockPos, SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.4f,
                0.6f);
        world.spawnParticles(ParticleTypes.FLAME, meteor.getX(), meteor.getY(), meteor.getZ(), 80, 0.4, 0.6, 0.4, 0.06);
        world.spawnParticles(ParticleTypes.SMOKE, meteor.getX(), meteor.getY(), meteor.getZ(), 50, 0.5, 0.7, 0.5, 0.03);
    }

    private static void tickWorld(ServerWorld world) {
        List<Meteor> meteors = METEORS.get(world.getRegistryKey());
        if (meteors == null || meteors.isEmpty()) return;

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

            world.spawnParticles(ParticleTypes.FLAME, meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z, 8, 0.25, 0.25,
                    0.25, 0.02);
            world.spawnParticles(ParticleTypes.SMOKE, meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z, 3, 0.2, 0.2, 0.2,
                    0.01);

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

        world.createExplosion(caster, meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z, meteor.explosionPower, true,
                World.ExplosionSourceType.MOB);
        world.playSound(null, BlockPos.ofFloored(meteor.lastPos), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS,
                1.8f, 0.9f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, meteor.lastPos.x, meteor.lastPos.y, meteor.lastPos.z, 1, 0, 0, 0,
                0);
    }
}
