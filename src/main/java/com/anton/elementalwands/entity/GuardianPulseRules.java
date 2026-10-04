package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Phase-two core pulse: the Guardian plants, its open core drags everyone near toward it, then
 * detonates. Sprinting away escapes the pull, walking barely does, and standing still to cast is
 * dragged into the blast. Shared by the server attack, the client pull and the contract checks.
 */
public final class GuardianPulseRules {
    /** Plant until {@link #PULL_START}, pull until {@link #BLAST}, then recover. */
    public static final int PULL_START = 10, BLAST = 50, DURATION = 76;
    /** The first pulse waits this long after the shell breaks. */
    public static final int FIRST_DELAY = 160;
    public static final double PULL_RADIUS = 12, BLAST_RADIUS = 6;
    /**
     * Velocity added toward the core each tick a player stands on the ground. Vanilla ground
     * movement adds 0.1 a tick walking and 0.13 sprinting, so walking nets a crawl outward and
     * sprinting escapes; standing still is dragged about four blocks a second.
     */
    public static final double PULL = .09;
    /** Airborne players get the same share of the pull as their own air control (0.02 / 0.1). */
    public static final double AIR_SHARE = .2;
    public static final float DAMAGE = 16;
    private GuardianPulseRules() {}

    public static boolean pulling(int tick) { return tick >= PULL_START && tick < BLAST; }

    /** Ground impulse toward the core; nothing once a player is already at its feet. */
    public static Vec3d pull(Vec3d core, Vec3d feet) {
        Vec3d delta = new Vec3d(core.x - feet.x, 0, core.z - feet.z);
        double distance = delta.length();
        if (distance < 1.5 || distance > PULL_RADIUS) return Vec3d.ZERO;
        return delta.multiply(PULL / distance);
    }

    public static boolean inBlast(Vec3d center, Box victim) { return GuardianCombatRules.radial(center, victim, BLAST_RADIUS, 5); }

    /** 0–1 brightness of the gathering core: a slow plant, a building pull, a flash that fades after the blast. */
    public static float charge(float tick) {
        if (tick < 0 || tick >= DURATION) return 0;
        if (tick < PULL_START) return .25f * tick / PULL_START;
        if (tick < BLAST) return .25f + .75f * (tick - PULL_START) / (BLAST - PULL_START);
        return Math.max(0, 1 - (tick - BLAST) / 12f);
    }
}
