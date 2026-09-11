package com.anton.elementalwands.entity;

/** Shared server balance and visual timing. Health and guard are independent pools. */
public final class GuardianGuardRules {
    public static final int OPEN_TICKS = 24, EXPOSED_TICKS = 140, CLOSE_TICKS = 30;
    public static final int CLOSE_START = OPEN_TICKS + EXPOSED_TICKS;
    public static final int CYCLE_TICKS = CLOSE_START + CLOSE_TICKS;
    public static final float GUARDED_DAMAGE = .4f, EXPOSED_DAMAGE = 1.5f;
    private GuardianGuardRules() {}

    public static int health(int players) { return 600 + 450 * (Math.clamp(players, 1, 64) - 1); }
    public static int guard(int players) { return 120 + 90 * (Math.clamp(players, 1, 64) - 1); }
    // Ordinary attacks retain their full strength; exceptional burst still grows, more slowly.
    public static float impact(float damage) {
        if (!Float.isFinite(damage) || damage <= 0) return 0;
        return Math.min(damage, 40) + Math.max(0, damage - 40) * .35f;
    }
    public static float openness(float ticks) {
        if (ticks < 0 || ticks >= CYCLE_TICKS) return 0;
        float u = ticks < OPEN_TICKS ? ticks / OPEN_TICKS
                : ticks < CLOSE_START ? 1 : (CYCLE_TICKS - ticks) / CLOSE_TICKS;
        return u * u * (3 - 2 * u);
    }
    public static float multiplier(float ticks) {
        return GUARDED_DAMAGE + (EXPOSED_DAMAGE - GUARDED_DAMAGE) * openness(ticks);
    }
    public static int cracks(float remaining, float maximum) {
        float ratio = remaining / Math.max(1, maximum);
        return ratio <= .25f ? 3 : ratio <= .5f ? 2 : ratio <= .75f ? 1 : 0;
    }
}
