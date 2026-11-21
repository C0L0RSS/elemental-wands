package com.anton.elementalwands.client.blockink;

import net.minecraft.util.math.Vec3d;

/**
 * One copy's tentacles as this client last drew them ({@link TendrilFx}): their clocks, each one's spring toward the
 * pose the move in hand asks of it ({@link TendrilMoves}), the last such pose to blend back out of, and where their tips
 * are planted on the floor. Client drawing state only: nothing here decides a hit.
 */
final class TendrilRig {
    /** Each tentacle's crawl and questing-tip clocks (radians), and how much faster than at rest its waves run now (turns a second). */
    final double[] phase = new double[3], quest = new double[3], hurry = new double[3];
    /** Each tentacle's spring toward its attack pose (0 at rest, 1 in the pose; it overshoots a little either way) and its speed. */
    final double[] w = new double[3], wSpeed = new double[3];
    /**
     * Each tentacle's last attack pose in her frame (her left, up, ahead, from her body), so it blends back out of it as
     * she moves; and the pose a new move takes over from, with when (world seconds) it began to.
     */
    final Vec3d[][] held = new Vec3d[3][], from = new Vec3d[3][];
    final double[] fadeAt = new double[3];
    /**
     * Where each tip is planted on the floor (world), null when it is not, and since when (ticks of the move); a step
     * to a fresh spot as she turns: when it began (ticks of the move, NaN when none) and where it left from.
     */
    final Vec3d[] plant = new Vec3d[3], stepFrom = new Vec3d[3];
    final double[] plantedAt = new double[3], stepAt = {Double.NaN, Double.NaN, Double.NaN};
    /** The way each gripping arm lies along the floor (unit, level), kept from when it planted so the planted part never turns with her. */
    final Vec3d[] grip = new Vec3d[3];
    /** Ink thrown up where a tip struck the floor: where and when (world seconds); and whether this move's slap has struck. */
    final Vec3d[] splashAt = new Vec3d[3];
    final double[] splashTime = new double[3];
    final boolean[] slapped = new boolean[3];
    /** The striking tentacle's spring (Slam, Sweep, the change's sweep), its last strike, and the Slam's eased aim. */
    double lash, lashSpeed;
    TendrilFx.Strike last;
    Vec3d aim;
    /** Eased targets: the Leap's landing; the ink a spell gathers. */
    Vec3d land, focus;
    /** Her velocity (blocks a second, eased) and her body where it was last frame. */
    Vec3d vel = Vec3d.ZERO, lastBody;
    /** How fast she turns (radians a second toward her left, eased), and her forward last frame. */
    double turn;
    Vec3d lastFwd;
    /** The move it last drew and that move's age, to tell when a new one begins. */
    String move = "";
    double age = -1;
    double time;
    boolean started;

    /** A new move (or none): plants and eased targets start afresh, and each tentacle still in a pose fades from it. */
    void begin(double now) {
        for (int i = 0; i < 3; i++) {
            plant[i] = grip[i] = null;
            stepAt[i] = Double.NaN;
            slapped[i] = false;
            from[i] = held[i] != null && Math.abs(w[i]) > .02 ? held[i] : null;
            fadeAt[i] = now;
        }
        land = focus = null;
    }
}
