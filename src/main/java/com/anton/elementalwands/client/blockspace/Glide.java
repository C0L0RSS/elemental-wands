package com.anton.elementalwands.client.blockspace;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * A smooth drawn position for a spell entity the server moves with setPosition once a tick. Such an entity has no
 * interpolator, so each update snaps it forward whenever the packet lands and it stalls in between, which reads as a
 * low frame rate. Here the drawn head keeps moving on the entity's own speed after the latest update (for at most two
 * ticks), and when the next update lands, the small mismatch eases out over about a tenth of a second instead of
 * jumping. Purely visual: hits and collisions stay the server's.
 */
final class Glide {
    /** How long a correction takes to fade, and how far ahead of the latest update the head may run. */
    private static final double EASE = .06, AHEAD = .1;

    private Vec3d known, before, offset = Vec3d.ZERO;
    private double knownAt, last;

    Vec3d at(Entity e, double now) {
        Vec3d raw = e.getEntityPos();
        if (known == null) { known = raw; knownAt = last = now; return raw; }
        offset = offset.multiply(Math.exp(-Math.max(0, now - last) / EASE));
        last = now;
        if (!raw.equals(known)) {
            Vec3d drawn = ahead(e, now).add(offset);
            before = known; known = raw; knownAt = now;
            offset = drawn.subtract(known);
            // A teleport or a big correction snaps rather than gliding across the gap.
            if (offset.lengthSquared() > 9) offset = Vec3d.ZERO;
        }
        return ahead(e, now).add(offset);
    }

    /** The latest update moved on by the entity's velocity (blocks per tick), from its last two updates when it has them. */
    private Vec3d ahead(Entity e, double now) {
        Vec3d perTick = before != null && known.squaredDistanceTo(before) < 16 ? known.subtract(before) : e.getVelocity();
        return known.add(perTick.multiply(20 * Math.min(AHEAD, now - knownAt)));
    }
}
