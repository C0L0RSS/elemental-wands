package com.anton.elementalwands.client.blocknature;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.basis;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;

import net.minecraft.util.math.Vec3d;

/**
 * Nature's pieces, ported from the approved browser study (art/nature/blocknature/kit.js): a stepped petal, a
 * three-box leaf, a pollen kernel. Every function is a pure function of the mesh clock, its arguments and a seed, so a
 * spell looks the same on every client. Pieces are opaque, oriented in the world (never camera-facing), tumble within
 * a bounded wobble and disappear only by shrinking.
 */
public final class BlockNatureKit {
    /** Atlas rows in textures/misc/blocknature.png. */
    public static final int LEAF = 0, DARKLEAF = 1, PINK = 2, VIOLET = 3, GOLD = 4, BARK = 5, SAP = 6, CREAM = 7;
    /** Piece shapes. */
    public static final int PETAL = 0, LEAF_PIECE = 1, POLLEN = 2;
    /** How much each band holds its own light at night (1 = full-bright); pollen and sap glow most. */
    private static final double[] GLOW = {.15, 0, .3, .3, .65, 0, .5, .25};
    private static final double FPS = 8;
    /** Seconds the shared heal cue lasts (kit.js HEAL_TIME). */
    static final double HEAL_TIME = .9;
    private BlockNatureKit() {}

    static void cube(BlockNatureMesh m, Vec3d p, double sx, double sy, double sz, int band, double seed, Vec3d[] axes) {
        int frame = (int) Math.floor(m.time * FPS + rnd(seed) * 4) % 4;
        m.box(p, sx * .5, sy * .5, sz * .5, axes, band, Math.floorMod(frame, 4), GLOW[band]);
    }

    /** One piece of the given shape and band, size blocks across, tumbling with age. */
    static void piece(BlockNatureMesh m, Vec3d p, double size, int kind, int band, double seed, double age) {
        if (size <= 1e-3) return;
        Vec3d[] ax = basis(new Vec3d(Math.sin(seed + age * 2), 1, Math.cos(seed * .7 + age)), seed + age * 2);
        switch (kind) {
            case POLLEN -> part(m, p, ax, size, 0, 0, 0, .5, .5, .5, band, seed);
            case LEAF_PIECE -> {
                part(m, p, ax, size, 0, 0, 0, .65, .18, 1, band, seed);
                part(m, p, ax, size, 0, 0, .58, .38, .18, .3, band, seed);
                part(m, p, ax, size, 0, .1, -.1, .12, .12, .7, band, seed);
            }
            default -> {
                part(m, p, ax, size, 0, 0, 0, .8, .2, 1, band, seed);
                part(m, p, ax, size, 0, .04, .57, .48, .16, .22, band, seed);
            }
        }
    }

    private static void part(BlockNatureMesh m, Vec3d p, Vec3d[] ax, double size, double ox, double oy, double oz,
                             double ex, double ey, double ez, int band, double seed) {
        Vec3d c = p.add(ax[0].multiply(ox * size)).add(ax[1].multiply(oy * size)).add(ax[2].multiply(oz * size));
        cube(m, c, ex * size, ey * size, ez * size, band, seed, ax);
    }

    /** A burst's look; see burst. */
    record Burst(int count, double scale, double radius, double duration, int kind, int band, boolean fall) {
        static Burst petals(int count, double scale, double radius) { return new Burst(count, scale, radius, .7, PETAL, PINK, false); }
        Burst falling() { return new Burst(count, scale, radius, duration, kind, band, true); }
        Burst lasting(double seconds) { return new Burst(count, scale, radius, seconds, kind, band, fall); }
    }

    /** Pieces thrown out from p on a golden-angle spiral, rising (or falling) a little and shrinking away. */
    static void burst(BlockNatureMesh m, Vec3d p, double age, Burst o, double seed) {
        if (age < 0 || age >= o.duration()) return;
        double k = age / o.duration(), keep = 1 - smoothstep((k - .35) / .65);
        for (int i = 0; i < o.count(); i++) {
            double a = i * 2.39996 + seed, r = o.radius() * (.2 + Math.sin(k * Math.PI / 2)) * (.65 + .35 * rnd(i + seed));
            double y = (o.fall() ? -k * .6 : Math.sin(k * Math.PI) * .45) + rnd(i + seed) * .15;
            piece(m, p.add(Math.cos(a) * r, y, Math.sin(a) * r), o.scale() * keep, o.kind(), o.band(), seed + i, age);
        }
    }

    /** The shared heal: six sap leaves spiral up around the body with four gold kernels climbing faster between them. */
    static void heal(BlockNatureMesh m, Vec3d feet, double age) {
        if (age < 0 || age >= HEAL_TIME) return;
        double keep = (1 - smoothstep((age - .45) / .45)) * smoothstep(age / .1);
        for (int i = 0; i < 6; i++) {
            double a = i * TAU / 6 + age * 1.6, r = .62 + age * .13;
            piece(m, feet.add(Math.cos(a) * r, .45 + age * 1.5 + (i % 2) * .2, Math.sin(a) * r), .34 * keep, LEAF_PIECE, SAP, i, age);
        }
        for (int i = 0; i < 4; i++) {
            double a = (i + .5) * TAU / 4 - age * 2.2, r = .5;
            piece(m, feet.add(Math.cos(a) * r, .8 + age * 2, Math.sin(a) * r), .19 * keep, POLLEN, GOLD, i + 11, age);
        }
    }
}
