package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Dependency-free regression executable, run by Gradle checkGuardianBeam/check. */
public final class GuardianBeamContractTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        GuardianBossContractTest.run();
        GuardianLeapContractTest.run();
        GuardianSocketContractTest.run();
        Vec3d start = new Vec3d(0,1,0), end = new Vec3d(0,1,24);
        Box centered = new Box(-.3,0,8,.3,1.8,8.6);
        require(GuardianBeamGeometry.contact(start,end,centered).isPresent(), "Direct shot missed");
        require(GuardianBeamGeometry.contact(start,end,centered.offset(2,0,0)).isEmpty(), "Sideways dodge was hit");
        require(GuardianBeamGeometry.contact(start,end,centered.offset(0,0,-12)).isEmpty(), "Target behind muzzle was hit");
        require(GuardianBeamGeometry.contact(start,end,centered.offset(0,0,17)).isEmpty(), "Target beyond range was hit");
        require(GuardianBeamGeometry.contact(start,start,centered).isEmpty(), "Zero-length pulse hit");
        require(GuardianBeamGeometry.contact(start,new Vec3d(0,1,7.9),centered).isEmpty(),
                "Expanded hitbox leaked damage beyond a wall-truncated endpoint");
        Box edge = new Box(.2,0,8,.8,1.8,8.6);
        Vec3d contact = GuardianBeamGeometry.contact(start,end,edge).orElseThrow();
        require(contact.x >= edge.minX && contact.z >= edge.minZ,
                "Cover ray must end on real target surface rather than expanded volume");
        Box containing = new Box(-1,0,-1,1,2,1);
        require(GuardianBeamGeometry.contact(start,end,containing).isPresent(), "Origin-inside case was lost");
        Vec3d verticalStart=new Vec3d(0,10,0),verticalEnd=new Vec3d(0,0,0);
        require(GuardianBeamGeometry.contact(verticalStart,verticalEnd,new Box(-.3,3,-.3,.3,5,.3)).isPresent(),
                "Vertical ray failed");
        int fireEvents=0, visibleTicks=0;
        for (int tick=0; tick<GuardianBeamTiming.END; tick++) {
            if (GuardianBeamTiming.fires(tick)) {
                fireEvents++;
                require(GuardianBeamTiming.isPulse(tick), "Damage occurred outside visible pulse");
                require(!GuardianBeamTiming.isTracking(tick), "Shot still tracked at impact");
            }
            if (GuardianBeamTiming.isPulse(tick)) visibleTicks++;
        }
        require(fireEvents==1, "Charge must fire exactly once");
        require(visibleTicks==12, "Pulse must last 0.6 seconds at 20 TPS");
        require(GuardianBeamTiming.FIRE-GuardianBeamTiming.LOCK>=8, "Dodge commitment window is too short");
        require(GuardianBeamTiming.END-GuardianBeamTiming.FIRE-GuardianBeamTiming.PULSE>=20,
                "Recovery needs at least one second");
        System.out.println("Guardian beam checks passed: finite rays, dodge/cover edges, single emission, full pulse window, aim lock, pulse and recovery.");
    }
}
