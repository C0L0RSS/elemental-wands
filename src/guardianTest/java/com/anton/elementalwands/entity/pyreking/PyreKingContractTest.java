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
        require(PyreKingRules.RUN < 5.6 && PyreKingRules.RUN > 5 && PyreKingRules.RUN_TWO < 5.6 && PyreKingRules.RUN_TWO >= PyreKingRules.RUN,
                "He must be a little slower than a sprint, and no slower than that");
        require(near(PyreKingRules.tempo("run").clip(1), PyreKingRules.RUN / PyreKingRules.RUN_AUTHORED), "His run clip should stride at the speed he runs");

        // The Chain Scythe up close (the developer's second playtest, Nov 11: "impossible to dodge at close range").
        require(!PyreKingRules.scytheReaches(PyreKingRules.HOLD, .3) && !PyreKingRules.scytheReaches(4, .3) && PyreKingRules.scytheReaches(6.5, .3),
                "The Scythe should strike only out where its blade swings, not a player standing close to him");
        for (double d : new double[]{1, 3, 5, 6.5})
            for (boolean jumped : new boolean[]{false, true}) {
                Map<Move, Double> near = PyreKingRules.weights(new Read(d, false, false, false, jumped, false, true, d > 3), null, null);
                require(!near.containsKey(Move.SCYTHE_LOW) && !near.containsKey(Move.SCYTHE_HIGH), "He should not use the Scythe on a player " + d + " blocks away");
            }

        // Tempo: the pace the developer's playtest asked for (Nov 11), set without baking the clips again.
        for (PyreKingClips.Clip clip : PyreKingClips.ALL) {
            PyreKingRules.Tempo tempo = PyreKingRules.tempo(clip.name());
            double last = -1;
            for (double seconds = 0; seconds <= clip.seconds(); seconds += .05) {
                double moment = tempo.clip(seconds);
                require(moment > last && near(tempo.seconds(moment), seconds), clip.name() + "'s tempo should run forward and turn back into the same time");
                require(moment >= seconds - 1e-9, clip.name() + " should never play slower than it was made");
                last = moment;
            }
            require(near(tempo.clip(-.1), -.1), "Before a clip begins its time is the blend's own");
        }
        for (String plain : new String[]{"haul", "snare", "unbound", "drag_down", "throned", "awaken", "intro_haul", "idle"})
            require(PyreKingRules.tempo(plain).plain(), plain + " should play as it was made");
        require(Move.CAST.seconds(Move.CAST.free) < 2.2, "A Cast should cost him about two seconds, not four");
        require(Move.CAST.seconds(3.45) - Move.CAST.seconds(1.3) < .5, "He should stand with the sword out for under half a second");
        for (Move move : new Move[]{Move.CAST, Move.HOOK, Move.LUNGE}) {
            // A throw is judged where it ends, at range; the Lunge where its cut begins.
            double warned = move.seconds(move == Move.LUNGE ? move.windows.get(0).from() : move.windows.get(0).to()) - move.seconds(move.aimUntil);
            require(warned >= .25 && warned <= (move == Move.LUNGE ? .8 : .5), move + " should fix its aim late enough that a dash must be timed, with a fair warning: " + warned);
        }
        // The pause before the spinning cut "was really easy to get out" of: from landing to the cut is now a moment.
        for (Move move : new Move[]{Move.ZIP, Move.LUNGE}) {
            PyreKingTracks.Track track = PyreKingTracks.get(move.clip);
            double landed = 0;
            while (track.travel(landed) < track.travelEnd() - .05) landed += .01;
            double pause = move.seconds(move.windows.get(0).from()) - move.seconds(landed);
            require(pause >= 0 && pause < .2, move + " should cut within a fifth of a second of landing, not " + pause);
            require(PyreKingRules.closes(move), move + " brings him to his target, so he presses at once");
        }
        for (int i = 0; i <= 10; i++) {
            double roll = i / 10.0 * .999;
            require(PyreKingRules.gap(false, () -> roll) <= 8 && PyreKingRules.gap(false, () -> roll) >= 3 && PyreKingRules.gap(true, () -> roll) <= 3,
                    "His gap between strings should be under half a second, and next to nothing in phase two");
        }

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
            // The opening after his last blow is tight, as the developer asked, and tighter since their playtest (Nov 11):
            // about half a second (Unbound is a set piece).
            if (move != Move.UNBOUND && move != Move.CAST && !move.hooks() && move != Move.HAUL)
                require(move.opening() <= .7 + 1e-6 && move.opening() >= .3, move + " should leave an opening of 0.3 to 0.7 s, not " + move.opening());
            for (double cue : move.cues) require(cue >= 0 && cue < move.lastBlow(), move + " warns after its last blow");
            // A blow that needs a warning gets one at least 0.3 s before it can land.
            if (!move.windows.isEmpty() && move != Move.ZIP)
                require(move.cues.length > 0 && move.seconds(move.windows.get(0).from()) - move.seconds(move.cues[0]) >= .28, move + " lands without a warning");
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

        // His intro: the baked scene and his two clips share one clock, and the camera stays in the hall.
        PyreKingIntroTrack scene = PyreKingIntroTrack.get();
        require(scene.length() == scene.beat("length") && scene.length() >= 600, "His intro should be long, and as long as its last beat says");
        require(Math.round(PyreKingClips.get("awaken").seconds() * 20) == scene.beat("haul") - scene.beat("awaken"), "awaken must end as his haul down the hall begins");
        // The scene's own haul is made for exactly its distance, and slow enough to carry his weight.
        PyreKingTracks.Track pull = PyreKingTracks.get(PyreKingIntro.HAUL);
        require(near(pull.travelEnd(), scene.runway().distanceTo(scene.mark())), "intro_haul should carry him exactly from the runway to his mark");
        require(scene.beat("haul") + Math.round(PyreKingClips.get(PyreKingIntro.HAUL).seconds() * 20) <= scene.length(), "The scene must not end before intro_haul does");
        double slung = (scene.beat("launch") - scene.beat("haul")) / 20.0, flown = (scene.beat("stopped") - scene.beat("launch")) / 20.0;
        require(pull.travel(slung - .25) == 0 && slung >= 2.5, "He should throw, take the chain and brace before it moves him");
        require(flown >= 1.2 && pull.travel((scene.beat("landed") - scene.beat("haul")) / 20.0) < pull.travelEnd() - 2,
                "He should take over a second to cross the hall, and land short enough to plough to a stop");
        require(PyreKingAim.get(PyreKingIntro.HAUL) == null, "intro_haul is never aimed or stretched");
        require(PyreKingClips.get("throned").loop() && scene.beat("awaken") % Math.round(PyreKingClips.get("throned").seconds() * 20) == 0,
                "The statue's loop must end as awaken begins");
        require(scene.beat("pulse") < scene.beat("burn") && scene.beat("burn") < scene.beat("fall") && scene.beat("fall") < scene.beat("struck")
                && scene.beat("struck") < scene.beat("grasp") && scene.beat("grasp") < scene.beat("ignite") && scene.beat("ignite") < scene.beat("braziers")
                && scene.beat("braziers") < scene.beat("lit") && scene.beat("lit") <= scene.beat("rise") && scene.beat("rise") < scene.beat("title")
                && scene.beat("title_end") <= scene.beat("haul") && scene.beat("haul") < scene.beat("throw") && scene.beat("throw") < scene.beat("stuck")
                && scene.beat("stuck") < scene.beat("grab") && scene.beat("grab") < scene.beat("strain") && scene.beat("strain") < scene.beat("heave")
                && scene.beat("heave") < scene.beat("launch") && scene.beat("launch") < scene.beat("landed") && scene.beat("landed") < scene.beat("stopped")
                && scene.beat("stopped") < scene.beat("take") && scene.beat("take") < scene.beat("tear") && scene.beat("tear") < scene.beat("return")
                && scene.beat("return") < scene.beat("length"), "His intro's beats are out of order");
        require(scene.chains().length >= 15, "His intro needs its web of chains");
        for (PyreKingIntroTrack.Chain chain : scene.chains())
            require(chain.heat() < chain.parts() && chain.parts() <= scene.beat("fall") && chain.gone() < scene.beat("ignite") && chain.a().distanceTo(chain.b()) > 1,
                    "Every chain must heat, part by the time the sword falls, and be ash before the blade lights");
        require(java.util.Arrays.stream(scene.chains()).filter(PyreKingIntroTrack.Chain::sword).count() == 1, "One chain holds the sword");
        require(scene.flames().length >= 300, "The hall's fires and their lights should be in the track");
        for (PyreKingIntroTrack.Flame flame : scene.flames())
            require(flame.tick() >= scene.beat("braziers") && flame.tick() <= scene.beat("lit"), "A fire catches outside the wave: " + flame);
        for (int t = 0; t <= scene.length(); t++) {
            net.minecraft.util.math.Vec3d eye = scene.eye(t);
            require(Math.hypot(eye.x, eye.z) < 60 && eye.y > 1.3 && eye.y < 62 && scene.fov(t) >= 25 && scene.fov(t) <= 75 && Math.abs(scene.pitch(t)) < 89,
                    "The intro's camera leaves the hall or loses its lens at tick " + t + ": " + eye);
        }
        require(scene.dark(0) == 1 && scene.dark(scene.length()) == 0 && scene.ember(0) == 0 && scene.ember(scene.length()) == 1
                && scene.blade(scene.beat("ignite")) == 0 && scene.blade(scene.length()) == 1, "The hall starts dark and he cold; both end lit");
        require(scene.mark().distanceTo(new net.minecraft.util.math.Vec3d(.5, 1, .5)) < 1e-6 && scene.seat().z < -45, "He ends on his mark and starts at his throne");

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
        require(share(far, Move.CAST, random) > share(far, Move.LUNGE, random) && share(far, Move.LUNGE, random) > share(far, Move.HOOK, random)
                && share(distant, Move.HOOK, random) > .5, "Far targets draw the Cast most, very far ones the Hook");
        // A player who keeps their distance is brought to sword range (the developer's playtest, Nov 11: at range he only threw).
        require(share(far, Move.LUNGE, random) + share(far, Move.HOOK, random) > .45, "Far targets should often draw a move that closes the distance");
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
