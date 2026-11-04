package com.anton.elementalwands.util;

import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Tells nearby clients when a Space spell moment happens, so they can draw it as void glass. Purely visual: every
 * teleport, hit and cooldown is decided before an event is sent. Clients draw Singularity Bolts, Hollow Purple orbs,
 * Gravity Bombs and the Astral orb and double from the entities themselves; these events cover what an entity can't
 * show (where a bolt struck, both ends of a teleport, a charge that follows the caster, a collapse, a poof).
 */
public final class BlockSpaceEvents {
    /** entity: a Singularity Bolt; pos: where it struck; dir: its flight direction; value: its strength (1, or 0.7 for an echo). */
    public static final int BOLT_HIT = 1;
    /** entity: the caster; pos: their feet before; dir: their feet after (Blink Rift or an Astral Double swap). */
    public static final int BLINK = 2;
    /** entity: the caster; pos: their feet; dir: their look. A blink with no safe spot. */
    public static final int BLINK_FAIL = 3;
    /** pos: where something dissolves (an Astral Double or its orb, or what Hollow Purple swallows). */
    public static final int POOF = 4;
    /** entity: the caster; pos: their feet as Hollow Purple's charge begins. */
    public static final int CHARGE = 5;
    /** entity: the caster; the charge ended early (death, world exit, encounter teardown). */
    public static final int CHARGE_END = 6;
    /** entity: a Hollow Purple orb; pos: where it collapses. */
    public static final int COLLAPSE = 7;

    private BlockSpaceEvents() {}

    public static void send(ServerWorld world, int kind, int entity, Vec3d pos, Vec3d dir, float value) {
        var payload = new ModNetworking.BlockSpacePayload(kind, entity, pos, dir, value);
        for (var player : PlayerLookup.around(world, pos, 96))
            if (ServerPlayNetworking.canSend(player, ModNetworking.BlockSpacePayload.ID)) ServerPlayNetworking.send(player, payload);
    }
    public static void send(ServerWorld world, int kind, int entity, Vec3d pos) { send(world, kind, entity, pos, Vec3d.ZERO, 0); }
}
