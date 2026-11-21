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
 * Slam (phase two): the free tendril rises high over the copy while a line is marked in ink on the floor toward its
 * target, then comes straight down along the line. Sidestep after the aim locks.
 *
 * <ul>
 *   <li>The line runs from the copy {@link #LENGTH} blocks toward its target, {@link #HALF} each side (2 wide). It
 *       follows the target, turning at most {@link FirstWizardRules#AIM_TURN} a tick, until the aim lock, when its
 *       edges flare violet; the ripple runs up the tendril in the same moment.</li>
 *   <li>At the release the tendril lands along the line: a heavy hit to every body on it. A jump does not clear it
 *       (it comes down from above); a step of {@link #HALF} and a little to either side after the lock does.</li>
 *   <li>The tendril lies there a moment, then draws back.</li>
 * </ul>
 */
public final class SlamSpell implements BossSpell {
    public static final String KIND = "slam";
    /** It rises from 0; the line locks at 0.7 s; it lands at 1.1 s; free 0.6 s later. */
    public static final Timing TIMING = new Timing(0, 14, 22, 34);
    /** The tell's ripple runs from the shoulder to the tip between these ticks. */
    public static final int RIPPLE_FROM = 6, RIPPLE_TO = 14;
    /** The line: its length from the copy's feet, and half its width. */
    public static final double LENGTH = 12, HALF = 1;
    /** Where the line starts, out from the copy's feet (under the arch nearer in). */
    public static final double START = .8;
    /** Feet this high over the floor are still struck: it comes down on them. */
    public static final double REACH_UP = 2.5;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.SLAM; }
    @Override public Timing timing() { return TIMING; }

    /** The line's far end for a copy at feet facing along dir, kept on the floor. */
    public static Vec3d end(com.anton.elementalwands.entity.firstwizard.Arena arena, Vec3d feet, Vec3d dir) {
        Vec3d to = feet.add(dir.multiply(LENGTH));
        return arena.onFloor(feet.lerp(to, arena.exit(feet, to, FirstWizardRules.EDGE * .5)), FirstWizardRules.EDGE * .5);
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Slam(cast);
    }

    private static final class Slam implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity line;
        private Vec3d dir, end;

        Slam(Cast cast) {
            this.cast = cast;
            Vec3d feet = cast.boss().getEntityPos();
            ServerPlayerEntity target = cast.target();
            Vec3d to = target.getEntityPos().subtract(feet).multiply(1, 0, 1);
            dir = to.lengthSquared() < 1e-4 ? cast.boss().getRotationVector().multiply(1, 0, 1).normalize() : to.normalize();
            end = end(cast.arena(), feet, dir);
            line = cast.effect(KIND, feet, TIMING.aimLock(), TIMING.free() + 6).aux(end).dir(dir).size(HALF);
            cast.focus(null);
            sound(cast, feet, SoundEvents.ENTITY_WARDEN_ROAR, .9f, 1.6f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d feet = cast.boss().getEntityPos();
            if (t < TIMING.aimLock()) {
                // The line follows its target, no faster than the copy turns.
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
                double want = target == null ? Tendrils.angle(dir) : Tendrils.angle(target.getEntityPos().subtract(feet));
                dir = Tendrils.dir(Tendrils.turn(Tendrils.angle(dir), want, Math.toRadians(FirstWizardRules.AIM_TURN)));
                end = end(cast.arena(), feet, dir);
                line.setPosition(feet);
                line.aux(end).dir(dir);
                cast.reach(feet.add(0, 4.5, 0).add(dir.multiply(-1.2)));
                return;
            }
            if (t == TIMING.aimLock()) {
                end = end(cast.arena(), feet, dir);
                line.aux(end).dir(dir).stage(1);
                cast.aim(end);
                cast.face(end, 180);
                sound(cast, feet, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 1.2f);
            }
            if (t < TIMING.release()) { cast.reach(feet.add(0, 4.8, 0).add(dir.multiply(-.6))); return; }
            if (t == TIMING.release()) {
                line.stage(2);
                cast.reach(end);
                Vec3d from = feet.add(dir.multiply(START));
                Set<UUID> struck = new HashSet<>();
                for (ServerPlayerEntity player : cast.players())
                    if (struck.add(player.getUuid()) && Tendrils.onLine(player.getBoundingBox(), cast.arena().floor(), from, end, HALF, REACH_UP))
                        cast.hit(player, FirstWizardRules.HEAVY, Cast.Hit.SPELL, null);
                sound(cast, end, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 2.5f, .5f);
                sound(cast, end, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1f, .8f);
            }
            if (t == TIMING.free() - 4) cast.reach(null);
        }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
