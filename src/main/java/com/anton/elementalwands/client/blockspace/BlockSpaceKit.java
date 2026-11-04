package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.X;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.basis;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.fract;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rotate;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.sphereDir;

import net.minecraft.util.math.Vec3d;

/**
 * Shared void-glass primitives, ported from the approved browser study (art/space/blockspace/kit.js). Every function is
 * a pure function of the mesh clock, its arguments and a seed, so a spell looks the same on every client.
 *
 * <p>Look rules: void glass is dark see-through glass with a bright frame and stars inside; cores are opaque void
 * ringed by a corona that faces the viewer, so they read as eclipses. Space falls inward: streams accelerate toward a
 * core, stretch along the fall and shrink to nothing on arrival; only bursts move outward, and briefly. Pieces are few
 * and large, and anything that loops restarts while it is invisible.</p>
 */
public final class BlockSpaceKit {
    /** Atlas rows in textures/misc/blockspace.png. */
    public static final int VOID = 0, GLASS = 1, NEBULA = 2, RIM = 3, AMETHYST = 4, BLUE = 5, RED = 6, STARLIGHT = 7;
    /** How much each band ignores the world's light (1 = full-bright); amethyst is lit like a block. */
    private static final double[] GLOW = {1, 1, 1, 1, .55, 1, 1, 1};
    private static final double FPS = 6;
    /** Default extra opacity on glass pieces (the texture already makes the interior see-through). */
    static final double GLASS_ALPHA = .9;
    /** Passed as alpha to use a band's default (glass and nebula see-through, everything else solid). */
    static final double DEFAULT = Double.NaN;
    private BlockSpaceKit() {}

    static double easeOut(double k) { k = clamp(k); return 1 - (1 - k) * (1 - k); }
    static double easeIn(double k) { k = clamp(k); return k * k; }
    private static boolean glassy(int band) { return band == GLASS || band == NEBULA; }

    // ---- Bases ---------------------------------------------------------------------------------------

    /** Basis whose first axis runs along dir (texture u follows the flow on the long faces); second leans toward up. */
    static Vec3d[] flowAxes(Vec3d dir, Vec3d up, double roll) {
        Vec3d x = dir.normalize(), ref = up.subtract(x.multiply(up.dotProduct(x)));
        if (ref.lengthSquared() < 1e-6) ref = Math.abs(x.x) < .9 ? X : new Vec3d(0, 0, 1);
        Vec3d y = ref.subtract(x.multiply(ref.dotProduct(x))).normalize(), z = x.crossProduct(y);
        if (roll != 0) {
            double c = Math.cos(roll), s = Math.sin(roll);
            Vec3d ny = y.multiply(c).add(z.multiply(s)), nz = z.multiply(c).subtract(y.multiply(s));
            y = ny; z = nz;
        }
        return new Vec3d[]{x, y, z};
    }
    /** Right-handed basis whose third axis is normal (a plate's face), first axis level where it can be. */
    static Vec3d[] faceAxes(Vec3d normal, double roll) {
        Vec3d z = normal.normalize(), ref = Math.abs(z.y) > .92 ? X : UP;
        Vec3d x = ref.crossProduct(z).normalize(), y = z.crossProduct(x);
        if (roll != 0) {
            double c = Math.cos(roll), s = Math.sin(roll);
            Vec3d nx = x.multiply(c).add(y.multiply(s)), ny = y.multiply(c).subtract(x.multiply(s));
            x = nx; y = ny;
        }
        return new Vec3d[]{x, y, z};
    }
    /** A tumbling basis: a fixed random orientation per seed, turning at rate (radians per second). */
    static Vec3d[] tumble(double seed, double t, double rate) {
        Vec3d[] b = basis(sphereDir((int) Math.floor(rnd(seed) * 29), 29), rnd(seed + .3) * TAU + t * rate);
        double turn = rnd(seed + .6) * TAU;
        return new Vec3d[]{rotate(b[0], UP, turn), rotate(b[1], UP, turn), rotate(b[2], UP, turn)};
    }
    /** Unit vector from p toward the camera (for pieces that face the viewer, like coronas and the Blink slit). */
    static Vec3d toEye(BlockSpaceMesh m, Vec3d p) {
        Vec3d d = m.camera.subtract(p);
        return d.lengthSquared() < 1e-8 ? UP : d.normalize();
    }

    // ---- Pieces --------------------------------------------------------------------------------------

    /**
     * One void-glass piece: an oriented box with full edge lengths along axes. Glass and nebula are always see-through
     * (sorted, blended); other bands are solid unless alpha is below 1. Pass DEFAULT for the band's usual alpha.
     */
    static void piece(BlockSpaceMesh m, Vec3d p, double sx, double sy, double sz, int band, double seed, Vec3d[] axes, double alpha) {
        boolean glassy = glassy(band);
        double a = Double.isNaN(alpha) ? glassy ? GLASS_ALPHA : 1 : alpha;
        if (a <= .02) return;
        int frame = Math.floorMod((int) Math.floor(m.time * FPS + rnd(seed + .77) * 4), 4);
        m.box(p, sx * .5, sy * .5, sz * .5, axes, band, frame, GLOW[band], Math.min(1, a), glassy || a < 1);
    }
    static void piece(BlockSpaceMesh m, Vec3d p, double size, int band, double seed, Vec3d[] axes) {
        piece(m, p, size, size, size, band, seed, axes, DEFAULT);
    }
    /** A flat plate facing normal: w x h across its face, thick through it. */
    static void plate(BlockSpaceMesh m, Vec3d p, Vec3d normal, double w, double h, double thick, int band, double seed, double roll, double alpha) {
        piece(m, p, w, h, thick, band, seed, faceAxes(normal, roll), alpha);
    }

    /**
     * A void core: a stepped ball of opaque void (three crossing slabs around a cube), so its silhouette is round at any
     * angle. corona scales a broken ring of rim segments around it, facing the camera, so it reads as an eclipse.
     */
    static void core(BlockSpaceMesh m, Vec3d c, double r, double seed, double spin, double corona, double t) {
        if (r <= .01) return;
        Vec3d[] ax = tumble(seed, t, spin * .6);
        piece(m, c, 1.62 * r, VOID, seed, ax);
        for (int i = 0; i < 3; i++) {
            double[] s = {1.22 * r, 1.22 * r, 1.22 * r};
            s[i] = 2 * r;
            piece(m, c, s[0], s[1], s[2], VOID, seed + i + 1, ax, DEFAULT);
        }
        if (corona <= 0) return;
        int count = Math.max(8, (int) Math.round(10 + r * 9));
        ring(m, c, toEye(m, c), r * 1.28, count, Math.max(.035, r * .16) * corona, Math.max(.03, r * .1), 1, RIM, DEFAULT,
                t * spin * .9 + rnd(seed) * TAU, seed + 7, .18, true);
    }
    static void core(BlockSpaceMesh m, Vec3d c, double r, double seed, double spin) { core(m, c, r, seed, spin, 1, m.time); }

    /**
     * count segments on a circle around normal. flat segments lie in the ring's plane (a corona, a field boundary);
     * otherwise they stand up like a band. gaps (0..1) drops that share of segments for a broken orbit.
     */
    static void ring(BlockSpaceMesh m, Vec3d center, Vec3d normal, double radius, int count, double size, double depth, double stretch,
            int band, double alpha, double phase, double seed, double gaps, boolean flat) {
        if (radius <= 1e-3 || size <= 1e-3) return;
        Vec3d n = normal.normalize(), a0 = n.crossProduct(Math.abs(n.y) > .9 ? X : UP).normalize(), b0 = n.crossProduct(a0);
        double arc = TAU * radius / count, d = Double.isNaN(depth) ? size * .6 : depth;
        double along = Math.min(arc * .86, size * stretch * 3 + arc * .4);
        for (int i = 0; i < count; i++) {
            if (gaps > 0 && rnd(seed + i * 3.1) < gaps) continue;
            double a = phase + TAU * i / count;
            Vec3d radial = a0.multiply(Math.cos(a)).add(b0.multiply(Math.sin(a))), tangent = n.crossProduct(radial);
            // Right-handed: tangent x n = radial.
            Vec3d[] axes = {tangent, n, radial};
            Vec3d p = center.add(radial.multiply(radius));
            if (flat) piece(m, p, along, d, size, band, seed + i, axes, alpha);
            else piece(m, p, along, size, d, band, seed + i, axes, alpha);
        }
    }

    /**
     * A continuous inward stream around c: each piece starts at radius, spirals in faster and faster, stretches along its
     * fall and shrinks to nothing at the core, then restarts unseen at the rim. period is one piece's fall time. With a
     * normal the stream lies in a disk (a vortex) whose plates lie flat; with null, pieces fall in from every direction.
     * k (0..1) speeds up and tightens the stream (a collapse). once makes a single fall: t is then the effect's age.
     */
    static void inflow(BlockSpaceMesh m, Vec3d c, double t, int count, double radius, double period, double seed, double size, double spin,
            Vec3d normal, double lift, int band, double alpha, double k, double inner, boolean once) {
        Vec3d n = normal == null ? null : normal.normalize();
        Vec3d a0 = n == null ? null : n.crossProduct(Math.abs(n.y) > .9 ? X : UP).normalize(), b0 = n == null ? null : n.crossProduct(a0);
        for (int i = 0; i < count; i++) {
            double u = once ? (t / period - rnd(seed + i * .71) * .33) * 1.5 : clamp(fract(t / period * (1 + k * 1.5) + rnd(seed + i * .71)));
            if (u <= 0 || u >= 1) continue;
            double grow = once ? smoothstep(u / .08) : smoothstep(u / .14), shrink = 1 - smoothstep((u - .72) / .28);
            double s = size * grow * shrink * (.6 + .4 * rnd(seed + i * 2.3));
            if (s <= .01) continue;
            Vec3d p = fall(c, i, count, u, radius, inner, seed, spin, k, n, a0, b0, lift);
            Vec3d dir = fall(c, i, count, Math.min(1, u + .04), radius, inner, seed, spin, k, n, a0, b0, lift).subtract(p);
            if (dir.lengthSquared() < 1e-10) continue;
            double stretch = 1 + 2.4 * u * u;
            int b = i % 5 == 2 ? NEBULA : band;
            if (n != null) piece(m, p, s * stretch, s * .18 + .02, s * .8, b, seed + i, flowAxes(dir, n, 0), alpha);
            else piece(m, p, s * stretch, s * .8, s * .18 + .02, b, seed + i, flowAxes(dir, UP, rnd(seed + i) * TAU), alpha);
        }
    }
    static void inflow(BlockSpaceMesh m, Vec3d c, double t, int count, double radius, double period, double seed, double size, boolean once) {
        inflow(m, c, t, count, radius, period, seed, size, 2.4, null, 0, GLASS, DEFAULT, 0, .12, once);
    }
    /** Where stream piece i is at u: it lingers near the rim, then rushes in (r falls with u squared). */
    private static Vec3d fall(Vec3d c, int i, int count, double u, double radius, double inner, double seed, double spin, double k,
            Vec3d n, Vec3d a0, Vec3d b0, double lift) {
        double r = radius * (1 - u * u) + inner * (1 - u), ang = rnd(seed + i * 1.31) * TAU + spin * u * u * (1 + k);
        if (n != null) return c.add(a0.multiply(Math.cos(ang) * r)).add(b0.multiply(Math.sin(ang) * r)).add(n.multiply(lift * (1 - u)));
        // From every direction: each piece falls along a curved path (its direction swings around a fixed side axis).
        Vec3d d = sphereDir(i, count), side = d.crossProduct(Math.abs(d.y) > .9 ? X : UP).normalize();
        return c.add(rotate(d, side, ang * .3).multiply(r));
    }

    /** A short outward burst of shards from c: they fly out, slow, tumble and shrink away. dir biases the spray (null = all ways). */
    static void spill(BlockSpaceMesh m, Vec3d c, double age, int count, double speed, double duration, double seed, double size, int band, Vec3d dir) {
        double k = age / duration;
        if (k < 0 || k >= 1) return;
        for (int i = 0; i < count; i++) {
            Vec3d d = dir == null ? sphereDir(i, count) : dir.normalize().multiply(1.2).add(sphereDir(i, count)).normalize();
            double v = speed * (.6 + .5 * rnd(seed + i));
            Vec3d p = c.add(d.multiply(v * duration * easeOut(k) * .5));
            double s = size * (1 - smoothstep((k - .25) / .75)) * (.6 + .5 * rnd(seed + i * 1.9));
            if (s <= .01) continue;
            piece(m, p, s, s, s * .25 + .015, i % 4 == 3 ? RIM : band, seed + i, tumble(seed + i, age, 6), DEFAULT);
        }
    }

    /** Tiny twinkling starlight motes around c (the reduced-particle setting removes them). inward pulls them to c. */
    static void stars(BlockSpaceMesh m, Vec3d c, double age, int count, double spread, double duration, double seed, double size, boolean inward) {
        if (!m.embers) return;
        for (int i = 0; i < count; i++) {
            double u = fract(age / duration + rnd(seed + i * .37));
            Vec3d d = sphereDir(i + (int) Math.floor(rnd(seed + i) * 5), count + 5);
            double r = spread * (inward ? Math.pow(1 - u, 1.3) : u) * (.5 + .5 * rnd(seed + i * 3.1));
            double s = size * (.55 + .45 * Math.sin(age * 9 + i * 2.1)) * smoothstep(u / .15) * (1 - smoothstep((u - .8) / .2));
            if (s <= .008) continue;
            piece(m, c.add(d.multiply(r)), s, STARLIGHT, seed + i, basis(d, Math.PI / 4));
        }
    }

    /** A moving head's position at an earlier time, for wakes. */
    interface Path { Vec3d at(double time); }

    /**
     * A wake pulled after a moving head: pieces are left along the path behind it (one every `every` seconds from
     * `from`), then dragged toward the head's current position, stretching along the pull and shrinking as they catch
     * it. until is when shedding stops.
     */
    static void chase(BlockSpaceMesh m, Path headAt, double t, double from, double until, double every, double duration, double spread,
            double size, double seed, int band) {
        double last = Math.min(t, until);
        Vec3d head = headAt.at(last);
        for (int j = Math.max(0, (int) Math.floor((t - duration - from) / every)); from + j * every <= last; j++) {
            double tb = from + j * every, a = t - tb;
            if (a < 0 || a >= duration) continue;
            double k = a / duration, id = seed + j * 1.7;
            Vec3d birth = headAt.at(tb).add(sphereDir((int) Math.floor(rnd(id) * 23), 23).multiply(.5 * spread + .5 * spread * rnd(id + .4)));
            Vec3d p = birth.add(head.subtract(birth).multiply(easeIn(k) * .92));
            double s = size * smoothstep(k / .12) * (1 - smoothstep((k - .55) / .45));
            if (s <= .01) continue;
            Vec3d dir = head.subtract(p);
            piece(m, p, s * (1 + 1.2 * k), s * .75, s * .2 + .015, j % 4 == 1 ? NEBULA : band, id,
                    dir.lengthSquared() > 1e-8 ? flowAxes(dir, UP, rnd(id) * TAU) : tumble(id, t, 1), DEFAULT);
        }
    }

    /**
     * The standing eclipse slit: a vertical lens of void rows framed by rim edges, turned to face the camera around the
     * vertical axis. open (0..1) widens it from a hairline; height is its full height from base (its bottom centre).
     */
    static void slit(BlockSpaceMesh m, Vec3d base, double height, double width, double open, double seed, boolean halo) {
        if (open <= .01) return;
        Vec3d e = m.camera.subtract(base), flat = new Vec3d(e.x, 0, e.z);
        Vec3d fwd = flat.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flat.normalize(), side = UP.crossProduct(fwd);
        Vec3d[] axes = {side, UP, fwd};
        final int rows = 11;
        double rowH = height / rows;
        for (int i = 0; i < rows; i++) {
            double v = (i + .5) / rows, w = Math.max(.04, width * open * Math.pow(Math.sin(Math.PI * v), .75));
            // Step the outline to sixteenths so the lens edge reads as pixel steps, not a smooth curve.
            double half = Math.round(w * 8) / 16.0 + .03;
            Vec3d p = base.add(0, v * height, 0);
            piece(m, p, half * 2, rowH * 1.02, .08, VOID, seed + i, axes, DEFAULT);
            for (int s = -1; s <= 1; s += 2) {
                piece(m, p.add(side.multiply(s * (half + .035))), .07, rowH * 1.02, .1, RIM, seed + i * 2 + s, axes, DEFAULT);
                if (halo) piece(m, p.add(side.multiply(s * (half + .17))), .16, rowH * .9, .06, GLASS, seed + i * 3 + s, axes, .55 * open);
            }
        }
    }

    /** A sphere of glass plates facing outward (an orb's shell); spin turns it around up; keep thins it. */
    static void shell(BlockSpaceMesh m, Vec3d c, double r, int count, double size, double thick, double seed, double spin, int band, double keep) {
        for (int i = 0; i < count; i++) {
            if (rnd(seed + i * 1.13) > keep) continue;
            Vec3d d = rotate(sphereDir(i, count), UP, m.time * spin + rnd(seed) * TAU);
            double s = size * (.75 + .35 * rnd(seed + i * 2.7));
            plate(m, c.add(d.multiply(r)), d, s, s * (.7 + .3 * rnd(seed + i)), thick, i % 6 == 0 ? GLASS : band, seed + i, rnd(seed + i * .5) * TAU, DEFAULT);
        }
    }

    // ---- Spell pieces shared by several effects ------------------------------------------------------

    /** A Singularity Bolt head: an eclipse core and a fast broken orbit leaning a little toward the flight. */
    static void boltHead(BlockSpaceMesh m, Vec3d p, Vec3d dir, double age, double scale, double seed) {
        core(m, p, .25 * scale * smoothstep(age / .06 + .3), seed, 2.2);
        // From behind (the caster's view) the orbit is a thin ellipse, not a hoop.
        ring(m, p, UP.add(dir.normalize().multiply(.25)), .44 * scale, 8, .065 * scale, .05, 1, RIM, DEFAULT, -m.time * 9, seed + 3, .35, false);
    }

    /** How open a rift slit is age seconds after it appears: snaps open, holds, then pinches shut. */
    static double openness(double age, double open, double hold, double close, double peak) {
        if (age < 0) return 0;
        if (age < open) return peak * smoothstep(age / open);
        return peak * (1 - smoothstep((age - open - hold) / close));
    }

    /**
     * A Blink-style rift at feet, age seconds after it opened. Folding in, the space around it falls into the slit and a
     * ring pulls shut as it closes; unfolding (out), shards spill back out.
     */
    static void rift(BlockSpaceMesh m, Vec3d feet, double age, double seed, boolean out, double hold) {
        if (age < 0 || age > .9) return;
        Vec3d c = feet.add(0, 1.1, 0);
        slit(m, feet, 2.2, .9, openness(age, .07, hold, .32, 1), seed, true);
        if (out) {
            spill(m, c, age, 10, 2.2, .42, seed + 2, .22, GLASS, null);
            stars(m, c, age, 8, 1.2, .6, seed + 4, .06, false);
            return;
        }
        inflow(m, c, age, 14, 1.6, .38, seed + 2, .24, true);
        double close = (age - .07 - hold) / .32;
        if (close > 0 && close < 1)
            ring(m, c, toEye(m, c), lerp(1.1, .05, smoothstep(close)), 12, .07, DEFAULT, 1, RIM, DEFAULT, age * 4, seed + 4, 0, true);
        stars(m, c, age, 8, 1.3, .5, seed + 6, .06, true);
    }

    /** A magical poof: void glass pulled into a point at c, a ring snapping shut and a last pinpoint of starlight. */
    static void poof(BlockSpaceMesh m, Vec3d c, double age, double seed, double radius) {
        if (age < 0 || age > .7) return;
        inflow(m, c, age, 16, radius, .4, seed, .22, true);
        double k = age / .3;
        if (k < 1) ring(m, c, toEye(m, c), lerp(radius, .05, smoothstep(k)), 12, .07, DEFAULT, 1, RIM, DEFAULT, age * 5, seed + 3, 0, true);
        pinpoint(m, c, .24, (age - .32) / .14, seed);
        stars(m, c, age, 10, radius * 1.2, .5, seed + 5, .06, true);
    }
    /** A starlight pinpoint shrinking away as k runs from 0 to 1. */
    static void pinpoint(BlockSpaceMesh m, Vec3d c, double size, double k, double seed) {
        if (k > 0 && k < 1) piece(m, c, size * (1 - k), STARLIGHT, seed, basis(UP, Math.PI / 4));
    }

    /** A burning star for Hollow Purple's charge: a stepped ball of one energy band around a starlight heart. */
    static void star(BlockSpaceMesh m, Vec3d c, double r, int band, double seed) {
        if (r <= .01) return;
        Vec3d[] ax = tumble(seed, m.time, 2.5);
        piece(m, c, 1.6 * r, band, seed, ax);
        for (int i = 0; i < 3; i++) {
            double[] s = {1.2 * r, 1.2 * r, 1.2 * r};
            s[i] = 2 * r;
            piece(m, c, s[0], s[1], s[2], band, seed + i + 1, ax, DEFAULT);
        }
        piece(m, c, .9 * r, STARLIGHT, seed + 5, tumble(seed + 5, m.time, -3));
    }

    /**
     * A glass silhouette of a standing player at feet facing fwd: legs, body, arms and head as void-glass boxes a little
     * larger than the body. build (0..1) assembles it from the feet up (or, run backwards, dissolves it from the head down).
     */
    static void ghost(BlockSpaceMesh m, Vec3d feet, Vec3d fwd, double build, double alpha, double seed) {
        Vec3d flat = new Vec3d(fwd.x, 0, fwd.z);
        Vec3d f = flat.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : flat.normalize(), r = f.crossProduct(UP);
        Vec3d[] axes = {f, UP, r};
        double[][] parts = {{.375, -.125, .25, .75, .25}, {.375, .125, .25, .75, .25}, {1.125, 0, .25, .75, .5},
                {1.125, -.375, .25, .75, .25}, {1.125, .375, .25, .75, .25}, {1.75, 0, .5, .5, .5}};
        for (int i = 0; i < parts.length; i++) {
            double[] q = parts[i];
            double g = build >= 1 ? 1 : smoothstep(build * 2.2 - q[0] / 1.9);
            if (g <= .01) continue;
            piece(m, feet.add(0, q[0], 0).add(r.multiply(q[1])), q[2] * g * 1.12, q[3] * g * 1.12, q[4] * g * 1.12, GLASS, seed + i, axes, alpha);
        }
    }

    // ---- Models --------------------------------------------------------------------------------------

    /** The Astral Double's orb: an amethyst crystal (two crossed, stepped prisms) inside a tumbling void-glass cube. */
    static void astralOrb(BlockSpaceMesh m, Vec3d p, double seed) {
        Vec3d[] ax = tumble(seed, m.time, 3), ax2 = tumble(seed + 5, m.time, -2.2);
        piece(m, p, .12, .3, .12, AMETHYST, seed, ax, DEFAULT);
        piece(m, p, .24, .12, .12, AMETHYST, seed + 1, ax, DEFAULT);
        piece(m, p, .08, STARLIGHT, seed + 2, ax);
        piece(m, p, .3, .3, .3, GLASS, seed + 3, ax2, .8);
    }

    /**
     * The Gravity Bomb in void glass: a void cube inside six glass plates that part with each pulse, three rim satellites
     * on a tilted orbit. open (0..1) blows the plates outward for the impact.
     */
    static void gravityBomb(BlockSpaceMesh m, Vec3d p, double seed, double open) {
        double t = m.time;
        Vec3d[] ax = tumble(seed, t, 2.4);
        double pulse = .5 + .5 * Math.sin(t * TAU * 1.6), gap = .2 + .03 * pulse + open * 1.4, fade = 1 - smoothstep((open - .3) / .7);
        core(m, p, .13 * (1 - open * .5), seed, 2, 0, t);
        piece(m, p, .11, RIM, seed + 9, ax);
        if (fade > .01) for (int i = 0; i < 3; i++) for (int s = -1; s <= 1; s += 2) {
            Vec3d n = ax[i].multiply(s);
            plate(m, p.add(n.multiply(gap)), n, .36 * fade, .36 * fade, .05, GLASS, seed + i * 2 + s, i * .7, DEFAULT);
        }
        ring(m, p, ax[1].add(ax[0].multiply(.5)), .42 * (1 + open), 3, .06, DEFAULT, 1, RIM, DEFAULT, t * 5, seed + 20, 0, false);
    }
}
