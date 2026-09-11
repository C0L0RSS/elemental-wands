package com.anton.elementalwands.util;

/** Tuning and finite geometry shared by the server, renderer and regression checks. */
public final class StoneClusterRules {
    public static final int MAX_MASS = 100, PULL_MASS = 25, PULL_TICKS = 12;
    public static final int SMALL_COOLDOWN = 30, HEAVY_COOLDOWN = 50;
    public static final int STAGGER_MASS = 75, STAGGER_TICKS = 10, CHIP_GRACE_TICKS = 10;
    public static final double RANGE = 36, GATHER_RANGE = 4.5;
    private StoneClusterRules() {}
    public static int mass(int value) { return Math.clamp(value, 0, MAX_MASS); }
    public static float damage(int mass) { return mass <= 0 ? 5 : 8 + mass(mass) * .24f; }
    public static double weight(int mass) { return -.25 * mass(mass) / MAX_MASS; }
    public static double radius(int mass) { return mass <= 0 ? .18 : .35 + .0065 * mass(mass); }
    public static double speed(int mass) { return mass <= 0 ? 1.35 : 1.1 - .002 * mass(mass); }
    public static int chipLoss(float damage) {
        return !Float.isFinite(damage) || damage <= 0 ? 0 : Math.clamp((int)Math.ceil(damage * 3), 5, 30);
    }
    public static int afterDamage(int mass, float damage) {
        return mass <= 0 ? 0 : Math.max(1, mass(mass) - chipLoss(damage));
    }
    public static int cooldownRemaining(long elapsed, int duration, boolean entangled) {
        return (int)Math.max(0, duration - Math.max(0, elapsed) / (entangled ? 2 : 1));
    }
}
