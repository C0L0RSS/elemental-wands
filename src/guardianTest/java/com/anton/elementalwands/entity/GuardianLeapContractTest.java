package com.anton.elementalwands.entity;

import java.util.List;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import static com.anton.elementalwands.entity.GuardianLeapRules.*;

final class GuardianLeapContractTest {
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static boolean blockedBy(List<Box> obstacles, boolean oldDiagonalPath) {
        Vec3d start=new Vec3d(0,64,0),end=new Vec3d(20,64,0),previous=start;
        for (int i=1;i<=FLIGHT;i++) {
            double t=i/(double)FLIGHT;
            Vec3d next=oldDiagonalPath?start.lerp(end,t).add(0,4*HEIGHT*t*(1-t),0):position(start,end,t);
            int samples=Math.max(1,(int)Math.ceil(previous.distanceTo(next)/.35));
            for (int j=1;j<=samples;j++) {
                Vec3d p=previous.lerp(next,j/(double)samples);
                Box body=new Box(p.x-1.59,p.y+.01,p.z-1.59,p.x+1.59,p.y+5.2,p.z+1.59);
                if (obstacles.stream().anyMatch(body::intersects)) return true;
            }
            previous=next;
        }
        return false;
    }
    static void run() {
        Box floor=new Box(-5,63,-5,5,64,5);
        require(supportBox(new Vec3d(0,64,0),3.2).intersects(floor),"Frozen boss lost physical floor support");
        require(!supportBox(new Vec3d(0,64.2,0),3.2).intersects(floor),"Airborne boss counted as grounded");
        var steps=List.of(new Box(1.6,64,-2,3.6,65,2),new Box(16,64,-2,18.35,65,2));
        require(blockedBy(steps,true),"Fixture must reproduce the old diagonal launch/descent rejection");
        require(!blockedBy(steps,false),"Vertical departure/arrival still clips adjacent low steps");
        require(blockedBy(List.of(new Box(-3,71,-3,3,72,3)),false),"Ceiling protection was removed");
        require(blockedBy(List.of(new Box(9.9,64,-5,10.1,84,5)),false),"Leap can pass through a wall");
        Vec3d start=new Vec3d(0,64,0);
        for (Vec3d end:List.of(new Vec3d(20,64,0),new Vec3d(-15,67,10),new Vec3d(0,59,30))) {
            require(position(start,end,0).equals(start),"Leap did not start at its actual feet");
            require(position(start,end,1).equals(end),"Leap overshot the committed landing");
            require(Math.abs(position(start,end,.5).y-(start.y+end.y)/2-HEIGHT)<1e-8,"Leap lost its high arc");
            Vec3d previous=start;
            for (int i=1;i<=FLIGHT;i++) {
                Vec3d next=position(start,end,i/(double)FLIGHT);
                require(next.distanceTo(previous)<2.5,"Leap contains a teleport step");
                require(next.y>=Math.min(start.y,end.y)-.001,"Flight clipped below landing ground");
                previous=next;
            }
        }
        Vec3d landing=Vec3d.ZERO;
        require(damage(landing,new Box(-.3,0,-.3,.3,1.8,.3))==16,"Center smash must be heavy");
        require(damage(landing,new Box(4,0,0,4.6,1.8,.6))==0,"Outer landing must be handled by the jumpable wave");
        require(damage(landing,new Box(6.1,0,0,6.7,1.8,.6))==0,"Marked perimeter does not bound damage");
        require(damage(landing,new Box(-.3,-4,-.3,.3,-2,.3))==0,"Smash damaged a player through a lower floor");
        require(damage(landing,new Box(-.3,4,-.3,.3,6,.3))==0,"Smash reached above the impact volume");
        int first=0,second=0,overlap=0;
        for (int tick=0;tick<LAND+RECOVERY;tick++) {
            if (launchWaveTick(tick)>=0) first++;
            if (landingWaveTick(tick)>=0) second++;
            if (launchWaveTick(tick)>=0 && landingWaveTick(tick)>=0) overlap++;
        }
        require(first==38 && second==38,"One of the two shockwaves was truncated");
        require(FLIGHT==36 && HEIGHT==11,"Longer, higher reaction window changed unexpectedly");
        require(overlap==0,"The longer airtime should separate the two waves");
        require(launchWaveTick(TAKEOFF)==0,"Push-off wave is not synchronized to the fists");
        require(landingWaveTick(LAND)<0 && landingWaveTick(LAND+WAVE_DELAY)==0,"Landing wave lost its distinct beat");
        require(LOCK<TAKEOFF && TAKEOFF<LAND,"Commitment/launch/impact order broke");
        require(GuardianCombatRules.Attack.LEAP.duration==LAND+RECOVERY,"Director can overlap the leap recovery");
        try {
            var animations=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/resources/assets/elementalwands/geckolib/animations/fractured_guardian.animation.json")))
                    .getAsJsonObject().getAsJsonObject("animations");
            require(Math.abs(animations.getAsJsonObject("animation.fractured_guardian.leap_air")
                    .get("animation_length").getAsDouble()-FLIGHT/20.0)<.0001,"Air pose ends before the actual flight");
            require(Math.abs(animations.getAsJsonObject("animation.fractured_guardian.leap_launch")
                    .get("animation_length").getAsDouble()-TAKEOFF/20.0)<.0001,"Takeoff pose lost fist-impact sync");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        System.out.println("Guardian leap checks passed: high arc, continuous travel, marked AoE bounds, two complete separated waves, recovery timing.");
    }
}
