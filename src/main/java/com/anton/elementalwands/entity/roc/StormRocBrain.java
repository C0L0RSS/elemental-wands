package com.anton.elementalwands.entity.roc;

import static com.anton.elementalwands.entity.roc.StormRocRules.*;

import com.anton.elementalwands.summit.StormSummitLayout;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntToDoubleFunction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/**
 * What the Roc decides, independent of a running world. Like the Guardian and the Pyre King it is a
 * hand-written rule system: where its target stands and which way its body faces decide what it
 * can reach, what the target just did tilts the weights, and chance picks among them. It is the
 * raptor: it watches, the storm herds, and it commits on whoever is predictable. Its body turns
 * slowly, so getting beside it takes its bill away; its aims run ahead of a runner and lock late,
 * so a straight line is hit and a sharp turn is the dodge.
 */
public final class StormRocBrain {
    private StormRocBrain() {}

    /** Straight-ahead distances from its feet that a head attack's bill can reach. */
    public record Reach(double min, double max) {
        public boolean has(double distance) { return distance >= min && distance <= max; }
    }

    /**
     * Measured from the shipped sockets, so a retimed or reposed clip keeps the choices honest. A
     * one-block margin leaves room for a target drifting through the windup.
     */
    public static final class Reaches {
        /** The bite closes this far ahead of its feet, standing or after a leap that carries it up to BITE_LUNGE further. */
        public static final double BITE_AT = flat(StormRocPose.socket(Action.BITE.pose, Action.BITE.impact, "beak")).length();
        public static final double BITE_LEAP_AT = flat(StormRocPose.socket("bite_leap", BITE_LEAP_IMPACT, "beak")).length();
        public static final Reach BITE = new Reach(BITE_AT - (BITE_RADIUS - 1), Math.max(BITE_AT, BITE_LEAP_AT) + BITE_LUNGE + BITE_RADIUS - 1);
        public static final Reach SWING = reach(Action.SWING_LEFT.pose, Action.SWING_LEFT.impact, Action.SWING_LEFT.impact + SWING_SWEEP, SWING_RADIUS - 1);
        public static final double SCREAM_START = flat(StormRocPose.socket(Action.SCREAM.pose, Action.SCREAM.impact, "beak")).length();
        private Reaches() {}
    }

    /** Model space faces -Z, so a target straight ahead at distance d sits at (0, -d). */
    private static Reach reach(String pose, int from, int to, double radius) {
        double min = Double.MAX_VALUE, max = -1;
        for (double d = 0; d <= 60; d += .25) {
            for (int t = from; t <= to; t++) {
                Vec3d bill = StormRocPose.socket(pose, t, "beak");
                if (Math.hypot(bill.x, bill.z + d) > radius) continue;
                min = Math.min(min, d);
                max = Math.max(max, d);
                break;
            }
        }
        return new Reach(min, max);
    }

    private static Vec3d flat(Vec3d v) { return new Vec3d(v.x, 0, v.z); }

    // ── Aim ──────────────────────────────────────────────────────────────────

    /**
     * Where a player running steadily will be in this many ticks. The lead is capped, so a dash
     * or a throw cannot fling an aim across the summit.
     */
    public static Vec3d lead(Vec3d position, Vec3d velocity, int ticks) {
        Vec3d offset = new Vec3d(velocity.x, 0, velocity.z).multiply(Math.max(0, ticks));
        if (offset.length() > LEAD_CAP) offset = offset.normalize().multiply(LEAD_CAP);
        return position.add(offset);
    }

    /**
     * How far off its facing (degrees) a target may stand for it to bring this attack round in
     * time: it turns TURN_RATE a tick until the aim locks, less a margin for a target still moving.
     */
    public static double turnBudget(Action action) {
        return action == Action.STAMPEDE ? 180 : action == Action.WING ? 180 : action.lock * TURN_RATE - 10;
    }

    // ── Who it turns on ──────────────────────────────────────────────────────

    /** A player it could attack. Threat is the damage they dealt it recently, decayed. */
    public record Candidate(UUID id, double distance, double threat, boolean still, boolean healing) {}

    /**
     * Who it turns on. Anyone left alone for NEGLECT ticks comes first. Otherwise the highest
     * priority wins: recent damage dealt, eating, standing still, and time since it last attacked
     * them, with distance breaking ties, so equal threat still rotates through the party.
     * {@code avoid} (whom it just attacked, inside a string) is skipped when someone else qualifies.
     * A player it has not weighed before joins {@code lastTargeted} as of now, so their NEGLECT
     * ticks run from the first time it could have attacked them.
     */
    public static Candidate focus(List<Candidate> players, Map<UUID, Long> lastTargeted, long now, UUID avoid) {
        for (var p : players) lastTargeted.putIfAbsent(p.id(), now);
        List<Candidate> pool = players;
        if (avoid != null && pool.stream().anyMatch(p -> !p.id().equals(avoid)))
            pool = pool.stream().filter(p -> !p.id().equals(avoid)).toList();
        Comparator<Candidate> ties = Comparator.<Candidate>comparingDouble(Candidate::distance).thenComparing(p -> p.id().toString());
        var neglected = pool.stream().filter(p -> unattacked(p, lastTargeted, now) >= NEGLECT)
                .min(Comparator.<Candidate>comparingLong(p -> -unattacked(p, lastTargeted, now)).thenComparing(ties));
        if (neglected.isPresent()) return neglected.get();
        return pool.stream().min(Comparator.<Candidate>comparingDouble(p -> -priority(p, lastTargeted, now)).thenComparing(ties)).orElse(null);
    }

    public static double priority(Candidate player, Map<UUID, Long> lastTargeted, long now) {
        return player.threat() + (player.healing() ? HEALING_PRIORITY : 0) + (player.still() ? STILL_PRIORITY : 0)
                + NEGLECT_PRIORITY * Math.min(NEGLECT, unattacked(player, lastTargeted, now)) / 20.0;
    }

    private static long unattacked(Candidate player, Map<UUID, Long> lastTargeted, long now) {
        Long at = lastTargeted.get(player.id());
        return at == null ? 0 : Math.max(0, now - at);
    }

    // ── The brawl's chooser ──────────────────────────────────────────────────

    /**
     * What it knows about its target when it picks. Distance is in blocks from its feet.
     *
     * @param bearing degrees its target stands off its facing: 0 straight ahead, 180 behind
     * @param rightSide which side of its bill they stand on, which picks the sweep's direction
     * @param healing they are eating or drinking
     * @param airborne they are well off the rock: launched, hovering or falling
     * @param still they have stood in place for a moment, usually to cast
     * @param keptAway they have stayed out past RANGED_DISTANCE, or just hit it from there
     * @param jumped they cleared the last ring that reached them by jumping
     * @param dashed they have just spent a dash or a blink
     * @param flanked somebody else stands beside or behind it, inside the Thunderclap's ring
     * @param chargeReady the Thunder Charge is off its cooldown
     * @param camped attacks it has made since it last moved
     */
    public record Read(double distance, double bearing, boolean rightSide, boolean healing, boolean airborne, boolean still,
                       boolean keptAway, boolean jumped, boolean dashed, boolean flanked, boolean chargeReady, int camped) {
        /** A target on the rock in front of it, doing nothing in particular. */
        public Read(double distance, double bearing) {
            this(distance, bearing, false, false, false, false, false, false, false, false, true, 0);
        }
    }

    /**
     * The weights of every attack it could start now. Its bill reaches only what its slow body can
     * turn to before the aim locks, so the front draws the bite, the sweep and the scream; beside,
     * behind or under it the Thunderclap answers; the Raptor Strike and the Thunder Charge close
     * distance. What the target is doing tilts it: eating or standing still draws the fast, straight
     * hits; jumping its rings draws the chest-high sweep; keeping away draws the charge and the
     * strike; a player in the air draws the bite's burst.
     */
    public static Map<Action, Double> weights(Read read, Action last, Action before) {
        Map<Action, Double> w = new EnumMap<>(Action.class);
        double d = read.distance(), b = read.bearing();
        Action sweep = read.rightSide() ? Action.SWING_LEFT : Action.SWING_RIGHT;
        boolean close = d < WING_REACH - 2;
        if (b <= turnBudget(Action.BITE) && Reaches.BITE.has(d)) w.put(Action.BITE, 5.0);
        if (b <= turnBudget(sweep) && Reaches.SWING.has(d)) w.put(sweep, 5.0);
        if (b <= turnBudget(Action.SCREAM) && d >= Reaches.SCREAM_START) w.put(Action.SCREAM, d > Reaches.BITE.max() ? 6.0 : 4.0);
        // Beside it, behind it or under its chest no bill reaches: there the wings answer. In front
        // of it they are a mix-up.
        if (close) w.put(Action.WING, b > FRONT || d < Reaches.SWING.min() ? 8.0 : 1.5);
        if (b <= turnBudget(Action.POUNCE)) w.put(Action.POUNCE, d > POUNCE_FAR ? 6.0 : close ? 1.5 : 2.0);
        if (read.chargeReady() && d >= CHARGE_MIN) w.put(Action.STAMPEDE, 2.0);

        if (read.keptAway()) {
            w.computeIfPresent(Action.STAMPEDE, (a, x) -> x + 8);
            w.computeIfPresent(Action.POUNCE, (a, x) -> x + 3);
        }
        // It has stood in one spot long enough: it moves.
        if (read.camped() >= ACTIONS_BEFORE_POUNCE) w.computeIfPresent(Action.POUNCE, (a, x) -> x + 6);
        // A player who jumps every ring meets what a jump does not clear.
        if (read.jumped()) {
            w.computeIfPresent(sweep, (a, x) -> x + 8);
            w.computeIfPresent(Action.SCREAM, (a, x) -> x + 4);
            w.computeIfPresent(Action.WING, (a, x) -> x * .3);
        }
        // Standing still to cast, or stopping to eat, is what its fast straight hits are for.
        if (read.still() || read.healing()) {
            w.computeIfPresent(Action.SCREAM, (a, x) -> x + 8);
            w.computeIfPresent(sweep, (a, x) -> x + (read.healing() ? 8 : 3));
        }
        // A dash is spent: the next aim finds them where they landed, with no dash to answer it.
        if (read.dashed()) {
            w.computeIfPresent(Action.SCREAM, (a, x) -> x + 4);
            w.computeIfPresent(sweep, (a, x) -> x + 4);
        }
        // Off the rock the rings and the scream pass under them; the bite's burst does not.
        if (read.airborne()) {
            w.computeIfPresent(Action.BITE, (a, x) -> x + 8);
            w.computeIfPresent(Action.SCREAM, (a, x) -> x * .2);
            w.computeIfPresent(Action.WING, (a, x) -> x * .2);
        }
        // Somebody else is beside it while it looks at this player.
        if (read.flanked()) w.merge(Action.WING, 5.0, Math::max);
        // Never the same attack three times running, and seldom twice. Both sweeps count as one.
        if (last != null && w.size() > 1) {
            boolean twice = before != null && family(last) == family(before);
            for (Action a : Action.values()) {
                if (family(a) != family(last)) continue;
                if (twice) w.remove(a);
                else w.computeIfPresent(a, (k, x) -> x * .45);
            }
        }
        return w;
    }

    private static Action family(Action action) { return action.swing() ? Action.SWING_LEFT : action; }

    /** Null when nothing reaches its target: it keeps turning toward them instead. */
    public static Action choose(Read read, Action last, Action before, DoubleSupplier random) {
        return pick(weights(read, last, before), random);
    }

    public static <T> T pick(Map<T, Double> weights, DoubleSupplier random) {
        double total = 0;
        for (double weight : weights.values()) total += weight;
        if (total <= 0) return null;
        double roll = random.getAsDouble() * total;
        T chosen = null;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            chosen = entry.getKey();
            roll -= entry.getValue();
            if (roll < 0) break;
        }
        return chosen;
    }

    /**
     * These cut its rest short: it answers eating and a spent dash at once, and a player standing
     * still where its scream can reach. Standing still under its bill is left alone: that is the
     * opening a close-range build fights in.
     */
    public static boolean punishes(Read read) {
        boolean front = read.bearing() <= turnBudget(Action.SCREAM);
        return read.healing() || read.dashed() && front
                || read.still() && front && read.distance() >= Reaches.SCREAM_START;
    }

    /**
     * A string is a run of attacks with only STRING_GAP between them, each chosen fresh from where
     * its target has gone; every clip still plays its whole recovery. This is how likely it is to go
     * straight into another. A Raptor Strike that has just landed usually does, the Thunder Charge
     * never does (its skid is the opening), and phase two strings far more often.
     */
    public static double chainChance(boolean two, Action finished, Difficulty difficulty) {
        if (finished == Action.STAMPEDE) return 0;
        double base = finished == Action.POUNCE ? (two ? .9 : .6) : two ? .65 : .35;
        return Math.min(1, base * difficultyScale(difficulty, .6, 1.3));
    }

    /** The most attacks in one string. */
    public static int chainLimit(boolean two) { return two ? 3 : 2; }

    /** The rest after a string, in ticks: shorter with each player and on Hard. */
    public static int rest(int players, Difficulty difficulty) {
        double[] party = {1, .8, .65, .55};
        return Math.max(6, (int) Math.round(REST * party[Math.clamp(players, 1, AIMED_CAP) - 1] * difficultyScale(difficulty, 1.4, .75)));
    }

    private static double difficultyScale(Difficulty difficulty, double easy, double hard) {
        return difficulty == Difficulty.EASY ? easy : difficulty == Difficulty.HARD ? hard : 1;
    }

    // ── The air stretch ──────────────────────────────────────────────────────

    /**
     * What its call brings down on each challenger. VOLLEY: a large strike where they stand; keep
     * moving. FORK: three along the way they are running, ahead, on and behind; turn aside. CAGE: a
     * ring of strikes round them; hold still.
     */
    public enum Call { VOLLEY, FORK, CAGE }

    /**
     * Which call it makes. Players who run draw the fork and the cage, which a straight sprint does
     * not dodge; players who stand draw the volley, sometimes the fork, and never the cage, which
     * cannot hit them. A call that hit nobody is seldom made again at once, one that worked may be,
     * and never the same three times.
     *
     * @param moving the share of challengers who are running
     * @param lastHit whether its last call hit anybody
     */
    public static Map<Call, Double> callWeights(double moving, Call last, Call before, boolean lastHit) {
        Map<Call, Double> w = new EnumMap<>(Call.class);
        boolean running = moving >= .5;
        w.put(Call.VOLLEY, running ? 2.0 : 8.0);
        w.put(Call.FORK, running ? 7.0 : 2.0);
        if (running) w.put(Call.CAGE, 5.0);
        if (last != null && w.size() > 1) {
            if (last == before) w.remove(last);
            else if (!lastHit) w.computeIfPresent(last, (c, x) -> x * .3);
        }
        return w;
    }

    /** Calls it makes in one air stretch before it may come down, by phase and difficulty. */
    public static int minCalls(boolean two, Difficulty difficulty) {
        if (difficulty == Difficulty.EASY) return two ? 2 : 3;
        return two || difficulty == Difficulty.HARD ? 1 : 2;
    }

    /**
     * Whether it comes down now, asked on its spire where its next call would be. Never before
     * minCalls, always after one more. Between them it is a coin toss, unless it has a good read:
     * somebody is slowed, caught or eating, or the party is bunched under one landing.
     */
    public static boolean dives(int calls, boolean goodRead, boolean two, Difficulty difficulty, DoubleSupplier random) {
        int min = minCalls(two, difficulty);
        if (calls < min) return false;
        return calls > min || goodRead || random.getAsDouble() < .5;
    }

    /**
     * Whether it leaves this spire on a low pass across the summit instead of a hop: never in the
     * fight's first air stretch, which teaches the storm alone, only after a stretch's first call,
     * and once in a stretch (twice in phase two).
     *
     * @param stretch how many air stretches the fight has had before this one
     */
    public static boolean passes(int stretch, int calls, int passes, boolean two, DoubleSupplier random) {
        return stretch >= 1 && calls >= 1 && passes < (two ? 2 : 1) && random.getAsDouble() < (two ? .6 : .5);
    }

    /**
     * The spire it hops to: the neighbour nearer its challengers, so it keeps to their side of the
     * summit and in their view. When neither is clearly nearer it flies on the way it was going.
     *
     * @param distance how far the challengers are from each spire
     */
    public static int hop(int perch, int cameFrom, IntToDoubleFunction distance) {
        int count = StormSummitLayout.PERCHES.length, a = (perch + 1) % count, b = (perch + count - 1) % count;
        double da = distance.applyAsDouble(a), db = distance.applyAsDouble(b);
        if (Math.abs(da - db) < HOP_MARGIN) return cameFrom == a ? b : a;
        return da < db ? a : b;
    }

    /**
     * The spire a low pass ends on: the one most nearly straight on from its line, which leaves
     * this spire heading (dx, dz) through a point (x, z) blocks from the summit's centre.
     */
    public static int across(int perch, double x, double z, double dx, double dz) {
        int best = -1;
        double most = -2;
        for (int i = 0; i < StormSummitLayout.PERCHES.length; i++) {
            if (i == perch) continue;
            double px = StormSummitLayout.PERCHES[i][0] + .5 - x, pz = StormSummitLayout.PERCHES[i][2] + .5 - z, length = Math.hypot(px, pz);
            double along = length < 1e-6 ? -1 : (px * dx + pz * dz) / length;
            if (along > most) { most = along; best = i; }
        }
        return best;
    }

    /**
     * How far along its line a low pass has flown, this many ticks after it leaves its spire: it
     * gathers speed over PASS_RAMP ticks, then holds PASS_SPEED.
     */
    public static double passDistance(double ticks) {
        double t = Math.max(0, ticks);
        return t <= PASS_RAMP ? PASS_SPEED * t * t / (2 * PASS_RAMP) : PASS_SPEED * (t - PASS_RAMP / 2.0);
    }

    /** The tick of a low pass on which it has flown this far along its line. */
    public static int passTick(double distance) {
        double ramp = PASS_SPEED * PASS_RAMP / 2.0, d = Math.max(0, distance);
        return (int) Math.ceil(d <= ramp ? Math.sqrt(2 * PASS_RAMP * d / PASS_SPEED) : d / PASS_SPEED + PASS_RAMP / 2.0);
    }
}
