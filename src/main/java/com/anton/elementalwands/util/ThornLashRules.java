package com.anton.elementalwands.util;

import net.minecraft.util.math.Vec3d;

/** Provisional playtest tuning and shared server/client vine geometry. */
public final class ThornLashRules {
    public static final int COOLDOWN = 20;
    public static final int SWEEP_TICKS = 8;
    public static final int LIFETIME = 12;
    public static final double RANGE = 4.5;
    public static final float DAMAGE = 6;
    public static final float LIFESTEAL = .25f;
    public static final float HEAL_CAP = 2;
    public static final double WIDTH = .28;

    public static Vec3d point(float yaw, float pitch, double age, double reach) {
        double progress = Math.clamp(age / SWEEP_TICKS, 0, 1);
        double angle = Math.toRadians(-55 + 110 * progress) - .3 * (1 - reach);
        Vec3d forward = Vec3d.fromPolar(pitch, yaw);
        Vec3d side = Vec3d.fromPolar(0, yaw + 90);
        return forward.multiply(Math.cos(angle)).add(side.multiply(Math.sin(angle)))
                .multiply(.45 + (RANGE - .45) * reach);
    }

    private ThornLashRules() {}
}
