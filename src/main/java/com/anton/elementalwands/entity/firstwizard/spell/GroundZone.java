package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Lingering ground that hurts whoever stands in it: a disc (Fireball's burning ground) or a strip along a line (a
 * dash's wake). It arms {@link FirstWizardRules#GROUND_ARM} ticks after it appears, then burns on contact and once a
 * second for {@link FirstWizardRules#GROUND} (about 2 health a second in diamond or netherite). It is the copy's own
 * ground, decided here on the server; its effect entity only shows it, and is discarded when it ends.
 */
public final class GroundZone implements Lingering {
    /** A body must have its feet this close above the floor to stand in it (a jump over it is still burned on landing). */
    public static final double REACH_UP = 1.2;

    private final Cast cast;
    private final Vec3d from, to;
    private final double half;
    private final long armed, until;
    private final float damage;
    private final FirstWizardEffectEntity effect;
    private final Map<UUID, Long> next = new HashMap<>();

    private GroundZone(Cast cast, Vec3d from, Vec3d to, double half, int life, float damage, FirstWizardEffectEntity effect) {
        this.cast = cast; this.from = from; this.to = to; this.half = half; this.damage = damage; this.effect = effect;
        long now = cast.now();
        armed = now + FirstWizardRules.GROUND_ARM;
        until = now + life;
    }

    /** A disc of this radius on the floor at centre, burning for life ticks. */
    public static GroundZone disc(Cast cast, Vec3d centre, double radius, int life, float damage, FirstWizardEffectEntity effect) {
        return new GroundZone(cast, centre, centre, radius, life, damage, effect);
    }

    /** A strip half wide each side of the line from one point to another, burning for life ticks. */
    public static GroundZone strip(Cast cast, Vec3d from, Vec3d to, double half, int life, float damage, FirstWizardEffectEntity effect) {
        return new GroundZone(cast, from, to, half, life, damage, effect);
    }

    @Override public boolean tick(ServerWorld world, long now) {
        if (now >= until) { if (effect != null) effect.discard(); return false; }
        if (now < armed) return true;
        for (ServerPlayerEntity player : cast.players()) {
            if (now < next.getOrDefault(player.getUuid(), Long.MIN_VALUE) || !standsIn(player.getBoundingBox(), from.y)) continue;
            boolean burned = cast.hit(player, damage, Cast.Hit.GROUND, effect);
            // A hurt cooldown from another blow refuses it: try again shortly rather than a whole second later.
            next.put(player.getUuid(), now + (burned ? FirstWizardRules.GROUND_EVERY : 4));
        }
        return true;
    }

    @Override public void cancel(ServerWorld world) { if (effect != null) effect.discard(); }

    /** Whether a body's box touches the ground: its feet near the floor and its footprint within reach of the line. */
    public boolean standsIn(Box box, double floor) {
        if (box.minY > floor + REACH_UP || box.maxY < floor - .5) return false;
        return reach(box, from, to) <= half;
    }

    /**
     * Horizontal distance from the line to a box's footprint: from the line's point nearest the box's centre to the
     * nearest point of the footprint. A footprint is small next to any zone, so this is close enough.
     */
    public static double reach(Box box, Vec3d from, Vec3d to) {
        Vec3d near = closest(from, to, box);
        double px = Math.clamp(near.x, box.minX, box.maxX), pz = Math.clamp(near.z, box.minZ, box.maxZ);
        return segment(px, pz, from.x, from.z, to.x, to.z);
    }

    /** The point of the line nearest the box's centre. */
    private static Vec3d closest(Vec3d from, Vec3d to, Box box) {
        double cx = (box.minX + box.maxX) / 2, cz = (box.minZ + box.maxZ) / 2;
        double dx = to.x - from.x, dz = to.z - from.z, l = dx * dx + dz * dz;
        double t = l < 1e-9 ? 0 : Math.clamp(((cx - from.x) * dx + (cz - from.z) * dz) / l, 0, 1);
        return new Vec3d(from.x + dx * t, from.y, from.z + dz * t);
    }

    /** Horizontal distance from a point to a segment. */
    public static double segment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, l = dx * dx + dz * dz;
        double t = l < 1e-9 ? 0 : Math.clamp(((px - ax) * dx + (pz - az) * dz) / l, 0, 1);
        return Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
    }
}
