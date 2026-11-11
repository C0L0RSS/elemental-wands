package com.anton.elementalwands.util;

import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Tells nearby clients when a Nature spell moment happens, so they can draw it as block nature. Purely visual: every
 * hit, heal and blessing is decided before an event is sent. Clients draw seed trails and wilting flowers from the
 * entities themselves.
 */
public final class BlockNatureEvents {
    /** pos: the planted flower's base. */
    public static final int PLANT = 1;
    /** pos: the flower's base as Full Bloom grows its second head. */
    public static final int GROW = 2;
    /** pos: the flower's base as it returns to one head. */
    public static final int SHRINK = 3;
    /** pos: the head's mouth; dir: the shot direction; value: the head's scale. */
    public static final int SHOT = 4;
    /** pos: the flower's base as it is removed. */
    public static final int WILT = 5;
    /** pos: where a Seed struck a creature; value: 1 for a flower's seed, 0 for the caster's. */
    public static final int SEED_HIT = 6;
    /** pos: where a Seed struck a block. */
    public static final int SEED_BLOCK = 7;
    /** entity: a player whose health just rose from a Nature heal or blessing; pos: their feet. */
    public static final int HEAL = 8;
    /** pos: where Thornbite's jaws closed on a creature. */
    public static final int BITE = 9;
    /** entity: the caster; pos: the bloom's origin; value: the wave's radius. */
    public static final int FULL_BLOOM = 10;
    /** entity: the launched player; pos: their feet at launch. */
    public static final int SPRING_LAUNCH = 11;
    /** pos: a Springbloom pad's base as it expires or breaks. */
    public static final int SPRING_END = 12;
    /** pos: where a thrown Springbloom pod landed without planting. */
    public static final int POD_HIT = 13;

    private BlockNatureEvents() {}

    public static void send(ServerWorld world, int kind, int entity, Vec3d pos, Vec3d dir, float value) {
        var payload = new ModNetworking.BlockNaturePayload(kind, entity, pos, dir, value);
        for (var player : PlayerLookup.around(world, pos, 96))
            if (ServerPlayNetworking.canSend(player, ModNetworking.BlockNaturePayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    public static void send(ServerWorld world, int kind, Vec3d pos) { send(world, kind, -1, pos, Vec3d.ZERO, 0); }
}
