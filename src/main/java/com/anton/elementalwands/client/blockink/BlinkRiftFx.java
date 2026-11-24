package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.BlinkRiftSpell;
import net.minecraft.util.math.Vec3d;

/** Three upright folds announce the arrival. The copy leaves a collapsing ink column behind it. */
final class BlinkRiftFx {
    private BlinkRiftFx() {}

    /** {@link BlinkRiftSpell#POOL_KIND}: stage 0 pooling, 1 the arrival. */
    static void pool(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 128)) return;
        double seed = e.getId() * 11.3, t = f.now(), R = e.size();
        if (e.stage() == 0) {
            double k = e.formed(delta);
            // Fold upright strips of ink shut around the arrival, leaving the player's view of the copy open.
            for (int i = 0; i < 3; i++) {
                double a = i * TAU / 3 + k * 1.2;
                Vec3d strip = c.add(Math.cos(a) * (.8 - .55 * k), 1.15, Math.sin(a) * (.8 - .55 * k));
                InkKit.cube(f.ink(), strip, .24, .4 + 1.6 * k, .24, InkKit.RIM, seed + i, yawAxes(a), 1);
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

}
