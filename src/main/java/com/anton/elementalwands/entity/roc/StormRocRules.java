package com.anton.elementalwands.entity.roc;

/** Starting values for Phase One. Damage is raw, before vanilla diamond armor and difficulty. */
public final class StormRocRules {
    private StormRocRules() {}
    public static final int AIR_TICKS = 360, DIVE_TICKS = 60, STUCK_TICKS = 120, TAKEOFF_TICKS = 50;
    public static final double DIVE_RADIUS = 14, TORNADO_RADIUS = 5, TORNADO_CORE = 1.4;
    public static final double SHOCK_SPEED = 1.25, SHOCK_HEIGHT = .65, SAFE_RADIUS = 32;
    public static final float SMALL_DAMAGE = 20, LARGE_DAMAGE = 31.25f, DIVE_DAMAGE = 50;
    public static final float PECK_DAMAGE = 23.2f, SCREAM_DAMAGE = 31.25f, WING_DAMAGE = 12.4f;
    public static final float POUNCE_DAMAGE = 35.9f, RING_DAMAGE = 16.5f, TORNADO_DAMAGE = 16.5f;
    public static final double LANDING_SHARE = .125, PHASE_GATE = .5;
    public static int health(int players) { return 2400 + Math.max(0, players - 1) * 1600; }
    public enum Stage { IDLE, AIR, DIVE, STUCK, BRAWL, TAKEOFF, COMPLETE }
    public enum Action {
        PECK("peck", 12, 36), SWING_LEFT("swing_left",16,36), SWING_RIGHT("swing_right",16,36),
        SCREAM("scream",24,44), WING("wing",20,40), POUNCE("pounce",44,64), STAMPEDE("stampede",24,84);
        public final String pose;
        public final int impact, duration;
        Action(String pose,int impact,int duration) { this.pose=pose;this.impact=impact;this.duration=duration; }
    }
}
