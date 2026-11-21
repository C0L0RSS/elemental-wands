package com.anton.elementalwands.end;

import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * What the End's encounter needs from the boss that fights in it. {@link EndEncounter} owns the
 * roster, admission and the way home; the boss owns its fight. The First Wizard implements this.
 */
public interface EndFighter {
    /**
     * Begins the fight on a flat round arena. {@code centre} is the floor's centre block (players
     * stand on top of it, at {@code centre.getY() + 1}) and the floor is every cell within
     * {@code floorRadius} of it; the boss keeps itself and what it casts inside that. {@code skinOwner}
     * is the first player who entered: the boss wears their skin and opens with their element.
     */
    void startFight(BlockPos centre, double floorRadius, List<ServerPlayerEntity> roster, UUID skinOwner);

    /** A party member who arrived after the start. The boss grows to the larger party and keeps its share of health. */
    void addParticipant(ServerPlayerEntity player);

    /** A challenger who fell, left or logged out. */
    void removeParticipant(UUID player);

    /** Ends the fight without a result: the boss stops acting and clears everything it left on the arena. */
    void stopFight();

    /** True once the roster has beaten the boss. */
    boolean fightComplete();

    /** The boss itself. */
    Entity asEntity();
}
