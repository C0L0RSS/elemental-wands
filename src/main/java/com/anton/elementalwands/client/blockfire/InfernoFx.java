package com.anton.elementalwands.client.blockfire;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.InfernoWaveEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Inferno Wave (art/fire/blockfire/scenes/inferno.js): a rolling fireball with swept-back tongues that
 * sheds cooling pieces along its path. It follows the InfernoWaveEntity; the cast, each hit and the
 * break where it stops come from the server as one-off bursts.
 */
final class InfernoFx implements BlockFireEffects.Fx {
    private static final double SPEED = 30, GROW = .1, WAKE_BLEND = 3, HEAD_LEAD = .2, TAIL_START = 1.8, SHED_DT = .0105;
    // Swept-back tongues: angle around the flight axis, root offset, outward splay, length, width, heat.
    private static final double[][] TONGUES = {{0, 0, 0, 2.7, .6, 1.05},
            {.15, .23, .04, 2, .44, .95}, {1.75, .26, .06, 1.6, .42, .9}, {3.3, .24, .05, 1.25, .4, .85}, {4.75, .26, .06, 1.7, .42, .9}};

    private final InfernoWaveEntity wave;
    private final double start, seed;
    private final Vec3d origin, dir, tipOffset;
    private double end = Double.NaN, travelled;

    InfernoFx(InfernoWaveEntity wave, double now) {
        this.wave = wave;
        Vec3d velocity = wave.getVelocity();
        dir = velocity.lengthSquared() < 1e-8 ? new Vec3d(0, 0, 1) : velocity.normalize();
        start = now - wave.age / 20.0;
        origin = wave.getEntityPos().subtract(dir.multiply(SPEED / 20 * wave.age));
        // The fireball bends out of the caster's wand onto the true eye line over its first 3 blocks.
        tipOffset = wave.getOwner() instanceof LivingEntity owner && wave.age < 3
                ? BlockFireEffects.tip(owner, 1).subtract(origin) : Vec3d.ZERO;
        seed = wave.getId() % 89 * 13.7;
    }

    private Vec3d path(double s) {
        s = Math.max(0, s);
        return origin.add(dir.multiply(s)).add(tipOffset.multiply(1 - smoothstep(s / WAKE_BLEND)));
    }
    private Vec3d headAt(double s) { return path(s + HEAD_LEAD); }
    private double grow(double t) { return smoothstep((t - start) / GROW); }

    @Override public boolean draw(BlockFireEffects.Frame f) {
        double now = f.now();
        if (Double.isNaN(end) && wave.isRemoved()) end = now;
        double last = Double.isNaN(end) ? now : end;
        if (!Double.isNaN(end) && now > end + .8) return false;
        if (!BlockFireEffects.near(f, path(travelled), 96)) return true;
        BlockFireMesh m = f.m();
        if (Double.isNaN(end)) {
            travelled = Math.max(0, wave.getLerpedPos(f.delta()).subtract(origin).dotProduct(dir));
            double g = grow(now), t = f.t();
            Vec3d c = headAt(travelled), fw = headAt(travelled + .05).subtract(headAt(travelled - .05)).normalize();
            tongues(m, c, fw, t, g, travelled);
            head(m, c, fw, t, g);
        }
        trail(m, now, last);
        return true;
    }

    /** Dense hot head: a bright nose and core, then two flickering shells that cool from front to back. */
    private static void head(BlockFireMesh m, Vec3d c, Vec3d f, double t, double g) {
        Vec3d r = f.crossProduct(Math.abs(f.y) > .99 ? X : UP).normalize(), u = r.crossProduct(f);
        // A slow roll about the side axis keeps the shell churning; heat is read after the roll so the nose stays hottest.
        double roll = t * 5.5 + .7;
        cube(m, c.add(f.multiply(.1 * g)), .7 * g * flick(t, 1), CORE, 1, rotate(AXES, r, roll * .6));
        cube(m, c.add(f.multiply(.42 * g)), .44 * g * flick(t, 2), CORE, 2, tumble(2, t, 2.5));
        for (int i = 0; i < 7; i++) {
            Vec3d d = rotate(local(sphereDir(i, 7), f, u, r), r, roll);
            double front = d.dotProduct(f), s = .42 * g * flick(t, i + 10) * (1 - .22 * Math.max(0, -front));
            cube(m, c.add(d.multiply(.33 * g)), s, heat(3.05 + .95 * front), 10 + i, tumble(10 + i, t, 2));
        }
        for (int i = 0; i < 10; i++) {
            Vec3d d = rotate(local(sphereDir(i, 10), f, u, r), r, roll);
            double front = d.dotProduct(f), s = .3 * g * flick(t, i + 30) * (1 - .3 * Math.max(0, -front));
            cube(m, c.add(d.multiply((.52 + .06 * front) * g)), s, heat(2.25 + 1.25 * front), 30 + i, tumble(30 + i, t, 3));
        }
    }
    private static double flick(double t, double k) { return .84 + .3 * noise(t * 7.3 + k * 3.71); }
    private static Vec3d local(Vec3d d, Vec3d f, Vec3d u, Vec3d r) { return f.multiply(d.y).add(u.multiply(d.x)).add(r.multiply(d.z)); }

    private static void tongues(BlockFireMesh m, Vec3d c, Vec3d f, double t, double g, double s) {
        Vec3d r = f.crossProduct(Math.abs(f.y) > .99 ? X : UP).normalize(), u = r.crossProduct(f);
        double room = Math.max(0, s + HEAD_LEAD - TAIL_START);
        for (int i = 0; i < TONGUES.length; i++) {
            double[] q = TONGUES[i];
            Vec3d radial = u.multiply(Math.cos(q[0])).add(r.multiply(Math.sin(q[0])));
            Vec3d root = c.add(f.multiply(-.12 * g)).add(radial.multiply(q[1] * g));
            Vec3d dir = f.multiply(-1).add(radial.multiply(q[2])).add(0, .03, 0).normalize();
            tongue(m, root, dir, q[4] * g, Math.min(q[3], room) * g, 200 + i * 13, q[5], t);
        }
    }

    /**
     * A swept-back tongue rooted inside the head: pieces stream from a hidden root along dir, cool and shrink to
     * nothing at the tip, and the tongue flickers by length. Each piece starts lined up and tumbles a little more
     * the further back it gets, so the tail churns instead of stacking into a slab.
     */
    private static void tongue(BlockFireMesh m, Vec3d root, Vec3d dir, double width, double length, double seed, double heat, double t) {
        if (length <= .01 || width <= .01) return;
        Vec3d d = dir.normalize();
        double roll = rnd(seed + .9) * TAU, top = heat * 3;
        Vec3d[] b = basis(d, roll);
        double L = length * (.82 + .3 * noise(t * 4.3 + seed * 7.1));
        int n = (int) clamp(Math.round(L / width * 2), 3, 7);
        double period = clamp(.32 + L * .2, .32, 1.1) / 2.6;
        for (int i = 0; i < n; i++) {
            double phase = t / period + i / (double) n + rnd(seed + i * 1.31) * .35 / n, loop = Math.floor(phase), u = phase - loop;
            double k = seed + i * 4.1 + loop * 17.3, w = width * .8 * Math.pow(1 - u, .85) * (.85 + .3 * rnd(k));
            if (w < .012) continue;
            Vec3d side = b[0].multiply((rnd(k + 1.7) - .5) * width * .5 * u).add(b[2].multiply((rnd(k + 2.9) - .5) * width * .5 * u));
            Vec3d spinAxis = new Vec3d(rnd(k + 4.1) - .5, rnd(k + 4.3) - .5, rnd(k + 4.7) - .5).normalize();
            Vec3d[] axes = rotate(basis(d, roll + (rnd(k + 3.3) - .5) * 1.2), spinAxis, (1.2 + 1.4 * rnd(k + 5.3)) * u);
            cube(m, root.add(d.multiply(L * Math.pow(u, .85))).add(side), w, w * 1.12, w, heat(top - u * 2.7), seed + i, axes);
        }
    }

    /** Pieces that peel off the tongues, stay behind in the world, drift a little, rise, cool and shrink to nothing. */
    private void trail(BlockFireMesh m, double now, double last) {
        Vec3d r = dir.crossProduct(Math.abs(dir.y) > .99 ? X : UP).normalize(), up = r.crossProduct(dir);
        int first = (int) Math.max(0, Math.ceil((now - .7 - start - .035) / SHED_DT));
        int lastShed = (int) Math.floor((last - start - .035) / SHED_DT);
        for (int i = first; i <= lastShed; i++) {
            double tb = start + .035 + i * SHED_DT;
            if (now < tb) continue;
            double sb = SPEED * (tb - start), g = grow(tb), age = now - tb;
            for (int k = 0; k < 2; k++) {
                double sd = seed + 500 + i * 7.31 + k * 3.17, life = .38 + .3 * rnd(sd), l = age / life;
                if (l >= 1) continue;
                double back = .5 + (k > 0 ? 1.9 : 1.1) * rnd(sd + 1), a = rnd(sd + 2) * TAU;
                Vec3d radial = up.multiply(Math.cos(a)).add(r.multiply(Math.sin(a)));
                double along = sb + HEAD_LEAD - back * g;
                if (along < TAIL_START) continue;
                Vec3d birth = path(along).add(radial.multiply((.08 + (.22 + .16 * back) * rnd(sd + 3)) * g));
                // Momentum carries a piece forward about half a block before it hangs and rises.
                Vec3d p = birth.add(dir.multiply(.5 * (1 - Math.exp(-age / .05)))).add(radial.multiply(.3 * l)).add(0, .15 * l + .45 * l * l, 0);
                // Pieces shed in the first few blocks stay small, so the caster's own view stays clear.
                double near = .4 + .6 * smoothstep((along - TAIL_START) / 3);
                double s = (k > 0 ? .24 : .34) * (.75 + .5 * rnd(sd + 4)) * g * near * Math.pow(1 - l, 1.15);
                cube(m, p, s, heat(3.2 - k * .5 - l * 3.4 + rnd(sd + 5) * .4), sd, tumble(sd, age, 5));
            }
            if (i % 2 == 0) sparkBurst(m, path(sb - .6), age, new Sparks(1, .9, .5, .55, seed + 900 + i).g(-2).size(.06));
        }
    }

    /**
     * burst() with a direction: pieces lean toward dir (a pierce's follow-through), or with a normal they fold into
     * a half-ball off a face and flatten along it (flat 0..1). flash scales the hot centre cluster.
     */
    static void splash(BlockFireMesh m, Vec3d c, double age, Vec3d dir, double bias, Vec3d normal, double flat,
            double radius, int count, double size, double duration, double up, double seed, double flash) {
        if (age < 0) return;
        double life = age / duration;
        if (life > 2.2) return;
        if (flash > 0 && life < .42) {
            double k = life / .42, s = size * 2.2 * flash * Math.pow(Math.sin(k * Math.PI), .6);
            cube(m, c, s, heat(4 - k * 1.5), seed, tumble(seed, age, 3));
            cube(m, c, s * .84, heat(4 - k * 1.5 - 1), seed + 1, tumble(seed + 1, age, -3));
        }
        for (int i = 0; i < count; i++) {
            double delay = rnd(seed + i * 1.7) * .1, l = clamp((life - delay) / (1 - delay));
            if (l <= 0 || l >= 1) continue;
            Vec3d d = sphereDir(i, count);
            if (normal != null) {
                double k = d.dotProduct(normal);
                if (k < 0) d = d.subtract(normal.multiply(2 * k));
                d = d.subtract(normal.multiply(d.dotProduct(normal) * flat));
            }
            if (dir != null) d = d.add(dir.multiply(bias));
            d = d.add(0, up, 0).normalize();
            double r = radius * (1 - Math.pow(1 - l, 2.2)) * (.55 + .45 * rnd(seed + i * 3.3));
            Vec3d p = c.add(d.multiply(r)).add(0, .45 * l * l, 0);
            double s = size * (.6 + .6 * rnd(seed + i * 4.9)) * Math.min(1, l * 8) * Math.pow(1 - l, 1.1);
            cube(m, p, s, heat(4 - l * 4.2 + rnd(seed + i * 2.3) * .6), seed + i, tumble(seed + i, age, 4));
        }
    }

    /** The small burst just past the wand tip at the cast. */
    record Ignition(Vec3d at, Vec3d dir, double start) implements BlockFireEffects.Fx {
        @Override public boolean draw(BlockFireEffects.Frame f) {
            double age = f.now() - start;
            if (age > .7) return false;
            burst(f.m(), at, age, new Burst(.5, 8, .16, .24, 7).up(.25).smoke(false));
            sparkBurst(f.m(), at, age, new Sparks(5, 1.2, 1, .45, 40).g(4).dir(dir, 1.2).size(.07));
            return true;
        }
    }

    /** A forward-thrown burst on each enemy the wave burns through. The burning itself is vanilla fire. */
    record Hit(Vec3d at, Vec3d dir, double start) implements BlockFireEffects.Fx {
        @Override public boolean draw(BlockFireEffects.Frame f) {
            double age = f.now() - start, s = BlockFireEffects.seed(at, start);
            if (age > 1.4) return false;
            if (!BlockFireEffects.near(f, at, 96)) return true;
            BlockFireMesh m = f.m();
            splash(m, at, age, dir, .7, null, 0, 1, 16, .32, .5, .45, s + 60, .9);
            sparkBurst(m, at, age, new Sparks(8, 2.2, 1.8, .6, s + 80).g(6).dir(dir, 1.4).size(.07));
            smoke(m, at.add(dir.multiply(.2)).add(0, .5, 0), age - .25, .3, s + 95, 1, 1.1);
            return true;
        }
    }

    /**
     * Where the wave stops. On a block face it squashes into a hot disc, splashes back off the face, licks up it
     * and leaves embers stuck there; in open air (no face) it simply breaks apart.
     */
    record Break(Vec3d at, Vec3d dir, Vec3d normal, double start) implements BlockFireEffects.Fx {
        @Override public boolean draw(BlockFireEffects.Frame f) {
            double age = f.now() - start, sd = BlockFireEffects.seed(at, start);
            if (age > 2.6) return false;
            if (!BlockFireEffects.near(f, at, 96)) return true;
            BlockFireMesh m = f.m();
            if (normal.lengthSquared() < .5) {
                splash(m, at, age, dir, .4, null, 0, 1.6, 24, .42, .6, .35, sd + 300, 1.2);
                sparkBurst(m, at, age, new Sparks(12, 2.4, 2.2, .75, sd + 340).g(6).dir(dir, .8).size(.07));
                for (int i = 0; i < 4; i++)
                    smoke(m, at.add((rnd(sd + 360 + i) - .5) * 1.2, .2 + .4 * rnd(sd + 370 + i), (rnd(sd + 380 + i) - .5) * 1.2),
                            age - .25 - i * .06, .5, sd + 390 + i, 1.4, 1.4);
                return true;
            }
            Vec3d n = normal.normalize();
            // The face's own frame: u runs up the face (or along the flight on a floor), w across it, into = -n.
            Vec3d u = UP.subtract(n.multiply(n.y));
            if (u.lengthSquared() < .01) u = dir.subtract(n.multiply(dir.dotProduct(n)));
            if (u.lengthSquared() < .01) u = X.subtract(n.multiply(n.x));
            u = u.normalize();
            Vec3d into = n.multiply(-1), w = into.crossProduct(u);
            Vec3d face = at.add(n.multiply(.15));
            splash(m, face, age, null, 0, n, .55, 2.2, 34, .5, .75, .3, sd + 300, 1.5);
            pancake(m, age, u, w, into, n, sd);
            crumple(m, age, n, sd);
            wallFlames(m, age, u, w, n, sd);
            scorch(m, age, u, w, into, n, sd);
            sparkBurst(m, face, age, new Sparks(14, 2.6, 2.4, .8, sd + 340).g(6).dir(n, 1.1).size(.07));
            for (int i = 0; i < 5; i++)
                smoke(m, face.add(n.multiply(.4 + .3 * rnd(sd + 360 + i))).add(0, .3 + .4 * rnd(sd + 370 + i), 0)
                        .add(w.multiply((rnd(sd + 380 + i) - .5) * 1.4)), age - .25 - i * .06, .55, sd + 390 + i, 1.4, 1.4);
            return true;
        }
        private Vec3d[] spin(Vec3d into, Vec3d u, Vec3d w, double a) {
            double c = Math.cos(a), s = Math.sin(a);
            return new Vec3d[]{into, u.multiply(c).add(w.multiply(s)), w.multiply(c).subtract(u.multiply(s))};
        }
        /** The head squashes flat on the face: a hot disc that spreads across it, cools and shrinks away. */
        private void pancake(BlockFireMesh m, double age, Vec3d u, Vec3d w, Vec3d into, Vec3d n, double sd) {
            if (age < 0 || age > .4) return;
            double k = smoothstep(age / .05) * (1 - smoothstep((age - .04) / .2));
            if (k > .02) {
                double s = 1.15 * k;
                cube(m, at.add(n.multiply(.16)), .3, s, s * 1.1, heat(4 - age * 6), sd + 1601, spin(into, u, w, age * .6));
                cube(m, at.add(n.multiply(.26)), .24, s * .8, s * .85, CORE, sd + 1602, spin(into, u, w, -age * .8 + .4));
            }
            for (int i = 0; i < 14; i++) {
                double seed = sd + 1610 + i * 4.3, a = (i + .4 * rnd(seed)) / 14 * TAU, life = .24 + .12 * rnd(seed + 1), l = age / life;
                if (l >= 1) continue;
                double r = (.35 + 1.5 * (1 - Math.pow(1 - l, 2.4))) * (.8 + .35 * rnd(seed + 2));
                double sz = (.42 + .18 * rnd(seed + 3)) * Math.min(1, l * 10) * Math.pow(1 - l, 1.2);
                Vec3d p = at.add(n.multiply(sz * .55 + .03)).add(u.multiply(Math.sin(a) * r * .85)).add(w.multiply(Math.cos(a) * r)).add(0, .3 * l * l, 0);
                cube(m, p, sz * .75, sz, sz, heat(3.8 - l * 3.2), seed, spin(into, u, w, a + l * 1.4));
            }
        }
        /** The tongues' pieces carry on into the face, slow against it and fan out across it. */
        private void crumple(BlockFireMesh m, double age, Vec3d n, double sd) {
            if (age < 0) return;
            Vec3d r = dir.crossProduct(Math.abs(dir.y) > .99 ? X : UP).normalize(), up = r.crossProduct(dir);
            for (int i = 0; i < 18; i++) {
                double seed = sd + 1500 + i * 5.3, life = .32 + .2 * rnd(seed), l = age / life;
                if (l >= 1) continue;
                double back = .2 + 2.4 * rnd(seed + 1), a = rnd(seed + 2) * TAU;
                Vec3d radial = up.multiply(Math.cos(a)).add(r.multiply(Math.sin(a)));
                double s0 = .36 * (1 - back / 3.2) * (.7 + .5 * rnd(seed + 3)), spread = 1 - Math.pow(1 - l, 2);
                Vec3d p = at.subtract(dir.multiply(.55 + back)).add(radial.multiply((.1 + .3 * back) * rnd(seed + 4)))
                        .add(dir.multiply(1.4 * (1 - Math.exp(-age / .07))));
                double gap = p.subtract(at).dotProduct(n), keep = s0 * .5 + .02;
                if (gap < keep) p = p.add(n.multiply(keep - gap));
                p = p.add(radial.multiply(spread)).add(0, .4 * l * l, 0);
                cube(m, p, s0 * Math.pow(1 - l, 1.1), heat(3.3 - l * 3.4), seed, tumble(seed, age, 5));
            }
        }
        /** Tongues that flare up the face and die down by shrinking. */
        private void wallFlames(BlockFireMesh m, double age, Vec3d u, Vec3d w, Vec3d n, double sd) {
            if (age < 0) return;
            for (int i = 0; i < 8; i++) {
                double seed = sd + 1700 + i * 9.1, a = i / 8.0 * TAU + rnd(seed) * .6, r = .35 + .75 * rnd(seed + 1);
                double env = smoothstep(age / .08) * (1 - smoothstep((age - .15 - .2 * rnd(seed + 2)) / (.5 + .3 * rnd(seed + 3))));
                if (env <= .02) continue;
                Vec3d root = at.add(n.multiply(.2)).add(u.multiply(Math.sin(a) * r * .8 - .3)).add(w.multiply(Math.cos(a) * r * 1.2));
                Vec3d up = n.multiply(.3).add(UP).add(w.multiply(Math.cos(a) * .15));
                flame(m, root, up, new Flame(.46 * (.6 + .4 * env), (1.1 + .8 * rnd(seed + 4)) * env, Math.floor(seed)).heat(.95).sway(.04).t(age));
            }
        }
        /** Embers stuck to the face that glow and shrink away. */
        private void scorch(BlockFireMesh m, double age, Vec3d u, Vec3d w, Vec3d into, Vec3d n, double sd) {
            if (age < 0) return;
            for (int i = 0; i < 10; i++) {
                double seed = sd + 1900 + i * 3.7, a = rnd(seed) * TAU, r = 1.15 * Math.sqrt(rnd(seed + 1));
                double k = 1 - smoothstep((age - .25 - .6 * rnd(seed + 2)) / (.7 + .4 * rnd(seed + 3)));
                if (k <= 0) continue;
                double s = (.14 + .12 * rnd(seed + 4)) * k * Math.min(1, age / .05);
                cube(m, at.add(n.multiply(.03)).add(u.multiply(Math.sin(a) * r * .8)).add(w.multiply(Math.cos(a) * r)), .06, s, s, EMBER, seed,
                        new Vec3d[]{into, u, w});
            }
        }
    }
}
