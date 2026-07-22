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
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
    private static final int LANDING_VFX_TICKS = 8;
    private static final Map<UUID, ActiveStrike> ACTIVE = new HashMap<>();
    private static final List<LandingBurst> LANDING_BURSTS = new ArrayList<>();
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
        tickLandingBursts();

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
        if (velocity.y > 0.18) {
            spawnAscentStreams(world, player, velocity, currentTick);
        }
        if (velocity.y < -0.5 && currentTick % 2 == 0) {
            spawnDescentCompressionCircles(world, player, currentTick);
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

    private static void spawnAscentStreams(ServerWorld world, ServerPlayerEntity player,
            Vec3d velocity, int currentTick) {
        Vec3d direction = velocity.lengthSquared() > 1.0e-4
                ? velocity.normalize()
                : player.getRotationVec(1.0f).normalize();
        Vec3d lateral = horizontalPerpendicular(direction);
        Vec3d center = new Vec3d(player.getX(), player.getBodyY(0.52), player.getZ());

        for (double side : new double[] {-1.0, 1.0}) {
            Vec3d wingRoot = center.add(lateral.multiply(side * 0.58));
            for (int lane = 0; lane < 4; lane++) {
                Vec3d point = wingRoot.subtract(direction.multiply(0.18 + lane * 0.34));
                Vec3d wake = direction.multiply(-0.075).add(0.0, -0.018 * lane, 0.0);
                spawnDirected(world, ModParticles.WIND_SLIPSTREAM, point, wake);
            }

            if (((currentTick + (side > 0.0 ? 1 : 0)) & 1) == 0) {
                Vec3d featherPoint = wingRoot.subtract(direction.multiply(0.45));
                Vec3d featherVelocity = direction.multiply(-0.055)
                        .add(lateral.multiply(side * 0.035));
                spawnDirected(world, ModParticles.WIND_SHEAR_FEATHER,
                        featherPoint, featherVelocity);
            }
        }

        if (currentTick % 4 == 0) {
            world.spawnParticles(ModParticles.WIND_BURST_RING,
                    center.x, center.y, center.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnDescentCompressionCircles(ServerWorld world,
            ServerPlayerEntity player, int currentTick) {
        Vec3d center = new Vec3d(player.getX(), player.getY() + 0.95, player.getZ());
        double phase = currentTick * 0.19;
        spawnCompressionCircle(world, center.add(0.0, 0.48, 0.0),
                1.62, 16, phase, 0.095);
        spawnCompressionCircle(world, center.add(0.0, -0.38, 0.0),
                1.02, 12, -phase * 0.82, 0.075);

        if (currentTick % 6 == 0) {
            world.spawnParticles(ModParticles.WIND_BURST_RING,
                    center.x, center.y, center.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnCompressionCircle(ServerWorld world, Vec3d center,
            double radius, int points, double phase, double inwardSpeed) {
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0 * i / points;
            Vec3d radial = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
            Vec3d point = center.add(radial.multiply(radius));
            Vec3d inward = radial.multiply(-inwardSpeed).add(0.0, -0.045, 0.0);
            spawnDirected(world, ModParticles.WIND_SLIPSTREAM, point, inward);

            if ((i & 3) == 0) {
                Vec3d tangent = new Vec3d(-radial.z, 0.0, radial.x);
                spawnDirected(world, ModParticles.WIND_SHEAR_FEATHER,
                        point.add(0.0, 0.06, 0.0),
                        inward.add(tangent.multiply((i & 4) == 0 ? 0.035 : -0.035)));
            }
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

        int immediateDensity = Math.max(24, Math.min(48, Math.round(power * 4.0f)));
        world.spawnParticles(ModParticles.WIND_BURST_RING,
                player.getX(), player.getY() + 0.25, player.getZ(),
                3, 0.35, 0.08, 0.35, 0.0);
        world.spawnParticles(ModParticles.WIND_SLIPSTREAM,
                player.getX(), player.getY() + 0.35, player.getZ(),
                immediateDensity, 1.35, 0.3, 1.35, 0.12);
        world.spawnParticles(ModParticles.WIND_SHEAR_FEATHER,
                player.getX(), player.getY() + 0.4, player.getZ(),
                Math.max(16, immediateDensity / 2), 1.5, 0.48, 1.5, 0.15);
        world.spawnParticles(ModParticles.WIND_MOTE,
                player.getX(), player.getY() + 0.4, player.getZ(),
                immediateDensity, 1.5, 0.45, 1.5, 0.16);

        double visualRadius = Math.max(4.5, Math.min(9.0, 1.8 + power * 0.88));
        int ringPoints = Math.max(24, Math.min(42, 18 + Math.round(power * 2.2f)));
        LANDING_BURSTS.add(new LandingBurst(
                world,
                new Vec3d(player.getX(), player.getY() + 0.18, player.getZ()),
                visualRadius,
                ringPoints));
    }

    private static void tickLandingBursts() {
        Iterator<LandingBurst> iterator = LANDING_BURSTS.iterator();
        while (iterator.hasNext()) {
            LandingBurst burst = iterator.next();
            spawnLandingStage(burst);
            burst.age++;
            if (burst.age >= LANDING_VFX_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void spawnLandingStage(LandingBurst burst) {
        double progress = (burst.age + 1.0) / LANDING_VFX_TICKS;
        double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
        double outerRadius = burst.maxRadius * (0.12 + eased * 0.88);
        double innerRadius = outerRadius * 0.62;
        double phase = burst.age * 0.17;

        spawnLandingRing(burst.world, burst.center, outerRadius,
                burst.ringPoints, phase, 0.13, true);
        spawnLandingRing(burst.world, burst.center.add(0.0, 0.10, 0.0), innerRadius,
                Math.max(14, burst.ringPoints / 2), -phase * 0.72, 0.09, false);

        // A short central column holds the landing silhouette together while
        // the two pressure fronts race outward across the ground.
        int columnPoints = 4;
        double columnHeight = 0.45 + (1.0 - progress) * 3.4;
        for (int i = 0; i < columnPoints; i++) {
            Vec3d point = burst.center.add(0.0, 0.25 + columnHeight * i / columnPoints, 0.0);
            spawnDirected(burst.world, ModParticles.WIND_SLIPSTREAM,
                    point, new Vec3d(0.0, 0.12 + i * 0.018, 0.0));
        }
    }

    private static void spawnLandingRing(ServerWorld world, Vec3d center,
            double radius, int points, double phase, double speed, boolean feathers) {
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0 * i / points;
            Vec3d radial = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
            Vec3d point = center.add(radial.multiply(radius));
            Vec3d outward = radial.multiply(speed).add(0.0, 0.018, 0.0);
            spawnDirected(world, ModParticles.WIND_SLIPSTREAM, point, outward);
            if (feathers && i % 3 == 0) {
                Vec3d tangent = new Vec3d(-radial.z, 0.0, radial.x);
                spawnDirected(world, ModParticles.WIND_SHEAR_FEATHER,
                        point.add(0.0, 0.08, 0.0),
                        outward.add(tangent.multiply((i & 1) == 0 ? 0.055 : -0.055)));
            }
            if (i % 4 == 0) {
                spawnDirected(world, ModParticles.WIND_MOTE,
                        point.add(0.0, 0.04, 0.0), outward.multiply(0.65));
            }
        }
    }

    private static Vec3d horizontalPerpendicular(Vec3d direction) {
        Vec3d lateral = new Vec3d(-direction.z, 0.0, direction.x);
        return lateral.lengthSquared() > 1.0e-4
                ? lateral.normalize()
                : new Vec3d(1.0, 0.0, 0.0);
    }

    private static void spawnDirected(ServerWorld world, SimpleParticleType particle,
            Vec3d position, Vec3d velocity) {
        world.spawnParticles(particle,
                position.x, position.y, position.z,
                0, velocity.x, velocity.y, velocity.z, 1.0);
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
        LANDING_BURSTS.clear();
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

    private static final class LandingBurst {
        private final ServerWorld world;
        private final Vec3d center;
        private final double maxRadius;
        private final int ringPoints;
        private int age;

        private LandingBurst(ServerWorld world, Vec3d center,
                double maxRadius, int ringPoints) {
            this.world = world;
            this.center = center;
            this.maxRadius = maxRadius;
            this.ringPoints = ringPoints;
        }
    }
}
