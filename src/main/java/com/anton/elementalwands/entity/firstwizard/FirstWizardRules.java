package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.data.WizardAffinity;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The First Wizard's fight as data and pure rules: no world access, so the contract check can exercise all of it.
 * A table of its moves (the ten spells it casts from phase one on and the five ink moves of phase two, where each is
 * meant to be used and how much it weighs), the chooser that weighs them by where the target stands and what it just
 * did, which moves a phase may use, the opener, the scripted follow-ups and the gaps. What a move does is its own class
 * under {@code spell/}; when it is cast is decided here.
 */
public final class FirstWizardRules {
    private FirstWizardRules() {}

    // ── Tuning ───────────────────────────────────────────────────────────────

    /**
     * Raw damage on Normal; armor and Protection reduce it as usual. Sized like the Pyre King's for plain diamond to
     * netherite armor: there a heavy hit costs about 8 of 20 health (two leave a player alive, the third kills), a
     * medium one about 5 to 6, and a second on lingering ground about 2.
     */
    public static final float HEAVY = 21, MEDIUM = 16, GROUND = 8;
    /** Lingering ground arms this long after it appears, then burns on contact and once a second. */
    public static final int GROUND_ARM = 10, GROUND_EVERY = 20;
    /** The End's floor: every cell within this of its centre. A summoned copy uses the same round floor. */
    public static final double FLOOR_RADIUS = 44.5;
    /** It keeps itself, and the middle of everything it casts, this far inside the floor's edge. */
    public static final double EDGE = 1.5;
    /** A summoned copy fights Survival and Adventure players this close; it forgets them beyond the floor and this. */
    public static final double ENGAGE_RANGE = 34, ENCOUNTER_MARGIN = 12;
    /** Ticks with nobody to fight before a summoned copy resets. A copy fighting for the End waits for its encounter. */
    public static final int RESET_AFTER = 200;
    /** The middle distance it holds from its target while free. */
    public static final double HOLD_NEAR = 7, HOLD_FAR = 15;
    /** Blocks per second: a walk forward, a slower step back, a strafe. A sprinting player (5.6) outruns all three. */
    public static final double WALK = 4.3, BACK = 3.4, STRAFE = 3.2;
    /** Degrees per tick it can turn while free and while a spell forms. */
    public static final float TURN = 12, AIM_TURN = 18;
    /** Ticks between noticing a player and its first spell. */
    public static final int OPEN_DELAY = 30;
    /** Ticks it chases one player without casting before another gets its attention (co-op). */
    public static final int PATIENCE = 120;
    /** Players within this of it count as crowding it. */
    public static final double CROWD = 4.5;
    /** Ticks a target must stand still before it reads as standing still. */
    public static final int STILL_TICKS = 30;
    /** Phase two starts at this share of its health: it is held there until the change begins. */
    public static final float PHASE_TWO_AT = .5f;

    // ── Phase two ────────────────────────────────────────────────────────────

    /**
     * Phase two's body: it floats, holds closer to its target than in phase one, and drifts a little faster than it
     * walked (blocks per second). Still slower than a sprint (5.6).
     */
    public static final double HOLD_NEAR_TWO = 3.5, HOLD_FAR_TWO = 8;
    public static final double DRIFT = 5.0, BACK_TWO = 3.8, STRAFE_TWO = 3.6;
    /** How much of the way to the speed it wants a floating body gets each tick: it eases into and out of a drift. */
    public static final double DRIFT_EASE = .35;
    /** How high the floating body hovers: its drawn feet over the floor (the entity itself stays on the floor). */
    public static final double LIFT = .9;
    /** Inside this distance, phase two's tendrils dominate: the ten spells weigh this much of their weight. */
    public static final double CLOSE = 9, SPELLS_CLOSE = .3;
    /** Phase two's gaps between moves, as a share of phase one's. */
    public static final double GAP_TWO = .65;
    /** Hollow Purple is available only at or below this share of its health, and never sooner than this after the last. */
    public static final float PURPLE_AT = .25f;
    public static final int PURPLE_EVERY = 500;
    /** The finisher's hit: more than a heavy one (about 12 to 13 of 20 health in diamond or netherite). */
    public static final float PURPLE = 28;
    /** The flame: this raw damage every FLAME_EVERY ticks in the cone (about 4 health a second in diamond or netherite). */
    public static final float FLAME = 8;
    public static final int FLAME_EVERY = 10;
    /** Throws lead a moving target by its recent velocity, at most this far. */
    public static final double LEAD_MAX = 10;

    /** 1000 for one player, plus 60% for each player after the first (a first guess, like every boss's). */
    public static float health(int players) { return 1000 * (1 + .6f * (Math.max(1, players) - 1)); }

    // ── Moves ────────────────────────────────────────────────────────────────

    /** The five elements of its spells, and the ink of its phase-two body. */
    public enum Element { FIRE, WIND, STONE, NATURE, SPACE, INK }

    /**
     * Its moves: the ten spells in the order the design lists them (cast from phase one on), then the five ink moves of
     * phase two. near and far are the target distances a move is meant for (it is not weighed outside them); weight is
     * its share there. attack is false for a move that only moves the copy. phase is the first phase that uses it.
     * Whether a move can be cast at all is whether a class implements it ({@code BossSpells}).
     */
    public enum Move {
        FIREBALL("fireball", Element.FIRE, 6, 34, 5, true, 1),
        METEOR("meteor", Element.FIRE, 8, 40, 3, true, 1),
        SKY_SHEAR("sky_shear", Element.WIND, 4, 22, 4, true, 1),
        WAYLAY_DASH("waylay_dash", Element.WIND, 8, 30, 3, true, 1),
        STONE_WALL("stone_wall", Element.STONE, 6, 30, 2, true, 1),
        FAULTLINE("faultline", Element.STONE, 10, 26, 4, true, 1),
        THORNBITE("thornbite", Element.NATURE, 0, 14, 4, true, 1),
        SENTRY_BLOOM("sentry_bloom", Element.NATURE, 6, 40, 2, true, 1),
        BLINK_RIFT("blink_rift", Element.SPACE, 0, 90, 1, false, 1),
        GRAVITY_WELL("gravity_well", Element.SPACE, 6, 28, 3, true, 1),
        // Phase two: the tendrils up close, the finisher from the human hand.
        SLAM("slam", Element.INK, 0, 11, 6, true, 2),
        SWEEP("sweep", Element.INK, 0, 8.5, 6, true, 2),
        LEAP("leap", Element.INK, 5, 18, 5, true, 2),
        FLAME("flame", Element.INK, 0, 7, 2, true, 2),
        HOLLOW_PURPLE("hollow_purple", Element.INK, 6, 60, 8, true, 2);

        public final String id;
        public final Element element;
        public final double near, far, weight;
        public final boolean attack;
        public final int phase;

        Move(String id, Element element, double near, double far, double weight, boolean attack, int phase) {
            this.id = id; this.element = element; this.near = near; this.far = far; this.weight = weight; this.attack = attack; this.phase = phase;
        }

        /** One of the ten spells it casts from its wand hand (from phase one on), not an ink move. */
        public boolean spell() { return element != Element.INK; }

        public static Move byId(String id) {
            for (Move move : values()) if (move.id.equals(id)) return move;
            return null;
        }
    }

    /**
     * What the copy reads of its target when it chooses. distance is horizontal. crowded: another player (or this one)
     * stands within {@link #CROWD} of it. closing and fleeing: the target ran at it or away from it in the last half
     * second. still: it has not moved for {@link #STILL_TICKS}. airborne: off the ground. healing: eating or drinking.
     */
    public record Read(double distance, boolean crowded, boolean closing, boolean fleeing, boolean still, boolean airborne, boolean healing) {
        public static Read at(double distance) { return new Read(distance, false, false, false, false, false, false); }
    }

    /**
     * The weights of every available spell for this read. Distance decides which spells are meant for it; what the
     * target just did tilts them; chance picks within them. The same spell is never cast three times running, and
     * seldom twice.
     */
    public static Map<Move, Double> weights(Read read, Move last, Move before, Set<Move> available) {
        return weights(read, last, before, available, false);
    }

    /**
     * The same, for a phase. In phase two the ten spells, cast from its human hand, are mostly for range: within
     * {@link #CLOSE} they weigh {@link #SPELLS_CLOSE} of their weight and the tendrils dominate.
     */
    public static Map<Move, Double> weights(Read read, Move last, Move before, Set<Move> available, boolean phaseTwo) {
        Map<Move, Double> w = new EnumMap<>(Move.class);
        double d = read.distance();
        for (Move move : available) if (d >= move.near && d <= move.far)
            w.put(move, move.weight * (phaseTwo && move.spell() && d < CLOSE ? SPELLS_CLOSE : 1));
        // Crowded: it gets away. Blink Rift is meant for exactly this; Thornbite bites whoever is that close.
        if (read.crowded()) {
            tilt(w, Move.BLINK_RIFT, 12);
            tilt(w, Move.THORNBITE, 3);
        }
        // Running at it: something that meets them on the way in.
        if (read.closing()) {
            tilt(w, Move.FAULTLINE, 3);
            tilt(w, Move.SKY_SHEAR, 3);
            tilt(w, Move.STONE_WALL, 2);
        }
        // Running away: something that reaches or holds them.
        if (read.fleeing()) {
            tilt(w, Move.FIREBALL, 3);
            tilt(w, Move.WAYLAY_DASH, 3);
            tilt(w, Move.GRAVITY_WELL, 3);
        }
        // Standing still: the slow spells that punish it.
        if (read.still()) {
            tilt(w, Move.METEOR, 4);
            tilt(w, Move.FAULTLINE, 2);
        }
        // In the air: a throw finds them; a low crescent would only be jumped.
        if (read.airborne()) {
            tilt(w, Move.FIREBALL, 2);
            w.computeIfPresent(Move.SKY_SHEAR, (move, weight) -> weight * .3);
        }
        // Eating or drinking: the spells that punish standing in the open.
        if (read.healing()) {
            tilt(w, Move.FIREBALL, 4);
            tilt(w, Move.METEOR, 3);
            tilt(w, Move.WAYLAY_DASH, 3);
            tilt(w, Move.LEAP, 4);
        }
        // Phase two: running at it meets the low sweep; running away draws the leap after them; in the air, the slam.
        if (read.closing()) tilt(w, Move.SWEEP, 3);
        if (read.fleeing()) tilt(w, Move.LEAP, 4);
        if (read.airborne()) {
            tilt(w, Move.SLAM, 2);
            w.computeIfPresent(Move.SWEEP, (move, weight) -> weight * .3);
        }
        if (read.crowded()) tilt(w, Move.SWEEP, 4);
        if (last != null) {
            if (last == before) w.remove(last);
            else w.computeIfPresent(last, (move, weight) -> weight * .45);
        }
        return w;
    }

    /** Adds to a spell's weight only where it is already weighed: within its range and available. */
    private static void tilt(Map<Move, Double> w, Move move, double extra) { w.computeIfPresent(move, (m, weight) -> weight + extra); }

    /** The spell to cast now, or null when nothing fits (it keeps moving and looks again). */
    public static Move choose(Read read, Move last, Move before, Set<Move> available, DoubleSupplier random) {
        return pick(weights(read, last, before, available), random);
    }

    public static Move choose(Read read, Move last, Move before, Set<Move> available, boolean phaseTwo, DoubleSupplier random) {
        // The finisher is not left to chance: once it may come ({@link #usable} lets it through) and its target is in
        // its range, it is the next move. Its own wait keeps it rare.
        if (phaseTwo && available.contains(Move.HOLLOW_PURPLE) && read.distance() >= Move.HOLLOW_PURPLE.near
                && read.distance() <= Move.HOLLOW_PURPLE.far) return Move.HOLLOW_PURPLE;
        return pick(weights(read, last, before, available, phaseTwo), random);
    }

    /**
     * The moves the chooser may use now, out of those built: in phase one the ten spells; in phase two everything,
     * except that Hollow Purple waits until its health is at or below {@link #PURPLE_AT} of its maximum and for
     * {@link #PURPLE_EVERY} ticks after the last one began. sincePurple is ticks since the last one, or a large
     * number for none.
     */
    public static Set<Move> usable(Set<Move> built, boolean phaseTwo, float healthShare, long sincePurple) {
        Set<Move> out = java.util.EnumSet.noneOf(Move.class);
        for (Move move : built) {
            if (move.phase > (phaseTwo ? 2 : 1)) continue;
            if (move == Move.HOLLOW_PURPLE && !purpleReady(healthShare, sincePurple)) continue;
            out.add(move);
        }
        return out;
    }

    /** Whether the finisher may come now. */
    public static boolean purpleReady(float healthShare, long sincePurple) { return healthShare <= PURPLE_AT && sincePurple >= PURPLE_EVERY; }

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

    /** The spell each element opens with (the design: it opens with a spell from the first player's element). */
    public static Move preferredOpener(WizardAffinity affinity) {
        return switch (affinity) {
            case FIRE, NONE -> Move.FIREBALL;
            case WIND -> Move.SKY_SHEAR;
            case STONE -> Move.FAULTLINE;
            case NATURE -> Move.THORNBITE;
            case SPACE -> Move.GRAVITY_WELL;
        };
    }

    /**
     * The opening spell: the skin owner's element's, or, while that one is not built, the nearest available attack in
     * the table (the same element first, then by distance in the table's order, earlier first on a tie). Null when no
     * attack is available.
     */
    public static Move opener(WizardAffinity affinity, Set<Move> available) {
        Move preferred = preferredOpener(affinity);
        if (available.contains(preferred)) return preferred;
        Move best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Move move : Move.values()) {
            if (!move.attack || move.phase > 1 || !available.contains(move)) continue;
            int score = Math.abs(move.ordinal() - preferred.ordinal()) * 2 + (move.ordinal() > preferred.ordinal() ? 1 : 0)
                    + (move.element == preferred.element ? 0 : 100);
            if (score < bestScore) { bestScore = score; best = move; }
        }
        return best;
    }

    /** A scripted combination: after {@code first}, {@code then} follows with only a short gap, this often. */
    public record FollowUp(Move first, Move then, double chance) {}

    /**
     * The design's three combinations. Stone Wall cuts off a way out and the Fireball goes down the open lane; a
     * Meteor falls on a Gravity Well while it pulls everyone in; a Blink Rift behind the player sets up
     * Thornbite.
     */
    public static final List<FollowUp> FOLLOW_UPS = List.of(
            new FollowUp(Move.STONE_WALL, Move.FIREBALL, .75),
            new FollowUp(Move.GRAVITY_WELL, Move.METEOR, .5),
            new FollowUp(Move.BLINK_RIFT, Move.THORNBITE, .7),
            // Not one of the design's three: Faultline throws a player high, and half the time a Fireball meets the landing.
            new FollowUp(Move.FAULTLINE, Move.FIREBALL, .5),
            // The developer's phase-two combination: the leap lands, then it roots itself and breathes fire at whoever is close.
            new FollowUp(Move.LEAP, Move.FLAME, .75));

    /** The follow-up to cast straight after this one, or null. Only when the follow-up is available. */
    public static Move followUp(Move just, Set<Move> available, DoubleSupplier random) {
        if (just == null) return null;
        for (FollowUp combo : FOLLOW_UPS)
            if (combo.first() == just && available.contains(combo.then()) && random.getAsDouble() < combo.chance()) return combo.then();
        return null;
    }

    /** Ticks between a follow-up and the spell before it. */
    public static final int FOLLOW_GAP = 3;

    /**
     * Ticks it moves between spells: tight but real (0.45 to 0.9 s alone). Each extra player shortens them by 15%, up
     * to four players (the roadmap: co-op pressure grows with the party and stops growing at four).
     */
    public static int gap(int players, DoubleSupplier random) {
        double base = 9 + random.getAsDouble() * 9;
        return Math.max(4, (int) Math.round(base * pressure(players)));
    }

    /** The same in a phase: phase two's gaps are {@link #GAP_TWO} of phase one's. */
    public static int gap(int players, DoubleSupplier random, boolean phaseTwo) {
        int one = gap(players, random);
        return phaseTwo ? Math.max(4, (int) Math.round(one * GAP_TWO)) : one;
    }

    /** How much of a solo gap a party of this size gets. */
    public static double pressure(int players) { return 1 - .15 * (Math.min(4, Math.max(1, players)) - 1); }
}
