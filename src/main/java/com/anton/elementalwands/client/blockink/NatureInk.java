package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Nature's colour for the First Wizard's Nature spells, drawn on the frame's {@code nature} mesh from the boss's painted
 * atlas (its rows are {@code BlockNatureKit}'s: leaf, dark leaf, pink, violet, gold, bark, sap, cream). The pieces are
 * the kit's, at boss size: a three-box leaf, a stepped petal, a pollen kernel. Every Nature spell cools from these rows
 * into {@link InkKit#cool}.
 */
final class NatureInk {
    static final Identifier ATLAS = Identifier.of("elementalwands", "textures/misc/first_wizard_nature.png");
    static final int LEAF = 0, DARKLEAF = 1, PINK = 2, VIOLET = 3, GOLD = 4, BARK = 5, SAP = 6, CREAM = 7;
    /** How much each row ignores the world's light: brighter than the player's kit, so a spell reads at midnight. */
    private static final double[] GLOW = {.5, .35, .7, .7, 1, .3, .9, .6};
    private static final double FPS = 8;

    private NatureInk() {}

    /** One opaque cube of a Nature row, full edge lengths along axes. */
    static void cube(InkFrame f, Vec3d p, double sx, double sy, double sz, int row, double seed, Vec3d[] axes) {
        AtlasMesh m = f.nature();
        int frame = Math.floorMod((int) Math.floor(m.time * FPS + rnd(seed + .77) * 4), 4);
        m.box(p, sx * .5, sy * .5, sz * .5, axes, row, frame, GLOW[row], 1);
    }

    static void cube(InkFrame f, Vec3d p, double s, int row, double seed, Vec3d[] axes) { cube(f, p, s, s, s, row, seed, axes); }

    /** The kit's leaf: a blade, its tip and a rib, size blocks long along axes[2], flat along axes[1]. */
    static void leaf(InkFrame f, Vec3d p, double size, int row, double seed, Vec3d[] axes) {
        part(f, p, axes, size, 0, 0, 0, .65, .18, 1, row, seed);
        part(f, p, axes, size, 0, 0, .58, .38, .18, .3, row, seed);
        part(f, p, axes, size, 0, .1, -.1, .12, .12, .7, row == LEAF ? DARKLEAF : row, seed);
    }

    /** The kit's stepped petal, size blocks long along axes[2]. */
    static void petal(InkFrame f, Vec3d p, double size, int row, double seed, Vec3d[] axes) {
        part(f, p, axes, size, 0, 0, 0, .8, .2, 1, row, seed);
        part(f, p, axes, size, 0, .04, .57, .48, .16, .22, row, seed);
    }

    private static void part(InkFrame f, Vec3d p, Vec3d[] ax, double size, double ox, double oy, double oz,
                             double ex, double ey, double ez, int row, double seed) {
        if (size <= 1e-3) return;
        Vec3d c = p.add(ax[0].multiply(ox * size)).add(ax[1].multiply(oy * size)).add(ax[2].multiply(oz * size));
        cube(f, c, ex * size, ey * size, ez * size, row, seed, ax);
    }

    /**
     * A cube that has begun to cool: its Nature row until {@code cool} passes its own threshold (seeded, so a shape
     * cools in patches), then the ink band for how far past it is.
     */
    static void cooling(InkFrame f, Vec3d p, double sx, double sy, double sz, int row, double seed, Vec3d[] axes, double cool) {
        double threshold = .15 + .7 * rnd(seed * 1.31 + .4);
        if (cool <= threshold) cube(f, p, sx, sy, sz, row, seed, axes);
        else InkKit.cube(f.ink(), p, sx, sy, sz, InkKit.cool((cool - threshold) / (1.05 - threshold)), seed, axes, 1);
    }
}
