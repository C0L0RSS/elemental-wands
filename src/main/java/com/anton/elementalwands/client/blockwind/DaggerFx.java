package com.anton.elementalwands.client.blockwind;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockwind.BlockWindKit.*;

import com.anton.elementalwands.entity.GaleDaggerEntity;
import com.anton.elementalwands.util.GaleDaggers;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Gale Daggers (art/wind/blockwind/scenes/daggers.js) around one GaleDaggerEntity: while it is being forged, curls
 * of air spiral in to it (GaleDaggerRenderer builds the blade from grip to tip over the same time); once fired, crisp
 * cloud rings are left every 1.2 blocks where it passed, and a reported hit bursts a small cloud.
 */
final class DaggerFx implements BlockWindEffects.Fx {
    /** Forging length in ticks; GaleDaggerRenderer grows the blade over the same span. */
    public static final int FORGE_TICKS = 8;
    private static final double SPEED = GaleDaggers.SPEED * 20, RING_GAP = 1.2, RING_LIFE = .32;

    private final GaleDaggerEntity dagger;
    private final double seed;
    private Vec3d origin, dir, last, hitAt;
    private double fired = Double.NaN, end = Double.NaN, travelled;

    DaggerFx(GaleDaggerEntity dagger, double now) {
        this.dagger = dagger;
        seed = dagger.getId() % 89 * 11.3;
    }

    @Override public boolean draw(BlockWindEffects.Frame f) {
        double now = f.now();
        boolean gone = dagger.isRemoved();
        if (Double.isNaN(end) && gone) {
            end = now;
            hitAt = BlockWindEffects.takeHit(dagger.getId());
            if (hitAt != null && dir != null) travelled = Math.max(travelled, hitAt.subtract(origin).dotProduct(dir));
        }
        if (!Double.isNaN(end) && (Double.isNaN(fired) || now > end + .6)) return false;
        Vec3d pos = gone ? last : dagger.getLerpedPos(f.delta());
        if (pos == null) return !gone;
        last = pos;
        if (!BlockWindEffects.near(f, pos, 96)) return true;
        BlockWindMesh m = f.m();
        if (!dagger.fired() && !gone) { forge(m, pos, f.delta()); return true; }
        if (Double.isNaN(fired)) {
            Vec3d v = dagger.getVelocity();
            if (v.lengthSquared() < 1e-6) return true;
            fired = now; dir = v.normalize(); origin = pos;
        }
        if (!gone) travelled = Math.max(0, pos.subtract(origin).dotProduct(dir));
        double a = now - fired;
        // Trail: rings left every RING_GAP blocks where the dagger passed, plus a short streak behind it in flight.
        for (double d = .8; d < travelled; d += RING_GAP) {
            double age = a - d / SPEED;
            if (age > RING_LIFE || age < 0) continue;
            double k = age / RING_LIFE;
            ring(m, origin.add(dir.multiply(d)), dir, lerp(.1, .48, smoothstep(k * 1.4)), 10, .055 * (1 - k),
                    d % 2.4 < 1.2 ? GALE : CLOUD, .85, d, seed + d);
        }
        if (!gone) streak(m, pos.subtract(dir.multiply(.6)), pos.subtract(dir.multiply(1.8)), .08, STREAK, .6, seed);
        if (hitAt != null) {
            double h = now - end;
            puff(m, hitAt, h, .3, seed + 70, .5, dir.multiply(.5).add(0, .2, 0), 3, AIR_ALPHA);
            motes(m, hitAt, h, 6, .8, 1.2, .4, seed + 80, dir.multiply(-1));
            if (m.embers) wisp(m, hitAt, dir.multiply(-1).add(0, .4, 0), h, .07, seed + 90, .4, .6);
        }
        return true;
    }

    /** Curls of air spiralling in to the prepared dagger while it forms. */
    private void forge(BlockWindMesh m, Vec3d at, float delta) {
        double k = clamp((dagger.age + delta) / FORGE_TICKS);
        if (k >= 1) return;
        double yaw = dagger.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d fw = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), right = side(fw);
        double r = lerp(.9, .05, smoothstep(k));
        for (int j = 0; j < 6; j++) {
            double a = TAU * j / 6 + k * 5 + dagger.index();
            Vec3d p = at.add(fw.multiply(Math.cos(a) * r * .4 - (1 - k) * .2)).add(UP.multiply(Math.sin(a) * r * .5)).add(right.multiply(Math.cos(a + 1) * r));
            Vec3d spin = fw.multiply(-Math.sin(a)).add(UP.multiply(Math.cos(a)));
            cube(m, p, .09 * (1 - k) + .02, j % 2 == 1 ? STREAK : CLOUD, seed + j, flowAxes(spin), .75);
        }
    }
}
