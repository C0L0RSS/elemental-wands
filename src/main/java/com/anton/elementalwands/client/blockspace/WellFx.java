package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockspace.BlockSpaceKit.*;

import com.anton.elementalwands.entity.GravityBombEntity;
import com.anton.elementalwands.util.GravityWellManager;
import net.minecraft.util.math.Vec3d;

/**
 * Gravity Well (art/space/blockspace/scenes/well.js), drawn from one GravityBombEntity and its tracked phase clocks.
 * In flight it is a void cube inside six glass plates. On impact the plates blow open and a vortex of large glass
 * plates spirals across the floor into a hovering eclipse core, inside a segmented boundary at the pull radius. The
 * buildup speeds the vortex and swells the core; the burst blows a rim ring and glass shards outward.
 */
final class WellFx implements BlockSpaceEffects.Fx {
    private static final double CORE_LIFT = .6, BUILDUP = GravityBombEntity.COLLAPSE_TICKS / 20.0, BURST = GravityBombEntity.BURST_TICKS / 20.0;
    private static final double RADIUS = GravityWellManager.RADIUS;

    private final GravityBombEntity bomb;
    private final double seed;
    private Vec3d last;
    /** When the burst began on this client's clock, once it has. */
    private double burstAt = Double.NaN;

    WellFx(GravityBombEntity bomb, double now) {
        this.bomb = bomb; seed = bomb.getId() % 79 * 11.0;
    }

    @Override public boolean draw(BlockSpaceEffects.Frame f) {
        double now = f.now();
        boolean gone = bomb.isRemoved();
        if (!gone) {
            last = bomb.getLerpedPos(f.delta());
            if (bomb.isBurst() && Double.isNaN(burstAt)) burstAt = now - (bomb.burstAge() + f.delta()) / 20.0;
        }
        if (gone && (Double.isNaN(burstAt) || now - burstAt > BURST)) return false;
        if (last == null || !BlockSpaceEffects.near(f, last, 96)) return true;
        BlockSpaceMesh m = f.m();
        if (!Double.isNaN(burstAt)) { burst(m, now - burstAt); return true; }
        if (!bomb.isWell()) {
            gravityBomb(m, last, seed, 0);
            stars(m, last, now, 3, .45, .35, seed + 2, .04, true);
            return true;
        }
        double age = (bomb.wellAge() + f.delta()) / 20.0;
        double build = bomb.isCollapsing() ? clamp((bomb.collapseAge() + f.delta()) / 20.0 / BUILDUP) : 0;
        double open = clamp(age / .35);
        if (open < 1) gravityBomb(m, last.add(0, lerp(.2, CORE_LIFT, open), 0), seed, open);
        well(m, now, age, build);
        return true;
    }

    /** The formed well: a void core and corona, two broken orbits, a dark floor disk, a segmented boundary and a vortex. */
    private void well(BlockSpaceMesh m, double t, double age, double build) {
        Vec3d core = last.add(0, CORE_LIFT, 0);
        double grow = smoothstep(age / .5), r = lerp(.3, .48, build) * grow;
        core(m, core, r, seed + 3, 1.4 + build * 3);
        ring(m, core, new Vec3d(.35, 1, .15), .78 * grow, 9, .07, DEFAULT, 1, RIM, DEFAULT, t * 2.4 * (1 + build * 2), seed + 5, .3, false);
        ring(m, core, new Vec3d(-.4, 1, .3), 1.02 * grow, 11, .06, DEFAULT, 1, GLASS, DEFAULT, -t * 1.7 * (1 + build * 2), seed + 7, .35, false);
        // Dark floor disk: three stepped void slabs under the core.
        for (int i = 0; i < 3; i++) {
            double w = (1.9 - i * .5) * grow * (1 - build * .4);
            plate(m, last.add(0, .02 + i * .012, 0), UP, w, w, .03, i == 2 ? NEBULA : VOID, seed + 11 + i, i * .5 + t * .2, DEFAULT);
        }
        // Segmented boundary at the pull radius, low on the floor; it tightens a little in the buildup.
        ring(m, last.add(0, .06, 0), UP, RADIUS * grow * (1 - build * .12), 22, .11, .05, 1, RIM, DEFAULT, t * .35, seed + 13, .22, true);
        // The vortex: large glass plates spiralling in from the boundary to the core and swallowed there.
        inflow(m, core, t, 24, RADIUS * grow, 2.3, seed + 17, .42, 3.2, UP, -CORE_LIFT + .08, GLASS, DEFAULT, build, .12, false);
        if (m.embers) inflow(m, core, t + .4, 14, RADIUS * .9 * grow, 1.6, seed + 19, .07, 4, UP, .3, STARLIGHT, DEFAULT, build, .12, false);
    }

    /** The burst: the core is gone, a rim ring and glass shards blow outward, a pinpoint marks where it was. */
    private void burst(BlockSpaceMesh m, double age) {
        if (age < 0 || age > BURST) return;
        double k = age / BURST;
        Vec3d core = last.add(0, CORE_LIFT, 0);
        ring(m, last.add(0, .3, 0), UP, lerp(.5, RADIUS + .5, smoothstep(k * 1.4)), 22, .16 * (1 - k), .08, 1, RIM, DEFAULT, .3, seed + 23, 0, true);
        spill(m, core, age, 16, 8, BURST, seed + 25, .34, GLASS, null);
        pinpoint(m, core, .3, k / .3, seed + 27);
        stars(m, core, age, 12, 3, BURST, seed + 29, .06, false);
    }
}
