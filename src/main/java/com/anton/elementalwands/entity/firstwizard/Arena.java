package com.anton.elementalwands.entity.firstwizard;

import net.minecraft.util.math.Vec3d;

/**
 * The flat round floor the copy fights on: its centre at standing height and its radius. It keeps itself, and the
 * middle of everything it casts, inside. Pure geometry, so the contract check can use it.
 *
 * @param centre the floor's centre, at the height players stand (the top of the centre block)
 * @param radius every cell within this of the centre is floor
 */
public record Arena(Vec3d centre, double radius) {
    /** Standing height on the floor. */
    public double floor() { return centre.y; }

    /** Horizontal distance from the centre. */
    public double fromCentre(Vec3d point) { return Math.hypot(point.x - centre.x, point.z - centre.z); }

    /** Whether a point is over the floor with this much to spare from its edge. */
    public boolean inside(Vec3d point, double margin) { return fromCentre(point) <= radius - margin; }

    /** The point moved straight in toward the centre until it is this far inside the edge; height kept. */
    public Vec3d clamp(Vec3d point, double margin) {
        double out = fromCentre(point), limit = Math.max(0, radius - margin);
        if (out <= limit || out < 1e-6) return point;
        double k = limit / out;
        return new Vec3d(centre.x + (point.x - centre.x) * k, point.y, centre.z + (point.z - centre.z) * k);
    }

    /** The same, set down on the floor. */
    public Vec3d onFloor(Vec3d point, double margin) {
        Vec3d in = clamp(point, margin);
        return new Vec3d(in.x, floor(), in.z);
    }

    /**
     * Where a straight run from {@code from} toward {@code to} first leaves the floor's margin, as a fraction of the
     * run (1 when it never does). Used to stop the copy's own steps and anything it throws at the edge.
     */
    public double exit(Vec3d from, Vec3d to, double margin) {
        double r = Math.max(0, radius - margin);
        double fx = from.x - centre.x, fz = from.z - centre.z, dx = to.x - from.x, dz = to.z - from.z;
        double a = dx * dx + dz * dz;
        if (a < 1e-12) return 1;
        double b = 2 * (fx * dx + fz * dz), c = fx * fx + fz * fz - r * r;
        if (c > 0) return 0;        // already outside
        double t = (-b + Math.sqrt(Math.max(0, b * b - 4 * a * c))) / (2 * a);
        return Math.clamp(t, 0, 1);
    }
}
