package com.anton.elementalwands.entity;

import net.minecraft.util.math.Vec3d;

public final class WindFanRules {
    public static final double RANGE = 12;
    public static final int DASH_RECHARGE_TICKS = 100;
    private WindFanRules() {}
    public static Vec3d direction(Vec3d forward, int blade) {
        return forward.rotateY((float)Math.toRadians(blade*8)).normalize();
    }
    public static float damage(double distance) { return (float)(7-3*Math.clamp(distance/RANGE,0,1)); }
}
