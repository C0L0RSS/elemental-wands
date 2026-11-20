package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.roclanding.L.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Stone's landing in the Storm Roc intro (art/storm_roc/intro/stone_landing.py), ported line for
 * line from art/storm_roc/intro/landing_stone.js: Gathered Mass tears rocks and earth out of the
 * summit and packs them round the falling player into one big round boulder; it crashes down onto
 * the arrival, the summit cracks open round the crater, and after a beat the ball cracks and breaks
 * apart, the rock falling away and crumbling on the snow.
 *
 * <p>The rock wears the game's own stone: each chunk of the ball is one kind (stone, andesite,
 * tuff, cobblestone, granite, deepslate...), its lumps and the debris others; earth is dirt, the
 * crumbs gravel, the snow snow. The bake's colours pick the family (grey, dark, light, earth).
 *
 * <p>The bake's columns: n_clock (the story clock), st_ball (the ball's centre), st_roll (its roll
 * about +X, radians), st_tip (the wand's tip), st_glow, st_speed, st_bt and st_drop. Its facts:
 * bursts (rip, impact, crack, break) and the ball's chunks, each with its slot frame on the ball,
 * its boxes and crack line, where it tears out of the summit and when it packs on and falls away.
 */
public final class StoneLanding implements RocLanding {
    private static final double GRAV = 32;  // blocks/s/s, as the throw
    private static final double[][] GREYS = {{.62, .64, .64}, {.55, .57, .58}, {.69, .71, .7}, {.5, .52, .53}, {.58, .61, .6}};
    private static final double[] COBBLE = {.44, .46, .47}, DARK = {.33, .34, .37}, SNOW = {.93, .95, 1};
    private static final double[][] DIRT = {{.46, .38, .3}, {.4, .33, .26}};
    private static final double[] LIGHT = {.8, .82, .81}, CRACK = {.16, .17, .19}, PIT = {.38, .39, .42}, DUST = {.86, .86, .84};

    private static Identifier block(String name) { return Identifier.ofVanilla("textures/block/" + name + ".png"); }
    /** The kinds of rock each colour family is drawn in, the commonest repeated. */
    private static final Identifier[] GREY_ROCK = {block("stone"), block("stone"), block("andesite"), block("andesite"), block("tuff"),
            block("cobblestone"), block("granite"), block("stone"), block("andesite"), block("deepslate")};
    private static final Identifier[] DARK_ROCK = {block("deepslate"), block("cobbled_deepslate"), block("tuff")};
    private static final Identifier[] LIGHT_ROCK = {block("diorite"), block("calcite"), block("diorite")};
    private static final Identifier[] COBBLE_ROCK = {block("cobblestone"), block("cobblestone"), block("cobbled_deepslate")};
    private static final Identifier[] EARTH = {block("coarse_dirt"), block("coarse_dirt"), block("dirt"), block("rooted_dirt")};
    private static final Identifier SNOW_BLOCK = block("snow"), GRAVEL = block("gravel");

    /** The block texture a piece of this colour is drawn in, picked by seed within its family. */
    private static Identifier rockFor(double[] col, double seed) {
        Identifier[] family;
        if (col == SNOW) return SNOW_BLOCK;
        if (col[0] - col[2] > .08) family = hash(seed * 2.71 + 3) < .5 ? EARTH : GREY_ROCK;  // half the earth comes up as rock
        else if (col == COBBLE) family = COBBLE_ROCK;
        else if (col[0] < .4) family = DARK_ROCK;
        else if (col[0] > .75) family = LIGHT_ROCK;
        else family = GREY_ROCK;
        return family[(int) Math.floor(hash(seed * 1.913 + 7) * family.length)];
    }

    /** A rock box in the game's stone, as m.box(lo, hi, col, place). */
    private static void rock(LandingMesh m, double[] lo, double[] hi, double[] col, double seed, double[] place) {
        m.box(lo, hi, rockFor(col, seed), seed, place);
    }

    private static double sec(double a) { return a / 20; }  // story-clock game ticks to seconds
    private static double easeOut(double u) { return 1 - Math.pow(1 - clamp(u, 0, 1), 3); }

    private record Burst(double tick, double clock, String kind, double[] at) {}
    private record Box(double[] lo, double[] hi, double[] col) {}
    private record Crack(double[] a, double[] b, int i) {}
    /** One chunk of the ball: its slot frame in the ball's frame (local), boxes and crack, and its life. */
    private record Chunk(double[] local, Box[] boxes, Crack[] cracks, double[] src, double launch, double arrive, double detach,
                         boolean stay, double[] vel, double curve, double[] spin, double crackDelay, double halfMin, double halfMax) {}
    private record Slab(double a, double r, double w, double h, double d, double tilt, double[] col, double delay) {}
    private static final Slab[] SLABS = new Slab[16];
    static {
        for (int i = 0; i < 16; i++)
            SLABS[i] = new Slab(i / 16.0 * Math.PI * 2 + hash(i + 40) * .3, 2.15 + .5 * hash(i + 41), .22 + .16 * hash(i + 42),
                    .18 + .22 * hash(i + 43), .09 + .05 * hash(i + 44), .55 + .5 * hash(i + 45), i % 4 == 1 ? COBBLE : GREYS[i % 5], hash(i + 46) * 1.2);
    }

    private List<Burst> bursts;
    private List<Chunk> chunks;

    private void load(Scene s) {
        if (chunks != null) return;
        JsonObject f = s.facts();
        bursts = new ArrayList<>();
        for (JsonElement e : f.getAsJsonArray("bursts")) {
            JsonObject o = e.getAsJsonObject();
            bursts.add(new Burst(o.get("tick").getAsDouble(), o.get("clock").getAsDouble(), o.get("kind").getAsString(), Scene.vec(o.getAsJsonArray("at"))));
        }
        chunks = new ArrayList<>();
        for (JsonElement e : f.getAsJsonArray("chunks")) {
            JsonObject o = e.getAsJsonObject();
            JsonArray basis = o.getAsJsonArray("basis");
            double[] x = Scene.vec(basis.get(0).getAsJsonArray()), y = Scene.vec(basis.get(1).getAsJsonArray()), z = Scene.vec(basis.get(2).getAsJsonArray());
            double[] centre = Scene.vec(o.getAsJsonArray("centre"));
            double[] local = {x[0], x[1], x[2], 0, y[0], y[1], y[2], 0, z[0], z[1], z[2], 0, centre[0], centre[1], centre[2], 1};
            JsonArray bs = o.getAsJsonArray("boxes");
            Box[] boxes = new Box[bs.size()];
            for (int i = 0; i < boxes.length; i++) {
                JsonArray b = bs.get(i).getAsJsonArray();
                boxes[i] = new Box(Scene.vec(b.get(0).getAsJsonArray()), Scene.vec(b.get(1).getAsJsonArray()), Scene.vec(b.get(2).getAsJsonArray()));
            }
            JsonArray cs = o.getAsJsonArray("cracks");
            Crack[] cracks = new Crack[cs.size()];
            for (int i = 0; i < cracks.length; i++) {
                JsonArray c = cs.get(i).getAsJsonArray();
                cracks[i] = new Crack(Scene.vec(c.get(0).getAsJsonArray()), Scene.vec(c.get(1).getAsJsonArray()), c.get(2).getAsInt());
            }
            double[] half = boxes[0].hi;
            chunks.add(new Chunk(local, boxes, cracks, Scene.vec(o.getAsJsonArray("src")), o.get("launch").getAsDouble(), o.get("arrive").getAsDouble(),
                    o.get("detach").getAsDouble(), o.get("stay").getAsBoolean(), Scene.vec(o.getAsJsonArray("vel")), o.get("curve").getAsDouble(),
                    Scene.vec(o.getAsJsonArray("spin")), o.get("crackDelay").getAsDouble(),
                    Math.min(half[0], Math.min(half[1], half[2])), Math.max(half[0], Math.max(half[1], half[2]))));
        }
    }

    private Burst burst(String kind) {
        for (Burst b : bursts) if (b.kind.equals(kind)) return b;
        throw new IllegalStateException("No " + kind + " burst");
    }

    @Override public void draw(Scene s) {
        load(s);
        double ft = s.ft();
        double[] ball = ballMatrix(s.pt("st_ball"), s.col("st_roll"));
        quarry(s, ft);
        crater(s, ft);
        chunks(s, ft, ball);
        air(s, ft, ball);
    }

    // ------------------------------------------------------------------ maths the JS keeps to itself

    private static double[] rotOnly(double[] m) { double[] r = m.clone(); r[12] = r[13] = r[14] = 0; return r; }
    private static double[] origin(double[] m) { return v(m[12], m[13], m[14]); }
    private static double[] axisRot(double[] axis, double a) {
        double[] n = norm(axis);
        double x = n[0], y = n[1], z = n[2], c = Math.cos(a), s = Math.sin(a), k = 1 - c;
        return new double[]{c + x * x * k, y * x * k + z * s, z * x * k - y * s, 0, x * y * k - z * s, c + y * y * k, z * y * k + x * s, 0,
                x * z * k + y * s, y * z * k - x * s, c + z * z * k, 0, 0, 0, 0, 1};
    }
    /** The ball: at its centre, rolled about +X (the animatic's M.quat with [cos r/2, sin r/2, 0, 0]). */
    private static double[] ballMatrix(double[] centre, double roll) {
        return quat(centre, Math.cos(roll / 2), Math.sin(roll / 2), 0, 0);
    }

    /** A chunk's boxes at m (scaled by k), the slab in the chunk's own kind of rock; its crack line grown by crack (0..1). */
    private static void drawChunk(LandingMesh mesh, Chunk c, double[] m, double crack, double k, int index) {
        if (k != 1) m = mul(m, L.s(k));
        for (int b = 0; b < c.boxes.length; b++) rock(mesh, c.boxes[b].lo, c.boxes[b].hi, c.boxes[b].col, index * 11.3 + b * 3.1, m);
        if (crack > 0) for (Crack cr : c.cracks) {
            double g = clamp(crack * 3 - cr.i, 0, 1);
            if (g <= 0) continue;
            mesh.beam(apply(m, cr.a), apply(m, lerp(cr.a, cr.b, g)), .035, CRACK);
        }
    }

    /** Where a piece thrown from p with velocity v (blocks/s) is after sec seconds, resting on the snow rest above it. */
    private record Thrown(double[] pos, boolean landed, double since, double tl) {}
    private static Thrown thrown(double ground, double[] p, double[] v, double sec, double rest) {
        double y0 = p[1] - rest;
        double tl = (v[1] + Math.sqrt(Math.max(0, v[1] * v[1] + 2 * GRAV * Math.max(0, y0 - ground)))) / GRAV;  // when it lands
        double t = Math.min(sec, tl);
        return new Thrown(v(p[0] + v[0] * t, Math.max(ground + rest, p[1] + v[1] * t - GRAV / 2 * t * t), p[2] + v[2] * t), sec >= tl, sec - tl, tl);
    }

    // ------------------------------------------------------------------ the ball

    private void chunks(Scene s, double ft, double[] ball) {
        LandingMesh m = s.mesh();
        double g0 = s.ground(), crackAt = burst("crack").clock;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            if (ft < c.launch) continue;
            double[] w = mul(ball, c.local);
            if (ft < c.arrive) {
                // Torn out of the snow and flying up to the ball, tumbling, homing on its slot.
                double u = (ft - c.launch) / (c.arrive - c.launch), e = Math.pow(u, 1.7);
                double[] target = origin(w), src = v(c.src[0], g0 - .3, c.src[2]);
                double[] side = norm(cross(sub(target, src), v(0, 1, 0)));
                double[] pos = flight(c, u, target, g0);
                drawChunk(m, c, mul(L.t(pos), rotOnly(w), axisRot(c.spin, (1 - u) * 9)), 0, 1.5 - .5 * smooth(u), i);
                for (int k = 1; k <= 2; k++) {  // gravel dragged along behind it
                    double uk = u - .06 * k;
                    if (uk <= 0) continue;
                    double ek = Math.pow(uk, 1.7);
                    double[] q = add(lerp(src, target, ek), add(scale(side, c.curve * 2.4 * Math.sin(Math.PI * ek)),
                            v((hash(i * 9 + k) - .5) * .6, (hash(i * 5 + k) - .5) * .6, (hash(i * 3 + k) - .5) * .6)));
                    double z = .05 + .04 * hash(i + k * 7);
                    rock(m, v(-z, -z, -z), v(z, z, z), k == 2 ? DIRT[i % 2] : COBBLE, i * 5.7 + k, mul(L.t(q), axisRot(c.spin, ft * .4 + k)));
                }
                continue;
            }
            if (ft < c.detach) {
                // Packed on: slammed into place, cracked once the beat is over.
                double age = ft - c.arrive, pop = 1 + .15 * Math.exp(-age / 1.2) * (age < 6 ? 1 : 0);
                drawChunk(m, c, w, clamp((ft - crackAt - c.crackDelay) / 4, 0, 1), pop, i);
                if (age < 12) for (int k = 0; k < 4; k++) {  // chips knocked off as it lands
                    final int kk = k, ii = i;
                    java.util.function.DoubleUnaryOperator r = q -> hash(ii * 17.3 + kk * 4.1 + q);
                    double a = r.applyAsDouble(0) * Math.PI * 2;
                    double[] out = norm(dir(w, v(0, 0, 1)));
                    double[] p = add(origin(w), scale(add(out, v(Math.cos(a) * .6, 0, Math.sin(a) * .6)), (2 + 3 * r.applyAsDouble(1)) * sec(age)));
                    double z = .045 * (1 - age / 12);
                    rock(m, v(-z, -z, -z), v(z, z, z), k % 2 != 0 ? LIGHT : GREYS[k % 5], i * 3.3 + k, mul(L.t(p), ry(a + age), rx(age * .7)));
                }
                continue;
            }
            double age = ft - c.detach, sec = sec(age);
            if (c.stay) {
                // The bottom of the ball: left bedded in the crater, slumping and crumbling.
                double k = 1 - .5 * smooth(age / 30), sink = .4 * smooth(age / 24);
                drawChunk(m, c, mul(L.t(0, -sink, 0), w), 1, k, i);
                continue;
            }
            // Falling away: thrown off the ball, tumbling, breaking on the snow into pieces that crumble.
            Thrown fall = thrown(g0, origin(w), c.vel, sec, c.halfMin);
            if (!fall.landed) {
                drawChunk(m, c, mul(L.t(fall.pos), axisRot(c.spin, sec * 4 * (hash(i * 13.7 + 5) + .4)), rotOnly(w)), 1, 1, i);
                continue;
            }
            double big = c.halfMax, since = fall.since;
            double[] hit = fall.pos;
            for (int k = 0; k < 3; k++) {
                final int kk = k, ii = i;
                java.util.function.DoubleUnaryOperator rr = q -> hash(ii * 31.1 + kk * 7.7 + q);
                double a = rr.applyAsDouble(0) * Math.PI * 2, z = big * (.3 + .1 * rr.applyAsDouble(1));
                double out = .8 + 1.4 * rr.applyAsDouble(2);
                Thrown piece = thrown(g0, add(hit, v(Math.cos(a) * z, 0, Math.sin(a) * z)),
                        v(Math.cos(a) * out + c.vel[0] * .35, 1 + 1.6 * rr.applyAsDouble(3), Math.sin(a) * out + c.vel[2] * .35), since, z * .7);
                double rest = piece.landed ? piece.since * 20 : 0, crumble = 1 - .6 * smooth(rest / 30);
                double zz = z * crumble, spin = piece.landed ? piece.tl : since;
                double[] col = k == 2 ? COBBLE : c.boxes[0].col;
                rock(m, v(-zz, -zz * .7, -zz * .9), v(zz, zz * .7, zz * .9), col, k == 2 ? i * 2.9 + k : i * 11.3,
                        mul(L.t(sub(piece.pos, v(0, z * .7 - zz * .7, 0))), ry(a + spin * 3), rx(spin * 4 * (rr.applyAsDouble(4) - .5))));
                for (int j = 0; j < 3; j++) {  // gravel crumbling off it
                    double g = smooth((rest - 4 - j * 8) / 12);
                    if (g <= 0) continue;
                    double[] b = add(piece.pos, v((hash(i * 3 + k * 5 + j) - .5) * z * 3.2, 0, (hash(i * 7 + k * 3 + j) - .5) * z * 3.2));
                    double gz = .03 * g;
                    b[1] = g0 + gz * .6;
                    m.box(v(-gz, -gz * .6, -gz), v(gz, gz * .6, gz), GRAVEL, i + j * .3 + k * .7, mul(L.t(b), ry(j + k)));
                }
            }
        }
    }

    /** Where a chunk flying up to its slot at target is, u (0..1) of the way through its flight. */
    private static double[] flight(Chunk c, double u, double[] target, double g0) {
        double e = Math.pow(clamp(u, 0, 1), 1.7);
        double[] src = v(c.src[0], g0 - .3, c.src[2]);
        double[] side = norm(cross(sub(target, src), v(0, 1, 0)));
        return add(lerp(src, target, e), add(scale(side, c.curve * 2.4 * Math.sin(Math.PI * e)), v(0, .5 * smooth(u / .15) * (1 - e), 0)));
    }

    // ------------------------------------------------------------------ cracks in the snow, the quarry's pits

    private static void crackLines(LandingMesh m, double g0, double[] centre, double ft, double at, int n, double len0, double len1,
                                   double seed, double grow, double r0) {
        for (int k = 0; k < n; k++) {
            double ang = k / (double) n * Math.PI * 2 + hash(seed + k) * .5, len = len0 + (len1 - len0) * hash(seed + k * 3.3);
            double g = easeOut((ft - at - hash(seed + k * 2.1) * 1.5) / grow);
            if (g <= 0) continue;
            double[] p = v(centre[0] + Math.cos(ang) * r0, g0 + .03, centre[2] + Math.sin(ang) * r0);
            double dir = ang;
            for (int j = 0; j < 6; j++) {
                dir += (hash(seed + k * 6 + j) - .5) * .9;
                double step = len / 6 * clamp(g * 6 - j, 0, 1);
                if (step <= 0) break;
                double[] q = v(p[0] + Math.cos(dir) * step, p[1], p[2] + Math.sin(dir) * step);
                m.leaf(p, q, .2 * (1 - j / 7.0), CRACK);
                if (j % 2 == 1) {  // a broken edge kicked up along it
                    double z = .08 * (1 - j / 8.0);
                    rock(m, v(-z, -z * .5, -z * 1.6), v(z, z * .5, z * 1.6), j % 3 != 0 ? GREYS[(k + j) % 5] : COBBLE, seed + k * 6 + j, mul(L.t(q[0], g0 + z * .5, q[2]), ry(dir + j), rz(.5)));
                }
                p = q;
            }
        }
    }

    private void quarry(Scene s, double ft) {
        LandingMesh m = s.mesh();
        Burst rip = burst("rip");
        double g0 = s.ground();
        if (ft < rip.clock) return;
        crackLines(m, g0, rip.at, ft, rip.clock, 8, 2.4, 4.8, 11, 5, .5);
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            double age = ft - c.launch;
            if (age < 0) continue;
            double open = easeOut(age / 3), r0 = (.35 + .25 * hash(i * 1.7)) * open;
            final int ii = i;
            m.fan(c.src, g0 + .025, a -> r0 * (.8 + .25 * Math.sin(a * 3 + ii) + .12 * Math.sin(a * 7 + ii * 2)), PIT, 9);
            m.fan(c.src, g0 + .03, a -> r0 * .55 * (.8 + .2 * Math.sin(a * 5 + ii)), DARK, 7);
            for (int k = 0; k < 5; k++) {  // snow, earth and gravel flicked up as it tears out
                final int kk = k;
                java.util.function.DoubleUnaryOperator r = q -> hash(ii * 23.3 + kk * 3.9 + q);
                double a = r.applyAsDouble(0) * Math.PI * 2, z = .04 + .05 * r.applyAsDouble(1), out = 1 + 2.2 * r.applyAsDouble(2);
                Thrown piece = thrown(g0, v(c.src[0], g0 + .1, c.src[2]), v(Math.cos(a) * out, 2.5 + 4 * r.applyAsDouble(3), Math.sin(a) * out), sec(age), z);
                double[] col = k < 2 ? SNOW : k < 4 ? GREYS[k] : DIRT[k % 2];
                rock(m, v(-z, -z * .8, -z), v(z, z * .8, z), col, ii * 4.1 + kk, mul(L.t(piece.pos), ry(a + (piece.landed ? piece.tl : sec(age)) * 6)));
            }
        }
    }

    // ------------------------------------------------------------------ the crater

    private void crater(Scene s, double ft) {
        LandingMesh m = s.mesh();
        Burst b = burst("impact");
        double age = ft - b.clock, g0 = s.ground();
        double[] c = b.at;
        if (age < 0) return;
        double open = easeOut(age / 2.2);
        m.fan(c, g0 + .018, a -> 2.9 * open * (.85 + .12 * Math.sin(a * 5 + 1) + .06 * Math.sin(a * 11)), v(.56, .57, .6), 48);
        m.fan(c, g0 + .024, a -> 2.1 * open * (.86 + .1 * Math.sin(a * 6 + 2) + .05 * Math.sin(a * 13)), PIT, 48);
        m.fan(c, g0 + .03, a -> 1.3 * open * (.82 + .18 * Math.sin(a * 4)), DARK, 32);
        crackLines(m, g0, c, ft, b.clock, 12, 3, 7.5, 61, 6, 2.2);
        for (Slab sl : SLABS) {  // kicked up round the rim, tilted out, snow still on top
            double g = clamp((age - sl.delay) / 3, 0, 1);
            if (g <= 0) continue;
            double up = backEase(g);
            double[] at = v(c[0] + Math.cos(sl.a) * sl.r, g0, c[2] + Math.sin(sl.a) * sl.r);
            double[] mm = mul(L.t(at), ry(-sl.a + Math.PI / 2), rx(-sl.tilt * up));
            rock(m, v(-sl.w, -.12, -sl.d), v(sl.w, sl.h * up, sl.d), sl.col, sl.a * 9, mm);
            m.box(v(-sl.w * .9, sl.h * up, -sl.d * 1.05), v(sl.w * .9, sl.h * up + .05, sl.d * 1.05), SNOW_BLOCK, sl.a * 7, mm);
        }
        for (int i = 0; i < 70; i++) {  // rubble and snow thrown out, landing; the snow melts away
            final int ii = i;
            java.util.function.DoubleUnaryOperator r = q -> hash(ii * 7.31 + q + 500);
            double a = r.applyAsDouble(0) * Math.PI * 2, out = 3 + 8 * r.applyAsDouble(1), z = .05 + .12 * r.applyAsDouble(2) * r.applyAsDouble(2);
            Thrown piece = thrown(g0, v(c[0] + Math.cos(a) * 1.6, g0 + .3, c[2] + Math.sin(a) * 1.6), v(Math.cos(a) * out, 3.5 + 9 * r.applyAsDouble(3), Math.sin(a) * out), sec(age), z * .7);
            double pick = r.applyAsDouble(4);
            double[] col = pick < .3 ? SNOW : pick < .45 ? DIRT[i % 2] : pick < .6 ? COBBLE : GREYS[i % 5];
            double melt = pick < .3 ? 1 - smooth((age - 24) / 36) : 1;
            if (melt <= .02) continue;
            double spin = piece.landed ? piece.tl : sec(age);
            rock(m, v(-z * melt, -z * .7 * melt, -z * .9 * melt), v(z * melt, z * .7 * melt, z * .9 * melt), col, i * 1.7,
                    mul(L.t(piece.pos), ry(a + spin * 7), rx(spin * 9 * (r.applyAsDouble(5) - .5))));
        }
    }

    // ------------------------------------------------------------------ dust and light

    private void air(Scene s, double ft, double[] ball) {
        LandingMesh m = s.mesh();
        double g0 = s.ground(), t = s.t();
        // The quarry's ring of snow dust as it breaks open.
        Burst rip = burst("rip");
        double ra = ft - rip.clock;
        if (ra >= 0 && ra < 34) {
            double spread = 1 - Math.exp(-ra / 5), opacity = .55 * (1 - smooth(ra / 34));
            for (int i = 0; i < 18; i++) {
                double a = i / 18.0 * Math.PI * 2 + hash(i) * .4, r = .5 + 4 * spread * (.75 + .5 * hash(i * 3));
                m.puff(v(rip.at[0] + Math.cos(a) * r, g0 + .3 + .4 * spread * hash(i * 5), rip.at[2] + Math.sin(a) * r), .6 + .8 * spread, v(.93, .94, .96), opacity, i);
            }
        }
        // Snow puffing where each rock tears out; grit where each packs on (shrinking as they fade);
        // dust where a falling chunk breaks on the snow.
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            double la = ft - c.launch, aa = ft - c.arrive, da = ft - c.detach;
            if (la >= 0 && la < 18) for (int k = 0; k < 2; k++)
                m.puff(v(c.src[0] + (hash(i + k) - .5) * .6, g0 + .25 + la * .03 + k * .15, c.src[2] + (hash(i * 2 + k) - .5) * .6),
                        (.3 + .5 * (1 - Math.exp(-la / 4))) * (1 - la / 18), v(.92, .93, .94), .5, i * 2 + k);
            if (la >= 0 && aa < 0) {
                // A trail of snow and grit streaming off it as it flies up, so the stream reads against the summit.
                double[] target = origin(mul(ball, c.local));
                double u = la / (c.arrive - c.launch);
                for (int k = 1; k <= 4; k++) {
                    double uk = u - .07 * k;
                    if (uk <= 0) break;
                    double[] q = flight(c, uk, target, g0);
                    double fade = (1 - k / 5.0) * smooth(u / .1);
                    m.puff(add(q, (hash(i * 7 + k) - .5) * .3, (hash(i * 3 + k) - .5) * .3, (hash(i * 5 + k) - .5) * .3),
                            .22 + .1 * k, k % 2 != 0 ? v(.95, .96, .97) : v(.72, .66, .58), .5 * fade, i * 8 + k + 800);
                }
            }
            if (aa >= 0 && aa < 10) {
                double[] w = mul(ball, c.local);
                m.puff(add(origin(w), dir(w, v(0, 0, .25))), (.2 + .4 * (1 - Math.exp(-aa / 3))) * (1 - aa / 10), v(.8, .8, .79), .45, i + 100);
            }
            if (da > 0 && da < 40 && !c.stay) {
                double[] w = mul(ball, c.local);
                Thrown fall = thrown(g0, origin(w), c.vel, sec(da), c.halfMin);
                if (fall.landed && fall.since < .8) {
                    double k = fall.since / .8;
                    m.puff(add(fall.pos, 0, .15 + .25 * k, 0), (.25 + .45 * k) * (1 - k * .5), DUST, .3, i + 200);
                }
            }
        }
        // Dust shed off the boulder as it drops.
        double drop = s.col("st_drop");
        if (drop > .02) for (int k = 1; k <= 12; k++) {
            double[] p = s.ptAt("st_ball", t - k * .45);
            m.puff(v(p[0] + (hash(k) - .5) * .8, p[1] + (hash(k * 3) - .5) * .8, p[2] + (hash(k * 5) - .5) * .8), .5 + .1 * k, v(.8, .8, .79), .4 * drop, k + 300);
        }
        // The impact: a fast ring along the snow, then the dust rolling out and settling.
        Burst b = burst("impact");
        double ia = ft - b.clock;
        if (ia >= 0 && ia < 12) {
            double u = ia / 12, r = 1.8 + 7 * easeOut(u);
            for (int i = 0; i < 64; i++) {
                double a = i / 64.0 * Math.PI * 2 + hash(i) * .1;
                if (hash(i + 7) < .2) continue;
                m.puff(v(b.at[0] + Math.cos(a) * r, g0 + .12, b.at[2] + Math.sin(a) * r), .35 + .3 * u, v(.95, .95, .95), .38 * (1 - u), i + 400);
            }
        }
        if (ia >= 0 && ia < 46) {
            double spread = 1 - Math.exp(-ia / 6), opacity = .48 * (1 - smooth(ia / 46));
            for (int i = 0; i < 20; i++) {
                double a = i / 20.0 * Math.PI * 2 + hash(i + 70) * .3, r = 2 + 3.4 * spread * (.75 + .5 * hash(i * 3 + 70));
                m.puff(v(b.at[0] + Math.cos(a) * r, g0 + .25 + .4 * spread * hash(i * 5 + 70), b.at[2] + Math.sin(a) * r), .35 + .6 * spread,
                        i % 3 != 0 ? v(.93, .93, .92) : DUST, opacity, i + 500);
            }
        }
        // Grit puffing out of the cracks; a ring of dust as it breaks apart.
        double ca = ft - burst("crack").clock;
        if (ca >= 0 && ca < 16) for (int i = 0; i < chunks.size(); i += 4) {
            double[] w = mul(ball, chunks.get(i).local);
            m.puff(add(origin(w), dir(w, v(0, 0, .3 + ca * .02))), .15 + .3 * (1 - Math.exp(-ca / 4)), DUST, .4 * (1 - ca / 16), i + 600);
        }
        Burst br = burst("break");
        double ba = ft - br.clock;
        if (ba >= 0 && ba < 40) {
            double spread = 1 - Math.exp(-ba / 6), opacity = .3 * (1 - smooth(ba / 40));
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2 + hash(i + 30) * .4, r = 1.6 + 2 * spread * (.8 + .4 * hash(i + 31));
                m.puff(v(br.at[0] + Math.cos(a) * r, g0 + .3 + .5 * spread * hash(i + 32), br.at[2] + Math.sin(a) * r), .35 + .5 * spread,
                        i % 2 != 0 ? DUST : v(.92, .92, .91), opacity, i + 700);
            }
        }
        // The wand's pull: a small pale light at its tip while it gathers.
        double glow = s.col("st_glow");
        if (glow > .02) {
            double[] tip = s.pt("st_tip");
            double f = 1 + .1 * Math.sin(ft * 1.7);
            m.glow(tip, v(.72, .8, .74), (.18 + .3 * glow) * f, .35 * glow);
            m.glow(tip, v(1, 1, .96), (.06 + .1 * glow) * f, .5 * glow);
        }
    }

    /** Over the frame: the slow motion's dark edge, speed lines in the fall and the drop. */
    @Override public void hud(DrawContext context, Scene s, int w, int h, double shown) {
        double bt = s.col("st_bt") * shown;
        if (bt > .01) LandingHud.vignette(context, w, h, 14 / 255.0, 15 / 255.0, 17 / 255.0, .55 * bt, .4);
        double speed = s.col("st_speed") * shown;
        LandingHud.speedLines(context, w, h, s.t(), speed, .38 * speed, 110);
    }
}
