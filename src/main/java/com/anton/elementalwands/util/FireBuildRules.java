package com.anton.elementalwands.util;

import net.minecraft.util.math.Vec3d;

/** Initial playtest values for the two optional Fire spells. */
public final class FireBuildRules {
    public static final double RANGE = 6, HEAT_PER_TICK = 1.25, COOL_PER_TICK = 1.5, UNLOCK_HEAT = 25;
    public static final int DAMAGE_INTERVAL = 10, HOP_COOLDOWN = 120, LEASE_TICKS = 6;
    public static final float FLAME_START_DAMAGE = 0.5f, FLAME_MAX_DAMAGE = 2.5f;
    public static final int FLAME_RAMP_TICKS = 60, IGNITE_SECONDS = 2;
    public static float flameDamage(int sprayingTicks) {
        return FLAME_START_DAMAGE + (FLAME_MAX_DAMAGE-FLAME_START_DAMAGE)*Math.clamp(sprayingTicks/(float)FLAME_RAMP_TICKS,0,1);
    }
    public static boolean inCone(Vec3d eye, Vec3d aim, Vec3d point) {
        var delta = point.subtract(eye);
        double along = delta.dotProduct(aim);
        return along >= 0 && along <= RANGE && delta.lengthSquared()-along*along <= Math.pow(0.25+along*0.32,2);
    }
    private FireBuildRules() {}
}
