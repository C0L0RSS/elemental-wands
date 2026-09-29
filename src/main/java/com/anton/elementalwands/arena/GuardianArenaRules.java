package com.anton.elementalwands.arena;

/** Admission and arrival timing for Guardian fights in the Shattered Nave, shared with the checks. */
public final class GuardianArenaRules {
    /** Players this close to the church socket are sealed into the ritual's fight. */
    public static final int GATHER_RADIUS = 20;
    /** From the players' arrival (blinded by the ritual) to the Guardian's intro on its seat. */
    public static final int RISE_DELAY = 40;
    /** How long an ended fight holds its players before sending them home. */
    public static final int VICTORY_DELAY = 200, WIPE_DELAY = 60;

    private GuardianArenaRules() {}

    public static double ease(double t) {
        t = Math.clamp(t, 0, 1);
        return t*t*(3-2*t);
    }
}
