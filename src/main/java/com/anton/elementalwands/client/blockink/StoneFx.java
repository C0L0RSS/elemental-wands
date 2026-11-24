package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.FaultlineSpell;
import com.anton.elementalwands.entity.firstwizard.spell.StoneWallSpell;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** A lintel above the caster splits into the future wall. Faultline tips three teeth down the wave heading. */
final class StoneFx {
    private StoneFx() {}

    public static final Identifier ATLAS = Identifier.of("elementalwands", "textures/misc/first_wizard_stone.png");
    /** Atlas rows (see build_stone.py). */
    static final int WALL = 0, VEINED = 1, SPIKE = 2, SPIKE_INK = 3, STAINED = 4, DUST = 5, CRACK = 6, COOLING = 7;
    /** Stone is lit by the world, but never quite lost at midnight. */
    private static final double GLOW = .45;

    /** A cube of stone cooling into ink: k 0 is the given stone row, then veined, violet grey, and the ink's purples to black. */
    static void cooling(InkFrame f, Vec3d p, double sx, double sy, double sz, int stone, double k, double seed, Vec3d[] axes) {
        if (k < .25) f.stone().box(p, sx * .5, sy * .5, sz * .5, axes, stone, 0, GLOW, 1);
        else if (k < .45) f.stone().box(p, sx * .5, sy * .5, sz * .5, axes, stone == WALL || stone == VEINED ? VEINED : SPIKE_INK, 0, GLOW, 1);
        else if (k < .6) f.stone().box(p, sx * .5, sy * .5, sz * .5, axes, COOLING, 0, .8, 1);
        else InkKit.cube(f.ink(), p, sx, sy, sz, InkKit.cool(.25 + (k - .6) / .4 * .75), seed, axes, 1);
    }

    // ── Stone Wall ───────────────────────────────────────────────────────────

    /** {@link StoneWallSpell#KIND}: stage 0 the airborne lintel, 1 standing, 2 crumbling. Its position is the line's middle; dir is half the line. */
    static void wall(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 160)) return;
        Vec3d half = e.dir();
        double reach = half.horizontalLength();
        if (reach < .5) return;
        Vec3d along = new Vec3d(half.x / reach, 0, half.z / reach), across = new Vec3d(-along.z, 0, along.x);
        double seed = e.getId() * 9.1, t = f.now(), yaw = Math.atan2(along.z, along.x);
        Vec3d[] axes = yawAxes(-yaw);
        switch (e.stage()) {
            case 0 -> {
                double formed = e.formed(delta);
                Entity owner = f.world().getEntityById(e.ownerId());
                if (owner == null) return;
                // A miniature lintel assembles across the wand. The two end blocks fly to the real wall's ends.
                Vec3d above = owner.getLerpedPos(delta).add(0, 2.8, 0);
                double lift = smoothstep(formed / .24), send = smoothstep((formed - .35) / .65);
                for (int part = -2; part <= 2; part++) {
                    Vec3d held = above.add(along.multiply(part * .64)).add(0, .4 * lift, 0);
                    Vec3d dst = c.add(along.multiply(part * reach * .5)).add(0, 2.7, 0);
                    Vec3d block = held.lerp(dst, send).add(0, Math.sin(Math.PI * send), 0);
                    cooling(f, block, .64 * lift, (.8 + .23 * Math.abs(part)) * lift, .55 * lift, VEINED, 0, seed + part, axes);
                }
            }
            case 1 -> {
                double age = e.stageAge(delta) / 20;
                // It bursts up: chips of stone and ink thrown off both faces as the rows rise.
                if (age < 1.2) for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 9; i++) {
                        double u = ((i + .5) / 9 * 2 - 1) * reach * .92;
                        Vec3d base = c.add(along.multiply(u)).add(across.multiply(side * (StoneWallSpell.HALF_THICK + .3)));
                        chips(f, base, across.multiply(side), age, seed + i * 13 + side * 101);
                    }
                }
                // Ink running down both faces from the top while it stands.
                if (f.embers()) for (int i = 0; i < 14; i++) {
                    double life = 1.4 + rnd(seed + i * 2.9) * .8, k = fract((age + rnd(seed + i * 3.7) * life) / life);
                    double side = i % 2 == 0 ? 1 : -1, u = (rnd(seed + i * 4.9 + Math.floor((age + rnd(seed + i * 3.7) * life) / life) * 7) * 2 - 1) * reach * .9;
                    Vec3d top = c.add(along.multiply(u)).add(across.multiply(side * (StoneWallSpell.HALF_THICK + .78))).add(0, StoneWallSpell.HEIGHT - .1 - k * (StoneWallSpell.HEIGHT - .4), 0);
                    InkKit.cube(f.ink(), top, .16, .34, .16, k < .15 ? InkKit.RIM : InkKit.INK, seed + i, axes, 1);
                }
            }
            default -> {
                double age = e.stageAge(delta) / 20, every = StoneWallSpell.CRUMBLE_EVERY / 20.0;
                // Each row falls as it goes, top first, its stone cooling into ink on the way down, and splats.
                for (int row = 0; row < StoneWallSpell.HEIGHT; row++) {
                    double fell = age - row * every;
                    if (fell < 0) continue;
                    double y = StoneWallSpell.HEIGHT - 1 - row + .5;
                    int count = (int) Math.ceil(reach * 2);
                    for (int i = 0; i < count; i++) for (int side = -1; side <= 1; side += 2) {
                        double k = seed + row * 311 + i * 7.3 + side * 3.1;
                        double u = ((i + .5) / count * 2 - 1) * reach;
                        Vec3d start = c.add(along.multiply(u)).add(across.multiply(side * .5)).add(0, y, 0);
                        rubble(f, start, c.y, fell, across.multiply(side), k, axes);
                    }
                }
            }
        }
    }

    /** Chips of stone thrown off a face as the wall rises, falling and cooling. age seconds. */
    private static void chips(InkFrame f, Vec3d base, Vec3d out, double age, double seed) {
        for (int i = 0; i < 3; i++) {
            double k = seed + i * 2.7, up = 4 + 3 * rnd(k), side = 1.5 + 2 * rnd(k + 1), along = (rnd(k + 2) - .5) * 1.5;
            double fly = 2 * up / 14;
            Vec3d dir = out.multiply(side).add(new Vec3d(-out.z, 0, out.x).multiply(along));
            double s = .22 + .2 * rnd(k + 3);
            if (age < fly) {
                Vec3d p = base.add(dir.multiply(age)).add(0, .2 + up * age - 7 * age * age, 0);
                cooling(f, p, s, s, s, SPIKE, age / fly * .7, k, tumble(k, age, 6));
            } else InkKit.drip(f.ink(), base.add(dir.multiply(fly)).add(0, s, 0), base.y, age - fly + .001, .7, s, k);
        }
        // Dust at the foot for the first moment.
        if (age < .5) {
            double s = .5 * (1 - age / .5) + .1;
            f.stone().box(base.add(out.multiply(.3 + age * 1.5)).add(0, .25 + age, 0), s, s * .6, s, yawAxes(seed), DUST, 0, GLOW, 1);
        }
    }

    /** One block's worth of the wall falling from start: stone cooling into ink, then a splat on the floor. fell seconds. */
    private static void rubble(InkFrame f, Vec3d start, double floor, double fell, Vec3d out, double seed, Vec3d[] axes) {
        double drop = start.y - floor - .3, fall = Math.sqrt(2 * Math.max(.05, drop) / 14);
        double spread = .6 + .8 * rnd(seed);
        if (fell < fall) {
            double k = fell / fall;
            Vec3d p = start.add(out.multiply(spread * fell)).add((rnd(seed + 1) - .5) * .3, -7 * fell * fell, (rnd(seed + 2) - .5) * .3);
            double s = .78 - .3 * k;
            cooling(f, p, s, s, s, VEINED, .1 + k * .9, seed, rotate(axes, tumble(seed, fell, 5)[0], fell * 4));
        } else {
            Vec3d at = start.add(out.multiply(spread * fall));
            InkKit.drip(f.ink(), new Vec3d(at.x, floor + .5, at.z), floor, fell - fall + .001, 1.1, .5, seed);
        }
    }

    // ── Faultline ────────────────────────────────────────────────────────────

    /** {@link FaultlineSpell#KIND}: stage 0 the teeth tilting forward, 1 the wave. Its position is the origin; dir runs to the end. */
    static void faultline(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d o = e.getLerpedPos(delta);
        if (!f.near(o, 170)) return;
        Vec3d whole = e.dir();
        double length = whole.horizontalLength();
        if (length < 1) return;
        Vec3d dir = new Vec3d(whole.x / length, 0, whole.z / length), across = new Vec3d(-dir.z, 0, dir.x);
        double seed = e.getId() * 6.3, t = f.now();
        Vec3d[] axes = yawAxes(-Math.atan2(dir.z, dir.x));
        if (e.stage() == 0) {
            double formed = e.formed(delta), age = e.age(delta);
            boolean locked = age >= FaultlineSpell.TIMING.aimLock();
            // Three raised stone teeth tilt down the real heading. Their dark stone flashes at the lock.
            for (int tooth = -1; tooth <= 1; tooth++) {
                double k = smoothstep(formed / .45);
                Vec3d p = o.add(dir.multiply(.8 + (1 - Math.abs(tooth)) * .65)).add(across.multiply(tooth * 1.1))
                        .add(0, 1.65 + .6 * k, 0);
                Vec3d[] toothAxes = basis(UP.multiply(1 - .85 * k).add(dir.multiply(.4 + k)));
                cooling(f, p, .46 * k, 1.8 * k, .46 * k, SPIKE, locked ? .48 : 0, seed + tooth, toothAxes);
            }
            return;
        }
        double age = e.stageAge(delta), front = Math.min(length, (age + 1) * FaultlineSpell.SPEED);
        int floorY = (int) Math.floor(o.y + 1e-6);
        for (int lane = 0; lane < FaultlineSpell.LANES; lane++) {
            double end = FaultlineSpell.laneEnd(FaultlineSpell.solid(f.world()), o, dir, lane, length, at -> !f.world().getBlockState(BlockPos.ofFloored(at.x, floorY - 1, at.z)).isAir());
            // A spike every block and a half, staggered lane to lane, from 2 out: the copy stays in sight behind it.
            for (double a = 2 + (lane % 2) * .75; a <= Math.min(front, end) + 1e-6; a += 1.5) {
                double since = age - a / FaultlineSpell.SPEED;
                Vec3d p = FaultlineSpell.lanePoint(o, dir, lane, a);
                double k = seed + lane * 37.1 + a * 5.3;
                p = p.add(dir.multiply((rnd(k) - .5) * .5));
                spike(f, p, since, k, t, lane % 2 == 0);
            }
            // A lane stopped short bursts against what stopped it.
            if (end < length - .6 && front >= end) {
                double since = age - end / FaultlineSpell.SPEED;
                if (since < 12) chips(f, FaultlineSpell.lanePoint(o, dir, lane, end), dir.multiply(-1), since / 20, seed + lane * 19);
            }
        }
        // Dust thrown up along the front.
        if (front < length && f.embers()) for (int i = 0; i < 7; i++) {
            double side = (i / 6.0) * 2 - 1, s = .6 + .3 * rnd(seed + i + Math.floor(age));
            Vec3d p = o.add(dir.multiply(front + .4)).add(across.multiply(side * FaultlineSpell.half(front))).add(0, .4 + .3 * fract(age * .5 + i * .3), 0);
            f.stone().box(p, s * .5, s * .35, s * .5, tumble(seed + i, t, 2), DUST, 0, GLOW, 1);
        }
    }

    /**
     * One of the player's Faultline spikes at boss size: three stepped teeth, the middle one up to
     * {@link FaultlineSpell#REACH_UP} tall. Up in two ticks, held, then cooling into ink and sinking. since is ticks
     * since it erupted.
     */
    private static void spike(InkFrame f, Vec3d p, double since, double seed, double t, boolean full) {
        if (since < 0) return;
        double rise = smoothstep(since / 2), hold = 4, cool = 10;
        double k = clamp((since - hold) / cool, 0, 1), sink = smoothstep((since - hold - cool * .5) / (cool * .5));
        double height = FaultlineSpell.REACH_UP * (.75 + .25 * rnd(seed)) * rise * (1 - sink);
        if (height < .05) {
            if (since < hold + cool + 20) InkKit.cube(f.ink(), p.add(0, .02, 0), .9 * (1 - clamp((since - hold - cool) / 20, 0, 1)), .04, .6, InkKit.INK, seed, yawAxes(rnd(seed) * TAU), 1);
            return;
        }
        Vec3d[] axes = yawAxes(rnd(seed + 1) * TAU);
        // The player's three teeth on every other lane, the middle one alone between.
        for (int tooth = full ? 0 : 1; tooth < (full ? 3 : 2); tooth++) {
            double size = tooth == 1 ? 1 : .64;
            Vec3d base = p.add(axes[0].multiply((tooth - 1) * .7)).add(axes[2].multiply(tooth == 1 ? 0 : .4));
            for (int step = 0; step < 3; step++) {
                double bottom = step / 3.0 * height * size, top = (step + 1) / 3.0 * height * size;
                double w = (.62 - step * .17) * size * 2;
                Vec3d c = base.add(axes[0].multiply(step * .08 * size)).add(0, (bottom + top) / 2, 0);
                // The top step cools first: ink runs down the spike from its tip.
                double kk = clamp(k * 1.3 + (step - 1) * .2, 0, 1);
                cooling(f, c, w, top - bottom + .02, w, step == 2 ? SPIKE_INK : SPIKE, kk, seed + tooth * 3 + step, axes);
            }
        }
        if (since < 3) f.stone().box(p.add(0, .3, 0), .7 * (1 - since / 3) + .1, .25, .7 * (1 - since / 3) + .1, axes, DUST, 0, GLOW, 1);
    }
}
