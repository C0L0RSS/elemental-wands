package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockspace.BlockSpaceKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireEffects;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Blink Rift and the Astral Double swap (art/space/blockspace/scenes/blink.js): a standing eclipse slit opens at both
 * ends. The caster folds into the first one as void glass from the head down while the space around them falls in,
 * then coalesces out of the second as shards spill back out. Drawn from the BLINK message alone.
 */
final class BlinkFx implements BlockSpaceEffects.Fx {
    private final ClientWorld world;
    private final int entity;
    private final Vec3d from, to, facing;
    private final double t0, seed;

    BlinkFx(ClientWorld world, int entity, Vec3d from, Vec3d to, double now) {
        this.world = world; this.entity = entity; this.from = from; this.to = to; t0 = now;
        Entity e = world.getEntityById(entity);
        Vec3d look = e != null ? e.getRotationVec(1) : to.subtract(from);
        facing = look.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : look;
        seed = BlockFireEffects.seed(from, now);
    }

    @Override public boolean draw(BlockSpaceEffects.Frame f) {
        double age = f.now() - t0;
        if (age > .9) return false;
        BlockSpaceMesh m = f.m();
        if (BlockSpaceEffects.near(f, from, 96)) {
            rift(m, from, age, seed, false, .12);
            if (age < .2) ghost(m, from, facing, 1 - age / .2, .95, seed + 5);
        }
        if (BlockSpaceEffects.near(f, to, 96)) {
            rift(m, to, age, seed + 13, true, .16);
            // The arriving caster wears a fading void-glass sheen as they coalesce.
            Entity e = world.getEntityById(entity);
            Vec3d feet = e != null && !e.isRemoved() ? e.getLerpedPos(f.delta()) : to;
            if (age < .35) ghost(m, feet, facing, clamp(age / .12), (1 - age / .35) * .9, seed + 21);
        }
        return true;
    }

    /** A blink with no safe spot: a hairline slit flickers half open just ahead of the caster and shuts. */
    static final class Fail implements BlockSpaceEffects.Fx {
        private final Vec3d at;
        private final double t0, seed;
        Fail(Vec3d feet, Vec3d look, double now) {
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            at = feet.add(flat.lengthSquared() < 1e-6 ? Vec3d.ZERO : flat.normalize().multiply(.55));
            t0 = now; seed = BlockFireEffects.seed(feet, now);
        }
        @Override public boolean draw(BlockSpaceEffects.Frame f) {
            double age = f.now() - t0;
            if (age > .4) return false;
            if (BlockSpaceEffects.near(f, at, 96)) slit(f.m(), at, 1.6, .5, openness(age, .05, .03, .18, .4), seed, false);
            return true;
        }
    }
}
