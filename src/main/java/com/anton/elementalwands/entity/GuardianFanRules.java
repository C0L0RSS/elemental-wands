package com.anton.elementalwands.entity;

import net.minecraft.util.math.Vec3d;

/** Readable, committed fan volleys. No wand-type detection or post-release steering. */
public final class GuardianFanRules {
    public static final int COUNT=5, LOCK=26, RELEASE=32, REPEAT=50, RECOVERY=26, HOVER_TICKS=24;
    public static final double SPEED=.9, RANGE=40, RADIUS=.32;
    private GuardianFanRules() {}
    public static int duration(boolean secondPhase) { return RELEASE+RECOVERY+(secondPhase?REPEAT:0); }
    public static Vec3d socket(Vec3d feet, float yaw, float pitch, int index) {
        Vec3d forward=Vec3d.fromPolar(0,yaw),right=new Vec3d(forward.z,0,-forward.x);
        return feet.add(right.multiply((index-2)*1.2)).add(forward.multiply(.85))
                .add(0,6.2-Math.abs(index-2)*.25+Math.clamp(-pitch/60,0,1)*.8,0);
    }
    public static Vec3d direction(Vec3d origin, Vec3d aim, int index) {
        return aim.subtract(origin).normalize().rotateY((float)Math.toRadians((index-2)*12));
    }
    public static boolean hovering(double clearance, boolean grounded, int ticks) {
        return !grounded && clearance>2.5 && ticks>=HOVER_TICKS;
    }
}
