package com.anton.elementalwands.entity;

import com.anton.elementalwands.item.StoneAbilityHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Cover consumed by one discrete Guardian hit. Its shadow survives only that hit. */
final class GuardianWallImpact {
    private final List<Box> absorbed = new ArrayList<>();

    void breakWall(ServerWorld world, BlockPos pos) {
        for (BlockPos block : StoneAbilityHandler.breakWallFromGuardian(world,pos))
            absorbed.add(new Box(block));
    }

    Vec3d clip(ServerWorld world, FracturedGuardianEntity guardian, Vec3d from, Vec3d to,
            boolean wave, boolean consume) {
        Vec3d end=to;
        for (Box box:absorbed) {
            var hit=box.contains(from)?java.util.Optional.of(from):box.raycast(from,end);
            if(hit.isPresent())end=hit.get();
        }
        var hit=wave?GuardianWaveSurface.raycast(world,guardian,from,end):world.raycast(
                new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,guardian));
        if(hit.getType()!=HitResult.Type.MISS) {
            if(consume)breakWall(world,hit.getBlockPos());
            return hit.getPos();
        }
        return end;
    }

    boolean clear(ServerWorld world, FracturedGuardianEntity guardian, Vec3d from, Vec3d to, boolean wave) {
        return clip(world,guardian,from,to,wave,false).squaredDistanceTo(to)<1e-10;
    }
}
