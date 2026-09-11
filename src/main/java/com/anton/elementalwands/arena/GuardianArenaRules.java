package com.anton.elementalwands.arena;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

/** Geometry and cinematic timing shared by the server, renderer, and executable checks. */
public final class GuardianArenaRules {
    public static final int HALF = 64, GATHER_RADIUS = 20;
    public static final int WALL_TICKS = 14, FLOOR_TICKS = 46, LIFT_TICKS = 160, SETTLE_TICKS = 30;
    public static final int ARRIVAL_TICKS = 36, LANDING_TICKS = 32;
    public static final int DESCEND_TICKS = 160, WITHDRAW_TICKS = 30;
    public static final int BUILD_BUDGET = 1024, CLEAN_BUDGET = 8192;
    public enum Phase { WALLS, FLOOR, ASCENT, SETTLE, ARRIVAL, LANDING, FIGHT, DESCENT, WITHDRAW, CLEANUP }

    private GuardianArenaRules() {}

    /** Keep camera/body clearance from the boss and separate players approaching on the same line. */
    public static java.util.List<Vec3d> liftSeats(java.util.List<Vec3d> players,Vec3d guardian) {
        java.util.List<Vec3d> seats=new java.util.ArrayList<>();
        for (Vec3d player:players) {
            double dx=player.x-guardian.x,dz=player.z-guardian.z;
            double angle=Math.atan2(dz,dx),preferred=Math.max(10,Math.hypot(dx,dz));
            Vec3d chosen=null;
            for(int ring=0;ring<10 && chosen==null;ring++) for(int attempt=0;attempt<72;attempt++) {
                double turn=attempt==0?0:((attempt+1)/2)*(attempt%2==1?1:-1)*Math.PI/36;
                double radius=preferred+ring*3;
                Vec3d candidate=new Vec3d(guardian.x+Math.cos(angle+turn)*radius,0,guardian.z+Math.sin(angle+turn)*radius);
                if(seats.stream().allMatch(other -> other.squaredDistanceTo(candidate)>=9)) {chosen=candidate;break;}
            }
            if(chosen==null) throw new IllegalArgumentException("Too many players for separated arena seats");
            seats.add(chosen);
        }
        return java.util.List.copyOf(seats);
    }

    public static double ease(double t) {
        t = Math.clamp(t, 0, 1);
        return t*t*(3-2*t);
    }

    public static boolean contains(double cx, double cz, double floor, double top, Vec3d p, double margin) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && p.x >= cx-HALF+margin && p.x <= cx+HALF-margin
                && p.z >= cz-HALF+margin && p.z <= cz+HALF-margin
                && p.y >= floor && p.y <= top;
    }

    public static Vec3d clamp(double cx, double cz, double floor, double top, Vec3d p, double margin) {
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return new Vec3d(cx,floor,cz);
        return new Vec3d(Math.clamp(p.x,cx-HALF+margin,cx+HALF-margin),
                Math.clamp(p.y,floor,top),Math.clamp(p.z,cz-HALF+margin,cz+HALF-margin));
    }

    /** Floor [center-64, center+63], walls immediately outside: exactly 128 clear blocks. */
    public static boolean wall(int dx, int dz) {
        return dx == -HALF-1 || dx == HALF || dz == -HALF-1 || dz == HALF;
    }

    public static int constructionCount(int floor,int top) { return 128*128+516*(top-floor); }
    public static BlockPos constructionPosition(int x,int z,int floor,int top,int index) {
        if (index<0 || index>=constructionCount(floor,top)) throw new IndexOutOfBoundsException(index);
        if (index<128*128) return new BlockPos(x-HALF+index%128,floor,z-HALF+index/128);
        int remaining=index-128*128,y=floor+1+remaining/516,p=remaining%516;
        if (p<130) return new BlockPos(x-65+p,y,z-65);
        if (p<260) return new BlockPos(x-65+p-130,y,z+64);
        if (p<388) return new BlockPos(x-65,y,z-64+p-260);
        return new BlockPos(x+64,y,z-64+p-388);
    }
}
