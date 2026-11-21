package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import net.minecraft.util.math.Vec3d;

/**
 * A tentacle's attack pose as a smooth curve: a centripetal Catmull-Rom curve through a few hand-placed points from the
 * wound to the tip, sampled at even spacing along its length (so it tapers evenly, as the rest pose does), then brought
 * to life the same way the rest pose is: waves travel along it from the root, and a straining one shudders. A tip
 * planted on the floor stays where it is: the waves and the shudder die away toward it.
 */
final class TendrilCurve {
    private TendrilCurve() {}

    /** Curve samples taken before spacing them evenly. */
    private static final int M = 180;

    /** n + 1 points spaced evenly along a smooth curve through the control points (world). */
    static Vec3d[] through(Vec3d[] c, int n) { return sample(c, 0, 1, n); }

    /**
     * The same curve in parts, each part's points spaced evenly along it on its own: the part from control point at[j]
     * to at[j + 1] gets rings[j] lengths. A part that holds still in the world (an arm lying on the floor) so keeps its
     * points where they are while another part (the arch above it) stretches and heaves.
     */
    static Vec3d[] through(Vec3d[] c, int[] at, int[] rings) {
        int n = 0;
        for (int r : rings) n += r;
        Vec3d[] p = new Vec3d[n + 1];
        double segs = c.length - 1;
        for (int j = 0, k = 0; j < rings.length; k += rings[j], j++)
            System.arraycopy(sample(c, at[j] / segs, at[j + 1] / segs, rings[j]), 0, p, k, rings[j] + 1);
        return p;
    }

    /** n + 1 points spaced evenly along the curve between x0 and x1 (0 to 1 over all of it). */
    private static Vec3d[] sample(Vec3d[] c, double x0, double x1, int n) {
        Vec3d[] raw = new Vec3d[M + 1];
        double[] along = new double[M + 1];
        for (int k = 0; k <= M; k++) {
            raw[k] = at(c, x0 + (x1 - x0) * k / M);
            if (k > 0) along[k] = along[k - 1] + raw[k].distanceTo(raw[k - 1]);
        }
        Vec3d[] p = new Vec3d[n + 1];
        for (int k = 0, j = 0; k <= n; k++) {
            double want = along[M] * k / n;
            while (j < M - 1 && along[j + 1] < want) j++;
            double u = Math.clamp((want - along[j]) / Math.max(1e-9, along[j + 1] - along[j]), 0, 1);
            p[k] = raw[j].lerp(raw[j + 1], u);
        }
        return p;
    }

    /**
     * Brings a pose to life, in place: two travelling waves across it (amp blocks at most, phase its crawl clock),
     * and a shudder (blocks at most) that runs as a standing wave between the root and the tip. pinned 0 to 1 holds
     * the tip still (planted on the floor). seed keeps the three out of step.
     */
    static void live(Vec3d[] p, TendrilFx.Frame fr, double amp, double phase, double shudder, double pinned, double t, double seed) {
        live(p, fr, amp, phase, shudder, pinned, t, seed, .55, .45);
    }

    /** As above, its pinned part beginning pinFrom along it (0 to 1) and wholly still pinOver further on. */
    static void live(Vec3d[] p, TendrilFx.Frame fr, double amp, double phase, double shudder, double pinned, double t, double seed, double pinFrom, double pinOver) {
        int n = p.length - 1;
        Vec3d[] was = p.clone();
        for (int k = 0; k <= n; k++) {
            double s = k / (double) n;
            Vec3d tan = was[Math.min(n, k + 1)].subtract(was[Math.max(0, k - 1)]);
            tan = tan.lengthSquared() < 1e-12 ? UP : tan.normalize();
            // Across the tentacle, never flipping: her up and her left with the part along it taken off.
            Vec3d a = UP.subtract(tan.multiply(tan.y)), b = fr.out().subtract(tan.multiply(fr.out().dotProduct(tan)));
            a = a.lengthSquared() < 1e-6 ? fr.fwd() : a.normalize();
            b = b.lengthSquared() < 1e-6 ? fr.fwd() : b.normalize();
            double held = 1 - pinned * smoothstep((s - pinFrom) / pinOver);
            double env = smoothstep((s - .08) / .4) * held;
            double wave = env * amp * Math.sin(TAU * 1.25 * s - phase), wave2 = env * amp * .7 * Math.sin(TAU * .95 * s - phase * .8 + seed);
            // The shudder: a slow tremor (five or six a second), strongest mid-way, so it strains rather than jitters.
            double mid = Math.sin(Math.PI * Math.min(1, s * (1 + .4 * (1 - pinned)))) * held;
            double sh = shudder * mid;
            double q1 = sh * (Math.sin(t * 34 + s * 7 + seed) + .5 * Math.sin(t * 23 - s * 5 + seed * 2));
            double q2 = sh * (Math.sin(t * 29 - s * 6 + seed * 3) + .5 * Math.sin(t * 41 + s * 4));
            p[k] = was[k].add(a.multiply(wave + q1)).add(b.multiply(wave2 + q2));
        }
    }

    /** Keeps every point over the floor, eased onto it (a little over the tentacle's own thickness there). */
    static void overFloor(Vec3d[] p, double floor) {
        int n = p.length - 1;
        for (int k = 0; k <= n; k++) {
            double lo = floor + TendrilRest.girth(k / (double) n) + .03, y = p[k].y;
            double eased = (y + lo + Math.sqrt((y - lo) * (y - lo) + .01)) / 2;
            if (eased != y) p[k] = new Vec3d(p[k].x, eased, p[k].z);
        }
    }

    /**
     * The curve a share w of the way from a to b (the same number of points, from the same root), blended by its bends
     * rather than point by point: its first length turns from a's direction toward b's, each next length bends from
     * the one before by a blend of how much (and which way) it bends in a and in b, measured on a frame carried along
     * each curve without twisting, each length stretches between theirs, and the curve is built out from the root. No
     * part of it so bends more than it does in either pose, and a tentacle sweeps from one pose into a very different
     * one, where the two averaged point by point (or length by length) can fold into a small loop near her or wind a
     * hook further round before opening it. Past 0 or 1 (a spring's overshoot) it carries on point by point.
     */
    static Vec3d[] blend(Vec3d[] a, Vec3d[] b, double w) {
        int n = a.length - 1;
        double k = Math.clamp(w, 0, 1), over = w - k;
        Vec3d[] p;
        if (k <= 0) p = a.clone();
        else if (k >= 1) p = b.clone();
        else {
            Bends ba = bends(a), bb = bends(b);
            p = new Vec3d[n + 1];
            p[0] = a[0].lerp(b[0], k);
            Vec3d t = slerp(ba.first, bb.first, k), nn = normalTo(t);
            for (int j = 1; j <= n; j++) {
                if (j > 1) {
                    double x = ba.x[j] + (bb.x[j] - ba.x[j]) * k, y = ba.y[j] + (bb.y[j] - ba.y[j]) * k, angle = Math.hypot(x, y);
                    if (angle > 1e-12) {
                        Vec3d side = nn.multiply(x / angle).add(t.crossProduct(nn).multiply(y / angle));
                        t = t.multiply(Math.cos(angle)).add(side.multiply(Math.sin(angle))).normalize();
                        Vec3d m = nn.subtract(t.multiply(nn.dotProduct(t)));
                        nn = m.lengthSquared() < 1e-12 ? normalTo(t) : m.normalize();
                    }
                }
                p[j] = p[j - 1].add(t.multiply(ba.len[j] + (bb.len[j] - ba.len[j]) * k));
            }
        }
        if (over != 0) for (int j = 0; j <= n; j++) p[j] = p[j].add(b[j].subtract(a[j]).multiply(over));
        return p;
    }

    /**
     * A curve as its bends: the direction of its first length, each length's length, and how each length after the
     * first bends from the one before it (radians toward the normal, x, and the binormal, y, of a frame carried along
     * the curve without twisting, the frame starting from up).
     */
    private record Bends(Vec3d first, double[] len, double[] x, double[] y) {}

    private static Bends bends(Vec3d[] p) {
        int n = p.length - 1;
        double[] len = new double[n + 1], x = new double[n + 1], y = new double[n + 1];
        Vec3d first = null, t = null, nn = null;
        for (int j = 1; j <= n; j++) {
            Vec3d d = p[j].subtract(p[j - 1]);
            len[j] = d.length();
            Vec3d u = len[j] < 1e-9 ? (t != null ? t : UP) : d.multiply(1 / len[j]);
            if (t == null) { first = t = u; nn = normalTo(u); continue; }
            Vec3d bn = t.crossProduct(nn);
            double cn = u.dotProduct(nn), cb = u.dotProduct(bn), across = Math.hypot(cn, cb), angle = Math.atan2(across, u.dotProduct(t));
            if (across > 1e-12) { x[j] = angle * cn / across; y[j] = angle * cb / across; }
            t = u;
            Vec3d m = nn.subtract(t.multiply(nn.dotProduct(t)));
            nn = m.lengthSquared() < 1e-12 ? normalTo(t) : m.normalize();
        }
        return new Bends(first == null ? UP : first, len, x, y);
    }

    /** A unit vector at right angles to t, as near up as it can be. */
    private static Vec3d normalTo(Vec3d t) {
        Vec3d m = UP.subtract(t.multiply(t.y));
        if (m.lengthSquared() < 1e-8) m = X.subtract(t.multiply(t.x));
        return m.normalize();
    }

    /** The unit vector a share k of the way round from a to b (both unit), the short way. */
    private static Vec3d slerp(Vec3d a, Vec3d b, double k) {
        double dot = Math.clamp(a.dotProduct(b), -1, 1), angle = Math.acos(dot);
        if (angle < 1e-6) return a;
        Vec3d axis = a.crossProduct(b);
        axis = axis.lengthSquared() < 1e-12 ? normalTo(a) : axis.normalize();
        double th = angle * k;
        return a.multiply(Math.cos(th)).add(axis.crossProduct(a).multiply(Math.sin(th))).normalize();
    }

    /** A centripetal Catmull-Rom curve through the points, its ends carried straight on, x 0 to 1. */
    static Vec3d at(Vec3d[] c, double x) {
        int n = c.length, segs = n - 1;
        double t = Math.clamp(x * segs, 0, segs - 1e-9);
        int k = (int) t;
        Vec3d p0 = k > 0 ? c[k - 1] : c[0].multiply(2).subtract(c[1]), p1 = c[k], p2 = c[k + 1];
        Vec3d p3 = k + 2 < n ? c[k + 2] : c[n - 1].multiply(2).subtract(c[n - 2]);
        double t1 = knot(p0, p1), t2 = t1 + knot(p1, p2), t3 = t2 + knot(p2, p3), tt = t1 + (t2 - t1) * (t - k);
        Vec3d a1 = p0.multiply((t1 - tt) / t1).add(p1.multiply(tt / t1));
        Vec3d a2 = p1.multiply((t2 - tt) / (t2 - t1)).add(p2.multiply((tt - t1) / (t2 - t1)));
        Vec3d a3 = p2.multiply((t3 - tt) / (t3 - t2)).add(p3.multiply((tt - t2) / (t3 - t2)));
        Vec3d b1 = a1.multiply((t2 - tt) / t2).add(a2.multiply(tt / t2));
        Vec3d b2 = a2.multiply((t3 - tt) / (t3 - t1)).add(a3.multiply((tt - t1) / (t3 - t1)));
        return b1.multiply((t2 - tt) / (t2 - t1)).add(b2.multiply((tt - t1) / (t2 - t1)));
    }

    private static double knot(Vec3d a, Vec3d b) { return Math.sqrt(Math.max(1e-4, a.distanceTo(b))); }
}
