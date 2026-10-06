package com.anton.elementalwands.entity;

/** Shared phase timing: authored animation seconds and server impacts use this same clock. */
public final class GuardianPhaseRules {
    /** The shell break: a telegraphed stagger, a burst at {@link #BURST}, then the ribs swing open. */
    public static final int TRANSITION_TICKS = 64, BURST = 32;
    public static final double THRESHOLD = .50, BURST_RADIUS = 6;
    public static final float BURST_DAMAGE = 10;
    /** Hold lengths, in ticks, a delayed slam can pause at the top of its swing; each has its own clip. */
    public static final int[] SLAM_HOLDS = {10, 16, 22};
    /** Seconds into the slam clip where its fists peak; a delayed slam's hold is inserted here. */
    public static final double SLAM_APEX = 1.075;
    private GuardianPhaseRules() {}
    public static boolean threshold(float health, float maximum) { return health > 0 && health <= maximum * THRESHOLD; }
    public static double waveRange(boolean unstable) { return unstable ? 44 : 32; }
    public static double waveSpeed(boolean unstable) { return unstable ? .95 : .85; }
    public static int waveTicks(boolean unstable) { return (int)Math.ceil(waveRange(unstable)/waveSpeed(unstable)); }
    public static int gap(boolean unstable) { return unstable ? 10 : 12; }
    public static int slamDuration(boolean fast) { return fast ? 45 : 56; }
    public static int slamImpact(boolean fast) { return fast ? 21 : 26; }
    public static int slamDuration(boolean fast, int hold) { return slamDuration(fast) + (fast ? 0 : hold); }
    public static int slamImpact(boolean fast, int hold) { return slamImpact(fast) + (fast ? 0 : hold); }
    public static String slamClip(boolean fast, int hold) { return fast ? "slam_fast" : hold > 0 ? "slam_hold_" + hold : "slam"; }
    public static int throwDuration(boolean unstable) { return unstable ? 47 : 72; }
    public static int throwRelease(boolean unstable) { return unstable ? 29 : 44; }
    public static int throwLock(boolean unstable) { return throwRelease(unstable)-4; }
    public static int repeats(GuardianCombatRules.Attack attack, boolean unstable) {
        return unstable && (attack == GuardianCombatRules.Attack.THROW || attack == GuardianCombatRules.Attack.SLAM
                || attack == GuardianCombatRules.Attack.SHOCKWAVE) ? 3 : 1;
    }
}
