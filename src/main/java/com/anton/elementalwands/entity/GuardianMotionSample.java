package com.anton.elementalwands.entity;

import net.minecraft.util.math.Vec3d;

/** Observe server positions: player velocity fields do not reliably contain walking input. */
final class GuardianMotionSample {
    private Vec3d previous;
    private Vec3d velocity = Vec3d.ZERO;
    void reset(Vec3d position) { previous = position; velocity = Vec3d.ZERO; }
    Vec3d observe(Vec3d position) {
        if (previous == null) { reset(position); return velocity; }
        Vec3d delta = position.subtract(previous);
        previous = position;
        if (delta.lengthSquared() > 1.5*1.5) velocity = Vec3d.ZERO;
        else velocity = velocity.multiply(.5).add(new Vec3d(delta.x,0,delta.z).multiply(.5));
        return velocity;
    }
}
