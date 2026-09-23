package com.anton.elementalwands.util;

import net.minecraft.util.math.Vec3d;

public final class SpringbloomRules {
    public static final int COOLDOWN = 200, LIFETIME = 80, FLIGHT_LIMIT = 60;
    public static final double HEIGHT = .6875, HALF_WIDTH = .75;
    // Vanilla air: vertical velocity loses .08 then multiplies by .98 each tick.
    public static final double UPWARD_SPEED = 2.0;
    public static final double MAX_HORIZONTAL_SPEED = 1.35;    public static Vec3d launch(Vec3d incoming) {
        double speed = incoming.horizontalLength();
        if (speed < .015) return new Vec3d(0, UPWARD_SPEED, 0);
        double horizontal = MAX_HORIZONTAL_SPEED * Math.min(1, speed / .28);
        return new Vec3d(incoming.x / speed * horizontal, UPWARD_SPEED, incoming.z / speed * horizontal);
    }
    private SpringbloomRules() {}
}
