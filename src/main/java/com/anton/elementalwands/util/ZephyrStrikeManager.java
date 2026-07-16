package com.anton.elementalwands.util;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative lifecycle for Zephyr Strike.
 *
 * <p>This manager deliberately does not depend on an inventory tick from the
 * wand. It owns fall protection, impact detection, and exact chest-slot
 * restoration even when the active flow is interrupted.</p>
 */
public final class ZephyrStrikeManager {

    private static final int IMPACT_GRACE_TICKS = 10;
    private static final Map<UUID, ActiveStrike> ACTIVE = new HashMap<>();
    private static boolean initialized;

    private ZephyrStrikeManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerTickEvents.END_SERVER_TICK.register(ZephyrStrikeManager::tick);

        // Catch both freshly dropped stacks and orphaned item entities loaded
        // from disk. Temporary spell equipment is never allowed to exist loose.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity itemEntity
                    && itemEntity.getStack().isOf(ModItems.ZEPHYR_WINGS)) {
                itemEntity.discard();
            }
        });

        ServerPlayerEvents.LEAVE.register(ZephyrStrikeManager::finish);
        ServerPlayerEvents.AFTER_RESPAWN.register(ZephyrStrikeManager::afterRespawn);
        ServerPlayerEvents.JOIN.register(ZephyrStrikeManager::purgeOrphanedWings);

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                finish(player));

        ServerLifecycleEvents.SERVER_STOPPING.register(ZephyrStrikeManager::finishAll);
    }

    public static boolean isActive(net.minecraft.entity.player.PlayerEntity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    /** Begins the strike after its ultimate charge has been spent. */
    public static void begin(ServerPlayerEntity player, ItemStack sourceWand, int startTick) {
        if (isActive(player)) {
            return;
        }

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack savedChest = chest.isOf(ModItems.ZEPHYR_WINGS) ? ItemStack.EMPTY : chest.copy();

        // Put the session in the map before equipping so the transient item's
        // inventory guard can never mistake a legitimate wing stack for debris.
        ACTIVE.put(player.getUuid(), new ActiveStrike(
                sourceWand,
                savedChest,
                player.getEntityWorld(),
                startTick));
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(ModItems.ZEPHYR_WINGS));
    }

    /** Explicit cancellation hook for any future interrupted-cast path. */
    public static void cancel(ServerPlayerEntity player) {
        finish(player);
    }

    /** Called at the head of PlayerEntity.dropInventory, after death is final. */
    public static void onPlayerDeath(ServerPlayerEntity player) {
        finish(player);
    }

    private static void tick(MinecraftServer server) {
        for (UUID playerId : List.copyOf(ACTIVE.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            ActiveStrike strike = ACTIVE.get(playerId);
            if (strike == null) {
                continue;
            }
            if (player == null) {
                // LEAVE normally restores first. Avoid retaining a stale
                // session if another mod removes a player without that event.
                ACTIVE.remove(playerId);
                continue;
            }

            if (!player.isAlive()
                    || player.getEntityWorld() != strike.originWorld()
                    || EWAttachments.getAffinity(player) != WizardAffinity.WIND
                    || !isSourceWandHeld(player, strike.sourceWand())
                    || !player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.ZEPHYR_WINGS)) {
                finish(player);
                continue;
            }

            tickStrike(player, strike, server.getTicks());
        }
    }

    private static void tickStrike(ServerPlayerEntity player, ActiveStrike strike, int currentTick) {
        ServerWorld world = player.getEntityWorld();
        player.fallDistance = 0.0f;

        Vec3d velocity = player.getVelocity();
        if (velocity.y < -0.5 && currentTick % 2 == 0) {
            spawnDescentRing(world, player, currentTick);
        }

        if (currentTick - strike.startTick() <= IMPACT_GRACE_TICKS
                || (!player.isOnGround() && !player.horizontalCollision)) {
            return;
        }

        float power = 3.0f + (float) velocity.length() * 2.0f;
        if (power < 3.0f) {
            power = 4.0f;
        }

        createWindImpact(world, player, power);
        finish(player);
    }

    private static void spawnDescentRing(ServerWorld world, ServerPlayerEntity player, int currentTick) {
        int points = 16;
        double radius = 1.5;
        double y = player.getY() + 1.0;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = player.getX() + radius * Math.cos(angle);
            double z = player.getZ() + radius * Math.sin(angle);
            world.spawnParticles(ModParticles.WIND_AIR_RIBBON, x, y, z, 1,
                    0.0, 0.0, 0.0, 0.025);
            if ((i & 1) == 0) {
                world.spawnParticles(ModParticles.WIND_MOTE, x, y, z, 1,
                        0.0, 0.0, 0.0, 0.015);
            }
        }

        if (currentTick % 6 == 0) {
            world.spawnParticles(ModParticles.WIND_BURST_RING,
                    player.getX(), y, player.getZ(), 1,
                    0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void createWindImpact(ServerWorld world, ServerPlayerEntity player, float power) {
        // This is the same damage source, power, fire flag, and source type as
        // the prior explosion. Only its visual payload is replaced: custom
        // Zephyr art and no vanilla block-debris particle pool.
        world.createExplosion(
                player,
                Explosion.createDamageSource(world, player),
                null,
                player.getX(), player.getY(), player.getZ(),
                power,
                false,
                World.ExplosionSourceType.NONE,
                ModParticles.WIND_ZEPHYR_IMPACT,
                ModParticles.WIND_ZEPHYR_IMPACT,
                Pool.<BlockParticleEffect>empty(),
                SoundEvents.ENTITY_GENERIC_EXPLODE);

        world.spawnParticles(ModParticles.WIND_BURST_RING,
                player.getX(), player.getY() + 0.25, player.getZ(),
                3, 0.35, 0.08, 0.35, 0.0);
        world.spawnParticles(ModParticles.WIND_AIR_RIBBON,
                player.getX(), player.getY() + 0.35, player.getZ(),
                30, 1.35, 0.3, 1.35, 0.12);
        world.spawnParticles(ModParticles.WIND_MOTE,
                player.getX(), player.getY() + 0.4, player.getZ(),
                36, 1.5, 0.45, 1.5, 0.16);
    }

    private static boolean isSourceWandHeld(ServerPlayerEntity player, ItemStack sourceWand) {
        return player.getMainHandStack() == sourceWand || player.getOffHandStack() == sourceWand;
    }

    private static void finishAll(MinecraftServer server) {
        for (UUID playerId : List.copyOf(ACTIVE.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                finish(player);
            } else {
                ACTIVE.remove(playerId);
            }
        }
    }

    private static void afterRespawn(ServerPlayerEntity oldPlayer,
            ServerPlayerEntity newPlayer, boolean alive) {
        ActiveStrike strike = ACTIVE.remove(oldPlayer.getUuid());
        oldPlayer.stopGliding();

        // End-return respawns can copy the temporary wings to a new player
        // object without being a death. Restore the saved chest directly onto
        // that live replacement before removing either orphaned wing stack.
        if (strike != null) {
            restoreChest(newPlayer, strike.savedChest());
        }

        purgeOrphanedWings(oldPlayer);
        purgeOrphanedWings(newPlayer);
    }

    private static void finish(ServerPlayerEntity player) {
        ActiveStrike strike = ACTIVE.remove(player.getUuid());
        if (strike == null) {
            purgeOrphanedWings(player);
            return;
        }

        player.stopGliding();
        restoreChest(player, strike.savedChest());
        purgeOrphanedWings(player);
    }

    private static void restoreChest(ServerPlayerEntity player, ItemStack savedChest) {
        ItemStack current = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!current.isEmpty() && !current.isOf(ModItems.ZEPHYR_WINGS)) {
            ItemStack displaced = current.copy();
            int emptySlot = player.getInventory().getEmptySlot();
            if (emptySlot >= 0) {
                player.getInventory().setStack(emptySlot, displaced);
            } else {
                // Avoid PlayerInventory.insertStack here: in creative it can
                // report success and erase an unaccepted stack, and partial
                // insertion can otherwise strand a remainder.
                player.dropItem(displaced, false);
            }
        }

        // The saved stack is a copy owned only by this session; equality with
        // another equipped stack never proves it is the original. Always put
        // the saved stack back after preserving any non-Wings replacement.
        player.equipStack(EquipmentSlot.CHEST, savedChest.copy());
    }

    private static void purgeOrphanedWings(ServerPlayerEntity player) {
        if (isActive(player)) {
            return;
        }

        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.ZEPHYR_WINGS)) {
            player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        if (player.currentScreenHandler.getCursorStack().isOf(ModItems.ZEPHYR_WINGS)) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        }
        if (player.currentScreenHandler != player.playerScreenHandler
                && player.playerScreenHandler.getCursorStack().isOf(ModItems.ZEPHYR_WINGS)) {
            player.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(ModItems.ZEPHYR_WINGS)) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    private record ActiveStrike(
            ItemStack sourceWand,
            ItemStack savedChest,
            ServerWorld originWorld,
            int startTick) {
    }
}
