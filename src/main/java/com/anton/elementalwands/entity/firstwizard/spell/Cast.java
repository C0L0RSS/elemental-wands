package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * What the fight hands a spell for one cast: the copy, its floor, its target and the tools to aim, hit and leave
 * things behind. It stays valid after the copy is free, so a {@link Lingering} effect may keep using it. Every hit,
 * block and effect made through it is cleared by the fight when it ends.
 */
public interface Cast {
    /** How a hit is dealt. SPELL is a blow a raised shield can stop; GROUND is lingering ground, which ignores shields. */
    enum Hit { SPELL, GROUND }

    FirstWizardEntity boss();
    ServerWorld world();
    /** The floor it fights on. */
    Arena arena();
    /** The world clock, and the tick this cast began. */
    long now();
    long started();
    Random random();

    /** Who the cast was aimed at; null if they are gone (logged off, died, left the floor). */
    ServerPlayerEntity target();
    /** Everyone the fight can hurt now. */
    List<ServerPlayerEntity> players();
    /** Someone stands within {@code FirstWizardRules.CROWD} of the copy. */
    boolean crowded();

    /** A player's recent horizontal velocity, in blocks per tick (averaged over the last few ticks). */
    Vec3d velocity(ServerPlayerEntity player);
    /** Where a player's feet will be after this many seconds if they keep moving as they are, on the floor. */
    Vec3d lead(ServerPlayerEntity player, double seconds);

    /** Turns the copy toward a point by at most this many degrees; returns how far it turned. */
    float face(Vec3d point, float limitDegrees);
    /** Where forming ink gathers, so clients draw it rising off the wand arm to there; null for none. */
    void focus(Vec3d point);
    /** Records where this cast was aimed, for a follow-up to use. */
    void aim(Vec3d point);
    /** Where the cast before this one was aimed, or null. */
    Vec3d previousAim();
    /** Moves the copy to a point on the floor at once (Blink Rift). */
    void teleport(Vec3d feet);
    /** Moves the copy to a point on the floor this tick, which clients slide it to rather than snap (a dash, step by step). */
    void step(Vec3d feet);
    /** The same, with its feet this high over the floor (a leap's arc); the point is still kept over the floor. */
    void step(Vec3d feet, double height);
    /**
     * Where its free tendril reaches during an ink move (a slam's line end, a sweep's start), for clients to draw it
     * there; null when it is back at its side. Cleared when the copy is free.
     */
    void reach(Vec3d point);
    /**
     * Moves the cast's clock on by this many ticks, which are skipped (the change's cinematic, once every watcher has
     * skipped it): the next tick is that many later, and clients follow (the cast's age and the change's clock jump).
     */
    void advance(int ticks);

    /**
     * Spawns a vanilla entity the copy leaves (the flame's cloud of breath): tagged as the End encounter's actor so the
     * encounter keeps it, and discarded with everything else when the fight ends. Returns it.
     */
    <T extends Entity> T adopt(T entity);

    /** Spawns an effect entity for clients to draw, starting now. It is discarded at the end of its life or of the fight. */
    FirstWizardEffectEntity effect(String kind, Vec3d at, int form, int life);
    /** Keeps something running after the copy is free; see {@link Lingering}. */
    void linger(Lingering effect);

    /**
     * Hurts a player for the copy, on the server. False when they may not be hurt (not eligible, creative, spectating,
     * an ally of the copy, dead) or the game refused it (a shield, a hurt cooldown). {@code source} is the thing that
     * struck (a fireball's effect entity), so a shield is raised against where it really is; null means the copy.
     */
    boolean hit(ServerPlayerEntity player, float amount, Hit kind, Entity source);

    /** Sets a temporary block, remembered so the fight puts back what was there when it ends. False if it may not. */
    boolean placeBlock(BlockPos pos, BlockState state);
    /** Puts back what was at a temporary block now. */
    void restoreBlock(BlockPos pos);
}
