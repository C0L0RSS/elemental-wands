package com.anton.elementalwands.client.blockfire;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.GuardianWaveSurface;
import com.anton.elementalwands.util.FireLeapRules;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Fire Leap (art/fire/blockfire/scenes/leap.js): a ring of fire thrown out at take-off, a comet tail along the
 * committed arc, and on landing a knee-high wall that rolls out to the wave range over the same ground and cover
 * the server's damage uses, then burns down tips first.
 */
final class LeapFx {
    private static final double TICK = .05, WAVE_RANGE = FireLeapRules.WAVE_RANGE, WAVE_SPEED = FireLeapRules.WAVE_SPEED;
    /** The crest stops at the wave range on this age, in seconds. */
    private static final double STOP = (WAVE_RANGE / WAVE_SPEED - 1) * TICK;
    private static final double COL_GAP = .4, COL_MIN = 14, COL_MAX = 78, WAKE_DT = .07, WAKE_LIFE = .18;
    private static final double SHED_RATE = 110, SHED_LIFE = .16;
    private static final Map<Integer, Flight> FLIGHTS = new HashMap<>();

    private LeapFx() {}

    /** Axes facing along ground direction a that roll outward by angle, like a cube tumbling across the floor. */
    private static Vec3d[] rolled(double a, double angle) {
        return rotate(yawAxes(a), new Vec3d(Math.sin(a), 0, -Math.cos(a)), angle);
    }
    /** Upright axes facing outward at angle a, turned by jitter and leaning outward by lean radians. */
    private static Vec3d[] leaning(double a, double jitter, double lean) {
        return rotate(yawAxes(a + jitter), new Vec3d(Math.sin(a), 0, -Math.cos(a)), lean);
    }

    /** Take-off and flight, from the leap packet every viewer receives. */
    static final class Flight implements BlockFireEffects.Fx {
        private final ClientWorld world;
        private final int entity;
        private final Vec3d from, to;
        private final double start, flight, seed;
        private double ended = Double.NaN;

        Flight(ClientWorld world, int entity, Vec3d from, Vec3d to, double now) {
            this.world = world; this.entity = entity; this.from = from; this.to = to; this.start = now;
            flight = FireLeapRules.duration(from, to) * TICK;
            seed = Math.floorMod(entity, 53) * 1000;
            Flight old = FLIGHTS.put(entity, this);
            if (old != null && Double.isNaN(old.ended)) old.ended = now;
        }
        static void end(int entity, double now) {
            Flight f = FLIGHTS.get(entity);
            if (f != null && Double.isNaN(f.ended)) f.ended = now;
        }
        private Vec3d pos(double p) { return FireLeapRules.position(from, to, clamp(p)); }
        private Vec3d dir(double p) { return pos(p + .01).subtract(pos(p - .01)).normalize(); }

        @Override public boolean draw(BlockFireEffects.Frame f) {
            double a = f.now() - start, last = Double.isNaN(ended) ? flight : Math.min(flight, ended - start);
            if (a > Math.max(1.6, last + .4)) { FLIGHTS.remove(entity, this); return false; }
            if (!BlockFireEffects.near(f, from.lerp(to, .5), 96)) return true;
            takeoff(f.m(), a);
            flight(f, a, last);
            return true;
        }

        /** A ring of fire thrown out across the ground from the caster's feet. */
        private void takeoff(BlockFireMesh m, double age) {
            if (age < 0 || age > 1.6) return;
            Vec3d c = from;
            // A low hot flash at the feet that the ring pieces start hidden inside.
            if (age < .18) {
                double k = age / .18, s = Math.pow(Math.sin(k * Math.PI), .6);
                cube(m, c.add(0, .28, 0), 1.5 * s, .55 * s, 1.5 * s, heat(4 - k * 1.2), 501, yawAxes(.4 + age * 3));
                cube(m, c.add(0, .22, 0), 1.2 * s, .42 * s, 1.2 * s, heat(3 - k), 502, yawAxes(1.2 - age * 3));
            }
            // Short tongues flare around the feet and shrink to nothing as the caster leaves.
            double fl = 1 - smoothstep(age / .4);
            if (fl > 0) for (int j = 0; j < 7; j++) {
                double a = j / 7.0 * TAU + .3, r = .55 + .12 * rnd(510 + j);
                flame(m, c.add(Math.cos(a) * r, 0, Math.sin(a) * r), new Vec3d(Math.cos(a) * .7, 1, Math.sin(a) * .7),
                        new Flame(.5 * fl, 1.05 * fl * (.8 + .4 * rnd(511 + j)), 512 + j * 7).heat(1.15).sway(.04));
            }
            // The ring: chunky pieces roll outward along the floor, lift a little, cool and shrink away.
            int n = 22;
            for (int i = 0; i < n; i++) {
                double li = .55 * (.8 + .4 * rnd(520 + i * 1.7)), l = age / li;
                if (l <= 0 || l >= 1) continue;
                double a = (i + .45 * rnd(521 + i * 2.3)) / n * TAU, R = 2.4 * (.75 + .25 * rnd(522 + i * 3.1)), r = .3 + R * (1 - Math.pow(1 - l, 2.4));
                double s = .46 * (.8 + .4 * rnd(523 + i)) * Math.min(1, l * 6) * Math.pow(1 - l, 1.15);
                cube(m, c.add(Math.cos(a) * r, .16 + s * .35 + .5 * l * l, Math.sin(a) * r), s, heat(4.3 - l * 4), 524 + i, rolled(a, r * 2.4));
            }
            sparkBurst(m, c.add(0, .15, 0), age, new Sparks(16, 2.6, 3.2, .75, 540));
            for (int i = 0; i < 5; i++) {
                double a = i / 5.0 * TAU + .7;
                smoke(m, c.add(Math.cos(a) * 1.5, .3, Math.sin(a) * 1.5), age - .22 - rnd(550 + i) * .12, .5, 551 + i, 1.3, 1.1);
            }
        }

        /** Pieces peel off the body where it was, cool and shrink away, so the tail stays 3 to 4 blocks long. */
        private void flight(BlockFireEffects.Frame f, double a, double last) {
            BlockFireMesh m = f.m();
            int first = (int) Math.ceil(Math.max(0, a - SHED_LIFE) * SHED_RATE), lastShed = (int) Math.floor(Math.min(a, last) * SHED_RATE);
            for (int i = first; i <= lastShed; i++) {
                double te = i / SHED_RATE, age = a - te, l = age / SHED_LIFE, k = seed + i;
                if (l < 0 || l >= 1) continue;
                double p = te / flight;
                Vec3d d = dir(p), hip = pos(p).add(0, .75, 0);
                Vec3d side = d.crossProduct(Math.abs(d.y) > .99 ? X : UP).normalize(), up = side.crossProduct(d);
                double j1 = (rnd(k * 1.31) - .5) * .45, j2 = (rnd(k * 2.17) - .5) * .45;
                Vec3d q = hip.add(d.multiply(-.3)).add(side.multiply(j1)).add(up.multiply(j2)).add(0, .8 * age, 0);
                double s = (.3 + .16 * rnd(k * 3.7)) * Math.min(1, l * 10 + .4) * Math.pow(1 - l, 1.15);
                cube(m, q, s, heat(3.6 - l * 3.2 + rnd(k * 4.1) * .5), k, tumble(k, age, 4));
                if (i % 3 == 0 && s > .12) cube(m, q.add(side.multiply(j1 > 0 ? -s * .45 : s * .45)), s * .55, heat(3.1 - l * 3), k + .5, tumble(k + .5, age, 4));
            }
            if (a > last + .15) return;
            // The attached tongue streams back from the hips; it grows in a blink and shrinks to nothing on touchdown.
            double p = clamp(a / flight), k = Math.min(1, a / .07) * (1 - smoothstep((a - last + .04) / .14));
            if (k > 0) {
                Vec3d d = dir(p);
                Vec3d feet = world.getEntityById(entity) instanceof Entity body && !body.isRemoved() ? body.getLerpedPos(f.delta()) : pos(p);
                Vec3d hip = feet.add(0, .8, 0);
                flame(m, hip.add(d.multiply(.15)), d.multiply(-1), new Flame(.66 * k, 1.7 * k, 71).heat(1.2).sway(.05).speed(1.7).count(6));
                flame(m, hip.add(0, -.45, 0), d.multiply(-1).add(0, -.3, 0), new Flame(.42 * k, .9 * k, 83).heat(1).sway(.05).speed(1.8));
            }
            // Two ember flecks a tick follow the body.
            if (m.embers && a < last) for (int i = 0; i < 14; i++) {
                double te = a - (i + fract(a * 40)) / 40;
                if (te < 0) continue;
                double age = a - te, slot = Math.floor(te * 40) + seed;
                Vec3d q = pos(te / flight).add((rnd(i * 7.7 + slot) - .5) * .5, .4 + .25 * rnd(i * 3.3 + slot) + age * .6 - 2 * age * age, (rnd(i * 5.1 + slot) - .5) * .5);
                cube(m, q, .08 * (1 - age / .35), heat(3.4 - age * 6), 600 + i, tumble(600 + i, f.t(), 6));
            }
        }
    }

    /** The landing: a hot blast over the double-damage zone and the knee-high wall rolling out to the wave range. */
    static final class Landing implements BlockFireEffects.Fx {
        private final ClientWorld world;
        private final int caster;
        private final Vec3d at;
        private final double start;
        /** Footing per column and radius bucket; the crest keeps its radius once it stops, so this settles fast. */
        private final Map<Long, Vec3d> ground = new HashMap<>();
        private static final Vec3d NONE = new Vec3d(Double.NaN, 0, 0);

        Landing(ClientWorld world, int caster, Vec3d at, double now) { this.world = world; this.caster = caster; this.at = at; this.start = now; }

        private static double crest(double a) { return Math.min(WAVE_RANGE, (a / TICK + 1) * WAVE_SPEED); }
        private static double crestR(double a) { return Math.min(WAVE_RANGE, crest(Math.min(a, STOP))) - .21; }
        private static double colCount(double r) { return clamp(TAU * r / COL_GAP, COL_MIN, COL_MAX); }
        private static double colAngle(int i) { return i * 2.399963 + (rnd(i * 1.3 + 7) - .5) * .08; }
        private static double dieAt(int i) { return STOP + .05 + .32 * rnd(i * 3.9); }

        /** The surface under (dir · r), with the same footing and cover rules as the wave's damage; null if blocked. */
        private Vec3d surface(int column, double r, Vec3d dir) {
            long key = ((long) column << 20) | Math.round(r * 50);
            Vec3d hit = ground.get(key);
            if (hit == null) {
                Entity owner = world.getEntityById(caster);
                if (owner == null) owner = MinecraftClient.getInstance().player;
                Vec3d s = GuardianWaveSurface.ground(world, owner, at, at.x + dir.x * r, at.z + dir.z * r);
                hit = s != null && GuardianWaveSurface.visible(world, owner, at, s) ? s : NONE;
                ground.put(key, hit);
            }
            return hit == NONE ? null : hit;
        }

        @Override public boolean draw(BlockFireEffects.Frame f) {
            double a = f.now() - start;
            if (a > 2) return false;
            if (!BlockFireEffects.near(f, at, 96)) return true;
            BlockFireMesh m = f.m();
            double t = f.t();
            burst(m, at.add(0, .35, 0), a, new Burst(1.5, 16, .42, .42, 400).up(.12).smoke(m.embers));
            sparkBurst(m, at.add(0, .2, 0), a, new Sparks(18, 3.5, 3, .7, 430));
            double rc = crestR(a), cols = colCount(rc), spawn = smoothstep(a / .05);
            boolean moving = a < STOP;
            // Inside 1.5 blocks the wave deals double damage: the wall burns hotter there.
            double inner = 1 - smoothstep((rc - 1.3) / .4);
            for (int i = 0; i < Math.ceil(cols); i++) {
                double ang = colAngle(i);
                Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                // A short three-tier flame (hot wide base, narrower middle, a tip that flickers in length) together the
                // 0.6 hit height, plus one loose piece rising off the tip. Dying columns burn down from the top.
                double g = clamp(cols - i) * spawn, burn = 3 * clamp((a - dieAt(i)) / .5), die = burn / 3;
                double kt = g * (1 - smoothstep(burn)), km = g * (1 - smoothstep(burn - 1)), kb = g * (1 - smoothstep(burn - 2));
                if (kb > .01) {
                    double ri = rc + (rnd(i * 2.1 + 3) - .5) * .16 * Math.min(1, rc / 2);
                    Vec3d s = surface(i, ri, dir);
                    if (s != null) column(m, s, i, ang, dir, t, kb, km, kt, die, inner, moving);
                }
                // Smoke curls off a few columns as they gutter out.
                if (m.embers && i % 6 == 0) {
                    Vec3d s = surface(i, crestR(STOP), dir);
                    if (s != null) smoke(m, s.add(0, .35, 0), a - dieAt(i) - .12, .3, 880 + i, 1.1, .9);
                }
                // The wake: pieces the crest leaves on the ground behind it, rolling to a stop as they cool and shrink.
                if (i % 2 == 1) continue;
                for (int n = 0; n * WAKE_DT <= STOP; n++) {
                    double te = (n + rnd(i * 8.9 + n * .37)) * WAKE_DT;
                    if (te > STOP || te > a || i >= colCount(crestR(te))) continue;
                    double age = a - te, l = age / WAKE_LIFE;
                    if (l >= 1) continue;
                    Vec3d s = surface(i, crestR(te) - .25, dir);
                    if (s == null) continue;
                    double sz = (.24 + .08 * rnd(i * 9.7 + n)) * Math.pow(1 - l, 1.2);
                    cube(m, s.add(dir.x * .4 * l, .12 + .28 * l, dir.z * .4 * l), sz, heat(3.3 - l * 3.2), 900 + i * 7 + n, rolled(ang, l * 2.5 + n));
                }
            }
            return true;
        }

        private static void column(BlockFireMesh m, Vec3d s, int i, double ang, Vec3d dir, double t,
                double kb, double km, double kt, double die, double inner, boolean moving) {
            double hot = lerp(3.5, 4.2, inner) - die * 2.4, jit = (rnd(i * 4.3) - .5) * .7, lean = (moving ? .12 : 0) + (rnd(i * 4.9) - .5) * .16;
            Vec3d up = leaning(ang, jit, lean)[1];
            double tall = .9 + .2 * rnd(i * 6.7), w = (.38 + .1 * rnd(i * 3.7 + 1)) * (.92 + .16 * noise(t * 6.3 + i * 1.7));
            double bh = w * .92 * tall * (.92 + .16 * noise(t * 5.1 + i * 2.9)) * kb, mw = w * .72 * km, mh = w * .82 * tall * (.9 + .2 * noise(t * 6.1 + i * 3.7)) * km;
            double tw = w * .46 * kt, th = w * .62 * tall * (.55 + .9 * noise(t * 7.7 + i * 4.3)) * kt;
            // The base is sunk 0.08 into the floor; the solid body tops out near the 0.6 hit height.
            double bTop = bh - .08, mTop = bTop + mh - .1;
            cube(m, s.add(up.multiply(bTop - bh / 2)), w * kb, bh, w * .94 * kb, heat(hot - .8), 700 + i, leaning(ang, jit, lean));
            if (km > .01) cube(m, s.add(up.multiply(mTop - mh / 2)), mw, mh, mw * .94, heat(hot - 1.3), 740 + i, leaning(ang, jit + .5, lean));
            if (kt > .01) {
                Vec3d tip = s.add(up.multiply(mTop - .07 + th / 2));
                cube(m, tip, tw, th, tw * .94, heat(hot - 2.1), 780 + i, leaning(ang, jit + 1, lean));
                double u = fract(t / .28 + rnd(i * 5.3)), rs = w * .4 * Math.pow(1 - u, .9) * kt;
                if (rs > .02) cube(m, tip.add(up.multiply(th * .2 + .36 * u)).add(dir.multiply(.06 * u)), rs, heat(hot - 1.9 - u * 1.6), 820 + i,
                        leaning(ang, u * 1.4 + rnd(i * 6.1) * 3, u * .8));
            }
        }
    }

    static void ended(int entity, double now) { Flight.end(entity, now); }
    static void clear() { FLIGHTS.clear(); }
}
