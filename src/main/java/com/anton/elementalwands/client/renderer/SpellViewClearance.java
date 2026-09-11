package com.anton.elementalwands.client.renderer;

import net.minecraft.util.math.Vec3d;

/** View-only clearance, shared by particles and spell meshes. Never changes a hitbox. */
public final class SpellViewClearance {
    private SpellViewClearance() {}

    public static float opacity(boolean firstPerson, double distance, double halfSize) {
        if (!firstPerson) return 1.0f;
        // Larger sprites need more clearance; even small motes should not cross the lens.
        double hidden = 0.35 + Math.max(0.0, halfSize) * 0.9;
        double visible = hidden + Math.max(0.75, halfSize * 0.85);
        double t = Math.clamp((distance - hidden) / (visible - hidden), 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    public static int color(int argb, float opacity) {
        int alpha = Math.round((argb >>> 24) * opacity);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Distance to the ribbon itself, including a tail that reaches behind its entity. */
    public static double distanceToSegment(Vec3d camera, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 1.0E-10) return camera.distanceTo(start);
        double t = Math.clamp(camera.subtract(start).dotProduct(segment) / lengthSquared, 0.0, 1.0);
        return camera.distanceTo(start.add(segment.multiply(t)));
    }
}
