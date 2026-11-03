package com.anton.elementalwands.util;

import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Tells nearby clients when a Fire spell moment happens, so they can draw it as block fire.
 * Purely visual: every hit, block and cooldown is decided before an event is sent.
 */
public final class BlockFireEvents {
    /** pos: ignition point; dir: aim. */
    public static final int INFERNO_CAST = 1;
    /** pos: the target's body; dir: flight direction. */
    public static final int INFERNO_HIT = 2;
    /** pos: where the wave stopped; dir: flight direction; aux: the face it struck, or zero in open air. */
    public static final int INFERNO_BREAK = 3;
    /** entity: caster; pos: eye at the cast; dir: the front's direction. */
    public static final int PYRE_CAST = 4;
    /** pos: the target's body; dir: the front's direction. */
    public static final int PYRE_HIT = 5;
    /** entity: caster; pos: landing feet. */
    public static final int LEAP_LAND = 6;
    /** pos: ember; aux: the face it was stuck to, or zero on a creature; value: pop order. */
    public static final int FLASHOVER_POP = 7;
    /** entity: the meteor; pos: the marked landing point; value: the meteor's spawn height. */
    public static final int METEOR_FALL = 8;
    /** pos: impact point. */
    public static final int METEOR_IMPACT = 9;
    /** entity: caster, sent while spraying; value: heat 0..100; aux.x: ticks spent spraying. */
    public static final int FLAME = 10;
    /** entity: caster whose wand just overheated. */
    public static final int FLAME_OVERHEAT = 11;

    private BlockFireEvents() {}

    public static void send(ServerWorld world, double radius, int kind, int entity, Vec3d pos, Vec3d dir, Vec3d aux, float value) {
        var payload = new ModNetworking.BlockFirePayload(kind, entity, pos, dir, aux, value);
        for (var player : PlayerLookup.around(world, pos, radius))
            if (ServerPlayNetworking.canSend(player, ModNetworking.BlockFirePayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    public static void send(ServerWorld world, int kind, Vec3d pos, Vec3d dir) {
        send(world, 96, kind, -1, pos, dir, Vec3d.ZERO, 0);
    }
}
