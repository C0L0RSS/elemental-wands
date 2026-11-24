package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;

/**
 * What floats round the copy itself: its skin is the player's own and left alone (the developer, Nov 18), so what shows
 * it is a copy is a loose cloud of ink motes, with a little violet, drifting slowly round it and up off its body, in the
 * block style: sparse enough that the skin is plainly seen, enough of them to notice at fight distance. And, while a
 * spell forms, ink rising off its wand arm to where the spell gathers. The model is FirstWizardRenderer's; phase two's
 * legs and wound are {@link TendrilFx}'s.
 */
final class CopyFx {
    private CopyFx() {}

    /** Motes at a time (with full particles; a third of them when particles are reduced). */
    private static final int MOTES = 18;

    static void draw(InkFrame f, FirstWizardEntity copy) {
        float delta = f.delta();
        Vec3d feet = copy.getLerpedPos(delta);
        if (!f.near(feet, 96) || copy.isRemoved()) return;
        if (CopyForming.formed(copy, delta) < 1) return; // still being poured into shape: the intro's EndSkyFx draws its ink
        double seed = copy.getId() * 3.1, t = f.now();
        boolean leftHanded = copy.getMainArm() == Arm.LEFT;
        double age = copy.castAge(delta);
        boolean forming = age >= 0 && age < copy.castRelease();
        double lift = WizardBody.lift(copy, delta);
        Vec3d body = feet.add(0, lift, 0);

        // Motes: each rises from somewhere on the body, drifting outward and slowly round it, and shrinks away above it.
        int motes = f.embers() ? MOTES : MOTES / 3;
        for (int i = 0; i < motes; i++) {
            double life = 2.6 + rnd(seed + i * 4.3) * 1.4, phase = t / life + rnd(seed + i * 6.1), u = fract(phase);
            double k = seed + i * 7.7 + Math.floor(phase) * 13.1;      // a new path each time round
            double a = rnd(k) * TAU + t * (.25 + .2 * rnd(k + 1)) * (i % 2 == 0 ? 1 : -1);
            double r = .36 + .22 * rnd(k + 2) + .35 * u;
            double y = .15 + 1.6 * rnd(k + 3) + .9 * u;
            Vec3d p = body.add(Math.cos(a) * r, y, Math.sin(a) * r);
            double size = (.07 + .06 * rnd(k + 4)) * Math.sin(Math.PI * Math.min(1, u * 1.15));
            double pick = rnd(k + 5);
            int row = pick < .14 ? InkKit.VIOLET : pick < .3 ? InkKit.RIM : pick < .42 ? InkKit.GLOSS : InkKit.INK;
            InkKit.cube(f.ink(), p, size, row, seed + i, tumble(k, t, 1.2));
        }
        // Ink rising off the wand arm to where the spell gathers.
        Vec3d focus = copy.focus();
        if (forming && focus != null) {
            double k = Math.min(1, age / 6);
            Vec3d hand = WizardBody.castPoint(copy, delta, new Vec3d(leftHanded ? .36 : -.36, 2.2, .10));
            InkKit.strand(f.ink(), hand, focus, t, (int) Math.round(10 * k) + 2, .26, seed + 50);
            InkKit.strand(f.ink(), WizardBody.castPoint(copy, delta, new Vec3d(0, 1.1, 0)), focus, t + .3, 4, .14, seed + 70);
        }
        // The change's ink (the seam, the strands, the socket, the melting arm, the swelling), then phase two's tentacles.
        ChangeFx.draw(f, copy);
        TendrilFx.draw(f, copy);
    }
}
