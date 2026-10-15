package com.anton.elementalwands.entity.roc;

/**
 * Starting values for phase one, to tune in playtests. Damage is raw: against vanilla full
 * diamond (20 armor, 8 toughness) SMALL_DAMAGE is about 4 hearts on Normal. World difficulty
 * scales it like the other bosses (Easy about halves it, Hard is x1.5); Normal is the intended fight.
 */
public final class StormRocRules {
    private StormRocRules() {}

    // Stage timing, in ticks. Easy and Hard lengthen or shorten only the air stretch.
    public static final int AIR_TICKS = 360, AIR_TICKS_EASY = 400, AIR_TICKS_HARD = 300;
    public static final int DIVE_TICKS = 60, STUCK_TICKS = 120, TAKEOFF_TICKS = 50;

    // Lightning: each strike warns, then hits the same circle with every bolt at full strength.
    public static final double SMALL_RADIUS = 2.5, LARGE_RADIUS = 5;
    public static final int SMALL_WARNING = 20, LARGE_WARNING = 30, BOLT_GAP = 10;
    public static final int SMALL_BOLTS = 2, LARGE_BOLTS = 3;
    /** Aimed strikes per player, then the summit-wide random strikes and the harmless distant bolts. */
    public static final int SMALL_EVERY = 40, LARGE_EVERY = 120, RANDOM_EVERY = 30, DISTANT_EVERY = 100;
    /** Random strikes fall anywhere within this radius of the centre; one in four is large. */
    public static final double RANDOM_RADIUS = 49;
    /** Aimed volleys never cover more than this many players; larger parties rotate through it. */
    public static final int AIMED_CAP = 4;

    // Tornadoes, the dive and shockwave rings.
    public static final double DIVE_RADIUS = 14, TORNADO_RADIUS = 5, TORNADO_CORE = 1.4;
    public static final double SHOCK_SPEED = 1.25, SHOCK_HEIGHT = .65, SAFE_RADIUS = 32;
    /** A caught player spins this long, then can't be caught again for TORNADO_IMMUNITY. */
    public static final int TORNADO_HOLD = 24, TORNADO_IMMUNITY = 60, TORNADO_CHIP_EVERY = 30;

    // Damage.
    public static final float SMALL_DAMAGE = 20, LARGE_DAMAGE = 31.25f, DIVE_DAMAGE = 50;
    public static final float PECK_DAMAGE = 23.2f, SCREAM_DAMAGE = 31.25f, WING_DAMAGE = 12.4f;
    public static final float POUNCE_DAMAGE = 35.9f, RING_DAMAGE = 16.5f, TORNADO_DAMAGE = 16.5f;
    public static final float STAMPEDE_DAMAGE = 31.25f, TORNADO_CHIP = 8, FOOT_DAMAGE = 14, CAW_DAMAGE = 31.25f;
    /**
     * Roc hits skip vanilla's hurt cooldown, so a repeat bolt half a second later always lands in
     * full. Inside this shorter window a new hit only adds its excess over the largest recent one:
     * overlapping circles, a crush and its ring, or a chip and a bolt never stack.
     */
    public static final int STACK_TICKS = 8;
    /** Feet moving faster than this per tick are stepping and hurt anyone under them. */
    public static final double STOMP_MOTION = .15, FOOT_RADIUS = 3.3;
    public static final int FOOT_COOLDOWN = 20;

    // Brawl.
    /** Hit radii around the bill at each attack's impact, and the scream's half width. */
    public static final double PECK_RADIUS = 6.2, SWING_RADIUS = 6.5, SCREAM_HALF_WIDTH = 2.5;
    /** Wing wave reach; a player closer than WING_REACH - 2 can draw it. */
    public static final double WING_REACH = 26;
    /** Hitting it from beyond this distance draws a stampede at that player. */
    public static final double RANGED_DISTANCE = 28;
    public static final int RANGED_MEMORY = 160, STAMPEDE_COOLDOWN = 160;
    /** It pounces at a target this far away, or after this many attacks in one spot. */
    public static final double POUNCE_FAR = 35;
    public static final int ACTIONS_BEFORE_POUNCE = 4;
    /** Degrees per tick it turns while winding up or flying between perches. */
    public static final float TURN_RATE = 15, FLIGHT_TURN_RATE = 10;
    /** A player's damage counts half as much toward being targeted after this many ticks. */
    public static final int THREAT_HALF_LIFE = 100;
    /** Every fighter keeps this much weight, so a quieter player is still picked sometimes. */
    public static final double THREAT_FLOOR = 30;

    public static final double LANDING_SHARE = .125, PHASE_GATE = .5;
    public static int health(int players) { return 2400 + Math.max(0, players - 1) * 1600; }

    public enum Stage { IDLE, AIR, DIVE, STUCK, BRAWL, TAKEOFF, COMPLETE }

    /**
     * impact: the tick its hit lands. lock: the tick it stops turning to follow its target
     * (swings aim where the target stood when chosen). duration includes recovery.
     */
    public enum Action {
        PECK("peck", 12, 8, 36),
        SWING_LEFT("swing_left", 16, 6, 36),
        SWING_RIGHT("swing_right", 16, 6, 36),
        SCREAM("scream", 24, 18, 44),
        WING("wing", 20, 0, 40),
        POUNCE("pounce", 44, 16, 64),
        STAMPEDE("stampede", 24, 20, 84);

        public final String pose;
        public final int impact, lock, duration;
        Action(String pose, int impact, int lock, int duration) {
            this.pose = pose; this.impact = impact; this.lock = lock; this.duration = duration;
        }
        public boolean swing() { return this == SWING_LEFT || this == SWING_RIGHT; }
    }
}
