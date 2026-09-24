package com.anton.elementalwands.entity;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * An encounter boss. Player spells treat every boss alike: no crowd-control roots,
 * knockback, stagger or interrupts, and damage only while the encounter admits the caster.
 */
public interface WandBoss {
    boolean isBossAggressive();

    /** Whether this player belongs to the encounter and may exchange damage with the boss. */
    default boolean eligible(ServerPlayerEntity player) { return true; }

    /** Nature stacks landed; the boss decides its own capped response. */
    default void onNatureEntangle(int stacks) {}

    default void onNatureThorns() {}

    /** Client-visible earned opening shown by the Entangle wrap. */
    default boolean natureOpening() { return false; }
}
