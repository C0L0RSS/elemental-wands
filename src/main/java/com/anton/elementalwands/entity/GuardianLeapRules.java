package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Timings and volume shared by leap planning, damage, animation and the warning marker. */
public final class GuardianLeapRules {
    public static final int TAKEOFF = 14, FLIGHT = 36;
    public static final int LAND = TAKEOFF + FLIGHT, WAVE_DELAY = 6, RECOVERY = 44;
    /**
     * It follows its target through the wind-up and the first part of the flight, then commits here:
     * the landing ring is marked and stays put for the last {@code LAND - LOCK} ticks.
     */
    public static final int LOCK = LAND - 22;
    /** Blocks a tick the landing may slide across the floor once it is airborne. */
    public static final double STEER = .7;
    public static final double MIN_RANGE = 12, MAX_RANGE = 30, HEIGHT = 11;
    /** The marked ring: everyone inside it when it lands takes {@link #DAMAGE}. */
    public static final double IMPACT_RADIUS = 4.5;
    public static final float DAMAGE = 18;
    private GuardianLeapRules() {}

    private static int waveTick(int age) {
        return age >= 0 && age < Math.ceil(GuardianCombatRules.WAVE_RANGE/GuardianCombatRules.WAVE_SPEED) ? age : -1;
    }
    public static int launchWaveTick(int tick) { return waveTick(tick-TAKEOFF); }
    public static int landingWaveTick(int tick) { return waveTick(tick-LAND-WAVE_DELAY); }

    public static Box supportBox(Vec3d feet, double width) {
        double r=width/2-.03;
        return new Box(feet.x-r,feet.y-.08,feet.z-r,feet.x+r,feet.y+.001,feet.z+r);
    }

    public static Vec3d position(Vec3d start, Vec3d end, double progress) {
        double t = Math.clamp(progress, 0, 1);
        // Clear adjacent steps before translating the wide body; finish horizontal
        // travel above the landing site before descending between nearby obstacles.
        double horizontal = Math.clamp((t-.1)/.8,0,1);
        horizontal = horizontal*horizontal*(3-2*horizontal);
        return new Vec3d(start.x+(end.x-start.x)*horizontal,
                start.y+(end.y-start.y)*t+4*HEIGHT*t*(1-t),
                start.z+(end.z-start.z)*horizontal);
    }

    public static float damage(Vec3d landing, Box victim) {
        if (victim.minY > landing.y+3 || victim.maxY < landing.y-.5) return 0;
        double x = Math.clamp(landing.x,victim.minX,victim.maxX);
        double z = Math.clamp(landing.z,victim.minZ,victim.maxZ);
        double radius = Math.hypot(x-landing.x,z-landing.z);
        return radius <= IMPACT_RADIUS ? DAMAGE : 0;
    }
}
