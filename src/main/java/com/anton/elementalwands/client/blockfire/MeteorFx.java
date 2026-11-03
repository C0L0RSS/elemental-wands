package com.anton.elementalwands.client.blockfire;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.util.MeteorManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Meteor (art/fire/blockfire/scenes/meteor.js): the approved eight-block stepped ball, built from block fire
 * with tongues streaming back against its fall and a shower of burning chunks; a ring of fire marks where it
 * lands; on impact a dome of fire balloons out to the 10-block damage edge with a racing ring of flames and
 * molten chunks. The explosion, crater and vanilla fires stay the server's.
 */
final class MeteorFx {
    private static final double TICK = .05, SIZE = 8, R = SIZE / 2, SPIN = .19, CELL = 2 / 3.0;
    private static final double RING_R = 5.4, RING_CLOSE = .8, DAMAGE_R = MeteorManager.DAMAGE_RADIUS;
    /** Downward speed on the last full step of the 35-block fall (vanilla falling-block physics from -0.2). */
    private static final double LAND_SPEED;
    private static final List<Vec3d> SHELL_C = new ArrayList<>(), CORE_C = new ArrayList<>();
    private static final List<Vec3d> TONGUE_D = new ArrayList<>();

    static {
        List<Double> ys = new ArrayList<>();
        double y = 35, v = -.2;
        ys.add(y);
        while (y > 0) { v -= .04; y = Math.max(0, y + v); v *= .98; ys.add(y); }
        int n = ys.size() - 1;
        LAND_SPEED = (ys.get(n - 2) - ys.get(n - 1)) / TICK;
        // Exposed cells of a sphere of radius 3.98 on a 2/3-block grid (12 cells across, the approved outline).
        cells(6, CELL, 3.98, SHELL_C);
        // The hot layer under the shell: exposed cells of a radius-3.1 sphere on a one-block grid.
        cells(3, 1, 3.1, CORE_C);
        // Tongue roots: even directions over the top half of the ball.
        for (int i = 0; i < 46; i++) { Vec3d d = sphereDir(i, 46); if (d.y > .05) TONGUE_D.add(d); }
    }
    private static boolean inside(int n, double cell, double radius, int i, int j, int k) {
        return i >= -n && i < n && j >= -n && j < n && k >= -n && k < n && Math.sqrt((i + .5) * (i + .5) + (j + .5) * (j + .5) + (k + .5) * (k + .5)) * cell < radius;
    }
    private static void cells(int n, double cell, double radius, List<Vec3d> out) {
        int[][] around = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int i = -n; i < n; i++) for (int j = -n; j < n; j++) for (int k = -n; k < n; k++) {
            if (!inside(n, cell, radius, i, j, k)) continue;
            boolean exposed = false;
            for (int[] a : around) if (!inside(n, cell, radius, i + a[0], j + a[1], k + a[2])) { exposed = true; break; }
            if (exposed) out.add(new Vec3d((i + .5) * cell, (j + .5) * cell, (k + .5) * cell));
        }
    }
    private MeteorFx() {}

    /** The surface under (x, z) within a few blocks of y, or y itself when there is none. */
    static double ground(ClientWorld world, double x, double z, double y) {
        Vec3d top = new Vec3d(x, y + 2.5, z);
        var hit = world.raycast(new RaycastContext(top, top.add(0, -7, 0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        return hit.getType() == HitResult.Type.MISS ? y : hit.getPos().y;
    }

    private record Sample(double time, Vec3d center, double speed) {}

    /** The falling ball and the chunks it sheds; follows the METEOR_CORE falling block. */
    static final class Ball implements BlockFireEffects.Fx {
        private final FallingBlockEntity meteor;
        private final double appear, seed;
        private final ArrayDeque<Sample> path = new ArrayDeque<>();
        private double gone = Double.NaN;

        Ball(FallingBlockEntity meteor, double now) {
            this.meteor = meteor; appear = now - meteor.age / 20.0; seed = Math.floorMod(meteor.getId(), 31) * 10000;
        }
        private Sample at(double time) {
            Sample before = null;
            for (Sample s : path) {
                if (s.time >= time) return before == null ? s : before;
                before = s;
            }
            return before;
        }

        @Override public boolean draw(BlockFireEffects.Frame f) {
            double t = f.now();
            if (Double.isNaN(gone) && meteor.isRemoved()) gone = t;
            if (!Double.isNaN(gone) && t > gone + 1.6) return false;
            if (Double.isNaN(gone)) {
                Vec3d c = meteor.getLerpedPos(f.delta()).add(0, R, 0);
                // One sample per moment: the clock stands still while the game is paused and can step back a tick.
                while (!path.isEmpty() && path.peekLast().time >= t) path.removeLast();
                path.addLast(new Sample(t, c, Math.max(0, -meteor.getVelocity().y / TICK)));
                while (path.size() > 2 && path.peekFirst().time < t - 1.6) path.removeFirst();
            }
            if (path.isEmpty() || !BlockFireEffects.near(f, path.peekLast().center, 192)) return true;
            BlockFireMesh m = f.m();
            if (Double.isNaN(gone)) ball(m, path.peekLast(), t);
            shower(m, t, Double.isNaN(gone) ? t : gone);
            return true;
        }

        private void ball(BlockFireMesh m, Sample now, double t) {
            Vec3d C = now.center;
            double k = smoothstep((t - appear) / .25), age = t - appear, sp = clamp(now.speed / LAND_SPEED), mt = m.time;
            Vec3d[] ax = yawAxes(SPIN * age);
            // Shell: hottest on the leading (bottom) face, cooling toward the top. Each cube boils in size and band;
            // some pulse small for a moment and open vents onto the hot layer below.
            for (int i = 0; i < SHELL_C.size(); i++) {
                Vec3d c = SHELL_C.get(i), n = c.normalize();
                double vent = smoothstep((noise(mt * 1.6 + i * 3.17) - .56) / .16);
                double sz = CELL * k * (.86 + .2 * noise(mt * 5.3 + i * 1.37)) * (1 - .72 * vent);
                double h = 2.3 - n.y * 1.15 + (noise(mt * 2.1 + i * .71) - .5) * 1.4 - (c.length() > 3.6 ? .3 : 0) + .35 * sp;
                cube(m, C.add(local(ax, c.multiply(k))), sz, heat(h), 1000 + i, ax);
            }
            // Hot layer: a one-block stepped sphere just under the shell, so every gap glows.
            for (int i = 0; i < CORE_C.size(); i++)
                cube(m, C.add(local(ax, CORE_C.get(i).multiply(k))), k, noise(mt * 2.7 + i * 1.9) > .45 ? CORE : YELLOW, 1700 + i, ax);
            // Tongues stream back (up) against the fall from the top half, lengthening as the ball speeds up.
            for (int j = 0; j < TONGUE_D.size(); j++) {
                Vec3d d = TONGUE_D.get(j), n = local(ax, d);
                boolean crown = d.y > .8;
                Vec3d root = C.add(n.multiply((R - .8) * k)), dir = n.multiply(crown ? .22 : .62).add(0, .9 + .9 * sp, 0).normalize();
                double L = (2 + 2.2 * rnd(2000 + j * 1.9)) * (crown ? 1.45 : 1) * (.8 + .55 * sp) * k, W = (.75 + .32 * rnd(2001 + j * 2.3)) * (crown ? 1.25 : 1) * k;
                flame(m, root, dir, new Flame(W, L, 2000 + j * 13).heat(1.05).sway(.04).speed(1.4 + sp));
            }
        }
        private static Vec3d local(Vec3d[] ax, Vec3d c) { return ax[0].multiply(c.x).add(UP.multiply(c.y)).add(ax[2].multiply(c.z)); }

        /** Burning chunks shed in flight, left behind as the ball falls away, so they stream above it. */
        private void shower(BlockFireMesh m, double t, double last) {
            double shed = 44;
            int first = (int) Math.ceil(Math.max(.18, t - appear - 1.15) * shed), end = (int) Math.floor((last - appear) * shed);
            for (int i = first; i <= end; i++) {
                double te = appear + i / shed, age = t - te, k = seed + i, life = .65 + .45 * rnd(k * 1.9), l = age / life;
                if (l < 0 || l >= 1) continue;
                Sample s = at(te);
                if (s == null) continue;
                double a = rnd(k * 2.3) * TAU, y = -.55 + 1.05 * rnd(k * 3.1), h = Math.sqrt(1 - y * y);
                Vec3d n = new Vec3d(Math.cos(a) * h, y, Math.sin(a) * h);
                // World velocity: half the ball's fall speed at release plus an outward kick.
                double out = 1.8 + 1.6 * rnd(k * 4.7), sz = (.45 + .4 * rnd(k * 5.3)) * Math.min(1, l * 6) * Math.pow(1 - l, 1.1);
                Vec3d p = s.center.add(n.multiply(R * .96)).add(n.x * out * age, -s.speed * .5 * age, n.z * out * age);
                Vec3d[] axes = tumble(k, age, 5);
                cube(m, p, sz, heat(3.9 - l * 3), k, axes);
                if (i % 2 == 0 && sz > .15) cube(m, p.add(axes[1].multiply(sz * .5)), sz * .55, heat(3.1 - l * 3.2), k + .5, axes);
            }
            if (!m.embers) return;
            // Ember sparks off the shell.
            int a0 = (int) Math.ceil(Math.max(.1, t - appear - .5) * 40), a1 = (int) Math.floor((last - appear) * 40);
            for (int i = a0; i <= a1; i++) {
                double te = appear + i / 40.0, age = t - te, l = age / .5, k = seed + 7000 + i;
                if (l < 0 || l >= 1) continue;
                Sample s = at(te);
                if (s == null) continue;
                double a = rnd(k * 6.1) * TAU, y = -.3 + 1.1 * rnd(k * 6.7), h = Math.sqrt(Math.max(0, 1 - y * y));
                Vec3d n = new Vec3d(Math.cos(a) * h, y, Math.sin(a) * h);
                Vec3d p = s.center.add(n.multiply(R + .2)).add(n.x * 5 * age, (-s.speed * .35 + 3) * age, n.z * 5 * age);
                cube(m, p, .16 * (1 - l), heat(3.6 - l * 2), k, tumble(k, age, 6));
            }
            // Smoke puffs come off the top half and are left behind, spreading into a loose trail.
            int s0 = (int) Math.ceil(Math.max(.3, t - appear - 1.5) * 5), s1 = (int) Math.floor((Math.min(last, t) - appear - .15) * 5);
            for (int i = s0; i <= s1; i++) {
                double te = appear + i / 5.0, k = seed + 7500 + i;
                Sample s = at(te);
                if (s == null) continue;
                double a = rnd(k * 8.3) * TAU, y = .35 + .6 * rnd(k * 8.9), h = Math.sqrt(1 - y * y);
                Vec3d n = new Vec3d(Math.cos(a) * h, y, Math.sin(a) * h);
                smoke(m, s.center.add(n.multiply(R + .3)).add(new Vec3d(n.x, 0, n.z).multiply(2.2 * (t - te))), t - te, .9 + .4 * rnd(k * 9.7), k, 2, 1.5);
            }
        }
    }

    /** The warning ring: short block flames on the ground that close in and grow as the ball comes down. */
    static final class Ring implements BlockFireEffects.Fx {
        private static final int FLAMES = 28, BAND = 40;
        private final ClientWorld world;
        private final int meteor;
        private final Vec3d at;
        private final double spawnY, start;
        private double gone = Double.NaN, progress;
        private final Map<Long, Double> ground = new HashMap<>();

        Ring(ClientWorld world, int meteor, Vec3d at, double spawnY, double now) {
            this.world = world; this.meteor = meteor; this.at = at; this.spawnY = spawnY; this.start = now;
        }
        private double groundAt(int i, double r, double x, double z) {
            return ground.computeIfAbsent(((long) i << 20) | Math.round(r * 20), key -> MeteorFx.ground(world, x, z, at.y));
        }

        @Override public boolean draw(BlockFireEffects.Frame f) {
            double t = f.now();
            Entity e = world.getEntityById(meteor);
            if (Double.isNaN(gone) && (e == null || e.isRemoved()) && t > start + .5) gone = t;
            if (!Double.isNaN(gone) && t > gone + .12) return false;
            if (t > start + 15) return false;
            if (e != null && !e.isRemoved())
                progress = clamp((spawnY - e.getLerpedPos(f.delta()).y) / Math.max(1, spawnY - at.y));
            if (!BlockFireEffects.near(f, at, 160)) return true;
            BlockFireMesh m = f.m();
            double p = progress, r = RING_R - RING_CLOSE * p, mt = m.time;
            double grow = smoothstep((t - start) / .3) * (Double.isNaN(gone) ? 1 : 1 - smoothstep((t - gone) / .12));
            if (grow <= 0) return true;
            for (int i = 0; i < FLAMES; i++) {
                double a = (i + .3 * rnd(3000 + i)) / FLAMES * TAU, c = Math.cos(a), s = Math.sin(a), x = at.x + c * r, z = at.z + s * r;
                flame(m, new Vec3d(x, groundAt(i, r, x, z), z), new Vec3d(-c * .12, 1, -s * .12),
                        new Flame(.55 * grow, (.5 + .85 * p) * grow * (.8 + .4 * rnd(3001 + i * 1.7)), 3100 + i * 7).heat(1 + .18 * p).sway(.04).speed(1 + .8 * p));
            }
            // A low band of burning ground under the flames, so the ring reads as a solid circle from far away.
            double chord = TAU * r / BAND;
            for (int i = 0; i < BAND; i++) {
                double a = (i + .5) / BAND * TAU, x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r, h = .14 * grow * (.8 + .4 * noise(mt * 5 + i * 2.1));
                cube(m, new Vec3d(x, groundAt(100 + i, r, x, z) + h / 2, z), chord * .94 * grow, h, .55 * grow,
                        heat(1.6 + p + (noise(mt * 3.3 + i * 1.3) - .5) * 1.4), 3500 + i, yawAxes(a + Math.PI / 2));
            }
            return true;
        }
    }

    /** The impact: the heart bursts, a dome balloons out to the damage edge, a ring of flames races out, chunks fly. */
    static final class Impact implements BlockFireEffects.Fx {
        private static final int DOME = 110, INNER = 64, SHOCK = 44, CHUNKS = 24, FLASH = 14;
        private static final double SHOCK_REACH = .3, CHUNK_G = 18;
        private final ClientWorld world;
        private final Vec3d at;
        private final double start;
        private final Map<Long, Double> ground = new HashMap<>();
        private final double[] landY = new double[CHUNKS], flightT = new double[CHUNKS];

        Impact(ClientWorld world, Vec3d at, double now) {
            this.world = world; this.at = at; this.start = now;
            for (int i = 0; i < CHUNKS; i++) { landY[i] = Double.NaN; }
        }
        private double groundAt(long key, double x, double z) {
            return ground.computeIfAbsent(key, k -> MeteorFx.ground(world, x, z, at.y));
        }

        @Override public boolean draw(BlockFireEffects.Frame f) {
            double a = f.now() - start;
            if (a > 4.2) return false;
            if (!BlockFireEffects.near(f, at, 192)) return true;
            BlockFireMesh m = f.m();
            flash(m, a);
            dome(m, a);
            shock(m, a);
            chunks(m, a);
            sparkBurst(m, at.add(0, 1, 0), a, new Sparks(40, 11, 10, 1.2, 6800).size(.2).g(14));
            for (int i = 0; i < 12; i++) {
                double sd = 6900 + i * 2.3, ang = rnd(sd) * TAU, r = 3 + 5 * rnd(sd + .1);
                smoke(m, at.add(Math.cos(ang) * r, 1 + 2 * rnd(sd + .2), Math.sin(ang) * r), a - .45 - .6 * rnd(sd + .3), 1 + .6 * rnd(sd + .4), sd, 5 + 2 * rnd(sd + .5), 2.2);
            }
            return true;
        }

        /** The ball's heart bursts: white-hot cubes where the ball was, swelling a little and sinking away. */
        private void flash(BlockFireMesh m, double a) {
            if (a < 0 || a > .34) return;
            double grow = a < .05 ? lerp(.85, 1, a / .05) : 1 - smoothstep((a - .05) / .29);
            for (int j = 0; j < FLASH; j++) {
                Vec3d d = sphereDir(j, FLASH);
                double sd = 4000 + j * 2.3, o = (1.6 + .8 * rnd(sd)) * (1 + 2.2 * a);
                cube(m, at.add(d.x * o, R * .8 + d.y * o * .8, d.z * o), (2.6 + 1.1 * rnd(sd + .1)) * grow, heat(4.3 - a * 6 - rnd(sd + .2) * .8), sd, tumble(sd, a, 1.6));
            }
        }

        private void dome(BlockFireMesh m, double a) {
            if (a < 0 || a > 2) return;
            // Outer shell: burning clumps leave the ball's surface and balloon out to the damage edge, then cool,
            // drift up and shrink away. The top thins and cools first; the skirt near the ground lasts longest.
            for (int i = 0; i < DOME; i++) {
                Vec3d d = sphereDir(i, DOME * 2);
                double sd = 4100 + i * 1.3, delay = .04 * rnd(sd), e = 1 - Math.pow(1 - clamp((a - delay) / .4), 3), R1 = DAMAGE_R * (.9 + .1 * rnd(sd + .2));
                double r = lerp(SIZE * d.y * .95, R1, e) + .7 * Math.max(0, a - .4);
                double die = 1 - smoothstep((a - .22 - .25 * rnd(sd + .4) - .2 * (1 - d.y)) / (.4 + .25 * rnd(sd + .5)));
                if (die <= 0) continue;
                double sz = (.62 + .45 * rnd(sd + .6)) * (.8 + .2 * e) * die;
                Vec3d p = at.add(d.multiply(r)).add(0, 1.1 * Math.pow(Math.max(0, a - .3), 2), 0);
                double h = 3.75 - a * 2.6 - .4 * d.y * smoothstep(a / .3) + (rnd(sd + .7) - .5) * 1.1;
                clump(m, p, sz, h, sd, basis(d, rnd(sd + .8) * TAU + a * (rnd(sd + .9) < .5 ? -1.4 : 1.4)));
            }
            // Inner layer: hotter, a little behind, gone sooner; it fills the gaps of the outer shell.
            for (int i = 0; i < INNER; i++) {
                Vec3d d = sphereDir(i, INNER * 2);
                double sd = 4700 + i * 1.7, e = 1 - Math.pow(1 - clamp(a / .46), 3);
                double r = lerp(SIZE * d.y * .75, DAMAGE_R * .64 * (.88 + .12 * rnd(sd)), e);
                double die = 1 - smoothstep((a - .18 - .2 * rnd(sd + .4)) / (.35 + .25 * rnd(sd + .5)));
                if (die <= 0) continue;
                double sz = (.8 + .5 * rnd(sd + .6)) * die;
                Vec3d p = at.add(d.multiply(r)).add(0, 1.4 * Math.pow(Math.max(0, a - .25), 2), 0);
                cube(m, p, sz, sz * 1.3, sz, heat(3.95 - a * 2.6 + (rnd(sd + .7) - .5) * .8), sd, basis(d, rnd(sd + .8) * TAU + a * 1.2));
            }
        }
        /** A burning clump: a main cube with a cooler tip along axes[1] and two smaller cubes on its sides. */
        private static void clump(BlockFireMesh m, Vec3d p, double size, double h, double seed, Vec3d[] axes) {
            cube(m, p, size, size * 1.25, size, heat(h), seed, axes);
            cube(m, p.add(axes[1].multiply(size * .7)), size * .58, heat(h - .8), seed + 1, axes);
            for (int k = 0; k < 2; k++) {
                Vec3d side = axes[k * 2].multiply(rnd(seed + k * 2.9) < .5 ? -1 : 1);
                double s = size * (.45 + .12 * rnd(seed + k * 3.7));
                cube(m, p.add(side.multiply(size * .5)).add(axes[1].multiply(-size * .2)), s, heat(h - 1.1), seed + k + 2, axes);
            }
        }

        /** A ring of flames races across the ground to the 10-block edge, holds there for a moment and dies down. */
        private void shock(BlockFireMesh m, double a) {
            if (a < 0 || a > 1.3) return;
            double e = clamp(a / SHOCK_REACH), r = 1 + (DAMAGE_R - 1) * (1 - (1 - e) * (1 - e));
            double hs = smoothstep(a / .05) * (1 - smoothstep((a - SHOCK_REACH - .12) / .85));
            if (hs <= 0) return;
            double lean = lerp(.55, .15, smoothstep((a - SHOCK_REACH) / .25));
            for (int i = 0; i < SHOCK; i++) {
                double ang = (i + .4 * rnd(4500 + i)) / SHOCK * TAU, c = Math.cos(ang), s = Math.sin(ang), ri = r * (1 + .025 * (rnd(4501 + i * 1.3) - .5));
                double x = at.x + c * ri, z = at.z + s * ri, y = groundAt(((long) i << 20) | Math.round(ri * 4), x, z);
                flame(m, new Vec3d(x, y, z), new Vec3d(c * lean, 1, s * lean), new Flame(.95 * (.8 + .4 * rnd(4502 + i)) * (.6 + .4 * hs),
                        1.8 * hs * (.75 + .5 * rnd(4503 + i * 2.1)), 4600 + i * 7).heat(1.02 - .4 * smoothstep((a - SHOCK_REACH) / .8)).sway(.04).speed(1.6));
            }
        }

        /** Molten chunks: magma lumps thrown out in arcs that land, sit burning with a small flame, then shrink away. */
        private void chunks(BlockFireMesh m, double a) {
            if (a < 0) return;
            for (int i = 0; i < CHUNKS; i++) {
                double sd = 5000 + i * 3.1, az = (i + .6 * rnd(sd)) / CHUNKS * TAU, vh = 3.5 + 5.5 * rnd(sd + .1), vy = 7 + 6 * rnd(sd + .2), sz = .55 + .35 * rnd(sd + .3);
                Vec3d p0 = at.add(Math.cos(az) * 1.3, 1 + .8 * rnd(sd + .4), Math.sin(az) * 1.3), dir = new Vec3d(Math.cos(az), 0, Math.sin(az));
                if (Double.isNaN(landY[i])) {
                    double fl = flightTo(vy, p0.y, at.y, sz);
                    landY[i] = MeteorFx.ground(world, p0.x + dir.x * vh * fl, p0.z + dir.z * vh * fl, at.y);
                    flightT[i] = flightTo(vy, p0.y, landY[i], sz);
                }
                double age = a - .06 * rnd(sd + .5), burn = 1.5 + 1.1 * rnd(sd + .6), fl = flightT[i];
                if (age < 0) continue;
                if (age < fl) {
                    double g = Math.min(1, age / .08);
                    cube(m, arc(p0, dir, vh, vy, age), sz * g, MAGMA, sd, tumble(sd, age, 6));
                    // A short burning wake: two cooler pieces just behind, smaller each step back.
                    for (int j = 1; j <= 2; j++) {
                        double u = age - j * .035;
                        if (u < .02) continue;
                        cube(m, arc(p0, dir, vh, vy, u), sz * g * (.75 - j * .2), heat(3.2 - j * .9), sd + j, tumble(sd + j, u, 6));
                    }
                    continue;
                }
                double q = (age - fl) / burn;
                if (q >= 1) continue;
                double live = 1 - smoothstep((q - .3) / .7), s = sz * live;
                Vec3d land = arc(p0, dir, vh, vy, fl), p = new Vec3d(land.x, landY[i] + s / 2, land.z);
                cube(m, p, s, q < .72 ? MAGMA : CHAR, sd, yawAxes(rnd(sd + .8) * TAU));
                if (live > .08) flame(m, new Vec3d(p.x, landY[i] + s * .85, p.z), UP, new Flame(.48 * live, .85 * live, sd + 11).heat(.95).sway(.04));
                if (q < .12) sparkBurst(m, p, q * burn, new Sparks(5, 1.6, 2.2, .4, sd + 20).size(.12));
            }
        }
        private static double flightTo(double vy, double y0, double yg, double sz) {
            return (vy + Math.sqrt(Math.max(0, vy * vy + 2 * CHUNK_G * (y0 - yg - sz / 2)))) / CHUNK_G;
        }
        private static Vec3d arc(Vec3d p0, Vec3d dir, double vh, double vy, double u) {
            return p0.add(dir.x * vh * u, vy * u - CHUNK_G * u * u / 2, dir.z * vh * u);
        }
    }
}
