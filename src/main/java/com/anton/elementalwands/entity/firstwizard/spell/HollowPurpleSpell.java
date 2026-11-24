package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Hollow Purple, the finisher (phase two, low health only; {@link FirstWizardRules#usable} holds when): the copy ascends beneath two orbiting
 * stars with all three tendrils open. The stars fuse overhead and the copy pitches the orb into the lane. The orb forms over three seconds
 * and the server resolves its rolling hit along the locked heading.
 *
 * <ul>
 *   <li>The lane runs from the copy to the floor's edge, {@link #RADIUS} each side. While the orb forms it turns after
 *       the target at most {@link #TURN} degrees a tick: slower than a player sprinting round it close in, so one near
 *       it can circle out; one far away has the orb's travel to step aside after the lock.</li>
 *   <li>The aim locks {@link #LOCK_LEAD} ticks before the release, as the two stars meet.</li>
 *   <li>The orb leaves from in front of the hand at a third of its size, opens to full size over its first
 *       {@link #GROW} blocks and rolls at {@link #SPEED} a tick to the end of the lane. A body it touches takes
 *       {@link FirstWizardRules#PURPLE}, once. A jump does not clear it. It breaks no blocks.</li>
 * </ul>
 */
public final class HollowPurpleSpell implements BossSpell {
    public static final String KIND = "hollow_purple", LANE_KIND = "purple_lane";
    /** Ticks between the aim lock and the release: a little over half a second. */
    public static final int LOCK_LEAD = 13;
    /** Forms for 3 s from 0; the lane locks 0.65 s before the release; free 0.6 s after it (the orb rolls on). */
    public static final Timing TIMING = new Timing(0, 60 - LOCK_LEAD, 60, 72);
    /** The orb's radius at full size (12 blocks across: its core is twice the player's, its shell above it), and the lane's half width. */
    public static final double RADIUS = 6;
    /** Blocks over which it opens from a third of its size to full, and its speed in blocks a tick (14 a second: the player's is 26). */
    public static final double GROW = 10, SPEED = .7;
    /** Where its centre starts, out from the copy's feet, and its height over the floor at full size. */
    public static final double START = 2.2, HEIGHT = 2.6;
    /** Degrees a tick the lane turns after its target while it forms. */
    public static final float TURN = .6f;
    /** Where the orb forms, over the copy's feet: before its human hand, raised. */
    public static final double FORM_HEIGHT = 5.6;
    /** How long the orb is drawn collapsing at the end of its lane. */
    public static final int COLLAPSE = 12;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.HOLLOW_PURPLE; }
    @Override public Timing timing() { return TIMING; }

    /** The orb's radius once its centre has travelled d blocks. */
    public static double radius(double d) { return RADIUS * Math.clamp(1 / 3.0 + (2 / 3.0) * d / GROW, 1 / 3.0, 1); }

    static double levitation(double age) {
        return 1.85 * CastLevitation.ease(age / 35) * (1 - CastLevitation.ease((age - TIMING.aimLock()) / (TIMING.free() - TIMING.aimLock())));
    }

    /** Carries the merged stars forward first, then down to the exact start of the roll. */
    public static Vec3d formingCenter(Vec3d feet, double floor, Vec3d dir, double age) {
        double fall = (age - TIMING.aimLock()) / LOCK_LEAD;
        double ahead = START * CastLevitation.ease(fall * 1.6);
        double down = CastLevitation.ease((fall - .24) / .76);
        return new Vec3d(feet.x, (1 - down) * (feet.y + FORM_HEIGHT) + down * (floor + HEIGHT), feet.z)
                .add(dir.multiply(ahead));
    }

    /** The lane's far end from feet along dir: the floor's edge. */
    public static Vec3d end(Arena arena, Vec3d feet, Vec3d dir) {
        Vec3d to = feet.add(dir.multiply(arena.radius() * 2.2));
        return arena.onFloor(feet.lerp(to, arena.exit(feet, to, .5)), .5);
    }

    /** Whether the orb, its centre d blocks along the lane from origin, touches a body: a sphere of radius(d) on the floor. */
    public static boolean touches(Box box, double floor, Vec3d origin, Vec3d dir, double d) {
        double r = radius(d);
        Vec3d c = origin.add(dir.multiply(d));
        double cy = floor + Math.max(r * .45, 1);
        if (box.minY > cy + r || box.maxY < floor - .5) return false;
        return GroundZone.reach(box, c, c) <= r;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Form(cast);
    }

    private static final class Form implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity orb, lane;
        private final CastLevitation lift;
        private Vec3d dir;

        Form(Cast cast) {
            this.cast = cast;
            lift = new CastLevitation(cast);
            Vec3d feet = cast.boss().getEntityPos();
            Vec3d to = cast.target().getEntityPos().subtract(feet).multiply(1, 0, 1);
            dir = to.lengthSquared() < 1e-4 ? cast.boss().getRotationVector().multiply(1, 0, 1).normalize() : to.normalize();
            Vec3d over = new Vec3d(0, FORM_HEIGHT, 0);
            orb = cast.effect(KIND, feet.add(over), TIMING.release(), TIMING.release() + 200).size(RADIUS).anchor(over).dir(dir).aux(new Vec3d(feet.x, cast.arena().floor(), feet.z));
            lane = cast.effect(LANE_KIND, feet, TIMING.aimLock(), TIMING.release() + 200).size(RADIUS).dir(dir).aux(end(cast.arena(), feet, dir));
            cast.focus(feet.add(over));
            sound(cast, feet, SoundEvents.BLOCK_BEACON_ACTIVATE, 2f, .5f);
            sound(cast, feet, SoundEvents.ENTITY_WARDEN_DIG, 1.4f, .7f);
        }

        @Override public void tick(int t) {
            lift.height(levitation(t));
            ServerPlayerEntity target = cast.target();
            Vec3d feet = cast.boss().getEntityPos();
            if (t < TIMING.aimLock()) {
                double want = target == null ? Tendrils.angle(dir) : Tendrils.angle(target.getEntityPos().subtract(feet));
                dir = Tendrils.dir(Tendrils.turn(Tendrils.angle(dir), want, Math.toRadians(TURN)));
                cast.face(feet.add(dir), 180);
                lane.dir(dir).aux(end(cast.arena(), feet, dir));
                orb.dir(dir);
                cast.focus(feet.add(0, FORM_HEIGHT, 0));
                if (t % 20 == 0) sound(cast, feet, SoundEvents.BLOCK_BEACON_AMBIENT, 2f, .5f + t / 120f);
                return;
            }
            if (t == TIMING.aimLock()) {
                Vec3d end = end(cast.arena(), feet, dir);
                lane.dir(dir).aux(end).stage(1);
                cast.aim(end);
                sound(cast, feet, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, .6f);
            }
            if (t <= TIMING.release()) {
                Vec3d center = formingCenter(feet, cast.arena().floor(), dir, t);
                orb.anchor(center.subtract(feet));
                cast.focus(center);
            }
            if (t == TIMING.release()) {
                Vec3d origin = new Vec3d(feet.x, cast.arena().floor(), feet.z).add(dir.multiply(START));
                double length = Math.max(0, end(cast.arena(), feet, dir).subtract(origin).horizontalLength());
                orb.unanchor().aux(origin).dir(dir.multiply(SPEED)).stage(1);
                orb.setPosition(origin.add(0, HEIGHT, 0));
                lane.stage(2);
                cast.focus(null);
                cast.linger(new Roll(cast, orb, lane, origin, dir, length));
                sound(cast, feet, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.5f, .5f);
                released = true;
            }
        }

        private boolean released;

        @Override public void cancel() {
            lift.height(0);
            if (released) return;
            orb.discard();
            lane.discard();
        }
    }

    /** The orb down its lane, then its collapse at the end. */
    private static final class Roll implements Lingering {
        private final Cast cast;
        private final FirstWizardEffectEntity orb, lane;
        private final Vec3d origin, dir;
        private final double length;
        private final Set<UUID> struck = new HashSet<>();
        private int ticks;

        Roll(Cast cast, FirstWizardEffectEntity orb, FirstWizardEffectEntity lane, Vec3d origin, Vec3d dir, double length) {
            this.cast = cast; this.orb = orb; this.lane = lane; this.origin = origin; this.dir = dir; this.length = length;
        }

        @Override public boolean tick(ServerWorld world, long now) {
            double d0 = Math.min(length, ticks * SPEED);
            ticks++;
            double d1 = Math.min(length, ticks * SPEED);
            if (d0 >= length) {
                if (ticks * SPEED >= length + COLLAPSE * SPEED) { orb.discard(); lane.discard(); return false; }
                if (orb.stage() != 2) { orb.stage(2); sound(cast, origin.add(dir.multiply(length)), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2f, .4f); }
                return true;
            }
            // Four steps a tick, so a body is never stepped over.
            for (int s = 1; s <= 4; s++) {
                double d = d0 + (d1 - d0) * s / 4;
                for (ServerPlayerEntity player : cast.players())
                    if (!struck.contains(player.getUuid()) && touches(player.getBoundingBox(), cast.arena().floor(), origin, dir, d)) {
                        struck.add(player.getUuid());
                        cast.hit(player, FirstWizardRules.PURPLE, Cast.Hit.SPELL, orb);
                    }
            }
            orb.setPosition(origin.add(dir.multiply(d1)).add(0, HEIGHT, 0));
            return true;
        }

        @Override public void cancel(ServerWorld world) { orb.discard(); lane.discard(); }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
