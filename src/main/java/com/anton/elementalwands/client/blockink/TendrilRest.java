package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;

import net.minecraft.util.math.Vec3d;

/** The hanging hook, the backswept arm and the raised striker, authored at the hinges in wound-local blocks. */
final class TendrilRest implements TendrilPose {
    static final double[] CRAWL = {.12, .09, .11}, QUEST = {.18, .15, .21};
    private static final double[][][] JOINTS = {
        {{0,0,0}, {.8,-.5,.15}, {1.8,-1.7,.55}, {4.55,-1.55,1.35}, {4.2,-.4,2.25}},
        {{0,0,0}, {.8,.25,-.4}, {2.4,1.4,-1.35}, {4.65,.55,-2.3}, {4.2,2.15,-2.8}},
        {{0,0,0}, {.55,.85,.15}, {1.6,3.05,.7}, {.95,3.65,2.35}, {.6,2.05,2.7}}
    };

    static double girth(double s) { return (.39 - .28 * s) * .5; }

    @Override public Vec3d rest(TendrilFx.Frame f, int arm, double s, double phase, double quest,
                                double hold, double unfurl, double burst) {
        double part = Math.clamp(s, 0, 1) * 4;
        int hinge = Math.min((int) part, 3);
        return joint(f, arm, hinge, phase, quest).lerp(joint(f, arm, hinge + 1, phase, quest), part - hinge);
    }

    private static Vec3d joint(TendrilFx.Frame f, int arm, int joint, double phase, double quest) {
        double[] p = JOINTS[arm][joint];
        double sway = smoothstep(joint / 4.0);
        return f.wound().add(f.out().multiply(p[0] + sway * .36 * Math.cos(phase - joint * .6)))
                .add(0, p[1] + sway * .27 * Math.sin(quest - joint * .8), 0)
                .add(f.fwd().multiply(p[2] + sway * .48 * Math.sin(phase - joint * .6)));
    }
}
