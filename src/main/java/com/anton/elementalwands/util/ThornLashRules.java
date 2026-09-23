package com.anton.elementalwands.util;

import net.minecraft.util.math.Vec3d;

/** Thornbite's committed tip trajectory. The decorative stem never deals damage. */
public final class ThornLashRules {
    public static final int COOLDOWN = 20;
    public static final int EXTEND_TICKS = 3;
    public static final int BITE_TICKS = 1;
    public static final int RETRACT_TICKS = 4;
    public static final int LIFETIME = EXTEND_TICKS + BITE_TICKS + RETRACT_TICKS;
    public static final double RANGE = 4.5;
    public static final float DAMAGE = 3;
    public static final float LIFESTEAL = .5f;
    public static final float HEAL_CAP = 2;
    public static final double WIDTH = .22;

    public static Vec3d tip(float yaw, float pitch, double progress) {
        progress = Math.clamp(progress, 0, 1);
        Vec3d forward = Vec3d.fromPolar(pitch, yaw);
        Vec3d up = Vec3d.fromPolar(pitch - 90, yaw);
        return forward.multiply(RANGE * progress).add(up.multiply(.20 * Math.sin(Math.PI * progress)));
    }
    private ThornLashRules() {}
}
