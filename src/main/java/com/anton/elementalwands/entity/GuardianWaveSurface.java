package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Identical surface/cover rules for stone-wave rendering and server damage. */
public final class GuardianWaveSurface {
    private GuardianWaveSurface() {}

    // Small primary spell obstacles are not defenses against this ground wave.
    // Do not ignore tree trunks/leaves, Stone Wall, Titan Dome or ordinary terrain.
    private static boolean permeable(BlockState state) {
        return ModSpellBlocks.isNatureGrowth(state) || state.isOf(ModSpellBlocks.STONE_SPIKE) || state.isOf(Blocks.FLOWERING_AZALEA)
                || state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.LILY_PAD);
    }

    static BlockHitResult raycast(World world, Entity owner, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,owner) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockView view, BlockPos pos) {
                return permeable(state) ? VoxelShapes.empty() : super.getBlockShape(state,view,pos);
            }
        });
    }

    public static boolean clearLine(World world, Entity owner, Vec3d from, Vec3d to) {
        return raycast(world,owner,from,to).getType() == HitResult.Type.MISS;
    }

    static Vec3d groundUnderWall(World world, Entity owner, Vec3d origin, BlockPos pos) {
        Vec3d top=new Vec3d(pos.getX()+.5,origin.y+2,pos.getZ()+.5);
        var hit=world.raycast(new RaycastContext(top,top.add(0,-5,0),RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,owner) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockView view, BlockPos at) {
                return state.isOf(ModSpellBlocks.STONE_WALL) || permeable(state)
                        ? VoxelShapes.empty():super.getBlockShape(state,view,at);
            }
        });
        return hit.getType()==HitResult.Type.MISS?null:hit.getPos();
    }

    public static Vec3d ground(World world, Entity owner, Vec3d origin, double x, double z) {
        Vec3d top = new Vec3d(x, origin.y+2, z), bottom = new Vec3d(x, origin.y-3, z);
        if (!world.isChunkLoaded(BlockPos.ofFloored(top))) return null;
        var hit = raycast(world,owner,top,bottom);
        return hit.getType() == HitResult.Type.MISS ? null : hit.getPos();
    }
    public static boolean visible(World world, Entity owner, Vec3d origin, Vec3d surface) {
        return clearLine(world,owner,origin.add(0,.7,0),surface.add(0,.7,0));
    }
}
