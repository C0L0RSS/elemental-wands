package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import net.minecraft.util.math.Vec3d;

/** The torn copy's three articulated ink limbs. Every link is a flat-faced, square-ended Minecraft cuboid. */
final class TendrilFx {
    private TendrilFx() {}

    static final int LINKS = 8;
    private static final TendrilPose REST = TendrilPose.pick(new TendrilRest());

    record Frame(Vec3d body, Vec3d wound, Vec3d fwd, Vec3d out, double floor) {}

    static void draw(InkFrame f, FirstWizardEntity copy) {
        if (!copy.torn()) return;
        float delta = f.delta();
        double change = WizardBody.change(copy, delta);
        double burst = change < 0 ? 1e9 : change - PhaseChange.GROW_FROM;
        if (burst < 0) return;

        Vec3d feet = copy.getLerpedPos(delta);
        double yaw = WizardBody.yaw(copy, delta);
        Frame frame = new Frame(feet.add(0, WizardBody.lift(copy, delta), 0), WizardBody.wound(copy, delta),
                WizardBody.forward(yaw), WizardBody.left(yaw), InkKit.floorBelow(f.world(), feet.add(0, .5, 0)));
        TendrilMoves cast = new TendrilMoves(f, copy, frame, change);
        TendrilRecovery recovery = TendrilRecovery.sample(copy, f.world().getTime() + (double) delta, cast.age());
        double seed = copy.getId() * 5.3;
        for (int arm = 0; arm < 3; arm++) {
            double grown = smoothstep((burst - arm * 2) / 10);
            if (grown <= 0) continue;
            double crawl = f.now() * REST.crawl(arm) * TAU + seed + arm;
            double quest = f.now() * REST.quest(arm) * TAU + seed + arm;
            TendrilMoves.Shape shape = cast.pose(arm);
            Vec3d last = frame.wound();
            for (int link = 0; link < LINKS; link++) {
                double s = (link + 1.0) / LINKS;
                Vec3d end = REST.rest(frame, arm, s, crawl, quest, 0, 1, burst);
                if (shape != null) end = end.lerp(cast.point(shape, arm, s), cast.weight(arm, s));
                end = frame.wound().lerp(end, grown);
                double width = 2 * REST.radius(s - .5 / LINKS) * grown * cast.thickness(s);
                end = recovery.joint(arm, link, frame, end, width);
                width = recovery.width(arm, link);
                end = new Vec3d(end.x, Math.max(end.y, frame.floor() + width * .5), end.z);
                drawLink(f, last, end, width, cast.charge(arm, s), link, seed + arm * 13);
                last = end;
            }
        }
        wound(f, frame, seed, f.now(), burst);
    }

    private static void drawLink(InkFrame f, Vec3d from, Vec3d to, double width, double charge, int link, double seed) {
        Vec3d axis = to.subtract(from);
        double length = axis.length();
        if (length < .01) return;
        Vec3d[] axes = basis(axis);
        Vec3d mid = from.lerp(to, .5);
        // Overlap the joint on the inside of a bend. The square cuff stays visible on its outside.
        InkKit.cube(f.ink(), mid, width, length + width * .27, width, InkKit.INK, seed + link, axes, 1);
        double cuff = width * (1.08 + .08 * charge);
        int row = charge > .8 ? InkKit.VIOLET : InkKit.GLOSS;
        InkKit.cube(f.ink(), from.lerp(to, .82), cuff, Math.min(length * .20, width * .36), cuff,
                row, seed + link, axes, 1);
    }

    static Vec3d clear(Frame fr, Vec3d at, double s) {
        Vec3d rel = at.subtract(fr.body);
        double o = rel.dotProduct(fr.out), fw = rel.dotProduct(fr.fwd), h = rel.y;
        double inFront = smoothstep((fw + .7) / .5), below = 1 - smoothstep((h - 2.05) / .45), fromRoot = smoothstep((s - .05) / .1);
        double w = inFront * below * fromRoot;
        if (w <= 1e-4) return at;
        double edge = .8, k = .35;
        double pushed = (o + edge + Math.sqrt((o - edge) * (o - edge) + k * k)) / 2;      // a smooth max(o, edge)
        return at.add(fr.out.multiply((pushed - o) * w));
    }

    private static void wound(InkFrame f, Frame fr, double seed, double t, double burst) {
        AtlasMesh m = f.ink();
        Vec3d[] axes = yawAxes(Math.atan2(fr.fwd.x, fr.fwd.z));
        for (int i = 0; i < 3; i++) {
            double k = i / 2.0, wob = .025 * Math.sin(t * 3.1 + i * 1.7 + seed);
            Vec3d p = fr.wound.add(fr.out.multiply(.08 + .04 * Math.sin(t * 2 + i))).add(0, .1 - k * .45, 0);
            double s = .36 - k * .1 + wob;
            InkKit.cube(m, p, s * .9, s, s * 1.1, i == 0 ? InkKit.GLOSS : InkKit.INK, seed + i * 2.1, axes, 1);
        }
        double life = 1.4, a = (t + rnd(seed)) % life;
        InkKit.drip(m, fr.wound.add(fr.out.multiply(.2)).add(0, -.3, 0), fr.floor, a, life, .1, seed + Math.floor((t + rnd(seed)) / life));
        if (burst < 40) InkKit.splash(m, fr.wound.add(fr.out.multiply(.3)), fr.floor, burst / 20, 22, 6, .24, seed + 60);
    }
}
