package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.entity.firstwizard.spell.GroundZone;
import com.anton.elementalwands.entity.firstwizard.spell.SkyShearSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Timing;
import com.anton.elementalwands.entity.firstwizard.spell.WaylayDashSpell;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The contract for the First Wizard's two Wind spells, run from {@link FirstWizardContractTest}: Sky Shear's sizes and
 * the arithmetic of jumping it with vanilla's jump (a timed jump clears each crescent, an early or late one does not,
 * and three in a row are cleared in step), and Waylay Dash's line, its hit and its wake. Both stay on the floor.
 */
final class WindSpellsContract {
    private WindSpellsContract() {}

    /** Vanilla's player jump: 0.42 up, then gravity 0.08 and drag 0.98 a tick. */
    private static final double JUMP = .42, GRAVITY = .08, DRAG = .98;
    /** A player's box: 0.6 wide, 1.8 high. */
    private static final double WIDTH = .6, TALL = 1.8;

    static void run() {
        shear();
        dash();
    }

    // ── Sky Shear ────────────────────────────────────────────────────────────

    private static void shear() {
        Timing t = SkyShearSpell.TIMING;
        require(t.release() >= 16 && t.release() <= 20, "Sky Shear's crescents form for about 0.9 s: " + t.release());
        require(SkyShearSpell.COUNT == 3, "Sky Shear sends three crescents");
        require(SkyShearSpell.leaves(SkyShearSpell.COUNT - 1) < t.free(), "The copy is free only after the last crescent leaves");
        require(SkyShearSpell.HALF * 2 >= 12 && SkyShearSpell.HALF * 2 <= 16, "A crescent is about 14 blocks wide");
        require(SkyShearSpell.SPEED * 20 <= 20.01, "Bigger means slower: a crescent travels no faster than 20 blocks a second");
        require(SkyShearSpell.RANGE >= 36 && SkyShearSpell.RANGE <= 44, "A crescent travels about 40 blocks");
        require(SkyShearSpell.HEIGHT < 1 && SkyShearSpell.CLEAR == .6, "A crescent hugs the floor; feet 0.6 up clear it, as for the Pyre King's low sweep");
        // Each hit of three can land: the gap is longer than the 10-tick hurt cooldown.
        require(SkyShearSpell.GAP > 10, "Crescents leave more than 10 ticks apart, so the hurt cooldown swallows none: " + SkyShearSpell.GAP);

        double[] jump = jumpHeights();
        int air = jump.length;
        // One crescent: which jump starts (ticks before its band reaches a standing body) clear it.
        int arrival = arrival(10);
        require(struck(10, List.of()), "A body that does not jump is struck");
        List<Integer> clear = new ArrayList<>();
        for (int lead = 0; lead <= 20; lead++) if (!struck(10, List.of(arrival - lead))) clear.add(lead);
        require(!clear.isEmpty(), "No jump clears a crescent");
        int first = clear.getFirst(), last = clear.getLast();
        require(last - first + 1 == clear.size(), "The jumps that clear a crescent are one window: " + clear);
        require(clear.size() >= 5, "A jump timed within a quarter of a second clears a crescent; window " + clear + " ticks before it arrives");
        require(first >= 1 && struck(10, List.of(arrival)), "A jump on the tick it arrives does not clear it: window " + clear);
        require(last < air, "A jump that has landed again does not clear it: window " + clear + ", air " + air);
        require(struck(10, List.of(arrival - (last + 2))) && struck(10, List.of(arrival)), "Too early or too late is struck");
        // Three in a row: a jump each at any timing in the window clears all three (the next jump starts after landing).
        require(SkyShearSpell.GAP >= air, "Crescents come no faster than a jump lasts: " + SkyShearSpell.GAP + " < " + air);
        for (int lead : clear) {
            List<Integer> jumps = new ArrayList<>();
            for (int i = 0; i < SkyShearSpell.COUNT; i++) jumps.add(arrival - lead + i * SkyShearSpell.GAP);
            for (int i = 0; i < SkyShearSpell.COUNT; i++)
                require(!struck(10, jumps, i * SkyShearSpell.GAP), "The same timing should clear crescent " + i + " too: lead " + lead);
        }
        // A jump too late for the first (as it arrives) is struck, but lands in time to clear the second by jumping at once.
        int slack = SkyShearSpell.GAP - air;
        List<Integer> late = List.of(arrival, arrival + air);
        require(struck(10, late, 0) && !struck(10, late, SkyShearSpell.GAP),
                "After a late jump the next crescent can still be cleared, jumping on landing: " + slack + " ticks to spare");
        System.out.println("Sky Shear: a jump clears a crescent when it starts " + first + " to " + last + " ticks before it arrives ("
                + clear.size() + " ticks); a jump lasts " + air + " ticks; crescents " + SkyShearSpell.GAP + " ticks apart leave "
                + slack + " to spare after a late jump");

        // Width: a body 6.4 out at full width is struck, 7.6 out is not. Nobody shelters anyone.
        Vec3d origin = new Vec3d(0, 64, 0), dir = new Vec3d(0, 0, 1);
        require(sweeps(body(6.4, 20 - SkyShearSpell.lag(6.1, SkyShearSpell.HALF), 0), origin, dir), "A body near a tip is struck");
        require(!sweeps(body(7.65, 20, 0), origin, dir), "A body beyond the tip is clear");
        require(sweeps(body(0, 20, 0), origin, dir) && sweeps(body(0, 20.7, 0), origin, dir) && sweeps(body(.3, 21.4, 0), origin, dir),
                "Standing behind another player shelters nobody");
        require(!sweeps(body(0, 20, .6), origin, dir) && sweeps(body(0, 20, .55), origin, dir), "Feet 0.6 up are clear; lower are struck");
        // On the floor: wherever it leaves, its run ends on the floor.
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(5);
        for (int i = 0; i < 4000; i++) {
            Vec3d from = arena.onFloor(spot(arena, random), FirstWizardRules.EDGE * .5);
            double a = random.nextDouble() * Math.PI * 2;
            Vec3d d = new Vec3d(Math.cos(a), 0, Math.sin(a));
            double reach = SkyShearSpell.reach(arena, from, d);
            require(reach >= 0 && reach <= SkyShearSpell.RANGE + 1e-9, "A crescent's run is at most its range");
            require(arena.inside(from.add(d.multiply(reach)), .5 - 1e-6), "A crescent runs off the floor: " + from + " " + d);
        }
    }

    /** Feet heights after each tick of a jump from the floor, until it lands. */
    private static double[] jumpHeights() {
        List<Double> heights = new ArrayList<>();
        double y = 0, v = JUMP;
        do { y += v; v = (v - GRAVITY) * DRAG; heights.add(Math.max(0, y)); } while (y > 0 && heights.size() < 40);
        double[] out = new double[heights.size()];
        for (int i = 0; i < out.length; i++) out[i] = heights.get(i);
        return out;
    }

    /** Feet height on tick n of a body that jumps on the given ticks (a jump is ignored while one is in the air). */
    private static double feet(int n, List<Integer> jumps) {
        double[] jump = jumpHeights();
        int airborneUntil = Integer.MIN_VALUE, from = 0;
        for (int start : jumps.stream().sorted().toList()) if (start >= airborneUntil) { from = start; airborneUntil = start + jump.length; if (n >= from && n < airborneUntil) return jump[n - from]; }
        return 0;
    }

    /** The first tick on which a crescent leaving at tick 0 strikes a body standing `distance` along its line. */
    private static int arrival(double distance) {
        for (int n = 0; n < 200; n++) if (SkyShearSpell.strikes(body(0, distance, 0), 64, Vec3d.ZERO.add(0, 64, 0), new Vec3d(0, 0, 1), n * SkyShearSpell.SPEED, (n + 1) * SkyShearSpell.SPEED)) return n;
        throw new AssertionError("A crescent never reached a body on its line");
    }

    private static boolean struck(double distance, List<Integer> jumps) { return struck(distance, jumps, 0); }

    /** Whether a crescent leaving at tick `leaves` strikes a body `distance` along its line that jumps on these ticks. */
    private static boolean struck(double distance, List<Integer> jumps, int leaves) {
        for (int n = 0; n < 200; n++) {
            double d0 = n * SkyShearSpell.SPEED, d1 = (n + 1) * SkyShearSpell.SPEED;
            if (SkyShearSpell.strikes(body(0, distance, feet(n + leaves, jumps)), 64, new Vec3d(0, 64, 0), new Vec3d(0, 0, 1), d0, d1)) return true;
        }
        return false;
    }

    /** Whether a crescent from origin along dir strikes a body at any tick of its run. */
    private static boolean sweeps(Box box, Vec3d origin, Vec3d dir) {
        for (int n = 0; n * SkyShearSpell.SPEED < SkyShearSpell.RANGE; n++)
            if (SkyShearSpell.strikes(box, origin.y, origin, dir, n * SkyShearSpell.SPEED, (n + 1) * SkyShearSpell.SPEED)) return true;
        return false;
    }

    /** A player's box, x sideways and z along a line from the origin, feet this far over the floor at y 64. */
    private static Box body(double x, double z, double up) {
        return new Box(x - WIDTH / 2, 64 + up, z - WIDTH / 2, x + WIDTH / 2, 64 + up + TALL, z + WIDTH / 2);
    }

    // ── Waylay Dash ──────────────────────────────────────────────────────────

    private static void dash() {
        Timing t = WaylayDashSpell.TIMING;
        require(t.aimLock() <= 2, "Waylay Dash's line is fixed as the tell begins, so it forms where it will appear");
        require(t.release() >= 8 && t.release() <= 12, "Waylay Dash's tell lasts about half a second: " + t.release());
        require(WaylayDashSpell.DASH < 10, "It crosses its line in under half a second");
        require(WaylayDashSpell.MAX_LENGTH >= 22 && WaylayDashSpell.MAX_LENGTH <= 30, "The line runs at most about 26 blocks");
        int burns = WaylayDashSpell.WAKE_LIFE - FirstWizardRules.GROUND_ARM;
        require(burns >= 50 && burns <= 70, "The wake burns for about 3 s: " + burns);
        require(WaylayDashSpell.WAKE_HALF * 2 >= 1.6 && WaylayDashSpell.WAKE_HALF * 2 <= 2.4, "The wake is about 2 blocks wide");
        require(t.release() + WaylayDashSpell.DASH <= t.release() + FirstWizardRules.GROUND_ARM, "The wake arms only once the copy has crossed it");
        // Passing through: on the line it hits, beside it neither the dash nor the wake does, and one step leaves the wake.
        Vec3d a = new Vec3d(0, 64, 0), b = new Vec3d(0, 64, 22);
        require(WaylayDashSpell.passes(body(0, 10, 0), 64, a, b) && WaylayDashSpell.passes(body(.6, 10, 0), 64, a, b), "A body on its line is passed through");
        require(!WaylayDashSpell.passes(body(2.5, 10, 0), 64, a, b), "A body 2.5 beside its line is not");
        require(GroundZone.reach(body(2.5, 10, 0), a, b) > WaylayDashSpell.WAKE_HALF, "A body 2.5 beside its line is clear of the wake");
        require(GroundZone.reach(body(0, 10, 0), a, b) <= WaylayDashSpell.WAKE_HALF, "A body on its line stands in the wake");
        require(GroundZone.reach(body(WaylayDashSpell.WAKE_HALF + WIDTH / 2 + .05, 10, 0), a, b) > WaylayDashSpell.WAKE_HALF, "A step sideways from the line leaves the wake");
        // Its line: through the target and on, at most MAX_LENGTH, ending on the floor.
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(9);
        int through = 0, room = 0;
        for (int i = 0; i < 4000; i++) {
            // It keeps itself EDGE inside, and is only set back past EDGE / 2.
            Vec3d from = arena.onFloor(spot(arena, random), random.nextBoolean() ? FirstWizardRules.EDGE : FirstWizardRules.EDGE * .5);
            Vec3d target = arena.onFloor(spot(arena, random), 0);
            Vec3d end = WaylayDashSpell.end(arena, from, target);
            double length = end.subtract(from).horizontalLength(), distance = target.subtract(from).horizontalLength();
            require(arena.inside(end, FirstWizardRules.EDGE - 1e-6) && end.y == arena.floor(), "A dash ends off the floor: " + from + " -> " + end);
            require(length <= WaylayDashSpell.MAX_LENGTH + 1e-6, "A dash longer than its most: " + length);
            if (distance + 1 <= WaylayDashSpell.MAX_LENGTH && arena.inside(from.add(target.subtract(from).normalize().multiply(distance + 1)), FirstWizardRules.EDGE)) {
                room++;
                if (length >= distance + .9) through++;
            }
        }
        require(through == room, "With room on the floor, a dash goes through its target and on: " + through + " of " + room);
    }

    private static Vec3d spot(Arena arena, Random random) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * arena.radius();
        return new Vec3d(arena.centre().x + Math.cos(a) * r, arena.floor(), arena.centre().z + Math.sin(a) * r);
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
