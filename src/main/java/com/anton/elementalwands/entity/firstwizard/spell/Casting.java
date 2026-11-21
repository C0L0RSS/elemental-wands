package com.anton.elementalwands.entity.firstwizard.spell;

/**
 * One cast of a spell in progress: what {@link BossSpell#begin} returns. It keeps whatever this cast needs to remember
 * (its aim, its effect entities) in its own fields.
 */
public interface Casting {
    /**
     * Called once a tick with the ticks since the cast began, from 0 through the spell's {@code free} tick. Do the
     * forming, the aim lock and the release on the ticks its {@link Timing} names; hand anything that must outlive
     * {@code free} to {@link Cast#linger}.
     */
    void tick(int t);

    /**
     * The cast is cut short before {@code free} (the fight stopped, the copy died, an operator took over). Take away
     * anything it placed that the fight does not already clear (it clears effect entities, lingering effects and
     * temporary blocks made through {@link Cast}).
     */
    default void cancel() {}
}
