package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.google.common.collect.MapMaker;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.util.math.Vec3d;

/** Carries the last drawn joints through a cast change. Samples live in the wound's frame, including across a blink. */
final class TendrilRecovery {
    // Weak identity keys: Minecraft can reuse a network entity ID on the next connection.
    private static final Map<FirstWizardEntity, TendrilRecovery> COPIES = new MapMaker().weakKeys().makeMap();
    private static final long IDLE = Long.MIN_VALUE;
    private static final int JOINTS = 3 * TendrilFx.LINKS;
    private final Vec3d[] last = new Vec3d[JOINTS], velocity = new Vec3d[JOINTS];
    private final Vec3d[] from = new Vec3d[JOINTS], drift = new Vec3d[JOINTS];
    private final double[] width = new double[JOINTS], fromWidth = new double[JOINTS];
    private long castStart = IDLE;
    private double time = Double.NEGATIVE_INFINITY, elapsed, since = Double.POSITIVE_INFINITY;

    static TendrilRecovery sample(FirstWizardEntity copy, double tick, double castAge) {
        TendrilRecovery pose = COPIES.computeIfAbsent(copy, unused -> new TendrilRecovery());
        pose.begin(tick, castAge);
        return pose;
    }

    void begin(double tick, double castAge) {
        long next = castAge < 0 ? IDLE : Math.round(tick - castAge);
        elapsed = tick - time;
        time = tick;
        since += elapsed;
        // Returning to view or rewinding the clock starts at the current synced pose.
        if (elapsed < 0 || elapsed > 21) {
            since = Double.POSITIVE_INFINITY;
            Arrays.fill(last, null);
        } else if (next != castStart && (castStart != IDLE || since < 21)) {
            System.arraycopy(last, 0, from, 0, JOINTS);
            System.arraycopy(velocity, 0, drift, 0, JOINTS);
            System.arraycopy(width, 0, fromWidth, 0, JOINTS);
            since = 0;
        }
        castStart = next;
    }

    Vec3d joint(int arm, int link, TendrilFx.Frame body, Vec3d target, double size) {
        int i = arm * TendrilFx.LINKS + link;
        double s = (link + 1.0) / TendrilFx.LINKS;
        // The shoulder releases first; the three tips peel away on different beats.
        double duration = castStart == IDLE ? 12 + 6 * s + arm * 1.5 : 5 + 2 * s;
        Vec3d rel = target.subtract(body.wound());
        Vec3d local = new Vec3d(rel.dotProduct(body.out()), rel.y, rel.dotProduct(body.fwd()));
        if (since < duration && from[i] != null) {
            double t = since / duration;
            double mix = smoothstep(t);
            local = from[i].lerp(local, mix);
            if (drift[i] != null) local = local.add(drift[i].multiply(since * (1 - t) * (1 - t)));
            size = fromWidth[i] + (size - fromWidth[i]) * mix;
        }
        if (elapsed > 0 && elapsed <= 3 && last[i] != null) {
            Vec3d speed = local.subtract(last[i]).multiply(1 / elapsed);
            velocity[i] = speed.multiply(1 / Math.max(1, speed.length() / .65));
        } else if (last[i] == null || elapsed > 3) velocity[i] = Vec3d.ZERO;
        last[i] = local;
        width[i] = size;
        return body.wound().add(body.out().multiply(local.x)).add(0, local.y, 0).add(body.fwd().multiply(local.z));
    }

    double width(int arm, int link) { return width[arm * TendrilFx.LINKS + link]; }
}
