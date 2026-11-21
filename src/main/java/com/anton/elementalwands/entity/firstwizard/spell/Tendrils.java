package com.anton.elementalwands.entity.firstwizard.spell;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The geometry of phase two's tendrils, shared by the ink moves, the phase change and the contract check: pure, with no
 * world access. Angles are horizontal, in radians, measured as {@code atan2(z, x)} round the copy's feet.
 *
 * <p>Three tendrils grow from the wound where its off arm was. Two carry the body; the third is the weapon. A tendril
 * leaves the wound at shoulder height and reaches the floor about {@link #INNER} out, so a body closer in than that
 * is under its arch, as a player under the Pyre King's chain is under his scythe.</p>
 */
public final class Tendrils {
    private Tendrils() {}

    /** How far a low sweep reaches from the copy's feet, and how far in it first touches the floor. */
    public static final double REACH = 9, INNER = 1.5;
    /** Feet at least this high over the floor clear a low sweep (the Pyre King's low scythe and Sky Shear use the same). */
    public static final double CLEAR = .6;
    /** Half the thickness of a sweeping tendril where it lies on the floor. */
    public static final double THICK = .35;

    /** The horizontal angle of a direction. */
    public static double angle(Vec3d dir) { return Math.atan2(dir.z, dir.x); }

    /** A horizontal unit direction from an angle. */
    public static Vec3d dir(double angle) { return new Vec3d(Math.cos(angle), 0, Math.sin(angle)); }

    /**
     * Whether a low tendril sweeping round {@code centre} from angle a0 to a1 this tick strikes a body: its feet below
     * {@link #CLEAR} over the floor, part of its footprint between {@link #INNER} and {@code reach} from the centre, and
     * the sweep crossing it (the tendril's thickness and the body's width included). a1 may be less than a0 (a sweep
     * the other way round). Judged for each body alone: nobody shelters anyone.
     */
    public static boolean sweeps(Box box, double floor, Vec3d centre, double a0, double a1, double reach) {
        if (box.minY - floor >= CLEAR - 1e-6 || box.maxY < floor - .5) return false;
        double cx = (box.minX + box.maxX) / 2 - centre.x, cz = (box.minZ + box.maxZ) / 2 - centre.z;
        double r = Math.max(box.maxX - box.minX, box.maxZ - box.minZ) / 2, d = Math.hypot(cx, cz);
        if (d + r < INNER || d - r > reach) return false;
        double half = d < 1e-6 ? Math.PI : Math.asin(Math.min(1, (r + THICK) / d));
        double at = Math.atan2(cz, cx), lo = Math.min(a0, a1), hi = Math.max(a0, a1);
        // Whether the swept wedge overlaps the body's width, at any turn of the body's angle.
        for (int n = -3; n <= 3; n++) {
            double m = at + n * 2 * Math.PI;
            if (m - half <= hi && m + half >= lo) return true;
        }
        return false;
    }

    /**
     * Whether a body's footprint lies on a line on the floor: within {@code half} of the segment from {@code from} to
     * {@code to}, its feet no higher than {@code reachUp} over the floor.
     */
    public static boolean onLine(Box box, double floor, Vec3d from, Vec3d to, double half, double reachUp) {
        if (box.minY > floor + reachUp || box.maxY < floor - .5) return false;
        return GroundZone.reach(box, from, to) <= half;
    }

    /** Whether a body's footprint reaches into a circle on the floor, its feet no higher than reachUp over it. */
    public static boolean inCircle(Box box, double floor, Vec3d centre, double radius, double reachUp) {
        return onLine(box, floor, centre, centre, radius, reachUp);
    }

    /**
     * Whether a body is in a cone on the floor: horizontally within {@code reach} of the apex, its angle within
     * {@code halfAngle} of the cone's (its own width included), and its feet no higher than reachUp over the floor.
     */
    public static boolean inCone(Box box, double floor, Vec3d apex, Vec3d dir, double reach, double halfAngle, double reachUp) {
        if (box.minY > floor + reachUp || box.maxY < floor - .5) return false;
        double cx = (box.minX + box.maxX) / 2 - apex.x, cz = (box.minZ + box.maxZ) / 2 - apex.z;
        double r = Math.max(box.maxX - box.minX, box.maxZ - box.minZ) / 2, d = Math.hypot(cx, cz);
        if (d - r > reach) return false;
        if (d <= r) return true;
        double off = Math.abs(wrap(Math.atan2(cz, cx) - angle(dir)));
        return off <= halfAngle + Math.asin(Math.min(1, r / d));
    }

    /** An angle brought into -pi..pi. */
    public static double wrap(double a) {
        a = (a + Math.PI) % (2 * Math.PI);
        if (a < 0) a += 2 * Math.PI;
        return a - Math.PI;
    }

    /** Turns from one angle toward another by at most {@code limit} radians, the short way round. */
    public static double turn(double from, double to, double limit) {
        double d = wrap(to - from);
        return from + Math.clamp(d, -limit, limit);
    }
}
