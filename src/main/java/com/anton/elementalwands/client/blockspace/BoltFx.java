package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockspace.BlockSpaceKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireEffects;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.entity.SingularityBoltEntity;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Singularity Bolt (art/space/blockspace/scenes/bolt.js): an eclipse core with a broken orbit, dragging the space
 * behind it into itself, following one SingularityBoltEntity. Its first three blocks bend out of the wand tip of
 * whoever cast it (the owner, or the Astral Double for an echo, drawn a little smaller). When the server reports a hit
 * the core swells, swallows the space around it and pinches shut; removed without one, it ran out of range and folds away.
 */
final class BoltFx implements BlockSpaceEffects.Fx {
    private static final double BEND_IN = 3, BLOOM = .45;

    private final SingularityBoltEntity bolt;
    private final Vec3d origin, tipOffset, castTip;
    private Vec3d dir;
    private final double start, seed, scale;
    /** The drawn head over time; two seconds covers the longest wake piece and the whole 24-block flight. */
    private final Track track = new Track(2);
    private final Glide glide = new Glide();
    private double end = Double.NaN;
    private Vec3d endAt;
    private boolean hit;

    BoltFx(ClientWorld world, SingularityBoltEntity bolt, double now) {
        this.bolt = bolt;
        Vec3d v = bolt.getVelocity();
        dir = v.lengthSquared() < 1e-8 ? new Vec3d(0, 0, 1) : v.normalize();
        start = now - bolt.age / 20.0;
        origin = bolt.getEntityPos().subtract(v.multiply(bolt.age));
        // Who it left: the owner's eye for a cast, the Astral Double's for an echo.
        LivingEntity caster = bolt.getOwner() instanceof LivingEntity o && o.getEyePos().squaredDistanceTo(origin) < 1 ? o : null;
        boolean echo = false;
        if (caster == null) {
            List<AstralDoubleEntity> doubles = world.getEntitiesByClass(AstralDoubleEntity.class, new Box(origin, origin).expand(2), e -> true);
            if (!doubles.isEmpty()) { caster = doubles.getFirst(); echo = true; }
        }
        scale = echo ? .85 : 1;
        castTip = caster != null && bolt.age < 3 ? BlockFireEffects.tip(caster, 1) : null;
        tipOffset = castTip != null ? castTip.subtract(origin) : Vec3d.ZERO;
        seed = bolt.getId() % 97 * 13.0;
    }

    private Vec3d headAt(double t) { return track.isEmpty() ? origin : track.at(t); }

    @Override public boolean draw(BlockSpaceEffects.Frame f) {
        double now = f.now(), age = now - start;
        if (Double.isNaN(end) && bolt.isRemoved()) {
            end = now;
            double[] h = BlockSpaceEffects.takeHit(bolt.getId());
            hit = h != null;
            endAt = hit ? new Vec3d(h[0], h[1], h[2]) : headAt(now);
        }
        if (!Double.isNaN(end) && now > end + .8) return false;
        if (Double.isNaN(end)) {
            Vec3d p = glide.at(bolt, now), v = bolt.getVelocity();
            if (v.lengthSquared() > 1e-8) dir = v.normalize();
            Vec3d shown = p.add(tipOffset.multiply(1 - smoothstep(p.distanceTo(origin) / BEND_IN)));
            track.add(now, shown);
        }
        Vec3d head = Double.isNaN(end) ? headAt(now) : endAt;
        if (!BlockSpaceEffects.near(f, head, 96)) return true;
        BlockSpaceMesh m = f.m();
        if (Double.isNaN(end)) {
            boltHead(m, head, dir, age, scale, seed);
            stars(m, head, now, 5, .8 * scale, .5, seed + 5, .05, true);
        }
        // Shedding starts a block out, so the wake never joins the wand in a solid rod; it keeps falling in after a hit.
        chase(m, this::headAt, now, start + .06, Double.isNaN(end) ? Double.POSITIVE_INFINITY : end, .028, .3, .45 * scale, .2 * scale, seed, GLASS);
        if (castTip != null) bloom(m, age);
        if (!Double.isNaN(end)) {
            double a = now - end;
            if (hit) impact(m, endAt, a);
            else fizzle(m, endAt, a);
        }
        return true;
    }

    /** Cast bloom just past the wand tip: a small ring pulled shut and a few stars sucked in. */
    private void bloom(BlockSpaceMesh m, double age) {
        if (age > .3) return;
        Vec3d p = castTip.add(dir.multiply(BLOOM));
        double k = age / .22;
        if (k < 1) ring(m, p, dir, lerp(.28, .04, smoothstep(k)), 8, .035 * (1 - k * .6), DEFAULT, 1, RIM, DEFAULT, age * 6, seed + 1, 0, true);
        stars(m, p, age, 5, .5, .3, seed + 9, .045, true);
    }

    /** Direct hit: the core swells as it swallows the contact, the space it disturbed falls back in, then a pinpoint. */
    private void impact(BlockSpaceMesh m, Vec3d at, double age) {
        if (age > .75) return;
        double r = (age < .08 ? lerp(.25, .5, age / .08) : .5 * (1 - smoothstep((age - .08) / .37))) * scale;
        core(m, at, r, seed + 17, 3);
        inflow(m, at, age - .06, 14, 1.7 * scale, .42, seed + 18, .26 * scale, true);
        pinpoint(m, at, .26 * scale, (age - .45) / .12, seed + 17);
        if (m.embers) spill(m, at, age, 8, 2.4, .35, seed + 19, .09, NEBULA, null);
    }

    /** Full range: the core folds shut and pinches to a point. */
    private void fizzle(BlockSpaceMesh m, Vec3d at, double age) {
        if (age > .35) return;
        double k = age / .22;
        if (k < 1) core(m, at, .25 * scale * (1 - k), seed + 19, 1);
        pinpoint(m, at, .18 * scale, (age - .18) / .14, seed + 19);
    }
}
