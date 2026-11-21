package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.SkyShearSpell;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Sky Shear: three crescents of ink forming stacked over the copy's head and taking Wind's pale
 * colour, then each one dropping to the floor and running along it, 14 blocks wide and under a block high. Each has a
 * solid gale rim at its front, see-through air behind it and a band of ink behind that, over a flat ink shadow, so it
 * reads low and wide on the pale floor; echoes behind it cool through violet and purple to ink and splat on the floor,
 * and at the end of its run it falls apart into ink.
 */
final class SkyShearFx {
    private SkyShearFx() {}

    /** {@link SkyShearSpell#FORM_KIND}: stage n means n have left; the rest grow stacked over the head. */
    static void form(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Entity owner = f.world().getEntityById(e.ownerId());
        Vec3d feet = owner != null ? owner.getLerpedPos(delta) : e.getLerpedPos(delta).subtract(0, SkyShearSpell.STACK, 0);
        if (!f.near(feet, 160)) return;
        Vec3d fwd = owner != null ? owner.getRotationVec(delta).multiply(1, 0, 1) : new Vec3d(0, 0, 1);
        if (fwd.lengthSquared() < 1e-6) fwd = new Vec3d(0, 0, 1);
        fwd = fwd.normalize();
        double formed = e.formed(delta), t = f.now(), seed = e.getId() * 9.1;
        // Ink gathers first; Wind's colour takes it from a fifth of the way in.
        double colour = smoothstep((formed - .2) / .5);
        for (int i = Math.max(0, e.stage()); i < SkyShearSpell.COUNT; i++) {
            // The lowest forms first; each above it a little behind.
            double grow = Math.pow(clamp(formed * 1.15 - i * .08), .7);
            double half = .3 + 1.3 * grow;
            Vec3d c = feet.add(0, SkyShearSpell.STACK + i * SkyShearSpell.STACK_STEP + .04 * Math.sin(t * 4 + i * 2.1), 0);
            crescent(f, c, fwd, half, .18 + .2 * grow, colour, 0, seed + i * 31, t, false);
        }
    }

    /** {@link SkyShearSpell#KIND}: one crescent from where it left, along the floor, then falling apart into ink. */
    static void flight(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d origin = e.auxPoint(), vel = e.dir();
        if (vel.lengthSquared() < 1e-8) return;
        Vec3d dir = vel.normalize();
        double length = e.size(), age = e.age(delta), travelled = age * SkyShearSpell.SPEED, d = Math.min(length, travelled);
        Vec3d front = origin.add(dir.multiply(d));
        if (!f.near(front, 160)) return;
        double seed = e.getId() * 4.3, t = f.now();
        double k = clamp((travelled - length) / (SkyShearSpell.DISSOLVE * SkyShearSpell.SPEED));
        // It drops from over the copy's head to the floor as it leaves.
        double drop = 1 - smoothstep(age / 3);
        double half = SkyShearSpell.halfWidth(d);
        if (k < 1) {
            crescent(f, front.add(0, drop * (SkyShearSpell.STACK + .4), 0), dir, half, SkyShearSpell.HEIGHT * (1 - .5 * drop), 1, k, seed, t, drop < .5);
            // Echoes behind it, each lower and cooler, until the last is only ink on the floor.
            for (int echo = 1; echo <= 3; echo++) {
                double de = d - echo * 1.25;
                if (de < .3) break;
                echo(f, origin.add(dir.multiply(de)), dir, SkyShearSpell.halfWidth(de) * (1 - .07 * echo), echo, k, seed + echo * 17, t);
            }
        }
        if (!f.embers()) return;
        // Ink shaken off it splats along its path.
        double step = 1.5;
        for (int j = 0; j < 14; j++) {
            double s = (Math.floor(d / step) - j) * step;
            if (s < 1) break;
            double since = (age - s / SkyShearSpell.SPEED) / 20;
            for (int n = 0; n < 2; n++) {
                double ks = seed + s * 3.1 + n * 7.7, side = (rnd(ks) - .5) * 2 * SkyShearSpell.halfWidth(s) * .85;
                Vec3d p = origin.add(dir.multiply(s - SkyShearSpell.lag(side, SkyShearSpell.halfWidth(s)))).add(WindInk.side(dir).multiply(side));
                // Shed from low on it, so it falls as a short drop and spreads, not a long streak.
                InkKit.drip(f.ink(), p.add(0, .14 + .1 * rnd(ks + 1), 0), origin.y, since, .9, .16 + .08 * rnd(ks + 2), ks);
            }
        }
        // Spent: it comes apart into blobs of ink that splat on the floor.
        if (travelled > length) {
            double a = (travelled - length) / SkyShearSpell.SPEED / 20;
            Vec3d side = WindInk.side(dir);
            for (int n = -2; n <= 2; n++) {
                double v = n / 2.0 * half * .8;
                Vec3d p = front.add(side.multiply(v)).subtract(dir.multiply(SkyShearSpell.lag(v, half))).add(0, .4, 0);
                InkKit.splash(f.ink(), p, origin.y, a, 4, 2.2, .2, seed + n * 13);
            }
        }
    }

    /**
     * A crescent lying flat, convex toward fwd, its tips trailing: c is the middle of its front at its foot. A solid
     * rim along the front, see-through air behind it, a band of ink behind that, and (on the floor) a flat ink shadow.
     * colour 0 draws it all in ink, 1 in Wind's colours. k 0 to 1 takes it apart from the tips in and cools it to ink.
     */
    private static void crescent(InkFrame f, Vec3d c, Vec3d fwd, double half, double height, double colour, double k,
            double seed, double t, boolean shadow) {
        Vec3d side = WindInk.side(fwd);
        int n = (int) clamp(Math.round(half * 2.4), 7, 19);
        for (int i = 0; i < n; i++) {
            double v = (i + .5) / n * 2 - 1, lateral = v * half;
            double own = clamp(k * 1.6 - (1 - Math.abs(v)) * .6), keep = 1 - smoothstep(own);
            if (keep <= .02) continue;
            double lag = SkyShearSpell.lag(lateral, half);
            Vec3d p = c.add(side.multiply(lateral)).subtract(fwd.multiply(lag));
            Vec3d tangent = side.multiply(half).subtract(fwd.multiply(2 * SkyShearSpell.SWEEP * v)).normalize();
            Vec3d normal = UP.crossProduct(tangent);
            Vec3d[] axes = WindInk.flat(tangent);
            double seg = half * 2 / n * 1.18 / Math.max(.4, Math.abs(tangent.dotProduct(side)));
            double h = height * (.4 + .6 * Math.pow(Math.cos(v * Math.PI / 2), .6)) * keep;
            double ripple = 1 + .08 * Math.sin(t * 9 + i * 1.3 + seed);
            boolean wind = rnd(seed + i * 2.7) < colour && own < .35;
            // The rim: a solid pale strip at the front.
            Vec3d rim = p.add(0, h * .5 * ripple, 0);
            if (wind) WindInk.air(f.wind(), rim, seg, h * ripple, .2, WindInk.GALE, seed + i, axes, 1);
            else InkKit.cube(f.ink(), rim, seg, h * ripple, .2, own > 0 ? InkKit.cool(own) : InkKit.RIM, seed + i, axes, 1);
            // Air behind it, see-through, so a body behind the crescent still shows.
            Vec3d body = p.subtract(normal.multiply(.42)).add(0, h * .42, 0);
            if (wind) WindInk.air(f.wind(), body, seg * .97, h * .82, .6, i % 3 == 1 ? WindInk.STREAK : WindInk.CLOUD, seed + i + 40, axes, .55 * keep);
            // The band of ink it is made of, behind: where Wind's colour cools first.
            Vec3d band = p.subtract(normal.multiply(.92)).add(0, h * .28, 0);
            InkKit.cube(f.ink(), band, seg * .94, h * .56, .42, InkKit.cool(.25 + .5 * own + .25 * (1 - colour)), seed + i + 80, axes, 1);
            // A flat ink shadow on the floor under it, dark against the pale stone.
            if (shadow) InkKit.cube(f.ink(), p.subtract(normal.multiply(.55)).add(0, .02, 0), seg, .03, 1.15, rnd(seed + i * 5.1) < .25 ? InkKit.GLOSS : InkKit.INK, seed + i + 120, axes, 1);
        }
    }

    /** A fainter copy trailing the crescent: lower and cooler with each echo, the last only ink lying on the floor. */
    private static void echo(InkFrame f, Vec3d c, Vec3d fwd, double half, int echo, double k, double seed, double t) {
        Vec3d side = WindInk.side(fwd);
        int n = (int) clamp(Math.round(half * 1.8), 5, 15);
        // The first still stands a little; the later ones lie on the floor as ink.
        double height = SkyShearSpell.HEIGHT * (echo == 1 ? .4 : .1 / (echo - 1));
        for (int i = 0; i < n; i++) {
            double v = (i + .5) / n * 2 - 1, lateral = v * half;
            double own = clamp(k * 1.6 - (1 - Math.abs(v)) * .6 + echo * .1), keep = 1 - smoothstep(own);
            if (keep <= .02) continue;
            Vec3d p = c.add(side.multiply(lateral)).subtract(fwd.multiply(SkyShearSpell.lag(lateral, half)));
            Vec3d tangent = side.multiply(half).subtract(fwd.multiply(2 * SkyShearSpell.SWEEP * v)).normalize();
            Vec3d[] axes = WindInk.flat(tangent);
            double seg = half * 2 / n * 1.1 / Math.max(.4, Math.abs(tangent.dotProduct(side)));
            double h = Math.max(.04, height * (.4 + .6 * Math.cos(v * Math.PI / 2)) * keep);
            double width = .5 * keep * (.8 + .4 * rnd(seed + i));
            InkKit.cube(f.ink(), p.add(0, h * .5 + .01, 0), seg * keep, h, width, InkKit.cool(.25 + echo * .25), seed + i, axes, 1);
            // Wisps of wind still peeling off the first echo.
            if (echo == 1 && i % 3 == 0 && f.embers())
                WindInk.air(f.wind(), p.add(0, h + .15 + .1 * Math.sin(t * 7 + i), 0), seg * .7, .12, .25, WindInk.CLOUD, seed + i + 9, axes, .45 * keep);
        }
    }
}
