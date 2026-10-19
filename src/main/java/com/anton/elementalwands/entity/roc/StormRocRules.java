package com.anton.elementalwands.entity.roc;

/**
 * Starting values for both phases, to tune in playtests. Damage is raw: against vanilla full
 * diamond (20 armor, 8 toughness) SMALL_DAMAGE is about 4 hearts on Normal. World difficulty
 * scales it like the other bosses (Easy about halves it, Hard is x1.5); Normal is the intended fight.
 */
public final class StormRocRules {
    private StormRocRules() {}

    // Stage timing, in ticks. Easy and Hard lengthen or shorten only the air stretch.
    /** Normal sees three calls, Easy three and an arrival, Hard two; see AIR_CYCLE. */
    public static final int AIR_TICKS = 394, AIR_TICKS_EASY = 450, AIR_TICKS_HARD = 300;
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

    /**
     * Each spire visit lasts AIR_CYCLE: it lands (LAND_TICKS), then CALL_AT into the visit it rears up
     * and calls a large volley down on every challenger CALL_VOLLEY into its call, which lasts
     * CALL_TICKS. It flies to the next spire at FLY_AT.
     */
    public static final int AIR_CYCLE = 150, LAND_TICKS = 16, CALL_AT = 34, CALL_VOLLEY = 10, CALL_TICKS = 60, FLY_AT = 106;

    // Tornadoes, the dive and shockwave rings.
    public static final double DIVE_RADIUS = 14, TORNADO_RADIUS = 5;
    /** A tornado catches anyone inside this share of its radius; its winds drag from this multiple of it. */
    public static final double TORNADO_CATCH = .45, TORNADO_REACH = 1.8;
    /**
     * Blocks a tornado moves per tick, a quarter of a walk. In the air stretch it turns slowly toward
     * the nearest challenger on the rock; in the brawl it only wanders.
     */
    public static final double TORNADO_SPEED = .055;
    /** A caught player is whirled this high up the funnel (at phase one's radius). */
    public static final double TORNADO_LIFT = 10;
    public static final double SHOCK_SPEED = 1.25, SHOCK_HEIGHT = .65, SAFE_RADIUS = 32;
    /** A caught player spins this long, then is thrown and can't be caught again for TORNADO_IMMUNITY. */
    public static final int TORNADO_HOLD = 36, TORNADO_IMMUNITY = 60, TORNADO_CHIP_EVERY = 30;

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
    public static final double STOMP_MOTION = .1, FOOT_RADIUS = 2;
    public static final int FOOT_COOLDOWN = 20;

    // Brawl.
    /** Hit radii around the bill at each attack's impact, and the scream's half width. */
    public static final double PECK_RADIUS = 4, SWING_RADIUS = 4.2, SCREAM_HALF_WIDTH = 2;
    /** Wing wave reach; a player closer than WING_REACH - 2 can draw it. */
    public static final double WING_REACH = 16;
    /** Hitting it from beyond this distance draws a stampede at that player. */
    public static final double RANGED_DISTANCE = 22;
    public static final int RANGED_MEMORY = 160, STAMPEDE_COOLDOWN = 160;
    /** It pounces at a target this far away, or after this many attacks in one spot. */
    public static final double POUNCE_FAR = 26;
    public static final int ACTIONS_BEFORE_POUNCE = 4;
    /** Degrees per tick it turns while winding up or flying between perches. */
    public static final float TURN_RATE = 15, FLIGHT_TURN_RATE = 10;
    /** A player's damage counts half as much toward being targeted after this many ticks. */
    public static final int THREAT_HALF_LIFE = 100;
    /** Every fighter keeps this much weight, so a quieter player is still picked sometimes. */
    public static final double THREAT_FLOOR = 30;

    public static final double LANDING_SHARE = .125, PHASE_GATE = .5;
    public static int health(int players) { return 2400 + Math.max(0, players - 1) * 1600; }

    // Phase two: the storm is on its side. Nowhere is safe and the ground is always live.
    /** The storm gathers this long between the phase-one takeoff and phase two's first air stretch. */
    public static final int CHARGE_TICKS = 160;
    /** It comes down to the players more often. */
    /** It dives from a spire, so a stretch runs on to the next perched moment after this. */
    public static final int AIR_TICKS_TWO = 300, AIR_TICKS_TWO_EASY = 394, AIR_TICKS_TWO_HARD = 244;
    /** Larger strikes that hit harder: about 5 and 9 hearts against diamond. */
    public static final double SMALL_RADIUS_TWO = 3.5, LARGE_RADIUS_TWO = 7;
    public static final float SMALL_DAMAGE_TWO = 23.2f, LARGE_DAMAGE_TWO = 33.6f;
    /** Random strikes fall more often, and keep falling through the stuck window and the brawl. */
    public static final int RANDOM_EVERY_TWO = 20, DISTANT_EVERY_TWO = 24;
    /** A large strike leaves its circle live: half a heart every half second to anyone on the ground. */
    public static final int LIVE_TICKS = 50, LIVE_EVERY = 10;
    public static final float LIVE_DAMAGE = 4.15f;
    /** Phase two's tornadoes are larger, and a third joins them. */
    public static final double TORNADO_RADIUS_TWO = 7;
    public static final int TORNADOES_TWO = 3;
    /**
     * The chain smash replaces the dive: SMASHES beak slams, each at a player. The first falls from
     * the sky after FIRST_SMASH_WARNING; each later one rips free after SMASH_PAUSE and hops at most
     * SMASH_HOP blocks, landing SMASH_WARNING after its circle appears. Only the last leaves it stuck.
     */
    public static final int SMASHES = 4, FIRST_SMASH_WARNING = 40, SMASH_WARNING = 24, SMASH_PAUSE = 8;
    public static final double SMASH_RADIUS = 8, SMASH_HOP = 25, SMASH_RING = 26;
    /** The first smash falls like the dive and is as deadly; the hops are about 6 hearts. */
    public static final float HOP_DAMAGE = 26.06f;

    public enum Stage { IDLE, AIR, DIVE, SMASH, STUCK, BRAWL, TAKEOFF, CHARGE, COMPLETE }

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
