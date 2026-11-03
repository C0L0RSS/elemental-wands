package com.anton.elementalwands.client.blockwind;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.X;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.sphereDir;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.yawAxes;

import net.minecraft.util.math.Vec3d;

/**
 * Shared block-wind primitives, ported from the approved browser study (art/wind/blockwind/kit.js). Every function
 * is a pure function of the mesh clock, its arguments and a seed, so a spell looks the same on every client.
 *
 * <p>Look rules: air is see-through and fades by shrinking and thinning; only blade rims, wings and motes are solid.
 * Fast air is stretched streaks, slow air is puffy clusters. Thin streaks read as wires, loose solid cubes as debris,
 * and straight lines meeting at angles as lightning, so trails stay puffy or curved.</p>
 */
public final class BlockWindKit {
    /** Atlas rows in textures/misc/blockwind.png. */
    public static final int GALE = 0, CLOUD = 1, MIST = 2, STREAK = 3, SWIRL = 4, FEATHER = 5, PEARL = 6, SILVER = 7;
    /** How much each band ignores the world's light (1 = full-bright). */
    private static final double[] GLOW = {1, .55, .45, .8, .7, .5, .35, .25};
    private static final double FPS = 8;
    /** Default opacity of see-through air cubes. */
    static final double AIR_ALPHA = .62;
    private BlockWindKit() {}

    static double easeOut(double k) { k = clamp(k); return 1 - (1 - k) * (1 - k); }

    /** Basis whose first axis runs along dir (texture u follows the flow on the long faces); second leans toward up. */
    static Vec3d[] flowAxes(Vec3d dir, Vec3d up) {
        Vec3d x = dir.normalize(), ref = up.subtract(x.multiply(up.dotProduct(x)));
        if (ref.lengthSquared() < 1e-6) ref = Math.abs(x.x) < .9 ? X : new Vec3d(0, 0, 1);
        Vec3d y = ref.subtract(x.multiply(ref.dotProduct(x))).normalize();
        return new Vec3d[]{x, y, x.crossProduct(y)};
    }
    static Vec3d[] flowAxes(Vec3d dir) { return flowAxes(dir, UP); }
    /** The horizontal right-hand side of a direction (Minecraft's +z when facing +x). */
    static Vec3d side(Vec3d dir) {
        Vec3d s = new Vec3d(-dir.z, 0, dir.x);
        return s.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : s.normalize();
    }

    // ---- Cubes ---------------------------------------------------------------------------------------

    /** One cube with full edge lengths along axes; alpha below 1 makes it a see-through air cube. */
    static void cube(BlockWindMesh m, Vec3d p, double sx, double sy, double sz, int band, double seed, Vec3d[] axes, double alpha) {
        if (alpha <= .02) return;
        int frame = Math.floorMod((int) Math.floor(m.time * FPS + rnd(seed + .77) * 4), 4);
        m.box(p, sx * .5, sy * .5, sz * .5, axes, band, frame, GLOW[band], Math.min(1, alpha), alpha < 1);
    }
    static void cube(BlockWindMesh m, Vec3d p, double size, int band, double seed, Vec3d[] axes, double alpha) {
        cube(m, p, size, size, size, band, seed, axes, alpha);
    }

    /** A speed line: one long box from a to b; up orients its flat side. */
    static void streak(BlockWindMesh m, Vec3d a, Vec3d b, double width, int band, double alpha, double seed, Vec3d up) {
        Vec3d d = b.subtract(a);
        double l = d.length();
        if (l < 1e-3 || width <= 1e-3) return;
        cube(m, a.add(b).multiply(.5), l, width, width, band, seed, flowAxes(d, up), alpha);
    }
    static void streak(BlockWindMesh m, Vec3d a, Vec3d b, double width, int band, double alpha, double seed) {
        streak(m, a, b, width, band, alpha, seed, UP);
    }

    /**
     * A puffy cloud cluster that blooms, drifts and thins away over duration: a core cube and lobes that spread as it
     * ages. Lower lobes use the shadowed mist band, like the underside of a real cloud.
     */
    static void puff(BlockWindMesh m, Vec3d center, double age, double size, double seed, double duration, Vec3d drift,
            double spread, int lobes, double alpha, int band) {
        double k = age / duration;
        if (k < 0 || k >= 1) return;
        double grow = .45 + .55 * easeOut(k / .3), fade = 1 - smoothstep((k - .5) / .5), s = size * grow * (.35 + .65 * fade);
        Vec3d c = center.add(drift.multiply(easeOut(k)));
        Vec3d[] axes = yawAxes(rnd(seed) * TAU + k * .8);
        double a = alpha * (.55 + .45 * fade);
        cube(m, c, s, band, seed, axes, a);
        for (int i = 0; i < lobes; i++) {
            Vec3d d = sphereDir(i + (int) Math.floor(rnd(seed + i) * 3), lobes + 3);
            cube(m, c.add(d.multiply(s * (.42 + spread * k))), s * (.5 + .25 * rnd(seed + i * 1.7)), d.y < -.15 ? MIST : band,
                    seed + i + 1, axes, a);
        }
    }
    static void puff(BlockWindMesh m, Vec3d center, double age, double size, double seed, double duration, Vec3d drift, int lobes, double alpha) {
        puff(m, center, age, size, seed, duration, drift, .6, lobes, alpha, CLOUD);
    }

    /**
     * A stepped crescent air blade flying along fwd that faces the caster and the target: the arc spans
     * side = fwd × up, bulges toward up and sweeps its tips back, so from the side it reads as a swept bow. The convex
     * rim is a thin solid gale strip with a wider see-through body inside. k (0..1) dissolves it from the tips inward;
     * echo drops the solid rim, for the fainter copies that trail a fast blade.
     */
    static void blade(BlockWindMesh m, Vec3d center, Vec3d fwd, Vec3d up, double radius, double width, double depth,
            double seed, double k, double alpha, boolean echo) {
        final double sweep = 2.3, bend = .55, back = .7;
        final int segments = 13;
        Vec3d f = fwd.normalize(), u = up.subtract(f.multiply(up.dotProduct(f))).normalize(), side = f.crossProduct(u);
        double step = sweep / segments;
        for (int i = 0; i < segments; i++) {
            double v = (i + .5) / segments * 2 - 1, a = v * sweep / 2;
            double own = clamp(k * 1.6 - (1 - Math.abs(v)) * .6), keep = 1 - smoothstep(own);
            if (keep <= .02) continue;
            double w = width * (.25 + .75 * Math.pow(Math.cos(v * Math.PI / 2), .7)) * keep;
            Vec3d p = arc(center, side, u, f, radius, bend, back, a);
            Vec3d p0 = arc(center, side, u, f, radius, bend, back, a - step / 2), p1 = arc(center, side, u, f, radius, bend, back, a + step / 2);
            Vec3d tangent = p1.subtract(p0).normalize();
            Vec3d outward = side.multiply(Math.sin(a)).add(u.multiply(Math.cos(a) * bend)).normalize();
            Vec3d radial = outward.subtract(tangent.multiply(outward.dotProduct(tangent))).normalize(), thin = radial.crossProduct(tangent);
            double seg = p1.distanceTo(p0) * 1.1;
            // Right-handed (tangent × thin = radial): a mirrored basis turns every face inside out, and in game
            // inside-out cubes take the dim underside lighting.
            Vec3d[] axes = {tangent, thin, radial};
            Vec3d drift = f.multiply(-own * .6).add(radial.multiply(own * .15));
            if (!echo) cube(m, p.add(radial.multiply(-w * .12)).add(drift), seg, depth * keep, w * .26, GALE, seed + i, axes, 1);
            cube(m, p.add(radial.multiply(-w * .62)).add(drift), seg * .96, depth * .75 * keep, w * .74, i % 3 == 1 ? STREAK : CLOUD,
                    seed + i + 40, axes, alpha * keep);
        }
    }
    private static Vec3d arc(Vec3d c, Vec3d side, Vec3d u, Vec3d f, double r, double bend, double back, double a) {
        double fall = (Math.cos(a) - 1) * r;
        return c.add(side.multiply(Math.sin(a) * r)).add(u.multiply(fall * bend)).add(f.multiply(fall * back));
    }

    /** count cubes on a circle around normal, each stretched along the tangent; phase turns the ring. */
    static void ring(BlockWindMesh m, Vec3d center, Vec3d normal, double radius, int count, double size, int band, double alpha,
            double phase, double seed) {
        if (radius <= 1e-3 || size <= 1e-3) return;
        Vec3d n = normal.normalize(), a0 = n.crossProduct(Math.abs(n.y) > .9 ? X : UP).normalize(), b0 = n.crossProduct(a0);
        double arcLen = TAU * radius / count;
        for (int i = 0; i < count; i++) {
            double a = phase + TAU * i / count;
            Vec3d radial = a0.multiply(Math.cos(a)).add(b0.multiply(Math.sin(a))), tangent = n.crossProduct(radial);
            cube(m, center.add(radial.multiply(radius)), Math.min(arcLen * .85, size * 1.4 * 2.2), size, size, band, seed + i,
                    new Vec3d[]{tangent, n, radial}, alpha);
        }
    }

    /** A small curled wisp: a few cubes stepping along a tightening curl, shrinking toward the tail. */
    static void wisp(BlockWindMesh m, Vec3d p, Vec3d dir, double age, double size, double seed, double duration, double curl,
            double length, double alpha, Vec3d up) {
        double k = age / duration;
        if (k < 0 || k >= 1) return;
        Vec3d x = dir.normalize(), y0 = up.subtract(x.multiply(up.dotProduct(x)));
        if (y0.lengthSquared() < 1e-6) return;
        Vec3d y = y0.normalize().multiply(rnd(seed) < .5 ? -1 : 1);
        double fade = 1 - smoothstep((k - .35) / .65);
        Vec3d head = p.add(x.multiply(length * easeOut(k)));
        for (int i = 0; i < 4; i++) {
            double a = i / 3.0 * curl, r = length * .35 * (1 - i * .18), s = size * (1 - i * .2) * fade;
            if (s <= .01) continue;
            Vec3d q = head.add(x.multiply(-Math.sin(a) * r)).add(y.multiply((1 - Math.cos(a)) * r));
            cube(m, q, s * 1.6, s, s, i == 0 ? GALE : CLOUD, seed + i, flowAxes(x.multiply(Math.cos(a)).add(y.multiply(Math.sin(a))), up),
                    alpha * (1 - i * .15));
        }
    }
    static void wisp(BlockWindMesh m, Vec3d p, Vec3d dir, double age, double size, double seed, double duration, double length) {
        wisp(m, p, dir, age, size, seed, duration, 2.2, length, .7, UP);
    }

    /** Tiny solid motes drifting out (reduced particles remove them). */
    static void motes(BlockWindMesh m, Vec3d origin, double age, int count, double spread, double speed, double duration, double seed, Vec3d dir) {
        double k = age / duration;
        if (k < 0 || k >= 1 || !m.embers) return;
        for (int i = 0; i < count; i++) {
            Vec3d d = dir == null ? sphereDir(i, count) : dir.normalize().add(sphereDir(i, count).multiply(.6)).normalize();
            Vec3d p = origin.add(sphereDir(i + 3, count + 5).multiply(spread * .3)).add(d.multiply(speed * duration * easeOut(k) * (.6 + .4 * rnd(seed + i))));
            cube(m, p, .05 * (1 - k), GALE, seed + i, com.anton.elementalwands.client.blockfire.BlockFireKit.AXES, 1);
        }
    }

    /** A rising helix of streak boxes; k (0..1) unwinds it from the bottom. */
    static void spiral(BlockWindMesh m, Vec3d base, double radius, double height, double turns, int count, double width, double phase,
            double k, double seed, double alpha) {
        for (int i = 0; i < count; i++) {
            double v = i / (double) count, own = clamp(k * 1.5 - (1 - v) * .5), keep = 1 - smoothstep(own);
            if (keep <= .02) continue;
            double a = phase + v * turns * TAU, a2 = phase + (v + 1.0 / count) * turns * TAU, r = radius * (1 + own * .6);
            Vec3d p = base.add(Math.cos(a) * r, v * height, Math.sin(a) * r), q = base.add(Math.cos(a2) * r, (v + 1.0 / count) * height, Math.sin(a2) * r);
            boolean bright = i % 4 == 0;
            streak(m, p, q, width * keep, bright ? GALE : STREAK, bright ? 1 : alpha * keep, seed + i);
        }
    }

    /**
     * 3D feathered wings from a back point: a short gale-white leading bone and layered feather slabs that fan out
     * toward the tip, with smaller cloud coverts on top. axes are the body's forward, up and right; spread 0 folds the
     * wings back along the body and 1 opens them wide.
     */
    static void wings(BlockWindMesh m, Vec3d back, Vec3d[] axes, double spread, double flap, double seed) {
        Vec3d f = axes[0], u = axes[1], r = axes[2];
        final double bone = 1.35;
        final int feathers = 8;
        for (int side = -1; side <= 1; side += 2) {
            Vec3d root = back.add(r.multiply(side * .16));
            Vec3d boneDir = r.multiply(side * (.35 + .65 * spread)).add(u.multiply(.35 + flap * .25)).add(f.multiply(-(.75 - .55 * spread))).normalize();
            for (int i = 0; i < 4; i++)
                cube(m, root.add(boneDir.multiply(bone * (i + .5) / 4)), bone / 4 * 1.05, .09, .1, GALE, seed + side * 9 + i, flowAxes(boneDir, u), 1);
            for (int i = 0; i < feathers; i++) {
                double v = (i + .5) / feathers, len = .45 + .75 * v * v;
                Vec3d base = root.add(boneDir.multiply(bone * v));
                Vec3d fd = f.multiply(-.9).add(u.multiply(-.55 + .25 * spread)).add(boneDir.multiply(v * v * .9)).normalize();
                Vec3d[] fa = flowAxes(fd, boneDir.crossProduct(fd));
                cube(m, base.add(fd.multiply(len / 2)), len, .04, .17, FEATHER, seed + side * 31 + i, fa, 1);
                cube(m, base.add(fd.multiply(len * .24)).add(fa[1].multiply(.035 * side)), len * .5, .035, .15, CLOUD, seed + side * 51 + i, fa, 1);
            }
        }
    }
}
