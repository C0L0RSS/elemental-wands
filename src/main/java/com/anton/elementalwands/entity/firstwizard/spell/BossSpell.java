package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;

/**
 * One of the First Wizard's moves, on the server: one of its ten spells, or one of phase two's ink moves (the tendrils'
 * Slam, Sweep and Leap, the Flame and Hollow Purple), which follow the same recipe with their move's {@code phase} 2.
 * An ink move shows where its free tendril reaches with {@link Cast#reach} and leaves vanilla entities only through
 * {@link Cast#adopt}. The change to phase two is not a move: it is {@link PhaseChange}, started by the fight. Each spell is one class implementing this, listed once in
 * {@link BossSpells#ALL}; the fight loop ({@code FirstWizardCombat}) never names a spell. The chooser
 * ({@link FirstWizardRules}) decides when a spell is cast; the spell decides what happens.
 *
 * <h2>What a spell must do</h2>
 * <ol>
 *   <li><b>Name its move</b> ({@link #move()}): its row in {@link FirstWizardRules.Move}. That row holds its range and
 *       weight; a move is available to the chooser exactly when a class here implements it.</li>
 *   <li><b>Give its {@link Timing}</b>, as constants: it forms in the open from {@code formFrom}, its aim locks at
 *       {@code aimLock}, it is released at {@code release} and the copy is free at {@code free}. The contract check
 *       holds formFrom &lt; aimLock &lt;= release &lt; free and at least half a second of forming. Bigger means wider
 *       and slower, never faster: a boss spell warns for longer than the player's.</li>
 *   <li><b>Form in the open.</b> From tick 0 spawn an effect entity ({@link Cast#effect}) that clients draw growing
 *       through the forming time: over the copy's head for a thrown spell (call {@link Cast#focus} there so ink rises
 *       off its wand arm to it), where it will appear for one that is not thrown.</li>
 *   <li><b>Lock its aim</b> at {@code aimLock}: the target's position, or {@link Cast#lead} for a throw. Nothing
 *       re-aims after this, so a sidestep after the lock dodges it. Record the point with {@link Cast#aim} so a
 *       follow-up can use it.</li>
 *   <li><b>Decide every hit on the server,</b> through {@link Cast#hit}, which respects {@code eligible}, creative
 *       and spectating players, allies, and uses the copy's own damage types. Use {@link FirstWizardRules#HEAVY},
 *       {@link FirstWizardRules#MEDIUM} and {@link FirstWizardRules#GROUND}; never a client's word.</li>
 *   <li><b>Stay on the floor.</b> Keep every spot it picks inside {@link Cast#arena()} (see {@code Arena.clamp}).</li>
 *   <li><b>Cool into ink</b> on the client: its drawer starts in the element's colour and ends purple, then black,
 *       where the player's version would smoke or fade.</li>
 *   <li><b>Hand anything that outlives the cast</b> (a flight, a burning floor, a falling meteor, a flower) to
 *       {@link Cast#linger}; place blocks only with {@link Cast#placeBlock}. The fight then clears all of it when it
 *       is stopped, when the copy dies or is removed, and when the server stops.</li>
 * </ol>
 *
 * <h2>Adding a spell</h2>
 * <ol>
 *   <li>A class here implementing this interface (copy {@link FireballSpell} for a throw, {@link BlinkRiftSpell} for a
 *       spell that forms where it appears).</li>
 *   <li>One line in {@link BossSpells#ALL}.</li>
 *   <li>A drawer for each effect kind it spawns, in {@code client/blockink/}, and one line per kind in
 *       {@code client.blockink.InkDrawers}.</li>
 *   <li>Its timing and damage in the contract check ({@code FirstWizardContractTest}) and a case in the fight fixture
 *       ({@code FirstWizardFightSmoke}).</li>
 * </ol>
 */
public interface BossSpell {
    /** Its row in the move table. */
    FirstWizardRules.Move move();

    /** When the parts of a cast happen; constant for the spell. */
    Timing timing();

    /**
     * Starts a cast on tick 0. Return null when the spell cannot be cast from here (no room, no target); the copy then
     * keeps moving and chooses again. The returned {@link Casting} is ticked from 0 to {@link Timing#free()}.
     */
    Casting begin(Cast cast);
}
