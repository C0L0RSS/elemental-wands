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
 * Leap (phase two): the three tendrils push off and unfold around the locked landing spot on the descent.
 * Move across its trajectory. Often it then roots itself and breathes fire ({@link FlameSpell}, the
 * scripted follow-up).
 *
 * <ul>
 *   <li>At the aim lock the server picks a landing within {@link #RANGE}, leading the target. The effect
 *       carries the origin and landing to the client for the launch and catch poses.</li>
 *   <li>It launches at the release and flies {@link #FLIGHT} ticks on an arc {@link #HEIGHT} high, moved a step a tick
 *       ({@link Cast#step(Vec3d, double)}), so clients slide it.</li>
 *   <li>Landing: a heavy hit to every body in the circle; a jump does not clear it.</li>
 * </ul>
 */
public final class LeapSpell implements BossSpell {
    public static final String KIND = "leap";
    /** Ticks in the air. */
    public static final int FLIGHT = 16;
    /** Coils from 0, marks its landing at 0.5 s, launches at 0.8 s, lands at 1.6 s; free 0.4 s after. */
    public static final Timing TIMING = new Timing(0, 10, 16, 16 + FLIGHT + 8);
    /** The tell's ripple runs along the tendrils between these ticks. */
    public static final int RIPPLE_FROM = 2, RIPPLE_TO = 10;
    /** The marked circle, its farthest landing from where it leaps, and its arc's height. */
    public static final double RADIUS = 3.5, RANGE = 18, HEIGHT = 3.5;
    /** A body is struck only with its feet this close over the floor (a jump does not clear it). */
    public static final double REACH_UP = 3;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.LEAP; }
    @Override public Timing timing() { return TIMING; }

    /** The tick of the cast it lands on. */
    public static int lands() { return TIMING.release() + FLIGHT; }

    /** Where it lands: where the target is led to, at most RANGE from the copy, on the floor. */
    public static Vec3d landing(Arena arena, Vec3d from, Vec3d led) {
        Vec3d to = led.subtract(from).multiply(1, 0, 1);
        if (to.length() > RANGE) to = to.normalize().multiply(RANGE);
        return arena.onFloor(from.add(to), FirstWizardRules.EDGE + 1);
    }

    /** The copy's feet k (0 to 1) of the way through its flight. */
    public static Vec3d arc(Vec3d from, Vec3d to, double k) {
        Vec3d flat = from.lerp(to, k);
        return new Vec3d(flat.x, from.y + 4 * HEIGHT * k * (1 - k), flat.z);
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Leap(cast);
    }

    private static final class Leap implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity mark;
        private Vec3d from, to;
        private boolean landed;

        Leap(Cast cast) {
            this.cast = cast;
            from = cast.boss().getEntityPos();
            mark = cast.effect(KIND, from, TIMING.aimLock(), lands() + 14).size(RADIUS);
            cast.focus(null);
            sound(cast, from, SoundEvents.ENTITY_WARDEN_DIG, 1.2f, 1.2f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            if (t < TIMING.aimLock()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
                return;
            }
            if (t == TIMING.aimLock()) {
                from = cast.boss().getEntityPos();
                Vec3d led = target != null ? cast.lead(target, (lands() - TIMING.aimLock()) / 20.0)
                        : from.add(cast.boss().getRotationVector().multiply(8));
                to = clear(landing(cast.arena(), from, led));
                cast.aim(to);
                cast.face(to, 180);
                mark.setPosition(to);
                mark.aux(from).stage(1);
                cast.reach(to);
                sound(cast, to, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 1f);
                return;
            }
            if (t < TIMING.release()) return;
            if (t == TIMING.release()) {
                mark.stage(2);
                sound(cast, from, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 1.5f);
            }
            if (t <= lands()) {
                double k = (t - TIMING.release()) / (double) FLIGHT;
                Vec3d at = arc(from, to, k);
                cast.step(at, at.y - from.y);
                cast.face(to, 180);
            }
            if (t == lands() && !landed) {
                landed = true;
                mark.stage(3);
                cast.reach(null);
                Set<UUID> struck = new HashSet<>();
                for (ServerPlayerEntity player : cast.players())
                    if (struck.add(player.getUuid()) && Tendrils.inCircle(player.getBoundingBox(), cast.arena().floor(), to, RADIUS, REACH_UP))
                        cast.hit(player, FirstWizardRules.HEAVY, Cast.Hit.SPELL, null);
                sound(cast, to, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 3f, .45f);
                sound(cast, to, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.4f, .7f);
            }
            // After landing it holds the heading it flew: a player who dodged round it is behind it for the Flame.
        }

        /** The landing moved back toward the copy until its body fits (a Stone Wall may stand there). */
        private Vec3d clear(Vec3d landing) {
            Box body = cast.boss().getBoundingBox().contract(.02);
            for (int i = 0; i <= 10; i++) {
                Vec3d at = landing.lerp(from, i / 10.0);
                if (cast.world().isSpaceEmpty(cast.boss(), body.offset(at.subtract(cast.boss().getEntityPos())))) return cast.arena().onFloor(at, FirstWizardRules.EDGE);
            }
            return from;
        }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
