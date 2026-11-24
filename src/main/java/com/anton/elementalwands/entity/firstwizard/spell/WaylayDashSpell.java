package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Waylay Dash: wind gathers behind the copy along its dash heading, then it crosses
 * that line in under half a second and leaves a low wall of churning wind and ink that burns for about 3 s. It is
 * also how the copy closes on a target that runs away.
 *
 * <ul>
 *   <li>Its line is fixed as the cast begins: through the target and {@link #BEYOND} past them, at least
 *       {@link #MIN_LENGTH} and at most {@link #MAX_LENGTH} blocks, ending inside the floor and short of anything solid.
 *       The tell lasts half a second; a step off the line during it dodges the dash.</li>
 *   <li>It crosses the line in {@link #DASH} ticks. Anyone its body passes through takes a medium hit, once.</li>
 *   <li>The wake is a {@link GroundZone} strip {@link #WAKE_HALF} each side of the line (about 2 blocks wide), armed
 *       half a second after the dash starts and burning for 3 s more. One step sideways leaves it.</li>
 *   <li>It ends facing back toward its target.</li>
 * </ul>
 */
public final class WaylayDashSpell implements BossSpell {
    public static final String KIND = "waylay_dash";
    /** Ticks it takes to cross its line. */
    public static final int DASH = 8;
    /** The line is fixed at tick 1; it dashes at 0.5 s and is free 0.2 s after it arrives. */
    public static final Timing TIMING = new Timing(0, 1, 10, 10 + DASH + 4);
    /** The line: this far past the target, within these lengths. */
    public static final double BEYOND = 10, MIN_LENGTH = 12, MAX_LENGTH = 26;
    /** A body whose footprint is this close to the copy's path is passed through (the copy is 0.6 wide). */
    public static final double BODY = .5;
    /** Half the wake's width, and ticks it lives from the dash's start (it arms after GROUND_ARM of them). */
    public static final double WAKE_HALF = 1;
    public static final int WAKE_LIFE = FirstWizardRules.GROUND_ARM + 60;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.WAYLAY_DASH; }
    @Override public Timing timing() { return TIMING; }

    /**
     * Where a dash from here at this target ends: through them and BEYOND past, between MIN_LENGTH and MAX_LENGTH,
     * stopped at the floor's margin. On the floor, so the contract check can hold it there.
     */
    public static Vec3d end(Arena arena, Vec3d from, Vec3d target) {
        Vec3d along = new Vec3d(target.x - from.x, 0, target.z - from.z);
        double distance = along.length();
        Vec3d dir = distance < 1e-3 ? new Vec3d(0, 0, 1) : along.multiply(1 / distance);
        double length = Math.clamp(distance + BEYOND, MIN_LENGTH, MAX_LENGTH);
        Vec3d to = from.add(dir.multiply(length));
        // Stopped at the floor's margin; a copy standing on (or just past) the margin measures from where it stands.
        double margin = Math.clamp(arena.radius() - arena.fromCentre(from) - 1e-4, 0, FirstWizardRules.EDGE);
        return arena.onFloor(from.lerp(to, arena.exit(from, to, margin)), FirstWizardRules.EDGE);
    }

    /** Whether the copy's body, moving from a to b, passes through a body: footprints overlapping, heights too. */
    public static boolean passes(Box box, double floor, Vec3d a, Vec3d b) {
        if (box.minY > floor + 1.8 || box.maxY < floor) return false;
        return GroundZone.reach(box, a, b) <= BODY;
    }

    @Override public Casting begin(Cast cast) {
        ServerPlayerEntity target = cast.target();
        if (target == null) return null;
        Vec3d from = cast.boss().getEntityPos();
        Vec3d end = end(cast.arena(), from, target.getEntityPos());
        // Short of anything solid on the floor (a Stone Wall): it stops where its body would no longer fit.
        Box body = cast.boss().getBoundingBox().contract(.02);
        double length = end.subtract(from).horizontalLength(), clear = 0;
        for (double s = .5; s <= length + 1e-6; s += .5) {
            Vec3d at = from.lerp(end, s / length);
            if (!cast.world().isSpaceEmpty(cast.boss(), body.offset(at.subtract(from)))) break;
            clear = s;
        }
        if (clear < MIN_LENGTH * .5) return null;
        if (clear < length - 1e-6) end = from.lerp(end, clear / length);
        return new Dash(cast, from, new Vec3d(end.x, from.y, end.z));
    }

    private static final class Dash implements Casting {
        private final Cast cast;
        private final Vec3d from, end;
        private final FirstWizardEffectEntity line;
        private final Set<UUID> struck = new HashSet<>();
        private Vec3d at;

        Dash(Cast cast, Vec3d from, Vec3d end) {
            this.cast = cast; this.from = from; this.end = end; this.at = from;
            cast.aim(end);
            cast.focus(null);
            line = cast.effect(KIND, from, TIMING.release(), TIMING.release() + WAKE_LIFE + 2).aux(end).dir(end.subtract(from)).size(WAKE_HALF);
            sound(cast, from, SoundEvents.ENTITY_BREEZE_INHALE, 1.6f, .55f);
        }

        @Override public void tick(int t) {
            int release = TIMING.release();
            if (t < release) { cast.face(end, FirstWizardRules.AIM_TURN); return; }
            if (t == release) {
                line.stage(1);
                cast.linger(GroundZone.strip(cast, from, end, WAKE_HALF, WAKE_LIFE, FirstWizardRules.GROUND, line));
                sound(cast, from, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2f, .6f);
                return;
            }
            if (t <= release + DASH) {
                Vec3d next = from.lerp(end, (t - release) / (double) DASH);
                for (ServerPlayerEntity player : cast.players()) {
                    if (struck.contains(player.getUuid()) || !passes(player.getBoundingBox(), cast.arena().floor(), at, next)) continue;
                    struck.add(player.getUuid());
                    cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, null);
                }
                at = next;
                cast.step(next);
                cast.face(end, 180);
            }
            if (t == release + DASH) {
                ServerPlayerEntity target = cast.target();
                cast.face(target != null ? target.getEntityPos() : from, 180);
                sound(cast, end, SoundEvents.ENTITY_BREEZE_LAND, 1.4f, .6f);
            } else if (t > release + DASH) {
                ServerPlayerEntity target = cast.target();
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            }
        }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
