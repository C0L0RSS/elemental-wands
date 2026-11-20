package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireKit;
import com.anton.elementalwands.client.blockfire.BlockFireMesh;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.math.Vec3d;

/**
 * Fire's landing in the Storm Roc intro (art/storm_roc/intro/fire_landing.py; its effects are
 * art/storm_roc/intro/landing_fire.js, ported here near line for line): out of the tumble a
 * Flamethrower jet held straight down brakes the fall like a rocket thruster, splashing across the
 * snow and boiling it off; the flame cut a few blocks up; a Fire Leap landing, its ring of fire
 * rolling out across the snow; low flames licking round the crouch.
 *
 * <p>The fire is the Fire spells' own block fire (BlockFireMesh and BlockFireKit, which the browser
 * kit was ported from): it is built in world coordinates (the summit frame plus the mesh's origin)
 * and drawn in {@link #emit}. The marks on the snow, the steam and the light go in the landing mesh,
 * in the summit's frame. The bake's columns are the f_ ones and n_clock; the wand's tip is f_tip.</p>
 */
public final class FireLanding implements RocLanding {
    // ------------------------------------------------------------------ the bake's facts

    private record Burst(double tick, double clock, double[] at) {}

    private double floor, reach, jet0, jet1;
    private double[] arrival, landAt, meltAt;
    private Burst ignite, cut, touch;
    /** This frame's block fire, built in draw and drawn in emit. */
    private BlockFireMesh fire;
    /** The summit frame's origin in the world, for this frame. */
    private Vec3d o = Vec3d.ZERO;

    private void load(Scene s) {
        if (arrival != null) return;
        JsonObject f = s.facts();
        floor = f.get("floor").getAsDouble();
        reach = f.get("reach").getAsDouble();
        JsonObject jet = f.getAsJsonObject("jet");
        jet0 = jet.get("c0").getAsDouble();
        jet1 = jet.get("c1").getAsDouble();
        arrival = Scene.vec(f.getAsJsonArray("arrival"));
        landAt = Scene.vec(f.getAsJsonArray("land_at"));
        meltAt = Scene.vec(f.getAsJsonArray("melt_at"));
        for (JsonElement e : f.getAsJsonArray("bursts")) {
            JsonObject b = e.getAsJsonObject();
            Burst burst = new Burst(b.get("tick").getAsDouble(), b.get("clock").getAsDouble(), Scene.vec(b.getAsJsonArray("at")));
            switch (b.get("kind").getAsString()) {
                case "ignite" -> ignite = burst;
                case "cut" -> cut = burst;
                case "touch" -> touch = burst;
                default -> {}
            }
        }
        if (ignite == null || cut == null || touch == null) throw new IllegalStateException("Fire landing bake is missing a burst");
    }

    /** A summit-frame point in the world. */
    private Vec3d w(double[] p) { return new Vec3d(p[0] + o.x, p[1] + o.y, p[2] + o.z); }
    private Vec3d w(double x, double y, double z) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
    /** A world point back in the summit's frame. */
    private double[] summit(Vec3d p) { return new double[]{p.x - o.x, p.y - o.y, p.z - o.z}; }
    private static Vec3d vec(double[] d) { return new Vec3d(d[0], d[1], d[2]); }

    @Override public void draw(Scene s) {
        load(s);
        LandingMesh lm = s.mesh();
        o = new Vec3d(lm.origin[0], lm.origin[1], lm.origin[2]);
        var client = MinecraftClient.getInstance();
        boolean embers = client.options.getParticles().getValue() == ParticlesMode.ALL;
        fire = new BlockFireMesh(new Vec3d(lm.camera[0], lm.camera[1], lm.camera[2]), true, embers, s.ft() / 20, client.world);
        double[] tip = s.pt("f_tip");
        scorch(s);
        jet(s, w(tip));
        nozzle(s, w(tip));
        landing(s);
        lingering(s);
        glows(s, tip);
    }

    @Override public void emit(VertexConsumerProvider consumers, MatrixStack.Entry entry) {
        BlockFireMesh m = fire;
        if (m == null || m.isEmpty()) return;
        // Fire is opaque and lights itself; smoke is see-through and lit by the world, drawn after.
        if (m.hasFire()) {
            m.emitFire(consumers.getBuffer(BlockFireMesh.FIRE), entry);
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockFireMesh.FIRE);
        }
        if (m.hasSmoke()) {
            m.emitSmoke(consumers.getBuffer(BlockFireMesh.SMOKE), entry);
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(BlockFireMesh.SMOKE);
        }
    }

    // ------------------------------------------------------------------ the thruster

    // As the Flamethrower's stream, but longer, wider and hotter, and carried with the wand like a
    // rocket's exhaust: each puff flies out along the jet from where the tip is now, along the way
    // the wand pointed when it left (so the jet bends as the arm swings). Puffs that reach the snow
    // splash and spread across it, boiling it off as steam.
    private static final double SPREAD = .26, D0 = .15, EASE = 2.2, RATE = 10, MAX_AGE = 1.3;  // RATE: puffs per game tick
    private static final double[] STEAM = {.86, .87, .9};  // the browser's smoke texture tinted pale

    /** The cut: the jet coughs in widening gaps for a few ticks, then stops. */
    private double gate(double e) {
        if (e < jet1 - 4) return 1;
        if (e >= jet1) return 0;
        double q = (e - (jet1 - 4)) / 4;
        return smoothstep((noise(e * .5 + 3.7) - (.15 + .6 * q)) / .12);
    }

    private void jet(Scene s, Vec3d tipNow) {
        double ft = s.ft(), t = s.t();
        if (ft < jet0 || ft > jet1 + MAX_AGE * 20) return;
        BlockFireMesh m = fire;
        double floorW = floor + o.y;
        int k0 = (int) Math.ceil(Math.max(jet0, ft - MAX_AGE * 20) * RATE), k1 = (int) Math.floor(Math.min(ft, jet1) * RATE);
        // Once it cuts, what is still in the air flies on from where the tip was, the way it was pointing.
        boolean late = ft > jet1;
        Vec3d dNow = vec(late ? s.ptAt("f_dir", cut.tick) : s.pt("f_dir"));
        Vec3d tip = late ? w(cut.at) : tipNow;
        double rate = Math.max(.25, s.col("f_rate"));
        for (int k = k0; k <= k1; k++) {
            double e = k / RATE, sd = k * 7.31 + 11;
            if (rnd(sd + 4.4) > gate(e)) continue;
            double age = (ft - e) / 20, tb = t - (ft - e) / rate, power = s.colAt("f_power", tb), young = clamp((e - jet0) / 3);
            Vec3d dThen = vec(s.ptAt("f_dir", Math.min(tb, cut.tick)));
            Vec3d D = dNow.multiply(.45).add(dThen.multiply(.55)).normalize();
            Vec3d R = D.crossProduct(UP);
            if (R.length() < .1) R = X;
            R = R.normalize();
            Vec3d VU = R.crossProduct(D);
            double a = rnd(sd + .1) * TAU, r = Math.sqrt(rnd(sd + .2)) * .95;
            Vec3d radial = R.multiply(Math.cos(a)).add(VU.multiply(Math.sin(a)));
            Vec3d dir = D.add(radial.multiply(r * SPREAD)).normalize(), or = tip.add(radial.multiply(r * .06));
            double life = .46 + .16 * rnd(sd + .3), reachK = reach * (.9 + .12 * rnd(sd + .4)) * (.75 + .25 * young), j = .8 + .4 * rnd(sd + .5);
            double hitT = dir.y < -1e-3 ? (or.y - (floorW + .04)) / -dir.y : Double.POSITIVE_INFINITY;
            double stop = Math.max(D0 + .05, hitT - .15);
            boolean blocked = stop < reachK;
            double uHit = blocked ? 1 - Math.pow(1 - (stop - D0) / (reachK - D0), 1 / EASE) : 1;
            double top = 3.7 + .5 * (rnd(sd + .05) - .5);
            double u = age / life;
            if (u < Math.min(uHit, 1)) {
                double x = sAt(u, reachK), sp = Math.pow(1 - u, EASE - 1);
                Vec3d p = or.add(dir.multiply(x));
                double wd = widthAt(x, j, power);
                if (!blocked) wd *= 1 - smoothstep((u - .7) / .3);
                Vec3d kx = new Vec3d(rnd(sd + .8) - .5, rnd(sd + .9) - .5, rnd(sd + 1) - .5).normalize();
                double th = age * 5 * smoothstep((u - .08) / .45);
                Vec3d[] ax = rotate(basis(dir, rnd(sd + .6) * TAU + age * (rnd(sd + .7) < .5 ? -3 : 3)), kx, th);
                double hb = bandAt(x, top);
                cube(m, p, wd, wd * (1 + .8 * sp), wd, heat(hb), sd, ax);
                if (wd > .2) for (int i = 0; i < 2; i++) {
                    double sg = rnd(sd + i * 1.9 + 5) < .5 ? -1 : 1;
                    cube(m, p.add(ax[i * 2].multiply(sg * wd * .5)), wd * .55, heat(hb - .8), sd + i + 1, ax);
                }
                if (rnd(sd + 3) < .12) {  // a spark flicks out of the side of the stream
                    double u0 = .15 + .35 * rnd(sd + 3.1), sa = (age - u0 * life) / .35;
                    if (sa > 0 && sa < 1 && u0 < uHit) {
                        Vec3d start = or.add(dir.multiply(sAt(u0, reachK))), drift = radial.multiply(1.1).add(dir.multiply(.8)).add(0, .5, 0);
                        cube(m, start.add(drift.multiply(1.4 * (1 - Math.pow(1 - sa, 2)))), .08 * (1 - sa), heat(3.6 - 2 * sa), sd + 7, ax);
                    }
                }
            }
            if (blocked) {
                // Splash: flattened against the snow, spreading out across it, then burning out; steam boils off.
                double ageH = uHit * life, sDur = .34 + .2 * rnd(sd + 1.1), vv = (age - ageH) / sDur;
                if (vv < 0) continue;
                Vec3d surf = or.add(dir.multiply(hitT));
                Vec3d tan = new Vec3d(dir.x, 0, dir.z).add(new Vec3d(rnd(sd + 1.2) - .5, 0, rnd(sd + 1.4) - .5).multiply(1.6)).normalize();
                double spread = (.5 + 1.5 * rnd(sd + 1.5)) * (.6 + .7 * (1 - uHit)) * power;
                double wH = Math.max(widthAt(stop, j, power), .14), hbH = bandAt(stop, top);
                if (vv < 1) {
                    double ws = splashWidth(wH, vv);
                    Vec3d c = splashAt(surf, tan, spread, ws, vv);
                    Vec3d[] ax = basis(UP, rnd(sd + 1.6) * TAU + age * 2.5);
                    cube(m, c, ws * 1.2, ws * .5, ws * 1.2, heat(hbH - .2 - 1.6 * vv), sd, ax);
                    if (ws > .18) cube(m, c.add(tan.multiply(ws * .6)).add(0, ws * .2, 0), ws * .5, heat(hbH - .9 - 1.6 * vv), sd + 5, ax);
                }
                if (rnd(sd + 2.1) < .14 && m.embers) {
                    Vec3d c = splashAt(surf, tan, spread, splashWidth(wH, .8), .8);
                    steam(s.mesh(), summit(c.add(0, .1, 0)), age - ageH - sDur * .5, .5 * j, sd + 300, 1.3, .9, .22);
                }
                continue;
            }
            if (rnd(sd + 2) < .16) {  // free puffs that reach the end turn to smoke
                double sa = age - .76 * life, dur = .65 + .15 * rnd(sd + 2.2);
                if (sa > 0 && sa < dur)
                    smoke(m, or.add(dir.multiply(sAt(.76, reachK))).add(dir.multiply(.35 * smoothstep(sa / dur))), sa, .4 * j, sd + 200, 1, dur);
            }
        }
    }

    private static double sAt(double u, double reachK) { return D0 + (reachK - D0) * (1 - Math.pow(1 - u, EASE)); }
    private double fOf(double x) { return clamp((x - D0) / (reach - D0)); }
    private double widthAt(double x, double j, double power) { return j * power * (.13 + .85 * Math.pow(fOf(x), 1.05)) * smoothstep((x - D0) / .35); }
    private double bandAt(double x, double top) { return Math.max(1, top - 2.6 * fOf(x)); }
    private static double splashWidth(double wH, double vv) { return wH * (1 + .35 * Math.sin(Math.min(vv * 2.5, 1) * Math.PI / 2)) * Math.pow(1 - vv, .75); }
    private static Vec3d splashAt(Vec3d surf, Vec3d tan, double spread, double ws, double vv) {
        return surf.add(0, .1 + ws * .25, 0).add(tan.multiply(spread * (1 - Math.pow(1 - vv, 2))));
    }

    /**
     * Steam off the snow: the kit's smoke puff (a body and a smaller cap) as pale see-through cubes in
     * the landing mesh, since the spell mesh's smoke can only darken its texture. p in the summit frame.
     */
    private static void steam(LandingMesh lm, double[] p, double age, double size, double seed, double rise, double duration, double alpha) {
        double l = age / duration;
        if (l <= 0 || l >= 1) return;
        double sz = .7 * size * Math.pow(Math.sin(l * Math.PI), .7) * (.8 + .4 * rnd(seed));
        if (sz <= .01) return;
        double[] q = L.add(p, (rnd(seed + 1) - .5) * .6 * l, rise * l, (rnd(seed + 2) - .5) * .6 * l);
        double tilt = (rnd(seed + 3) - .5) * .3;
        Vec3d[] axes = rotate(yawAxes(rnd(seed + 4) * TAU + age * (rnd(seed + 5) < .5 ? -.5 : .5)), X, tilt);
        double a = alpha * Math.min(1, (1 - l) * 2.5);
        double[] col = {STEAM[0], STEAM[1], STEAM[2], a};
        steamCube(lm, q, sz * 1.15, sz * .78, sz * 1.15, axes, col);
        double ang = rnd(seed + 6) * TAU, c = sz * .62;
        Vec3d off = axes[0].multiply(Math.cos(ang) * sz * .22).add(axes[2].multiply(Math.sin(ang) * sz * .22)).add(axes[1].multiply(sz * (.42 + .25 * l)));
        steamCube(lm, L.add(q, off.x, off.y, off.z), c * 1.1, c * .8, c * 1.1, axes, col);
    }

    private static void steamCube(LandingMesh lm, double[] c, double sx, double sy, double sz, Vec3d[] ax, double[] col) {
        double[] m = {ax[0].x, ax[0].y, ax[0].z, 0, ax[1].x, ax[1].y, ax[1].z, 0, ax[2].x, ax[2].y, ax[2].z, 0, c[0], c[1], c[2], 1};
        lm.box(L.v(-sx / 2, -sy / 2, -sz / 2), L.v(sx / 2, sy / 2, sz / 2), col, m);
    }

    /** The wand's tip: heating before the jet lights, a hot nozzle flame while it burns, a cough of smoke as it cuts. */
    private void nozzle(Scene s, Vec3d tip) {
        BlockFireMesh m = fire;
        double ft = s.ft(), sec = ft / 20, heatK = s.col("f_heat");
        Vec3d dir = vec(s.pt("f_dir"));
        if (heatK > .02 && ft < jet0) {  // heating: the tip glowing up through the bands, sparks shed off it
            cube(m, tip, .1 + .14 * heatK, heat(1 + 3 * heatK), 3, tumble(3, sec, 3));
            sparks(m, tip.add(0, -.1, 0), sec, (int) Math.round(3 + 7 * heatK), .18, .7, 61, .07);
        }
        if (ft >= jet0 && ft < jet1 + 1) {
            double on = smoothstep((ft - jet0) / 1.5) * (ft < jet1 ? 1 : 1 - (ft - jet1));
            flame(m, tip, dir, new Flame(.34 * on, .9 * on, 5).heat(1.35).count(4).sway(.02).speed(1.9).t(sec));
        }
        // The ignition: a hot bloom off the tip and sparks.
        double ia = (ft - ignite.clock) / 20;
        if (ia > 0 && ia < 1.2) {
            burst(m, tip.add(dir.multiply(.5)), ia, new BlockFireKit.Burst(1.3, 18, .48, .42, 41).up(0).heat(1).smoke(false));
            sparkBurst(m, tip.add(dir.multiply(.3)), ia, new Sparks(16, 4, 1, .55, 47).size(.09).g(2).dir(dir, .8));
        }
        // The cut: dark, smoking tip; one last cough of smoke forward and dying sparks.
        double ca = (ft - jet1) / 20;
        if (ca > 0 && ca < 2.5) {
            if (ca < 1) cube(m, tip, .15, ca < .25 ? DEEP : ca < .6 ? EMBER : CHAR, 21, basis(dir, Math.PI / 4));
            for (int i = 0; i < 3; i++) {
                double side = (rnd(400 + i) - .5) * .5, fwd = .2 + .7 * rnd(405 + i);
                smoke(m, tip.add(dir.multiply(fwd)).add(side, .08 * (i % 4), (rnd(415 + i) - .5) * .4), ca - i * .03, .22 + .12 * rnd(410 + i), 420 + i, 1.2, 1.0);
            }
            for (int i = 0; i < 6; i++) {
                double sa = (ca - .02 * i) / .45;
                if (sa <= 0 || sa >= 1) continue;
                Vec3d d = dir.multiply(.8).add(rnd(430 + i) - .5, .7 + .5 * rnd(440 + i), rnd(435 + i) - .5).normalize();
                cube(m, tip.add(d.multiply(.8 * (1 - Math.pow(1 - sa, 2)))), .07 * (1 - sa), heat(3 - 2.5 * sa), 450 + i, AXES);
            }
            for (int i = 0; i < 4; i++) smoke(m, tip.add(0, .1, 0), ca - .7 - i * .35, .14, 460 + i, .75, .8);
        }
    }

    // ------------------------------------------------------------------ the Fire Leap's landing

    // The hot blast at the feet, sparks, and the knee-high wall of fire rolling out to 5 blocks
    // across the snow, its wake rolling to a stop behind the crest. Here the wall stands a moment
    // longer than the spell's before it burns down, tips first, so the ring reads round you.
    private static final double WAVE_RANGE = 5, WAVE_SPEED = .65, COL_GAP = .4, COL_MIN = 14, COL_MAX = 78, WAKE_DT = .07, WAKE_LIFE = .18, TICK = .05;
    private static final double WSTOP = (WAVE_RANGE / WAVE_SPEED - 1) * TICK;
    private static double crest(double a) { return Math.min(WAVE_RANGE, (a / TICK + 1) * WAVE_SPEED); }
    private static double colAngle(int i) { return i * 2.399963 + (rnd(i * 1.3 + 7) - .5) * .08; }
    private static double colCount(double r) { return clamp(TAU * r / COL_GAP, COL_MIN, COL_MAX); }
    private static double dieAt(int i) { return WSTOP + .3 + .55 * rnd(i * 3.9); }
    private static double crestR(double a) { return Math.min(WAVE_RANGE, crest(Math.min(a, WSTOP))) - .21; }
    private static Vec3d[] leaning(double a, double jitter, double lean) { return rotate(yawAxes(a + jitter), new Vec3d(Math.sin(a), 0, -Math.cos(a)), lean); }
    private static Vec3d[] rolled(double a, double angle) { return rotate(yawAxes(a), new Vec3d(Math.sin(a), 0, -Math.cos(a)), angle); }

    private void landing(Scene s) {
        BlockFireMesh m = fire;
        double ft = s.ft(), a = (ft - touch.clock) / 20, sec = ft / 20;
        double[] to = touch.at;
        if (a < 0 || a > 3.4) return;
        burst(m, w(to[0], to[1] + .35, to[2]), a, new BlockFireKit.Burst(1.5, 18, .44, .45, 400).up(.12).heat(1).smoke(true));
        sparkBurst(m, w(to[0], to[1] + .2, to[2]), a, new Sparks(22, 3.5, 3.2, .75, 430));
        double rc = crestR(a), cols = colCount(rc), spawn = smoothstep(a / .05), inner = 1 - smoothstep((rc - 1.3) / .4);
        boolean moving = a < WSTOP;
        for (int i = 0; i < Math.ceil(cols); i++) {
            double ang = colAngle(i);
            Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
            double g = clamp(cols - i) * spawn, burn = 3 * clamp((a - dieAt(i)) / .5), die = burn / 3;
            double kt = g * (1 - smoothstep(burn)), km = g * (1 - smoothstep(burn - 1)), kb = g * (1 - smoothstep(burn - 2));
            if (kb > .01) {
                double ri = rc + (rnd(i * 2.1 + 3) - .5) * .16 * Math.min(1, rc / 2);
                Vec3d base = w(to[0] + dir.x * ri, floor, to[2] + dir.z * ri);
                double hot = lerp(3.5, 4.2, inner) - die * 2.4, jit = (rnd(i * 4.3) - .5) * .7, lean = (moving ? .12 : 0) + (rnd(i * 4.9) - .5) * .16;
                Vec3d[] ax0 = leaning(ang, jit, lean), ax1 = leaning(ang, jit + .5, lean), ax2 = leaning(ang, jit + 1, lean);
                Vec3d up = ax0[1];
                double tall = .9 + .2 * rnd(i * 6.7), wd = (.38 + .1 * rnd(i * 3.7 + 1)) * (.92 + .16 * noise(sec * 6.3 + i * 1.7));
                double bh = wd * .92 * tall * (.92 + .16 * noise(sec * 5.1 + i * 2.9)) * kb, mw = wd * .72 * km;
                double mh = wd * .82 * tall * (.9 + .2 * noise(sec * 6.1 + i * 3.7)) * km;
                double tw = wd * .46 * kt, th = wd * .62 * tall * (.55 + .9 * noise(sec * 7.7 + i * 4.3)) * kt;
                double bTop = bh - .08, mTop = bTop + mh - .1;
                cube(m, base.add(up.multiply(bTop - bh / 2)), wd * kb, bh, wd * .94 * kb, heat(hot - .8), 700 + i, ax0);
                if (km > .01) cube(m, base.add(up.multiply(mTop - mh / 2)), mw, mh, mw * .94, heat(hot - 1.3), 740 + i, ax1);
                if (kt > .01) {
                    Vec3d tipP = base.add(up.multiply(mTop - .07 + th / 2));
                    cube(m, tipP, tw, th, tw * .94, heat(hot - 2.1), 780 + i, ax2);
                    double u = fract(sec / .28 + rnd(i * 5.3)), rs = wd * .4 * Math.pow(1 - u, .9) * kt;
                    if (rs > .02) cube(m, tipP.add(up.multiply(th * .2 + .36 * u)).add(dir.multiply(.06 * u)), rs, heat(hot - 1.9 - u * 1.6), 820 + i,
                            leaning(ang, u * 1.4 + rnd(i * 6.1) * 3, u * .8));
                }
            }
            if (i % 6 == 0) {
                double r = crestR(WSTOP);
                smoke(m, w(to[0] + dir.x * r, floor + .35, to[2] + dir.z * r), a - dieAt(i) - .12, .32, 880 + i, 1.2, 1.0);
            }
            if (i % 2 != 0) continue;
            for (int n = 0; n * WAKE_DT <= WSTOP; n++) {
                double te = (n + rnd(i * 8.9 + n * .37)) * WAKE_DT;
                if (te > WSTOP || te > a || i >= colCount(crestR(te))) continue;
                double age = a - te, l = age / WAKE_LIFE;
                if (l >= 1) continue;
                double r = crestR(te) - .25, sz = (.24 + .08 * rnd(i * 9.7 + n)) * Math.pow(1 - l, 1.2);
                cube(m, w(to[0] + dir.x * (r + .4 * l), floor + .12 + .28 * l, to[2] + dir.z * (r + .4 * l)), sz, heat(3.3 - l * 3.2), 900 + i * 7 + n, rolled(ang, l * 2.5 + n));
            }
        }
    }

    /** Low flames licking round the crouch, embers in the scorched snow, sparks and wisps going up. */
    private static final double[][] SPOTS = {{1.05, .2}, {.95, 1.0}, {1.25, 1.75}, {.9, 2.5}, {1.35, 3.1}, {1.0, 3.75}, {1.5, 4.45}, {.85, 5.2},
            {1.2, 5.85}, {2.1, .7}, {2.3, 2.9}, {2.0, 4.9}};
    /** The glare's camera sits low behind you this way: its side is kept clear of flames. */
    private static final double GLARE = Math.atan2(3.6, 1.2);

    private void lingering(Scene s) {
        double k = s.col("f_flames");
        if (k < .01) return;
        BlockFireMesh m = fire;
        double sec = s.ft() / 20;
        for (int i = 0; i < SPOTS.length; i++) {
            double r = SPOTS[i][0], a = SPOTS[i][1];
            if (Math.abs(Math.atan2(Math.sin(a - GLARE), Math.cos(a - GLARE))) < .75) continue;
            double grow = clamp(k * 1.4 - i * .03), h = (.32 + .22 * rnd(i * 3.1)) * grow * (r > 1.8 ? .75 : 1);
            if (h < .02) continue;
            flame(m, w(arrival[0] + Math.cos(a) * r, floor, arrival[2] + Math.sin(a) * r), new Vec3d(Math.cos(a) * .1, 1, Math.sin(a) * .1),
                    new Flame((.26 + .1 * rnd(i * 2.3)) * grow, h * 1.6, 950 + i * 11).heat(.95).sway(.05).t(sec));
        }
        for (int i = 0; i < 22; i++) {  // embers: glowing coals pressed into the snow, dimming
            double a = rnd(i * 4.1 + 2) * TAU, r = .5 + 2.4 * Math.sqrt(rnd(i * 7.3 + 1)), z = (.07 + .06 * rnd(i * 2.2)) * k;
            cube(m, w(landAt[0] + Math.cos(a) * r, floor + z * .3, landAt[2] + Math.sin(a) * r), z, z * .6, z,
                    heat(1.2 + 1.3 * noise(sec * 1.3 + i) * k), 990 + i, yawAxes(a));
        }
        sparks(m, w(arrival[0], floor + .25, arrival[2]), sec, (int) Math.round(10 * k), 1.7, 2.2, 1100, .08);
        for (int i = 0; i < 5; i++) {
            double per = 1.6, ph = sec / per + rnd(1200 + i), loop = Math.floor(ph), a = rnd(1210 + i + loop * 3.3) * TAU;
            smoke(m, w(arrival[0] + Math.cos(a) * 1.3, floor + .5, arrival[2] + Math.sin(a) * 1.3), (ph - loop) * per, .22 * k, 1220 + i + loop * 7, 1.1, per);
        }
    }

    // ------------------------------------------------------------------ the mark on the snow

    private static final double[] UPN = {0, 1, 0};

    /** A ring on the snow from r0 to r1, its edges wavering. */
    private static void ring(LandingMesh lm, double[] c, double y, double r0, double r1, double[] col) {
        int n = 72;
        for (int i = 0; i < n; i++) {
            double a0 = i / (double) n * TAU, a1 = (i + 1) / (double) n * TAU;
            double j0 = .12 * Math.sin(a0 * 7) + .08 * Math.sin(a0 * 13), j1 = .12 * Math.sin(a1 * 7) + .08 * Math.sin(a1 * 13);
            double[] p00 = ringPoint(c, y, a0, r0 + j0), p10 = ringPoint(c, y, a1, r0 + j1), p11 = ringPoint(c, y, a1, r1 + j1), p01 = ringPoint(c, y, a0, r1 + j0);
            lm.tri(p00, p10, p11, UPN, col);
            lm.tri(p00, p11, p01, UPN, col);
        }
    }
    private static double[] ringPoint(double[] c, double y, double a, double r) { return L.v(c[0] + Math.cos(a) * r, y, c[2] + Math.sin(a) * r); }

    private void scorch(Scene s) {
        LandingMesh lm = s.mesh();
        // Where the jet beat down: the snow melted back to wet grey, darkest in the middle.
        double melt = s.col("f_melt");
        if (melt > .02) {
            LandingMesh.Radius r = a -> 2.4 * melt * (.86 + .1 * Math.sin(a * 5 + 1) + .06 * Math.sin(a * 11));
            lm.fan(meltAt, floor + .006, r, L.v(.7, .72, .77), 48);
            lm.fan(meltAt, floor + .01, a -> r.at(a) * .62, L.v(.5, .5, .53), 48);
        }
        // The Fire Leap's ring: a char line where the wall stood, the snow burnt dark under the blast.
        double a = (s.ft() - touch.clock) / 20;
        if (a > 0) {
            double rc = crestR(a) + .2, burnt = clamp((a - WSTOP - .6) / 1.2);
            if (rc > .6) ring(lm, touch.at, floor + .014, rc - .32, rc + .02, L.v(lerp(.78, .5, burnt), lerp(.78, .47, burnt), lerp(.82, .47, burnt)));
            lm.fan(touch.at, floor + .016, x -> Math.min(1.6, rc) * (.8 + .12 * Math.sin(x * 6 + 2)), L.v(.32, .29, .29), 48);
        }
    }

    // ------------------------------------------------------------------ light

    private static final double[] ORANGE = {1, .52, .16}, GOLD = {1, .78, .35};

    /** A little warm glow where the fire is hottest. tip in the summit frame. */
    private void glows(Scene s, double[] tip) {
        LandingMesh lm = s.mesh();
        double ft = s.ft(), sec = ft / 20;
        if (ft >= jet0 && ft < jet1 + 2) {
            double on = smoothstep((ft - jet0) / 1.5) * (ft < jet1 ? 1 : 1 - (ft - jet1) / 2), f = 1 + .15 * Math.sin(sec * 37) + .1 * Math.sin(sec * 53);
            lm.glow(tip, GOLD, 1.1 * f, .55 * on);
            double[] dir = s.pt("f_dir");
            double hit = dir[1] < -.2 ? (tip[1] - floor) / -dir[1] : 99;
            if (hit < reach + 1.5) lm.glow(L.v(tip[0] + dir[0] * hit, floor + .4, tip[2] + dir[2] * hit), ORANGE, 3.4 * on, .3 * on * clamp((reach + 1.5 - hit) / 3));
        }
        double heatK = s.col("f_heat");
        if (heatK > .05 && ft < jet0) lm.glow(tip, ORANGE, .5 + .5 * heatK, .5 * heatK);
        double a = (ft - touch.clock) / 20;
        if (a > 0 && a < .6) lm.glow(L.add(touch.at, 0, .6, 0), GOLD, 2.4 + 3 * a, .6 * (1 - a / .6));
        double flames = s.col("f_flames");
        if (flames > .02) lm.glow(L.v(arrival[0], floor + .5, arrival[2]), ORANGE, 2.6, .2 * flames * (1 + .2 * Math.sin(sec * 13)));
    }

    // ------------------------------------------------------------------ over the frame

    /** Speed lines in the fall, a warm edge while the jet burns, the slow landing's vignette, a soft warm flash on touchdown. */
    @Override public void hud(DrawContext context, Scene s, int w, int h, double shown) {
        double speed = s.col("f_speed") * shown;
        LandingHud.speedLines(context, w, h, s.t(), speed, .38 * speed, 110);
        double edge = (.16 * s.col("f_jetfx") + .18 * s.col("f_bt")) * shown;
        LandingHud.vignette(context, w, h, 40 / 255.0, 10 / 255.0, 0, edge, .35);
        double impact = s.col("f_impact");
        LandingHud.tint(context, w, h, 1, 190 / 255.0, 120 / 255.0, .1 * impact * impact * shown);
    }
}
