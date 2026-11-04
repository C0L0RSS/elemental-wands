package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;

import java.util.ArrayDeque;
import net.minecraft.util.math.Vec3d;

/** Where a moving head was over the last few seconds, sampled once per frame, so a wake can be pulled after it. */
final class Track implements BlockSpaceKit.Path {
    private final ArrayDeque<double[]> samples = new ArrayDeque<>();
    private final double keep;

    /** keep is how many seconds of history to hold. */
    Track(double keep) { this.keep = keep; }

    void add(double now, Vec3d p) {
        if (!samples.isEmpty() && now <= samples.getLast()[0] + 1e-4) return;
        samples.add(new double[]{now, p.x, p.y, p.z});
        while (samples.size() > 2 && samples.getFirst()[0] < now - keep) samples.removeFirst();
    }
    boolean isEmpty() { return samples.isEmpty(); }
    Vec3d last() { double[] q = samples.getLast(); return new Vec3d(q[1], q[2], q[3]); }

    @Override public Vec3d at(double t) {
        double[] prev = samples.getFirst();
        if (t <= prev[0]) return new Vec3d(prev[1], prev[2], prev[3]);
        for (double[] q : samples) {
            if (q[0] >= t) {
                double k = q[0] - prev[0] < 1e-6 ? 1 : (t - prev[0]) / (q[0] - prev[0]);
                return new Vec3d(lerp(prev[1], q[1], k), lerp(prev[2], q[2], k), lerp(prev[3], q[3], k));
            }
            prev = q;
        }
        return new Vec3d(prev[1], prev[2], prev[3]);
    }
}
