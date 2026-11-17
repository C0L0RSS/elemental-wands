package com.anton.elementalwands.util;

import static com.anton.elementalwands.util.PyreWaveRules.LANES;
import static com.anton.elementalwands.util.PyreWaveRules.LENGTH;
import static com.anton.elementalwands.util.PyreWaveRules.NONE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Dragon's Pyre's ground path over made-up terrain; run by the Gradle check task without launching Minecraft. */
public final class PyreWaveContractTest {
    /** The floor's top block; a caster stands at y 64. */
    private static final int FLOOR = 63;
    private static int checked;

    public static void main(String[] args) {
        // Level ground: every lane runs its full length on the floor.
        var flat = run(floor(), 0);
        for (int lane = 0; lane < LANES; lane++) {
            for (int step = 1; step <= LENGTH; step++) expect(flat.ground(step, lane) == FLOOR, "level floor, lane " + lane + " step " + step);
        }

        // Aimed back from the lip of a 2-block ledge, the caster's centre over the drop: it starts on the ledge.
        var ledge = floor().box(-6, FLOOR - 2, 1, 6, FLOOR, 60, 0).box(-6, FLOOR - 2, 1, 6, FLOOR - 2, 60, 1);
        var lip = new PyreWaveRules.Tracer(ledge, new Vec3d(.5, 64, 1.2), PyreWaveRules.heading(180));
        lip.advance(LENGTH);
        for (int lane = 0; lane < LANES; lane++) expect(lip.ground(1, lane) == FLOOR && lip.ground(10, lane) == FLOOR, "ledge lip, lane " + lane);

        // A river flush with the ground, and a pool sunk a block below it, end the wall at their bank.
        var river = run(floor().water(-6, FLOOR, 10, 6, FLOOR, 14), 0);
        var sunk = run(floor().box(-6, FLOOR, 10, 6, FLOOR, 14, 0).water(-6, FLOOR - 1, 10, 6, FLOOR - 1, 14), 0);
        for (var pool : new PyreWaveRules.Tracer[]{river, sunk}) {
            for (int lane = 0; lane < LANES; lane++) {
                expect(pool.ground(9, lane) == FLOOR, "bank before a pool, lane " + lane);
                expect(pool.ground(10, lane) == NONE && pool.ground(20, lane) == NONE, "fire beyond a pool, lane " + lane);
            }
            expect(!pool.covers(20, new Vec3d(.5, 64, 20.5), 0), "a pool's far bank is hit");
        }

        // A 2-high wall on a 1-thick floor over a cellar: the fire stops at it, not under the floor.
        var cellar = floor().box(-6, FLOOR - 3, -2, 6, FLOOR - 3, 60, 1).box(-6, FLOOR - 2, -2, 6, FLOOR - 1, 60, 0)
                .box(-6, FLOOR + 1, 10, 6, FLOOR + 2, 10, 1);
        var walled = run(cellar, 0);
        for (int lane = 0; lane < LANES; lane++) {
            for (int step = 10; step <= LENGTH; step++) expect(walled.ground(step, lane) == NONE, "fire past a wall over a cellar, lane " + lane + " step " + step);
        }
        expect(!walled.covers(15, new Vec3d(.5, 64, 15.5), 0), "a target beyond the wall is hit");

        // Drops: two blocks down through open air lands; a hole deeper than three is crossed and lit again beyond.
        var drop = run(floor().box(-6, FLOOR - 1, 10, 6, FLOOR, 60, 0).box(-6, FLOOR - 2, 10, 6, FLOOR - 2, 60, 1), 0);
        for (int lane = 0; lane < LANES; lane++) expect(drop.ground(10, lane) == FLOOR - 2 && drop.ground(30, lane) == FLOOR - 2, "2-block drop, lane " + lane);
        var hole = run(floor().box(-6, FLOOR - 6, 10, 6, FLOOR, 11, 0), 0);
        for (int lane = 0; lane < LANES; lane++) {
            expect(hole.ground(10, lane) == NONE && hole.ground(11, lane) == NONE, "fire in a deep hole, lane " + lane);
            expect(hole.ground(12, lane) == FLOOR && hole.ground(LENGTH, lane) == FLOOR, "fire beyond a deep hole, lane " + lane);
        }

        // A one-block step up is climbed; a two-block one ends the wall.
        var steps = run(floor().box(-6, FLOOR + 1, 10, 6, FLOOR + 1, 60, 1).box(-6, FLOOR + 2, 20, 6, FLOOR + 3, 60, 1), 0);
        for (int lane = 0; lane < LANES; lane++) {
            expect(steps.ground(10, lane) == FLOOR + 1 && steps.ground(19, lane) == FLOOR + 1, "one-block step, lane " + lane);
            expect(steps.ground(20, lane) == NONE, "two-block step, lane " + lane);
        }

        // A pillar in the centre column shields what is behind it and nothing beside it, and fire just in front
        // of it still hits. Lanes 4 and 5 run over x = 0 from x = .5 facing +z.
        var pillar = run(floor().box(0, FLOOR + 1, 10, 0, FLOOR + 3, 10, 1), 0);
        expect(pillar.ground(11, 4) == NONE && pillar.ground(11, 5) == NONE && pillar.ground(11, 3) == FLOOR, "pillar lanes");
        expect(!pillar.covers(11, new Vec3d(.8, 64, 11.5), -.3), "a target behind the pillar is hit");
        expect(pillar.covers(11, new Vec3d(-.24, 64, 11.5), .74), "a target beside the pillar is missed");
        expect(pillar.covers(10, new Vec3d(.5, 64, 9.9), 0), "a target just in front of the pillar is missed");

        // Ground is read when the front reaches it: a wall raised or removed after the cast counts as it is then.
        var raised = floor();
        var late = new PyreWaveRules.Tracer(raised, new Vec3d(.5, 64, .5), PyreWaveRules.heading(0));
        late.advance(5);
        raised.box(-6, FLOOR + 1, 10, 6, FLOOR + 2, 10, 1);
        late.advance(LENGTH);
        expect(late.ground(9, 4) == FLOOR && late.ground(10, 4) == NONE && late.ground(30, 4) == NONE, "a wall raised during the wave");
        var cleared = floor().box(-6, FLOOR + 1, 10, 6, FLOOR + 2, 10, 1);
        var early = new PyreWaveRules.Tracer(cleared, new Vec3d(.5, 64, .5), PyreWaveRules.heading(0));
        early.advance(5);
        cleared.box(-6, FLOOR + 1, 10, 6, FLOOR + 2, 10, 0);
        early.advance(LENGTH);
        expect(early.ground(30, 4) == FLOOR, "a wall removed during the wave");

        // Aimed up or down makes no difference: only the yaw is used, so a diagonal heading runs its length too.
        var diagonal = run(floor(), 45);
        for (int lane = 0; lane < LANES; lane++) expect(diagonal.ground(LENGTH, lane) == FLOOR, "diagonal heading, lane " + lane);

        System.out.println("Pyre wave contract passed: " + checked + " checks of the ground path");
    }

    /** A floor wide and long enough for any heading, with air above it. */
    private static Terrain floor() { return new Terrain().box(-45, FLOOR, -45, 45, FLOOR, 45, 1).box(-6, FLOOR, -2, 6, FLOOR, 60, 1); }

    private static PyreWaveRules.Tracer run(Terrain terrain, float yaw) {
        var tracer = new PyreWaveRules.Tracer(terrain, new Vec3d(.5, 64, .5), PyreWaveRules.heading(yaw));
        tracer.advance(LENGTH);
        return tracer;
    }

    private static void expect(boolean condition, String message) {
        checked++;
        if (!condition) throw new AssertionError(message);
    }

    /** Cells by collision top (0 is air), and fluid. */
    private static final class Terrain implements PyreWaveRules.Ground {
        private final Map<BlockPos, Double> tops = new HashMap<>();
        private final Set<BlockPos> fluid = new HashSet<>();

        Terrain box(int x0, int y0, int z0, int x1, int y1, int z1, double top) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                BlockPos p = new BlockPos(x, y, z);
                fluid.remove(p);
                if (top == 0) tops.remove(p); else tops.put(p, top);
            }
            return this;
        }

        Terrain water(int x0, int y0, int z0, int x1, int y1, int z1) {
            box(x0, y0, z0, x1, y1, z1, 0);
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) fluid.add(new BlockPos(x, y, z));
            return this;
        }

        @Override public double top(BlockPos pos) { return tops.getOrDefault(pos, 0.0); }
        @Override public boolean wet(BlockPos pos) { return fluid.contains(pos); }
    }
}
