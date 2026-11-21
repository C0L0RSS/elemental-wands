package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireKit;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Meteor: a knot of ink over the raised wand, flung up to form a stepped ball of fire twice the
 * player's high over the mark; its fall with tongues streaming back that cool to purple tips and ink, shedding ink
 * where the player's sheds smoke; the impact. The mark is an ink ring at the rim with a violet edge and a shadow that
 * fills in from the middle as the meteor nears. All of the mark lies flat on the floor.
 */
final class MeteorFx {
    private MeteorFx() {}

    /** Exposed cells of a unit sphere on a twelve-cell grid: the ball's stepped shell, scaled to its radius. */
    private static final List<Vec3d> SHELL = new ArrayList<>();
    private static final int CELLS = 6;

    static {
        double cell = 1.0 / CELLS;
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = -CELLS; i < CELLS; i++) for (int j = -CELLS; j < CELLS; j++) for (int k = -CELLS; k < CELLS; k++) {
            if (!in(i, j, k)) continue;
            boolean exposed = false;
            for (int[] a : around) if (!in(i + a[0], j + a[1], k + a[2])) { exposed = true; break; }
            if (exposed) SHELL.add(new Vec3d((i + .5) * cell, (j + .5) * cell, (k + .5) * cell));
        }
    }

    private static boolean in(int i, int j, int k) {
        return i >= -CELLS && i < CELLS && j >= -CELLS && j < CELLS && k >= -CELLS && k < CELLS
                && Math.sqrt((i + .5) * (i + .5) + (j + .5) * (j + .5) + (k + .5) * (k + .5)) / CELLS < .995;
    }

    /** Where the ball's centre is this many ticks into its fall, over a floor at height floor. */
    static double fallHeight(double ticks, double radius) {
        double u = clamp(ticks / MeteorSpell.FALL);
        return radius + (MeteorSpell.HEIGHT - radius) * (1 - u * u);
    }

    /** {@link MeteorSpell#KIND}: stage 0 the knot over the wand, 1 forming over the mark, 2 falling, 3 the impact. */
    static void ball(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double seed = e.getId() * 17.3, t = f.now();
        double releaseAfterLock = MeteorSpell.TIMING.release() - MeteorSpell.TIMING.aimLock();
        Vec3d centre = e.auxPoint();
        switch (e.stage()) {
            case 0 -> {
                Entity owner = f.world().getEntityById(e.ownerId());
                Vec3d feet = owner != null ? owner.getLerpedPos(delta) : e.getLerpedPos(delta).subtract(0, MeteorSpell.KNOT, 0);
                Vec3d c = feet.add(0, MeteorSpell.KNOT, 0);
                if (!f.near(c, 160)) return;
                double formed = e.formed(delta), r = .2 + .55 * Math.pow(formed, .7);
                knot(f, c, r, formed, seed, t);
            }
            case 1 -> {
                double age = e.stageAge(delta), k = clamp(age / releaseAfterLock);
                Vec3d top = centre.add(0, MeteorSpell.HEIGHT, 0), knot = e.dir();
                // The knot is flung up into the sky over the mark, a stream of ink beads, for the first half second.
                if (age < 12 && f.near(knot, 160)) {
                    double s = age / 12;
                    for (int i = 0; i < 14; i++) {
                        double u = clamp(s * 1.6 - i / 14.0 * .6);
                        if (u <= 0 || u >= 1) continue;
                        Vec3d p = knot.lerp(top, u).add(0, Math.sin(u * Math.PI) * 4, 0);
                        InkKit.cube(f.ink(), p, .55 * (1 - u * .4), i % 3 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 3));
                    }
                }
                if (!f.near(top, 200)) return;
                double r = MeteorSpell.BALL_RADIUS * (.18 + .42 * smoothstep(k));
                draw(f, top, r, smoothstep((k - .1) / .7), 0, seed, t);
            }
            case 2 -> {
                double age = e.stageAge(delta), u = clamp(age / MeteorSpell.FALL);
                // It keeps growing through the first half of its fall: the longer it forms, the bigger it ends.
                double r = MeteorSpell.BALL_RADIUS * (.6 + .4 * smoothstep(u / .5));
                Vec3d c = centre.add(0, fallHeight(age, r), 0);
                if (!f.near(c, 200)) return;
                draw(f, c, r, 1, u, seed, t);
            }
            default -> impact(f, centre, e.stageAge(delta) / 20, seed, t);
        }
    }

    /** The knot over the raised wand: ink, taking fire's colour in its heart from halfway. */
    private static void knot(InkFrame f, Vec3d c, double r, double formed, double seed, double t) {
        InkKit.blob(f.ink(), c, r, InkKit.INK, InkKit.GLOSS, seed, t);
        for (int i = 0; i < 6; i++) {
            Vec3d dir = rotate(sphereDir(i, 6), UP, t * 1.6 + seed);
            InkKit.cube(f.ink(), c.add(dir.multiply(r * 1.15)), r * .32, i % 2 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 2));
        }
        double heat = smoothstep((formed - .45) / .4);
        if (heat > .02) for (int i = 0; i < 5; i++) {
            Vec3d dir = rotate(sphereDir(i, 5), UP, -t * 1.1 + seed);
            InkKit.coolingFlame(f, c.add(dir.multiply(r * .5)), dir.add(0, .8, 0), r * .5 * heat, r * 1.1 * heat, seed + i * 3.7, .9, .45);
        }
    }

    /**
     * The ball: a stepped shell of block fire (ink where the colour has not taken it yet), hottest on its underside
     * as it falls, with tongues streaming up behind it that cool to purple and ink, and ink shed above it. colour 0
     * is all ink, 1 all fire; fall 0 to 1 how far through its fall.
     */
    private static void draw(InkFrame f, Vec3d c, double r, double colour, double fall, double seed, double t) {
        double cell = r / CELLS;
        Vec3d[] ax = yawAxes(t * .19 + seed);
        for (int i = 0; i < SHELL.size(); i++) {
            Vec3d unit = SHELL.get(i);
            Vec3d p = c.add(ax[0].multiply(unit.x * r)).add(ax[1].multiply(unit.y * r)).add(ax[2].multiply(unit.z * r));
            double s = cell * (1.02 + .14 * noise(t * 5.3 + i * 1.37));
            if (rnd(seed + i * .61) < colour) {
                double h = 2.4 - unit.y * 1.3 + (noise(t * 2.1 + i * .71) - .5) * 1.4 + .5 * fall;
                BlockFireKit.cube(f.fire(), p, s, heat(h), seed + i, ax);
            } else InkKit.cube(f.ink(), p, s, unit.y > .7 ? InkKit.RIM : rnd(seed + i * 2.3) < .2 ? InkKit.GLOSS : InkKit.INK, seed + i, ax);
        }
        if (colour < .05) return;
        // Tongues off its upper half, streaming back against the fall; their tips cool to purple and ink.
        int tongues = 16;
        for (int i = 0; i < tongues; i++) {
            Vec3d dir = rotate(sphereDir(i, tongues * 2), UP, seed);
            if (dir.y < 0) dir = new Vec3d(dir.x, -dir.y, dir.z);
            Vec3d root = c.add(dir.multiply(r * .85));
            Vec3d out = dir.add(0, 1.2 + fall * 2, 0).normalize();
            InkKit.coolingFlame(f, root, out, r * .26 * colour, r * (.55 + .7 * fall) * colour, seed + i * 7.3, 1, .4);
        }
        // Ink shed over it, where the player's sheds smoke: blobs left behind that hang and shrink.
        if (f.embers()) for (int i = 0; i < 12; i++) {
            double life = .7 + rnd(seed + i * 3.1) * .4, u = fract(t / life + rnd(seed + i * 5.3));
            double a = rnd(seed + i * 1.9) * TAU, d = r * (.2 + .7 * rnd(seed + i * 2.7));
            Vec3d p = c.add(Math.cos(a) * d, r * .7 + u * r * (1.2 + 3 * fall), Math.sin(a) * d);
            InkKit.cube(f.ink(), p, r * .16 * (1 - u) * colour, i % 4 == 0 ? InkKit.PURPLE : InkKit.INK, seed + i, tumble(seed + i, t, 2));
        }
    }

    /** The impact: a dome of fire to the rim that cools to purple and ink, a ring of flame racing out, ink thrown wide. */
    private static void impact(InkFrame f, Vec3d c, double age, double seed, double t) {
        if (!f.near(c, 200)) return;
        double R = MeteorSpell.RADIUS;
        // The ball flattens into a dome that swells out to the rim, burning, then cools through purple to ink and sinks.
        double k = clamp(age / .9);
        if (k < 1) {
            double reach = MeteorSpell.BALL_RADIUS * .8 + (R - MeteorSpell.BALL_RADIUS * .8) * smoothstep(age / .3);
            double cell = reach / CELLS * (1 - k * k);
            Vec3d[] ax = yawAxes(seed);
            for (int i = 0; i < SHELL.size(); i++) {
                Vec3d unit = SHELL.get(i);
                if (unit.y < -.05) continue;
                Vec3d p = c.add(unit.x * reach, unit.y * reach * .32 * (1 - k * .7), unit.z * reach);
                double s = cell * (1.05 + .2 * noise(t * 6 + i * 1.3));
                double cooled = k * 1.6 - .25 + rnd(seed + i * .37) * .35 - unit.y * .3;
                if (cooled < .35) BlockFireKit.cube(f.fire(), p, s, heat(3.6 - cooled * 6 - unit.y), seed + i, ax);
                else InkKit.cube(f.ink(), p, s, InkKit.cool((cooled - .35) / .65), seed + i, ax);
            }
        }
        BlockFireKit.burst(f.fire(), c.add(0, 1.5, 0), age, new BlockFireKit.Burst(R * .8, 70, 1.4, .6, seed).up(.6).smoke(false));
        // The racing ring: flames at its front, ink behind it.
        double front = R * smoothstep(age / .4), fade = 1 - smoothstep((age - .5) / 1.2);
        if (fade > 0) {
            int count = 40;
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + seed;
                Vec3d root = c.add(Math.cos(a) * front, .05, Math.sin(a) * front);
                Vec3d out = new Vec3d(Math.cos(a) * .5, 1, Math.sin(a) * .5);
                InkKit.coolingFlame(f, root, out, .7 * fade, (1.4 + rnd(seed + i) * 1.2) * fade, seed + i * 3.1, .95, .35);
            }
        }
        InkKit.splash(f.ink(), c.add(0, 1.5, 0), c.y, age, f.embers() ? 26 : 10, 9, .5, seed + 40);
    }

    /**
     * {@link MeteorSpell#MARK_KIND}: an ink ring at the rim with a violet edge, a dashed purple ring round the inner
     * part, and a shadow that fills in from the middle as the meteor nears; in the last half second the edge flashes.
     * The ring first burns in fire's colour and cools to violet as the meteor forms.
     */
    static void mark(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 160)) return;
        double seed = e.getId() * 9.1, t = f.now(), R = e.size();
        double k = e.formed(delta), left = (1 - k) * e.form() / 20.0;
        double appear = smoothstep(e.age(delta) / 6);
        // The shadow: a pool of ink spreading from the middle to the rim as the meteor nears.
        InkKit.pool(f.ink(), c.add(0, .01, 0), R * .98, .12 + .88 * Math.pow(k, 1.3), t, seed);
        double flash = left < .6 ? .5 + .5 * Math.sin(t * TAU * 6) : 0;
        int count = (int) Math.ceil(TAU * R / .62);
        double heat = 1 - smoothstep(k / .25);
        for (int i = 0; i < count; i++) {
            double a = i * TAU / count + seed;
            double rr = R * (.92 + .08 * appear);
            Vec3d p = c.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            if (InkKit.floorBelow(f.world(), p.add(0, .5, 0)) < c.y - 1) continue;      // nothing over the void past the floor's edge
            Vec3d[] axes = yawAxes(a + Math.PI / 2);
            InkKit.cube(f.ink(), p.add(0, .06, 0), .7, .12, .5, InkKit.INK, seed + i, axes, 1);
            int edge = flash > .5 ? InkKit.MAGENTA : InkKit.VIOLET;
            if (heat > .05 && rnd(seed + i * 1.3) < heat) BlockFireKit.cube(f.fire(), p.add(0, .16, 0), .5, .14, .26, BlockFireKit.ORANGE, seed + i, axes);
            else InkKit.cube(f.ink(), p.add(0, .15, 0), .64, .08, .22, i % 2 == 0 ? edge : InkKit.RIM, seed + i, axes, 1);
        }
        // The inner part, where it hits hardest: a dashed purple ring.
        double inner = MeteorSpell.INNER;
        int dashes = (int) Math.ceil(TAU * inner / .55);
        for (int i = 0; i < dashes; i += 2) {
            double a = i * TAU / dashes - t * .3;
            Vec3d p = c.add(Math.cos(a) * inner, .08, Math.sin(a) * inner);
            InkKit.cube(f.ink(), p, .5, .07, .2, flash > .5 ? InkKit.MAGENTA : InkKit.PURPLE, seed + 99 + i, yawAxes(a + Math.PI / 2), 1);
        }
    }
}
