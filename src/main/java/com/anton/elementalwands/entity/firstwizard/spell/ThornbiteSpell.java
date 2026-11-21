package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Thornbite: a giant flytrap forms over the copy's wand, lunges far along a locked line on braided vines and snaps at
 * the first player its mouth reaches. A bite is a heavy hit and heals the copy, which a pulse running back along the
 * vines into it shows; a miss heals nothing. Then it draws back.
 *
 * <ul>
 *   <li>Forms for 0.8 s over the copy's head and a little ahead, ink taking Nature's colour as it grows into a mouth
 *       3 blocks across (the player's is under half a block).</li>
 *   <li>Its aim locks 0.2 s before the lunge: the line from the copy to where the target stands then. A sidestep after
 *       the lock dodges it.</li>
 *   <li>It lunges 16 blocks (less at the floor's edge or a wall) in about a third of a second, dropping from over the
 *       copy's head to body height by the locked target's distance, and snaps on the first body within reach.</li>
 *   <li>A bite that lands heals the copy by {@link #HEAL} of its maximum health when the pulse reaches it.</li>
 * </ul>
 *
 * <p>The effect entity's {@code dir} carries the lunge: its horizontal part is the locked direction times the length
 * (the full reach while lunging, where it shut once it has shut), and its {@code y} the distance over which the mouth
 * drops to body height. {@code aux} is where the lunge left from. Stages: 0 forming, 1 lunging, 2 shut on nothing (or a
 * shield), 3 bit and healing.</p>
 */
public final class ThornbiteSpell implements BossSpell {
    public static final String KIND = "thornbite";
    /** Forms from tick 0, aim locks at 0.6 s, lunges at 0.8 s; free once it has drawn back. */
    public static final Timing TIMING = new Timing(0, 12, 16, 36);
    /** The mouth's radius once formed: 3 blocks across. */
    public static final double MOUTH_RADIUS = 1.5;
    /** A body this close to the mouth's centre is bitten: well inside the drawn jaws, so a near miss misses. */
    public static final double REACH = 1.0;
    /** How far it lunges, and in how many ticks: 16 blocks in about a third of a second. */
    public static final double LENGTH = 16;
    public static final int LUNGE = 7;
    public static final double SPEED = LENGTH / LUNGE;
    /** Ticks it holds shut, then takes to draw back; ticks the pulse takes to run back along the vines. */
    public static final int HOLD = 4, RETRACT = 8, PULSE = 8;
    /** A bite heals the copy by this share of its maximum health. */
    public static final float HEAL = .04f;
    /** Where it forms: this high over the copy's feet and this far ahead of it. Body height for the bite. */
    public static final double FORM_HEIGHT = 3.5, FORM_AHEAD = 1.0, BITE_HEIGHT = 1.1;
    /** The mouth drops to body height over the target's distance at the lock, within these. */
    public static final double DROP_MIN = 1.5, DROP_MAX = 5;
    public static final int FORMING = 0, LUNGING = 1, MISSED = 2, BIT = 3;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.THORNBITE; }
    @Override public Timing timing() { return TIMING; }

    /** The mouth's size while it forms, as a share of its full size. */
    public static double formingScale(double formed) { return .15 + .85 * Math.pow(Math.clamp(formed, 0, 1), .7); }

    /** The mouth's height over the floor after it has lunged {@code s} blocks, dropping to body height over {@code drop}. */
    public static double height(double s, double drop) {
        double k = Math.clamp(s / Math.max(.5, drop), 0, 1);
        k = k * k * (3 - 2 * k);
        return FORM_HEIGHT + (BITE_HEIGHT - FORM_HEIGHT) * k;
    }

    /** The mouth's centre after {@code s} blocks of a lunge that left {@code launch} along the unit {@code dir}. */
    public static Vec3d mouth(Vec3d launch, Vec3d dir, double s, double drop, double floor) {
        return new Vec3d(launch.x + dir.x * s, floor + height(s, drop), launch.z + dir.z * s);
    }

    /** How far the mouth drops over, given the target's horizontal distance from the launch at the lock. */
    public static double drop(double targetDistance) { return Math.clamp(targetDistance, DROP_MIN, DROP_MAX); }

    /** The lunge's length from a launch point: its full reach, cut short where it would leave the floor. */
    public static double reach(Arena arena, Vec3d launch, Vec3d dir) {
        Vec3d end = launch.add(dir.x * LENGTH, 0, dir.z * LENGTH);
        return LENGTH * arena.exit(launch, end, .5);
    }

    /** Whether a body is bitten by a mouth centred here. */
    public static boolean bites(Box body, Vec3d mouth) { return body.squaredMagnitude(mouth) <= REACH * REACH; }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Bite(cast);
    }

    private static final class Bite implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity trap;
        private Vec3d dir, launch;
        private double length, drop, shutAt = -1;
        private int healAt = -1;

        Bite(Cast cast) {
            this.cast = cast;
            Vec3d facing = facing();
            trap = cast.effect(KIND, formPoint(facing), TIMING.release(), TIMING.free() + 20)
                    .size(MOUTH_RADIUS).anchor(offset(facing)).dir(facing);
            cast.focus(formPoint(facing));
            sound(cast.boss().getEntityPos(), SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1.6f, .5f);
        }

        private Vec3d facing() {
            float yaw = cast.boss().getBodyYaw() * (float) (Math.PI / 180);
            return new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        private static Vec3d offset(Vec3d facing) { return facing.multiply(FORM_AHEAD).add(0, FORM_HEIGHT, 0); }
        private Vec3d formPoint(Vec3d facing) { return cast.boss().getEntityPos().add(offset(facing)); }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            if (t < TIMING.aimLock()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
                Vec3d facing = facing();
                trap.anchor(offset(facing)).dir(facing);
                cast.focus(formPoint(facing));
            } else if (t == TIMING.aimLock()) {
                // Locked: the line from the copy to where the target stands now. From here a sidestep dodges it.
                Vec3d boss = cast.boss().getEntityPos();
                Vec3d to = target != null ? target.getEntityPos().subtract(boss).multiply(1, 0, 1) : facing();
                dir = to.lengthSquared() < .25 ? facing() : to.normalize();
                cast.face(boss.add(dir.multiply(4)), 180);
                trap.anchor(offset(dir)).dir(dir);
                launch = formPoint(dir);
                double distance = target != null ? Math.hypot(target.getX() - launch.x, target.getZ() - launch.z) : DROP_MAX;
                drop = drop(distance);
                cast.aim(cast.arena().onFloor(launch.add(dir.multiply(Math.min(distance, LENGTH))), FirstWizardRules.EDGE));
                cast.focus(launch);
            }
            if (t == TIMING.release()) {
                length = reach(cast.arena(), launch, dir);
                trap.unanchor().aux(launch).dir(new Vec3d(dir.x * length, drop, dir.z * length)).stage(LUNGING);
                cast.focus(null);
                sound(launch, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 1.8f, .55f);
                sound(launch, SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_UP, 1.6f, .6f);
            }
            if (t > TIMING.release() && shutAt < 0) lunge(t - TIMING.release());
            if (t == healAt && cast.boss().isAlive()) {
                cast.boss().heal(HEAL * cast.boss().getMaxHealth());
                sound(cast.boss().getEntityPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, .8f);
            }
        }

        /** One tick of the lunge: six steps, the first body within reach, a block or the end of its reach. */
        private void lunge(int tick) {
            double from = Math.min(length, SPEED * (tick - 1)), to = Math.min(length, SPEED * tick);
            double floor = cast.arena().floor();
            for (int step = 1; step <= 6; step++) {
                double s = from + (to - from) * step / 6.0, before = from + (to - from) * (step - 1) / 6.0;
                Vec3d at = mouth(launch, dir, s, drop, floor);
                for (ServerPlayerEntity player : cast.players()) {
                    if (!bites(player.getBoundingBox(), at)) continue;
                    boolean hurt = cast.hit(player, FirstWizardRules.HEAVY, Cast.Hit.SPELL, trap);
                    shut(s, hurt, tick);
                    return;
                }
                var wall = cast.world().raycast(new RaycastContext(mouth(launch, dir, before, drop, floor), at,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, trap));
                if (wall.getType() != HitResult.Type.MISS) { shut(Math.max(0, before), false, tick); return; }
            }
            if (to >= length) shut(length, false, tick);
        }

        /** Snaps shut where it is: a bite starts the pulse back to the copy; anything else heals nothing. */
        private void shut(double s, boolean bit, int tick) {
            shutAt = s;
            trap.dir(new Vec3d(dir.x * s, drop, dir.z * s)).stage(bit ? BIT : MISSED);
            Vec3d at = mouth(launch, dir, s, drop, cast.arena().floor());
            sound(at, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 2f, bit ? .7f : .9f);
            if (bit) {
                healAt = TIMING.release() + tick + PULSE;
                sound(at, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.6f, .6f);
            }
        }

        private void sound(Vec3d at, SoundEvent event, float volume, float pitch) {
            cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
        }
    }
}
