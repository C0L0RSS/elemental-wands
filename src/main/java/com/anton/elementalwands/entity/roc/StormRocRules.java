package com.anton.elementalwands.entity.roc;

/**
 * Starting values for both phases, to tune in playtests. Damage is raw: against vanilla full
 * diamond (20 armor, 8 toughness) SMALL_DAMAGE is about 4 hearts on Normal. World difficulty
 * scales it like the other bosses (Easy about halves it, Hard is x1.5); Normal is the intended fight.
 */
public final class StormRocRules {
    private StormRocRules() {}

    // Stage timing, in ticks. How long an air stretch lasts is the Roc's own choice; see
    // StormRocBrain.dives.
    public static final int DIVE_TICKS = 60, STUCK_TICKS = 120;
    /**
     * Its takeoff as the brawl ends: crouched, it turns onto its way out, and its feet leave the rock
     * at TAKEOFF_PUSH as its downbeat blasts everyone back. Its first beats lift it slowly, then it
     * flies out past its spire and banks round onto it (see RocApproach); LAND_LEAD before it arrives
     * it flares into its landing, or, bound for the phase change, flies on in the flight loop. Its
     * clip lasts TAKEOFF_TICKS - LAND_LEAD.
     */
    public static final int TAKEOFF_TICKS = 112, TAKEOFF_PUSH = 24;
    /**
     * The dive, and the chain smash's first slam, hold it on its spire for DIVE_PUSH while it crouches,
     * as the hop between spires does, then launch it; the impact stays at DIVE_TICKS. Stuck, it gathers
     * itself to rip free from STUCK_GATHER, crackling, and its bill comes loose at tick 100.
     */
    public static final int DIVE_PUSH = 8, STUCK_GATHER = 88;

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
     * The cage call rings a challenger with small strikes CAGE_RING radii out, warned for
     * CAGE_WARNING; the middle stays safe. The fork call's strikes fall at least FORK_GAP blocks
     * past a large strike's radius apart along the runner's way.
     */
    public static final double CAGE_RING = 2.4, FORK_GAP = 1.5;
    public static final int CAGE_WARNING = 26;
    /** After a call, aimed small strikes hold off this long, so nothing lands in a cage's middle. */
    public static final int CALL_HOLD = 45;
    /** A neighbouring spire must be this much nearer the challengers to turn it back the way it came. */
    public static final double HOP_MARGIN = 8;
    /**
     * The low pass. Crouched on its spire it turns onto a line through a challenger, then flies it:
     * down to PASS_HEIGHT blocks over the rock by the rim, across the summit at PASS_SPEED blocks a
     * tick (after gathering speed for PASS_RAMP ticks) and up onto the spire beyond. Lightning from
     * its bill walks the line PASS_FRONT blocks ahead of it: the whole lane is marked as it leaves
     * its spire, in circles of LANE_RADIUS every LANE_SPACING blocks, the first never sooner than
     * LANE_WARNING. Each player is hit once, on the rock or jumping, and its body stays within
     * spell reach the whole way across.
     */
    public static final double PASS_HEIGHT = 13, PASS_SPEED = 1.7, PASS_FRONT = 10, LANE_RADIUS = 3.5, LANE_SPACING = 4;
    public static final int PASS_RAMP = 24, LANE_WARNING = 30;
    /** How sharply it slows on its way up onto the far spire: higher brakes earlier and takes longer. */
    public static final double PASS_BRAKE = 1.6;

    /**
     * Each spire visit lasts AIR_CYCLE: it settles from its landing, then CALL_AT into the visit it
     * rears up and calls a large volley down on every challenger CALL_VOLLEY into its call, which
     * lasts CALL_TICKS. At FLY_AT it starts its hop to the next spire: it crouches there for SPIRE_PUSH,
     * turning onto its way out, then flies out to the side of the next spire and banks round onto it
     * (see RocApproach). LAND_LEAD before it arrives it flares into its landing, which touches down as
     * the next visit begins and runs until its call. The hop's clip lasts AIR_CYCLE - FLY_AT -
     * LAND_LEAD; the landing's, LAND_LEAD + CALL_AT. Its legs start reaching for the spire LAND_REACH
     * before the landing begins, in the last stretch of the hop or the takeoff.
     */
    public static final int AIR_CYCLE = 178, CALL_AT = 34, CALL_VOLLEY = 10, CALL_TICKS = 60, FLY_AT = 98, SPIRE_PUSH = 8, LAND_LEAD = 16, LAND_REACH = 9;

    // Tornadoes, the dive and shockwave rings.
    /**
     * The dive lands on its challengers, not on the summit's centre. From the middle of its circle
     * a sprint gets out with half a second to spare and a walk does not.
     */
    public static final double DIVE_RADIUS = 12, TORNADO_RADIUS = 5;
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
    /**
     * The fight opens with its summon on its home spire (art/storm_roc/build_runtime.py: SUMMON,
     * SUMMON_CALL): it screams a bolt into the clouds at SUMMON_SCREAM, the storm strikes the first
     * tornado's spot at SUMMON_CALL as the funnel starts to form, and it is back on its perch at
     * SUMMON_TICKS, when the air stretch begins.
     */
    public static final int SUMMON_TICKS = 88, SUMMON_CALL = 34, SUMMON_SCREAM = 30;
    /** A new tornado touches down from the cloud over this long, harmless until it has formed. */
    public static final int TORNADO_FORM = 50;
    /** A caught player spins this long, then is thrown and can't be caught again for TORNADO_IMMUNITY. */
    public static final int TORNADO_HOLD = 36, TORNADO_IMMUNITY = 60, TORNADO_CHIP_EVERY = 30;

    // Damage.
    public static final float SMALL_DAMAGE = 20, LARGE_DAMAGE = 31.25f, DIVE_DAMAGE = 50;
    public static final float SWING_DAMAGE = 23.2f, BITE_DAMAGE = 31.25f, SCREAM_DAMAGE = 31.25f, WING_DAMAGE = 16.5f;
    public static final float POUNCE_DAMAGE = 35.9f, RING_DAMAGE = 16.5f, TORNADO_DAMAGE = 16.5f;
    public static final float STAMPEDE_DAMAGE = 31.25f, TORNADO_CHIP = 8, FOOT_DAMAGE = 14, CAW_DAMAGE = 31.25f;
    public static final float LANE_DAMAGE = 31.25f, LANE_DAMAGE_TWO = 33.6f;
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
    public static final double BITE_RADIUS = 5, SWING_RADIUS = 5.5, SCREAM_HALF_WIDTH = 3;
    /** How hard the Storm Scream shoves everyone in its line back along it. */
    public static final double SCREAM_PUSH = 1.2;
    /**
     * The Storm Sweep: its bill scythes the front for SWING_SWEEP ticks from its impact, throwing
     * everyone it catches sideways (SWING_PUSH). As it ends, a crescent of crackles rolls out over
     * the swept front, SWEEP_WAVE_HALF degrees each side of ahead, from SWEEP_WAVE_START blocks to
     * SWEEP_WAVE_REACH at SHOCK_SPEED; anyone on the ground it reaches is hit.
     */
    public static final int SWING_SWEEP = 8;
    public static final double SWING_PUSH = 1.4, SWEEP_WAVE_START = 10, SWEEP_WAVE_REACH = 22, SWEEP_WAVE_HALF = 80;
    /**
     * The Thunder Bite: how hard its burst throws everyone it catches away from the Roc, and how far
     * its lunge can leap forward to bite where its prey stood when the aim locked.
     */
    public static final double BITE_PUSH = 1.2, BITE_LUNGE = 10;
    /**
     * When its prey is out of reach at the aim lock it leaps instead (the bite_leap clip), slower as
     * befits its size: it pushes off at BITE_LEAP (2.15 s), lands at BITE_LAND (2.75 s), bites at
     * BITE_LEAP_IMPACT and is done at BITE_LEAP_DURATION, where the standing bite uses its Action's.
     */
    public static final int BITE_LEAP = 43, BITE_LAND = 55, BITE_LEAP_IMPACT = 56, BITE_LEAP_DURATION = 88;
    /**
     * It chooses between the standing bite and the leap at BITE_DECIDE, where the two clips part.
     * The leap's aim follows on through its crouch and locks at BITE_LEAP_LOCK, as long before its
     * bite as the standing bite's.
     */
    public static final int BITE_DECIDE = 24, BITE_LEAP_LOCK = 36;
    /** How far beyond the standing bite's reach its prey must be for it to leap. */
    public static final double BITE_LEAP_MIN = 2;
    /**
     * The Thunderclap's ring rolls out to WING_REACH; a player closer than WING_REACH - 2 can draw it.
     * Anyone on the ground it reaches is thrown away from the Roc (WING_PUSH); a jump clears it.
     */
    public static final double WING_REACH = 16, WING_PUSH = 1.4;
    /**
     * The Thunderclap's leap: it leaves the rock at WING_LIFT, rises WING_HEIGHT blocks to its apex
     * at WING_APEX, hangs there to WING_DROP, then smashes down and lands on the Action's impact,
     * crushing anyone within WING_CRUSH of it under it before the ring rolls out.
     */
    public static final int WING_LIFT = 17, WING_APEX = 35, WING_DROP = 41;
    public static final double WING_HEIGHT = 9, WING_CRUSH = 5;
    /**
     * A player who hits it from beyond this distance, or stays beyond it for KEPT_AWAY ticks, is
     * keeping away, and draws the Thunder Charge and the Raptor Strike. The charge is offered from
     * CHARGE_MIN blocks.
     */
    public static final double RANGED_DISTANCE = 22, CHARGE_MIN = 16;
    public static final int RANGED_MEMORY = 160, STAMPEDE_COOLDOWN = 160, KEPT_AWAY = 100;
    /**
     * The Thunder Charge launches on the Action's impact and runs at STAMPEDE_SPEED blocks a tick,
     * its stride's pace, for as long as the charge lasts, trampling anyone within
     * STAMPEDE_HALF_WIDTH of its path. It steers toward its target at up to STAMPEDE_TURN degrees
     * a tick until it is within STAMPEDE_COMMIT blocks, then holds its line and runs on
     * STAMPEDE_PAST blocks beyond where its target stood (stopping short of the rim, and after
     * STAMPEDE_MAX_RUN ticks at most). It skids on the next stride boundary, STAMPEDE_SKID blocks
     * over STAMPEDE_SKID_TICKS, and is back in its guard STAMPEDE_AFTER ticks after the skid began.
     * Its clips match build_runtime.py: the windup hands over to the run loop at STAMPEDE_RUN_AT,
     * whose stride is STAMPEDE_STRIDE ticks, and the skid clip is STAMPEDE_AFTER ticks long.
     */
    public static final int STAMPEDE_RUN_AT = 53, STAMPEDE_STRIDE = 9, STAMPEDE_MAX_RUN = 90, STAMPEDE_SKID_TICKS = 16, STAMPEDE_AFTER = 38;
    public static final double STAMPEDE_HALF_WIDTH = 4.5, STAMPEDE_SKID = 5, STAMPEDE_SPEED = .77, STAMPEDE_COMMIT = 15, STAMPEDE_PAST = 8;
    public static final float STAMPEDE_TURN = 2;
    /** The Raptor Strike is likeliest at a target this far away, or after this many attacks in one spot. */
    public static final double POUNCE_FAR = 26;
    public static final int ACTIONS_BEFORE_POUNCE = 4;
    /**
     * The Raptor Strike's leap: it pushes off at POUNCE_LAUNCH and flies flat and fast on a low arc,
     * at most POUNCE_HEIGHT blocks up, onto where its prey stood when its aim locked; its talons
     * strike there on the Action's impact.
     */
    public static final int POUNCE_LAUNCH = 32;
    public static final double POUNCE_HEIGHT = 4, POUNCE_RADIUS = 5;
    /**
     * Degrees per tick its body turns on the rock, resting or winding up: 80 a second, so a player
     * who gets beside it is off its bill until it comes round, and only the Thunderclap answers
     * them. In flight it turns faster.
     */
    public static final float TURN_RATE = 4, FLIGHT_TURN_RATE = 10;
    /** Beyond this many degrees off its facing a player is beside or behind it. */
    public static final double FRONT = 55;
    /**
     * Its aims run ahead of a runner by how far they will have gone when the hit lands, at most
     * this many blocks, and stop following at the Action's lock: a straight line is hit, a sharp
     * turn after the lock is the dodge.
     */
    public static final double LEAD_CAP = 8;
    /** A player's damage counts half as much toward being targeted after this many ticks. */
    public static final int THREAT_HALF_LIFE = 100;
    /** Nobody goes this long without being attacked, whatever the threat says. */
    public static final int NEGLECT = 200;
    /** Targeting weights beside threat (damage points): eating, standing still, and each second unattacked. */
    public static final double HEALING_PRIORITY = 40, STILL_PRIORITY = 15, NEGLECT_PRIORITY = 4;
    /** A player counts as standing still after this many ticks slower than STILL_SPEED blocks a tick. */
    public static final int STILL_TICKS = 15;
    public static final double STILL_SPEED = .05;
    /** How long it remembers a spent dash, and a ring cleared by jumping. */
    public static final int DASH_MEMORY = 30, JUMP_MEMORY = 300;
    /**
     * Its rest after a string, solo on Normal (1.25 s, after the last clip's own recovery), and the
     * gap between two attacks of one string.
     */
    public static final int REST = 25, STRING_GAP = 4;

    public static final double LANDING_SHARE = .125, PHASE_GATE = .5;
    public static int health(int players) { return 2400 + Math.max(0, players - 1) * 1600; }

    // Phase two: the storm is on its side. Nowhere is safe and the ground is always live.
    /**
     * The storm gathers this long between the phase-one takeoff and phase two's first air stretch.
     * From CHARGE_DESCENT it flies down onto the tallest spire, out past it and round (see RocApproach).
     */
    public static final int CHARGE_TICKS = 208, CHARGE_DESCENT = 120;
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
     * the sky after FIRST_SMASH_WARNING. Each later one's circle appears as the last slam lands; it
     * stays buried for SMASH_PAUSE, then rips free and hops at most SMASH_HOP blocks over
     * SMASH_WARNING, so a hop's circle warns for both: long enough to sprint out of from its middle.
     * Only the last leaves it stuck.
     */
    public static final int SMASHES = 4, FIRST_SMASH_WARNING = 40, SMASH_WARNING = 24, SMASH_PAUSE = 14;
    public static final double SMASH_RADIUS = 8, SMASH_HOP = 25, SMASH_RING = 26;
    /** The first smash falls like the dive and is as deadly; the hops are about 6 hearts. */
    public static final float HOP_DAMAGE = 26.06f;

    public enum Stage { IDLE, SUMMON, AIR, DIVE, SMASH, STUCK, BRAWL, TAKEOFF, CHARGE, COMPLETE }

    /**
     * impact: the tick its hit lands. lock: the tick it stops turning to follow its aim, which runs
     * ahead of its target (sweeps aim once, when chosen). The bite's leap locks at BITE_LEAP_LOCK
     * and the Raptor Strike as it launches. duration includes recovery.
     */
    public enum Action {
        BITE("bite", 46, 26, 78),
        SWING_LEFT("swing_left", 25, 16, 52),
        SWING_RIGHT("swing_right", 25, 16, 52),
        SCREAM("scream", 32, 14, 58),
        WING("wing", 52, 0, 80),
        POUNCE("pounce", 52, 32, 80),
        // The Thunder Charge ends STAMPEDE_AFTER ticks after its skid starts; this is its longest run.
        STAMPEDE("stampede", 44, 36, STAMPEDE_RUN_AT + STAMPEDE_MAX_RUN + STAMPEDE_STRIDE + STAMPEDE_AFTER);

        public final String pose;
        public final int impact, lock, duration;
        Action(String pose, int impact, int lock, int duration) {
            this.pose = pose; this.impact = impact; this.lock = lock; this.duration = duration;
        }
        public boolean swing() { return this == SWING_LEFT || this == SWING_RIGHT; }
    }
}
