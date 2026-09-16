package com.anton.elementalwands.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Shared preview/server geometry. Positions describe the player's feet. */
public final class FireLeapRules {
    public static final double RANGE=60, ARC=4.5, WAVE_RANGE=5, WAVE_SPEED=.65, WAVE_HEIGHT=.6;
    public static final int FLIGHT=20;
    public static Vec3d position(Vec3d from, Vec3d to, double progress) {
        double t=Math.clamp(progress,0,1), horizontal=t*t*(3-2*t);
        return new Vec3d(MathHelper.lerp(horizontal,from.x,to.x),
                MathHelper.lerp(t,from.y,to.y)+Math.sin(Math.PI*t)*ARC,MathHelper.lerp(horizontal,from.z,to.z));
    }
    public static Vec3d target(PlayerEntity player) {
        var hit=player.getEntityWorld().raycast(new RaycastContext(player.getEyePos(),player.getEyePos().add(player.getRotationVec(1).multiply(Math.hypot(RANGE, 8))),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,player));
        return hit.getType()==HitResult.Type.BLOCK && hit.getSide()==Direction.UP ? hit.getPos() : null;
    }
    public static boolean validTarget(PlayerEntity player, Vec3d to) {
        if (!finite(to)) return false;
        Vec3d from=player.getEntityPos(),delta=to.subtract(from);
        if (delta.horizontalLength()>RANGE || delta.horizontalLength()<1 || delta.y>4 || delta.y< -6) return false;
        var world=player.getEntityWorld();
        if (!space(world,player,to) || !supported(world,player,to,.15)) return false;
        var sight=world.raycast(new RaycastContext(player.getEyePos(),to.add(0,.04,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,player));
        if (sight.getType()!=HitResult.Type.MISS) return false;
        Vec3d previous=from;
        for(int i=1;i<=FLIGHT;i++) {
            Vec3d next=position(from,to,i/(double)FLIGHT);
            if(!segment(world,player,previous,next)) return false;
            if(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                    && !com.anton.elementalwands.arena.GuardianArenaManager.canTeleport(serverPlayer,serverPlayer.getEntityWorld(),next)) return false;
            previous=next;
        }
        return true;
    }
    public static boolean finite(Vec3d p) { return p!=null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    public static boolean supported(World world,Entity body,Vec3d feet,double depth) {
        return world.getBlockCollisions(body,new Box(feet.x-.25,feet.y-depth,feet.z-.25,feet.x+.25,feet.y-.001,feet.z+.25)).iterator().hasNext();
    }
    public static boolean space(World world,Entity body,Vec3d feet) {
        // Always reserve standing-player clearance, including when aiming while crouched.
        Box box=new Box(feet.x-.3,feet.y+.005,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3);
        if(!world.getWorldBorder().contains(box) || box.minY<world.getBottomY() || box.maxY>world.getTopYInclusive()+1) return false;
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(box.minX,box.minY,box.minZ),BlockPos.ofFloored(box.maxX,box.maxY,box.maxZ)))
            if(!world.isChunkLoaded(pos) || !world.getFluidState(pos).isEmpty()) return false;
        return !world.getBlockCollisions(body,box).iterator().hasNext();
    }
    public static boolean segment(World world,Entity body,Vec3d from,Vec3d to) {
        int samples=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.15));
        for(int i=1;i<=samples;i++) if(!space(world,body,from.lerp(to,i/(double)samples))) return false;
        return true;
    }
    public static float damage(double radius) { return radius<=1.5 ? 8 : 4; }
    private FireLeapRules() {}
}
