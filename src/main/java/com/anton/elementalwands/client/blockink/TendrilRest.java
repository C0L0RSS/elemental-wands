package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.math.Vec3d;

/**
 * Phase two's tentacles at rest, the game's own pose (chosen Nov 20 from three designs, after the developer asked for
 * them spread out instead of jumbled, one bent up or down, and a little thinner): the three arms fanned out of her left
 * flank like an octopus's hovering in open water, each in a space of its own, so the silhouette reads as three arms from
 * every side and her body stays readable from the front and her right. They leave the wound together and part at once,
 * one low, one at shoulder height, one high, each some eight to nine blocks long:
 * <ul>
 *   <li><b>0 hangs down</b> in front of her left side, sweeps out low over the floor and lifts its tip a little, curling
 *       it forward and back like a questing snake's head;</li>
 *   <li><b>1 reaches out and back</b> at shoulder height in a long S, its tip curling up behind her;</li>
 *   <li><b>2 rears up</b> from her left shoulder, leaning forward, and hooks forward and in high over her head like a
 *       scorpion's tail, the strike cocked.</li>
 * </ul>
 * Each bends in plan as well as in height, so from no side is a whole tentacle seen end on, and the striker leans forward
 * so that, from behind her left, the shoulder-height one passes beside it rather than over it. (A tentacle seen end on
 * from close by still folds into a coil; the views from her left and behind her left are the busiest.)
 *
 * <p>Each is a smooth curve through hand-placed points, spaced evenly along its length so it tapers evenly. Slow waves run
 * along each from the root to the tip, each at its own rhythm; the points themselves drift a little, more toward the tip;
 * the tips quest, and each outer third slowly curls tighter and opens again like an octopus's arm. hold draws each in
 * along its own line and curls it; bursting out, each starts rolled up and unrolls.
 */
final class TendrilRest implements TendrilPose {
    /** Samples kept along each tentacle, and curve samples taken before spacing them evenly. */
    private static final int N = 128, M = 240;
    /** Radius at the root (with a little swell where it leaves the wound) and at the tip. */
    private static final double ROOT = .18, SWELL = .03, TIP = .016;
    /** The wound in the points' frame (blocks: her left, up from the floor under her as she floats, ahead). */
    private static final double WO = .24, WU = 2.2;
    /**
     * Each tentacle's points at rest (her left, up from the floor, ahead), from the wound to the tip: 0 low in front,
     * 1 out and back at shoulder height, 2 up over her shoulder and hooked forward over her head.
     */
    private static final double[][][] REST = {
        {{.24, 2.2, 0}, {.6, 1.88, .12}, {.92, 1.38, .25}, {1.22, .9, .4}, {1.62, .56, .58}, {2.2, .42, .8}, {2.9, .4, 1.0},
         {3.55, .44, 1.12}, {4.1, .55, 1.14}, {4.55, .75, 1.26}, {4.82, 1.0, 1.5}, {4.85, 1.2, 1.85}, {4.62, 1.32, 2.12},
         {4.28, 1.3, 2.22}, {4.0, 1.18, 2.12}},
        {{.24, 2.2, 0}, {.8, 2.38, -.3}, {1.4, 2.68, -.62}, {2.0, 3.15, -.95}, {2.55, 3.55, -1.22}, {3.08, 3.5, -1.5}, {3.5, 3.08, -1.72},
         {3.9, 2.7, -1.92}, {4.32, 2.64, -2.14}, {4.72, 2.92, -2.34}, {4.92, 3.38, -2.45}, {4.86, 3.86, -2.42}, {4.58, 4.16, -2.28},
         {4.24, 4.18, -2.1}, {4.04, 3.95, -2.0}},
        {{.24, 2.2, 0}, {.62, 2.72, .2}, {.95, 3.25, .48}, {1.18, 3.82, .75}, {1.3, 4.42, .98}, {1.42, 5.0, 1.15}, {1.6, 5.48, 1.38},
         {1.62, 5.78, 1.72}, {1.42, 5.84, 2.08}, {1.1, 5.6, 2.35}, {.8, 5.18, 2.45}, {.6, 4.75, 2.38}, {.52, 4.42, 2.15}, {.6, 4.25, 1.92},
         {.78, 4.28, 1.75}},
    };
    /** The axis each rolls up round (her left, up, ahead): 0 up and in, 1 up, 2 forward over her. */
    private static final double[][] CURL_AXIS = {{0, 0, 1}, {.7071, 0, .7071}, {1, 0, 0}};
    /** The two directions each one's travelling waves swing across (her left, up, ahead). */
    private static final double[][] SWING = {{0, 0, 1}, {0, 1, 0}, {1, 0, 0}}, SWING2 = {{0, 1, 0}, {.7071, 0, .7071}, {0, 0, 1}};
    /** Each one's rhythm (turns a second of its crawl and its questing tip), its wave's size (blocks) and waves along it. */
    static final double[] CRAWL = {.085, .065, .1}, QUEST = {.3, .24, .36};
    private static final double[] WAVE = {.32, .34, .3}, WAVES = {1.3, 1.15, 1.45};
    /** How high each one's questing tip lifts (blocks, at most twice this): the low one least, so it stays low. */
    private static final double[] LIFT = {.18, .25, .32};
    /** How far each one's outer third curls tighter and opens again as its tip quests (radians, either way). */
    private static final double[] BREATHE = {.45, .5, .3};

    /** The last inputs each tentacle was built for, and its samples (world space). */
    private final double[][] key = new double[3][];
    private final double[][] xs = new double[3][N + 1], ys = new double[3][N + 1], zs = new double[3][N + 1];

    @Override
    public Vec3d rest(TendrilFx.Frame fr, int i, double s, double phase, double quest, double hold, double unfurl, double burst) {
        double[] now = {phase, quest, hold, unfurl, burst, fr.wound().x, fr.wound().y, fr.wound().z, fr.body().x, fr.body().y, fr.body().z,
                fr.out().x, fr.out().z, fr.fwd().x, fr.fwd().z, fr.floor()};
        if (!Arrays.equals(now, key[i])) { build(fr, i, phase, quest, hold, unfurl, burst); key[i] = now; }
        double x = Math.clamp(s, 0, 1) * N;
        int k = Math.min(N - 1, (int) x);
        double u = x - k;
        return new Vec3d(xs[i][k] + (xs[i][k + 1] - xs[i][k]) * u, ys[i][k] + (ys[i][k + 1] - ys[i][k]) * u, zs[i][k] + (zs[i][k + 1] - zs[i][k]) * u);
    }

    /** Radius at s, from the root (0) to the tip (1): {@link TendrilPose#radius}, for every pose that keeps the game's. */
    static double girth(double s) { return TIP + (ROOT - TIP) * (1 - s) + SWELL * (1 - smoothstep(s / .1)); }

    /** Builds tentacle i's samples for these inputs. */
    private void build(TendrilFx.Frame fr, int i, double phase, double quest, double hold, double unfurl, double burst) {
        double[][] rest = REST[i];
        int n = rest.length;
        double held = smoothstep(hold), drift = phase * .6;
        // ── Its points, from the wound: drifting a little (more toward the tip), and drawn in while it is held ──
        double[][] c = new double[n][3];
        for (int j = 0; j < n; j++) {
            // The drift varies slowly along it, so neighbouring points move together and never kink it.
            double q = j / (n - 1.0), sway = q * q * .35, in = .55 - .15 * q;
            double o = rest[j][0] - WO + sway * Math.sin(drift + q * 1.6 + i);
            double u = rest[j][1] - WU + sway * .8 * Math.sin(drift * 1.3 + q * 2.1 + 2 * i);
            double f = rest[j][2] + sway * Math.cos(drift * .8 + q * 1.3 + i);
            double k = 1 - (1 - in) * held;
            c[j][0] = o * k; c[j][1] = u * k; c[j][2] = f * k;
        }
        // ── A smooth curve through them, then samples spaced evenly along its length ──
        double[][] raw = new double[M + 1][];
        double[] along = new double[M + 1];
        for (int k = 0; k <= M; k++) {
            raw[k] = curve(c, k / (double) M);
            if (k > 0) along[k] = along[k - 1] + dist(raw[k], raw[k - 1]);
        }
        double[][] p = new double[N + 1][];
        for (int k = 0, j = 0; k <= N; k++) {
            double want = along[M] * k / N;
            while (j < M - 1 && along[j + 1] < want) j++;
            double t = Math.clamp((want - along[j]) / Math.max(1e-9, along[j + 1] - along[j]), 0, 1);
            p[k] = new double[] {raw[j][0] + (raw[j + 1][0] - raw[j][0]) * t, raw[j][1] + (raw[j + 1][1] - raw[j][1]) * t, raw[j][2] + (raw[j + 1][2] - raw[j][2]) * t};
        }
        // A light smoothing, so no bend is ever a corner (the root and the tip stay put).
        for (int pass = 0; pass < 3; pass++) {
            double[][] was = new double[N + 1][];
            for (int k = 0; k <= N; k++) was[k] = p[k].clone();
            for (int k = 1; k < N; k++) for (int a = 0; a < 3; a++) p[k][a] = (was[k - 1][a] + 2 * was[k][a] + was[k + 1][a]) / 4;
        }
        // ── The outer third curling tighter and opening again as the tip quests, round the way it already curls ──
        double[] bend = cross(heading(p, .66), heading(p, .96));
        double bent = Math.sqrt(bend[0] * bend[0] + bend[1] * bend[1] + bend[2] * bend[2]);
        if (bent > 1e-6) {
            double breathe = BREATHE[i] * Math.sin(quest * .45 + 1.3 * i) * (1 - held) * smoothstep((bent - .1) / .2);
            roll(p, new double[] {bend[0] / bent, bend[1] / bent, bend[2] / bent}, s -> breathe * smoothstep((s - .6) / .4));
        }
        // ── Rolled up: still curled from bursting out (and a little when held), each length turned further the nearer the tip ──
        double curl = 1 - Math.min(unfurl, 1.1) + .3 * held;
        if (Math.abs(curl) > 1e-4) roll(p, CURL_AXIS[i], s -> curl * 7 * s * s);
        // ── Into the world, with the travelling waves and the questing tip ──
        Vec3d out = fr.out(), fwd = fr.fwd(), w = fr.wound();
        Vec3d swing = local(out, fwd, SWING[i]), swing2 = local(out, fwd, SWING2[i]);
        double thrash = burst < 60 ? Math.exp(-burst / 10) : 0;
        double amp = WAVE[i] * (1 - .85 * held) * (1 + 2.5 * thrash), whip = burst < 60 ? burst * .7 : 0;
        double reach = 1 - .75 * held;
        Vec3d[] at = new Vec3d[N + 1];
        for (int k = 0; k <= N; k++) at[k] = w.add(local(out, fwd, p[k]));
        for (int k = 0; k <= N; k++) {
            double s = k / (double) N;
            Vec3d tan = at[Math.min(N, k + 1)].subtract(at[Math.max(0, k - 1)]);
            tan = tan.lengthSquared() < 1e-12 ? UP : tan.normalize();
            // Across the tentacle, never flipping: the swing directions with the part along it taken off.
            Vec3d a = swing.subtract(tan.multiply(swing.dotProduct(tan))), b = swing2.subtract(tan.multiply(swing2.dotProduct(tan)));
            double env = smoothstep((s - .1) / .45) * amp;
            double wave = env * Math.sin(TAU * WAVES[i] * s - phase - whip), wave2 = env * .7 * Math.sin(TAU * WAVES[i] * .75 * s - phase * .8 + 1.3 * i - whip);
            Vec3d q = at[k].add(a.multiply(wave)).add(b.multiply(wave2));
            double tip = Math.clamp((s - .78) / .22, 0, 1);
            if (tip > 0) {
                double t2 = tip * tip * reach;
                q = q.add(0, LIFT[i] * t2 * (1 + Math.sin(quest * .7 + i)), 0).add(swing.multiply(.45 * t2 * Math.sin(quest))).add(swing2.multiply(.3 * t2 * Math.cos(quest * .8)));
            }
            // Never below the floor: eased onto a little above it.
            double lo = fr.floor() + .3, y = (q.y + lo + Math.sqrt((q.y - lo) * (q.y - lo) + .0625)) / 2;
            q = TendrilFx.clear(fr, new Vec3d(q.x, y, q.z), s);
            xs[i][k] = q.x; ys[i][k] = q.y; zs[i][k] = q.z;
        }
    }

    /** A point of the points' frame (her left, up, ahead) as a world offset. */
    private static Vec3d local(Vec3d out, Vec3d fwd, double[] v) { return out.multiply(v[0]).add(0, v[1], 0).add(fwd.multiply(v[2])); }

    /** Its direction (unit, in the points' frame) at s along p. */
    private static double[] heading(double[][] p, double s) {
        int k = Math.clamp((int) Math.round(s * (p.length - 1)), 1, p.length - 2);
        double[] d = {p[k + 1][0] - p[k - 1][0], p[k + 1][1] - p[k - 1][1], p[k + 1][2] - p[k - 1][2]};
        double l = Math.max(1e-9, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]));
        return new double[] {d[0] / l, d[1] / l, d[2] / l};
    }

    /** Turns each length of p round axis (her left, up, ahead) by angle(s), s how far along it is, keeping its length. */
    private static void roll(double[][] p, double[] axis, DoubleUnaryOperator angle) {
        double[] from = p[0].clone(), last = p[0].clone();
        for (int k = 1; k < p.length; k++) {
            double s = (k - .5) / (p.length - 1), th = angle.applyAsDouble(s), cos = Math.cos(th), sin = Math.sin(th);
            double[] d = {p[k][0] - from[0], p[k][1] - from[1], p[k][2] - from[2]};
            from = p[k].clone();
            double[] kx = cross(axis, d);
            double dot = axis[0] * d[0] + axis[1] * d[1] + axis[2] * d[2];
            // Rodrigues, in the right-handed (left, up, ahead) frame.
            for (int a = 0; a < 3; a++) last[a] += d[a] * cos + kx[a] * sin + axis[a] * dot * (1 - cos);
            p[k] = last.clone();
        }
    }

    /** A centripetal Catmull-Rom curve through the points, its ends carried straight on, x 0 to 1. */
    private static double[] curve(double[][] c, double x) {
        int n = c.length, segs = n - 1;
        double t = Math.clamp(x * segs, 0, segs - 1e-9);
        int k = (int) t;
        double[] p0 = k > 0 ? c[k - 1] : beyond(c[0], c[1]), p1 = c[k], p2 = c[k + 1], p3 = k + 2 < n ? c[k + 2] : beyond(c[n - 1], c[n - 2]);
        double t1 = knot(p0, p1), t2 = t1 + knot(p1, p2), t3 = t2 + knot(p2, p3), tt = t1 + (t2 - t1) * (t - k);
        double[] r = new double[3];
        for (int a = 0; a < 3; a++) {
            double a1 = ((t1 - tt) * p0[a] + tt * p1[a]) / t1, a2 = ((t2 - tt) * p1[a] + (tt - t1) * p2[a]) / (t2 - t1), a3 = ((t3 - tt) * p2[a] + (tt - t2) * p3[a]) / (t3 - t2);
            double b1 = ((t2 - tt) * a1 + tt * a2) / t2, b2 = ((t3 - tt) * a2 + (tt - t1) * a3) / (t3 - t1);
            r[a] = ((t2 - tt) * b1 + (tt - t1) * b2) / (t2 - t1);
        }
        return r;
    }

    private static double knot(double[] a, double[] b) { return Math.sqrt(Math.max(1e-4, dist(a, b))); }

    private static double[] beyond(double[] end, double[] next) { return new double[] {2 * end[0] - next[0], 2 * end[1] - next[1], 2 * end[2] - next[2]}; }

    private static double dist(double[] a, double[] b) { return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    private static double[] cross(double[] a, double[] b) { return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
}
