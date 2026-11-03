package com.anton.elementalwands.client.blockfire;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.util.FireBuildRules;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Flamethrower (art/fire/blockfire/scenes/flamethrower.js): a cone of tumbling flame cubes that leave the wand
 * small and yellow, swell and redden toward the 6-block reach, and splash across any block in the way. Each cube
 * keeps the aim it was fired with, so sweeping the wand bends the stream. The server sends heat while spraying.
 */
final class FlameFx {
    private static final double RANGE = FireBuildRules.RANGE, HEAT_PER_S = FireBuildRules.HEAT_PER_TICK * 20;
    private static final double COOL_PER_S = FireBuildRules.COOL_PER_TICK * 20, UNLOCK = FireBuildRules.UNLOCK_HEAT;
    private static final double WAKE = 3, SPREAD = .3, D0 = .15, EASE = 2.2, RATE0 = 80, RATE1 = 150;
    /** Puffs per second grow with heat: RATE0 cold, RATE1 at overheat, so the count is a quadratic in spray time. */
    private static final double ACC = (RATE1 - RATE0) / 100 * HEAT_PER_S / 2;
    private static final double SPUTTER = .45, LEASE = .16, MAX_AGE = .66 + .45 + .95;
    private static final Map<Integer, Stream> STREAMS = new HashMap<>();

    private FlameFx() {}

    static void clear() { STREAMS.clear(); }
    static void tick(ClientWorld world, double now) { STREAMS.values().removeIf(s -> now > s.stop() + MAX_AGE); }

    /** A FLAME event: the caster is spraying with this heat after sprayTicks of this spray. */
    static void spray(ClientWorld world, int entity, double heat, int sprayTicks, double now) {
        double start = now - sprayTicks / 20.0;
        Stream s = STREAMS.get(entity);
        if (s == null || now > s.last + LEASE || Math.abs(s.start - start) > .2) {
            // A late packet (lag, a server hiccup) hands over to a new stream. The old one ends here and keeps the
            // puffs it already emitted; the new one draws only puffs born after that, so nothing is drawn twice.
            double from = start;
            if (s != null) { s.cut = Math.min(s.cut, now); from = s.stop(); }
            s = new Stream(world, entity, start, Math.max(0, heat - HEAT_PER_S * sprayTicks / 20.0), from);
            STREAMS.put(entity, s);
            BlockFireEffects.add(s);
        }
        s.last = now;
    }

    static void overheat(ClientWorld world, int entity, double now) {
        Stream s = STREAMS.get(entity);
        if (s != null) s.overheat = now;
        BlockFireEffects.add(new Overheat(world, entity, now));
    }

    private record Aim(double time, Vec3d eye, Vec3d look, Vec3d tip) {}
    private record Hit(double t, Vec3d n) {}

    private static final class Stream implements BlockFireEffects.Fx {
        final ClientWorld world;
        final int entity;
        /** from: the earliest birth time this stream draws; puffs before it belong to the stream it replaced. */
        final double start, heat0, from;
        double last, overheat = Double.NaN, cut = Double.POSITIVE_INFINITY;
        final ArrayDeque<Aim> aims = new ArrayDeque<>();
        final Map<Long, Hit> hits = new HashMap<>();
        Stream(ClientWorld world, int entity, double start, double heat0, double from) {
            this.world = world; this.entity = entity; this.start = start; this.heat0 = heat0; this.from = from;
        }

        /** When this spray stops emitting: overheat, handover to a newer stream, or the server's last word plus its lease. */
        double stop() { return Math.min(cut, Double.isNaN(overheat) ? last + LEASE : Math.min(overheat, last + LEASE)); }
        /** Predicted overheat from the heat at the start of the spray. */
        double full() { return start + (100 - heat0) / HEAT_PER_S; }
        double rate0() { return RATE0 + (RATE1 - RATE0) * heat0 / 100; }
        double emitted(double tau) { return rate0() * tau + ACC * tau * tau; }
        double emitAt(long k) { double a = rate0(); return start + (Math.sqrt(a * a + 4 * ACC * k) - a) / (2 * ACC); }

        /** Close to overheat the nozzle coughs: emission drops out in short gaps that widen until it stops. */
        double gate(double e) {
            double full = full();
            if (e < full - SPUTTER) return 1;
            if (e >= full) return 0;
            double q = (e - (full - SPUTTER)) / SPUTTER;
            return smoothstep((noise(e * 10 + 3.7) - (.18 + .55 * q)) / .12);
        }

        Aim aimAt(double time) {
            Aim best = aims.peekFirst();
            for (Aim a : aims) { if (a.time > time) break; best = a; }
            return best;
        }

        @Override public boolean draw(BlockFireEffects.Frame f) {
            double now = f.now(), stop = stop();
            if (now > stop + MAX_AGE) return false;
            if (world.getEntityById(entity) instanceof LivingEntity caster && !caster.isRemoved() && now <= stop + .2) {
                // One aim per moment: the clock stands still while the game is paused and can step back a tick.
                while (!aims.isEmpty() && aims.peekLast().time >= now) aims.removeLast();
                Vec3d eye = caster.getCameraPosVec(f.delta());
                aims.addLast(new Aim(now, eye, caster.getRotationVec(f.delta()).normalize(), BlockFireEffects.tip(caster, f.delta())));
                while (aims.size() > 2 && aims.peekFirst().time < now - MAX_AGE - .2) aims.removeFirst();
            }
            if (aims.isEmpty()) return true;
            Aim current = aims.peekLast();
            if (!BlockFireEffects.near(f, current.eye, 96)) return true;
            var client = MinecraftClient.getInstance();
            boolean own = f.m().lens && client.getCameraEntity() != null && client.getCameraEntity().getId() == entity;
            nozzle(f, current, own);
            double end = Math.min(now, stop) - start;
            long k0 = (long) Math.max(0, Math.ceil(emitted(Math.clamp(Math.max(now - MAX_AGE, from) - start, 0, Math.max(0, end)))));
            long k1 = (long) Math.floor(emitted(Math.max(0, end)) + 1e-9);
            hits.keySet().removeIf(k -> k < k0);
            for (long k = k0; k <= k1; k++) puff(f, k, own ? current : null);
            return true;
        }

        /** The wand tip: a short forward flame while spraying that coughs near overheat and dies when it stops. */
        private void nozzle(BlockFireEffects.Frame f, Aim a, boolean own) {
            double t = f.now(), stop = stop();
            if (t >= stop + .15) return;
            double h = clamp((heat0 + HEAT_PER_S * (t - start)) / 100);
            double on = smoothstep((t - start) / .08) * (t < stop ? 1 : 1 - (t - stop) / .15), g = .3 + .7 * gate(t);
            double fp = own ? .65 : 1;
            flame(f.m(), a.tip, a.look, new Flame((.2 + .07 * h) * on * fp, (.5 + .25 * h) * on * (t < stop ? g : 1), 5)
                    .heat(1.1 + .3 * h).count(4).sway(.02).speed(1.8).t(t));
        }

        /**
         * First person only: your own stream thins out around the crosshair so you can aim through it.
         * Puffs within about 0.25 rad of the sight line shrink to a third; everything off-axis keeps its size.
         */
        private static double sight(Vec3d p, Aim view) {
            if (view == null) return 1;
            Vec3d v = p.subtract(view.eye);
            double along = v.dotProduct(view.look);
            if (along < .3) return .7;
            double ang = v.subtract(view.look.multiply(along)).length() / along;
            return (.3 + .7 * smoothstep((ang - .05) / .22)) * (.7 + .3 * smoothstep((along - .9) / 1.6));
        }

        /** One puff, born at emitAt(k): flies, grows and cools, then splashes on a block or turns to smoke. */
        private void puff(BlockFireEffects.Frame f, long k, Aim view) {
            BlockFireMesh m = f.m();
            double e = emitAt(k), age = f.now() - e;
            if (age < 0 || age > MAX_AGE) return;
            double sd = k * 7.31 + 11;
            if (rnd(sd + 4.4) > gate(e)) return;
            Aim aim = aimAt(e);
            Vec3d look = aim.look, right = look.crossProduct(Math.abs(look.y) > .99 ? X : UP).normalize(), vup = right.crossProduct(look);
            Vec3d tipOff = aim.tip.subtract(aim.eye);
            // Heat at birth: hotter puffs leave the tip whiter and stay yellow further out.
            double hk = clamp((heat0 + HEAT_PER_S * (e - start)) / 100), top = 3.25 + .9 * hk + .5 * (rnd(sd + .05) - .5), grow = .94 + .18 * hk;
            double a = rnd(sd + .1) * TAU, r = Math.sqrt(rnd(sd + .2)) * .95;
            Vec3d radial = right.multiply(Math.cos(a)).add(vup.multiply(Math.sin(a)));
            Vec3d dir = look.add(radial.multiply(r * SPREAD)).normalize(), o = aim.eye.add(radial.multiply(r * .06));
            double life = .5 + .16 * rnd(sd + .3), reach = RANGE * (.92 + .1 * rnd(sd + .4)), j = .8 + .4 * rnd(sd + .5);
            Hit hit = hits.computeIfAbsent(k, key -> ray(o, dir));
            double stop = Math.max(D0 + .05, hit.t - .2);
            boolean blocked = stop < reach;
            double uHit = blocked ? 1 - Math.pow(1 - (stop - D0) / (reach - D0), 1 / EASE) : 1;
            double u = age / life;
            if (u < Math.min(uHit, 1)) {
                // In flight: a little stretched along its path while fast, tumbling more as it slows.
                double s = sAt(u, reach);
                Vec3d p = path(o, dir, tipOff, s), along = path(o, dir, tipOff, s + .05).subtract(p).normalize();
                double speed = Math.pow(1 - u, EASE - 1), w = width(s, j, grow) * sight(p, view);
                if (!blocked) w *= 1 - smoothstep((u - .7) / .3);
                Vec3d kx = new Vec3d(rnd(sd + .8) - .5, rnd(sd + .9) - .5, rnd(sd + 1) - .5).normalize();
                double th = age * 5 * smoothstep((u - .08) / .45);
                Vec3d[] ax = rotate(basis(along, rnd(sd + .6) * TAU + age * (rnd(sd + .7) < .5 ? -3 : 3)), kx, th);
                double hb = band(s, top);
                cube(m, p, w, w * (1 + .8 * speed), w, heat(hb), sd, ax);
                if (w > .2) for (int i = 0; i < 2; i++) {
                    double side = rnd(sd + i * 1.9 + 5) < .5 ? -1 : 1;
                    cube(m, p.add(ax[i * 2].multiply(side * w * .5)), w * .55, heat(hb - .8), sd + i + 1, ax);
                }
                if (m.embers && rnd(sd + 3) < .14) {
                    // A spark flicks out of the side of the stream, shrinking as it goes.
                    double u0 = .15 + .35 * rnd(sd + 3.1), sa = (age - u0 * life) / .35;
                    if (sa > 0 && sa < 1 && u0 < uHit) {
                        Vec3d from = path(o, dir, tipOff, sAt(u0, reach)), drift = radial.multiply(.9).add(dir.multiply(1.1)).add(0, .6, 0);
                        cube(m, from.add(drift.multiply(1.3 * (1 - Math.pow(1 - sa, 2)))), .075 * (1 - sa), heat(3.6 - 2 * sa), sd + 7, ax);
                    }
                }
            }
            if (blocked) {
                // Splash: the puff flattens against the block and spreads across it, climbing walls, then burns out.
                double ageH = uHit * life, sDur = .3 + .15 * rnd(sd + 1.1), v = (age - ageH) / sDur;
                if (v < 0) return;
                Vec3d n = hit.n, surf = o.add(dir.multiply(hit.t));
                boolean wall = Math.abs(n.y) < .5;
                Vec3d tan = dir.subtract(n.multiply(dir.dotProduct(n)));
                Vec3d jit = new Vec3d(rnd(sd + 1.2) - .5, rnd(sd + 1.3) - .5, rnd(sd + 1.4) - .5);
                jit = jit.subtract(n.multiply(jit.dotProduct(n)));
                if (wall) tan = tan.add(0, .55, 0);
                tan = tan.add(jit.multiply(.9)).normalize();
                double spread = (.4 + .7 * rnd(sd + 1.5)) * (.6 + .6 * (1 - uHit));
                double wH = Math.max(width(stop, j, grow), .12), hbH = band(stop, top);
                if (v < 1) {
                    double ws0 = wH * (1 + .3 * Math.sin(Math.min(v * 2.5, 1) * Math.PI / 2)) * Math.pow(1 - v, .75);
                    Vec3d c = surf.add(n.multiply(.12 + ws0 * .3)).add(tan.multiply(spread * (1 - Math.pow(1 - v, 2))));
                    double ws = ws0 * sight(c, view);
                    Vec3d[] ax = basis(n, rnd(sd + 1.6) * TAU + age * 2.5);
                    cube(m, c, ws * 1.15, ws * .5, ws * 1.15, heat(hbH - .2 - 1.6 * v), sd, ax);
                    if (ws > .18) cube(m, c.add(tan.multiply(ws * .6)).add(n.multiply(ws * .2)), ws * .5, heat(hbH - .9 - 1.6 * v), sd + 5, ax);
                }
                if (rnd(sd + 2.1) < .2) {
                    double ws = wH * (1 + .3) * Math.pow(.15, .75);
                    Vec3d q = surf.add(n.multiply(.12 + ws * .3)).add(tan.multiply(spread * (1 - .15 * .15))).add(n.multiply(.2));
                    smoke(m, q, age - ageH - sDur * .85, .3 * j * grow * sight(q, view), sd + 300, 1.1, .75);
                }
                return;
            }
            // Free puffs that reach the end of the range turn to smoke: it starts inside the shrinking puff and rises.
            if (rnd(sd + 2) < .2) {
                double sa = age - .76 * life, dur = .65 + .15 * rnd(sd + 2.2);
                if (sa > 0 && sa < dur) {
                    Vec3d p = path(o, dir, tipOff, sAt(.76, reach)).add(dir.multiply(.35 * smoothstep(sa / dur)));
                    smoke(m, p, sa, .36 * j * grow * sight(p, view), sd + 200, 1, dur);
                }
            }
        }

        private Hit ray(Vec3d o, Vec3d dir) {
            var hit = world.raycast(new RaycastContext(o, o.add(dir.multiply(RANGE * 1.1)), RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
            if (hit.getType() == HitResult.Type.MISS) return new Hit(Double.POSITIVE_INFINITY, UP);
            return new Hit(hit.getPos().distanceTo(o), Vec3d.of(hit.getSide().getVector()));
        }
    }

    /** The visual path: a straight eye-line ray, pulled onto the wand tip near the caster (SpellCastVisuals.Wake). */
    private static Vec3d path(Vec3d o, Vec3d dir, Vec3d tipOff, double s) {
        return o.add(dir.multiply(s)).add(tipOff.multiply(1 - smoothstep(s / WAKE)));
    }
    private static double sAt(double u, double reach) { return D0 + (reach - D0) * (1 - Math.pow(1 - u, EASE)); }
    private static double fOf(double s) { return clamp((s - D0) / (RANGE - D0)); }
    /** A tight jet of small cubes at the nozzle that blooms toward the reach. */
    private static double width(double s, double j, double grow) {
        return j * grow * (.075 + .48 * Math.pow(fOf(s), 1.15)) * smoothstep((s - D0) / .4);
    }
    /** Yellow (or white-hot) at the tip, orange through the middle, red at the reach; never the ember band in flight. */
    private static double band(double s, double top) { return Math.max(1, top - 2.7 * fOf(s)); }

    /**
     * Overheat: the wand coughs a last puff of smoke with a few dying sparks, the tip goes dark and wisps while it
     * cools, and a short flicker of flame says it is ready again.
     */
    private record Overheat(ClientWorld world, int entity, double start) implements BlockFireEffects.Fx {
        @Override public boolean draw(BlockFireEffects.Frame f) {
            double k = f.now() - start, lock = (100 - UNLOCK) / COOL_PER_S;
            if (k > lock + .8) return false;
            if (!(world.getEntityById(entity) instanceof LivingEntity caster) || caster.isRemoved()) return k < lock + .8;
            Vec3d tip = BlockFireEffects.tip(caster, f.delta()), look = caster.getRotationVec(f.delta()).normalize();
            Vec3d right = look.crossProduct(Math.abs(look.y) > .99 ? X : UP).normalize();
            BlockFireMesh m = f.m();
            var client = MinecraftClient.getInstance();
            boolean own = m.lens && client.getCameraEntity() == caster;
            // The cooling tip shows to others; beside your own held wand it would float loose of the model.
            if (k < lock && !own) cube(m, tip, .155, k < .25 ? DEEP : k < .6 ? EMBER : CHAR, 21, basis(look, Math.PI / 4));
            // In first person the cough is smaller so it doesn't hide the target for that second.
            double fs = own ? .55 : 1;
            for (int i = 0; i < 9; i++) {
                double side = (rnd(400 + i) - .5) * .45, fwd = .25 + .6 * rnd(405 + i);
                smoke(m, tip.add(look.multiply(fwd)).add(right.multiply(side)).add(0, .08 * (i % 4), 0), k - i * .03, (.3 + .22 * rnd(410 + i)) * fs, 420 + i, 1.3, 1.1);
            }
            if (m.embers) for (int i = 0; i < 6; i++) {
                double sa = (k - .02 * i) / .45;
                if (sa <= 0 || sa >= 1) continue;
                Vec3d d = look.multiply(.8).add(right.multiply(rnd(430 + i) - .5)).add(0, .7 + .5 * rnd(440 + i), 0).normalize();
                cube(m, tip.add(d.multiply(.8 * (1 - Math.pow(1 - sa, 2)))), .07 * (1 - sa), heat(3 - 2.5 * sa), 450 + i, AXES);
            }
            // Locked while cooling: thin wisps rise from the tip.
            for (int i = 0; i < 5; i++) {
                double age = k - (.8 + i * .4);
                if (age > 0 && k < lock + .6) smoke(m, tip.add(0, .1, 0), age, .14, 460 + i, .75, .8);
            }
            if (k >= lock) {
                burst(m, tip.add(look.multiply(.08)), k - lock, new Burst(.3, 5, .08, .3, 77).smoke(false));
                // Ready again: a short flicker of flame at the tip that grows and dies back.
                double q = (k - lock) / .6;
                if (q > 0 && q < 1) {
                    double s = Math.sin(q * Math.PI);
                    flame(m, tip, new Vec3d(look.x * .4, 1, look.z * .4), new Flame(.13 * s, .24 * s, 31).count(3).sway(.02).t(f.now()));
                }
            }
            return true;
        }
    }
}
