package com.anton.elementalwands.entity.roc;

import static com.anton.elementalwands.entity.roc.StormRocRules.*;

import com.anton.elementalwands.entity.roc.StormRocBrain.Call;
import com.anton.elementalwands.entity.roc.StormRocBrain.Candidate;
import com.anton.elementalwands.entity.roc.StormRocBrain.Reaches;
import com.anton.elementalwands.entity.roc.StormRocBrain.Read;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/**
 * Pure checks on what the Storm Roc decides and on whether a Minecraft player can answer it; run
 * by the Gradle check task without launching Minecraft. A retimed clip or a retuned radius that
 * breaks a dodge fails here, not in a playtest.
 */
public final class StormRocBrainContractTest {
    /** Blocks a tick: a walk or strafe while watching it, and a sprint, which faces away from it. */
    private static final double WALK = 4.317 / 20, SPRINT = 5.612 / 20;
    /** Ticks before a player acts on a warning. */
    private static final int REACT = 6;
    private static final double BODY = .3;

    public static void main(String[] args) {
        reaches();
        answers();
        reads();
        repeats();
        strings();
        targeting();
        calls();
        airStretch();
        dodges();
        System.out.println("Storm Roc brain contract passed");
    }

    private static void reaches() {
        require(Reaches.SWING.min() < Reaches.SWING.max() && Reaches.BITE.min() < Reaches.BITE.max(), "A head attack has no reach");
        require(Reaches.BITE.max() > Reaches.SWING.max(), "The bite's leap does not outreach the sweep");
        require(Reaches.SCREAM_START < Reaches.BITE.max(), "Nothing covers the ground between the bite and the scream");
        require(Reaches.SWING.min() < WING_REACH - 2, "Under its chest neither the sweep nor the Thunderclap reaches");
    }

    /** Every place to stand has an answer, and its bill never reaches further round than its body can turn. */
    private static void answers() {
        for (double d = 0; d <= 70; d += .5) {
            for (double b = 0; b <= 180; b += 5) {
                var w = StormRocBrain.weights(new Read(d, b), null, null);
                for (Action a : w.keySet())
                    require(b <= StormRocBrain.turnBudget(a), a + " offered " + b + " degrees off its facing, further than it can turn by its lock");
                if (b <= 40) require(!w.isEmpty(), "Nothing answers a player " + d + " blocks ahead");
                if (d < WING_REACH - 2) require(w.containsKey(Action.WING), "The Thunderclap does not answer a player " + d + " blocks away at " + b + " degrees");
                if (d >= CHARGE_MIN) require(w.containsKey(Action.STAMPEDE), "The Thunder Charge is not offered at " + d + " blocks");
                if (b > 125) for (Action a : w.keySet()) require(a == Action.WING || a == Action.STAMPEDE, a + " reaches a player behind it");
            }
        }
        // Behind it and out of the Thunderclap's ring with the charge spent: nothing reaches, and it turns.
        var none = StormRocBrain.weights(new Read(30, 170, false, false, false, false, false, false, false, false, false, 0), null, null);
        require(none.isEmpty() && StormRocBrain.pick(none, () -> .5) == null, "It attacks a player it cannot reach");
        require(StormRocBrain.turnBudget(Action.SCREAM) < FRONT, "The scream follows a player who has got beside it");
    }

    private static Read read(double d, double b, String flag) {
        return new Read(d, b, false, flag.equals("healing"), flag.equals("airborne"), flag.equals("still"), flag.equals("keptAway"),
                flag.equals("jumped"), flag.equals("dashed"), flag.equals("flanked"), true, flag.equals("camped") ? ACTIONS_BEFORE_POUNCE : 0);
    }

    private static Action top(Map<Action, Double> w) {
        return w.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    private static double share(Map<Action, Double> w, Action... actions) {
        double total = w.values().stream().mapToDouble(Double::doubleValue).sum(), part = 0;
        for (Action a : actions) part += w.getOrDefault(a, 0.0);
        return part / total;
    }

    /** What the target is doing tilts the choice toward the attack that answers it. */
    private static void reads() {
        var eating = StormRocBrain.weights(read(13, 10, "healing"), null, null);
        require(share(eating, Action.SCREAM, Action.SWING_RIGHT) > .6, "Eating in front of it does not draw its fast hits");
        require(top(StormRocBrain.weights(read(32, 10, "still"), null, null)) == Action.SCREAM, "Standing still at range does not draw the scream");
        var jumper = StormRocBrain.weights(read(12, 10, "jumped"), null, null);
        require(top(jumper) == Action.SWING_RIGHT, "Jumping its rings does not draw the sweep");
        require(jumper.get(Action.WING) < StormRocBrain.weights(read(12, 10, ""), null, null).get(Action.WING), "It still favours rings against a player who jumps them");
        require(top(StormRocBrain.weights(read(32, 10, "keptAway"), null, null)) == Action.STAMPEDE, "Keeping away does not draw the Thunder Charge");
        require(top(StormRocBrain.weights(read(14, 10, "airborne"), null, null)) == Action.BITE, "A player in the air does not draw the bite");
        require(StormRocBrain.weights(read(40, 10, "flanked"), null, null).getOrDefault(Action.WING, 0.0) >= 5, "A second player beside it is ignored");
        require(top(StormRocBrain.weights(read(12, 10, "camped"), null, null)) == Action.POUNCE, "It never moves from one spot");
        require(top(StormRocBrain.weights(read(8, 150, ""), null, null)) == Action.WING, "Behind it and close is not answered by the Thunderclap");
        // What cuts its rest short.
        require(StormRocBrain.punishes(read(13, 10, "healing")) && StormRocBrain.punishes(read(8, 150, "healing")), "Eating is not punished at once");
        require(StormRocBrain.punishes(read(20, 10, "still")), "Standing still in its line is not punished");
        require(!StormRocBrain.punishes(read(5, 10, "still")), "Standing still under its bill, the melee opening, is punished");
        require(!StormRocBrain.punishes(read(20, 150, "still")) && !StormRocBrain.punishes(read(20, 10, "")), "It cuts its rest short for nothing");
        require(StormRocBrain.punishes(read(20, 10, "dashed")), "A spent dash is not punished");
    }

    private static void repeats() {
        Read front = new Read(13, 10);
        require(!StormRocBrain.weights(front, Action.BITE, Action.BITE).containsKey(Action.BITE), "The same attack three times running");
        var swept = StormRocBrain.weights(front, Action.SWING_LEFT, Action.SWING_RIGHT);
        require(!swept.containsKey(Action.SWING_LEFT) && !swept.containsKey(Action.SWING_RIGHT), "Three sweeps running");
        require(StormRocBrain.weights(front, Action.BITE, null).get(Action.BITE) < StormRocBrain.weights(front, null, null).get(Action.BITE), "A repeat is as likely as a fresh attack");
        // Chance picks across the whole map, and nothing with no weight.
        var w = StormRocBrain.weights(front, null, null);
        List<Action> seen = new ArrayList<>();
        for (double roll = 0; roll < 1; roll += .01) {
            Action a = StormRocBrain.pick(w, constant(roll));
            if (!seen.contains(a)) seen.add(a);
        }
        require(seen.size() == w.size(), "Some attack in front of it can never be picked");
    }

    private static java.util.function.DoubleSupplier constant(double value) { return () -> value; }

    private static void strings() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (Action a : Action.values()) {
                double one = StormRocBrain.chainChance(false, a, difficulty), two = StormRocBrain.chainChance(true, a, difficulty);
                require(one >= 0 && two <= 1 && two >= one, "Phase two strings less than phase one after " + a);
                if (a == Action.STAMPEDE) require(two == 0, "It strings out of the Thunder Charge's skid, its opening");
            }
            require(StormRocBrain.rest(1, difficulty) > StormRocBrain.rest(2, difficulty) && StormRocBrain.rest(2, difficulty) >= StormRocBrain.rest(4, difficulty), "Its rest does not shrink with the party");
            require(StormRocBrain.rest(9, difficulty) >= 6, "A large party leaves no rest at all");
        }
        require(StormRocBrain.rest(1, Difficulty.NORMAL) == REST && REST <= 25, "The solo opening is not the agreed 1.25 seconds");
        require(StormRocBrain.chainLimit(true) > StormRocBrain.chainLimit(false), "Phase two's strings are no longer than phase one's");
    }

    private static void targeting() {
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        Map<UUID, Long> attacked = new HashMap<>();
        attacked.put(a, 1000L);
        attacked.put(b, 1000L - NEGLECT);
        var loud = new Candidate(a, 10, 500, false, false);
        var quiet = new Candidate(b, 40, 0, false, false);
        require(StormRocBrain.focus(List.of(loud, quiet), attacked, 1000, null) == quiet, "A player is left alone longer than NEGLECT");
        attacked.put(b, 990L);
        require(StormRocBrain.focus(List.of(loud, quiet), attacked, 1000, null) == loud, "The main damage dealer does not draw its attacks");
        require(StormRocBrain.focus(List.of(loud, quiet), attacked, 1000, a) == quiet, "A string never switches target");
        require(StormRocBrain.focus(List.of(loud), attacked, 1000, a) == loud, "Solo, a string has nobody to attack");
        var eater = new Candidate(b, 40, 0, false, true);
        require(StormRocBrain.focus(List.of(new Candidate(a, 10, 20, false, false), eater), attacked, 1000, null) == eater, "Eating does not draw its eye in a party");
        // Equal threat rotates: whoever was attacked longer ago comes first.
        attacked.put(a, 900L);
        attacked.put(b, 950L);
        require(StormRocBrain.focus(List.of(new Candidate(a, 10, 0, false, false), new Candidate(b, 10, 0, false, false)), attacked, 1000, null).id().equals(a), "Equal players are not attacked in turn");
    }

    private static void calls() {
        var running = StormRocBrain.callWeights(1, null, null, false);
        require(running.get(Call.FORK) + running.get(Call.CAGE) > 4 * running.get(Call.VOLLEY), "Runners mostly draw the volley, which running dodges");
        var standing = StormRocBrain.callWeights(0, null, null, false);
        require(standing.get(Call.VOLLEY) > standing.get(Call.FORK) && !standing.containsKey(Call.CAGE), "Standing players do not draw the volley, or draw a cage that cannot hit them");
        require(!StormRocBrain.callWeights(0, Call.VOLLEY, Call.VOLLEY, true).isEmpty(), "A standing party can leave it with no call to make");
        require(!StormRocBrain.callWeights(1, Call.FORK, Call.FORK, true).containsKey(Call.FORK), "The same call three times running");
        require(StormRocBrain.callWeights(1, Call.FORK, Call.CAGE, false).get(Call.FORK) < StormRocBrain.callWeights(1, Call.FORK, Call.CAGE, true).get(Call.FORK),
                "A call that hit nobody is as likely again as one that worked");
        for (Difficulty difficulty : Difficulty.values()) for (boolean two : new boolean[] {false, true}) {
            int min = StormRocBrain.minCalls(two, difficulty);
            require(min >= 1, "It can dive before it has called once");
            for (int calls = 0; calls < min; calls++) require(!StormRocBrain.dives(calls, true, two, difficulty, constant(0)), "It dives before its minimum calls");
            require(StormRocBrain.dives(min, true, two, difficulty, constant(.99)), "A good read does not bring it down");
            require(!StormRocBrain.dives(min, false, two, difficulty, constant(.99)) && StormRocBrain.dives(min, false, two, difficulty, constant(0)), "Its dive is not a coin toss at its minimum calls");
            require(StormRocBrain.dives(min + 1, false, two, difficulty, constant(.99)), "An air stretch can run on past one call more than its minimum");
        }
        require(StormRocBrain.minCalls(true, Difficulty.NORMAL) <= StormRocBrain.minCalls(false, Difficulty.NORMAL), "Phase two keeps it in the air longer");
    }

    private static void airStretch() {
        require(!StormRocBrain.passes(1, 0, 0, false, constant(0)), "A low pass before a stretch's first call");
        require(!StormRocBrain.passes(0, 2, 0, false, constant(0)), "A low pass in the fight's first air stretch");
        require(StormRocBrain.passes(1, 1, 0, false, constant(0)) && !StormRocBrain.passes(1, 1, 1, false, constant(0)), "Phase one makes other than one pass a stretch");
        require(StormRocBrain.passes(5, 2, 1, true, constant(0)) && !StormRocBrain.passes(5, 2, 2, true, constant(0)), "Phase two makes other than two passes a stretch");
        // It hops toward its challengers, and keeps its way when neither neighbour is nearer.
        require(StormRocBrain.hop(2, 1, i -> i == 3 ? 20 : 90) == 3 && StormRocBrain.hop(2, 3, i -> i == 1 ? 20 : 90) == 1, "It hops away from its challengers");
        require(StormRocBrain.hop(2, 1, i -> 50) == 3 && StormRocBrain.hop(2, 3, i -> 50) == 1, "With nobody nearer either way it turns back");
        require(StormRocBrain.hop(0, 1, i -> 50) == 5 && StormRocBrain.hop(5, 4, i -> 50) == 0, "Its way round the spires does not wrap");
        // From the north spire, due south through the centre, the pass ends on the south spire.
        require(StormRocBrain.across(0, 0, 0, 0, 1) == 3, "A pass south from the north spire does not end on the south spire");
        for (int perch = 0; perch < 6; perch++) require(StormRocBrain.across(perch, 0, 0, 1, 0) != perch, "A pass ends on the spire it left");
        double last = -1;
        for (int t = 0; t <= 200; t++) {
            double s = StormRocBrain.passDistance(t);
            require(s > last, "A pass does not always fly forward");
            require(Math.abs(StormRocBrain.passTick(s) - t) <= 1, "passTick does not invert passDistance at tick " + t);
            last = s;
        }
        require(Math.abs(StormRocBrain.passDistance(100) - StormRocBrain.passDistance(99) - PASS_SPEED) < 1e-9, "A pass does not hold PASS_SPEED");
        // Leads follow steady running and are capped.
        Vec3d led = StormRocBrain.lead(Vec3d.ZERO, new Vec3d(SPRINT, 5, 0), 20);
        require(Math.abs(led.x - SPRINT * 20) < 1e-9 && led.y == 0, "A lead is not where a sprinter will be");
        require(StormRocBrain.lead(Vec3d.ZERO, new Vec3d(3, 0, 0), 20).length() <= LEAD_CAP + 1e-9, "A lead is not capped");
    }

    /**
     * Whether a Minecraft player can answer each attack. Players have no roll and no i-frames:
     * they walk at 4.3 blocks a second watching it, sprint at 5.6 facing away, and take about a
     * third of a second to act on a warning.
     */
    private static void dodges() {
        // Led hits: a straight line is hit, turning back after the lock dodges, merely stopping does not.
        led("Storm Scream", Action.SCREAM.impact - Action.SCREAM.lock, SCREAM_HALF_WIDTH);
        led("Thunder Bite", Action.BITE.impact - Action.BITE.lock, BITE_RADIUS);
        led("Thunder Bite's leap", BITE_LEAP_IMPACT - BITE_LEAP_LOCK, BITE_RADIUS);
        led("Raptor Strike", Action.POUNCE.impact - Action.POUNCE.lock, POUNCE_RADIUS);
        require(Action.POUNCE.lock == POUNCE_LAUNCH, "The Raptor Strike's aim does not lock as it launches");
        require(BITE_DECIDE <= Action.BITE.lock && BITE_LEAP_LOCK < BITE_LEAP, "The bite locks before it has chosen its leap, or after it has jumped");

        // Areas: from the middle a sprint gets out and a walk does not.
        require(SPRINT * (DIVE_TICKS - REACT - 8) >= DIVE_RADIUS + BODY, "A sprint from the middle of the dive has under 0.4 s to spare");
        require(WALK * (DIVE_TICKS - REACT) < DIVE_RADIUS + BODY, "A walk gets out of the dive from its middle");
        require(SPRINT * (FIRST_SMASH_WARNING - REACT) >= SMASH_RADIUS + BODY, "A sprint does not escape the chain smash's first circle");
        require(SPRINT * (SMASH_PAUSE + SMASH_WARNING - 1 - REACT) >= SMASH_RADIUS + BODY, "A sprint does not escape a chain smash hop");
        // The low pass: a walk leaves the lane from its middle before the first bolt.
        require(WALK * (LANE_WARNING - REACT) >= LANE_RADIUS + BODY, "A walk does not leave the lane");
        require(LANE_SPACING < 2 * LANE_RADIUS, "The lane's circles leave gaps");
        // The fork: a sprinter who turns aside clears the large strike they were running into.
        require(SPRINT * (LARGE_WARNING - REACT) > LARGE_RADIUS + BODY, "Turning aside does not dodge the fork");
        require(LEAD_CAP + 1 >= LARGE_RADIUS + FORK_GAP && SPRINT * LARGE_WARNING >= LARGE_RADIUS + FORK_GAP, "The fork's lead strike overlaps the one on the player");
        // The cage: running on is hit by its ring; stopping, even after a late reaction, is safe.
        for (double radius : new double[] {SMALL_RADIUS, SMALL_RADIUS_TWO}) {
            double ring = radius * CAGE_RING;
            for (double speed : new double[] {WALK, SPRINT}) {
                double run = speed * CAGE_WARNING;
                require(run + BODY > ring - radius && run - BODY < ring + radius, "Running out of a cage is not hit by it");
            }
            require(SPRINT * REACT + BODY < ring - radius, "A sprinter cannot stop inside a cage");
        }
        require(CALL_HOLD >= CAGE_WARNING + BOLT_GAP * (SMALL_BOLTS - 1), "An aimed strike can land in a cage's middle while it is live");
        // Its body: slow enough that getting beside it means something, fast enough to come round in a rest.
        require(TURN_RATE * 20 <= 100, "Its body turns like a small mob's");
        require(TURN_RATE * (REST + Action.BITE.lock) >= 180, "A player behind it is safe from its bill for more than one rest");
    }

    private static void led(String name, int window, double size) {
        require(SPRINT * window <= LEAD_CAP, name + ": the lead cap lets a sprinter outrun its aim");
        require(2 * SPRINT * (window - REACT) > size + BODY, name + ": turning back after the lock does not dodge it at a sprint");
        require(WALK * (window - REACT) < size + BODY, name + ": a walker who merely stops after the lock dodges it");
        require(window >= 14 && window <= 24, name + ": its aim locks " + window + " ticks before the hit, outside 0.7-1.2 s");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
