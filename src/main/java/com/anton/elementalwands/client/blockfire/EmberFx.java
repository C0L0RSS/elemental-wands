package com.anton.elementalwands.client.blockfire;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.FlashoverEmberEntity;
import java.util.ArrayDeque;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Flashover (art/fire/blockfire/scenes/flashover.js): each coal is a charred lump with glowing cracks that
 * tumbles in flight, sits dim while it arms, then breathes heat once armed. The server announces each pop,
 * which swells into a ball of block fire at the real 4-block reach and collapses into smoke.
 */
final class EmberFx implements BlockFireEffects.Fx {
    private static final double TICK = .05, ARM = 10 * TICK, RADIUS = 4, SWELL = .18;
    private static final int SHELL = 78;
    private record Sample(double time, Vec3d pos) {}

    private final FlashoverEmberEntity ember;
    private final double seed, thrown;
    private final ArrayDeque<Sample> path = new ArrayDeque<>();
    private Vec3d tipOffset = Vec3d.ZERO, normal;
    private double settled = Double.NaN, armed = Double.NaN, primed = Double.NaN;

    EmberFx(FlashoverEmberEntity ember, double now) {
        this.ember = ember;
        seed = 11 + Math.floorMod(ember.getId(), 41) * 13;
        thrown = now - ember.age / 20.0;
        // The coal leaves its thrower's wand: find who threw it from where it started, a hair under their eyes.
        Vec3d launch = ember.getEntityPos().subtract(ember.getVelocity().multiply(ember.age));
        if (ember.age < 3 && !ember.settled()) {
            PlayerEntity best = null;
            for (PlayerEntity p : ember.getEntityWorld().getPlayers())
                if (p.getEyePos().squaredDistanceTo(launch) < 2.25 && (best == null
                        || p.getEyePos().squaredDistanceTo(launch) < best.getEyePos().squaredDistanceTo(launch))) best = p;
            if (best != null) tipOffset = BlockFireEffects.tip(best, 1).subtract(launch);
        }
    }

    /** Where the coal was at time, from the positions this client has drawn. */
    private Vec3d at(double time) {
        Sample before = null;
        for (Sample s : path) {
            if (s.time >= time) {
                if (before == null) return s.pos;
                double k = (time - before.time) / Math.max(1e-6, s.time - before.time);
                return before.pos.lerp(s.pos, k);
            }
            before = s;
        }
        return before == null ? ember.getEntityPos() : before.pos;
    }

    /** The face the coal sits on, from the blocks around it; up on a creature. */
    private Vec3d face() {
        if (normal != null) return normal;
        if (ember.attachedId() >= 0) return normal = UP;
        var w = ember.getEntityWorld();
        Vec3d p = ember.getEntityPos();
        Direction[] order = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
        for (Direction d : order) {
            BlockPos q = BlockPos.ofFloored(p.subtract(Vec3d.of(d.getVector()).multiply(.3)));
            if (!w.getBlockState(q).getCollisionShape(w, q).isEmpty()) return normal = Vec3d.of(d.getVector());
        }
        return normal = UP;
    }

    @Override public boolean draw(BlockFireEffects.Frame f) {
        if (ember.isRemoved()) return false;
        double t = f.now();
        Vec3d p = ember.attachedId() >= 0 ? ember.attachedPosition(f.delta()) : ember.getLerpedPos(f.delta());
        if (Double.isNaN(settled) && ember.settled()) settled = t;
        if (Double.isNaN(armed) && ember.armed()) armed = t;
        if (Double.isNaN(primed) && ember.primed()) primed = t;
        if (Double.isNaN(settled)) {
            double ticks = (t - thrown) / TICK;
            // Visual hand-off from the wand tip to the entity path over the first 3 ticks.
            Vec3d q = p.add(tipOffset.multiply(1 - smoothstep(ticks / 3)));
            // One sample per moment: the clock stands still while the game is paused and can step back a tick.
            while (!path.isEmpty() && path.peekLast().time >= t) path.removeLast();
            path.addLast(new Sample(t, q));
            while (path.size() > 2 && path.peekFirst().time < t - .2) path.removeFirst();
        }
        if (!BlockFireEffects.near(f, p, 80)) return true;
        BlockFireMesh m = f.m();
        if (Double.isNaN(settled)) {
            // In flight: dim, spinning, with a short trail that shrinks into the lump (nothing pops off).
            Vec3d q = path.peekLast().pos;
            lump(m, q, tumble(seed, t, 11), seed, 1.2, .55, .1, .95);
            for (int j = 1; j <= 8; j++) {
                double back = t - j * .008;
                if (back < thrown) break;
                cube(m, at(back), .2 * (1 - j / 9.5), heat(2.4 - j * .25), seed + 40 + j, tumble(seed + j, t, 8));
            }
            return true;
        }
        Vec3d n = face();
        Vec3d[] axes = basis(n, rnd(seed) * TAU);
        double since = t - settled;
        if (Double.isNaN(armed)) {
            // Arming: dark crust and a dull core that warms as the half second runs out.
            double w = clamp(since / ARM);
            lump(m, p, axes, seed, .6 + .8 * w, .42 + .1 * w, .05, 1);
            if (since < .25) thud(m, p, n, since);
            return true;
        }
        double armedFor = t - armed, b = .5 + .5 * Math.sin((t / TICK) * .12 + seed);
        double flare = armedFor < .6 ? Math.exp(-armedFor * 9) : 0;
        double heat = 2.5 + 1.1 * b + 1.5 * flare, glow = .86 + .14 * b, open = .3 + .35 * b + .9 * flare;
        if (!Double.isNaN(primed)) {
            // Primed: a fast flicker, and the chunks start to part before the pop.
            double k = clamp((t - primed) / .3), fl = .7 + .3 * Math.sin((t / TICK) * 1.5);
            heat = 3.2 + fl; glow = fl; open = .8 + 1.4 * k;
        }
        lump(m, p, axes, seed, heat, glow, open, 1);
        // Heat breathing off the top: a small tongue that grows and fades with each breath, always upright.
        Vec3d top = p.add(n.multiply(.06)).add(0, .13, 0);
        flame(m, top, new Vec3d(n.x * .25, 1, n.z * .25), new Flame(.12 + .03 * b, .16 + .26 * b + .3 * flare, seed + 5)
                .heat(.45 + .2 * b).sway(.04).count(3).speed(.7).t(t));
        if (m.embers) {
            // A spark every 12 ticks.
            double per = 12 * TICK, u = fract(armedFor / per), k = Math.floor(armedFor / per);
            cube(m, top.add((rnd(seed + k) - .5) * .1, .15 + u * .7, (rnd(seed + k + .5) - .5) * .1), .06 * (1 - u), heat(3 - u * 2), seed + k, tumble(seed + k, t, 5));
        }
        return true;
    }

    /**
     * A charred lump: eight char chunks in a loose 2x2x2 around a hot core cube, so the core shows through the
     * cracks. heat is the core's band index, glow dims the crust, open spreads the chunks.
     */
    private static void lump(BlockFireMesh m, Vec3d p, Vec3d[] axes, double seed, double heat, double glow, double open, double scale) {
        cube(m, p, .24 * scale * (1 + open * .35), heat(heat), seed, axes);
        double off = (.1 + .045 * open) * scale;
        for (int i = 0; i < 8; i++) {
            double sx = (i & 1) != 0 ? 1 : -1, sy = (i & 2) != 0 ? 1 : -1, sz = (i & 4) != 0 ? 1 : -1;
            double jx = (rnd(seed + i * 1.7 + .1) - .5) * .03 * scale, jy = (rnd(seed + i * 1.7 + .2) - .5) * .03 * scale, jz = (rnd(seed + i * 1.7 + .3) - .5) * .03 * scale;
            Vec3d c = p.add(axes[0].multiply(sx * off + jx)).add(axes[1].multiply(sy * off + jy)).add(axes[2].multiply(sz * off + jz));
            // Each chunk is tipped a little on its own axis, which opens wedge-shaped cracks instead of a neat grid.
            Vec3d k = new Vec3d(rnd(seed + i * 2.3) - .5, rnd(seed + i * 2.9) - .5, rnd(seed + i * 3.7) - .5).normalize();
            double tilt = .18 + .12 * rnd(seed + i * 4.1), s = .165 * scale * (.88 + .24 * rnd(seed + i * 5.3));
            cube(m, c, s, s * (.85 + .2 * rnd(seed + i * 6.1)), s, CHAR, seed + i + 1, rotate(axes, k, tilt), glow, 1);
        }
    }

    /** A few spark chips kicked off where the coal lands. */
    private void thud(BlockFireMesh m, Vec3d p, Vec3d n, double age) {
        if (!m.embers) return;
        for (int i = 0; i < 5; i++) {
            Vec3d d = n.add((rnd(seed + i * 1.1) - .5) * 1.6, .4, (rnd(seed + i * 1.9) - .5) * 1.6).normalize();
            double l = age / .25;
            cube(m, p.add(d.multiply(.9 * l)).add(0, -1.2 * l * l, 0), .07 * (1 - l), heat(3 - l * 2), seed + i, tumble(seed + i, age, 6));
        }
    }

    /** One pop: a ball of block fire at the real reach that cools, falls in on itself and turns to smoke. */
    record Blast(Vec3d c, Vec3d normal, double order, double start) implements BlockFireEffects.Fx {
        @Override public boolean draw(BlockFireEffects.Frame f) {
            double age = f.now() - start;
            if (age > 2.6) return false;
            if (!BlockFireEffects.near(f, c, 112)) return true;
            BlockFireMesh m = f.m();
            double seed = (BlockFireEffects.seed(c, start) + order * 7) * 13;
            boolean surface = normal.lengthSquared() > .5;
            Vec3d n = surface ? normal.normalize() : UP;
            double k = clamp(age / SWELL), grow = 1 - Math.pow(1 - k, 3), after = Math.max(0, age - SWELL);
            // The heart: big hot cubes that bloom first and outlast the shell.
            for (int j = 0; j < 4; j++) {
                double life = clamp(after / (.42 + .1 * j));
                if (life >= 1) continue;
                Vec3d d = sphereDir(j, 4);
                if (surface && d.dotProduct(n) < 0) d = d.subtract(n.multiply(2 * d.dotProduct(n)));
                double s = (1.9 - .2 * j) * Math.min(1, age / .06 + .25) * Math.pow(1 - life, .75);
                // White-hot only for the first couple of ticks, then yellow and cooling.
                cube(m, c.add(d.multiply(.95 * grow)).add(0, .5 * life, 0), s, heat(3.3 - j * .3 + .9 * (1 - k) - life * 3.2), seed + j, tumble(seed + j, age, 1.4));
            }
            // The shell: cubes thrown out to the 4-block edge, then cooling and falling back inward as they shrink away.
            for (int i = 0; i < SHELL; i++) {
                Vec3d d = sphereDir(i, SHELL);
                if (surface && d.dotProduct(n) < -.08) continue;
                boolean rim = rnd(seed + i * 2.1) < .7;
                double s0 = rim ? .68 + .42 * rnd(seed + i * 3.7) : 1.05 + .5 * rnd(seed + i * 3.7);
                double reach = rim ? RADIUS - s0 * .45 : RADIUS * (.38 + .4 * rnd(seed + i * 4.3));
                double life = clamp((after - .08 * rnd(seed + i * 5.9)) / (.46 + .36 * rnd(seed + i * 6.7)));
                if (life >= 1) continue;
                Vec3d pos = c.add(d.multiply(reach * grow * (1 - .55 * smoothstep(life)))).add(0, .7 * life * life, 0);
                double s = s0 * Math.min(1, k * 1.5 + .1) * Math.pow(1 - life, .85);
                double h = (rim ? 2.1 : 2.6) + .9 * rnd(seed + i * 7.3) + .5 * (1 - k) - life * 3.4;
                cube(m, pos, s, heat(h), seed + i, tumble(seed + i, age, 2.2));
            }
            // The crust: the eight char chunks blown off the lump.
            double l = age / .5;
            if (l < 1) for (int i = 0; i < 8; i++) {
                Vec3d d = new Vec3d((i & 1) != 0 ? 1 : -1, (i & 2) != 0 ? 1 : -1, (i & 4) != 0 ? 1 : -1).add(surface ? n.multiply(1.2) : new Vec3d(0, .4, 0)).normalize();
                cube(m, c.add(d.multiply(3.2 * (1 - Math.exp(-age * 7)))).add(0, -1.5 * age * age, 0), .2 * (1 - l), CHAR, seed + 60 + i, tumble(seed + 60 + i, age, 9));
            }
            if (!m.embers) return true;
            // Smoke rising out of the middle as the fire falls in on itself.
            Vec3d heart = c.add(surface ? n.multiply(.9) : new Vec3d(0, .3, 0));
            for (int i = 0; i < 9; i++) {
                double from = .3 + .32 * rnd(seed + i * 8.3), a = rnd(seed + i * 9.1) * TAU, r = .3 + 1.3 * rnd(seed + i * 9.4);
                Vec3d q = heart.add(Math.cos(a) * r, (rnd(seed + i * 4.4) - .3) * 1.2, Math.sin(a) * r);
                smoke(m, q, age - from, .5 + .3 * rnd(seed + i * 9.7), seed + 200 + i, 2.4 + 1.4 * rnd(seed + i * 3.1), 1.4 + .5 * rnd(seed + i * 2.7));
            }
            // Sparks flung out and falling.
            for (int i = 0; i < 18; i++) {
                double li = age / (.5 + .3 * rnd(seed + i * 1.3));
                if (li >= 1) continue;
                Vec3d d = sphereDir(i, 18).add(0, .5, 0).normalize();
                Vec3d q = c.add(d.multiply((2 + 2.2 * rnd(seed + i * 2.2)) * (1 - Math.pow(1 - li, 2)))).add(0, -1.4 * li * li, 0);
                cube(m, q, .09 * (1 - li), heat(3.8 - li * 3), seed + 300 + i, tumble(seed + 300 + i, age, 7));
            }
            return true;
        }
    }
}
