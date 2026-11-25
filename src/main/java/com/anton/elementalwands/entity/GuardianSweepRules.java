package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Sweep: a turning backhand for players beside or behind it. It does not turn to face anyone
 * first, so whoever got behind it is still behind it when the arm comes round. The slam covers its
 * front; this covers everything else, and reaches further.
 */
public final class GuardianSweepRules {
    /** Server ticks of the {@code sweep} clip: the arm points straight behind it at IMPACT. */
    public static final int IMPACT = 30, DURATION = 64;
    public static final double REACH = 7;
    /** The wedge either side of straight ahead that the arm never passes through. */
    public static final double FRONT_DEGREES = 60;
    /** Standing inside its body is never safe. */
    public static final double UNDER = 1.6;
    public static final float DAMAGE = 14;
    private GuardianSweepRules() {}

    /** Degrees round from straight ahead, 0 to 180, of a point seen from where it stands. */
    public static double bearing(Vec3d anchor, float yaw, Vec3d point) {
        Vec3d flat = new Vec3d(point.x - anchor.x, 0, point.z - anchor.z);
        if (flat.lengthSquared() < 1e-8) return 0;
        return Math.toDegrees(Math.acos(Math.clamp(Vec3d.fromPolar(0, yaw).dotProduct(flat.normalize()), -1, 1)));
    }

    /** Beside or behind it: outside the front wedge. */
    public static boolean behind(Vec3d anchor, float yaw, Vec3d point) {
        return bearing(anchor, yaw, point) > FRONT_DEGREES;
    }

    /** Close enough, and beside or behind it, for the arm to reach. */
    public static boolean inArc(Vec3d anchor, float yaw, Vec3d point) {
        return Math.hypot(point.x - anchor.x, point.z - anchor.z) <= REACH && behind(anchor, yaw, point);
    }

    /** The arm strikes a body within reach whose middle is outside the front wedge, or that stands under it. */
    public static boolean contact(Vec3d anchor, float yaw, Box victim) {
        if (!GuardianCombatRules.radial(anchor, victim, REACH, 3.5)) return false;
        Vec3d middle = victim.getCenter();
        return Math.hypot(middle.x - anchor.x, middle.z - anchor.z) < UNDER || behind(anchor, yaw, middle);
    }
}
