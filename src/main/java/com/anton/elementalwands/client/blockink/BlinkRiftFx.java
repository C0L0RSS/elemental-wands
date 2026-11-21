package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockspace.BlockSpaceKit;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.BlinkRiftSpell;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Blink Rift: ink pools at the arrival point (a ripple of Space's colour on it that cools to purple
 * and black), then a column of ink rises there as the copy arrives and drains away; where it left, the ink it was made
 * of runs down into a pool that sinks. Liquid all through: no slit, no glass, no stars.
 */
final class BlinkRiftFx {
    private BlinkRiftFx() {}

    /** {@link BlinkRiftSpell#POOL_KIND}: stage 0 pooling, 1 the arrival. */
    static void pool(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 128)) return;
        double seed = e.getId() * 11.3, t = f.now(), R = e.size();
        if (e.stage() == 0) {
            double formed = e.formed(delta), fill = Math.pow(formed, .6);
            InkKit.pool(f.ink(), c.add(0, .01, 0), R, fill, t, seed);
            ripple(f, c, R * fill, formed, seed, t);
            if (f.embers()) for (int i = 0; i < 5; i++) {
                double u = fract(t * 1.6 + rnd(seed + i));
                double a = rnd(seed + i * 2.2) * TAU, d = Math.sqrt(rnd(seed + i * 3.3)) * R * fill * .7;
                InkKit.cube(f.ink(), c.add(Math.cos(a) * d, .05 + u * .35 * formed, Math.sin(a) * d), .16 * Math.sin(u * Math.PI) * formed,
                        InkKit.GLOSS, seed + i, tumble(seed + i, t, 2));
            }
            return;
        }
        double age = e.stageAge(delta) / 20, drain = BlinkRiftSpell.DRAIN / 20.0;
        // The column it rises out of: up in a quarter of a second, then running back down into the pool.
        double rise = smoothstep(age / .22), sink = smoothstep((age - .25) / (drain - .3));
        column(f, c, 2.0 * rise * (1 - sink), .38 * (1 - sink * .5), seed, t);
        InkKit.pool(f.ink(), c.add(0, .01, 0), R, 1 - smoothstep((age - .2) / (drain - .2)), t, seed);
    }

    /** {@link BlinkRiftSpell#DEPART_KIND}: the ink it was made of runs down where it stood and sinks away. */
    static void depart(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 128)) return;
        double seed = e.getId() * 5.7, t = f.now(), age = e.age(delta) / 20, life = e.life() / 20.0;
        double down = smoothstep(age / .35);
        column(f, c, 1.9 * (1 - down), .34, seed, t);
        double fill = Math.min(smoothstep(age / .25), 1 - smoothstep((age - .4) / (life - .4)));
        InkKit.pool(f.ink(), c.add(0, .01, 0), e.size(), fill, t, seed);
        if (f.embers()) InkKit.splash(f.ink(), c.add(0, 1.2 * (1 - down), 0), c.y, age, 6, 1.6, .16, seed + 9);
    }

    /** A wobbling column of ink blobs from the floor to height h. */
    private static void column(InkFrame f, Vec3d c, double h, double r, double seed, double t) {
        if (h <= .05) return;
        int count = Math.max(1, (int) Math.ceil(h / (r * 1.4)));
        for (int i = 0; i < count; i++) {
            double y = (i + .5) / count * h, sway = .06 * Math.sin(t * 6 + i * 1.7 + seed);
            double s = r * (1 - .35 * i / Math.max(1, count - 1));
            InkKit.blob(f.ink(), c.add(sway, y, -sway * .6), s, InkKit.INK, i == count - 1 ? InkKit.RIM : InkKit.GLOSS, seed + i * 3, t);
        }
    }

    /**
     * Rings spreading across the pool: Space's colour (its kit's rim band) while the pool is new, cooling through
     * purple to ink as it fills. Flat cubes on the surface only.
     */
    private static void ripple(InkFrame f, Vec3d c, double radius, double formed, double seed, double t) {
        if (radius < .2) return;
        for (int ring = 0; ring < 2; ring++) {
            double u = fract(t * 1.2 + ring * .5), r = radius * (.25 + .75 * u);
            int count = (int) Math.ceil(r * 9);
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + seed;
                Vec3d p = c.add(Math.cos(a) * r, .08, Math.sin(a) * r);
                double s = .14 * (1 - u * .6);
                Vec3d[] axes = yawAxes(a);
                if (formed < .35) f.space().box(p, s * .5, .02, s * .5, axes, BlockSpaceKit.RIM, 0, 1, 1);
                else InkKit.cube(f.ink(), p, s, .04, s, InkKit.cool((formed - .35) / .65 * .75 + u * .25), seed + i, axes, 1);
            }
        }
    }
}
