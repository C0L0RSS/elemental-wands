package com.anton.elementalwands.util;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * When a spell launch has really lifted the player, for Springbloom and Updraft fall protection.
 * Vanilla applies a move packet's ground flag after its movement, so the packet that sets off a
 * launch marks the player grounded again, and so does every packet until the client applies the
 * launch. Only an accepted move packet that reports the player airborne, higher than a jump
 * from their last footing can reach, shows the launch has taken effect.
 */
public final class LaunchLiftoff {
    /** Above a vanilla jump's 1.25-block apex, below either launch's first-tick rise. */
    public static final double RISE = 1.3;
    /** Ticks a launch waits to lift off before ordinary ground contact may end it. */
    public static final int WAIT = 40;
    /**
     * The height a lift-off is measured from, after an accepted move packet: the player's footing
     * when the packet reports them grounded (so a step then a jump is still only a jump), and the
     * lowest point otherwise (so a cast while falling measures from where the launch turns them).
     */
    public static double floor(ServerPlayerEntity p, double floor) {
        return p.isOnGround() ? p.getY() : Math.min(floor, p.getY());
    }
    /** Called with the position and ground flag of an accepted move packet. */
    public static boolean rose(ServerPlayerEntity p, double floor) {
        return !p.isOnGround() && p.getY() > floor + RISE;
    }
    private LaunchLiftoff() {}
}
