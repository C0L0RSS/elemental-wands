package com.anton.elementalwands.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Shared preview/server geometry. Positions describe the player's feet. */
public final class FireLeapRules {
    public static final double RANGE=22, WAVE_RANGE=5, WAVE_SPEED=.65, WAVE_HEIGHT=.6;
    /** Effective gravity (blocks/tick²) of the scripted arc; slightly heavier than vanilla for a snappy drop. */
    private static final double GRAVITY=.09;
    private static final int MIN_FLIGHT=12, MAX_FLIGHT=30;
    private static final double MAX_RISE=4, MAX_DROP=6, LANDING_INSET=.35, FALLBACK_STEP=1.5, SAMPLE=.3;

    /** Peak height above the higher endpoint: short hops stay low, full-range leaps climb high. */
    public static double apex(Vec3d from,Vec3d to) {
        return Math.clamp(2.2+.2*to.subtract(from).horizontalLength(),2.5,6.5);
    }
    /** Flight ticks follow from the arc and a constant gravity, so every leap falls with the same weight. */
    public static int duration(Vec3d from,Vec3d to) {
        double[] arc=ballistic(from,to);
        return (int)Math.clamp(Math.round(Math.sqrt(arc[1]/GRAVITY)),MIN_FLIGHT,MAX_FLIGHT);
    }
    /** {launch speed, gravity} in units of one whole flight, for y(t)=y0+v·t−g·t²/2 with t in [0,1]. */
    private static double[] ballistic(Vec3d from,Vec3d to) {
        double peak=Math.max(from.y,to.y)+apex(from,to);
        double up=Math.sqrt(peak-from.y),down=Math.sqrt(peak-to.y);
        return new double[]{2*up*(up+down),2*(up+down)*(up+down)};
    }
    /** Constant horizontal speed and a true parabola: the arc reads like a thrown body, not an easing curve. */
    public static Vec3d position(Vec3d from, Vec3d to, double progress) {
        double t=Math.clamp(progress,0,1);double[] arc=ballistic(from,to);
        return new Vec3d(MathHelper.lerp(t,from.x,to.x),from.y+arc[0]*t-arc[1]*t*t/2,MathHelper.lerp(t,from.z,to.z));
    }
    /** The aimed landing, pulled back along the aim to the farthest leap that clears; null when nothing does. */
    public static Vec3d target(PlayerEntity player) {
        Vec3d aimed=aimed(player);
        if(aimed==null || validTarget(player,aimed)) return aimed;
        Vec3d from=player.getEntityPos(),step=new Vec3d(aimed.x-from.x,0,aimed.z-from.z);
        double distance=step.horizontalLength();
        if(distance<1) return null;
        step=step.multiply(1/distance);
        for(double d=distance-FALLBACK_STEP;d>=1;d-=FALLBACK_STEP) {
            Vec3d floor=ground(player,from.x+step.x*d,from.z+step.z*d);
            if(floor!=null && validTarget(player,floor)) return floor;
        }
        return null;
    }
    /** Where the crosshair points: a floor, a reachable ledge top, or open ground at the range cap. */
    private static Vec3d aimed(PlayerEntity player) {
        Vec3d from=player.getEntityPos(),look=player.getRotationVec(1);
        var hit=player.getEntityWorld().raycast(new RaycastContext(player.getEyePos(),player.getEyePos().add(look.multiply(Math.hypot(RANGE, 8))),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,player));
        if(hit.getType()==HitResult.Type.BLOCK) {
            if(hit.getSide()==Direction.UP && inRange(from,hit.getPos())) return hit.getPos();
            if(hit.getSide().getAxis().isHorizontal()) {
                // Step onto the aimed face's column, then find its real collision top.
                Vec3d inside=hit.getPos().subtract(Vec3d.of(hit.getSide().getVector()).multiply(LANDING_INSET));
                Vec3d ledge=ground(player,inside.x,inside.z);
                if(ledge!=null) return ledge;
            }
        }
        Vec3d direction=new Vec3d(look.x,0,look.z).normalize();
        if(direction.lengthSquared()<.5) return null;
        double distance=hit.getType()==HitResult.Type.BLOCK
                ? Math.min(Math.nextDown(RANGE),hit.getPos().subtract(from).horizontalLength()
                    -(hit.getSide()==Direction.UP ? 0 : LANDING_INSET)) : Math.nextDown(RANGE);
        // A miss or distant floor is ordinary forward aiming. Stop at the range
        // cap and project down; walk back only when the edge has no safe footing.
        for(double d=distance;d>=1;d-=.5) {
            Vec3d point=from.add(direction.multiply(d));
            Vec3d floor=ground(player,point.x,point.z);
            if(floor!=null) return floor;
        }
        return null;
    }
    private static Vec3d ground(PlayerEntity player,double x,double z) {
        var world=player.getEntityWorld();Vec3d from=player.getEntityPos();
        if(!world.isChunkLoaded(BlockPos.ofFloored(x,from.y,z))) return null;
        var hit=world.raycast(new RaycastContext(new Vec3d(x,from.y+MAX_RISE+.01,z),new Vec3d(x,from.y-MAX_DROP-.01,z),
                RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,player));
        Vec3d feet=hit.getPos();
        return hit.getType()==HitResult.Type.BLOCK && hit.getSide()==Direction.UP && inRange(from,feet)
                && space(world,player,feet) && supported(world,player,feet,.15) ? feet : null;
    }
    private static boolean inRange(Vec3d from,Vec3d to) {
        Vec3d delta=to.subtract(from);
        return delta.horizontalLength()<=RANGE && delta.horizontalLength()>=1 && delta.y<=MAX_RISE && delta.y>=-MAX_DROP;
    }
    public static boolean validTarget(PlayerEntity player, Vec3d to) {
        if (!finite(to)) return false;
        Vec3d from=player.getEntityPos();
        if (!inRange(from,to)) return false;
        var world=player.getEntityWorld();
        if (!space(world,player,to) || !supported(world,player,to,.15)) return false;
        // The actual curved flight must clear terrain; seeing the landing's top
        // face in a straight eye ray is unnecessary (and rejects raised ledges).
        int steps=duration(from,to)*2;Vec3d previous=from;
        for(int i=1;i<=steps;i++) {
            Vec3d next=position(from,to,i/(double)steps);
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
    /** Samples closer than the 0.6-block body width, so no full block or fence post can slip between them. */
    public static boolean segment(World world,Entity body,Vec3d from,Vec3d to) {
        int samples=Math.max(1,(int)Math.ceil(from.distanceTo(to)/SAMPLE));
        for(int i=1;i<=samples;i++) if(!space(world,body,from.lerp(to,i/(double)samples))) return false;
        return true;
    }
    /** Closest approach of a body position to the committed arc, used to confirm the client is flying it. */
    public static double offPath(Vec3d from,Vec3d to,Vec3d at) {
        int steps=duration(from,to)*2;double best=Double.MAX_VALUE;Vec3d previous=from;
        for(int i=1;i<=steps;i++) {
            Vec3d next=position(from,to,i/(double)steps),line=next.subtract(previous);
            double t=line.lengthSquared()<1e-9 ? 0 : Math.clamp(at.subtract(previous).dotProduct(line)/line.lengthSquared(),0,1);
            best=Math.min(best,at.distanceTo(previous.add(line.multiply(t))));previous=next;
        }
        return best;
    }
    public static float damage(double radius) { return radius<=1.5 ? 8 : 4; }
    private FireLeapRules() {}
}
