package com.anton.elementalwands.client.blockwind;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockwind.BlockWindKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireEffects;
import com.anton.elementalwands.entity.VacuumBladeEntity;
import com.anton.elementalwands.entity.WindFanRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Sky Shear (art/wind/blockwind/scenes/shear.js): a stepped crescent blade that faces the caster and the target,
 * trailed by two fainter echoes, following one VacuumBladeEntity. When the blade is removed short of full range it
 * hit something and bursts; at full range it peels apart into two little clouds. The centre blade also draws the
 * small cast bloom at its owner's wand tip.
 */
final class ShearFx implements BlockWindEffects.Fx {
    private static final double SPEED = 50, BEND_IN = 3, DISSOLVE = .24, ECHO_GAP = .75;

    private final VacuumBladeEntity blade;
    private final Vec3d origin, dir, up, tipOffset, castTip;
    private final double start, seed, radius;
    private final int wing;
    private double end = Double.NaN, travelled;
    private boolean hit;

    ShearFx(VacuumBladeEntity blade, double now) {
        this.blade = blade;
        Vec3d v = blade.getVelocity();
        dir = v.lengthSquared() < 1e-8 ? new Vec3d(0, 0, 1) : v.normalize();
        start = now - blade.age / 20.0;
        origin = blade.getEntityPos().subtract(dir.multiply(SPEED / 20 * blade.age));
        // Which blade of the fan this is: mirrored is the left wing; the centre blade runs along the owner's look.
        LivingEntity owner = blade.getOwner() instanceof LivingEntity o ? o : null;
        int w = blade.isMirrored() ? -1 : 1;
        if (!blade.isMirrored() && owner != null && owner.getRotationVec(1).normalize().dotProduct(dir) > Math.cos(Math.toRadians(4))) w = 0;
        wing = w;
        // The blade bends out of the wand onto the true eye line over its first three blocks.
        castTip = owner != null && blade.age < 3 ? BlockFireEffects.tip(owner, 1) : null;
        tipOffset = castTip != null ? castTip.subtract(origin) : Vec3d.ZERO;
        // Tilt each blade a little about its flight line, so the fan reads as three blades rather than one arc.
        up = UP.add(side(dir).multiply(-.38 + wing * .22)).normalize();
        radius = wing == 0 ? 1.05 : .9;
        seed = blade.getId() % 97 * 7.3;
    }

    private Vec3d path(double d) {
        d = Math.max(0, d);
        return origin.add(dir.multiply(d)).add(tipOffset.multiply(1 - smoothstep(d / BEND_IN)));
    }

    @Override public boolean draw(BlockWindEffects.Frame f) {
        double now = f.now();
        if (Double.isNaN(end) && blade.isRemoved()) {
            // A reported hit ends the blade where it struck; otherwise it reached full range.
            end = now;
            Vec3d at = BlockWindEffects.takeHit(blade.getId());
            hit = at != null;
            travelled = hit ? clamp(at.subtract(origin).dotProduct(dir), 0, WindFanRules.RANGE) : WindFanRules.RANGE;
        }
        if (!Double.isNaN(end) && now > end + .8) return false;
        if (!BlockWindEffects.near(f, path(travelled), 96)) return true;
        BlockWindMesh m = f.m();
        if (Double.isNaN(end)) travelled = clamp(blade.getLerpedPos(f.delta()).subtract(origin).dotProduct(dir), 0, WindFanRules.RANGE);
        double k = Double.isNaN(end) ? 0 : clamp((now - end) / DISSOLVE);
        if (k < 1) {
            blade(m, path(travelled), dir, up, radius, .5, .16, seed, k, .72, false);
            for (int e = 1; e <= 2; e++) {
                double de = travelled - e * ECHO_GAP;
                if (de >= .4) blade(m, path(de), dir, up, radius, .5 * (1 - e * .22), .16, seed + e * 97, Math.min(1, k + .28 * e), .42 / e, true);
            }
        }
        if (castTip != null && wing == 0) bloom(m, now - start);
        if (!Double.isNaN(end)) {
            double a = now - end;
            Vec3d at = path(travelled);
            if (hit) {
                puff(m, at, a, .42, 91 + seed, .6, dir.multiply(.8).add(0, .25, 0), 5, AIR_ALPHA);
                ring(m, at, dir, lerp(.3, 1.2, smoothstep(a / .35)), 14, .09 * (1 - smoothstep(a / .4)), CLOUD, .75, a * 1.5, 5 + seed);
                motes(m, at, a, 8, .8, 1.2, .5, 17 + seed, dir);
                if (m.embers) for (int i = 0; i < 3; i++)
                    wisp(m, at, dir.add(0, .2 * (i - 1), 0).add(side(dir).multiply(.5 * (i - 1))), a, .08, 50 + i + seed, .5, .6);
            } else {
                Vec3d s = side(dir);
                for (int i = -1; i <= 1; i += 2)
                    puff(m, at.add(s.multiply(i * .45)), a, .28, seed + i, .55, dir.multiply(.6).add(0, .15, 0), 4, AIR_ALPHA);
            }
        }
        return true;
    }

    /** The cast bloom just past the wand tip: a small pressure ring and two curls. */
    private void bloom(BlockWindMesh m, double age) {
        if (age > .45) return;
        Vec3d at = castTip.add(dir.multiply(.35));
        ring(m, at, dir, lerp(.08, .3, smoothstep(age / .18)), 9, .045 * (1 - smoothstep(age / .22)), GALE, .85, age * 2, seed);
        if (m.embers) for (int i = 0; i < 2; i++) {
            double s = i == 0 ? -1 : 1;
            wisp(m, castTip.add(dir.multiply(.2)), dir.add(0, .3 * s, 0).add(side(dir).multiply(-.4 * s)), age, .06, seed * 10 + i, .4, .45);
        }
    }
}
