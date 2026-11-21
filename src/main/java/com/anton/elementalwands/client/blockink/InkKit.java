package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireKit;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's ink as block-style primitives, in the block-fire kit's manner: every function is a pure function
 * of the mesh clock, its arguments and a seed. The ink is liquid, not Space's void glass: matte black with a violet
 * rim, wet highlights, dripping and pooling; never stars or glass panes.
 *
 * <p>Every boss spell cools into ink: it starts in its element's colour (drawn with that element's kit) and cools
 * through {@link #cool} (magenta, violet, purple, deep purple) to black, where the player's version smokes or fades.
 * {@link #coolingFlame} is the Fire kit's flame with that ending: orange at its base, purple tips, black ink where
 * smoke would be.</p>
 */
public final class InkKit {
    /** Atlas rows in textures/misc/first_wizard_ink.png (built by art/first_wizard/build_ink.py). */
    public static final int INK = 0, RIM = 1, VIOLET = 2, PURPLE = 3, DEEP = 4, GLOSS = 5, MIST = 6, MAGENTA = 7;
    public static final Identifier ATLAS = Identifier.of("elementalwands", "textures/misc/first_wizard_ink.png");
    /** Hot to cold: what an element's colour cools through on its way to black. */
    private static final int[] COOL = {MAGENTA, VIOLET, PURPLE, DEEP, INK};
    private static final double FPS = 6;

    private InkKit() {}

    /** The cooling band at k, 0 (just left the element's colour) to 1 (black ink). */
    public static int cool(double k) { return COOL[(int) Math.round(clamp(k, 0, 1) * (COOL.length - 1))]; }

    /** How much a row ignores the world's light: the purples glow; the ink is lit, but never quite lost in the dark. */
    static double glow(int row) { return row == VIOLET || row == MAGENTA || row == PURPLE || row == RIM ? 1 : .55; }

    // ---- Cubes ---------------------------------------------------------------------------------------

    public static void cube(AtlasMesh m, Vec3d p, double s, int row, double seed, Vec3d[] axes) { cube(m, p, s, s, s, row, seed, axes, 1); }

    /** One ink cube with full edge lengths along axes; alpha below 1 makes it see-through (the mist row is always). */
    public static void cube(AtlasMesh m, Vec3d p, double sx, double sy, double sz, int row, double seed, Vec3d[] axes, double alpha) {
        int frame = Math.floorMod((int) Math.floor(m.time * FPS + rnd(seed + .77) * 4), 4);
        m.box(p, sx * .5, sy * .5, sz * .5, axes, row, frame, glow(row), row == MIST ? Math.min(alpha, .75) : alpha);
    }

    // ---- Ink -----------------------------------------------------------------------------------------

    /**
     * A blob of ink: three crossing slabs round a cube, so its outline is round at any angle, wobbling slowly like a
     * held drop. The outer slabs take {@code skin}, the heart {@code row}.
     */
    public static void blob(AtlasMesh m, Vec3d c, double r, int row, int skin, double seed, double t) {
        if (r <= .01) return;
        double wob = .08 * Math.sin(t * 5.3 + seed);
        Vec3d[] axes = rotate(yawAxes(rnd(seed) * TAU + t * .7), X, .25 * Math.sin(t * 2.1 + seed * 3));
        cube(m, c, r * 1.25, row, seed, axes);
        cube(m, c, r * 2 * (1 + wob), r * 1.3, r * 1.3, skin, seed + 1, axes, 1);
        cube(m, c, r * 1.3, r * 2 * (1 - wob), r * 1.3, skin, seed + 2, axes, 1);
        cube(m, c, r * 1.3, r * 1.3, r * 2 * (1 + wob * .5), skin, seed + 3, axes, 1);
    }

    /**
     * A drop of ink falling from {@code from} to the floor at {@code floor}, then spreading into a flat splat that
     * shrinks away. age and life are seconds.
     */
    public static void drip(AtlasMesh m, Vec3d from, double floor, double age, double life, double size, double seed) {
        if (age < 0 || age > life) return;
        double drop = Math.max(0, from.y - floor - size * .5), fall = Math.sqrt(2 * drop / 14);
        Vec3d[] axes = yawAxes(rnd(seed) * TAU);
        if (age < fall) {
            // Stretched as it falls; a wet highlight on its side.
            double y = from.y - 7 * age * age;
            double stretch = 1 + Math.min(1.2, age * 6);
            cube(m, new Vec3d(from.x, y, from.z), size, size * stretch, size, age < .05 ? GLOSS : INK, seed, axes, 1);
            return;
        }
        double k = (age - fall) / Math.max(.05, life - fall);
        double w = size * (1.4 + 1.6 * Math.sqrt(k)) * (1 - k * k);
        cube(m, new Vec3d(from.x, floor + .02, from.z), w, .04, w * (.8 + .3 * rnd(seed + 1)), k < .3 ? GLOSS : INK, seed + 2, axes, 1);
    }

    /**
     * A pool of ink on the floor: flat slabs over a disc, lapping slowly, a violet meniscus at its edge. fill 0 to 1
     * grows it from the middle. Slabs are a few hundredths high, so a pool never hides a body.
     */
    public static void pool(AtlasMesh m, Vec3d centre, double radius, double fill, double t, double seed) {
        double r = radius * clamp(fill, 0, 1);
        if (r <= .05) return;
        double cell = Math.max(.32, r / 5);
        int n = (int) Math.ceil(r / cell);
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) {
            double x = i * cell, z = j * cell, d = Math.hypot(x, z);
            double edge = r * (.86 + .14 * noise(Math.atan2(z, x) * 2.2 + seed + t * .6));
            if (d > edge) continue;
            double k = seed + i * 7.1 + j * 13.3;
            double lap = .015 + .012 * Math.sin(t * 3 + d * 2.5 + rnd(k) * 3);
            // A violet meniscus only round a pool big enough to have an inside; a small one is all ink.
            boolean rim = r > cell * 2.5 && d > edge - cell * .9;
            Vec3d at = new Vec3d(centre.x + x, centre.y + lap, centre.z + z);
            double w = cell * (1.05 + .1 * rnd(k + 1));
            cube(m, at, w, rim ? .07 : .05, w, rim ? PURPLE : rnd(k + 2) < .25 ? GLOSS : INK, k, AXES, 1);
        }
    }

    /**
     * Beads of ink running from one point to another on a slight arc, looping: ink rising off the wand arm to where a
     * spell gathers. count beads at once, size their largest.
     */
    public static void strand(AtlasMesh m, Vec3d from, Vec3d to, double t, int count, double size, double seed) {
        Vec3d along = to.subtract(from);
        double length = along.length();
        if (length < .1) return;
        Vec3d bow = along.crossProduct(UP).lengthSquared() < 1e-6 ? X : along.crossProduct(UP).normalize();
        for (int i = 0; i < count; i++) {
            double period = .55 + rnd(seed + i * 1.7) * .3, u = fract(t / period + i / (double) count + rnd(seed + i) * .3);
            double arc = Math.sin(u * Math.PI) * length * .12 * (rnd(seed + i * 2.3) - .5) * 2;
            Vec3d p = from.add(along.multiply(u)).add(bow.multiply(arc)).add(0, Math.sin(u * Math.PI) * .25, 0);
            double s = size * (.55 + .45 * Math.sin(u * Math.PI)) * (.8 + .4 * rnd(seed + i * 3.1));
            // Every third bead catches the violet, so the strand reads against a dark sky.
            cube(m, p, s, i % 3 == 0 ? RIM : rnd(seed + i * 4.7) < .3 ? GLOSS : INK, seed + i, tumble(seed + i, t, 3));
        }
    }

    /** Blobs of ink thrown out from a point that fall and splat: the ink in a burst. age seconds. */
    public static void splash(AtlasMesh m, Vec3d c, double floor, double age, int count, double speed, double size, double seed) {
        for (int i = 0; i < count; i++) {
            double a = rnd(seed + i * 1.9) * TAU, h = speed * (.4 + .6 * rnd(seed + i * 2.9)), up = speed * (.3 + .6 * rnd(seed + i * 3.7));
            double fly = (up + Math.sqrt(up * up + 2 * 14 * Math.max(0, c.y - floor))) / 14;
            double s = size * (.6 + .6 * rnd(seed + i * 4.1));
            if (age < fly) {
                Vec3d p = c.add(Math.cos(a) * h * age, up * age - 7 * age * age, Math.sin(a) * h * age);
                cube(m, p, s, rnd(seed + i) < .3 ? GLOSS : INK, seed + i, tumble(seed + i, age, 5));
            } else drip(m, new Vec3d(c.x + Math.cos(a) * h * fly, floor + s, c.z + Math.sin(a) * h * fly), floor, age - fly + .001, .9, s, seed + i);
        }
    }

    /** The top of the first solid block at or below a point, looking at most six blocks down. */
    public static double floorBelow(net.minecraft.world.BlockView world, Vec3d p) {
        var pos = net.minecraft.util.math.BlockPos.ofFloored(p).mutableCopy();
        for (int i = 0; i < 6; i++, pos.move(0, -1, 0)) {
            var shape = world.getBlockState(pos).getCollisionShape(world, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
        }
        return p.y - 6;
    }

    // ---- Cooling ---------------------------------------------------------------------------------------

    /**
     * The Fire kit's flame tongue, cooling into ink: its base and lower part are block fire (heat 1 starts yellow),
     * from {@code inkFrom} (0 to 1 along it) it cools through magenta and purple to black ink, and where the player's
     * fire would smoke it lets off matte black ink that rises and shrinks away.
     */
    public static void coolingFlame(InkFrame f, Vec3d root, Vec3d dir, double width, double length, double seed, double heat, double inkFrom) {
        if (length <= .01 || width <= .01) return;
        var fire = f.fire();
        AtlasMesh ink = f.ink();
        double t = ink.time;
        Vec3d d = dir.normalize();
        double roll = rnd(seed + .9) * TAU, top = heat * 3;
        Vec3d[] b = basis(d, roll);
        Vec3d bx = b[0], bz = b[2];
        double L = length * (.85 + .3 * noise(t * 4.1 + seed * 7.1));
        int n = (int) clamp(Math.round(L / width * 2.2), 3, 8);
        double leanA = (noise(t * 1.6 + seed * 3.3) - .5) * .16, leanB = (noise(t * 1.9 + seed * 5.9) - .5) * .16;
        double bw = width * (.92 + .16 * noise(t * 6.7 + seed * 2.3));
        Vec3d base = root.add(d.multiply(bw * .36));
        for (int j = 0; j < 3; j++) {
            double a = roll + j * TAU / 3, s = bw * (.68 + .16 * noise(t * 6.1 + seed * 2.3 + j * 3.1));
            Vec3d c = base.add(bx.multiply(Math.cos(a) * bw * .17)).add(bz.multiply(Math.sin(a) * bw * .17));
            BlockFireKit.cube(fire, c, s, s * .92, s, heat(top - (j > 0 ? .7 : 0)), seed + j * 9, basis(d, roll + j * .55));
        }
        double period = clamp(.32 + L * .2, .32, 1.1);
        for (int i = 0; i < n; i++) {
            double phase = t / period + i / (double) n + rnd(seed + i * 1.31) * .35 / n, loop = Math.floor(phase), u = phase - loop;
            double k = seed + i * 4.1 + loop * 17.3;
            double w = bw * .74 * Math.pow(1 - u, .7) * (.85 + .3 * rnd(k));
            if (w < .012) continue;
            Vec3d side = bx.multiply((rnd(k + 1.7) - .5) * width * .4 * u + leanA * L * u * u)
                    .add(bz.multiply((rnd(k + 2.9) - .5) * width * .4 * u + leanB * L * u * u));
            Vec3d c = base.add(d.multiply(L * Math.pow(u, .85))).add(side);
            Vec3d[] axes = basis(d, roll + (rnd(k + 3.3) - .5) * 1.2 + u * .6);
            if (u < inkFrom) BlockFireKit.cube(fire, c, w, w * 1.15, w, heat(top - u * 2.5), seed + i, axes);
            else cube(ink, c, w, w * 1.15, w, cool((u - inkFrom) / Math.max(.05, 1 - inkFrom)), seed + i, axes, 1);
        }
        // Where its fire would smoke: ink, matte and opaque, rising off the tip and shrinking away.
        if (!f.embers()) return;
        for (int i = 0; i < 2; i++) {
            double life = .7 + rnd(seed + i * 5.3) * .3, u = fract(t / life + i * .5 + rnd(seed + i));
            Vec3d p = base.add(d.multiply(L * (1 + .55 * u))).add(bx.multiply((rnd(seed + i * 6.1) - .5) * width * .5 * u));
            double s = bw * .55 * Math.sin(Math.PI * Math.min(1, u * 1.4)) * (1 - u);
            cube(ink, p, s, s * .8, s, INK, seed + i * 3, tumble(seed + i, t, 1.5), 1);
        }
    }
}
