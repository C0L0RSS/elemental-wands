package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Read;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.anton.elementalwands.entity.firstwizard.spell.FlameSpell;
import com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell;
import com.anton.elementalwands.entity.firstwizard.spell.LeapSpell;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import com.anton.elementalwands.entity.firstwizard.spell.SlamSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Tendrils;
import com.anton.elementalwands.entity.firstwizard.spell.Timing;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntToDoubleFunction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The contract for phase two, run from {@link FirstWizardContractTest}: the change's beats and its full sweep, each ink
 * move's warning, dodge and damage, the low sweeps held to vanilla's jump, which moves a phase may use (Hollow Purple
 * only at low health and not more often than every 25 s), the Leap-then-Flame combination, phase two's pace, and that
 * every line, circle and lane stays on the floor.
 */
final class InkMovesContract {
    private InkMovesContract() {}

    /** Vanilla's player jump: 0.42 up, then gravity 0.08 and drag 0.98 a tick. */
    private static final double JUMP = .42, GRAVITY = .08, DRAG = .98;
    /** Blocks a second: a walk and a sprint. */
    private static final double WALK = 4.3, SPRINT = 5.6;

    static void run() {
        table();
        change();
        sweep();
        slam();
        leap();
        flame();
        purple();
        pace();
        floor();
    }

    private static void table() {
        for (Move move : Move.values()) {
            require(move.phase == (move.spell() ? 1 : 2), move + ": the ten spells are from phase one, the ink moves phase two's");
            require(BossSpells.get(move) != null, move + " is not built");
        }
        Set<Move> built = BossSpells.available();
        Set<Move> one = FirstWizardRules.usable(built, false, .2f, 100000), two = FirstWizardRules.usable(built, true, .2f, 100000);
        for (Move move : one) require(move.spell(), "Phase one used " + move);
        require(two.containsAll(one) && two.contains(Move.SLAM) && two.contains(Move.HOLLOW_PURPLE), "Phase two keeps the spells and adds the ink moves");
        require(FirstWizardRules.opener(com.anton.elementalwands.data.WizardAffinity.NONE, built).spell(), "It never opens with an ink move");
    }

    /**
     * The change: its cinematic's beats in order with room, a mid-fight length, the baked track in step with them, the
     * tell and the full sweep only after control returns, the wound always on the left, and its full circle is jumped.
     */
    private static void change() {
        Timing t = PhaseChange.TIMING;
        require(t.consistent() && t.warning() >= 80, "The change gives its beats room: " + t);
        require(PhaseChange.WRENCH < PhaseChange.TEAR && PhaseChange.TEAR < PhaseChange.SNAP && PhaseChange.SNAP < PhaseChange.LANDS
                && PhaseChange.LANDS < PhaseChange.MELT_FROM && PhaseChange.MELT_FROM < PhaseChange.MELTED, "The arm's beats run in order");
        require(PhaseChange.STAGGER < PhaseChange.TEAR && PhaseChange.BULGE_FROM < PhaseChange.BEND_FROM && PhaseChange.BEND_FROM < PhaseChange.BENT
                && PhaseChange.BENT <= PhaseChange.BURST && PhaseChange.GROW_FROM == PhaseChange.BURST && PhaseChange.MELTED <= PhaseChange.BURST,
                "The swelling and the head's bend run in order, and the tentacles come out as it bursts");
        require(PhaseChange.GROW_FROM <= PhaseChange.PLANT && PhaseChange.PLANT < PhaseChange.LIFTED && PhaseChange.PLANT <= PhaseChange.GROW_TO
                && PhaseChange.BURST < PhaseChange.LIFT_FROM && PhaseChange.LIFT_FROM < PhaseChange.LIFTED && PhaseChange.LIFTED <= PhaseChange.RETURN
                && PhaseChange.RETURN < PhaseChange.CINEMATIC, "It lifts after the burst and before the camera returns");
        // Unhurried, but a mid-fight beat: 12 to 18 seconds; each beat with room.
        require(PhaseChange.CINEMATIC >= 240 && PhaseChange.CINEMATIC <= 360, "The cinematic lasts 12 to 18 s: " + PhaseChange.CINEMATIC / 20.0);
        require(PhaseChange.TEAR - PhaseChange.WRENCH >= 60 && PhaseChange.BURST - PhaseChange.BULGE_FROM >= 60 && PhaseChange.BENT - PhaseChange.BEND_FROM >= 40
                && PhaseChange.MELTED - PhaseChange.MELT_FROM >= 30, "The wrench, the swelling, the bend and the melt each get their seconds");
        // The tell and the sweep are gameplay: they start once control is back, with the same warning as before.
        require(PhaseChange.RISE < PhaseChange.TELL && PhaseChange.CINEMATIC <= PhaseChange.TELL && PhaseChange.TELL < PhaseChange.SWEEP_FROM, "The tell follows the cinematic");
        require(PhaseChange.SWEEP_FROM - PhaseChange.CINEMATIC >= 16 && PhaseChange.SWEEP_FROM - PhaseChange.TELL >= 16, "The full sweep is told after control returns");
        require(PhaseChange.RIPPLE_TO <= PhaseChange.SWEEP_FROM && PhaseChange.RIPPLE_TO - PhaseChange.RIPPLE_FROM >= 8 && PhaseChange.RIPPLE_FROM >= PhaseChange.CINEMATIC,
                "The ripple warns before the full sweep, where the players can see it");
        require(SweepSpell.woundSide() == 1, "The wound is always on its left");
        track();
        require(Math.abs(PhaseChange.angleAt(PhaseChange.SWEEP_FROM + PhaseChange.SWEEP, 0, 1) - 2 * Math.PI) < 1e-9, "The sweep goes a full circle");
        double start = 1.1;
        IntToDoubleFunction angle = tick -> PhaseChange.angleAt(PhaseChange.SWEEP_FROM + tick, start, 1);
        // Every bearing round it, near and far: a standing body is struck, a timed jump clears it. (A body right where
        // the circle starts and ends is passed twice, 1.5 s apart, and jumps twice.)
        for (int b = 0; b < 16; b++) for (double d : new double[]{2.2, 5, 8.6}) {
            double a = start + .55 + b * (2 * Math.PI - 1.1) / 15;
            List<Integer> window = jumpWindow(angle, PhaseChange.SWEEP, a, d, Tendrils.REACH);
            require(window.size() >= 5, "A jump clears the change's sweep at bearing " + b + ", " + d + " out: window " + window);
        }
        require(!struckAt(angle, PhaseChange.SWEEP, start + 1, 1, Tendrils.REACH, List.of()), "Under its arch the sweep misses");
        require(!struckAt(angle, PhaseChange.SWEEP, start + 1, Tendrils.REACH + .5, Tendrils.REACH, List.of()), "Beyond its reach the sweep misses");
    }

    /** The baked cinematic (art/first_wizard/change/build_change.py) in step with PhaseChange: its beats, its camera, its body. */
    private static void track() {
        var scene = FirstWizardChangeTrack.get().scene();
        java.util.Map<String, Integer> beats = java.util.Map.ofEntries(
                java.util.Map.entry("stagger", PhaseChange.STAGGER), java.util.Map.entry("wrench", PhaseChange.WRENCH), java.util.Map.entry("tear", PhaseChange.TEAR),
                java.util.Map.entry("snap", PhaseChange.SNAP), java.util.Map.entry("lands", PhaseChange.LANDS), java.util.Map.entry("melt_from", PhaseChange.MELT_FROM),
                java.util.Map.entry("melted", PhaseChange.MELTED), java.util.Map.entry("bulge_from", PhaseChange.BULGE_FROM), java.util.Map.entry("bend_from", PhaseChange.BEND_FROM),
                java.util.Map.entry("bent", PhaseChange.BENT), java.util.Map.entry("burst", PhaseChange.BURST), java.util.Map.entry("grow_from", PhaseChange.GROW_FROM),
                java.util.Map.entry("lift_from", PhaseChange.LIFT_FROM), java.util.Map.entry("lifted", PhaseChange.LIFTED), java.util.Map.entry("return", PhaseChange.RETURN),
                java.util.Map.entry("length", PhaseChange.CINEMATIC));
        for (var beat : beats.entrySet())
            require(scene.beat(beat.getKey()) == beat.getValue(), "The baked change's beat " + beat.getKey() + " is " + scene.beat(beat.getKey()) + ", PhaseChange's " + beat.getValue() + ": run build_change.py --install");
        require(scene.length() == PhaseChange.CINEMATIC, "The baked change ends when control returns");
        // The swelling surges on the heartbeat the server plays.
        for (int beat = PhaseChange.HEARTBEAT_FROM; beat < PhaseChange.BURST; beat += PhaseChange.HEARTBEAT)
            require(scene.value("bulge", beat + 4) > scene.value("bulge", beat - 1), "The swelling surges on the heartbeat at " + beat);
        // The arm: on until it snaps, down on the floor clear of her feet before it melts, gone into the floor after.
        Vec3d lying = scene.point("arm", PhaseChange.MELT_FROM);
        require(Math.abs(lying.y - .125) < .02 && Math.hypot(lying.x, lying.z) > .55, "The torn arm lies on the floor clear of her feet before it melts: " + lying);
        require(scene.point("arm", PhaseChange.MELTED).y < 0 && scene.value("melt", PhaseChange.MELTED) >= .999, "It has melted into the floor by MELTED");
        require(scene.value("bulge", PhaseChange.BURST - 1) > .8 && scene.value("bulge", PhaseChange.BURST) == 0, "The swelling is at its fullest as it bursts, and gone after");
        // The head: bent to its peak further than phase two keeps it, and settled there when control returns.
        double peak = 0;
        for (int tick = 0; tick <= PhaseChange.CINEMATIC; tick++) peak = Math.max(peak, scene.value("bend", tick));
        double two = FirstWizardChangeTrack.get().bendTwo();
        require(peak > Math.toRadians(55) && two < peak && two > Math.toRadians(30), "The head bends unnaturally far, and stays a little less bent: peak " + Math.toDegrees(peak) + ", phase two " + Math.toDegrees(two));
        require(Math.abs(scene.value("bend", PhaseChange.CINEMATIC) - two) < .01 && scene.value("lift", PhaseChange.CINEMATIC) == 1, "Phase two's body when control returns");
        // The camera: close (within a few blocks of her) except the wide burst, and never under the floor.
        for (int tick = 0; tick <= PhaseChange.CINEMATIC; tick++) {
            Vec3d eye = scene.eye(tick);
            require(eye.y > .2 && eye.horizontalLength() < 9, "The camera at " + tick + " is under the floor or far off: " + eye);
        }
        System.out.println("Change: cinematic " + PhaseChange.CINEMATIC / 20.0 + " s, the arm lies at " + lying + ", the head's bend peaks at "
                + Math.round(Math.toDegrees(peak)) + " degrees and stays at " + Math.round(Math.toDegrees(two)));
    }

    private static void sweep() {
        Timing t = SweepSpell.TIMING;
        require(t.warning() >= 14 && t.release() - t.aimLock() >= 3, "The Sweep's windup is readable and its arc fixed before it swings: " + t);
        require(SweepSpell.RIPPLE_TO <= t.release(), "The Sweep's ripple ends before it swings");
        require(Math.toDegrees(SweepSpell.ARC) * 2 >= 180, "The Sweep's arc is wide");
        require(Tendrils.REACH >= 8.5 && Tendrils.REACH <= 9.5, "The Sweep reaches about 9 blocks");
        IntToDoubleFunction angle = tick -> SweepSpell.angleAt(t.release() + tick, 0, 1);
        for (double bearing : new double[]{-1.5, -.6, 0, .7, 1.6}) for (double d : new double[]{2.5, 5, 8.4}) {
            List<Integer> window = jumpWindow(angle, SweepSpell.SWING, bearing, d, Tendrils.REACH);
            require(window.size() >= 5, "A timed jump clears the Sweep at " + bearing + ", " + d + " out: " + window);
            require(window.getFirst() >= 1, "A jump on the tick it arrives is too late: " + window);
        }
        require(!struckAt(angle, SweepSpell.SWING, Math.PI, 4, Tendrils.REACH, List.of()), "Behind the copy, the Sweep misses");
        require(!struckAt(angle, SweepSpell.SWING, 0, Tendrils.REACH + .4, Tendrils.REACH, List.of()), "Out of its reach, the Sweep misses");
        System.out.println("Sweep: a timed jump clears it; window " + jumpWindow(angle, SweepSpell.SWING, 0, 5, Tendrils.REACH) + " ticks before it arrives 5 out");
    }

    private static void slam() {
        Timing t = SlamSpell.TIMING;
        require(t.warning() >= 18, "The Slam rises for about a second: " + t);
        double lockToHit = (t.release() - t.aimLock()) / 20.0;
        require(lockToHit * WALK >= SlamSpell.HALF + .3 + .2, "A walk sideways after the Slam's aim lock clears its line: " + lockToHit + " s");
        require(lockToHit <= .5, "The Slam commits soon after its lock");
        require(SlamSpell.LENGTH >= 11 && SlamSpell.LENGTH <= 13 && SlamSpell.HALF * 2 == 2, "The Slam's line is about 12 long and 2 wide");
        Vec3d from = new Vec3d(SlamSpell.START, 64, 0), to = new Vec3d(SlamSpell.LENGTH, 64, 0);
        require(Tendrils.onLine(body(6, 0, 0), 64, from, to, SlamSpell.HALF, SlamSpell.REACH_UP), "A body on the line is struck");
        require(Tendrils.onLine(body(6, 0, 1.2), 64, from, to, SlamSpell.HALF, SlamSpell.REACH_UP), "A jump does not clear the Slam");
        require(!Tendrils.onLine(body(6, 1.35, 0), 64, from, to, SlamSpell.HALF, SlamSpell.REACH_UP), "A step aside clears it");
    }

    private static void leap() {
        Timing t = LeapSpell.TIMING;
        double warning = (LeapSpell.lands() - t.aimLock()) / 20.0;
        require(warning * WALK >= LeapSpell.RADIUS + .3, "Walking out of the Leap's circle from its middle clears it: " + warning + " s");
        require(LeapSpell.RIPPLE_TO <= t.release(), "The Leap's ripple runs before it launches");
        require(Tendrils.inCircle(body(2.8, 0, 1), 64, new Vec3d(0, 64, 0), LeapSpell.RADIUS, LeapSpell.REACH_UP), "A jump does not clear the landing");
        require(!Tendrils.inCircle(body(LeapSpell.RADIUS + .4, 0, 0), 64, new Vec3d(0, 64, 0), LeapSpell.RADIUS, LeapSpell.REACH_UP), "Out of the circle is clear");
        require(FirstWizardRules.FOLLOW_UPS.stream().anyMatch(f -> f.first() == Move.LEAP && f.then() == Move.FLAME && f.chance() >= .5),
                "The Leap is often followed by the Flame");
        // In the air the copy is over the floor, landing on it.
        Vec3d a = new Vec3d(0, 64, 0), b = new Vec3d(10, 64, 0);
        for (int i = 0; i <= LeapSpell.FLIGHT; i++) require(LeapSpell.arc(a, b, i / (double) LeapSpell.FLIGHT).y >= 64 - 1e-9, "The Leap dips under the floor");
        require(LeapSpell.arc(a, b, 1).y == 64, "It lands on the floor");
    }

    private static void flame() {
        Timing t = FlameSpell.TIMING;
        require(t.release() - t.formFrom() >= 10, "Fire gathers in its hand for at least half a second");
        require(FlameSpell.BREATH >= 30 && FlameSpell.BREATH <= 60, "It breathes for a couple of seconds");
        for (double toughness : new double[]{8, 12}) {
            double tick = armored(FirstWizardRules.FLAME, 20, toughness), ground = armored(FirstWizardRules.GROUND, 20, toughness);
            double perSecond = tick * 20 / FirstWizardRules.FLAME_EVERY, whole = tick * Math.ceil(FlameSpell.BREATH / (double) FirstWizardRules.FLAME_EVERY);
            require(perSecond >= ground * 1.6, "The cone hurts clearly more than burning ground: " + perSecond + " a second against " + ground);
            require(whole < 20 && whole + armored(FirstWizardRules.HEAVY, 20, toughness) >= 15, "The whole breath is no kill in diamond or netherite, but with the landing it nearly is: " + whole);
        }
        // Behind it is safe; sprinting round it close in outruns the turn.
        Vec3d apex = new Vec3d(0, 64, 0), dir = new Vec3d(0, 0, 1);
        double half = Math.toRadians(FlameSpell.HALF_ANGLE);
        require(Tendrils.inCone(body(0, 6, 0), 64, apex, dir, FlameSpell.REACH, half, FlameSpell.REACH_UP), "In front, in the cone");
        require(!Tendrils.inCone(body(0, -3, 0), 64, apex, dir, FlameSpell.REACH, half, FlameSpell.REACH_UP), "Behind it, clear");
        require(!Tendrils.inCone(body(0, FlameSpell.REACH + .5, 0), 64, apex, dir, FlameSpell.REACH, half, FlameSpell.REACH_UP), "Out of reach, clear");
        // Straight behind it at the start: the cone's edge (and the body's width) never comes round to them before it ends.
        double behindFor = (Math.PI - half - Math.asin(.3 / 3)) / Math.toRadians(FlameSpell.TURN * 20);
        require(behindFor > FlameSpell.ends() / 20.0, "A player straight behind it is never turned on before the breath ends: " + behindFor + " s");
        require(SPRINT / 5 > Math.toRadians(FlameSpell.TURN * 20), "Sprinting round it 5 blocks out outruns the cone");
        require(FlameSpell.CLOUD_LIFE >= 200, "The cloud it leaves lasts long enough to bottle");
    }

    private static void purple() {
        Timing t = HollowPurpleSpell.TIMING;
        for (var spell : BossSpells.ALL)
            if (spell.move() != Move.HOLLOW_PURPLE) require(spell.timing().warning() < t.warning(), "Hollow Purple forms longest; " + spell.move() + " forms as long");
        require(t.warning() >= 56 && t.warning() <= 64, "Hollow Purple forms for about 3 s: " + t.warning());
        require(t.release() - t.aimLock() >= 11 && t.release() - t.aimLock() <= 15, "Its aim locks a little over half a second before release");
        require(HollowPurpleSpell.RADIUS * 2 >= 10, "The orb is at least the player's ten blocks across");
        require(HollowPurpleSpell.SPEED * 20 < 26, "Bigger means slower: it rolls slower than the player's 26 blocks a second");
        for (double toughness : new double[]{8, 12}) {
            double purple = armored(FirstWizardRules.PURPLE, 20, toughness), heavy = armored(FirstWizardRules.HEAVY, 20, toughness);
            require(purple > heavy + 2 && purple < 16, "Hollow Purple is more than a heavy hit and no kill alone: " + purple);
        }
        // Leaving the lane: far out, after the lock; close in, by circling out before it (the lane turns slower than a sprint there).
        double escape = (HollowPurpleSpell.RADIUS + .3) / SPRINT;
        for (double d = 15; d <= 40; d += 1) {
            double arrive = (t.release() - t.aimLock()) / 20.0 + Math.max(0, d - HollowPurpleSpell.START - HollowPurpleSpell.RADIUS) / (HollowPurpleSpell.SPEED * 20);
            require(arrive >= escape, "A sprint aside after the lock leaves the lane " + d + " out: " + arrive + " s against " + escape);
        }
        for (double d = 3; d <= 14; d += 1) {
            double gain = SPRINT / d - Math.toRadians(HollowPurpleSpell.TURN * 20), half = Math.atan2(HollowPurpleSpell.RADIUS + .3, d);
            require(gain > 0 && half / gain <= t.aimLock() / 20.0, "Circling " + d + " out leaves the lane before it locks: " + half / gain + " s");
        }
        Vec3d origin = new Vec3d(0, 64, 0), dir = new Vec3d(1, 0, 0);
        require(HollowPurpleSpell.touches(body(10, 0, 0), 64, origin, dir, 10), "The orb strikes a body it reaches");
        require(HollowPurpleSpell.touches(body(10, 0, 1.2), 64, origin, dir, 10), "A jump does not clear it");
        require(!HollowPurpleSpell.touches(body(20, HollowPurpleSpell.RADIUS + .5, 0), 64, origin, dir, 20), "Out of the lane, clear");
        // Rarity: only at or below a quarter of its health, and never within 25 s of the last.
        Set<Move> built = BossSpells.available();
        require(!FirstWizardRules.usable(built, true, .26f, 100000).contains(Move.HOLLOW_PURPLE), "Not above a quarter health");
        require(!FirstWizardRules.usable(built, true, .2f, FirstWizardRules.PURPLE_EVERY - 1).contains(Move.HOLLOW_PURPLE), "Not within 25 s of the last");
        require(FirstWizardRules.usable(built, true, .25f, FirstWizardRules.PURPLE_EVERY).contains(Move.HOLLOW_PURPLE), "At a quarter health, after the wait, it may come");
        require(!FirstWizardRules.usable(built, false, .1f, 100000).contains(Move.HOLLOW_PURPLE), "Never in phase one");
        require(FirstWizardRules.PURPLE_EVERY >= 480, "Hollow Purple waits about 25 s");
        // A long fight of picks at low health: never two within the wait.
        Random random = new Random(3);
        long lastPurple = -100000;
        Move last = null, before = null;
        int seen = 0;
        for (long tick = 0; tick < 20 * 600; tick += 30) {
            float health = tick < 20 * 120 ? .5f : .2f;
            Set<Move> usable = FirstWizardRules.usable(built, true, health, tick - lastPurple);
            Move next = FirstWizardRules.choose(new Read(6 + random.nextDouble() * 30, false, false, false, false, false, false), last, before, usable, true, random::nextDouble);
            if (next == null) continue;
            if (next == Move.HOLLOW_PURPLE) {
                require(health <= FirstWizardRules.PURPLE_AT && tick - lastPurple >= FirstWizardRules.PURPLE_EVERY, "Hollow Purple came too soon or too early at " + tick);
                lastPurple = tick; seen++;
            }
            before = last; last = next;
        }
        require(seen >= 3, "At low health the finisher does come: " + seen + " in eight minutes");
    }

    /** Phase two moves faster and closer, with shorter gaps; up close the tendrils dominate. */
    private static void pace() {
        require(FirstWizardRules.DRIFT > FirstWizardRules.WALK && FirstWizardRules.DRIFT < SPRINT, "It drifts a little faster than it walked and slower than a sprint");
        require(FirstWizardRules.DRIFT_EASE > .05 && FirstWizardRules.DRIFT_EASE < .5, "It eases into a drift within about half a second");
        require(FirstWizardRules.HOLD_FAR_TWO < FirstWizardRules.HOLD_FAR && FirstWizardRules.HOLD_NEAR_TWO < FirstWizardRules.HOLD_NEAR, "It holds closer in phase two");
        for (double r : new double[]{0, .5, .999})
            require(FirstWizardRules.gap(1, () -> r, true) < FirstWizardRules.gap(1, () -> r, false), "Phase two's gaps are shorter");
        Set<Move> two = FirstWizardRules.usable(BossSpells.available(), true, .6f, 100000);
        var close = FirstWizardRules.weights(Read.at(4), null, null, two, true);
        double ink = close.entrySet().stream().filter(e -> !e.getKey().spell()).mapToDouble(java.util.Map.Entry::getValue).sum();
        double spells = close.entrySet().stream().filter(e -> e.getKey().spell()).mapToDouble(java.util.Map.Entry::getValue).sum();
        require(ink > spells * 2, "Up close the tendrils dominate: ink " + ink + ", spells " + spells);
        var far = FirstWizardRules.weights(Read.at(20), null, null, two, true);
        require(far.keySet().stream().anyMatch(Move::spell), "At range it still casts its spells");
        require(FirstWizardRules.LIFT > .3 && FirstWizardRules.LIFT < 1, "It hangs a little off the ground");
    }

    /** Every line, circle and lane on the floor. */
    private static void floor() {
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(9);
        for (int i = 0; i < 3000; i++) {
            Vec3d from = arena.onFloor(spot(arena, random, 1.05), FirstWizardRules.EDGE);
            Vec3d led = arena.onFloor(spot(arena, random, 1.1), 0);
            double a = random.nextDouble() * Math.PI * 2;
            Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
            Vec3d slam = SlamSpell.end(arena, from, dir), lane = HollowPurpleSpell.end(arena, from, dir), land = LeapSpell.landing(arena, from, led);
            require(arena.inside(slam, .5 - 1e-6) && slam.y == arena.floor(), "A Slam's line off the floor: " + slam);
            require(arena.inside(lane, .5 - 1e-6) && lane.y == arena.floor(), "A lane off the floor: " + lane);
            require(arena.inside(land, FirstWizardRules.EDGE) && land.y == arena.floor(), "A Leap landing off the floor: " + land);
            require(land.subtract(from).horizontalLength() <= LeapSpell.RANGE + 1e-6, "A Leap further than its range");
        }
    }

    // ── Jumping a low sweep ──────────────────────────────────────────────────

    private static double[] jumpHeights() {
        List<Double> heights = new ArrayList<>();
        double y = 0, v = JUMP;
        do { y += v; v = (v - GRAVITY) * DRAG; heights.add(Math.max(0, y)); } while (y > 0 && heights.size() < 40);
        double[] out = new double[heights.size()];
        for (int i = 0; i < out.length; i++) out[i] = heights.get(i);
        return out;
    }

    /** Whether a body at this bearing and distance, jumping on the given tick (or never), is struck by a sweep of `ticks` ticks. */
    private static boolean struckAt(IntToDoubleFunction angle, int ticks, double bearing, double d, double reach, List<Integer> jumps) {
        double[] jump = jumpHeights();
        for (int n = 1; n <= ticks; n++) {
            double feet = 0;
            for (int start : jumps) if (n - 1 >= start && n - 1 - start < jump.length) feet = jump[n - 1 - start];
            Box box = body(Math.cos(bearing) * d, Math.sin(bearing) * d, feet);
            if (Tendrils.sweeps(box, 64, new Vec3d(0, 64, 0), angle.applyAsDouble(n - 1), angle.applyAsDouble(n), reach)) return true;
        }
        return false;
    }

    /** The jump starts (ticks before the first tick a standing body is struck) that clear it. */
    private static List<Integer> jumpWindow(IntToDoubleFunction angle, int ticks, double bearing, double d, double reach) {
        int arrival = -1;
        for (int n = 1; n <= ticks && arrival < 0; n++) {
            Box box = body(Math.cos(bearing) * d, Math.sin(bearing) * d, 0);
            if (Tendrils.sweeps(box, 64, new Vec3d(0, 64, 0), angle.applyAsDouble(n - 1), angle.applyAsDouble(n), reach)) arrival = n;
        }
        require(arrival > 0, "A standing body at " + bearing + ", " + d + " out is never struck");
        List<Integer> clear = new ArrayList<>();
        for (int lead = 0; lead <= 16; lead++) if (!struckAt(angle, ticks, bearing, d, reach, List.of(arrival - 1 - lead))) clear.add(lead);
        return clear;
    }

    /** A player's box with its feet at (x, 64 + up, z). */
    private static Box body(double x, double z, double up) { return new Box(x - .3, 64 + up, z - .3, x + .3, 64 + up + 1.8, z + .3); }

    private static Vec3d spot(Arena arena, Random random, double reach) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * arena.radius() * reach;
        return new Vec3d(arena.centre().x + Math.cos(a) * r, arena.floor(), arena.centre().z + Math.sin(a) * r);
    }

    private static double armored(double damage, double armor, double toughness) {
        double held = Math.clamp(armor - 4 * damage / (toughness + 8), armor / 5, 20);
        return damage * (1 - held / 25);
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
