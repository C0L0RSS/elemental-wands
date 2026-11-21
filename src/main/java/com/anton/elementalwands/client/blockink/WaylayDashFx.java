package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.WaylayDashSpell;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Waylay Dash, from one effect entity at the start of its line ({@code aux} is the end). The tell:
 * ink runs along the floor on the line, pale streaks of wind skimming it, while wind gathers behind the copy and cools
 * to ink as it arrives. The dash: streaks of wind trailing the copy. The wake: a low wall of churning wind and ink two
 * blocks wide over a pool of ink, pale where the copy has just passed and cooling through purple to black, no higher
 * than a player's waist and mostly see-through air, so nobody is hidden behind it.
 */
final class WaylayDashFx {
    private WaylayDashFx() {}

    /** {@link WaylayDashSpell#KIND}: stage 0 the tell, 1 the dash and its wake. */
    static void draw(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d from = e.getLerpedPos(delta), to = e.auxPoint();
        Vec3d line = new Vec3d(to.x - from.x, 0, to.z - from.z);
        double length = line.length();
        if (length < .5 || !f.near(from.lerp(to, .5), 160)) return;
        Vec3d dir = line.multiply(1 / length), side = WindInk.side(dir);
        double seed = e.getId() * 6.1, t = f.now(), half = e.size();
        Entity owner = f.world().getEntityById(e.ownerId());
        if (e.stage() == 0) {
            double formed = e.formed(delta);
            tell(f, from, dir, side, length * smoothstep(formed / .75), formed, seed, t);
            if (owner != null) gather(f, owner.getLerpedPos(delta), dir, side, formed, seed, t);
            return;
        }
        double sa = e.stageAge(delta), dash = WaylayDashSpell.DASH;
        // How far along the copy has come: where it is drawn, so the wake never runs ahead of it.
        double reached = length * clamp(sa / dash);
        if (owner != null && sa < dash + 6) reached = clamp(owner.getLerpedPos(delta).subtract(from).dotProduct(dir), 0, length);
        else if (sa >= dash + 6) reached = length;
        double remain = (WaylayDashSpell.WAKE_LIFE - sa) / 20;
        double fade = smoothstep(remain / .7);
        // The tell's ink stays under the wake ahead of the copy until it arrives.
        if (reached < length) tell(f, from.add(dir.multiply(reached)), dir, side, length - reached, 1, seed, t);
        wake(f, from, dir, side, length, reached, half, sa, fade, seed, t);
        if (owner != null && sa < dash + 5) streaks(f, owner.getLerpedPos(delta), dir, side, sa, seed, t);
        // The burst of air where it set off.
        if (sa < 8) {
            double open = smoothstep(sa / 6);
            Vec3d at = from.add(0, 1, 0).subtract(dir.multiply(.4));
            Vec3d[] axes = WindInk.flat(dir);
            int count = 16;
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + seed;
                Vec3d radial = side.multiply(Math.cos(a)).add(UP.multiply(Math.sin(a)));
                double r = .5 + 1.1 * open;
                WindInk.air(f.wind(), at.add(radial.multiply(r)), .3, .3 * (1 - open) + .05, .3, open < .5 ? WindInk.GALE : WindInk.CLOUD, seed + i, axes, .8 * (1 - open));
            }
        }
    }

    /** Ink running along the floor from a point for this many blocks, pale streaks of wind skimming toward its end. */
    private static void tell(InkFrame f, Vec3d start, Vec3d dir, Vec3d side, double run, double formed, double seed, double t) {
        if (run < .1) return;
        Vec3d[] axes = WindInk.flat(dir);
        double cell = .5;
        int n = (int) Math.ceil(run / cell);
        for (int i = 0; i < n; i++) {
            double s = (i + .5) * cell, ahead = 1 - s / Math.max(run, 1e-3);
            // Narrow at its running tip, a violet meniscus each side.
            double w = .5 + .5 * smoothstep(ahead * 4);
            Vec3d p = start.add(dir.multiply(s)).add(0, .02 + .01 * Math.sin(t * 4 + i), 0);
            InkKit.cube(f.ink(), p, cell * 1.05, .04, w, rnd(seed + i * 3.3) < .2 ? InkKit.GLOSS : InkKit.INK, seed + i, axes, 1);
            for (int edge = -1; edge <= 1; edge += 2)
                InkKit.cube(f.ink(), p.add(side.multiply(edge * w * .5)).add(0, .01, 0), cell * 1.05, .06, .1, InkKit.PURPLE, seed + i + edge * 7, axes, 1);
        }
        // Streaks of wind run up the line toward its end as the colour takes it.
        int streaks = 6;
        for (int i = 0; i < streaks; i++) {
            double u = fract(t * 1.4 + i / (double) streaks + rnd(seed + i) * .2);
            double s = u * run, lane = (rnd(seed + i * 4.1) - .5) * .7;
            Vec3d p = start.add(dir.multiply(s)).add(side.multiply(lane)).add(0, .12, 0);
            double a = formed * Math.sin(u * Math.PI);
            WindInk.air(f.wind(), p, 1.2, .07, .12, i % 2 == 0 ? WindInk.GALE : WindInk.STREAK, seed + i * 5, axes, .85 * a);
        }
    }

    /** Wind gathering behind the copy while the tell runs: pale air drawn in from round it, cooling to ink as it arrives. */
    private static void gather(InkFrame f, Vec3d feet, Vec3d dir, Vec3d side, double formed, double seed, double t) {
        Vec3d c = feet.add(0, 1.0, 0).subtract(dir.multiply(.9));
        Vec3d[] axes = WindInk.flat(dir);
        // A ring behind it, tightening.
        double r = .45 + 1.2 * (1 - formed);
        int count = 14;
        for (int i = 0; i < count; i++) {
            double a = i * TAU / count + t * 3.5;
            Vec3d radial = side.multiply(Math.cos(a)).add(UP.multiply(Math.sin(a)));
            WindInk.air(f.wind(), c.add(radial.multiply(r)), .28, .14, .14, i % 4 == 0 ? WindInk.GALE : WindInk.CLOUD, seed + i, axes, .7);
        }
        // Wisps drawn in toward it from behind and the sides: wind while far, ink as they arrive.
        for (int i = 0; i < 8; i++) {
            double u = fract(t * 1.8 + i / 8.0 + rnd(seed + i * 2.3) * .3);
            double a = rnd(seed + i * 1.7) * TAU;
            Vec3d out = side.multiply(Math.cos(a) * 1.6).add(UP.multiply(Math.sin(a) * .9)).subtract(dir.multiply(1.2 + rnd(seed + i) * 1.4));
            Vec3d p = c.add(out.multiply(1 - u));
            double s = .22 * (.4 + .6 * formed) * (1 - .4 * u);
            if (u < .6) WindInk.air(f.wind(), p, s * 1.8, s, s, WindInk.STREAK, seed + i * 3, WindInk.flat(out.multiply(-1)), .7);
            else InkKit.cube(f.ink(), p, s, InkKit.cool((u - .6) / .4), seed + i * 3, tumble(seed + i, t, 3));
        }
    }

    /** Streaks of wind trailing the copy while it dashes, cooling at their tails, and gone soon after it stops. */
    private static void streaks(InkFrame f, Vec3d feet, Vec3d dir, Vec3d side, double sa, double seed, double t) {
        double keep = 1 - smoothstep((sa - WaylayDashSpell.DASH) / 5);
        if (keep <= .02) return;
        Vec3d[] axes = WindInk.flat(dir);
        for (int i = 0; i < 9; i++) {
            double lane = (rnd(seed + i * 1.9) - .5) * 1.3, up = .2 + 1.5 * rnd(seed + i * 2.7);
            double len = (1.6 + 2.4 * rnd(seed + i * 3.1)) * keep, back = .5 + len * .5;
            Vec3d p = feet.add(side.multiply(lane)).add(0, up, 0).subtract(dir.multiply(back));
            WindInk.air(f.wind(), p, len, .1, .1, i % 3 == 0 ? WindInk.GALE : WindInk.STREAK, seed + i, axes, .8 * keep);
            InkKit.cube(f.ink(), p.subtract(dir.multiply(len * .5 + .2)), .16 * keep, InkKit.cool(.4 + .4 * rnd(seed + i)), seed + i + 30, axes);
        }
    }

    /**
     * The wake from the start to `reached`: a pool of ink with violet edges, and over it churning air and blobs of ink
     * under a block high. Each stretch starts in Wind's colours where the copy has just passed and cools to ink.
     */
    private static void wake(InkFrame f, Vec3d from, Vec3d dir, Vec3d side, double length, double reached, double half, double sa,
            double fade, double seed, double t) {
        if (reached < .1 || fade <= .01) return;
        double step = .5;
        int n = (int) Math.ceil(reached / step);
        Vec3d[] axes = WindInk.flat(dir);
        for (int i = 0; i < n; i++) {
            double s = (i + .5) * step;
            // Seconds since the copy passed here.
            double since = Math.max(0, (sa - s / length * WaylayDashSpell.DASH) / 20);
            double cool = clamp(since / 2.4);
            double rise = smoothstep(since / .15) * fade;
            Vec3d p = from.add(dir.multiply(s));
            double w = half * 2 * (.9 + .1 * Math.sin(t * 3 + i)) * rise;
            if (w > .05) {
                InkKit.cube(f.ink(), p.add(0, .025, 0), step * 1.05, .05, w, rnd(seed + i * 1.3) < .25 ? InkKit.GLOSS : InkKit.INK, seed + i, axes, 1);
                for (int edge = -1; edge <= 1; edge += 2)
                    InkKit.cube(f.ink(), p.add(side.multiply(edge * w * .5)).add(0, .04, 0), step * 1.05, .08, .14, InkKit.PURPLE, seed + i + edge * 5, axes, 1);
            }
            // The churn: air and ink rolling up off the floor and back down, under a block high.
            for (int j = 0; j < 2; j++) {
                double k = seed + i * 3.7 + j * 11.1;
                double u = fract(t * (1.1 + .5 * rnd(k)) + rnd(k + 1));
                double lane = (rnd(k + 2) - .5) * half * 1.7;
                Vec3d q = p.add(side.multiply(lane + .2 * Math.sin(u * TAU))).add(0, .12 + .7 * Math.sin(u * Math.PI), 0);
                double size = (.28 + .2 * rnd(k + 3)) * rise * (1 - .3 * u);
                if (size < .03) continue;
                if (rnd(k + 4) > cool) WindInk.air(f.wind(), q, size * 1.5, size, size, j == 0 ? WindInk.CLOUD : WindInk.STREAK, k, tumble(k, t, 2.5), .5);
                else InkKit.cube(f.ink(), q, size * .8, InkKit.cool(.3 + .7 * cool), k, tumble(k, t, 2.5));
            }
        }
    }
}
