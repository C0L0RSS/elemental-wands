package com.anton.elementalwands.entity.roc;

import net.minecraft.util.math.Vec3d;

/**
 * Its way onto a spire, as a bird its size flies it: not straight at the spire, turning its body
 * round as it slides in, but out to one side of it, then round in one banked turn of APPROACH_RADIUS
 * and in along a straight run-in of RUN_IN, so it comes down already facing the summit. Of the two
 * ways round it takes the shorter. The turn rolls in and out over ROLL blocks rather than starting
 * and stopping at once. Only the way across is here; the flight sets its height.
 */
final class RocApproach {
    static final double APPROACH_RADIUS = 20, RUN_IN = 20;
    private static final double STEP = .5, ROLL = 6;
    private final double[] x, z, heading;
    private final double step, length;

    private RocApproach(double[] x, double[] z, double[] heading, double step) {
        this.x = x;
        this.z = z;
        this.heading = heading;
        this.step = step;
        length = step * (x.length - 1);
    }

    /** Its way from where it stands onto the spire, facing the centre; null if it is too close to the turn to take it. */
    static RocApproach onto(Vec3d from, Vec3d spire, Vec3d centre) {
        double nx = centre.x - spire.x, nz = centre.z - spire.z, n = Math.hypot(nx, nz);
        if (n < 1e-6) return null;
        nx /= n;
        nz /= n;
        // Where its straight run-in begins, out beyond the spire.
        double ex = spire.x - nx * RUN_IN, ez = spire.z - nz * RUN_IN;
        double[] best = null;
        for (int side : new int[] {1, -1}) {
            double[] way = turn(from.x, from.z, ex, ez, nx, nz, side);
            if (way != null && (best == null || way[0] < best[0])) best = way;
        }
        if (best == null) return null;
        double straight = best[1], arc = best[2], start = best[3], side = best[4], total = straight + arc + RUN_IN;
        int count = (int) Math.ceil(total / STEP);
        double step = total / count;
        // Its heading every step along the way: held, turned at a steady rate, held again.
        double[] heading = new double[count + 1];
        for (int i = 0; i <= count; i++) {
            double s = i * step;
            heading[i] = start + side * Math.clamp(s - straight, 0, arc) / APPROACH_RADIUS;
        }
        // Rolled into and out of the turn: three passes of a moving average.
        int half = (int) Math.round(ROLL / step);
        for (int pass = 0; pass < 3; pass++) {
            double[] smoothed = new double[count + 1];
            for (int i = 0; i <= count; i++) {
                double sum = 0;
                for (int k = -half; k <= half; k++) sum += heading[Math.clamp(i + k, 0, count)];
                smoothed[i] = sum / (2 * half + 1);
            }
            heading = smoothed;
        }
        double[] x = new double[count + 1], z = new double[count + 1];
        x[0] = from.x;
        z[0] = from.z;
        for (int i = 1; i <= count; i++) {
            x[i] = x[i - 1] + Math.cos(heading[i - 1]) * step;
            z[i] = z[i - 1] + Math.sin(heading[i - 1]) * step;
        }
        // Rounding the turn moved its end a little: spread the difference along the way.
        double dx = spire.x - x[count], dz = spire.z - z[count];
        for (int i = 0; i <= count; i++) {
            x[i] += dx * i / count;
            z[i] += dz * i / count;
        }
        return new RocApproach(x, z, heading, step);
    }

    /**
     * The way from (ax, az) into a turn of APPROACH_RADIUS toward side (1 turns from +x toward +z)
     * that ends at (ex, ez) heading (nx, nz): its length, the straight part's, the turn's, the
     * heading it sets out on, and the side. Null if it starts inside the turn.
     */
    private static double[] turn(double ax, double az, double ex, double ez, double nx, double nz, int side) {
        double r = APPROACH_RADIUS;
        // The turn's centre, to that side of the run-in.
        double cx = ex - side * nz * r, cz = ez + side * nx * r;
        double dx = ax - cx, dz = az - cz, d = Math.hypot(dx, dz);
        if (d <= r * 1.05) return null;
        double a = Math.acos(r / d), base = Math.atan2(dz, dx);
        for (int s : new int[] {1, -1}) {
            double tx = cx + r * Math.cos(base + s * a), tz = cz + r * Math.sin(base + s * a);
            double hx = tx - ax, hz = tz - az, straight = Math.hypot(hx, hz);
            // Going this way round the turn, it must meet the turn heading along its straight.
            double mx = -side * (tz - cz) / r, mz = side * (tx - cx) / r;
            if ((mx * hx + mz * hz) / straight < .999) continue;
            double from = Math.atan2(tz - cz, tx - cx), to = Math.atan2(ez - cz, ex - cx);
            double arc = r * (((to - from) * side) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            return new double[] {straight + arc, straight, arc, Math.atan2(hz, hx), side};
        }
        return null;
    }

    double length() { return length; }

    /** Where it is this far along, at no height. */
    Vec3d at(double distance) {
        double k = Math.clamp(distance / step, 0, x.length - 1);
        int i = Math.min((int) k, x.length - 2);
        double f = k - i;
        return new Vec3d(x[i] + (x[i + 1] - x[i]) * f, 0, z[i] + (z[i + 1] - z[i]) * f);
    }

    /** The yaw it flies at this far along. */
    float yaw(double distance) {
        double k = Math.clamp(distance / step, 0, heading.length - 1);
        int i = Math.min((int) k, heading.length - 2);
        double h = heading[i] + (heading[i + 1] - heading[i]) * (k - i);
        return (float) Math.toDegrees(Math.atan2(-Math.cos(h), Math.sin(h)));
    }
}
