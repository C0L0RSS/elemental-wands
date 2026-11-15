package com.anton.elementalwands.entity;

import net.minecraft.util.math.Vec3d;

/**
 * The Airburst cast's formation: three committed bursts of three stones, as the {@code fan} clip shows them.
 * Shared by server release, client formation and timing checks; {@link GuardianAirburstRules} has the rest.
 */
public final class GuardianFanRules {
    public static final int COUNT = 3, BURSTS = 3, LOCK = 14, RELEASE = 18, REPEAT = 8,
            RECOVERY = 14;
    public static final double SPEED = 1.8, RANGE = 40, RADIUS = .32;
    private GuardianFanRules() {}
    public static int duration() { return RELEASE + (BURSTS - 1) * REPEAT + RECOVERY; }
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
}
