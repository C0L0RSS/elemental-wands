package com.anton.elementalwands.entity;

/** Shared phase timing: authored animation seconds and server impacts use this same clock. */
public final class GuardianPhaseRules {
    public static final int TRANSITION_TICKS = 64;
    public static final double THRESHOLD = .60;
    private GuardianPhaseRules() {}
    public static boolean threshold(float health, float maximum) { return health > 0 && health <= maximum * THRESHOLD; }
    public static double waveRange(boolean unstable) { return unstable ? 44 : 32; }
    public static double waveSpeed(boolean unstable) { return unstable ? .95 : .85; }
    public static int waveTicks(boolean unstable) { return (int)Math.ceil(waveRange(unstable)/waveSpeed(unstable)); }
    public static int gap(boolean unstable) { return unstable ? 10 : 12; }
    public static int slamDuration(boolean fast) { return fast ? 45 : 56; }
    public static int slamImpact(boolean fast) { return fast ? 21 : 26; }
    public static int throwDuration(boolean unstable) { return unstable ? 47 : 72; }
    public static int throwRelease(boolean unstable) { return unstable ? 29 : 44; }
    public static int throwLock(boolean unstable) { return throwRelease(unstable)-4; }
    public static int repeats(GuardianCombatRules.Attack attack, boolean unstable) {
        return unstable && (attack == GuardianCombatRules.Attack.THROW || attack == GuardianCombatRules.Attack.SLAM
                || attack == GuardianCombatRules.Attack.SHOCKWAVE) ? 3 : 1;
    }
}
