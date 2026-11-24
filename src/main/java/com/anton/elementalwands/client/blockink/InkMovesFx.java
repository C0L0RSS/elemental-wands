package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.spell.FlameSpell;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Contact debris and the breath through the striker's square tip. */
final class InkMovesFx {
    private InkMovesFx() {}

    static void slam(InkFrame f, FirstWizardEffectEntity e) {
        Vec3d from = e.getLerpedPos(f.delta());
        if (e.stage() < 2 || !f.near(from, 128)) return;
        for (int i = 1; i <= 4; i++) {
            Vec3d at = from.lerp(e.auxPoint(), i / 4.0);
            InkKit.splash(f.ink(), at.add(0, .2, 0), at.y, e.stageAge(f.delta()) / 20, 6, 3.5, .18, e.getId() + i * 11);
        }
    }

    static void leap(InkFrame f, FirstWizardEffectEntity e) {
        Vec3d at = e.getLerpedPos(f.delta());
        if (e.stage() == 3 && f.near(at, 128))
            InkKit.splash(f.ink(), at.add(0, .3, 0), at.y, e.stageAge(f.delta()) / 20, 18, 5, .22, e.getId());
    }

    static void flame(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Entity owner = f.world().getEntityById(e.ownerId());
        if (!(owner instanceof FirstWizardEntity copy)) return;
        double t = f.now(), seed = e.getId() * 3.3;
        Vec3d dir = e.dir();
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
        Vec3d mouth = TendrilMoves.flameMouth(copy.getLerpedPos(delta), dir);
        if (!f.near(mouth, 128)) return;
        Vec3d down = dir.add(0, -.12, 0).normalize();
        if (e.stage() == 0) {
            double formed = e.formed(delta);
            for (int i = 0; i < 5; i++) {
                Vec3d d = rotate(sphereDir(i, 5), UP, t * 2 + seed);
                InkKit.coolingFlame(f, mouth, d.add(dir).normalize(), .18 + .2 * formed, .3 + .4 * formed, seed + i, 1, .55);
            }
            return;
        }
        double age = e.stageAge(delta);
        double on = e.stage() == 1 ? smoothstep(age / 3) : 1 - smoothstep(age / 8);
        if (on <= .02) return;
        double reach = (FlameSpell.REACH - .8) * on, half = Math.toRadians(FlameSpell.HALF_ANGLE);
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        // Tongues fanned across the cone, each one the block-fire flame cooling to purple tips and ink.
        int tongues = 11;
        for (int i = 0; i < tongues; i++) {
            double k = (i + .5) / tongues - .5, spread = k * 2 * half * (.85 + .15 * Math.sin(t * 6 + i));
            Vec3d d = down.multiply(Math.cos(spread)).add(side.multiply(Math.sin(spread))).normalize().add(0, .05 * Math.sin(t * 5 + i * 2), 0);
            double len = reach * (.75 + .25 * noise(t * 3 + i * 1.7 + seed));
            InkKit.coolingFlame(f, mouth.add(d.multiply(.25)), d, .5 + .15 * Math.abs(k) * 4, len * .55, seed + i * 7, 1, .45);
            InkKit.coolingFlame(f, mouth.add(d.multiply(len * .45)), d.add(0, .08, 0).normalize(), .9, len * .55, seed + i * 7 + 3, .8, .3);
        }
        // Where it licks the floor: low flame and scorched ink along the cone.
        for (int i = 0; i < 6; i++) {
            double k = (i + .5) / 6, spread = (rnd(seed + i) - .5) * 2 * half;
            Vec3d at = new Vec3d(mouth.x, copy.getY() + .05, mouth.z).add(dir.multiply(Math.cos(spread) * reach * (.35 + .6 * k))).add(side.multiply(Math.sin(spread) * reach * (.35 + .6 * k)));
            InkKit.coolingFlame(f, at, UP, .35, .55 * on, seed + i * 13, .8, .4);
        }
        if (e.stage() == 2) {
            // Spent: the cooled breath hangs as ink and purple where the cone was (the cloud a bottle collects).
            for (int i = 0; i < 6; i++) {
                double u = fract(age / 20 + i / 6.0);
                Vec3d p = mouth.add(dir.multiply(2 + i * 1.1)).add(0, u * 1.2, 0);
                InkKit.cube(f.ink(), p, .35 * (1 - u), i % 2 == 0 ? InkKit.PURPLE : InkKit.INK, seed + i, tumble(seed + i, t, 1.5));
            }
        }
    }
}
