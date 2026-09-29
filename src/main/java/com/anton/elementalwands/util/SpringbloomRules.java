package com.anton.elementalwands.util;

import net.minecraft.util.math.Vec3d;

public final class SpringbloomRules {
    public static final int COOLDOWN = 120, LIFETIME = 160, FLIGHT_LIMIT = 60;
    public static final double HEIGHT = .6875, HALF_WIDTH = .75;
    // Vanilla air: vertical velocity loses .08 then multiplies by .98 each tick.
    // A flatter, longer arc than a straight spring: about 14 blocks up, far more ground covered.
    public static final double UPWARD_SPEED = 1.7;
    public static final double MAX_HORIZONTAL_SPEED = 1.9;
    /** Ground speeds (blocks a tick) a run or a walk into the cushion launches with. */
    public static final double RUN_SPEED = .28, WALK_SPEED = .216;
    /** How close to its edge a grounded player must press to set it off. */
    public static final double CONTACT = .08;
    public static Vec3d launch(Vec3d incoming) {
        double speed = incoming.horizontalLength();
        if (speed < .015) return new Vec3d(0, UPWARD_SPEED, 0);
        double horizontal = MAX_HORIZONTAL_SPEED * Math.min(1, speed / .28);
        return new Vec3d(incoming.x / speed * horizontal, UPWARD_SPEED, incoming.z / speed * horizontal);
    }
    /** Running into the cushion launches along the runner's facing, at their ground speed. */
    public static Vec3d runLaunch(float yaw, boolean sprinting) {
        double speed = sprinting ? RUN_SPEED : WALK_SPEED, radians = Math.toRadians(yaw);
        return launch(new Vec3d(-Math.sin(radians) * speed, 0, Math.cos(radians) * speed));
    }
    private SpringbloomRules() {}
}
