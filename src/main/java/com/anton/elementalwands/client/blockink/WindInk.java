package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;

import net.minecraft.util.math.Vec3d;

/**
 * Wind's colour for the First Wizard's Wind spells, drawn on the frame's {@code wind} mesh from the boss's wind atlas
 * (its rows are {@code BlockWindKit}'s: 0 gale, 1 cloud, 2 mist, 3 streak). Air is see-through below alpha 1; the gale
 * rim is solid. Every Wind spell cools from these rows into {@link InkKit#cool}.
 */
final class WindInk {
    static final int GALE = 0, CLOUD = 1, MIST = 2, STREAK = 3;
    /** How much each row ignores the world's light: brighter than the player's kit, so a spell reads at midnight. */
    private static final double[] GLOW = {1, .75, .6, .9, .8, .6, .45, .35};
    private static final double FPS = 8;

    private WindInk() {}

    /** One cube of wind with full edge lengths along axes; alpha below 1 makes it see-through air. */
    static void air(AtlasMesh m, Vec3d p, double sx, double sy, double sz, int row, double seed, Vec3d[] axes, double alpha) {
        if (alpha <= .02) return;
        int frame = Math.floorMod((int) Math.floor(m.time * FPS + rnd(seed + .77) * 4), 4);
        m.box(p, sx * .5, sy * .5, sz * .5, axes, row, frame, GLOW[row], Math.min(1, alpha));
    }

    /** An upright basis whose first axis runs along a horizontal direction (right-handed). */
    static Vec3d[] flat(Vec3d along) {
        Vec3d t = new Vec3d(along.x, 0, along.z);
        t = t.lengthSquared() < 1e-9 ? new Vec3d(1, 0, 0) : t.normalize();
        return new Vec3d[]{t, UP, t.crossProduct(UP)};
    }

    /** The horizontal right-hand side of a direction. */
    static Vec3d side(Vec3d dir) {
        Vec3d s = new Vec3d(-dir.z, 0, dir.x);
        return s.lengthSquared() < 1e-9 ? new Vec3d(0, 0, 1) : s.normalize();
    }
}
