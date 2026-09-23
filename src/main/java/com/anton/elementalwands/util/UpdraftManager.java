package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModParticles;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.particle.ParticleTypes;
import java.util.*;

/** A single vertical impulse; ordinary Minecraft movement owns the rest of the jump. */
public final class UpdraftManager {
    public static final String ID = "updraft";
    public static final int COOLDOWN = 160;
    // Vanilla gravity/drag give an unobstructed rise of approximately ten blocks.
    public static final double UPWARD_SPEED = 1.343;
    private static final class Flight {
        final ServerPlayerEntity player; final ServerWorld world; final long started;
        long landed = -1;
        Flight(ServerPlayerEntity p) { player = p; world = p.getEntityWorld(); started = world.getTime(); }
    }
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var flight : List.copyOf(FLIGHTS.values())) {
                var p = flight.player;
                if (!valid(flight) || p.hasVehicle() || p.isGliding() || p.getAbilities().flying
                        || p.isTouchingWater() || p.isInLava() || p.isClimbing()
                        || (flight.landed >= 0 && flight.world.getTime() > flight.landed + 2)) {
                    finish(p); continue;
                }
                // onGround may precede the final movement/fall-damage packet. Confirm
                // physical contact and allow its damage callback to consume protection first.
                if (flight.landed < 0 && flight.world.getTime() > flight.started + 2 && p.isOnGround()
                        && flight.world.raycast(new RaycastContext(p.getEntityPos(), p.getEntityPos().add(0,-.06,0),
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p)).getType() != HitResult.Type.MISS)
                    flight.landed = flight.world.getTime();
                long age = flight.world.getTime() - flight.started;
                if (age > 0 && age <= 8) spiral(p, age);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> finish(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLIGHTS.clear());
    }

    public static long remaining(PlayerEntity p) {
        long elapsed = p.getEntityWorld().getTime() - p.getAttachedOrElse(EWAttachments.UPDRAFT_LAST_CAST, -1_000_000_000L);
        if (EntangleTracker.getStacks(p) > 0) elapsed /= 2;
        return Math.max(0, COOLDOWN - elapsed);
    }

    public static boolean cast(ServerPlayerEntity p) {
        var world = p.getEntityWorld();
        if (!p.isAlive() || p.isRemoved() || p.isSpectator() || p.hasVehicle() || p.isGliding()
                || p.getAbilities().flying || EWAttachments.getAffinity(p) != WizardAffinity.WIND
                || !GuardianArenaManager.canCast(p) || HollowPurpleChargeManager.isCharging(world, p)
                || !(p.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandProgression.owns(p, WandSpells.find(ID)) || !WandLoadouts.get(p).contains(ID)) return false;
        long remaining = remaining(p);
        if (remaining > 0) { AbstractWandItem.sendCooldownActionbar(p, "Updraft", (int) remaining); return false; }
        if (!AbstractWandItem.tryStartCooldown(world, p, p.getMainHandStack(), ID, COOLDOWN)) return false;
        p.setAttached(EWAttachments.UPDRAFT_LAST_CAST, world.getTime());
        // Replacing a flower launch also releases that spell's special horizontal-input lock.
        SpringbloomManager.finishLanding(p);
        FLIGHTS.put(p.getUuid(), new Flight(p));
        p.setVelocity(launch(p.getVelocity())); p.setOnGround(false); p.fallDistance = 0;
        // A vertical-only message preserves the owning client's current horizontal momentum.
        // Do not send a full velocity packet with potentially stale server X/Z components.
        ServerPlayNetworking.send(p, ModNetworking.UpdraftLaunchPayload.INSTANCE);
        WandLoadouts.markCombat(p);
        // A circular smoke puff expands from the launch point, staying near the floor.
        // Midair casts use the same ring at the caster's feet.
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20, dx = Math.cos(angle), dz = Math.sin(angle);
            world.spawnParticles(ParticleTypes.CLOUD, p.getX() + dx * .35, p.getY() + .12,
                    p.getZ() + dz * .35, 0, dx * .14, .005, dz * .14, 1);
        }
        spiral(p, 0);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, .8f, 1.25f);
        return true;
    }

    public static Vec3d launch(Vec3d incoming) { return new Vec3d(incoming.x, UPWARD_SPEED, incoming.z); }
    private static boolean valid(Flight f) {
        var p = f.player;
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.getEntityWorld() == f.world
                && EWAttachments.getAffinity(p) == WizardAffinity.WIND && GuardianArenaManager.canCast(p);
    }
    public static boolean protectedFall(ServerPlayerEntity p) {
        var f = FLIGHTS.get(p.getUuid());
        return f != null && f.player == p && valid(f);
    }
    public static void finish(ServerPlayerEntity p) { FLIGHTS.remove(p.getUuid()); }
    private static void spiral(ServerPlayerEntity p, long age) {
        for (int arm = 0; arm < 2; arm++) {
            double angle = age * .8 + arm * Math.PI;
            p.getEntityWorld().spawnParticles(ModParticles.WIND_AIR_RIBBON,
                    p.getX() + Math.cos(angle) * .55, p.getY() + .15 + age * .06,
                    p.getZ() + Math.sin(angle) * .55, 1, .025, .025, .025, .015);
        }
    }
    private UpdraftManager() {}
}
