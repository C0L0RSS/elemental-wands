package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class GuardianPhaseContractTest {
    static void run() {
        require(GuardianPhaseRules.threshold(360,600) && !GuardianPhaseRules.threshold(361,600),"60% boundary moved");
        require(GuardianPhaseRules.threshold(1440,2400),"Party threshold differs from solo");
        for(boolean unstable:new boolean[]{false,true}) {
            double speed=GuardianPhaseRules.waveSpeed(unstable),range=GuardianPhaseRules.waveRange(unstable);
            require(speed>.3,"Sprinting can outrun the wave");
            // Time an ordinary vanilla jump across the sweeping damage band at several distances.
            for(double distance:new double[]{4,12,range-1}) {
                int crossing=(int)Math.floor(distance/speed),jumpAt=Math.max(-10,crossing-5);
                double height=0,velocity=.42;
                boolean groundedHit=false,jumpHit=false;
                for(int tick=jumpAt;tick<GuardianPhaseRules.waveTicks(unstable);tick++) {
                    if(tick>jumpAt && height>=0) {height+=velocity;velocity=(velocity-.08)*.98;}
                    height=Math.max(0,height);
                    if(tick<0)continue;
                    var box=new Box(distance-.3,0,-.3,distance+.3,1.8,.3);
                    double previous=tick*speed,radius=Math.min(range,(tick+1)*speed);
                    groundedHit|=GuardianCombatRules.waveContact(Vec3d.ZERO,box,previous,radius,0);
                    jumpHit|=GuardianCombatRules.waveContact(Vec3d.ZERO,box.offset(0,height,0),previous,radius,0);
                }
                require(groundedHit && !jumpHit,"Ordinary timed jump failed at "+distance+" unstable="+unstable);
            }
        }
        // The triple sequence needs at most two concurrent wave slots, including the next leap launch.
        int[] impacts={21,66,116,170,212};
        for(int tick=0;tick<260;tick++) {
            int count=0;for(int impact:impacts)if(tick>=impact && tick<impact+GuardianPhaseRules.waveTicks(true))count++;
            require(count<=2,"Combo can exhaust wave slots");
        }
        require(GuardianLeapRules.damage(Vec3d.ZERO,new Box(4,0,0,4.6,1.8,.6))==0,"Outer landing remains unjumpable");
        System.out.println("Guardian phase checks passed: party threshold, vanilla jump arcs, outer impact, overlapping wave capacity.");
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
