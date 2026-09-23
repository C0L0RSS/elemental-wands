package com.anton.elementalwands.util;

import net.minecraft.util.math.MathHelper;

/** Shared tuning: all distances are blocks and all durations are ticks. */
public final class StoneTechniqueRules {
    public static final String FAULTLINE = "faultline", CHARGE = "stone_charge";
    public static final int FAULT_COOLDOWN = 180, CHARGE_COOLDOWN = 160;
    public static final int WINDUP = 2, FAULT_ROWS = 20, INTERRUPT = 12, INTERRUPT_GRACE = 40;
    public static final int FAULT_ROWS_PER_TICK = 2;
    public static final double FAULT_LAUNCH_SPEED = .45;
    public static final int RAMP = 50, MAX_RUN = 100, BRAKE = 6;
    public static final double START_SPEED = .30, TOP_SPEED = .82;
    public static float power(double speed) {
        return (float)MathHelper.clamp((speed - START_SPEED) / (TOP_SPEED - START_SPEED), 0, 1);
    }
    public static double speed(double progress, double slow) {
        return (START_SPEED + (TOP_SPEED - START_SPEED) * MathHelper.clamp(progress / RAMP, 0, 1)) * slow;
    }
    public static float turn(float current, float desired, double speed, boolean ground) {
        float limit = (float)((4.0 - 2.5 * power(speed)) * (ground ? 1 : .35));
        return current + MathHelper.clamp(MathHelper.wrapDegrees(desired - current), -limit, limit);
    }
    public static int budget(float power) { return power < .3f ? 0 : power >= .98f ? 16 : 6; }
    public static double depth(float power) { return power >= .98f ? 4 : power >= .3f ? 2 : 1; }
    public static float damage(float power) { return 4 + 10 * power; }
    private StoneTechniqueRules() {}
}
