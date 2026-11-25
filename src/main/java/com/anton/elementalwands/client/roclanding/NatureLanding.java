package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.roclanding.L.*;

import com.anton.elementalwands.client.renderer.NatureMeshes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/**
 * Nature's landing (art/storm_roc/intro/nature_landing.py; its effects are natureSolids,
 * natureSnow, natureTrail, natureGlow and natureHud in animatic.js): a Springbloom pod pitched into
 * the snow bursts open and bounces them high, a short bullet time, a Thornbite vine swings them down
 * into a slide that leaves a track of grass and flowers. The bake's columns are the n_ ones.
 */
public final class NatureLanding implements RocLanding {
    private static final double[][] VINE = {{.47, .89, .19}, {.18, .52, .1}, {.19, .72, .13}};
    private static final double[][] PETALS = {{1, .55, .72}, {1, .97, .92}, {1, .84, .22}, {.74, .56, 1}, {1, .45, .36}, {1, .66, .82}};

    private record Piece(double[] col, double size, double flat) {}
    private static final Piece KERNEL = new Piece(v(1, .8, .15), .05, 1), GOLD = new Piece(v(.93, .61, .08), .045, 1),
            LEAF = new Piece(v(.47, .89, .19), .11, .2), DARK_LEAF = new Piece(v(.19, .72, .13), .1, .2),
            SNOW = new Piece(v(.93, .95, 1), .07, .8), CHUNK = new Piece(v(.85, .88, .94), .13, .8),
            PINK = new Piece(v(1, .55, .72), .08, .15), CREAM = new Piece(v(1, .97, .92), .08, .15);
    private record Kind(int n, double life, double out0, double out1, double up0, double up1, double g, Piece[] pieces) {}
    private static final Map<String, Kind> BURSTS = Map.of(
            "pollen", new Kind(40, 36, 1.2, 3.6, 3, 6.5, 9, new Piece[]{KERNEL, KERNEL, GOLD, LEAF, DARK_LEAF, PINK}),
            "bounce", new Kind(30, 32, 2, 5, 2, 5, 9, new Piece[]{KERNEL, GOLD, LEAF, LEAF, DARK_LEAF, PINK, CREAM}),
            "launch", new Kind(40, 34, .5, 2, 8, 14, 14, new Piece[]{KERNEL, GOLD, LEAF, PINK}),
            "bite", new Kind(34, 30, 1, 3.4, 2, 6, 10, new Piece[]{SNOW, SNOW, CHUNK, LEAF}));

    private record Burst(double tick, double clock, String kind, double[] at) {}
    private record Slide(double[] from, double[] to, double c0, double c1) {
        double[] point(double ft) { double u = clamp((ft - c0) / (c1 - c0), 0, 1); return lerp(from, to, 1 - Math.pow(1 - u, 3)); }
        double passed(double v) { return c0 + (1 - Math.cbrt(1 - clamp(v, 0, .9999))) * (c1 - c0); }
    }

    private List<Burst> bursts;
    private Slide slide;
    private double[] pad;
    private double latch, release;

    private void load(Scene s) {
        if (bursts != null) return;
        JsonObject f = s.facts();
        bursts = new ArrayList<>();
        for (JsonElement e : f.getAsJsonArray("bursts")) {
            JsonObject o = e.getAsJsonObject();
            bursts.add(new Burst(o.get("tick").getAsDouble(), o.get("clock").getAsDouble(), o.get("kind").getAsString(), Scene.vec(o.getAsJsonArray("at"))));
        }
        JsonObject sl = f.getAsJsonObject("slide");
        slide = new Slide(Scene.vec(sl.getAsJsonArray("from")), Scene.vec(sl.getAsJsonArray("to")), sl.get("c0").getAsDouble(), sl.get("c1").getAsDouble());
        pad = Scene.vec(f.getAsJsonArray("pad"));
        latch = s.track().beat("latch");
        release = s.track().beat("release");
    }

    private Burst burst(String kind) {
        for (Burst b : bursts) if (b.kind.equals(kind)) return b;
        throw new IllegalStateException("No " + kind + " burst");
    }

    @Override public void draw(Scene s) {
        load(s);
        double[] tip = s.pt("n_wtip");
        solids(s, tip);
        snow(s);
        trail(s);
        glow(s, tip);
    }

    // ------------------------------------------------------------------ solids

    private void solids(Scene s, double[] tip) {
        LandingMesh m = s.mesh();
        double ground = s.ground(), ft = s.ft(), t = s.t();
        double open = s.col("n_open"), wilt = s.col("n_wilt");
        // The Springbloom: popping open, dipping under you and springing back past full; wilting later.
        if (open > 0 && wilt < 1) {
            double k = .35 + .65 * open, sq = s.col("n_squash"), wide = 1 / Math.sqrt(Math.max(.3, sq));
            double[] place = mul(t(pad[0], pad[1] - .25 * wilt, pad[2]), s(k * wide, Math.max(.02, open) * sq * (1 - .85 * wilt), k * wide));
            m.placeMesh(NatureMeshes.mesh(wilt > 0 ? "springbloom_wilt" : "springbloom_pad"), place, id());
        }
        // Grass bursting up through the snow round it as it opens.
        double ga = ft - burst("grass").clock;
        if (ga >= 0) for (int i = 0; i < 26; i++) {
            double a = i / 26.0 * Math.PI * 2 + hash(i) * .3, r = 1.2 + 1.4 * hash(i * 2.1), g = clamp((ga - r * 1.2) / 3, 0, 1);
            if (g <= 0) continue;
            double[] base = v(pad[0] + Math.cos(a) * r, ground, pad[2] + Math.sin(a) * r);
            if (i % 3 != 0) tuft(m, base, a, backEase(g) * (1 - .6 * wilt), i + 500, ft);
            else flower(m, base, backEase(g) * (1 - .6 * wilt), i + 500, ft);
        }
        // The pod, spinning down off the wand.
        if (s.col("n_pod_on") > .5) {
            double[] spin = mul(ry(t * .55), rx(t * .8));
            m.placeMesh(NatureMeshes.mesh("springbloom_pod"), mul(L.t(s.pt("n_pod")), spin, L.s(1.5)), spin);
        }
        // Leaves spiralling in to the wand's tip as it charges.
        double glow = s.col("n_glow"), vineShown = s.col("n_vine");
        if (glow > .12 && vineShown < .5) for (int k = 0; k < 10; k++) {
            double ph = (ft * .05 + k / 10.0) % 1, r = .1 + 1.1 * (1 - ph), a = k * 2.4 + ft * .3 + ph * 5;
            double[] c = add(tip, Math.cos(a) * r, Math.sin(a * 1.3) * r * .45, Math.sin(a) * r);
            double z = .07 * glow * Math.sin(Math.PI * ph);
            if (z > .004) m.box(v(-z, -z * .2, -z * 1.7), v(z, z * .2, z * 1.7), k % 2 != 0 ? VINE[0] : VINE[2], mul(L.t(c), ry(a), rx(a * .7)));
        }
        // The vine and its flytrap.
        if (vineShown > .02) {
            double size = 1.7 * Math.min(1, vineShown * 1.3), taut = s.col("n_taut");
            double[] end = s.pt("n_tip"), dir = norm(sub(end, tip));
            boolean bitten = taut > 0 || (t > latch && t < release + 1);
            double[] head = bitten ? add(end, 0, .12, 0) : end;
            double[] neck = sub(head, scale(bitten ? v(0, -1, 0) : dir, .2 * size));
            vine(m, tip, neck, ft, taut, size, s.col("n_leaf"));
            trap(m, neck, bitten ? v(dir[0] * .3, -1, dir[2] * .3) : dir, bitten ? 4 : 38, size * .9);
        }
        // The bite: roots splayed out into the snow.
        Burst bite = burst("bite");
        double ba = ft - bite.clock;
        if (ba >= 0) {
            double grow = 1 - Math.pow(1 - clamp(ba / 10, 0, 1), 3);
            for (int k = 0; k < 8; k++) {
                double ang = k / 8.0 * Math.PI * 2 + hash(k) * .6, len = (1 + hash(k + 5) * 1.2) * grow;
                double[] p = v(bite.at[0], ground + .03, bite.at[2]);
                for (int j = 0; j < 5; j++) {
                    double turn = (hash(k * 4 + j) - .5) * .9;
                    double[] q = add(p, Math.cos(ang + turn) * len / 5, 0, Math.sin(ang + turn) * len / 5);
                    m.beam(p, q, .1 * (1 - j * .16), j % 2 != 0 ? v(.24, .4, .11) : v(.18, .52, .1));
                    p = q;
                }
            }
        }
        // The slide: the green track, the grass and the flowers, the spray.
        slideField(m, ft, ground);
        // Kernels, leaves and snow chunks thrown out by each burst.
        for (Burst b : bursts) {
            Kind kind = BURSTS.get(b.kind);
            if (kind == null) continue;
            double age = ft - b.clock;
            if (age < 0 || age > kind.life) continue;
            double sec = age / 20;
            for (int i = 0; i < kind.n; i++) {
                final int n = i;
                java.util.function.DoubleUnaryOperator r = k -> hash(b.clock * 1.37 + n * 7.1 + k * 3.3);
                double a = r.applyAsDouble(0) * Math.PI * 2, out = kind.out0 + r.applyAsDouble(1) * (kind.out1 - kind.out0);
                double upv = kind.up0 + r.applyAsDouble(2) * (kind.up1 - kind.up0);
                double[] p = v(b.at[0] + Math.cos(a) * (.2 + out * sec), Math.max(ground + .04, b.at[1] + upv * sec - kind.g * sec * sec / 2), b.at[2] + Math.sin(a) * (.2 + out * sec));
                Piece piece = kind.pieces[(int) Math.floor(r.applyAsDouble(4) * kind.pieces.length)];
                double fade = 1 - smooth((age - kind.life * .6) / (kind.life * .4)), z = piece.size * fade;
                if (z < .005) continue;
                double[] place = mul(L.t(p), ry(a + age * .3 * (r.applyAsDouble(5) - .5)), rx(age * .25 * (r.applyAsDouble(6) - .5)));
                m.box(v(-z, -z * piece.flat, -z * .7), v(z, z * piece.flat, z * .7), piece.col, place);
            }
        }
    }

    /** Thornbite's braid: three strands from the wand's tip to the head, slack in flight, pulled straight once it bites. */
    private static void vine(LandingMesh m, double[] root, double[] end, double ft, double taut, double size, double leafing) {
        double big = dist(root, end);
        if (big < .05) return;
        double[] axis = norm(sub(end, root)), side = cross(axis, v(0, 1, 0));
        if (len(side) < .1) side = v(1, 0, 0);
        side = norm(side);
        double[] up = norm(cross(side, axis));
        int n = Math.max(28, (int) Math.round(big * 7));
        final double[] sd = side;
        interface P { double[] at(double u, int k); }
        P point = (u, k) -> {
            double ph = u * Math.PI * (4 + big * .9) + k * Math.PI * 2 / 3, r = .043 * size * (.5 + .5 * Math.sin(Math.PI * Math.min(1, u * 3)));
            double sag = (1 - taut) * Math.sin(Math.PI * u) * (.18 * size + .35 * Math.sin(u * 9 - ft * .8));
            double sway = (1 - taut) * .2 * Math.sin(u * 7 + ft * .6) * Math.sin(Math.PI * u);
            double thrum = taut * .025 * Math.sin(ft * 3.1 + u * 20) * Math.sin(Math.PI * u);
            return add(add(lerp(root, end, u), scale(up, sag + thrum + Math.sin(ph) * r)), scale(sd, Math.cos(ph) * r + sway));
        };
        for (int k = 0; k < 3; k++) for (int i = 0; i < n; i++) {
            double u = i / (double) n;
            double[] a = point.at(u, k), b = point.at((i + 1) / (double) n, k);
            m.beam(a, b, (.043 - u * .012) * size, VINE[k]);
            if (k == 1 && i % 5 == 2) m.beam(a, add(a, add(scale(sd, (i % 2 != 0 ? 1 : -1) * .13 * size), scale(up, .06 * size))), .028 * size, v(.13, .32, .07));
        }
        for (int i = 2; i < n - 1; i += 3) {
            double u = i / (double) n, grow = clamp((leafing * 1.5 - (1 - u)) * 3, 0, 1);
            if (grow <= 0) continue;
            double[] a = point.at(u, 0);
            double sgn = i % 2 != 0 ? 1 : -1, g = backEase(grow);
            m.leaf(a, add(a, add(add(scale(sd, sgn * .3 * size * g), scale(up, .12 * size * g)), scale(axis, -.08 * size * g))), .13 * size * g, i % 4 < 2 ? VINE[0] : VINE[2]);
        }
    }

    /** The flytrap on the vine's end: jaws open in flight, shut and half buried once it bites. */
    private static void trap(LandingMesh m, double[] at, double[] dir, double open, double size) {
        double[] place = mul(along(at, add(at, dir)), L.s(size));
        double[] jaw = v(.18, .52, .1), inner = v(.78, .2, .24), tooth = v(.95, .93, .8);
        m.box(v(-.1, -.1, -.22), v(.1, .1, .02), v(.15, .42, .08), place);
        for (int sgn : new int[]{1, -1}) {
            double[] j = mul(place, rx(-sgn * rad(open)));
            m.box(v(-.16, sgn > 0 ? 0 : -.06, 0), v(.16, sgn > 0 ? .06 : 0, .34), jaw, j);
            m.box(v(-.13, sgn > 0 ? -.01 : 0, .02), v(.13, sgn > 0 ? 0 : .01, .3), inner, j);
            for (int i = 0; i < 4; i++) m.box(v(-.15 + i * .1, sgn > 0 ? -.05 : 0, .3), v(-.12 + i * .1, sgn > 0 ? 0 : .05, .38), tooth, j);
        }
    }

    /** Grass blades in a tuft at base, grown by e. */
    private static void tuft(LandingMesh m, double[] base, double a, double e, int i, double ft) {
        double[][] cols = {{.3, .62, .16}, {.42, .78, .2}, {.24, .5, .12}};
        for (int j = 0; j < 3; j++) {
            double h = (.26 + hash(i * 7 + j) * .34) * e, lean = (hash(i * 11 + j) - .5) * .6 + .12 * Math.sin(ft * .07 + i);
            m.beam(add(base, (j - 1) * .05, 0, (hash(i + j) - .5) * .08), add(base, Math.cos(a + j - 1) * lean * h, h, Math.sin(a + j - 1) * lean * h), .045, cols[j]);
        }
    }

    private static void flower(LandingMesh m, double[] base, double e, int i, double ft) {
        double h = (.3 + hash(i * 2.3) * .38) * e;
        double[] top = add(base, .06 * Math.sin(ft * .08 + i), h, .06 * Math.cos(ft * .07 + i * 1.3));
        m.beam(base, top, .035, v(.25, .55, .14));
        m.leaf(add(base, 0, h * .35, 0), add(base, .14 * e * Math.cos(i), h * .5, .14 * e * Math.sin(i)), .06 * e, VINE[0]);
        double[] col = PETALS[i % PETALS.length];
        double z = .08 * e;
        double[] place = mul(L.t(top), ry(hash(i) * 6.3 + ft * .01), rz(.3 * Math.sin(i)));
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            m.box(v(d[0] * z * 1.15 - z * .7, -.014, d[1] * z * 1.15 - z * .7), v(d[0] * z * 1.15 + z * .7, .014, d[1] * z * 1.15 + z * .7), col, place);
        m.box(v(-z * .5, -.01, -z * .5), v(z * .5, .035, z * .5), v(1, .8, .18), place);
    }

    /** The slide's mark: a green track behind you, grass and flowers springing up along it and round where you stop, snow sprayed off your feet, a few petals. */
    private void slideField(LandingMesh m, double ft, double ground) {
        Slide sl = slide;
        if (ft < sl.c0) return;
        double[] dir = norm(sub(sl.to, sl.from)), side = norm(cross(dir, v(0, 1, 0)));
        double e = 1 - Math.pow(1 - clamp((ft - sl.c0) / (sl.c1 - sl.c0), 0, 1), 3);
        java.util.function.DoubleFunction<double[]> at = u -> lerp(sl.from, sl.to, u);
        java.util.function.DoubleUnaryOperator width = u -> {
            double age = ft - sl.passed(Math.max(0, u));
            return (.3 + 1.2 * (1 - Math.exp(-age / 10))) * (.85 + .15 * Math.sin(u * 17)) * clamp((u + .12) / .1, 0, 1);
        };
        double[] green = {.36, .6, .21}, flat = v(0, 1, 0);
        for (int i = 0; i < 32; i++) {
            double v0 = -.12 + (e + .12) * i / 32, v1 = -.12 + (e + .12) * (i + 1) / 32, wa = width.applyAsDouble(v0), wb = width.applyAsDouble(v1);
            double[] a = at.apply(v0), b = at.apply(v1);
            double[][] q = {add(a, scale(side, -wa)), add(a, scale(side, wa)), add(b, scale(side, wb)), add(b, scale(side, -wb))};
            for (double[] x : q) x[1] = ground + .02;
            m.tri(q[0], q[2], q[1], flat, green);
            m.tri(q[0], q[3], q[2], flat, green);
        }
        double spread = 3.2 * (1 - Math.pow(1 - clamp((ft - sl.c1 + 3) / 34, 0, 1), 3));
        if (spread > .1) m.fan(sl.to, ground + .021, a -> spread * (.82 + .12 * Math.sin(a * 6 + 1) + .06 * Math.sin(a * 11)), green, 56);
        for (int i = 0; i < 120; i++) {
            double u = -.1 + 1.1 * hash(i * 2.37), off = (hash(i * 5.1) - .5) * 3;
            if (u > e) continue;
            double g = clamp((ft - sl.passed(Math.max(0, u)) - Math.abs(off) * 3) / 5, 0, 1);
            if (g <= 0) continue;
            double[] base = add(at.apply(u), scale(side, off));
            base[1] = ground;
            if (i % 4 != 0) tuft(m, base, hash(i) * 6.3, backEase(g), i, ft); else flower(m, base, backEase(g), i, ft);
        }
        for (int i = 0; i < 80; i++) {
            double a = hash(i * 3.71 + 9) * Math.PI * 2, r = .9 + 2.4 * Math.sqrt(hash(i * 6.13 + 4)), g = clamp((spread - r) / .8, 0, 1);
            if (g <= 0) continue;
            double[] base = v(sl.to[0] + Math.cos(a) * r, ground, sl.to[2] + Math.sin(a) * r);
            if (i % 3 != 0) tuft(m, base, a, backEase(g), i + 300, ft); else flower(m, base, backEase(g), i + 300, ft);
        }
        for (int k = 0; k < 36; k++) {  // snow sprayed out from your feet, less as you slow
            double tk = sl.c0 + k * (sl.c1 - sl.c0) / 36, age = ft - tk;
            if (age < 0 || age > 22) continue;
            double power = Math.pow(1 - k / 36.0, 1.5), sec = age / 20;
            double[] origin = sl.point(tk);
            for (int j = 0; j < 4; j++) {
                final int kk = k, jj = j;
                java.util.function.DoubleUnaryOperator r = q -> hash(kk * 13.1 + jj * 3.7 + q);
                double sgn = j % 2 != 0 ? 1 : -1;
                double[] vel = add(add(scale(side, sgn * (2 + 3 * r.applyAsDouble(1)) * power), scale(dir, (2 + 3 * r.applyAsDouble(2)) * power)), v(0, (2 + 4 * r.applyAsDouble(3)) * power, 0));
                double[] p = add(origin, scale(vel, sec));
                p[1] = Math.max(ground + .04, ground + .1 + vel[1] * sec - 5 * sec * sec);
                double z = (.05 + .06 * r.applyAsDouble(4)) * (1 - age / 22);
                if (z < .005) continue;
                m.box(v(-z, -z * .8, -z), v(z, z * .8, z), j % 3 != 0 ? v(.93, .95, 1) : v(.85, .88, .94), mul(L.t(p), ry(r.applyAsDouble(5) * 6 + age * .2)));
            }
        }
        for (int i = 0; i < 24; i++) {  // a few petals lifted off the new flowers, drifting
            double age = ft - sl.c1 - hash(i * 4.4) * 16;
            if (age < 0) continue;
            double z = .045 * clamp(age / 4, 0, 1) * (1 - smooth((age - 80) / 30));
            if (z < .004) continue;
            double[] p = v(sl.to[0] + (hash(i * 1.9) - .5) * 5 + age * .012, ground + .2 + age * (.006 + .01 * hash(i * 2.6)) + .15 * Math.sin(age * .1 + i), sl.to[2] + (hash(i * 3.1) - .2) * 5);
            m.box(v(-z, -.01, -z * .6), v(z, .01, z * .6), PETALS[i % PETALS.length], mul(L.t(p), ry(age * .05 + i), rx(age * .06 + i)));
        }
    }

    // ------------------------------------------------------------------ air and light

    /** Soft clouds: the snow ring round the bite, the bounce's and launch's pollen, snow off the slide. */
    private void snow(Scene s) {
        LandingMesh m = s.mesh();
        double ground = s.ground(), ft = s.ft();
        for (Burst b : bursts) {
            double[] ring = switch (b.kind) {
                case "bite" -> new double[]{12, 34, 2.4, .8};
                case "bounce" -> new double[]{16, 30, 3.2, .7};
                case "launch" -> new double[]{8, 20, 1.4, .55};
                default -> null;
            };
            if (ring == null) continue;
            double age = ft - b.clock;
            if (age < 0 || age > ring[1]) continue;
            double spread = 1 - Math.exp(-age / 6);
            boolean pollen = b.kind.equals("bounce") || b.kind.equals("launch");
            double opacity = (pollen ? .4 : .7) * (1 - smooth(age / ring[1]));
            double[] col = pollen ? v(1, .92, .55) : v(.96, .97, 1);
            for (int i = 0; i < ring[0]; i++) {
                double a = i / ring[0] * Math.PI * 2 + hash(i + b.clock) * .4, r = .4 + ring[2] * spread * (.75 + .5 * hash(i * 3 + b.clock));
                double[] c = v(b.at[0] + Math.cos(a) * r, ground + .3 + ring[3] * .4 * spread * hash(i * 5), b.at[2] + Math.sin(a) * r);
                m.puff(c, ring[3] * (.6 + .9 * spread), col, opacity, i + b.clock);
            }
        }
        Slide sl = slide;
        for (int k = 0; k < 10; k++) {
            double tk = sl.c0 + k * (sl.c1 - sl.c0) / 10, age = ft - tk;
            if (age < 0 || age > 30) continue;
            double[] p = sl.point(tk);
            double power = 1 - k / 16.0, size = (.3 + .7 * (1 - Math.exp(-age / 6))) * power * (1 - age / 30);
            for (int sgn : new int[]{-1, 1})
                m.puff(v(p[0] + sgn * (.4 + age * .03), ground + .25 + age * .012, p[2] + hash(k) * .3), size, v(1, 1, 1), .35, k * 2 + sgn);
        }
    }

    /** The wand's light trail through the pitch, the shot and the flip, carried along with you. */
    private void trail(Scene s) {
        double trail = s.col("n_trail");
        if (trail < .02) return;
        double t = s.t();
        double[] here = s.player();
        double[][] pts = new double[17][];
        for (int k = 0; k <= 16; k++) pts[k] = add(sub(s.ptAt("n_wtip", t - k * .5), s.playerAt(t - k * .5)), here);
        for (int k = 0; k < 16; k++) {
            double[] p = pts[k], q = pts[k + 1], d = sub(q, p);
            if (len(d) < 1e-3) continue;
            double[] side = norm(cross(norm(d), norm(sub(s.mesh().eye, p))));
            double w0 = .16 * (1 - k / 16.0), w1 = .16 * (1 - (k + 1) / 16.0), a = trail * (1 - k / 16.0);
            s.mesh().addQuad(sub(p, scale(side, w0)), add(p, scale(side, w0)), add(q, scale(side, w1)), sub(q, scale(side, w1)), new double[]{.55, 1, .4, a * .85});
            s.mesh().addQuad(sub(p, scale(side, w0 * .35)), add(p, scale(side, w0 * .35)), add(q, scale(side, w1 * .35)), sub(q, scale(side, w1 * .35)), new double[]{1, 1, .85, a * .7});
        }
    }

    /** Light: the wand's charge, the pod's pollen trail, pulses up the taut vine, shock rings, pollen hanging in the bullet time. */
    private void glow(Scene s, double[] tip) {
        LandingMesh m = s.mesh();
        double ft = s.ft(), t = s.t(), ground = s.ground();
        double[] green = {.5, 1, .35}, white = {1, 1, .88}, golden = {1, .82, .3};
        double charge = s.col("n_glow");
        if (charge > .02) {
            double f = 1 + .12 * Math.sin(ft * 1.9) + .08 * Math.sin(ft * 3.3);
            m.glow(tip, green, (.25 + 1.1 * charge) * f, .9 * charge);
            m.glow(tip, white, (.1 + .35 * charge) * f, charge);
        }
        for (int k = 1; k <= 14; k++) {
            double tt = t - k * .5;
            if (s.colAt("n_pod_on", tt) < .5) continue;
            m.glow(s.ptAt("n_pod", tt), golden, .45 * (1 - k / 15.0), .7 * (1 - k / 15.0));
        }
        if (s.col("n_pod_on") > .5) { m.glow(s.pt("n_pod"), green, .9, .8); m.glow(s.pt("n_pod"), white, .3, .7); }
        double taut = s.col("n_taut");
        if (s.col("n_vine") > .02 && taut > .2) for (int k = 0; k < 4; k++) {
            double u = 1 - ((ft * .11 + k / 4.0) % 1);
            m.glow(lerp(s.pt("n_tip"), tip, 1 - u), green, .45, taut * Math.sin(Math.PI * u));
        }
        for (Burst b : bursts) {
            double[] ring = switch (b.kind) {
                case "bounce" -> new double[]{6, 3.5, 32, .6};
                case "bite" -> new double[]{6, 3, 26, .7};
                case "launch" -> new double[]{6, 3.2, 30, .7};
                default -> null;
            };
            if (ring == null) continue;
            double age = ft - b.clock;
            if (age < 0 || age > ring[0]) continue;
            double u = age / ring[0], r = ring[1] * (1 - Math.pow(1 - u, 2)), k = (1 - u) * (1 - u);
            for (int i = 0; i < ring[2]; i++) {
                double a = i / ring[2] * Math.PI * 2;
                m.glow(v(b.at[0] + Math.cos(a) * r, ground + .2, b.at[2] + Math.sin(a) * r), v(.65, 1, .5), ring[3], .6 * k);
            }
        }
        double bt = s.col("n_bt");
        if (bt > .02) {
            double[] here = s.player();
            for (int i = 0; i < 60; i++) {
                double[] c = add(here, (hash(i) - .5) * 9, (hash(i + 40) - .35) * 6 + Math.sin(ft * .05 + i) * .2, (hash(i + 120) - .5) * 9);
                m.glow(c, i % 3 != 0 ? golden : green, .07 + .05 * hash(i + 7), .7 * bt);
            }
        }
    }

    /** Over the frame: the bullet time's dark green edges, speed lines, the bite's flash. */
    @Override public void hud(DrawContext context, Scene s, int w, int h, double shown) {
        double bt = s.col("n_bt") * shown;
        if (bt > .01) {
            LandingHud.vignette(context, w, h, 6 / 255.0, 22 / 255.0, 10 / 255.0, .65 * bt, .4);
            LandingHud.tint(context, w, h, 150 / 255.0, 235 / 255.0, 140 / 255.0, .05 * bt);
        }
        double speed = s.col("n_speed") * shown;
        LandingHud.speedLines(context, w, h, s.t(), speed, .38 * speed, 110);
        double impact = s.col("n_impact") * shown;
        LandingHud.tint(context, w, h, 240 / 255.0, 1, 232 / 255.0, .8 * impact * impact);
    }
}
