package com.anton.elementalwands.entity;

/**
 * Health and the stone shell. Phase one wears the shell toward the break (visible crack stages);
 * the break at half health is the phase change, and the ribs stay open for the rest of the fight.
 */
public final class GuardianShellRules {
    /** Ticks for the ribs to swing open after the break's burst. */
    public static final int RIB_OPEN_TICKS = 24;
    /** Height of the core above its feet: where the burst and the pulse come from. */
    public static final double CORE_HEIGHT = 3.3;
    private GuardianShellRules() {}

    public static int health(int players) { return 1400 + 1000 * (Math.clamp(players, 1, 64) - 1); }
    // Ordinary attacks retain their full strength; exceptional burst still grows, more slowly.
    public static float impact(float damage) {
        if (!Float.isFinite(damage) || damage <= 0) return 0;
        return Math.min(damage, 40) + Math.max(0, damage - 40) * .35f;
    }
    /** Phase one cannot lose health below this until the shell has broken. */
    public static float gate(float maximum) { return maximum * (float)GuardianPhaseRules.THRESHOLD; }
    /** Crack stage 0–3 as phase one wears toward the break; a broken shell stays fully cracked. */
    public static int cracks(float health, float maximum, boolean broken) {
        if (broken) return 3;
        float worn = (1 - health / Math.max(1, maximum)) / (1 - (float)GuardianPhaseRules.THRESHOLD);
        return Math.clamp((int)Math.floor(worn * 4 + 1e-4f), 0, 3);
    }
    /** Rib openness: shut through phase one, swinging open after the burst, then open for good. */
    public static float openness(float phaseTicks, boolean broken) {
        if (!broken) return 0;
        if (phaseTicks < 0) return 1;
        float u = Math.clamp((phaseTicks - GuardianPhaseRules.BURST) / RIB_OPEN_TICKS, 0, 1);
        return u * u * (3 - 2 * u);
    }
}
