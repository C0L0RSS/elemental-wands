package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.roclanding.L.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/**
 * Space's landing (art/storm_roc/intro/space_landing.py; its effects are landing_space.js): the
 * player falls head first and, a few blocks above the snow, folds into a Blink Rift; their momentum
 * still blows the snow out where they would have hit, and a second rift a few blocks to the side
 * lets them out already walking. Drawn in block space's void glass (art/space/blockspace/kit.js):
 * opaque void lenses framed in bright rim, dark see-through glass with a bright frame, starlight
 * motes. The bake's columns are the sp_ ones; its facts hold the events and the player's posed
 * body through the fold and unfold (shell), which the animatic reads off the posed model.
 */
public final class SpaceLanding implements RocLanding {
    private static final double TAU = Math.PI * 2;
    /** The animatic's summit sun, as LandingMesh lights solids with it. */
    private static final double[] SUN = norm(v(.4, .9, .3));

    // The void-glass bands as flat colours (blockspace.png's rows), and which are opaque.
    private static final double[] VOID = {.06, .035, .11, 1}, RIM = {.84, .44, 1, 1}, STARLIGHT = {.97, .95, 1, 1},
            BLUE = {.62, .82, 1, 1}, GLASS = {.26, .15, .46, .55}, NEBULA = {.5, .22, .52, .5}, FRAME = {.55, .34, .9, .8};
    private static final double[] SNOW = {.93, .95, 1}, PACK = {.8, .84, .92};
    private static final double[] SHELL = {.3, .16, .55};
    private static final String[] SHELL_BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private record Event(double tick, double clock, double[] at, double[] axis) {}

    private Event fold, burst, unfold;
    private double floor, hitStory;
    private double[] impact;
    private final Map<Integer, float[]> shell = new HashMap<>();
    private LandingMesh m;

    private void load(Scene s) {
        if (fold != null) return;
        JsonObject f = s.facts();
        for (JsonElement e : f.getAsJsonArray("events")) {
            JsonObject o = e.getAsJsonObject();
            Event ev = new Event(o.get("tick").getAsDouble(), o.get("clock").getAsDouble(), Scene.vec(o.getAsJsonArray("at")),
                    o.has("axis") ? Scene.vec(o.getAsJsonArray("axis")) : null);
            switch (o.get("kind").getAsString()) {
                case "fold" -> fold = ev;
                case "burst" -> burst = ev;
                case "unfold" -> unfold = ev;
                default -> {}
            }
        }
        floor = f.get("floor").getAsDouble();
        hitStory = f.get("hit_story").getAsDouble();
        impact = Scene.vec(f.getAsJsonArray("impact"));
        for (Map.Entry<String, JsonElement> e : f.getAsJsonObject("shell").entrySet()) {
            JsonArray a = e.getValue().getAsJsonArray();
            float[] values = new float[a.size()];
            for (int i = 0; i < values.length; i++) values[i] = a.get(i).getAsFloat();
            shell.put(Integer.parseInt(e.getKey()), values);
        }
    }

    /** Seconds since an event, on the story clock (so it slows with the slow motion). */
    private static double age(Scene s, Event e) { return e == null ? -1 : (s.ft() - e.clock) / 20; }
    private static double rnd(double n) { return hash(n * 1.618 + .37); }

    @Override public void draw(Scene s) {
        load(s);
        m = s.mesh();
        drifts();
        burstSolids(s);
        flecks(s);
        // The player folding into the rift and unfolding out of the other: their own glass sheen.
        double fa = age(s, fold), ua = age(s, unfold), sheen = 0;
        if (fa >= 0 && ua < 0) sheen = smooth(fa / .05);
        if (ua >= 0) sheen = clamp(1 - (ua - .03) / .4, 0, 1);
        if (sheen > .01 && s.col("sp_fold") > .001) shellOver(s.t(), sheen);
        riftIn(fa, m.eye);
        riftOut(ua, m.eye);
        burstPuffs(s);
    }

    // ------------------------------------------------------------------ flat void glass

    /** The box's face as the animatic's flat_ program draws it: LandingMesh's sun shading divided back out. */
    private void flatQuad(double[] a, double[] b, double[] c, double[] d, double[] col) {
        double[] n = norm(cross(sub(b, a), sub(c, a)));
        if (dot(n, sub(m.eye, a)) < 0) n = scale(n, -1);
        double tone = .5 + .5 * Math.max(0, dot(n, SUN)) + .12 * n[1];
        m.quad(a, b, c, d, new double[]{col[0] / tone, col[1] / tone, col[2] / tone, col.length > 3 ? col[3] : 1});
    }

    /** An unlit box from lo to hi, moved by mat. */
    private void flatBox(double[] lo, double[] hi, double[] col, double[] mat) {
        double x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
        double[][] c = {v(x0, y0, z0), v(x1, y0, z0), v(x1, y1, z0), v(x0, y1, z0), v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1)};
        for (int i = 0; i < 8; i++) c[i] = apply(mat, c[i]);
        int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) flatQuad(c[f[0]], c[f[1]], c[f[2]], c[f[3]], col);
    }

    private static double[] frame(double[][] axes, double[] p) {
        double[] x = axes[0], y = axes[1], z = axes[2];
        return new double[]{x[0], x[1], x[2], 0, y[0], y[1], y[2], 0, z[0], z[1], z[2], 0, p[0], p[1], p[2], 1};
    }

    /** One void-glass piece: an oriented box, centre p, extents along axes; glass and nebula get their bright frame. */
    private void piece(double[] p, double[] size, double[] band, double[][] axes) {
        double[] mat = frame(axes, p);
        flatBox(v(-size[0] / 2, -size[1] / 2, -size[2] / 2), v(size[0] / 2, size[1] / 2, size[2] / 2), band, mat);
        if (band == GLASS || band == NEBULA) {
            double f = .035;
            int[][] perms = {{0, 1, 2}, {1, 2, 0}, {2, 0, 1}};
            for (int[] q : perms) for (int u = -1; u <= 1; u += 2) for (int w = -1; w <= 1; w += 2) {
                int i = q[0], j = q[1], k = q[2];
                double[] lo = new double[3], hi = new double[3];
                lo[i] = -size[i] / 2; hi[i] = size[i] / 2;
                lo[j] = u * size[j] / 2 - f / 2; hi[j] = u * size[j] / 2 + f / 2;
                lo[k] = w * size[k] / 2 - f / 2; hi[k] = w * size[k] / 2 + f / 2;
                flatBox(lo, hi, FRAME, mat);
            }
        }
    }
    private void piece(double[] p, double size, double[] band, double[][] axes) { piece(p, v(size, size, size), band, axes); }

    private static double[] sphereDir(int i, int n) {
        double y = 1 - 2 * (i + .5) / n, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963;
        return v(Math.cos(a) * r, y, Math.sin(a) * r);
    }
    /** A basis whose second axis is dir. */
    private static double[][] basis(double[] dir, double roll) {
        double[] y = norm(dir), ref = Math.abs(y[1]) > .92 ? v(1, 0, 0) : v(0, 1, 0);
        double[] x = norm(cross(y, ref)), z = cross(x, y);
        if (roll != 0) {
            double c = Math.cos(roll), sn = Math.sin(roll);
            double[] nx = add(scale(x, c), scale(z, sn)), nz = sub(scale(z, c), scale(x, sn));
            x = nx; z = nz;
        }
        return new double[][]{x, y, z};
    }
    /** A basis whose first axis runs along dir. */
    private static double[][] flow(double[] dir, double roll) { double[][] b = basis(dir, roll); return new double[][]{b[1], b[2], b[0]}; }
    private static double[][] tumble(double seed, double t, double rate) {
        return basis(sphereDir((int) Math.floor(rnd(seed) * 29), 29), rnd(seed + .3) * TAU + t * rate);
    }

    /** How open a rift slit is age seconds after it appears: snaps open, holds, then pinches shut. */
    private static double openness(double a, double open, double hold, double close) {
        if (a < 0) return 0;
        if (a < open) return smooth(a / open);
        return 1 - smooth((a - open - hold) / close);
    }

    /** The eclipse slit: a lens of void rows framed by rim edges, long along up, turned to face the eye. */
    private void slit(double[] base, double[] up, double[] eye, double height, double width, double open, int rows) {
        if (open <= .01) return;
        double[] u = norm(up), e = sub(eye, base), toEye = norm(sub(e, scale(u, dot(e, u)))), side = norm(cross(u, toEye));
        double[][] axes = {side, u, toEye};
        double rowH = height / rows;
        for (int i = 0; i < rows; i++) {
            double vv = (i + .5) / rows, shape = Math.pow(Math.sin(Math.PI * vv), .75), w = Math.max(.04, width * open * shape);
            double half = Math.round(w * 8) / 16.0 + .03;
            double[] p = add(base, scale(u, vv * height));
            piece(p, v(half * 2, rowH * 1.02, .08), VOID, axes);
            for (int sg = -1; sg <= 1; sg += 2) {
                piece(add(p, scale(side, sg * (half + .035))), v(.07, rowH * 1.02, .1), RIM, axes);
                piece(add(p, scale(side, sg * (half + .17))), v(.16, rowH * .9, .06), GLASS, axes);
            }
        }
    }

    /** Shards of glass falling into c from round it, stretching along the fall, gone as they arrive. */
    private void inflow(double[] c, double a, int count, double radius, double period, double seed, double size) {
        for (int i = 0; i < count; i++) {
            double u = (a - rnd(seed + i) * period * .5) / period;
            if (u < 0 || u >= 1) continue;
            double[] d = sphereDir(i, count), p = add(c, scale(d, radius * (1 - u * u) * (.75 + .25 * rnd(seed + i * 3))));
            double z = size * smooth(u / .15) * (1 - u);
            if (z < .01) continue;
            piece(p, v(z * (1 + 1.5 * u), z * .7, z * .22 + .015), i % 4 == 1 ? NEBULA : i % 5 == 3 ? RIM : GLASS, flow(d, rnd(seed + i) * TAU));
        }
    }

    /** Shards spilling out of c, fast then slowing, shrinking away. */
    private void spill(double[] c, double a, int count, double speed, double duration, double seed, double size) {
        for (int i = 0; i < count; i++) {
            double u = a / duration;
            if (u < 0 || u >= 1) continue;
            double[] d = sphereDir(i, count), p = add(c, scale(d, speed * duration * (1 - (1 - u) * (1 - u)) * (.6 + .4 * rnd(seed + i))));
            double z = size * (1 - u);
            if (z < .01) continue;
            piece(p, v(z, z, z * .25 + .015), i % 4 == 3 ? RIM : GLASS, tumble(seed + i, a, 6));
        }
    }

    /** Tiny twinkling starlight motes round c, falling in (or drifting out). */
    private void stars(double[] c, double a, int count, double spread, double duration, double seed, double size, boolean inward) {
        for (int i = 0; i < count; i++) {
            double u = (a / duration + rnd(seed + i * .37)) % 1;
            double[] d = sphereDir(i + (int) Math.floor(rnd(seed + i) * 5), count + 5);
            double r = spread * (inward ? Math.pow(1 - u, 1.3) : u) * (.5 + .5 * rnd(seed + i * 3.1));
            double tw = .55 + .45 * Math.sin(a * 9 + i * 2.1), z = size * tw * smooth(u / .15) * (1 - smooth((u - .8) / .2));
            if (z <= .008) continue;
            piece(add(c, scale(d, r)), z, i % 3 != 0 ? STARLIGHT : BLUE, basis(d, Math.PI / 4));
        }
    }

    /** A ring of rim pieces round c facing n, radius r. */
    private void ring(double[] c, double[] n, double r, int count, double size, double phase) {
        double[][] b = basis(n, 0);
        double[] x = b[0], z = b[2];
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * TAU + phase;
            double[] d = add(scale(x, Math.cos(a)), scale(z, Math.sin(a)));
            piece(add(c, scale(d, r)), v(size * 1.6, size, size), RIM, new double[][]{norm(cross(n, d)), norm(n), d});
        }
    }

    /** The departure: the lens open round the falling body, the space round it pulled in; a ring snapping shut. */
    private void riftIn(double a, double[] eye) {
        if (a < 0 || a > .9) return;
        double[] c = fold.at, up = fold.axis, base = sub(c, scale(norm(up), 1.6));
        slit(base, up, eye, 3.2, 1.15, openness(a, .05, .1, .28), 13);
        inflow(c, a, 16, 2.2, .34, 5, .3);
        double close = (a - .05 - .1) / .28;
        if (close > 0 && close < 1) ring(c, norm(sub(eye, c)), mix(1.5, .05, smooth(close)), 14, .09, a * 4);
        stars(c, a, 10, 1.8, .5, 7, .08, true);
    }

    /** The arrival: a standing slit at the feet, shards and stars spilling back out. */
    private void riftOut(double a, double[] eye) {
        if (a < 0 || a > .9) return;
        double[] feet = unfold.at, c = add(feet, 0, 1.1, 0);
        slit(feet, v(0, 1, 0), eye, 2.4, 1.0, openness(a, .07, .16, .32), 11);
        spill(c, a, 12, 2.4, .45, 15, .24);
        stars(c, a, 10, 1.4, .6, 17, .07, false);
    }

    // ------------------------------------------------------------------ the player's glass sheen

    /** The player's posed body (baked box by box) again, a little proud, as see-through violet glass. */
    private void shellOver(double t, double k) {
        if (k <= .01) return;
        int g = (int) Math.floor(t);
        float[] a = shell.get(g), b = shell.get(g + 1);
        if (a == null) a = b;
        if (b == null) b = a;
        if (a == null) return;
        double f = clamp(t - g, 0, 1);
        double[] col = {SHELL[0], SHELL[1], SHELL[2], .62 * k};
        for (int bone = 0; bone < SHELL_BONES.length; bone++) {
            int o = bone * 12;
            double[] c = lerp3(a, b, o, f), hx = lerp3(a, b, o + 3, f), hy = lerp3(a, b, o + 6, f), hz = lerp3(a, b, o + 9, f);
            if (len(hx) < 1e-3 && len(hz) < 1e-3) continue;  // folded flat: nothing to see
            double[] mat = {hx[0], hx[1], hx[2], 0, hy[0], hy[1], hy[2], 0, hz[0], hz[1], hz[2], 0, c[0], c[1], c[2], 1};
            flatBox(v(-1, -1, -1), v(1, 1, 1), col, mat);
        }
    }

    private static double[] lerp3(float[] a, float[] b, int o, double f) {
        return v(a[o] + (b[o] - a[o]) * f, a[o + 1] + (b[o + 1] - a[o + 1]) * f, a[o + 2] + (b[o + 2] - a[o + 2]) * f);
    }

    // ------------------------------------------------------------------ the snow

    /** Wind-carved drifts on the summit's snow round the landing, so the ground reads as it rushes up. */
    private void drifts() {
        double g = floor;
        double[] c = impact;
        for (int i = 0; i < 70; i++) {
            double a = rnd(i + 200) * TAU, r = 3 + 30 * Math.sqrt(rnd(i + 300)), x = c[0] + Math.cos(a) * r, z = c[2] - 6 + Math.sin(a) * r * 1.4;
            double len = 1.2 + 3 * rnd(i + 400), wid = .25 + .4 * rnd(i + 500), ang = .4 + .3 * rnd(i + 600), sh = .86 + .05 * rnd(i + 700);
            m.fan(v(x, 0, z), g + .008, q -> { double e = Math.abs(Math.cos(q - ang)); return wid + (len - wid) * e * e; }, v(sh, sh + .02, sh + .07), 12);
        }
    }

    /** Where they would have hit: a shallow crater of packed snow, its lip broken into blocks, chunks thrown out. */
    private void burstSolids(Scene s) {
        double a = age(s, burst);
        if (a < 0) return;
        double g = floor;
        double[] c = burst.at;
        double grow = 1 - Math.pow(1 - clamp(a / .12, 0, 1), 3);
        m.fan(c, g + .015, ang -> 1.5 * grow * (.85 + .15 * Math.sin(ang * 5 + 1)), v(.74, .78, .88), 28);
        for (int i = 0; i < 12; i++) {
            double ang = i / 12.0 * TAU + rnd(i) * .4, r = 1.6 * grow, z = (.18 + .14 * rnd(i + 4)) * grow;
            double[] p = v(c[0] + Math.cos(ang) * r, g + z * .5, c[2] + Math.sin(ang) * r);
            m.box(v(-z, -z * .5, -z), v(z, z * .5, z), i % 3 != 0 ? SNOW : PACK, mul(t(p), ry(ang + rnd(i))));
        }
        // Chunks thrown out, up and away from the fall, tumbling down again.
        if (a < 1.6) for (int i = 0; i < 46; i++) {
            final int ii = i;
            java.util.function.DoubleUnaryOperator r = k -> rnd(ii * 7.1 + k * 3.3);
            double ang = r.applyAsDouble(0) * TAU, out = 2 + 6 * r.applyAsDouble(1), up = 4 + 9 * r.applyAsDouble(2), sec = a;
            double fwd = .5 + .8 * r.applyAsDouble(5);
            double[] p = v(c[0] + Math.cos(ang) * out * sec, Math.max(g + .05, g + .2 + up * sec - 16 * sec * sec), c[2] + Math.sin(ang) * out * sec + fwd * 3 * sec);
            double z = (.06 + .1 * r.applyAsDouble(4)) * (1 - smooth((a - 1) / .6));
            if (z < .01) continue;
            m.box(v(-z, -z * .8, -z), v(z, z * .8, z), i % 3 != 0 ? SNOW : PACK,
                    mul(t(p), ry(ang + a * 4 * (r.applyAsDouble(6) - .5)), rx(a * 5 * (r.applyAsDouble(7) - .5))));
        }
    }

    /** The burst's soft snow: a ring rolling out low over the snow and a plume thrown up out of the middle. */
    private void burstPuffs(Scene s) {
        double a = age(s, burst);
        if (a < 0 || a > 2.6) return;
        double g = floor, spread = 1 - Math.exp(-a / .35), op = .8 * (1 - smooth((a - .3) / 1.1));
        double[] c = burst.at;
        for (int i = 0; i < 26; i++) {
            double ang = i / 26.0 * TAU + rnd(i) * .4, r = .5 + 3.2 * spread * (.75 + .5 * rnd(i * 3)), lift = .35 + .8 * spread * rnd(i * 5);
            m.puff(v(c[0] + Math.cos(ang) * r, g + lift, c[2] + Math.sin(ang) * r + .8 * spread), .8 + 1.1 * spread, v(.96, .97, 1), op, i);
        }
        for (int i = 0; i < 16; i++) {
            double up = (1.2 + 4.2 * rnd(i + 40)) * (1 - Math.exp(-a / .25)), r = .4 + .9 * rnd(i + 60) * spread, ang = rnd(i + 80) * TAU;
            m.puff(v(c[0] + Math.cos(ang) * r, g + up, c[2] + Math.sin(ang) * r + .6 * spread), .6 + 1.0 * spread, v(.97, .98, 1), op, i + 40);
        }
    }

    /** Snow flecks in the air round you as you fall, streaked by your speed: the snow rushing past. */
    private void flecks(Scene s) {
        double k = Math.max(s.col("sp_speed"), s.col("sp_rush") * .8);
        if (k < .02 || s.ft() / 2 > hitStory) return;
        double[] here = s.player(), before = s.playerAt(s.t() - 1), vel = sub(here, before);
        double sp = len(vel);
        if (sp < 1e-3) return;
        double[][] ax = flow(scale(vel, 1 / sp), 0);
        double cell = 14;
        double[] col = {1, 1, 1, .55 * k};
        for (int i = 0; i < 90; i++) {
            double[] seed = v(rnd(i) * cell, rnd(i + 50) * cell, rnd(i + 90) * cell), p = new double[3];
            for (int j = 0; j < 3; j++) p[j] = here[j] + (((seed[j] - here[j]) % cell) + cell * 1.5) % cell - cell / 2;
            double l = Math.min(2.4, sp * .45 * (.6 + .4 * rnd(i + 7)));
            flatBox(v(-l / 2, -.03, -.03), v(l / 2, .03, .03), col, frame(ax, p));
        }
    }

    // ------------------------------------------------------------------ the screen

    @Override public void hud(DrawContext context, Scene s, int w, int h, double shown) {
        double bt = s.col("sp_bt") * shown;
        if (bt > .01) LandingHud.vignette(context, w, h, 14 / 255.0, 6 / 255.0, 28 / 255.0, .6 * bt, .4);  // the edges darken toward the void
        double speed = s.col("sp_speed") * shown;
        LandingHud.speedLines(context, w, h, s.t(), speed, .38 * speed, 120);
    }
}
