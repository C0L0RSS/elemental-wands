package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Airburst: stones that fly to a point and burst there, hurting everyone within {@link #BLAST}.
 * It is the Guardian's answer to a player in the air, and in co-op to the players it is not facing.
 * <ul>
 * <li>Cast: a player who has been launched draws it at once. Stones form over its head (the
 *     {@code fan} clip, timed by {@link GuardianFanRules}), fly at where that player will be and
 *     knock them out of the air.
 * <li>Back stones: in co-op, stones lift off its back while it attacks someone else (sockets and
 *     pacing in {@link GuardianVolleyRules}). At a player on the ground they burst on a ring marked
 *     {@link #FUSE} ticks ahead; at a player in the air they fly straight to them.
 * </ul>
 */
public final class GuardianAirburstRules {
    /** Blocks from the burst a body is hurt, and what it takes. */
    public static final double BLAST = 4;
    public static final float DAMAGE = 10;
    /** Blocks a tick an aimed stone flies, and how near or far its target may be. */
    public static final double SPEED = GuardianFanRules.SPEED, MIN_RANGE = 4, RANGE = GuardianFanRules.RANGE;
    /** A player this many blocks clear of the floor, off the ground this many ticks, has been launched. A jump never is. */
    public static final double CLEARANCE = 2.5;
    public static final int AIRBORNE_TICKS = 3;
    /** Ticks a ground ring is marked before the back stones burst on it. */
    public static final int FUSE = 26;
    /** A stone's aim leads its target by at most this far. */
    public static final double LEAD_MAX = 10;
    /** Downward speed a burst gives a player it catches in the air. */
    public static final double KNOCK_DOWN = 1.1;
    private GuardianAirburstRules() {}

    public static boolean launched(double clearance, boolean grounded, int ticks) {
        return !grounded && clearance > CLEARANCE && ticks >= AIRBORNE_TICKS;
    }

    public static int flightTicks(Vec3d origin, Vec3d aim) {
        return Math.max(1, (int)Math.ceil(origin.distanceTo(aim) / SPEED));
    }

    /**
     * Where a body will be after {@code ticks}: its velocity carried forward, and its fall if it is
     * falling ({@code fall} is its vertical acceleration, never upward). Capped, so a dash cannot fling
     * the aim away.
     */
    public static Vec3d lead(Vec3d position, Vec3d velocity, double fall, int ticks) {
        int t = Math.max(0, ticks);
        Vec3d offset = velocity.multiply(t).add(0, .5 * Math.clamp(fall, -.08, 0) * t * t, 0);
        if (offset.length() > LEAD_MAX) offset = offset.normalize().multiply(LEAD_MAX);
        return position.add(offset);
    }

    /** Where a stone from {@code origin} meets a body, allowing for the wait before release and its own flight. */
    public static Vec3d aim(Vec3d origin, Vec3d position, Vec3d velocity, double fall, int wait) {
        Vec3d aim = position;
        for (int i = 0; i < 4; i++) aim = lead(position, velocity, fall, wait + flightTicks(origin, aim));
        return aim;
    }

    /** A body any part of which is within {@link #BLAST} of the burst. */
    public static boolean inBlast(Vec3d center, Box victim) {
        double x = Math.clamp(center.x, victim.minX, victim.maxX) - center.x, y = Math.clamp(center.y, victim.minY, victim.maxY) - center.y,
                z = Math.clamp(center.z, victim.minZ, victim.maxZ) - center.z;
        return x * x + y * y + z * z <= BLAST * BLAST;
    }
}
