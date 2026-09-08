package com.anton.elementalwands.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Identical surface/cover rules for stone-wave rendering and server damage. */
public final class GuardianWaveSurface {
    private GuardianWaveSurface() {}
    public static Vec3d ground(World world, Entity owner, Vec3d origin, double x, double z) {
        Vec3d top = new Vec3d(x, origin.y+2, z), bottom = new Vec3d(x, origin.y-3, z);
        if (!world.isChunkLoaded(BlockPos.ofFloored(top))) return null;
        var hit = world.raycast(new RaycastContext(top,bottom,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,owner));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getPos();
    }
    public static boolean visible(World world, Entity owner, Vec3d origin, Vec3d surface) {
        return world.raycast(new RaycastContext(origin.add(0,.7,0),surface.add(0,.7,0),RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,owner)).getType() == HitResult.Type.MISS;
    }
}
