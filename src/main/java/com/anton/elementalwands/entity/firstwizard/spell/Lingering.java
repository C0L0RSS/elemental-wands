package com.anton.elementalwands.entity.firstwizard.spell;

import net.minecraft.server.world.ServerWorld;

/**
 * Something a spell leaves running after the copy is free: a fireball still in flight, burning ground, a falling
 * meteor, a flower, a well. Hand it to {@link Cast#linger}; the fight ticks it every server tick until it says it is
 * done, and cancels it if the fight is stopped, the copy dies or is removed, or the server stops.
 */
public interface Lingering {
    /** One server tick. Return false once it has finished; it is then dropped without {@link #cancel}. */
    boolean tick(ServerWorld world, long now);

    /**
     * Cut short. Take away everything it placed in the world (blocks it set, entities it spawned) at once. Effect
     * entities made through {@link Cast#effect} are discarded by the fight anyway.
     */
    default void cancel(ServerWorld world) {}
}
