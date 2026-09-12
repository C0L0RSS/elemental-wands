package com.anton.elementalwands.entity;

import net.minecraft.util.math.Vec3d;

/** Three committed bursts. Shared by server release, client formation and timing checks. */
public final class GuardianFanRules {
    public static final int COUNT = 3, BURSTS = 3, LOCK = 14, RELEASE = 18, REPEAT = 8,
            RECOVERY = 14, HOVER_TICKS = 24;
    public static final double SPEED = 1.8, RANGE = 40, RADIUS = .32;
    private GuardianFanRules() {}
    public static int duration(boolean secondPhase) { return RELEASE + (BURSTS - 1) * REPEAT + RECOVERY; }
    public static int burst(float age) {
        return Math.clamp((int)Math.ceil((age - RELEASE) / REPEAT), 0, BURSTS - 1);
    }
    public static float localTime(float age) { return age - burst(age) * REPEAT; }
    public static Vec3d socket(Vec3d feet, float yaw, float pitch, int index) {
        Vec3d forward = Vec3d.fromPolar(0, yaw), right = new Vec3d(forward.z, 0, -forward.x);
        return feet.add(right.multiply((index - 1) * 1.2)).add(forward.multiply(.85))
                .add(0, 6.2 - Math.abs(index - 1) * .25 + Math.clamp(-pitch / 60, 0, 1) * .8, 0);
    }
    public static Vec3d direction(Vec3d origin, Vec3d aim, int index) {
        return aim.subtract(origin).normalize().rotateY((float)Math.toRadians((index - 1) * 4));
    }
    public static boolean hovering(double clearance, boolean grounded, int ticks) {
        return !grounded && clearance > 2.5 && ticks >= HOVER_TICKS;
    }
}
