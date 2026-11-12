package com.anton.elementalwands.entity.pyreking;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * The Pyre King's fight as data and pure rules: no world access, so the contract checks can
 * exercise all of it. He is a hand-written rule system, as souls-like bosses are: a table of
 * moves (each a clip with its warning, the moments it can hit and when he is free again) and a
 * chooser that weighs them by where his target stands and what it just did.
 */
public final class PyreKingRules {
    private PyreKingRules() {}

    // ── Tuning ───────────────────────────────────────────────────────────────

    /**
     * Raw damage on Normal; armor and Protection reduce it as usual. Tuned for plain diamond to netherite armor, the
     * developer's target: there a sword blow takes about two fifths of a player's health (two leave them alive, the
     * third kills), the chain a little over a quarter, and a second in his fire about a tenth.
     */
    public static final float SWORD = 21, CHAIN = 16, FIRE = 8, UNBOUND_SWEEP = 19, YANK = 5, TUG = 4;
    /**
     * Blocks per second. A little slower than a sprinting player (5.6), the developer's rule, and well clear of one
     * backing away (4.3). It was 4.5 and 5.2 until their playtest (Nov 11) found him slow.
     */
    public static final double RUN = 5.3, RUN_TWO = 5.5;
    /** The speed his run clip strides at when played plainly; {@link #tempo} plays it faster to match RUN. */
    public static final double RUN_AUTHORED = 4.5;
    /** He stops closing inside this and holds his ground; beyond it he runs at his target. */
    public static final double HOLD = 3.4;
    /** Degrees per tick he can turn while free, so a quick player can get behind him for a moment. */
    public static final float TURN = 9, AIM_TURN = 14;
    public static final int ENGAGE_RANGE = 34, ENCOUNTER_RANGE = 72;
    /** How far from home he lets a fight carry him: the Burned Throne Hall's floor, less his own reach. */
    public static final double LEASH = 36;
    /** A combo is a run of blows less than this many ticks apart; a player takes one heavy hit per combo. */
    public static final int COMBO_GAP = 24;
    /** Ticks nobody eligible may be around before the fight resets. */
    public static final int RESET_AFTER = 200;
    /** Unbound starts at this share of his health; burst damage holds there until it has. */
    public static final float PHASE_GATE = .5f;
    /** Ticks behind him before he answers with the Turning Cut. */
    public static final int BEHIND_TICKS = 8;
    /** A target this far above his floor for this long draws Drag Down. */
    public static final double ELEVATED = 3;
    public static final int ELEVATED_TICKS = 30;
    public static final double DRAG_RANGE = 18;
    /** Floor fire: it arms this long after appearing, then burns on contact and once a second. */
    public static final int FIRE_ARM = 10, FIRE_EVERY = 20;
    /** A fire hit this soon after a weapon hit deals only what exceeds it. */
    public static final int FIRE_AFTER_WEAPON = 20;
    /** Ticks he chases one player without starting a move before another gets his attention (co-op). */
    public static final int PATIENCE = 120;
    /** Rings of fire where he will land (Zip, Lunge). */
    public static final double LANDING_RING = 5;
    /** Throws lead a moving target by its recent velocity, at most this far. */
    public static final double LEAD_MAX = 12;
    /** A Lunge, or a Haul with nothing to get round, lands this far short of where its target will be; a Zip this far short of the sword. */
    public static final double LUNGE_SHORT = 2, ZIP_SHORT = 1.1;

    public static float health(int players) { return 600 * (1 + .6f * (Math.max(1, players) - 1)); }

    // ── Moves ────────────────────────────────────────────────────────────────

    /** What part of him a window sweeps for hits. */
    public enum Part {
        /** The blade, pommel to tip. */
        BLADE,
        /** The chain from his wrist and the blade on its end. */
        CHAIN,
        /** Unbound's six chains, chest to hook. */
        CHAINS
    }

    /** Who a sweep can reach, by height. Heights are above his floor. */
    public enum Band {
        /** A cut: it reaches down to the floor under the blade, so only standing clear or above it is safe. */
        SOLID,
        /** A floor sweep: jump it. Feet at or above LOW_CLEAR are over it. */
        LOW,
        /** A head-height sweep: crouch under it. A body shorter than HIGH_UNDER is under it. */
        HIGH,
        /** A thrown sword: it hits only where it really is. */
        TRUE
    }

    public static final double LOW_CLEAR = .6, HIGH_UNDER = 1.55, HIGH_OVER = 3.2;

    /**
     * The Chain Scythe hurts only out where its blade swings: a body must reach this far from him (blocks, on the
     * floor). Inside it the low swing's chain runs at chest height and the high one's four blocks overhead, so
     * neither a jump nor a crouch could be read there; the developer's second playtest (Nov 11) found the Scythe
     * "impossible to dodge at close range". Close in, his sword is the threat, and stepping in under the chain is
     * a third way out of it.
     */
    public static final double SCYTHE_INNER = 5;

    /** Whether the Chain Scythe can strike a body whose middle is this far from him, this wide across. */
    public static boolean scytheReaches(double distance, double halfWidth) { return distance + halfWidth >= SCYTHE_INNER; }

    /**
     * Seconds into the clip the part can hit, how thick it is, and what it does. Each blow is its own group: it tests a
     * player once. A blow that a shield or a moment of immunity turned aside does not spare them the next; the combo
     * rule is what stops two heavy blows landing.
     */
    public record Window(double from, double to, Part part, Band band, double radius, float damage, int group) {}

    /** A real step toward his target during a blow: blocks covered between two moments of the clip. */
    public record Advance(double from, double to, double blocks) {}

    /** A limited re-aim before a follow-up blow: he may turn at most this far toward his target. */
    public record Retrack(double from, double to, float degrees) {}

    public enum Move {
        /** Two cuts across his front. He tracks until the snap, steps in, and the backhand turns toward the dodge. */
        ROYAL_CUT("royal_cut", .75, 2.95, new double[]{.55, 1.38},
                List.of(blade(.88, 1.17, Band.SOLID, .45, SWORD, 0), blade(1.73, 2.07, Band.SOLID, .45, SWORD, 1)),
                List.of(new Advance(.55, .9, 1.5)), List.of(new Retrack(1.3, 1.65, 45))),
        /** Phase two: the Royal Cut, then an overhead cut for whoever dodged both. */
        ROYAL_CUT_THREE("royal_cut_three", .75, 3.65, new double[]{.55, 1.38, 2.25},
                List.of(blade(.88, 1.17, Band.SOLID, .45, SWORD, 0), blade(1.73, 2.07, Band.SOLID, .45, SWORD, 1),
                        blade(2.6, 2.8, Band.SOLID, .9, SWORD, 2)),
                List.of(new Advance(.55, .9, 1.5), new Advance(2.52, 2.74, 1.4)),
                List.of(new Retrack(1.3, 1.65, 45), new Retrack(2.1, 2.5, 60))),
        /** A long thrust straight along the aim he froze at the snap. Sidestep it. */
        THRUST("kingsguard_thrust", .35, 1.85, new double[]{.35},
                List.of(blade(.8, .97, Band.SOLID, .75, SWORD, 0)), List.of(new Advance(.78, .93, 2.4)), List.of()),
        /** He spins and cuts the half behind him. */
        TURNING_CUT("turning_cut", 0, 1.75, new double[]{.25},
                List.of(blade(.68, .86, Band.SOLID, .45, SWORD, 0)), List.of(), List.of()),
        /** A straight overhead cut down the line in front of him. Sidestep it. */
        OVERHEAD("overhead_sentence", .5, 1.75, new double[]{.25},
                List.of(blade(.7, .87, Band.SOLID, .9, SWORD, 0)), List.of(new Advance(.64, .82, 2.2)), List.of()),
        /** The sword whirls round him at ankle height on its chain. Jump it. */
        SCYTHE_LOW("chain_scythe_low", .3, 2.1, new double[]{.2},
                List.of(new Window(.7, 1.17, Part.CHAIN, Band.LOW, .4, CHAIN, 0)), List.of(), List.of()),
        /** The sword whirls round him at head height. Crouch under it. */
        SCYTHE_HIGH("chain_scythe_high", .3, 2.1, new double[]{.2},
                List.of(new Window(.64, 1.15, Part.CHAIN, Band.HIGH, .4, CHAIN, 0)), List.of(), List.of()),
        /** He throws the sword, waits, and reels it back along its chain, or follows it with a Zip. */
        CAST("cast_reel", .72, 4.25, new double[]{.47, 2.8},
                List.of(new Window(.87, 1.26, Part.BLADE, Band.TRUE, .55, CHAIN, 0), new Window(3.48, 3.8, Part.CHAIN, Band.TRUE, .55, CHAIN, 1)),
                List.of(), List.of()),
        /** The chain hauls him to his stuck sword; he tears it free and spins into one cut. */
        ZIP("zip", 0, 2.55, new double[]{.0},
                List.of(blade(1.22, 1.64, Band.SOLID, .45, SWORD, 0)), List.of(), List.of()),
        /** A harpoon throw that drags whoever it catches to his feet, under the overhead cut. */
        HOOK("hook", .82, 2.9, new double[]{.6},
                List.of(new Window(1.0, 1.37, Part.BLADE, Band.TRUE, .65, TUG, 0)), List.of(), List.of()),
        /** He slings the sword short and flies after it into the same spinning cut. */
        LUNGE("lunge", .58, 2.85, new double[]{.45},
                List.of(blade(1.52, 1.94, Band.SOLID, .45, SWORD, 0)), List.of(), List.of()),
        /**
         * His way round a pillar: a quick low sling sends the sword far out past it, and the chain hauls him there at
         * once. It strikes nobody; the Snare follows it.
         */
        HAUL("haul", .2, .85, new double[]{.05}, List.of(), List.of(), List.of()),
        /** The Hook made in a hurry, straight off a Haul: whoever it catches is dragged to his feet, under the overhead cut. */
        SNARE("snare", .25, 2.3, new double[]{.05},
                List.of(new Window(.4, .77, Part.BLADE, Band.TRUE, .65, TUG, 0)), List.of(), List.of()),
        /** A rising sling at a player overhead, hauled down to the floor in front of him. */
        DRAG_DOWN("drag_down", .7, 1.75, new double[]{.2},
                List.of(new Window(.8, 1.07, Part.BLADE, Band.TRUE, .9, YANK, 0)), List.of(), List.of()),
        /** The phase change: six burning chains sweep the floor round him, then leave walls of fire. */
        UNBOUND("unbound", 0, 4.3, new double[]{1.2},
                List.of(new Window(1.62, 1.95, Part.CHAINS, Band.LOW, .4, UNBOUND_SWEEP, 0)), List.of(), List.of());

        public final String clip;
        /** He turns to keep his target in front until this moment; the aim is frozen from then on. */
        public final double aimUntil;
        /** When he may act again. Less than a second after his last blow: the opening is tight. */
        public final double free;
        /** The warnings: a chain snap or a hiss of embers before each blow. */
        public final double[] cues;
        public final List<Window> windows;
        public final List<Advance> advances;
        public final List<Retrack> retracks;

        Move(String clip, double aimUntil, double free, double[] cues, List<Window> windows, List<Advance> advances, List<Retrack> retracks) {
            this.clip = clip; this.aimUntil = aimUntil; this.free = free; this.cues = cues;
            this.windows = windows; this.advances = advances; this.retracks = retracks;
        }

        public boolean swordString() { return this == ROYAL_CUT || this == ROYAL_CUT_THREE || this == THRUST || this == TURNING_CUT || this == OVERHEAD; }
        public boolean chainMove() { return this == SCYTHE_LOW || this == SCYTHE_HIGH || this == CAST || this == HOOK || this == ZIP || this == HAUL || this == SNARE; }
        /** The Hook and the Snare: a thrown sword that catches whoever it strikes and drags them in. */
        public boolean hooks() { return this == HOOK || this == SNARE; }
        /** How much sooner than the Hook this does everything from the throw on. */
        public double hookLead() { return this == SNARE ? SNARE_LEAD : 0; }
        /** The end of his last blow, which the opening is measured from. A move with no blow has none until it ends. */
        public double lastBlow() { return windows.isEmpty() ? free : windows.get(windows.size() - 1).to(); }
        /** How fast this move's clip is played, part by part. */
        public Tempo tempo() { return PyreKingRules.tempo(clip); }
        /** Seconds after the move began at which this moment of its clip shows. */
        public double seconds(double moment) { return tempo().seconds(moment); }
        /** Seconds from the end of his last blow until he may act again: the opening he leaves. */
        public double opening() { return seconds(free) - seconds(lastBlow()); }
    }

    // ── Tempo ────────────────────────────────────────────────────────────────

    /**
     * How fast a clip is played, part by part. The clips are baked art; this is the dial that sets the fight's pace
     * without baking them again. Every time in the move table is a moment of its clip, so a blade is still swept
     * exactly where it is drawn; the server and every client read the same map.
     */
    public static final class Tempo {
        public static final Tempo PLAIN = new Tempo(new double[]{0}, new double[]{1});
        private final double[] at, rate, real;

        private Tempo(double[] at, double[] rate) {
            this.at = at; this.rate = rate;
            real = new double[at.length];
            for (int i = 1; i < at.length; i++) real[i] = real[i - 1] + (at[i] - at[i - 1]) / rate[i - 1];
        }

        /** Pairs of (moment of the clip, the rate it plays at from there until the next moment); the first moment is 0. */
        static Tempo of(double... pairs) {
            double[] at = new double[pairs.length / 2], rate = new double[pairs.length / 2];
            for (int i = 0; i < at.length; i++) {
                at[i] = pairs[i * 2]; rate[i] = pairs[i * 2 + 1];
                if (rate[i] <= 0 || (i == 0 ? at[i] != 0 : at[i] <= at[i - 1])) throw new IllegalArgumentException("A tempo runs forward from 0");
            }
            return new Tempo(at, rate);
        }

        /** The moment of the clip that shows this many seconds after it began. Before it begins, the same negative time. */
        public double clip(double seconds) {
            if (seconds <= 0) return seconds;
            int i = at.length - 1;
            while (i > 0 && real[i] > seconds) i--;
            return at[i] + (seconds - real[i]) * rate[i];
        }

        /** Seconds after the clip began at which this moment of it shows. */
        public double seconds(double clip) {
            if (clip <= 0) return clip;
            int i = at.length - 1;
            while (i > 0 && at[i] > clip) i--;
            return real[i] + (clip - at[i]) / rate[i];
        }

        public boolean plain() { return this == PLAIN; }
    }

    private static final double STRING = 1.12, RECOVER = 1.6;
    /**
     * The pace of each clip (the developer's playtest, Nov 11: "his overall speed in terms of the speed between his
     * attacks just has to be faster"; the thrown sword "was very slow"; and the pause before the spinning cut "was
     * really easy to get out" of). A clip not named here plays plainly: the Haul and the Snare, which they liked,
     * Drag Down, Unbound and his intro.
     * <ul>
     * <li>Sword strings: a little quicker throughout, and the return to his guard after the last blow much quicker.
     * <li>The Cast: a quicker wind-up, and the 2.2 s he stood with the sword out is under half a second.
     * <li>The Zip and the Lunge: the wait before the chain takes him is cut, and the pause between landing and the
     *     spinning cut is a tenth of a second, not a quarter.
     * <li>The Hook: a quicker wind-up and haul.
     * </ul>
     */
    private static final Map<String, Tempo> TEMPO = Map.ofEntries(
            Map.entry("run", Tempo.of(0, RUN / RUN_AUTHORED)),
            Map.entry("royal_cut", Tempo.of(0, STRING, 2.07, RECOVER)),
            Map.entry("royal_cut_three", Tempo.of(0, STRING, 2.8, RECOVER)),
            Map.entry("kingsguard_thrust", Tempo.of(0, STRING, .97, RECOVER)),
            Map.entry("turning_cut", Tempo.of(0, STRING, .86, RECOVER)),
            Map.entry("overhead_sentence", Tempo.of(0, STRING, .87, RECOVER)),
            Map.entry("chain_scythe_low", Tempo.of(0, 1.1, 1.17, RECOVER)),
            Map.entry("chain_scythe_high", Tempo.of(0, 1.1, 1.15, RECOVER)),
            // wind-up, flight, the wait with the sword out, the pull-back, his guard
            Map.entry("cast_reel", Tempo.of(0, 1.4, .87, 1.25, 1.3, 4.9, 3.45, 1.2, 3.8, 1.5)),
            // the wait, the flight, landing to the cut, the cut, his guard
            Map.entry("zip", Tempo.of(0, 3, .6, 1.2, 1.05, 2.2, 1.22, 1.15, 1.64, RECOVER)),
            // the tell and throw, the flight, landing to the cut, the cut, his guard
            Map.entry("lunge", Tempo.of(0, 1.3, .9, 1.2, 1.3, 2.2, 1.52, 1.15, 1.94, RECOVER)),
            // wind-up, flight, the haul in, his guard
            Map.entry("hook", Tempo.of(0, 1.35, 1, 1.15, 1.37, 1.3, 2.42, 1.5)));

    public static Tempo tempo(String clip) { return TEMPO.getOrDefault(clip, Tempo.PLAIN); }

    private static Window blade(double from, double to, Band band, double radius, float damage, int group) {
        return new Window(from, to, Part.BLADE, band, radius, damage, group);
    }

    // ── The Cast's second half, the Hook's catch and the phase change ────────

    /** The Cast: seconds in at which he decides between the Reel and a Zip, and at which a Zip takes over the clip. */
    public static final double CAST_DECIDE = 2.75, CAST_ZIP_AT = 2.8;
    /** He Zips when his target is this far from him at the decision, or always after a missed Hook's wait. */
    public static final double ZIP_BEYOND = 12;
    /** And this often when they are nearer: the chain exists to bring the fight back to sword range. */
    public static double zipChance(boolean second) { return second ? .6 : .35; }
    /**
     * Hiding. His own answers to a target behind a pillar (the developer, Nov 11: he gets round it himself, the hall
     * does not help him, and nothing of his passes through the stone). Out of his sight this many ticks, or this
     * many while eating or drinking, and he flanks. One who is behind the stone again as a flank ends waits only
     * FLANK_AGAIN more.
     */
    public static final int FLANK_AFTER = 30, FLANK_HEALING = 8, FLANK_AGAIN = 6;
    /** A Haul or a Lunge is drawn from where it began, so he first turns onto the line of his throw: degrees a tick. */
    public static final float FLANK_TURN = 30;
    /** The Snare is thrown as he turns on his target from wherever the Haul set him down: degrees a tick, until its aim freezes. */
    public static final float SNARE_TURN = 36;
    /** The Snare is the Hook this many seconds early from the throw on. */
    public static final double SNARE_LEAD = .6;
    /**
     * The Snare wastes no time on a target who got back behind the stone. Until the sword leaves his hand he keeps it
     * if he loses sight of them, and is free this long after; a throw that caught nobody is whipped straight back,
     * from SNARE_MISSED into the clip, and he is free this long after that.
     */
    public static final double SNARE_THROWN = .4, SNARE_KEPT = .3, SNARE_MISSED = .85, SNARE_RECALL = .35;
    /** The Snare's point goes this far past where its target will be, so one running away from him is still on the blade. */
    public static final double SNARE_PAST = 2.5;
    /** How far a hiding target would have to go to be out of his sight again is measured out to these distances round them. */
    private static final double[] COVER_RINGS = {1.5, 3, 4.5, 6};
    public static final double COVER_NONE = 8;
    public static double[] coverRings() { return COVER_RINGS.clone(); }

    /**
     * Where a flank may set him down: on one of these rings round his target, with his sword stuck between `near`
     * and `far` blocks from him. A wide one wants open ground with a long view of them; the other wants the nearest
     * spot beside them. (A Cast and a Zip were tried as a third and dropped: four seconds of him, which a player
     * circling the pillar simply outlasts.)
     */
    public record FlankRule(double[] rings, double near, double far, boolean wide) {}

    public static FlankRule flankRule(Move opener) {
        return switch (opener) {
            // Far out, where he can see them and they are a long way from cover: the Snare reaches from there.
            case HAUL -> new FlankRule(new double[]{8, 10, 12, 14, 16}, 12, 30, true);
            // Down beside them, into the spinning cut.
            default -> new FlankRule(new double[]{3, 4.5, 5.5}, 4.5, 16, false);
        };
    }

    /** The flanks open to him now, weighed: the Haul most, the Lunge most for a target who is healing, and seldom the same twice. */
    public static Map<Move, Double> flankWeights(boolean haul, boolean lunge, boolean healing, Move last) {
        Map<Move, Double> w = new EnumMap<>(Move.class);
        if (haul) w.put(Move.HAUL, 6.0);
        if (lunge) w.put(Move.LUNGE, healing ? 8.0 : 3.0);
        if (last != null && w.size() > 1) w.computeIfPresent(last, (move, weight) -> weight * .45);
        return w;
    }

    /** Phase two throws again after the Reel: the Double Cast. */
    public static final double CAST_AGAIN_AT = 3.95;
    /** The Hook drags its catch until this moment, to this far in front of him, then the Overhead follows. */
    public static final double HOOK_DRAG_UNTIL = 2.3, HOOK_HOLD = 4, HOOK_FOLLOW_AT = 2.42;
    /** A dragged player this far from where the chain has them has broken free (a movement spell). */
    public static final double HOOK_BREAK = 3.5;
    /** Drag Down pulls its catch to the floor by this moment. */
    public static final double DRAG_LAND = 1.3;
    /** Unbound: the hooks catch and the six lines of fire are lit; he cannot be hurt until the clip ends. */
    public static final double UNBOUND_WALLS_AT = 2.7, UNBOUND_REACH = 12;
    /** The Royal Cut's reads: after the first blow he may break off into another string. */
    public static final double ROYAL_BRANCH_AT = 1.25, ROYAL_THIRD_AT = 2.12;

    // ── Fire he leaves ───────────────────────────────────────────────────────

    public static final int FIRE_SHORT = 60, FIRE_LONG = 80;
    public static final double FIRE_LOW = 1, FIRE_TALL = 2;
    /** Half the width of a line of fire, and the radius of the fire where a Cast lands. */
    public static final double FIRE_HALF = .6, CAST_FIRE = 1.6;
    /** The line a Haul flies along burns this long, in both phases: low in the first, tall in the second. */
    public static final int HAUL_FIRE = 80;
    /** Phase two's third Royal Cut leaves a line of fire this long. */
    public static final double CUT_FIRE = 12;

    // ── The chooser ──────────────────────────────────────────────────────────

    /** What he knows about his target when he picks. Distances are horizontal, in blocks. */
    public record Read(double distance, boolean elevated, boolean behind, boolean backedAway, boolean jumped,
                       boolean healing, boolean sight, boolean second) {}

    /**
     * He waits this many ticks, closing on his target, between strings: 0.15 to 0.4 s, and next to nothing in phase
     * two. It was 0.3 to 0.8 s and 0.1 to 0.5 s until the developer's playtest (Nov 11) found him slow between attacks.
     */
    public static int gap(boolean second, DoubleSupplier random) {
        return second ? (int) (random.getAsDouble() * 4) : 3 + (int) (random.getAsDouble() * 6);
    }

    /** How likely he is to go straight into another string with no gap, and how many he may chain. */
    public static double chainChance(boolean second) { return second ? .75 : .5; }
    public static int chainLimit(boolean second) { return second ? 4 : 3; }
    /** A move that carries him to his target is followed at once, with no gap. */
    public static boolean closes(Move move) { return move == Move.ZIP || move == Move.LUNGE; }

    /**
     * The weights of every move he could open with now. Distance decides the family (sword strings
     * close, the Scythe in the middle, throws far away); what the target just did tilts it; chance
     * picks within it, so the same spot does not always draw the same move.
     */
    public static Map<Move, Double> weights(Read read, Move last, Move before) {
        Map<Move, Double> w = new EnumMap<>(Move.class);
        double d = read.distance();
        if (read.elevated()) {
            if (d <= DRAG_RANGE) w.put(Move.DRAG_DOWN, 1.0);
            return w;       // Out of reach: he closes in underneath first.
        }
        // He swings at nothing he cannot see: a pillar shelters whoever is behind it from every blow of his. Getting
        // round it is the flank's work.
        if (!read.sight()) return w;
        Move cut = read.second() ? Move.ROYAL_CUT_THREE : Move.ROYAL_CUT;
        if (d <= 6.5) {
            w.put(cut, 5.0);
            w.put(Move.OVERHEAD, 2.0);
            if (d > 3) w.put(Move.THRUST, 2.0);
            // No Scythe this close: it could not touch them (SCYTHE_INNER).
            if (read.behind()) w.put(Move.TURNING_CUT, 14.0);
            if (read.backedAway() && d > 3) w.merge(Move.THRUST, 5.0, Double::sum);
        } else if (d <= 14) {
            w.put(Move.SCYTHE_LOW, read.jumped() ? 1.0 : 5.0);
            w.put(Move.SCYTHE_HIGH, read.jumped() ? 7.0 : 2.0);
            w.put(Move.LUNGE, read.second() ? 4.0 : 3.0);
            if (d <= 9.5) w.put(Move.THRUST, read.backedAway() ? 9.0 : 3.0);
            if (d <= 8) w.put(Move.OVERHEAD, 1.5);
            if (read.healing()) {
                w.merge(Move.LUNGE, 7.0, Double::sum);
                if (d <= 9.5) w.merge(Move.THRUST, 5.0, Double::sum);
            }
        } else if (d <= 26) {
            // A player who keeps their distance is brought to sword range: the Lunge and the Hook weigh more than they did.
            w.put(Move.CAST, read.healing() ? 12.0 : 5.0);
            w.put(Move.LUNGE, read.healing() ? 6.0 : 4.0);
            w.put(Move.HOOK, read.second() ? 3.0 : 2.0);
        } else {
            w.put(Move.CAST, 2.0);
            w.put(Move.LUNGE, 3.0);
            w.put(Move.HOOK, 6.0);
        }
        // Never the same string three times running, and seldom twice.
        if (last != null && w.size() > 1) {
            if (last == before) w.remove(last);
            else w.computeIfPresent(last, (move, weight) -> weight * .45);
        }
        return w;
    }

    public static Move choose(Read read, Move last, Move before, DoubleSupplier random) {
        return pick(weights(read, last, before), random);
    }

    /** A punish for eating or drinking in the open: the same weights, but he does not wait out his gap. */
    public static boolean punishes(Read read) { return read.healing() && read.distance() > 5 && !read.elevated(); }

    static Move pick(Map<Move, Double> weights, DoubleSupplier random) {
        double total = 0;
        for (double weight : weights.values()) total += weight;
        if (total <= 0) return null;
        double roll = random.getAsDouble() * total;
        Move chosen = null;
        for (Map.Entry<Move, Double> entry : weights.entrySet()) {
            chosen = entry.getKey();
            roll -= entry.getValue();
            if (roll < 0) break;
        }
        return chosen;
    }

    /** After the Royal Cut's first blow: the string he breaks off into, or null to carry on with the backhand. */
    public static Move royalBranch(double distance, double degreesOffFront, boolean second, DoubleSupplier random) {
        if (distance <= 6 && Math.abs(degreesOffFront) > 110) return Move.TURNING_CUT;
        if (distance > 6.5 && distance <= 9.5 && Math.abs(degreesOffFront) < 50 && random.getAsDouble() < (second ? .8 : .5)) return Move.THRUST;
        return null;
    }

    // ── Geometry the sweep uses ──────────────────────────────────────────────

    /** Whether a body standing from feet to top (above his floor) is in a sweep's band, the weapon being between low and high. */
    public static boolean inBand(Band band, double feet, double top, double low, double high) {
        return switch (band) {
            // Both reach the floor he stands on and no deeper: a player in a pit or a cave under him is out of them.
            case SOLID -> feet < high + .5 && top > -.5;
            case LOW -> feet < LOW_CLEAR && top > -.5;
            case HIGH -> top - feet > HIGH_UNDER && top > HIGH_UNDER && feet < HIGH_OVER;
            case TRUE -> feet < high + .4 && top > low - .4;
        };
    }

    /** The squared distance, on the floor plane, from a point to the quad a moving segment sweeps (a0→b0 then a1→b1). */
    public static double sweptDistanceSquared(double px, double pz, double a0x, double a0z, double b0x, double b0z,
                                              double a1x, double a1z, double b1x, double b1z) {
        return Math.min(triangle(px, pz, a0x, a0z, b0x, b0z, b1x, b1z), triangle(px, pz, a0x, a0z, b1x, b1z, a1x, a1z));
    }

    private static double triangle(double px, double pz, double ax, double az, double bx, double bz, double cx, double cz) {
        double d1 = cross(bx - ax, bz - az, px - ax, pz - az), d2 = cross(cx - bx, cz - bz, px - bx, pz - bz),
                d3 = cross(ax - cx, az - cz, px - cx, pz - cz);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0, positive = d1 > 0 || d2 > 0 || d3 > 0;
        if ((negative || positive) && !(negative && positive)) return 0;      // inside, or on an edge
        // Otherwise outside it, or the triangle has no area (a weapon that stood still): the nearest edge.
        return Math.min(segment(px, pz, ax, az, bx, bz), Math.min(segment(px, pz, bx, bz, cx, cz), segment(px, pz, cx, cz, ax, az)));
    }

    private static double cross(double ax, double az, double bx, double bz) { return ax * bz - az * bx; }

    static double segment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, length = dx * dx + dz * dz;
        double f = length < 1e-12 ? 0 : Math.clamp(((px - ax) * dx + (pz - az) * dz) / length, 0, 1);
        double x = ax + dx * f - px, z = az + dz * f - pz;
        return x * x + z * z;
    }
}
