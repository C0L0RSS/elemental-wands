package com.anton.elementalwands.entity.pyreking;

import com.anton.elementalwands.entity.pyreking.PyreKingRules.Band;
import com.anton.elementalwands.entity.pyreking.PyreKingRules.Move;
import com.anton.elementalwands.entity.pyreking.PyreKingRules.Read;
import com.anton.elementalwands.entity.pyreking.PyreKingRules.Window;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Pure rule checks for the Pyre King's fight; run by the Gradle check task without launching Minecraft. */
public final class PyreKingContractTest {
    public static void main(String[] args) {
        require(PyreKingRules.health(1) == 600 && PyreKingRules.health(2) == 960 && PyreKingRules.health(3) == 1320, "Party health scaling moved");
        require(PyreKingRules.SWORD == 21 && PyreKingRules.CHAIN == 16 && PyreKingRules.FIRE == 8 && PyreKingRules.UNBOUND_SWEEP == 19
                && PyreKingRules.YANK == 5 && PyreKingRules.TUG == 4, "Damage tiers moved");
        // The fight is tuned for plain diamond to netherite armor (20 armor; toughness 8 and 12) and 20 health.
        for (double toughness : new double[]{8, 12}) {
            double sword = armored(PyreKingRules.SWORD, 20, toughness), chain = armored(PyreKingRules.CHAIN, 20, toughness);
            require(sword * 2 < 20 && sword * 3 > 20, "Two sword blows should leave a diamond or netherite player alive and a third kill: " + sword);
            require(sword + chain < 20 && chain < sword && chain > sword * .5, "The chain should hurt less than the sword, but count: " + chain);
            require(armored(PyreKingRules.UNBOUND_SWEEP, 20, toughness) < sword, "Unbound's sweep should not out-hit his sword");
            double fire = armored(PyreKingRules.FIRE, 20, toughness);
            require(fire >= 1.5 && fire <= 3, "A second in his fire should cost about a tenth of a player's health: " + fire);
        }
        require(PyreKingRules.YANK < PyreKingRules.CHAIN && PyreKingRules.TUG < PyreKingRules.CHAIN, "The drags' own damage is light: what follows them is the threat");
        // Hiding: a pillar is answered in under two seconds, eating there sooner than a bite takes, and slipping back
        // behind it as a flank ends sooner still.
        require(PyreKingRules.FLANK_AFTER <= 40 && PyreKingRules.FLANK_HEALING < 32 && PyreKingRules.FLANK_HEALING < PyreKingRules.FLANK_AFTER
                && PyreKingRules.FLANK_AGAIN < PyreKingRules.FLANK_AFTER, "A hiding player should draw a flank quickly, a healing one sooner, and one who hides again sooner still");
        // A flank must beat running there (the developer, Nov 11: a short one "might look faster if he just walked it").
        PyreKingRules.FlankRule haul = PyreKingRules.flankRule(Move.HAUL);
        require(Move.HAUL.free <= 1.0 && (haul.near() - 1.5) / Move.HAUL.free > PyreKingRules.RUN_TWO * 1.5, "The Haul's shortest flight should be far quicker than his run");
        require(haul.wide() && haul.rings()[0] >= 8 && haul.far() <= 30, "The Haul sets him down in the open, inside the Snare's reach of his target");
        require(Move.HAUL.windows.isEmpty() && Move.SNARE.hooks() && Move.HOOK.hooks() && !Move.CAST.hooks(), "The Haul strikes nobody; the Snare is a Hook");
        Window snare = Move.SNARE.windows.get(0), hook = Move.HOOK.windows.get(0);
        require(near(snare.from(), hook.from() - PyreKingRules.SNARE_LEAD) && near(snare.to(), hook.to() - PyreKingRules.SNARE_LEAD)
                && near(Move.SNARE.free, Move.HOOK.free - PyreKingRules.SNARE_LEAD) && snare.damage() == hook.damage(), "The Snare is the Hook, sooner");
        require(Move.HAUL.free + snare.from() < 1.5, "From the start of a Haul to the Snare's throw should be under a second and a half");
        require(near(PyreKingRules.SNARE_THROWN, snare.from()) && PyreKingRules.SNARE_MISSED > snare.to() && PyreKingRules.SNARE_MISSED + PyreKingRules.SNARE_RECALL < Move.SNARE.free - 1,
                "A Snare that catches nobody should cost him far less than the Hook's haul");
        require(PyreKingRules.SNARE_PAST > 1 && PyreKingRules.SNARE_PAST < PyreKingTracks.SWORD_BLOCKS - 1, "The Snare should overshoot its target by less than its blade, so they are on it");
        Map<Move, Double> all = PyreKingRules.flankWeights(true, true, false, null);
        require(all.get(Move.HAUL) > all.get(Move.LUNGE) && !all.containsKey(Move.CAST), "The Haul is his usual way round a pillar, and the slow Cast is none");
        Map<Move, Double> healing = PyreKingRules.flankWeights(true, true, true, null);
        require(healing.get(Move.LUNGE) > healing.get(Move.HAUL), "A target healing behind a pillar draws the flank that lands a blow soonest");
        require(PyreKingRules.flankWeights(true, true, false, Move.HAUL).get(Move.HAUL) < all.get(Move.HAUL)
                && PyreKingRules.flankWeights(true, false, false, Move.HAUL).get(Move.HAUL).equals(all.get(Move.HAUL))
                && PyreKingRules.flankWeights(false, false, false, null).isEmpty(), "He varies his flanks when he has more than one");
        require(PyreKingRules.RUN < 5.6 && PyreKingRules.RUN > 4.3 && PyreKingRules.RUN_TWO < 5.6, "He must be slower than a sprint and faster than a player backing away");

        // Every move is a real clip with tracks, and everything it does happens inside that clip.
        for (Move move : Move.values()) {
            PyreKingClips.Clip clip = PyreKingClips.get(move.clip);
            require(clip != null && !clip.loop(), move + " needs a one-shot clip");
            PyreKingTracks.Track track = PyreKingTracks.get(move.clip);
            require(track != null && Math.abs(track.seconds - clip.seconds()) < .02, move + " has no tracks the length of its clip");
            require(move.free <= clip.seconds() + 1e-6 && move.aimUntil < move.free, move + " is free after its clip ends");
            double end = 0;
            for (Window window : move.windows) {
                require(window.from() >= end && window.to() > window.from() && window.to() <= move.free, move + " has a window out of order or past its recovery");
                require(window.from() >= move.aimUntil, move + " can hit before its aim freezes");
                end = window.to();
            }
            // The opening after his last blow is tight, as the developer asked: under a second (Unbound is a set piece).
            if (move != Move.UNBOUND && move != Move.CAST && !move.hooks() && move != Move.HAUL)
                require(move.free - move.lastBlow() <= 1.0 + 1e-6 && move.free - move.lastBlow() >= .4, move + " should leave an opening of 0.4 to 1 s, not " + (move.free - move.lastBlow()));
            for (double cue : move.cues) require(cue >= 0 && cue < move.lastBlow(), move + " warns after its last blow");
            // A blow that needs a warning gets one at least 0.3 s before it can land.
            if (!move.windows.isEmpty() && move != Move.ZIP)
                require(move.cues.length > 0 && move.windows.get(0).from() - move.cues[0] >= .3, move + " lands without a warning");
        }
        // The thrown sword's aim: none in his hand, all of it where it sticks, and his body takes it over a haul.
        for (String clip : new String[]{"cast_reel", "hook", "lunge", "drag_down", "haul", "snare"}) {
            PyreKingAim.Throw thrown = PyreKingAim.get(clip);
            require(thrown != null && thrown.sword(0) == 0 && thrown.lift(0) == 0 && thrown.body(0) == 0, clip + " moves a sword that is still in his hand");
        }
        require(near(PyreKingAim.get("cast_reel").sword(1.3), 1) && near(PyreKingAim.get("hook").sword(1.35), 1)
                && near(PyreKingAim.get("lunge").sword(.85), 1) && near(PyreKingAim.get("drag_down").lift(1.0), 1), "A stuck sword must take the whole aim");
        PyreKingAim.Throw zip = PyreKingAim.get("zip"), lunge = PyreKingAim.get("lunge");
        for (double t = 0; t < 3.2; t += .05) require(near(zip.sword(t), 1), "The sword a Zip hauls him to must not move, then must travel with him");
        require(zip.body(.5) == 0 && near(zip.body(1.1), 1) && lunge.body(.85) == 0 && near(lunge.body(1.4), 1), "Zip and Lunge must carry his body to the end of the haul");
        PyreKingAim.Throw hauled = PyreKingAim.get("haul");
        require(near(hauled.sword(.5), 1) && hauled.body(.45) == 0 && near(hauled.body(.85), 1) && near(PyreKingAim.get("snare").sword(.75), 1),
                "The Haul must carry his body to its stuck sword, and the Snare's sword must take the whole aim");
        require(PyreKingTracks.get("haul").travelEnd() > PyreKingTracks.get("lunge").travelEnd() * 1.5, "The Haul should go much further than the Lunge");
        require(near(PyreKingAim.get("drag_down").lift(1.3), 0), "Drag Down's sword must come down to the floor");
        require(PyreKingAim.get("royal_cut") == null && PyreKingAim.get("chain_scythe_low") == null, "Only throws are aimed");
        double[] stick = PyreKingAim.get("cast_reel").stick();
        require(near(stick[1], 0) && stick[2] > 11.5 && stick[2] < 12.5, "The Cast's sword should meet the floor about 12 blocks out");

        // The chooser: distance picks the family, reads tilt it, and he never repeats a string three times.
        Random random = new Random(7);
        Read close = new Read(4, false, false, false, false, false, true, false), behind = new Read(4, false, true, false, false, false, true, false);
        Read mid = new Read(10, false, false, false, false, false, true, false), jumper = new Read(10, false, false, false, true, false, true, false);
        Read far = new Read(20, false, false, false, false, false, true, false), distant = new Read(32, false, false, false, false, false, true, false);
        Read hidden = new Read(32, false, false, false, false, false, false, false), aloft = new Read(8, true, false, false, false, false, true, false);
        Read out = new Read(30, true, false, false, false, false, true, false), healer = new Read(18, false, false, false, false, true, true, false);
        Read retreat = new Read(8, false, false, true, false, false, true, false), two = new Read(4, false, false, false, false, false, true, true);
        Map<Move, Integer> counts = new EnumMap<>(Move.class);
        for (int i = 0; i < 4000; i++) counts.merge(PyreKingRules.choose(close, null, null, random::nextDouble), 1, Integer::sum);
        require(counts.keySet().stream().allMatch(move -> move.swordString() || move == Move.SCYTHE_LOW), "Close in he uses sword strings: " + counts);
        require(counts.size() >= 3 && counts.get(Move.ROYAL_CUT) > counts.get(Move.OVERHEAD), "Close in he should mix his strings, the Royal Cut most: " + counts);
        require(share(behind, Move.TURNING_CUT, random) > .55, "Standing behind him should draw the Turning Cut");
        require(share(mid, Move.SCYTHE_LOW, random) > share(mid, Move.SCYTHE_HIGH, random), "The low Scythe is the usual one");
        require(share(jumper, Move.SCYTHE_HIGH, random) > share(jumper, Move.SCYTHE_LOW, random) * 3, "Jumping should draw the high Scythe");
        require(share(retreat, Move.THRUST, random) > .4, "Backing straight away should draw the Thrust");
        require(share(far, Move.CAST, random) > .5 && share(distant, Move.HOOK, random) > .5, "Far targets draw the Cast, very far ones the Hook");
        require(PyreKingRules.choose(hidden, null, null, random::nextDouble) == null
                && PyreKingRules.weights(new Read(10, false, false, false, false, false, false, false), null, null).isEmpty()
                && PyreKingRules.weights(new Read(4, false, true, false, false, true, false, true), null, null).isEmpty(),
                "He swings at nothing he cannot see: stone shelters a player from every blow, the Scythe included");
        require(PyreKingRules.choose(aloft, null, null, random::nextDouble) == Move.DRAG_DOWN && PyreKingRules.choose(out, null, null, random::nextDouble) == null,
                "A player overhead draws Drag Down once he is under them");
        // Eating in the open: he does not wait out his gap, and it is the sword that comes, thrown or flown after.
        require(PyreKingRules.punishes(healer) && !PyreKingRules.punishes(far) && !PyreKingRules.punishes(new Read(3, false, false, false, false, true, true, false))
                && share(healer, Move.HOOK, random) < share(far, Move.HOOK, random), "Eating in the open should be punished");
        require(share(two, Move.ROYAL_CUT_THREE, random) > 0 && share(two, Move.ROYAL_CUT, random) == 0, "Phase two's Royal Cut has three blows");
        for (Read read : new Read[]{close, mid, far, distant, two, healer})
            for (Move move : Move.values()) {
                Map<Move, Double> weights = PyreKingRules.weights(read, move, move);
                require(weights.isEmpty() || !weights.containsKey(move) || weights.size() == 1, "He must not use " + move + " three times running");
            }
        require(PyreKingRules.royalBranch(4, 150, false, random::nextDouble) == Move.TURNING_CUT && PyreKingRules.royalBranch(4, 20, false, random::nextDouble) == null,
                "After the first cut he turns on a player behind him and otherwise carries on");
        require(PyreKingRules.chainLimit(true) > PyreKingRules.chainLimit(false) && PyreKingRules.chainChance(true) > PyreKingRules.chainChance(false), "Phase two chains more");

        // Heights: a floor sweep is jumped, a head-height one is crouched under, a cut reaches the floor.
        require(PyreKingRules.inBand(Band.LOW, 0, 1.8, .2, .3) && !PyreKingRules.inBand(Band.LOW, .7, 2.5, .2, .3), "The low sweep must be jumpable");
        require(PyreKingRules.inBand(Band.HIGH, 0, 1.8, 1.4, 2.6) && !PyreKingRules.inBand(Band.HIGH, 0, 1.5, 1.4, 2.6), "The high sweep must pass over a crouch");
        require(PyreKingRules.inBand(Band.SOLID, 0, 1.8, 2.7, 3.1) && !PyreKingRules.inBand(Band.SOLID, 5, 6.8, 2.7, 3.1), "A cut reaches the floor but not a pillar top");
        require(!PyreKingRules.inBand(Band.SOLID, -4, -2.2, 2.7, 3.1) && !PyreKingRules.inBand(Band.LOW, -4, -2.2, .2, .3), "Nothing reaches a player in a cave under his floor");
        require(PyreKingAim.get("drag_down").lift(.85) == 0, "Drag Down must not lift a sword that is still in his hand");
        require(PyreKingRules.sweptDistanceSquared(3, 0, 0, 0, 0, 0, 0, 0, 0, 0) > 8, "A weapon standing still on one spot sweeps nothing");
        require(PyreKingRules.inBand(Band.TRUE, 0, 1.8, 1, 1.5) && !PyreKingRules.inBand(Band.TRUE, 0, 1.8, 3.5, 4), "A thrown sword hits only where it is");
        // The sweep: inside the swept quad, beside it, and clear of it.
        require(PyreKingRules.sweptDistanceSquared(1, 1, 0, 0, 4, 0, 0, 0, 0, 4) == 0, "A point inside the sweep is struck");
        require(near(Math.sqrt(PyreKingRules.sweptDistanceSquared(5, 0, 0, 0, 4, 0, 0, 0, 0, 4)), 1), "A point a block past the blade is a block away");
        require(PyreKingRules.sweptDistanceSquared(-3, -3, 0, 0, 4, 0, 0, 0, 0, 4) > 9, "A point behind the sweep is clear");
        System.out.println("Pyre King contract checks passed");
    }

    private static double share(Read read, Move move, Random random) {
        int hits = 0, tries = 3000;
        for (int i = 0; i < tries; i++) if (PyreKingRules.choose(read, null, null, random::nextDouble) == move) hits++;
        return hits / (double) tries;
    }

    /** Vanilla's armor sum: what is left of a blow after armor and its toughness. */
    private static double armored(double damage, double armor, double toughness) {
        double held = Math.clamp(armor - 4 * damage / (toughness + 8), armor / 5, 20);
        return damage * (1 - held / 25);
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-3; }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
