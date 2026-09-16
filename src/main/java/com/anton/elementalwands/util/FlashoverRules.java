package com.anton.elementalwands.util;

public final class FlashoverRules {
    public static final int CAPACITY=3, ARM_TICKS=10, LIFETIME=600, THROW_COOLDOWN=20, DETONATE_COOLDOWN=120, LOST_COOLDOWN=40, POP_INTERVAL=3;
    public static final double SPEED=1.1, GRAVITY=.045, RADIUS=4, DETONATE_RANGE=32;
    /** Cumulative damage: one bomb wounds a creeper; two kill it; three cap at 24. */
    public static float damage(int contacts) { return contacts<=0 ? 0 : contacts==1 ? 12 : contacts==2 ? 20 : 24; }
    private FlashoverRules() {}
}
