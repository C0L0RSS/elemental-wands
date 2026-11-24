package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Sweep (phase two): three tendrils wind together into a low whip, a violet ripple runs from the shoulder to its tip,
 * and it swings low across the floor in a wide arc in front of the copy. Jump it, as the Pyre King's low scythe and Sky
 * Shear are jumped, or be out of its reach.
 *
 * <ul>
 *   <li>The arc is fixed at the aim lock: {@link #ARC} each side of the line to the target, from the wound's side round
 *       to the wand's. The tendril lies there, low, from then until it swings.</li>
 *   <li>It swings through the arc in {@link #SWING} ticks, reaching {@link Tendrils#REACH} blocks; a body under its
 *       arch (within {@link Tendrils#INNER}) is not struck.</li>
 *   <li>A medium hit to a body whose feet are below {@link Tendrils#CLEAR} as it passes, once per player.</li>
 * </ul>
 */
public final class SweepSpell implements BossSpell {
    public static final String KIND = "sweep";
    /** Ticks the tendril takes through its arc. */
    public static final int SWING = 10;
    /** The ripple runs from 0.2 s, the arc is fixed at 0.7 s, the swing starts at 0.9 s; free 0.4 s after it ends. */
    public static final Timing TIMING = new Timing(0, 14, 18, 18 + SWING + 8);
    /** The tell's ripple: it runs from the shoulder to the tip between these ticks. */
    public static final int RIPPLE_FROM = 4, RIPPLE_TO = 14;
    /** Half the arc, either side of the line to the target (200 degrees in all). */
    public static final double ARC = Math.toRadians(100);

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.SWEEP; }
    @Override public Timing timing() { return TIMING; }

    /**
     * The side of its wound: always its left (+1), whichever hand the skin's owner uses (the developer, Nov 19: the
     * tentacles are "obviously from the left side of the body"); the wand is in its right hand from the change on.
     */
    public static double woundSide() { return 1; }

    /**
     * The arc's start and end angles round the line to the target at angle {@code aim}: it starts on the wound's side.
     * (Facing +z, angle pi/2, a body's left is +x, angle 0: its left is its facing angle minus a quarter turn.)
     */
    public static double start(double aim, double side) { return aim - side * ARC; }
    public static double end(double aim, double side) { return aim + side * ARC; }

    /** The tendril's angle on tick t of the cast (from the start before the swing to the end after it). */
    public static double angleAt(double t, double aim, double side) {
        double k = Math.clamp((t - TIMING.release()) / SWING, 0, 1);
        // Eased in: it gathers speed out of the windup, then whips through.
        k = k * k * (3 - 2 * k);
        return start(aim, side) + (end(aim, side) - start(aim, side)) * k;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Swing(cast);
    }

    private static final class Swing implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity arc;
        private final double side;
        private final Set<UUID> struck = new HashSet<>();
        private double aim = Double.NaN;

        Swing(Cast cast) {
            this.cast = cast;
            side = woundSide();
            Vec3d feet = cast.boss().getEntityPos();
            arc = cast.effect(KIND, feet, TIMING.aimLock(), TIMING.free() + 4).size(Tendrils.REACH);
            cast.focus(null);
            sound(cast, feet, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 1.2f, .5f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d feet = cast.boss().getEntityPos();
            if (t < TIMING.aimLock()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
                double facing = Tendrils.angle(cast.boss().getRotationVector().multiply(1, 0, 1).normalize());
                cast.reach(feet.add(Tendrils.dir(start(facing, side)).multiply(Tendrils.REACH)));
                return;
            }
            if (t == TIMING.aimLock()) {
                Vec3d to = target != null ? target.getEntityPos().subtract(feet) : cast.boss().getRotationVector();
                aim = Tendrils.angle(new Vec3d(to.x, 0, to.z).lengthSquared() < 1e-4 ? cast.boss().getRotationVector() : to);
                cast.aim(feet.add(Tendrils.dir(aim).multiply(Tendrils.REACH)));
                // The arc, for clients: its middle, half width and the side it starts.
                arc.dir(Tendrils.dir(aim)).aux(new Vec3d(side, 0, 0)).stage(1);
                cast.reach(feet.add(Tendrils.dir(start(aim, side)).multiply(Tendrils.REACH)));
                sound(cast, feet, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 1.4f);
            }
            if (t == TIMING.release()) {
                arc.stage(2);
                sound(cast, feet, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2f, .5f);
                sound(cast, feet, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 1.6f, .7f);
            }
            if (t > TIMING.release() && t <= TIMING.release() + SWING) {
                double a0 = angleAt(t - 1, aim, side), a1 = angleAt(t, aim, side);
                for (ServerPlayerEntity player : cast.players()) {
                    if (struck.contains(player.getUuid()) || !Tendrils.sweeps(player.getBoundingBox(), cast.arena().floor(), feet, a0, a1, Tendrils.REACH)) continue;
                    struck.add(player.getUuid());
                    cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, null);
                }
                cast.reach(feet.add(Tendrils.dir(a1).multiply(Tendrils.REACH)));
            }
            if (t == TIMING.release() + SWING + 3) cast.reach(null);
        }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
