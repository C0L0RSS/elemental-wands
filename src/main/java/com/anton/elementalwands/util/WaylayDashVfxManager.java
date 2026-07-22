package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Five-tick, server-authoritative visual tracer for Waylay Dash movement. */
public final class WaylayDashVfxManager {

    private static final int TRACE_TICKS = 5;
    private static final double SAMPLE_SPACING = 0.30;
    private static final Map<UUID, DashTrace> ACTIVE = new HashMap<>();
    private static boolean initialized;

    private WaylayDashVfxManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerTickEvents.END_WORLD_TICK.register(WaylayDashVfxManager::tickWorld);
        ServerPlayerEvents.LEAVE.register(player -> ACTIVE.remove(player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> ACTIVE.remove(player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
    }

    public static void start(ServerPlayerEntity player, int chainCount) {
        ACTIVE.put(player.getUuid(), new DashTrace(
                player.getEntityWorld(), bodyCenter(player), TRACE_TICKS, chainCount > 0));
    }

    private static void tickWorld(ServerWorld world) {
        Iterator<Map.Entry<UUID, DashTrace>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DashTrace> entry = iterator.next();
            DashTrace trace = entry.getValue();
            if (trace.world != world) {
                continue;
            }

            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.getEntityWorld() != world) {
                iterator.remove();
                continue;
            }

            Vec3d current = bodyCenter(player);
            spawnMovementSegment(world, trace, current);
            trace.previousPosition = current;
            trace.ticksRemaining--;
            if (trace.ticksRemaining <= 0) {
                iterator.remove();
            }
        }
    }

    private static void spawnMovementSegment(ServerWorld world, DashTrace trace, Vec3d current) {
        Vec3d delta = current.subtract(trace.previousPosition);
        double distance = delta.length();
        if (distance < 0.025) {
            return;
        }

        Vec3d direction = delta.normalize();
        Vec3d lateral = horizontalPerpendicular(direction);
        int samples = Math.max(1, Math.min(20, (int) Math.ceil(distance / SAMPLE_SPACING)));
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            Vec3d point = trace.previousPosition.add(delta.multiply(progress));
            Vec3d trailingVelocity = direction.multiply(-0.065);
            spawnDirected(world, ModParticles.WIND_SLIPSTREAM, point, trailingVelocity);

            double featherSide = ((sample + trace.ticksRemaining) & 1) == 0 ? -1.0 : 1.0;
            Vec3d featherVelocity = trailingVelocity.add(lateral.multiply(featherSide * 0.045));
            spawnDirected(world, ModParticles.WIND_SHEAR_FEATHER,
                    point.add(lateral.multiply(featherSide * 0.18)), featherVelocity);

            if (trace.chained) {
                // A chained second dash gains two wider pressure rails. Keeping
                // these spatially separate communicates the stronger chain even
                // when the player crosses the first trace at an angle.
                double laneWidth = 0.38 + progress * 0.10;
                for (double side : new double[] {-1.0, 1.0}) {
                    Vec3d lanePoint = point.add(lateral.multiply(side * laneWidth));
                    spawnDirected(world, ModParticles.WIND_SLIPSTREAM,
                            lanePoint, trailingVelocity.add(lateral.multiply(side * 0.02)));
                }
            }
        }
    }

    private static Vec3d bodyCenter(ServerPlayerEntity player) {
        return new Vec3d(player.getX(), player.getBodyY(0.48), player.getZ());
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

    private static final class DashTrace {
        private final ServerWorld world;
        private final boolean chained;
        private Vec3d previousPosition;
        private int ticksRemaining;

        private DashTrace(ServerWorld world, Vec3d previousPosition,
                int ticksRemaining, boolean chained) {
            this.world = world;
            this.previousPosition = previousPosition;
            this.ticksRemaining = ticksRemaining;
            this.chained = chained;
        }
    }
}
