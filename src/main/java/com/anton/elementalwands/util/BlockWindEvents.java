package com.anton.elementalwands.util;

import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Tells nearby clients when a Wind spell moment happens, so they can draw it as block wind. Purely visual: every
 * launch, hit and cooldown is decided before an event is sent. Clients draw Sky Shear blades and Gale Daggers from
 * the spell entities themselves; their hits are sent because a projectile at full range vanishes the same way.
 */
public final class BlockWindEvents {
    /** entity: the dashing player; pos: their body centre; dir: the dash direction; value: chain count (0 = first dash). */
    public static final int DASH = 1;
    /** entity: the launched player; pos: their feet at launch. */
    public static final int UPDRAFT = 2;
    /** entity: the caster; pos: their feet; dir: the launch direction. */
    public static final int ZEPHYR_LAUNCH = 3;
    /** entity: the caster; pos: the impact point; value: the burst's visual radius. */
    public static final int ZEPHYR_LAND = 4;
    /** entity: a Sky Shear blade or Gale Dagger; pos: where it struck a creature or a block; dir: its flight direction. */
    public static final int HIT = 5;

    private BlockWindEvents() {}

    public static void send(ServerWorld world, int kind, int entity, Vec3d pos, Vec3d dir, float value) {
        var payload = new ModNetworking.BlockWindPayload(kind, entity, pos, dir, value);
        for (var player : PlayerLookup.around(world, pos, 96))
            if (ServerPlayNetworking.canSend(player, ModNetworking.BlockWindPayload.ID)) ServerPlayNetworking.send(player, payload);
    }
}
