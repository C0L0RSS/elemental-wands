package com.anton.elementalwands.util;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * When a spell launch has really lifted the player, for Springbloom and Updraft fall protection.
 * Vanilla applies a move packet's ground flag after its movement, so the packet that sets off a
 * launch marks the player grounded again, and so does every packet until the client applies the
 * launch. Only an accepted move packet that reports the player airborne, well above the lowest
 * point since the launch, shows the launch has taken effect.
 */
public final class LaunchLiftoff {
    /** Above a vanilla jump's 1.25-block apex, below either launch's first-tick rise. */
    public static final double RISE = 1.3;
    /** Ticks a launch waits to lift off before ordinary ground contact may end it. */
    public static final int WAIT = 40;
    /** Called with the position and ground flag of an accepted move packet. */
    public static boolean rose(ServerPlayerEntity p, double floor) {
        return !p.isOnGround() && p.getY() > floor + RISE;
    }
    private LaunchLiftoff() {}
}
