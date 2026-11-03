package com.anton.elementalwands.client.blockfire;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.registry.ModSpellBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Dragon's Pyre (art/fire/blockfire/scenes/pyre.js): five block-fire columns rush 40 blocks along the ground,
 * each finding its own footing with the server's rule, and leave a carpet of flames a few inches high on every
 * PYRE_FLAME cell for its five seconds. The scheduler owns all damage, coals and flame blocks.
 */
final class PyreFx implements BlockFireEffects.Fx {
    private static final int TPS = 20, WAVE = 40, GROUND = 100, MAX_DELAY = 4;
    private static final double WALL_H = 2.8, FADE = .6;
    private static final double[] HEIGHTS = {.84, .93, 1, .93, .84};
    private static final double END = (WAVE - 1) / (double) TPS;

    private record Cell(BlockPos pos, double seed, double lit, double out, boolean curl) {}

    private final ClientWorld world;
    private final int caster;
    private final double start;
    private final Vec3d origin, forward, flat, right, lean;
    /** Surface height of each slice and half-block lane, filled on first use; NaN where the server finds no ground. */
    private final double[][] ground = new double[WAVE + 2][9];
    private final boolean[][] known = new boolean[WAVE + 2][9];
    private List<Cell> cells;

    PyreFx(ClientWorld world, int caster, Vec3d origin, Vec3d forward, double now) {
        this.world = world; this.caster = caster; this.origin = origin; this.forward = forward.normalize(); this.start = now;
        Vec3d r = this.forward.crossProduct(UP);
        right = r.lengthSquared() < .001 ? X : r.normalize();
        Vec3d f = new Vec3d(this.forward.x, 0, this.forward.z);
        flat = f.lengthSquared() < 1e-6 ? UP.crossProduct(right).normalize() : f.normalize();
        lean = flat.multiply(.16).add(UP).normalize();
    }

    /** The server's footing rule: the first solid block from the slice point down three blocks. */
    private double groundY(int d, int lane) {
        if (d < 1 || d > WAVE) return Double.NaN;
        if (!known[d][lane]) {
            known[d][lane] = true;
            ground[d][lane] = Double.NaN;
            BlockPos base = BlockPos.ofFloored(origin.add(forward.multiply(d)).add(right.multiply(lane * .5 - 2)));
            for (int y = 0; y >= -3; y--) {
                BlockPos p = base.up(y);
                if (world.getBlockState(p).isSolidBlock(world, p)) { ground[d][lane] = p.getY() + 1; break; }
            }
        }
        return ground[d][lane];
    }
    /** Ground under a moving column at distance d, blended between slices like the entity's interpolation. */
    private double laneGround(double d, int lane) {
        int i = (int) Math.floor(d);
        double a = groundY(i, lane), b = groundY(i + 1, lane);
        if (Double.isNaN(a)) return b;
        if (Double.isNaN(b)) return a;
        return lerp(a, b, smoothstep(d - i));
    }
    /** A point d blocks along the front's path, side blocks to its right, at height y. */
    private Vec3d at(double d, double side, double y) {
        Vec3d p = origin.add(forward.multiply(d)).add(right.multiply(side));
        return new Vec3d(p.x, y, p.z);
    }
    private static double front(double a) { return 1 + TPS * clamp(a, 0, END); }
    private static double wallEnv(double a) {
        if (a < 0 || a > END + .3) return 0;
        return smoothstep(a / .1) * (1 - smoothstep((a - END) / .3));
    }

    @Override public boolean draw(BlockFireEffects.Frame f) {
        double a = f.now() - start;
        if (a > (WAVE - 1 + GROUND) / (double) TPS + .2) return false;
        if (!BlockFireEffects.near(f, origin.add(flat.multiply(20)), 128)) return true;
        BlockFireMesh m = f.m();
        double t = f.t();
        ignition(f, a);
        wall(m, a, t);
        wake(m, a);
        spill(m, a);
        carpet(m, a, t);
        return true;
    }

    /** A short ignition at the wand tip so the wall has a visible cause. */
    private void ignition(BlockFireEffects.Frame f, double a) {
        double age = a + .04;
        if (age < 0 || age >= .24 || !(world.getEntityById(caster) instanceof LivingEntity c)) return;
        puff(f.m(), BlockFireEffects.tip(c, f.delta()), age, .2 * Math.pow(1 - age / .24, .8), 31, 3.6, 6);
    }

    private void wall(BlockFireMesh m, double a, double t) {
        double env = wallEnv(a);
        if (env <= 0) return;
        double d = front(a), wide = .45 + .55 * env;
        double[] ys = new double[5];
        for (int j = -2; j <= 2; j++) {
            int lane = (j + 2) * 2;
            double y = laneGround(d, lane);
            ys[j + 2] = y;
            if (Double.isNaN(y)) continue;
            double h = WALL_H * HEIGHTS[j + 2], H = h * env * (.94 + .12 * noise(t * 3.1 + j * 5.3)), seed = 100 + j * 13;
            // Outer body: a broad column that cools to ember at the crown; inside it a shorter, brighter core.
            flame(m, at(d, j, y), lean, new Flame(.98 * wide, H, seed).count(7).heat(1.08).speed(1.4).sway(.05).flicker(.6).t(t));
            flame(m, at(d + .08, j, y), lean, new Flame(.66 * wide, H * .52, seed + 5).count(4).heat(1.35).speed(1.7).sway(.03).flicker(.8).t(t));
        }
        // Lower tongues in the seams keep the wall closed without making it one slab.
        for (int g = 0; g < 4; g++) {
            double ya = ys[g], yb = ys[g + 1];
            if (Double.isNaN(ya) || Double.isNaN(yb)) continue;
            double len = WALL_H * (HEIGHTS[g] + HEIGHTS[g + 1]) * .31 * env;
            flame(m, at(d - .12, g - 1.5, Math.max(ya, yb)), lean, new Flame(.58 * wide, len, 170 + g * 11).count(3).heat(1.15).speed(1.5).sway(.04).t(t));
        }
    }

    /** Pieces left behind the moving wall: born inside a column, then shrink to nothing within a few blocks. */
    private void wake(BlockFireMesh m, double a) {
        double life = .3, dt = .05;
        if (a < 0 || a > END + life) return;
        for (int j = -2; j <= 2; j++) {
            int lane = (j + 2) * 2;
            int first = (int) Math.max(1, Math.ceil((a - life) / dt)), last = (int) Math.floor(Math.min(a, END) / dt);
            for (int e = first; e <= last; e++) {
                double te = e * dt, age = a - te, envE = wallEnv(te);
                if (age < 0 || age >= life || envE <= 0) continue;
                double k = 500 + j * 97 + e * 3.1, l = age / life, hf = .12 + .6 * rnd(k + 1), h = WALL_H * HEIGHTS[j + 2];
                double x0 = front(te) - .15 + (rnd(k) - .5) * .3, gy = laneGround(x0, lane);
                if (Double.isNaN(gy)) continue;
                double s = (.3 + .2 * rnd(k + 3)) * (1 - .65 * hf) * Math.pow(1 - l, 1.15) * Math.min(1, envE * 1.4);
                cube(m, at(x0, j + (rnd(k + 2) - .5) * .6, gy + hf * h * envE + .45 * l), s, heat(2.9 - 2.7 * l), k, tumble(k, age, 4));
            }
        }
    }

    /** When the front stops at 40 blocks, the collapsing columns throw their last pieces a block or two on. */
    private void spill(BlockFireMesh m, double a) {
        for (int j = -2; j <= 2; j++) for (int i = 0; i < 3; i++) {
            double k = 700 + j * 31 + i * 7.7, from = END - .04 + rnd(k) * .2, age = a - from, life = .5;
            if (age < 0 || age >= life) continue;
            double l = age / life, xs = front(from), env = Math.max(.35, wallEnv(from)), gy = laneGround(xs, (j + 2) * 2);
            if (Double.isNaN(gy)) continue;
            double h = WALL_H * HEIGHTS[j + 2], r = 1.6 * (1 - Math.pow(1 - l, 2)), s = (.28 + .18 * rnd(k + 5)) * Math.pow(1 - l, 1.1);
            Vec3d dir = new Vec3d(1 + rnd(k + 2) * .6, .25 + rnd(k + 3) * .6, (rnd(k + 4) - .5) * .7).normalize();
            double y = Math.max(gy + (.25 + .55 * rnd(k + 1)) * h * env + dir.y * r - 1.3 * l * l, gy + s / 2);
            cube(m, at(xs + dir.x * r, j + dir.z * r, y), s, heat(3 - 2.9 * l), k, tumble(k, age, 5));
        }
    }

    /** The floor cells, mirrored from the scheduler: each lights after its lane's fan-out delay and ends with its slice. */
    private List<Cell> cells() {
        if (cells != null) return cells;
        Map<BlockPos, Cell> found = new LinkedHashMap<>();
        for (int k = 0; k < WAVE; k++) {
            for (int lane = 0; lane <= 8; lane++) {
                double w = lane * .5 - 2, y = groundY(k + 1, lane);
                if (Double.isNaN(y)) continue;
                BlockPos column = BlockPos.ofFloored(origin.add(forward.multiply(k + 1)).add(right.multiply(w)));
                BlockPos cell = new BlockPos(column.getX(), (int) y, column.getZ());
                int delay = Math.min(MAX_DELAY, (int) Math.round(Math.abs(w) * 2));
                double seed = cell.getX() * 31.7 + cell.getZ() * 7.3 + .5;
                Cell old = found.get(cell);
                double lit = (k + delay) / (double) TPS;
                if (old == null || old.lit > lit)
                    found.put(cell, new Cell(cell, seed, lit, (k + GROUND) / (double) TPS, rnd(seed + .9) < .16));
            }
        }
        cells = new ArrayList<>(found.values());
        return cells;
    }

    private void carpet(BlockFireMesh m, double a, double t) {
        for (Cell c : cells()) {
            if (a < c.lit || a >= c.out || !world.getBlockState(c.pos).isOf(ModSpellBlocks.PYRE_FLAME)) continue;
            double env = smoothstep((a - c.lit) / .12) * smoothstep((c.out - a) / FADE);
            if (env > .01) carpetCell(m, c, t, env);
        }
    }

    /**
     * One cell of floor fire. The bed is the flame sheet itself, 2-3 inches thick and a little larger than its block,
     * so neighbours overlap into one ragged sheet. On it one flamelet (its top 4-8 inches up) grows, flickers taller
     * and shorter, and shrinks to nothing before it reappears somewhere else in the cell.
     */
    private static void carpetCell(BlockFireMesh m, Cell c, double t, double env) {
        double cx = c.pos.getX() + .5, cz = c.pos.getZ() + .5, y = c.pos.getY(), s = c.seed, cool = 1 - env;
        // Alternate cells sit a step higher, so overlapping tops never fight; the flicker is in thickness.
        double lift = Math.floorMod(c.pos.getX() + c.pos.getZ(), 2) == 1 ? .018 : 0;
        double bh = (.044 + lift + .014 * noise(t * 3.7 + s)) * env, grow = .4 + .6 * env;
        double bw = (.96 + .14 * rnd(s + .3) + .05 * noise(t * 2.3 + s * 1.7)) * grow, bd = (.96 + .14 * rnd(s + .35) + .05 * noise(t * 2.1 + s * 2.3)) * grow;
        cube(m, new Vec3d(cx + (rnd(s + .1) - .5) * .14, y + bh / 2, cz + (rnd(s + .2) - .5) * .14), bw, bh, bd,
                heat(.95 - cool * 1.2 + 1.2 * noise(t * 2.6 + s * 3)), s, yawAxes((rnd(s + .4) - .5) * .4));
        double per = .42 + .36 * rnd(s + .5), ph = t / per + rnd(s + .6), life = Math.floor(ph), u = ph - life, k = s + life * 13.7;
        double g = Math.pow(Math.sin(u * Math.PI), .6) * env, tw = (.18 + .1 * rnd(k)) * g, th = (.08 + .05 * rnd(k + 1)) * g * (.7 + .5 * noise(t * 7.3 + k));
        if (tw > .02) cube(m, new Vec3d(cx + (rnd(k + 2) - .5) * .55, y + bh * .8 + th / 2, cz + (rnd(k + 3) - .5) * .55), tw, th, tw * (.8 + .3 * rnd(k + 5)),
                heat(3.7 - cool * 2 - u * 1.5), s + 1, yawAxes(rnd(k + 4) * TAU));
        if (m.embers && c.curl) {
            double cp = .7 + .4 * rnd(s + .7), cu = fract(t / cp + rnd(s + .8)), ck = s + Math.floor(t / cp + rnd(s + .8)) * 5.1, cs = .07 * (1 - cu) * env;
            if (cs > .012) cube(m, new Vec3d(cx + (rnd(ck) - .5) * .6, y + .06 + .14 * cu, cz + (rnd(ck + 1) - .5) * .6), cs, heat(3.2 - cu * 2.4), s + 2, tumble(ck, t, 4));
        }
    }

    /** The hit: a short tumbling burst on the body. The burning that follows is vanilla fire. */
    record Hit(Vec3d at, Vec3d dir, double start) implements BlockFireEffects.Fx {
        @Override public boolean draw(BlockFireEffects.Frame f) {
            double age = f.now() - start;
            if (age > 1.2) return false;
            if (!BlockFireEffects.near(f, at, 96)) return true;
            Vec3d flatDir = new Vec3d(dir.x, 0, dir.z);
            Vec3d c = at.subtract(flatDir.lengthSquared() < 1e-6 ? Vec3d.ZERO : flatDir.normalize().multiply(.25));
            burst(f.m(), c, age, new Burst(1.3, 14, .36, .5, BlockFireEffects.seed(at, start) + 41).up(.5).smoke(false));
            return true;
        }
    }
}
