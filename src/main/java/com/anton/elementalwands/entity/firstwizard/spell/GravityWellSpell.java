package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.network.ModNetworking;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Gravity Well: a dark orb forms over the copy's head and is lobbed to where its target stood; where it lands a well
 * opens that drags everyone over a large part of the floor toward it, then bursts in the middle. Run or dash out.
 *
 * <ul>
 *   <li>Forms for 0.9 s over the head; its landing is fixed where the target stood as it is thrown (no lead), and
 *       recorded with {@link Cast#aim} so a Meteor that follows falls on the well.</li>
 *   <li>The orb arcs there in a second. The well pulls for 4 s within {@link #PULL_RADIUS}, then builds up for
 *       0.8 s (still pulling) and bursts: a heavy hit within {@link #BURST}. Nothing else hurts.</li>
 *   <li>The pull is added to each player's own movement on their client (the Guardian's pull payload), so input
 *       still counts: walking outward loses ground, sprinting outward barely wins, a dash or blink gets out.</li>
 *   <li>One well at a time per copy, counting one in flight.</li>
 * </ul>
 */
public final class GravityWellSpell implements BossSpell {
    public static final String KIND = "gravity_orb", WELL_KIND = "gravity_well";
    /** Forms from tick 0; the landing is fixed at 0.85 s and the orb lobbed at 0.9 s; free 0.3 s later. */
    public static final Timing TIMING = new Timing(0, 17, 18, 24);
    /** The orb's radius once formed: over 2 blocks across (the player's bomb is under 1). */
    public static final double ORB_RADIUS = 1.1;
    /** The orb's centre over the copy's feet while it forms. */
    public static final double HEIGHT = 1.95 + ORB_RADIUS;
    /** Ticks in the air, and how far over the straight line the arc rises at its middle. */
    public static final int FLIGHT = 20;
    public static final double ARC = 5;
    /** Ticks it pulls once open, then ticks of buildup (still pulling) before the burst, then ticks its burst is drawn. */
    public static final int OPEN = 80, BUILDUP = 16, FADE = 16;
    /** Horizontal reach of the pull; within CALM of the middle nothing pulls further. Everyone within BURST is struck. */
    public static final double PULL_RADIUS = 11, CALM = 1.2, BURST = 5;
    /**
     * Velocity added toward the middle each tick a player is pulled (the client scales it to air control while they
     * are airborne). It sits between what vanilla adds a tick walking (0.098) and sprinting (0.127) on the ground. In
     * the fixture, standing still is dragged in at about 4.5 blocks a second, walking outward loses about 1.5 a second
     * and sprinting outward gains about 2.
     */
    public static final double PULL = .108;
    /** A body is pulled, and struck by the burst, only with its feet this close over the floor. */
    public static final double REACH_UP = 3.5;
    /** Its landing stays this far inside the floor's edge. */
    public static final double MARGIN = 4;

    /** The copies with a well in flight or open, so there is one at a time and a Meteor can find it. Server thread only. */
    private static final Map<UUID, Well> WELLS = new HashMap<>();

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.GRAVITY_WELL; }
    @Override public Timing timing() { return TIMING; }

    /** The orb's radius while it forms. */
    public static double formingRadius(double formed) { return ORB_RADIUS * (.15 + .85 * Math.pow(Math.clamp(formed, 0, 1), .7)); }

    /** Where the orb lands for a target whose feet are here: there, on the floor and inside its margin. */
    public static Vec3d landing(Arena arena, Vec3d feet) { return arena.onFloor(feet, MARGIN); }

    /** The orb along its arc, u 0 (thrown) to 1 (landed). */
    public static Vec3d arc(Vec3d launch, Vec3d landing, double u) {
        u = Math.clamp(u, 0, 1);
        return launch.lerp(landing, u).add(0, ARC * 4 * u * (1 - u), 0);
    }

    /** One tick's pull on a body whose feet are here, toward the well's middle; zero outside the pull or in its calm middle. */
    public static Vec3d pull(Vec3d centre, Vec3d feet) {
        double dx = centre.x - feet.x, dz = centre.z - feet.z, d = Math.hypot(dx, dz);
        if (d <= CALM || d > PULL_RADIUS || Math.abs(feet.y - centre.y) > REACH_UP) return Vec3d.ZERO;
        return new Vec3d(dx / d * PULL, 0, dz / d * PULL);
    }

    /** Whether this copy has a well in flight or open whose middle is at this point (a follow-up's previous aim). */
    public static boolean wellAt(FirstWizardEntity boss, Vec3d point) {
        Well well = WELLS.get(boss.getUuid());
        return well != null && point != null && Math.hypot(well.landing.x - point.x, well.landing.z - point.z) < 1;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        Well running = WELLS.get(cast.boss().getUuid());
        if (running != null && !running.finished) return null;        // one at a time
        return new Lob(cast);
    }

    private static final class Lob implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity orb;
        private Vec3d landing;
        private boolean thrown;

        Lob(Cast cast) {
            this.cast = cast;
            Vec3d over = new Vec3d(0, HEIGHT, 0);
            orb = cast.effect(KIND, cast.boss().getEntityPos().add(over), TIMING.release(), TIMING.release() + FLIGHT + 2)
                    .size(ORB_RADIUS).anchor(over);
            cast.focus(cast.boss().getEntityPos().add(over));
            sound(cast, cast.boss().getEntityPos(), SoundEvents.BLOCK_BEACON_AMBIENT, 1.6f, .5f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d launch = cast.boss().getEntityPos().add(0, HEIGHT, 0);
            if (t < TIMING.release()) cast.focus(launch);
            if (t < TIMING.aimLock()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            } else if (t == TIMING.aimLock()) {
                // Where they stand now: a step after this is a step away from where it lands.
                Vec3d feet = target != null ? target.getEntityPos() : cast.boss().getEntityPos().add(cast.boss().getRotationVector().multiply(10));
                landing = landing(cast.arena(), feet);
                cast.aim(landing);
                cast.face(landing, 180);
            }
            if (t == TIMING.release()) {
                thrown = true;
                orb.unanchor().aux(launch).dir(landing.subtract(launch)).stage(1);
                cast.focus(null);
                Well well = new Well(cast, orb, launch, landing);
                WELLS.put(cast.boss().getUuid(), well);
                cast.linger(well);
                sound(cast, launch, SoundEvents.ENTITY_ENDER_PEARL_THROW, 1.6f, .5f);
            }
        }

        @Override public void cancel() { if (!thrown) orb.discard(); }
    }

    /** The orb in flight, then the open well, its buildup and its burst. */
    private static final class Well implements Lingering {
        private final Cast cast;
        private final FirstWizardEffectEntity orb;
        private final Vec3d launch, landing;
        private final UUID boss;
        private final long thrownAt;
        private FirstWizardEffectEntity well;
        private boolean finished;

        Well(Cast cast, FirstWizardEffectEntity orb, Vec3d launch, Vec3d landing) {
            this.cast = cast; this.orb = orb; this.launch = launch; this.landing = landing;
            boss = cast.boss().getUuid();
            thrownAt = cast.now();
        }

        @Override public boolean tick(ServerWorld world, long now) {
            int t = (int) (now - thrownAt);
            if (t < FLIGHT) { orb.setPosition(arc(launch, landing, (t + 1) / (double) FLIGHT)); return true; }
            if (t == FLIGHT) {
                orb.discard();
                well = cast.effect(WELL_KIND, landing, OPEN, OPEN + BUILDUP + FADE).size(PULL_RADIUS);
                sound(cast, landing, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, .5f);
                sound(cast, landing, SoundEvents.BLOCK_PORTAL_TRIGGER, .6f, 1.6f);
            }
            int open = t - FLIGHT;
            if (open == OPEN) {
                well.stage(1);
                sound(cast, landing, SoundEvents.BLOCK_BEACON_DEACTIVATE, 2f, .5f);
            }
            if (open < OPEN + BUILDUP) {
                for (ServerPlayerEntity player : cast.players()) {
                    Vec3d pull = pull(landing, player.getEntityPos());
                    if (pull.lengthSquared() > 0) ServerPlayNetworking.send(player, new ModNetworking.GuardianPullPayload((float) pull.x, (float) pull.z));
                }
                return true;
            }
            burst();
            return false;
        }

        /** A heavy hit to everyone in the middle; then it is gone. */
        private void burst() {
            for (ServerPlayerEntity player : cast.players()) {
                var box = player.getBoundingBox();
                if (box.minY > landing.y + REACH_UP || box.maxY < landing.y - .5) continue;
                if (GroundZone.reach(box, landing, landing) <= BURST) cast.hit(player, FirstWizardRules.HEAVY, Cast.Hit.SPELL, well);
            }
            well.stage(2);
            sound(cast, landing, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.6f, .55f);
            sound(cast, landing, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 2f, .5f);
            forget();
        }

        private void forget() {
            finished = true;
            WELLS.remove(boss, this);
        }

        @Override public void cancel(ServerWorld world) {
            orb.discard();
            if (well != null) well.discard();
            forget();
        }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
