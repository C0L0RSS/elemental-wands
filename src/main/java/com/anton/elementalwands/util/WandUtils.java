package com.anton.elementalwands.util;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.function.Predicate;
import java.util.Optional;

public class WandUtils {

    /**
     * Raycasts for BOTH blocks and entities and returns whichever is hit first.
     * (PlayerEntity#raycast only finds blocks.)
     */
    public static HitResult raycast(World world, Entity caster, double range) {
        return raycast(world, caster, caster.getRotationVec(1.0f), range);
    }

    public static HitResult raycast(World world, Entity caster, Vec3d direction, double range) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(direction.multiply(range));

        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                caster));

        Box searchBox = caster.getBoundingBox()
                .stretch(direction.multiply(range))
                .expand(1.0);

        Predicate<Entity> predicate = (e) -> e != caster && e.isAlive() && e.canHit() && !e.isSpectator();

        // 1.21.x changed ProjectileUtil#getEntityCollision to require a
        // ProjectileEntity.
        // We *aren't* a projectile here, so we do a small manual raycast against entity
        // hitboxes.
        EntityHitResult entityHit = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : world.getOtherEntities(caster, searchBox, predicate)) {
            Optional<Vec3d> hitPos = e.getBoundingBox().raycast(start, end);
            if (hitPos.isEmpty())
                continue;

            double d = start.squaredDistanceTo(hitPos.get());
            if (d < bestDist) {
                bestDist = d;
                entityHit = new EntityHitResult(e, hitPos.get());
            }
        }

        if (entityHit == null)
            return blockHit;

        // Pick the closer of the two hits
        double entityDist = start.squaredDistanceTo(entityHit.getPos());
        double blockDist = start.squaredDistanceTo(blockHit.getPos());

        if (blockHit.getType() == HitResult.Type.MISS || entityDist < blockDist) {
            return entityHit;
        }
        return blockHit;
    }

    public static void spawnBeam(ServerWorld world, Vec3d start, Vec3d end, ParticleEffect particle) {
        Vec3d delta = end.subtract(start);
        double length = delta.length();
        if (length <= 0.01)
            return;

        Vec3d step = delta.normalize().multiply(0.35);
        int steps = (int) Math.ceil(length / 0.35);

        Vec3d p = start;
        for (int i = 0; i < steps; i++) {
            world.spawnParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            p = p.add(step);
        }
    }
}
