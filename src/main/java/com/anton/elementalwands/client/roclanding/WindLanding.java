package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.roclanding.L.*;

import com.anton.elementalwands.client.blockfire.BlockFireKit;
import com.anton.elementalwands.client.blockwind.BlockWindKit;
import com.anton.elementalwands.client.blockwind.BlockWindMesh;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.Vec3d;

/**
 * Wind's landing in the Storm Roc intro (art/storm_roc/intro/wind_landing.py; its effects are
 * art/storm_roc/intro/landing_wind.js): chained Waylay Dashes zigzag down through the air, each a
 * pressure ring, a cloud burst that bleeds off speed and a widening cloud wake left hanging in the
 * sky; the last, chained, dives into a skim over the snow, a light touchdown and a skid. Drawn as
 * block wind with the Wind spells' own mesh and atlas (client.blockwind), at this scene's scale; the
 * primitives below are the study's (art/wind/blockwind/kit.js) with the preview's two additions: air
 * thins out completely at the end of a puff's life, and fades near the lens. The bake's facts are the
 * dashes, the flight (hips and wand tip every tenth of a story tick) and the touchdown; its columns
 * are n_clock and the w_ ones.
 */
public final class WindLanding implements RocLanding {
    private static final int GALE = BlockWindKit.GALE, CLOUD = BlockWindKit.CLOUD, MIST = BlockWindKit.MIST, STREAK = BlockWindKit.STREAK;
    private static final double AIR = .62, TAU = Math.PI * 2;
    private static final double[] UP = {0, 1, 0};

    private record Dash(double clock, boolean chained, double[] at, double[] dir, List<Sample> wake) {}
    /** A wake sample: when (story clock) and where the body passed, which way, and its side. */
    private record Sample(double c, double[] p, double[] f, double[] side) {}

    private double[][] flight;
    private List<Dash> dashes;
    private List<Sample> skid;
    private double floor, touch, stop, low;
    private double[] touchAt;

    /** This frame's cubes, built in draw and drawn in emit. */
    private BlockWindMesh mesh;
    private double[] origin, eye;

    private void load(Scene s) {
        if (dashes != null) return;
        JsonObject f = s.facts();
        JsonArray fl = f.getAsJsonArray("flight");
        flight = new double[fl.size()][];
        for (int i = 0; i < fl.size(); i++) {
            JsonArray r = fl.get(i).getAsJsonArray();
            double[] row = new double[7];
            for (int k = 0; k < 7; k++) row[k] = r.get(k).getAsDouble();
            flight[i] = row;
        }
        floor = f.get("floor").getAsDouble();
        touch = f.get("touch").getAsDouble();
        stop = f.get("stop").getAsDouble();
        low = f.get("low").getAsDouble();
        touchAt = Scene.vec(f.getAsJsonArray("touch_at"));
        JsonArray ds = f.getAsJsonArray("dashes");
        dashes = new ArrayList<>();
        for (int i = 0; i < ds.size(); i++) {
            JsonObject o = ds.get(i).getAsJsonObject();
            double clock = o.get("clock").getAsDouble();
            double end = i < ds.size() - 1 ? ds.get(i + 1).getAsJsonObject().get("clock").getAsDouble() - .4 : touch + 3;
            dashes.add(new Dash(clock, o.get("chained").getAsBoolean(), Scene.vec(o.getAsJsonArray("at")), Scene.vec(o.getAsJsonArray("dir")),
                    wakeSamples(clock, end, .62)));
        }
        skid = wakeSamples(touch, stop, .3);
    }

    // ------------------------------------------------------------------ the flight

    /** Hips (which 0) or wand tip (which 1) at story clock c, interpolated. */
    private double[] at(double c, int which) {
        int o = 1 + which * 3, n = flight.length - 1;
        if (c <= flight[0][0]) return v(flight[0][o], flight[0][o + 1], flight[0][o + 2]);
        if (c >= flight[n][0]) return v(flight[n][o], flight[n][o + 1], flight[n][o + 2]);
        int lo = 0, hi = n;
        while (hi - lo > 1) { int mid = (lo + hi) >> 1; if (flight[mid][0] <= c) lo = mid; else hi = mid; }
        double[] a = flight[lo], b = flight[hi];
        double k = b[0] - a[0] == 0 ? 0 : (c - a[0]) / (b[0] - a[0]);
        return v(mix(a[o], b[o], k), mix(a[o + 1], b[o + 1], k), mix(a[o + 2], b[o + 2], k));
    }

    /** Wake samples every step blocks along the flight from clock c0 to c1. */
    private List<Sample> wakeSamples(double c0, double c1, double step) {
        List<Sample> out = new ArrayList<>();
        Sample last = null;
        double[] prev = at(c0, 0);
        for (double c = c0; c <= c1; c += .05) {
            double[] p = at(c, 0), d = sub(p, prev);
            if (last == null || len(sub(p, last.p)) >= step) {
                double[] f = len(d) > 1e-4 ? norm(d) : last != null ? last.f : v(0, 0, 1);
                double[] side = norm(cross(f, Math.abs(f[1]) > .95 ? v(1, 0, 0) : UP));
                last = new Sample(c, p, f, side);
                out.add(last);
            }
            prev = p;
        }
        return out;
    }

    // ------------------------------------------------------------------ block wind at this scale

    private static double sstep(double k) { return smooth(k); }
    private static double easeOut(double k) { k = clamp(k, 0, 1); return 1 - (1 - k) * (1 - k); }
    private static Vec3d vec(double[] a) { return new Vec3d(a[0], a[1], a[2]); }

    private static Vec3d[] flowAxes(double[] dir, double[] up) {
        double[] x = norm(dir), ref = sub(up, scale(x, dot(up, x)));
        if (len(ref) < 1e-3) ref = Math.abs(x[0]) < .9 ? v(1, 0, 0) : v(0, 0, 1);
        double[] y = norm(sub(ref, scale(x, dot(ref, x)))), z = cross(x, y);
        return new Vec3d[]{vec(x), vec(y), vec(z)};
    }

    /** One wind cube at p (summit frame), edges along axes; see-through air thins out near the lens. */
    private void cube(double[] p, double sx, double sy, double sz, int band, double seed, Vec3d[] axes, double alpha) {
        if (alpha < 1) alpha *= sstep((dist(p, eye) - .7) / 2.1);
        if (alpha <= .02) return;
        BlockWindKit.cube(mesh, new Vec3d(p[0] + origin[0], p[1] + origin[1], p[2] + origin[2]), sx, sy, sz, band, seed, axes, alpha);
    }

    private void streak(double[] a, double[] b, double width, int band, double alpha, double seed) {
        double[] d = sub(b, a);
        double l = len(d);
        if (l < 1e-3 || width <= 1e-3) return;
        cube(scale(add(a, b), .5), l, width, width, band, seed, flowAxes(d, UP), alpha);
    }

    /** A puffy cloud that blooms, drifts and thins away completely; lower lobes take the shadowed mist band. */
    private void puff(double[] center, double age, double size, double seed, double duration, double[] drift, int lobes, double alpha, int band) {
        double k = age / duration;
        if (k < 0 || k >= 1) return;
        double grow = .45 + .55 * easeOut(k / .3), fade = 1 - sstep((k - .5) / .5), s = size * grow * (.35 + .65 * fade);
        double[] c = add(center, scale(drift, easeOut(k)));
        Vec3d[] axes = BlockFireKit.yawAxes(BlockFireKit.rnd(seed) * TAU + k * .8);
        double a = alpha * (.55 + .45 * fade) * (1 - sstep((k - .7) / .3));
        cube(c, s, s, s, band, seed, axes, a);
        for (int i = 0; i < lobes; i++) {
            Vec3d d = BlockFireKit.sphereDir(i + (int) Math.floor(BlockFireKit.rnd(seed + i) * 3), lobes + 3);
            double[] off = scale(v(d.x, d.y, d.z), s * (.42 + .6 * k));
            double z = s * (.5 + .25 * BlockFireKit.rnd(seed + i * 1.7));
            cube(add(c, off), z, z, z, d.y < -.15 ? MIST : band, seed + i + 1, axes, a);
        }
    }

    private void ring(double[] center, double[] normal, double radius, int count, double size, int band, double alpha, double phase, double seed) {
        if (radius <= 1e-3 || size <= 1e-3) return;
        double[] n = norm(normal), a0 = norm(cross(n, Math.abs(n[1]) > .9 ? v(1, 0, 0) : UP)), b0 = cross(n, a0);
        double arc = TAU * radius / count;
        for (int i = 0; i < count; i++) {
            double a = phase + TAU * i / count;
            double[] radial = add(scale(a0, Math.cos(a)), scale(b0, Math.sin(a))), tangent = cross(n, radial);
            cube(add(center, scale(radial, radius)), Math.min(arc * .85, size * 1.4 * 2.2), size, size, band, seed + i,
                    new Vec3d[]{vec(tangent), vec(n), vec(radial)}, alpha);
        }
    }

    private void wisp(double[] p, double[] dir, double age, double size, double seed, double duration, double curl, double length, double alpha, double[] up) {
        double k = age / duration;
        if (k < 0 || k >= 1) return;
        double[] x = norm(dir), y0 = sub(up, scale(x, dot(up, x)));
        if (len(y0) < 1e-6) return;
        double[] y = scale(norm(y0), BlockFireKit.rnd(seed) < .5 ? -1 : 1);
        double fade = 1 - sstep((k - .35) / .65);
        double[] head = add(p, scale(x, length * easeOut(k)));
        for (int i = 0; i < 4; i++) {
            double a = i / 3.0 * curl, r = length * .35 * (1 - i * .18), s = size * (1 - i * .2) * fade;
            if (s <= .01) continue;
            double[] q = add(head, add(scale(x, -Math.sin(a) * r), scale(y, (1 - Math.cos(a)) * r)));
            cube(q, s * 1.6, s, s, i == 0 ? GALE : CLOUD, seed + i, flowAxes(add(scale(x, Math.cos(a)), scale(y, Math.sin(a))), up), alpha * (1 - i * .15));
        }
    }

    /** Tiny solid motes thrown back (the particle setting's reduced modes leave them out, as the spells do). */
    private void motes(double[] from, double age, int count, double speed, double duration, double size, double seed, double[] dir) {
        double k = age / duration;
        if (k < 0 || k >= 1 || !mesh.embers) return;
        for (int i = 0; i < count; i++) {
            Vec3d sd = BlockFireKit.sphereDir(i, count), so = BlockFireKit.sphereDir(i + 3, count + 5);
            double[] d = norm(add(dir, scale(v(sd.x, sd.y, sd.z), .6)));
            double[] p = add(add(from, scale(v(so.x, so.y, so.z), .8 * .3)), scale(d, speed * duration * easeOut(k) * (.6 + .4 * BlockFireKit.rnd(seed + i))));
            double z = size * (1 - k);
            cube(p, z, z, z, GALE, seed + i, BlockFireKit.AXES, 1);
        }
    }

    // ------------------------------------------------------------------ the landing

    private static double sec(double ft, double c) { return (ft - c) / 20; }

    @Override public void draw(Scene s) {
        load(s);
        LandingMesh lm = s.mesh();
        var client = MinecraftClient.getInstance();
        double ft = s.ft();
        origin = lm.origin;
        eye = lm.eye;
        mesh = new BlockWindMesh(vec(lm.camera), false, client.options.getParticles().getValue() == ParticlesMode.ALL, ft / 20, client.world);
        for (int i = 0; i < dashes.size(); i++) drawDash(ft, dashes.get(i), i);
        drawTip(s, ft);
        drawLanding(ft);
        snowSolids(lm, ft);
    }

    private void drawDash(double ft, Dash d, int i) {
        double age = sec(ft, d.clock);
        if (age < -.02 || age > 6) return;
        double[] dir = d.dir, start = d.at;
        // The pressure ring snapping open behind you; a chained dash doubles it, pale blue.
        double open = sstep(age / .3), thin = 1 - sstep(age / .5);
        ring(add(start, scale(dir, -.8)), dir, .7 + 2.1 * open, 20, .3 * thin, CLOUD, .78, age * 1.2, i * 9);
        if (d.chained) ring(add(start, scale(dir, -2.0)), dir, .5 + 1.6 * open, 16, .24 * thin, STREAK, .72, -age * 1.6, i * 9 + 5);
        // The cloud burst that bleeds off your speed: puffs blown out round the turn and left behind.
        for (int k = 0; k < 9; k++) {
            Vec3d o3 = BlockFireKit.sphereDir(k, 9);
            double[] o = v(o3.x, o3.y, o3.z), out = norm(sub(o, scale(dir, dot(o, dir) * .8)));
            puff(add(start, scale(out, .5)), age - k * .012, .95, i * 31 + k, 1.1, add(scale(out, 2.2), scale(dir, -1.4)), 4, .6,
                    d.chained && k % 2 != 0 ? STREAK : CLOUD);
        }
        // The wake: a soft cloud trail born where you passed, widening into a V and drifting back, left
        // hanging in the sky; thin curled swooshes peel off the shoulders.
        boolean skim = i == dashes.size() - 1;
        for (int j = 0; j < d.wake.size(); j++) {
            Sample q = d.wake.get(j);
            double a = sec(ft, q.c);
            if (a < 0) continue;
            boolean near = skim && q.c > low - 3;  // low over the snow the wake is smaller, rolls on a little past you and clears
            if (near && j % 2 != 0) continue;
            for (int lane = -1; lane <= 1; lane++) {
                double spread = lane != 0 ? (near ? 1.15 : 1.8) * (1 - Math.exp(-a / .7)) : 0;
                double life = near ? (lane != 0 ? 1.3 : .9) : (lane != 0 ? 3.2 : 1.8);
                double[] p = add(add(q.p, scale(q.side, lane * spread)), add(scale(q.f, (near ? .7 : -1.2) * easeOut(a / 1.5)), v(0, (lane != 0 ? .05 : .2) - .25 * a, 0)));
                if (near) p[1] = Math.max(floor + .25, p[1] - .4);
                puff(p, a, (near ? .4 : lane != 0 ? .62 : .5) * (d.chained ? 1.12 : 1), q.c * 97 + lane * 13 + i, life, scale(q.side, lane * .4), 3,
                        near ? .42 : lane != 0 ? .52 : .44, d.chained && lane != 0 ? STREAK : CLOUD);
            }
            if (j % 5 == 2) for (int side = -1; side <= 1; side += 2)
                wisp(add(add(q.p, scale(q.side, side * .45)), v(0, .35, 0)), add(scale(q.f, -1), scale(q.side, side * .8)), a, .2, q.c * 7 + side, .6, 2.6, 1.3, .7,
                        scale(q.side, side));
        }
        motes(start, age, d.chained ? 12 : 8, 3.2, .6, .1, i * 3, scale(dir, -1));
    }

    /** The wand cutting the air: a short chunky streak trailing its tip through each dash's first burst. */
    private void drawTip(Scene s, double ft) {
        double k = s.col("w_lead");
        if (k < .05) return;
        double since = 99;
        for (Dash d : dashes) if (ft >= d.clock) since = Math.min(since, ft - d.clock);
        double burst = 1 - sstep((since - 2) / 3);
        if (burst < .05) return;
        double[] prev = at(ft, 1);
        for (int j = 1; j <= 2; j++) {
            double[] p = at(ft - j * .25, 1);
            streak(prev, p, .22 * (1 - j / 3.0) * k * burst, j < 2 ? GALE : STREAK, .85 * (1 - j / 4.0) * k * burst, j * 3.1);
            prev = p;
        }
    }

    /** The touchdown: a soft ring of cloud rolling out over the snow, and the skid's low spray of air. */
    private void drawLanding(double ft) {
        double g = floor, age = sec(ft, touch);
        if (age < 0) return;
        double[] c = touchAt;
        for (int k = 0; k < 14; k++) {
            double a = k / 14.0 * TAU + BlockFireKit.rnd(k) * .3;
            double[] dir = v(Math.cos(a), 0, Math.sin(a));
            puff(v(c[0] + dir[0] * .5, g + .22, c[2] + dir[2] * .5), age - BlockFireKit.rnd(k + 4) * .05, .38, 700 + k, 1.1, add(scale(dir, 2.2), v(0, .15, 0)), 3, .5, CLOUD);
        }
        ring(v(c[0], g + .12, c[2]), UP, .5 + 2.6 * sstep(age / .45), 22, .16 * (1 - sstep(age / .6)), CLOUD, .75, 0, 77);
        for (int j = 0; j < skid.size(); j += 2) {
            Sample q = skid.get(j);
            double a = sec(ft, q.c);
            if (a < 0) continue;
            for (int sgn = -1; sgn <= 1; sgn += 2)
                puff(v(q.p[0] + q.side[0] * sgn * .5, g + .2, q.p[2] + q.side[2] * sgn * .5), a, .3, 900 + j * 7 + sgn, 1,
                        add(scale(q.side, sgn * 1.2), add(scale(q.f, .8), v(0, .3, 0))), 3, .5, CLOUD);
        }
    }

    // ------------------------------------------------------------------ snow kicked up by the skim and the skid (solid, lit)

    private interface Power { double at(double v); }

    private void snowSolids(LandingMesh m, double ft) {
        spray(m, ft, low - 1, touch, 22, v -> .55, 11);                          // the skim's wind ruffling the snow under you
        spray(m, ft, touch, stop, 26, v -> 1.3 * Math.pow(1 - v, 1.4), 41);     // the skid
    }

    private void spray(LandingMesh m, double ft, double c0, double c1, int n, Power power, double seed) {
        double g = floor;
        for (int k = 0; k < n; k++) {
            double tk = c0 + (c1 - c0) * k / n, age = ft - tk;
            if (age < 0 || age > 24) continue;
            double[] o = at(tk, 0), h = at(tk - .3, 0), f = norm(v(o[0] - h[0], 0, o[2] - h[2])), side = cross(f, UP);
            double t = age / 20, pw = power.at((double) k / n);
            for (int j = 0; j < 3; j++) {
                double base = seed + k * 13.1 + j * 3.7;
                int sgn = j % 2 != 0 ? 1 : -1;
                double[] vel = add(add(scale(side, sgn * (1.5 + 2.5 * BlockFireKit.rnd(base + 1)) * pw), scale(f, (1 + 3 * BlockFireKit.rnd(base + 2)) * pw)),
                        v(0, (1.5 + 3.5 * BlockFireKit.rnd(base + 3)) * pw, 0));
                double[] p = add(v(o[0], g, o[2]), scale(vel, t));
                p[1] = Math.max(g + .04, g + .08 + vel[1] * t - 6 * t * t);
                double z = (.05 + .06 * BlockFireKit.rnd(base + 4)) * (1 - age / 24);
                if (z < .006) continue;
                m.box(v(-z, -z * .8, -z), v(z, z * .8, z), j % 3 != 0 ? v(.93, .95, 1) : v(.84, .88, .95), L.mul(t(p), ry(BlockFireKit.rnd(base + 5) * 6 + age * .2)));
            }
        }
    }

    @Override public void emit(VertexConsumerProvider consumers, MatrixStack.Entry entry) {
        BlockWindMesh m = mesh;
        mesh = null;
        if (m == null || m.isEmpty()) return;
        if (m.hasSolid()) {
            m.emitSolid(consumers.getBuffer(BlockWindMesh.SOLID), entry);
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockWindMesh.SOLID);
        }
        if (m.hasAir()) {
            m.emitAir(consumers.getBuffer(BlockWindMesh.AIR), entry);
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockWindMesh.AIR);
        }
    }

    /** Over the frame: the slow motion's cooled edges, and speed lines through the fall and the zigzag. */
    @Override public void hud(DrawContext context, Scene s, int w, int h, double shown) {
        double slow = s.col("w_slow") * shown;
        LandingHud.vignette(context, w, h, 14 / 255.0, 30 / 255.0, 52 / 255.0, .5 * slow, .4);
        double speed = s.col("w_speed") * shown;
        LandingHud.speedLines(context, w, h, s.t(), speed, .36 * speed, 110);
    }
}
