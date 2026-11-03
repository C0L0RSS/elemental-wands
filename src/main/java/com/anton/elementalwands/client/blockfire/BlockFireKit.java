package com.anton.elementalwands.client.blockfire;

import net.minecraft.util.math.Vec3d;

/**
 * Shared block-fire primitives, ported from the approved browser study (art/fire/blockfire/kit.js).
 * Every function is a pure function of the mesh clock, its arguments and a seed, so a spell looks the
 * same on every client and every frame without any per-cube state.
 *
 * <p>Look rules: flames flicker by length and width and never whip; pieces that leave a flame shrink to
 * nothing and restart only while hidden inside its base; contrast comes from the atlas bands and the
 * baked face shade. Sparks and smoke are accents and disappear with reduced particles.</p>
 */
public final class BlockFireKit {
    /** Atlas rows in textures/misc/blockfire.png. */
    public static final int CORE = 0, YELLOW = 1, ORANGE = 2, DEEP = 3, EMBER = 4, SMOKE = 5, CHAR = 6, MAGMA = 7;
    /** Hot to cold fire bands; heat index 4 is hottest. */
    private static final int[] HEAT = {EMBER, DEEP, ORANGE, YELLOW, CORE};
    /** Atlas frame rate; each cube offsets its frame by its seed so neighbours never flicker in sync. */
    private static final double FPS = 8;
    /** Set-wide smoke size and opacity: smoke stays a see-through accent beside the fire. */
    static final double SMOKE_SCALE = .7, SMOKE_ALPHA = .5;
    public static final double TAU = Math.PI * 2;
    public static final Vec3d X = new Vec3d(1, 0, 0), UP = new Vec3d(0, 1, 0), Z = new Vec3d(0, 0, 1);
    public static final Vec3d[] AXES = {X, UP, Z};
    private BlockFireKit() {}

    // ---- Numbers -------------------------------------------------------------------------------------

    public static double rnd(double n) { double a = Math.sin(n * 127.1 + 43.7) * 43758.5453; return a - Math.floor(a); }
    /** Smooth 1D value noise in [0, 1] built on the same hash as rnd. */
    public static double noise(double x) { double i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return lerp(rnd(i), rnd(i + 1), u); }
    public static double clamp(double v) { return Math.clamp(v, 0, 1); }
    public static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static double smoothstep(double t) { t = clamp(t); return t * t * (3 - 2 * t); }
    /** The fire band for a heat index (rounded, clamped to 0..4). */
    public static int heat(double i) { return HEAT[(int) Math.round(clamp(i, 0, 4))]; }

    // ---- Bases ---------------------------------------------------------------------------------------

    /** Right-handed basis whose second axis is dir; roll spins it about that axis. */
    public static Vec3d[] basis(Vec3d dir, double roll) {
        Vec3d y = dir.normalize(), ref = Math.abs(y.y) > .92 ? X : UP;
        Vec3d x = y.crossProduct(ref).normalize(), z = x.crossProduct(y);
        if (roll != 0) {
            double c = Math.cos(roll), s = Math.sin(roll);
            Vec3d nx = x.multiply(c).add(z.multiply(s)), nz = z.multiply(c).subtract(x.multiply(s));
            x = nx; z = nz;
        }
        return new Vec3d[]{x, y, z};
    }
    public static Vec3d[] basis(Vec3d dir) { return basis(dir, 0); }
    /** Upright basis turned about the vertical by yaw; axis 0 points along (cos, 0, sin). */
    public static Vec3d[] yawAxes(double yaw) {
        Vec3d f = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        return new Vec3d[]{f, UP, f.crossProduct(UP)};
    }
    /** Rodrigues rotation of v about unit axis k by angle a. */
    public static Vec3d rotate(Vec3d v, Vec3d k, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return v.multiply(c).add(k.crossProduct(v).multiply(s)).add(k.multiply(k.dotProduct(v) * (1 - c)));
    }
    public static Vec3d[] rotate(Vec3d[] axes, Vec3d k, double a) {
        return new Vec3d[]{rotate(axes[0], k, a), rotate(axes[1], k, a), rotate(axes[2], k, a)};
    }
    /** A tumbling basis: spins about a seeded axis at speed radians per second. */
    public static Vec3d[] tumble(double seed, double t, double speed) {
        Vec3d k = new Vec3d(rnd(seed + .1) - .5, rnd(seed + .2) - .5, rnd(seed + .3) - .5).normalize();
        double a = rnd(seed + .4) * TAU + t * speed * (rnd(seed + .5) < .5 ? -1 : 1);
        return rotate(AXES, k, a);
    }
    /** Even directions over a sphere (golden spiral). */
    public static Vec3d sphereDir(int i, int count) {
        double y = 1 - 2 * (i + .5) / count, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963;
        return new Vec3d(Math.cos(a) * r, y, Math.sin(a) * r);
    }
    public static Vec3d v(double x, double y, double z) { return new Vec3d(x, y, z); }

    // ---- Cubes ---------------------------------------------------------------------------------------

    public static void cube(BlockFireMesh m, Vec3d p, double size, int band, double seed, Vec3d[] axes) {
        cube(m, p, size, size, size, band, seed, axes, 1, SMOKE_ALPHA);
    }
    public static void cube(BlockFireMesh m, Vec3d p, double sx, double sy, double sz, int band, double seed, Vec3d[] axes) {
        cube(m, p, sx, sy, sz, band, seed, axes, 1, SMOKE_ALPHA);
    }
    /**
     * One cube with full edge lengths along axes. tint darkens the colour (charred crust); alpha applies only to
     * smoke, which is see-through and evenly shaded. Fire is opaque and fades by shrinking.
     */
    public static void cube(BlockFireMesh m, Vec3d p, double sx, double sy, double sz, int band, double seed, Vec3d[] axes,
            double tint, double alpha) {
        int frame = Math.floorMod((int) Math.floor(m.time * FPS + rnd(seed + .77) * 4), 4);
        boolean see = band == SMOKE;
        m.box(p, sx * .5, sy * .5, sz * .5, axes, band, frame, tint, see ? alpha : 1, see);
    }

    // ---- Flames --------------------------------------------------------------------------------------

    /** Options for {@link #flame}; defaults match kit.js. */
    public static final class Flame {
        double width, length, seed, heat = 1, sway = .08, flicker = 1, speed = 1, t = Double.NaN;
        int count;
        public Flame(double width, double length, double seed) { this.width = width; this.length = length; this.seed = seed; }
        public Flame heat(double v) { heat = v; return this; }
        public Flame sway(double v) { sway = v; return this; }
        public Flame count(int v) { count = v; return this; }
        public Flame flicker(double v) { flicker = v; return this; }
        public Flame speed(double v) { speed = v; return this; }
        public Flame t(double v) { t = v; return this; }
    }

    /**
     * A flame tongue: a flickering hot base of three cubes and a loop of cubes that rise from it along dir,
     * shrink, drift and cool through the heat bands until they vanish at the tip. Each rising cube restarts
     * hidden inside the base. heat 1 starts yellow at the base; about 0.45 is an ember flame.
     */
    public static void flame(BlockFireMesh m, Vec3d root, Vec3d dir, Flame o) {
        if (o.length <= .01 || o.width <= .01) return;
        double t = Double.isNaN(o.t) ? m.time : o.t, seed = o.seed, width = o.width;
        Vec3d d = dir.normalize();
        double roll = rnd(seed + .9) * TAU, top = o.heat * 3;
        Vec3d[] b = basis(d, roll);
        Vec3d bx = b[0], bz = b[2];
        double L = o.length * (1 - .15 * o.flicker + .3 * o.flicker * noise(t * 4.1 + seed * 7.1));
        int n = o.count > 0 ? o.count : (int) clamp(Math.round(L / width * 2.2), 3, 8);
        // A slow lean shared by the whole tongue; it bends more toward the tip but never ripples.
        double leanA = (noise(t * 1.6 + seed * 3.3) - .5) * 2 * o.sway, leanB = (noise(t * 1.9 + seed * 5.9) - .5) * 2 * o.sway;
        double bw = width * (.92 + .16 * noise(t * 6.7 + seed * 2.3));
        Vec3d base = root.add(d.multiply(bw * .36));
        // The base is three overlapping cubes, each flickering on its own, so it never reads as one box.
        for (int j = 0; j < 3; j++) {
            double a = roll + j * TAU / 3, s = bw * (.68 + .16 * noise(t * 6.1 + seed * 2.3 + j * 3.1));
            Vec3d c = base.add(bx.multiply(Math.cos(a) * bw * .17)).add(bz.multiply(Math.sin(a) * bw * .17));
            cube(m, c, s, s * .92, s, heat(top - (j > 0 ? .7 : 0)), seed + j * 9, basis(d, roll + j * .55));
        }
        double period = clamp(.32 + L * .2, .32, 1.1) / o.speed;
        for (int i = 0; i < n; i++) {
            double phase = t / period + i / (double) n + rnd(seed + i * 1.31) * .35 / n, loop = Math.floor(phase), u = phase - loop;
            double k = seed + i * 4.1 + loop * 17.3;
            double w = bw * .74 * Math.pow(1 - u, .8) * (.85 + .3 * rnd(k));
            if (w < .012) continue;
            Vec3d side = bx.multiply((rnd(k + 1.7) - .5) * width * .4 * u + leanA * L * u * u)
                    .add(bz.multiply((rnd(k + 2.9) - .5) * width * .4 * u + leanB * L * u * u));
            Vec3d c = base.add(d.multiply(L * Math.pow(u, .85))).add(side);
            cube(m, c, w, w * 1.15, w, heat(top - u * 2.5), seed + i, basis(d, roll + (rnd(k + 3.3) - .5) * 1.2 + u * .6));
        }
    }

    // ---- Bursts --------------------------------------------------------------------------------------

    /** Options for {@link #burst}; defaults match kit.js. */
    public static final class Burst {
        double radius, size, duration, seed, up = .35, heat = 1;
        int count;
        boolean smoke = true, flash = true;
        public Burst(double radius, int count, double size, double duration, double seed) {
            this.radius = radius; this.count = count; this.size = size; this.duration = duration; this.seed = seed;
        }
        public Burst up(double v) { up = v; return this; }
        public Burst heat(double v) { heat = v; return this; }
        public Burst smoke(boolean v) { smoke = v; return this; }
        public Burst flash(boolean v) { flash = v; return this; }
    }

    /**
     * A ball of fire that blooms and burns out: a bright flash cluster, then tumbling cubes thrown outward
     * that rise, cool and shrink to nothing, then a little smoke. age is seconds since the burst.
     */
    public static void burst(BlockFireMesh m, Vec3d center, double age, Burst o) {
        if (age < 0) return;
        double life = age / o.duration, seed = o.seed, size = o.size;
        if (life > 2.2) return;
        if (o.flash && life < .45) {
            double k = life / .45, s = size * 2.3 * Math.pow(Math.sin(k * Math.PI), .6);
            cube(m, center, s, heat(o.heat * 4 - k * 1.6), seed, tumble(seed, age, 3));
            cube(m, center, s * .86, heat(o.heat * 4 - k * 1.6 - 1), seed + 1, tumble(seed + 1, age, -3));
        }
        for (int i = 0; i < o.count; i++) {
            double delay = rnd(seed + i * 1.7) * .12, l = clamp((life - delay) / (1 - delay));
            if (l <= 0 || l >= 1) continue;
            Vec3d dir = sphereDir(i, o.count).add(0, o.up, 0).normalize();
            double r = o.radius * (1 - Math.pow(1 - l, 2.2)) * (.55 + .45 * rnd(seed + i * 3.3));
            Vec3d p = center.add(dir.multiply(r)).add(0, .5 * l * l, 0);
            double s = size * (.6 + .6 * rnd(seed + i * 4.9)) * Math.min(1, l * 8) * Math.pow(1 - l, 1.1);
            cube(m, p, s, heat(o.heat * 4 - l * 4.2 + rnd(seed + i * 2.3) * .6), seed + i, tumble(seed + i, age, 4));
        }
        if (o.smoke) for (int i = 0; i < Math.ceil(o.count / 5.0); i++) {
            double start = .3 + rnd(seed + i * 8.1) * .2, l = (life - start) / 1.6;
            if (l <= 0 || l >= 1) continue;
            Vec3d dir = sphereDir(i * 3 + 1, o.count).add(0, .8, 0).normalize();
            smoke(m, center.add(dir.multiply(o.radius * .65)).add(0, .2, 0), l * 1.6 * o.duration, size * .9, seed + i + 300,
                    o.radius * .8, 1.6 * o.duration);
        }
    }

    /**
     * A smoke puff that swells, rises and shrinks away, lit by the world. It stays upright and turns slowly:
     * a flattened body with a smaller cap, like a cloud lump. Skipped with reduced particles.
     */
    public static void smoke(BlockFireMesh m, Vec3d p, double age, double size, double seed, double rise, double duration) {
        if (!m.embers) return;
        double l = age / duration;
        if (l <= 0 || l >= 1) return;
        double s = SMOKE_SCALE * size * Math.pow(Math.sin(l * Math.PI), .7) * (.8 + .4 * rnd(seed));
        if (s <= .01) return;
        Vec3d q = p.add((rnd(seed + 1) - .5) * .6 * l, rise * l, (rnd(seed + 2) - .5) * .6 * l);
        double tilt = (rnd(seed + 3) - .5) * .3;
        Vec3d[] axes = rotate(yawAxes(rnd(seed + 4) * TAU + age * (rnd(seed + 5) < .5 ? -.5 : .5)), X, tilt);
        double alpha = SMOKE_ALPHA * Math.min(1, (1 - l) * 2.5);
        cube(m, q, s * 1.15, s * .78, s * 1.15, SMOKE, seed, axes, 1, alpha);
        // The cap sits up and to one side, rising a little faster, so the outline is lumpy rather than a box.
        double a = rnd(seed + 6) * TAU, c = s * .62;
        Vec3d cap = q.add(axes[0].multiply(Math.cos(a) * s * .22)).add(axes[2].multiply(Math.sin(a) * s * .22))
                .add(axes[1].multiply(s * (.42 + .25 * l)));
        cube(m, cap, c * 1.1, c * .8, c * 1.1, SMOKE, seed + 7, axes, 1, alpha);
    }

    /** Options for {@link #sparkBurst}; defaults match kit.js. */
    public static final class Sparks {
        double speed, up, life, seed, size = .08, g = 8, bias, cool = 2.4;
        int count;
        Vec3d dir;
        public Sparks(int count, double speed, double up, double life, double seed) {
            this.count = count; this.speed = speed; this.up = up; this.life = life; this.seed = seed;
        }
        public Sparks size(double v) { size = v; return this; }
        public Sparks g(double v) { g = v; return this; }
        public Sparks dir(Vec3d d, double b) { dir = d; bias = b; return this; }
    }

    /** One-shot spark cubes thrown up and out from c, falling under g; each winks out as it cools. */
    public static void sparkBurst(BlockFireMesh m, Vec3d c, double age, Sparks o) {
        if (age <= 0 || !m.embers) return;
        double seed = o.seed;
        for (int i = 0; i < o.count; i++) {
            double li = o.life * (.6 + .7 * rnd(seed + i * 1.3)), l = age / li;
            if (l <= 0 || l >= 1) continue;
            double a = rnd(seed + i * 2.1) * TAU, h = o.speed * (.35 + .65 * rnd(seed + i * 3.7)), vy = o.up * (.45 + .7 * rnd(seed + i * 4.9));
            Vec3d vel = new Vec3d(Math.cos(a) * h, vy, Math.sin(a) * h);
            if (o.dir != null) vel = vel.add(o.dir.multiply(o.bias * o.speed));
            Vec3d p = c.add(vel.x * age, vel.y * age - o.g * age * age / 2, vel.z * age);
            cube(m, p, o.size * (1 - l) * (.7 + .6 * rnd(seed + i * 5.3)), heat(3.6 - l * o.cool), seed + i, tumble(seed + i, age, 6));
        }
    }

    /** A compact tumbling flame puff: a main cube plus three satellites. heat is a band index 0..4. */
    public static void puff(BlockFireMesh m, Vec3d p, double age, double size, double seed, double heat, double spin) {
        if (size <= .01) return;
        Vec3d[] axes = tumble(seed, age, spin);
        cube(m, p, size, heat(heat), seed, axes);
        for (int k = 0; k < 3; k++) {
            double sign = rnd(seed + k * 2.9) < .5 ? -1 : 1;
            cube(m, p.add(axes[k].multiply(sign * size * .48)), size * .55, heat(heat - .7), seed + k + 1, axes);
        }
    }

    /** Tiny rising spark cubes on looping lives around origin. Skipped with reduced particles. */
    public static void sparks(BlockFireMesh m, Vec3d origin, double t, int count, double spread, double rise, double seed, double size) {
        if (!m.embers) return;
        for (int i = 0; i < count; i++) {
            double period = .7 + rnd(seed + i * 1.9) * .7, u = fract(t / period + rnd(seed + i * 2.7));
            double a = rnd(seed + i * 3.1) * TAU, r = spread * Math.sqrt(rnd(seed + i * 4.3));
            Vec3d p = origin.add(Math.cos(a) * r + Math.sin(u * 5 + i) * .08, rise * u, Math.sin(a) * r + Math.cos(u * 4 + i) * .08);
            cube(m, p, size * (1 - u) * (.7 + .6 * rnd(seed + i * 5.7)), heat(3.4 - u * 2.6), seed + i, tumble(seed + i, t, 5));
        }
    }
    public static double fract(double x) { return x - Math.floor(x); }
}
