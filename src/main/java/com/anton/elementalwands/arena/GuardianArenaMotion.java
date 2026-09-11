package com.anton.elementalwands.arena;

/** One clock for the passenger carriers and visible floor, including partial render ticks. */
public final class GuardianArenaMotion {
    private GuardianArenaMotion() {}
    public static double height(double from,double to,long start,int duration,double time) {
        if (duration<=0) return to;
        return from+(to-from)*GuardianArenaRules.ease((time-start)/duration);
    }
    public static double renderedHeight(double from,double to,long start,int duration,long tick,float delta) {
        double previous=height(from,to,start,duration,tick-1);
        return previous+(height(from,to,start,duration,tick)-previous)*delta;
    }
}
