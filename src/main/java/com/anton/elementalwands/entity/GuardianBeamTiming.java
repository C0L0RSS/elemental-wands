package com.anton.elementalwands.entity;

/** Tick timings shared by the authoritative attack and the client pulse renderer. */
public final class GuardianBeamTiming {
    public static final int LOCK = 24;
    public static final int FIRE = 32;
    public static final int PULSE = 12;
    public static final int END = FIRE + PULSE + 28;
    public static final int COOLDOWN = 60;
    public static final double RANGE = 24;
    public static final double RADIUS = .42;
    public static final float DAMAGE = 8;

    private GuardianBeamTiming() {}

    public static boolean isTracking(int tick) { return tick < LOCK; }
    public static boolean fires(int tick) { return tick == FIRE; }
    public static boolean isPulse(float tick) { return tick >= FIRE && tick < FIRE + PULSE; }
    public static float pulseFade(float tick) {
        if (!isPulse(tick)) return 0;
        float progress=(tick-FIRE)/PULSE;
        // Hold a strong cyan body, then taper through the last third of the pulse.
        return progress < .65f ? 1 : (1-progress)/.35f;
    }
}
