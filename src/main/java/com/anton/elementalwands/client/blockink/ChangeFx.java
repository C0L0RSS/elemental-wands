package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import net.minecraft.util.math.Vec3d;

/**
 * The ink of the change's cinematic, round the body FirstWizardRenderer draws from the same track ({@link WizardBody}):
 * ink beading out of the seam at her left shoulder while the arm is wrenched; strands of it stretching between the
 * socket and the arm as it comes away, and their drops when they snap; ink welling in the empty socket until the
 * swelling bursts; the pool the torn arm melts into, which soaks away into the floor; and ink oozing from the swelling
 * at each surge. The burst and the tentacles are {@link TendrilFx}'s. Matte ink with a little violet, in the block
 * style; nothing here is drawn outside the change.
 */
final class ChangeFx {
    private ChangeFx() {}

    /** The left arm's pivot and the middle of the socket it leaves (model pixels). */
    private static final double SHOULDER_X = 5, SHOULDER_Y = 2;

    static void draw(InkFrame f, FirstWizardEntity copy) {
        float delta = f.delta();
        double t = WizardBody.change(copy, delta);
        if (t < 0 || t > PhaseChange.BURST + 2) return;
        if (!f.near(copy.getLerpedPos(delta), 96)) return;
        AtlasMesh m = f.ink();
        double now = f.now(), seed = copy.getId() * 7.9;
        Vec3d[] axes = yawAxes(-WizardBody.yaw(copy, delta));
        double floor = copy.getLerpedPos(delta).y;
        Vec3d socket = WizardBody.place(copy, delta, WizardBody.frameOf(SHOULDER_X + .6, SHOULDER_Y - .8, 0));

        // The seam: ink beading out round the top of the arm while it strains.
        double seep = WizardBody.value("seep", t);
        if (seep > .02 && t < PhaseChange.SNAP) {
            int beads = (int) Math.round(3 + 9 * seep);
            for (int i = 0; i < beads; i++) {
                double a = i * TAU / beads + rnd(seed + i) * .6, k = seed + i * 3.3;
                Vec3d p = socket.add(WizardBody.left(WizardBody.yaw(copy, delta)).multiply(.06)).add(Math.cos(a) * .17, -.05 + Math.sin(a) * .12 * (1 + rnd(k)), Math.sin(a) * .17 * .7);
                double s = (.035 + .05 * seep * rnd(k + 1)) * (.8 + .3 * Math.sin(now * 5 + i));
                InkKit.cube(m, p, s, i % 4 == 0 ? InkKit.RIM : rnd(k + 2) < .3 ? InkKit.GLOSS : InkKit.INK, k, tumble(k, now, 1));
            }
            if (seep > .5 && f.embers()) {
                double life = 1.1, age = (now + rnd(seed)) % life;
                InkKit.drip(m, socket.add(0, -.15, 0), floor, age, life, .05, seed + Math.floor((now + rnd(seed)) / life));
            }
        }

        // The strands: from the socket to the top of the arm as it sags away, thinning as they stretch.
        double stretch = WizardBody.value("strands", t);
        if (t >= PhaseChange.TEAR && t < PhaseChange.SNAP) {
            Vec3d w = WizardBody.point("wrench", t), gap = WizardBody.point("gap", t);
            Vec3d top = WizardBody.frameOf(SHOULDER_X + gap.x, SHOULDER_Y + gap.y, 0).add(flip(WizardBody.turn(new Vec3d(1, -1.5, 0), w.x, w.y, w.z)));
            Vec3d end = WizardBody.place(copy, delta, top);
            for (int s = 0; s < 4; s++) {
                Vec3d off = new Vec3d((rnd(seed + s) - .5) * .14, (rnd(seed + s + 9) - .5) * .1, (rnd(seed + s + 4) - .5) * .14);
                Vec3d a = socket.add(off), b = end.add(off.multiply(.6));
                int beads = 7;
                for (int i = 0; i <= beads; i++) {
                    double u = i / (double) beads, sag = Math.sin(u * Math.PI) * .05 * stretch;
                    Vec3d p = a.lerp(b, u).add(0, -sag, 0);
                    double size = (.07 - .045 * stretch) * (.7 + .5 * Math.sin(u * Math.PI)) * (1 - .4 * Math.sin(u * Math.PI) * stretch);
                    InkKit.cube(m, p, size, i == beads / 2 && s % 2 == 0 ? InkKit.RIM : InkKit.INK, seed + s * 13 + i, axes);
                }
            }
        }
        // The snap: the strands' ink falls from both ends.
        if (t >= PhaseChange.SNAP && t < PhaseChange.SNAP + 20) {
            Vec3d armAt = WizardBody.place(copy, delta, WizardBody.point("arm", PhaseChange.SNAP));
            for (int i = 0; i < 6; i++) {
                Vec3d from = (i % 2 == 0 ? socket : armAt.add(0, .3, 0)).add((rnd(seed + i * 5) - .5) * .2, 0, (rnd(seed + i * 7) - .5) * .2);
                InkKit.drip(m, from, floor, (t - PhaseChange.SNAP) / 20.0 - i * .03, 1.1, .05 + .02 * rnd(seed + i), seed + 40 + i);
            }
        }

        // The socket: ink welling where the arm was, dripping, until the burst takes the flank.
        double well = WizardBody.value("socket", t);
        if (well > .02) {
            for (int i = 0; i < 5; i++) {
                double k = seed + 60 + i * 2.7, wob = .02 * Math.sin(now * 3.1 + i * 1.7);
                Vec3d p = socket.add((rnd(k) - .5) * .14, (rnd(k + 1) - .5) * .2 + wob, (rnd(k + 2) - .5) * .14);
                double s = (.08 + .07 * rnd(k + 3)) * well;
                InkKit.cube(m, p, s, s * (1.2 + .3 * Math.sin(now * 2 + i)), s, i == 0 ? InkKit.RIM : rnd(k + 4) < .3 ? InkKit.GLOSS : InkKit.INK, k, axes, 1);
            }
            if (f.embers()) {
                double life = 1.3, age = (now + rnd(seed + 1)) % life;
                InkKit.drip(m, socket.add(0, -.12, 0), floor, age, life, .06 * well, seed + 80 + Math.floor((now + rnd(seed + 1)) / life));
            }
        }

        // The melt: the arm's ink pools under it, spreading, then soaking away into the floor.
        double melt = WizardBody.value("melt", t);
        if (t >= PhaseChange.MELT_FROM - 4 && t < PhaseChange.MELTED + 40) {
            Vec3d lie = WizardBody.point("arm", Math.min(t, PhaseChange.MELT_FROM));
            Vec3d centre = WizardBody.place(copy, delta, new Vec3d(lie.x, 0, lie.z)).add(0, .005, 0);
            double soak = smoothstep((t - PhaseChange.MELTED) / 40);
            double spread = smoothstep(melt / .7) * (1 - soak);
            if (spread > .01) {
                InkKit.pool(m, centre, .2 + .38 * spread, Math.min(1, spread * 1.3) * (1 - soak), now, seed);
                if (melt > .05 && melt < .95) {
                    // Ink beading over the arm as it gives way.
                    for (int i = 0; i < 6; i++) {
                        double k = seed + 100 + i * 3.1, along = (i / 5.0 - .5) * .6;
                        Vec3d p = centre.add(WizardBody.left(WizardBody.yaw(copy, delta)).multiply(along)).add(0, .1 * (1 - melt), 0);
                        InkKit.cube(m, p, .07 * Math.sin(Math.PI * melt) * (.7 + .6 * rnd(k)), rnd(k + 1) < .3 ? InkKit.GLOSS : InkKit.INK, k, tumble(k, now, .5));
                    }
                }
            }
        }

        // The swelling: ink pressed out under the skin oozes through at each surge, and runs down her side.
        double bulge = WizardBody.value("bulge", t);
        if (bulge > .25 && t < PhaseChange.BURST) {
            double out = (4 + 4.6 * bulge) / 16;
            Vec3d flank = WizardBody.place(copy, delta, new Vec3d(out, WizardBody.MODEL_TOP - 4.6 / 16, 0));
            Vec3d left = WizardBody.left(WizardBody.yaw(copy, delta)), fwd = WizardBody.forward(WizardBody.yaw(copy, delta));
            int beads = (int) Math.round(6 * bulge);
            for (int i = 0; i < beads; i++) {
                double k = seed + 140 + i * 4.3, a = rnd(k) * TAU;
                Vec3d p = flank.add(left.multiply(.02)).add(fwd.multiply(Math.cos(a) * .2 * bulge)).add(0, Math.sin(a) * .22 * bulge, 0);
                double s = (.04 + .05 * rnd(k + 1)) * (.6 + .4 * Math.sin(now * 4 + i * 1.3));
                InkKit.cube(m, p, s, i % 3 == 0 ? InkKit.VIOLET : rnd(k + 2) < .4 ? InkKit.GLOSS : InkKit.INK, k, axes);
            }
            if (f.embers()) {
                double life = .9, age = (now + rnd(seed + 2)) % life;
                InkKit.drip(m, flank.add(left.multiply(.03)), floor, age, life, .05 + .03 * bulge, seed + 160 + Math.floor((now + rnd(seed + 2)) / life));
            }
        }
    }

    /** A model offset (pixels; y down, z back) as a change frame offset (blocks; y up, z ahead). */
    private static Vec3d flip(Vec3d px) { return new Vec3d(px.x / 16, -px.y / 16, -px.z / 16); }
}
