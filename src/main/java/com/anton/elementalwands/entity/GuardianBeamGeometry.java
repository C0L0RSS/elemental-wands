package com.anton.elementalwands.entity;

import java.util.Optional;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Finite beam/target contact shared by the attack and geometry regression checks. */
public final class GuardianBeamGeometry {
    private GuardianBeamGeometry() {}

    public static Optional<Vec3d> contact(Vec3d start, Vec3d end, Box target) {
        Vec3d ray = end.subtract(start);
        if (ray.lengthSquared() < 1.0E-8) return Optional.empty();
        Box expanded = target.expand(GuardianBeamTiming.RADIUS);
        Optional<Vec3d> intersection = expanded.contains(start) ? Optional.of(start) : expanded.raycast(start,end);
        if (intersection.isEmpty()) return Optional.empty();
        Vec3d p = intersection.get();
        // Test cover against the real target surface, not its widened hit-test box.
        Vec3d surface = new Vec3d(MathHelper.clamp(p.x,target.minX,target.maxX),
                MathHelper.clamp(p.y,target.minY,target.maxY),MathHelper.clamp(p.z,target.minZ,target.maxZ));
        double fraction = surface.subtract(start).dotProduct(ray)/ray.lengthSquared();
        if (fraction < 0 || fraction > 1) return Optional.empty();
        return Optional.of(surface);
    }
}
