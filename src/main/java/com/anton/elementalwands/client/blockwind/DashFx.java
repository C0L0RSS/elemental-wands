package com.anton.elementalwands.client.blockwind;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockwind.BlockWindKit.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Waylay Dash (art/wind/blockwind/scenes/dash.js): a pressure ring opens behind the dasher and a soft cloud trail
 * widens into a V along their path, with curled swooshes peeling off the shoulders. The trail is sampled from the
 * dasher's own interpolated body for the five ticks the server used to trace, and stays put in the world. A chained
 * dash doubles the ring and tints the outer cloud pale blue.
 */
final class DashFx implements BlockWindEffects.Fx {
    private static final double TRACE = .25, STEP = .01, LIFE = .4, PUFF_LIFE = .55, BODY = 1.8 * .48;

    private final ClientWorld world;
    private final int entity;
    private final Vec3d start, dir, side;
    private final boolean chained;
    private final double t0, seed;
    /** The body centre over time: {seconds, x, y, z}. */
    private final List<double[]> track = new ArrayList<>();

    DashFx(ClientWorld world, int entity, Vec3d body, Vec3d dir, boolean chained, double now) {
        this.world = world; this.entity = entity; this.start = body; this.chained = chained; t0 = now;
        Vec3d flat = new Vec3d(dir.x, 0, dir.z);
        this.dir = flat.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : flat.normalize();
        side = side(this.dir);
        seed = com.anton.elementalwands.client.blockfire.BlockFireEffects.seed(body, now);
        track.add(new double[]{now, body.x, body.y, body.z});
    }

    private Vec3d at(double t) {
        double[] prev = track.getFirst();
        for (double[] q : track) {
            if (q[0] >= t) {
                double k = q[0] - prev[0] < 1e-6 ? 1 : (t - prev[0]) / (q[0] - prev[0]);
                return new Vec3d(lerp(prev[1], q[1], k), lerp(prev[2], q[2], k), lerp(prev[3], q[3], k));
            }
            prev = q;
        }
        return new Vec3d(prev[1], prev[2], prev[3]);
    }

    @Override public boolean draw(BlockWindEffects.Frame f) {
        double now = f.now(), age = now - t0;
        if (age > 1.2) return false;
        if (age <= TRACE + .06) {
            Entity e = world.getEntityById(entity);
            if (e != null && !e.isRemoved() && now > track.getLast()[0] + 1e-4) {
                Vec3d p = e.getLerpedPos(f.delta()).add(0, e.getHeight() * .48, 0);
                track.add(new double[]{now, p.x, p.y, p.z});
            }
        }
        if (!BlockWindEffects.near(f, start, 96)) return true;
        BlockWindMesh m = f.m();
        // Launch: a pressure ring standing behind the body, opening and thinning; a chained dash doubles it.
        double open = smoothstep(age / .35), thin = 1 - smoothstep(age / .42);
        ring(m, start.subtract(dir.multiply(.35)), dir, lerp(.45, 1.3, open), 16, .14 * thin, CLOUD, AIR_ALPHA, age * 1.2, seed);
        if (chained) ring(m, start.subtract(dir.multiply(.9)), dir, lerp(.3, .95, open), 12, .1 * thin, STREAK, .8, -age * 1.6, seed + 5);
        // Kicked-up air at the feet, rolling back and out.
        Vec3d feet = start.subtract(0, BODY, 0);
        for (int i = 0; i < 6; i++) {
            int s = i < 3 ? -1 : 1, j = i % 3;
            puff(m, feet.add(side.multiply(s * (.25 + j * .15))).add(0, .18, 0), age - j * .03, .3, seed + 13 + i, .6,
                    dir.multiply(-.7 - j * .2).add(side.multiply(s * .6)), 3, AIR_ALPHA);
        }
        // The trail: samples every STEP seconds of the traced path, born where the body passed.
        List<Vec3d> points = new ArrayList<>();
        List<Double> born = new ArrayList<>();
        for (double tb = t0; tb <= Math.min(now, t0 + TRACE) + 1e-6; tb += STEP) { points.add(at(tb)); born.add(tb); }
        for (int i = 0; i < points.size(); i++) {
            Vec3d p = points.get(i), step = i > 0 ? p.subtract(points.get(i - 1)) : points.size() > 1 ? points.get(1).subtract(p) : dir;
            if (step.lengthSquared() < 4e-4) continue;
            Vec3d fw = step.normalize(), sd = side(fw);
            double a = now - born.get(i), sample = born.get(i) * 97 + i;
            // Wake puffs: a continuous cloud trail that widens into a V; a chained dash's outer puffs are pale blue.
            int lane = new int[]{-1, 1, 0}[i % 3];
            if (a <= PUFF_LIFE) {
                double spread = lane != 0 ? .15 + .95 * a : 0;
                Vec3d c = p.add(sd.multiply(lane * spread)).add(fw.multiply(-1.1 * a)).add(0, (lane != 0 ? .02 : .18) - .2 * a, 0);
                puff(m, c, a, (lane != 0 ? .34 : .26) * (chained ? 1.15 : 1), sample, PUFF_LIFE, sd.multiply(lane * .25), .6, 3,
                        lane != 0 ? .55 : .45, chained && lane != 0 ? STREAK : CLOUD);
            }
            // Short curled swooshes peeling back off each shoulder: curves, never long straight lines.
            if (i % 5 == 2) for (int s = -1; s <= 1; s += 2)
                wisp(m, p.add(sd.multiply(s * .32)).add(0, .3, 0), fw.multiply(-1).add(sd.multiply(s * .8)), a, .11, born.get(i) * 7 + s,
                        LIFE, 2.6, .85, .7, sd.multiply(s));
        }
        motes(m, start, age, 6 + (chained ? 4 : 0), .8, 1.6, .5, seed + 3, dir.multiply(-1));
        return true;
    }
}
