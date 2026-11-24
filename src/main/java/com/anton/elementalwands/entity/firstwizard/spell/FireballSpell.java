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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Fireball: a ball of fire several blocks across forms over the copy's head, is thrown slowly enough to sidestep and
 * bursts on the floor, a player or a wall, leaving burning ground. The example of a thrown spell.
 *
 * <ul>
 *   <li>Forms for 1.2 s, ink rising off the wand arm and swelling into a ball 3 blocks across (the player's is under 1).</li>
 *   <li>Its aim locks a quarter of a second before release, leading a moving target by its velocity.</li>
 *   <li>It flies at 16 blocks a second toward where their feet will be, and goes on past them to the floor if they have
 *       stepped aside. A body it touches takes a heavy hit; anyone else within the burst a medium one.</li>
 *   <li>The burst leaves burning ground 5 blocks across for 5 s (a {@link GroundZone}): the copy's own fire, drawn
 *       by its client drawer, never vanilla fire, so it neither spreads nor lights anything.</li>
 * </ul>
 */
public final class FireballSpell implements BossSpell {
    public static final String KIND = "fireball", GROUND_KIND = "fireball_ground";
    /** Forms from tick 0, aim locks at 0.95 s, thrown at 1.2 s, free 0.3 s later. */
    public static final Timing TIMING = new Timing(0, 19, 24, 30);
    /** The ball's radius once formed: 3 blocks across. */
    public static final double RADIUS = 1.5;
    /** Blocks per tick: 16 a second. */
    public static final double SPEED = 16 / 20.0;
    /** A body this close to the ball's centre is struck: a little inside its drawn size, so a near miss misses. */
    public static final double REACH = 1.2;
    /** Everyone within this of the burst takes a medium hit. */
    public static final double BURST = 2.5;
    /** The burning ground: its radius, and how long it burns. */
    public static final double GROUND_RADIUS = 2.5;
    public static final int GROUND_LIFE = 100;
    /** The ball's centre is this far over the copy's feet once formed (its underside just over the head). */
    public static final double HEIGHT = 2.60 + RADIUS;
    /** A flight that has met nothing for this long bursts where it is. */
    public static final int MAX_FLIGHT = 80;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.FIREBALL; }
    @Override public Timing timing() { return TIMING; }

    /** The ball's radius while it forms, from a bead of ink to its full size. */
    public static double formingRadius(double formed) { return RADIUS * (.12 + .88 * Math.pow(Math.clamp(formed, 0, 1), .7)); }

    public static double formingHeight(boolean torn, double formed) {
        return HEIGHT - RADIUS + formingRadius(formed) + (torn ? FirstWizardRules.LIFT : 0);
    }

    static double levitation(double age) {
        return 1.35 * CastLevitation.ease(age / 17) * (1 - CastLevitation.ease((age - TIMING.release()) / (TIMING.free() - TIMING.release())));
    }

    /**
     * Where to throw: the point the ball should reach, given where it leaves, the target's feet now and its velocity
     * (blocks per tick). It leads them by the time the ball takes to get there (two passes), at most LEAD_MAX, and
     * aims a little above their feet so it bursts on the floor where they will stand. Kept on the floor.
     */
    public static Vec3d aimPoint(Arena arena, Vec3d launch, Vec3d feet, Vec3d velocity) {
        Vec3d point = feet;
        for (int pass = 0; pass < 2; pass++) {
            double ticks = launch.distanceTo(point.add(0, .3, 0)) / SPEED;
            Vec3d lead = new Vec3d(velocity.x, 0, velocity.z).multiply(ticks);
            if (lead.length() > FirstWizardRules.LEAD_MAX) lead = lead.normalize().multiply(FirstWizardRules.LEAD_MAX);
            point = arena.clamp(feet.add(lead), FirstWizardRules.EDGE);
        }
        return new Vec3d(point.x, arena.floor() + .3, point.z);
    }

    @Override public Casting begin(Cast cast) {
        ServerPlayerEntity target = cast.target();
        if (target == null) return null;
        return new Throw(cast);
    }

    private static final class Throw implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity ball;
        private final CastLevitation lift;
        private Vec3d aim;
        private boolean released;

        Throw(Cast cast) {
            this.cast = cast;
            lift = new CastLevitation(cast);
            Vec3d over = new Vec3d(0, formingHeight(cast.boss().torn(), 0), 0);
            ball = cast.effect(KIND, cast.boss().getEntityPos().add(over), TIMING.release(), TIMING.release() + MAX_FLIGHT + 20)
                    .size(RADIUS).anchor(over);
            cast.focus(cast.boss().getEntityPos().add(over));
            sound(cast, cast.boss().getEntityPos(), SoundEvents.ITEM_FIRECHARGE_USE, 1.2f, .5f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            lift.height(levitation(t));
            if (released) return;
            Vec3d feetNow = cast.boss().getEntityPos();
            Vec3d launch = feetNow.add(0, formingHeight(cast.boss().torn(), 1), 0);
            Vec3d over = new Vec3d(0, formingHeight(cast.boss().torn(), (double) t / TIMING.release()), 0);
            ball.anchor(over);
            cast.focus(feetNow.add(over));
            if (t < TIMING.aimLock()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            } else if (t == TIMING.aimLock()) {
                // Locked: from here a sidestep dodges it.
                Vec3d feet = target != null ? target.getEntityPos() : cast.boss().getEntityPos().add(cast.boss().getRotationVector().multiply(12));
                aim = aimPoint(cast.arena(), launch, feet, target != null ? cast.velocity(target) : Vec3d.ZERO);
                cast.aim(aim);
                cast.face(aim, 180);
            }
            if (t == TIMING.release()) {
                Vec3d velocity = aim.subtract(launch).normalize().multiply(SPEED);
                ball.unanchor().aux(launch).dir(velocity).stage(1);
                cast.focus(null);
                released = true;
                cast.linger(new Flight(cast, ball, launch, velocity));
                sound(cast, launch, SoundEvents.ENTITY_BLAZE_SHOOT, 2f, .5f);
            }
        }
        @Override public void cancel() {
            lift.height(0);
            if (!released) ball.discard();
        }
    }

    /** The ball in flight, then its burst and burning ground. */
    private static final class Flight implements Lingering {
        private final Cast cast;
        private final FirstWizardEffectEntity ball;
        private final Vec3d launch, velocity;
        private int ticks;
        private boolean burst;

        Flight(Cast cast, FirstWizardEffectEntity ball, Vec3d launch, Vec3d velocity) {
            this.cast = cast; this.ball = ball; this.launch = launch; this.velocity = velocity;
        }

        @Override public boolean tick(ServerWorld world, long now) {
            if (burst) return false;
            Vec3d from = launch.add(velocity.multiply(ticks));
            ticks++;
            Vec3d to = launch.add(velocity.multiply(ticks));
            // Five steps a tick: a body, the floor, a wall or the floor's edge, whichever it meets first.
            for (int step = 1; step <= 5; step++) {
                Vec3d at = from.lerp(to, step / 5.0), before = from.lerp(to, (step - 1) / 5.0);
                for (ServerPlayerEntity player : cast.players())
                    if (reaches(player.getBoundingBox(), at)) { burst(world, at, player); return false; }
                var wall = world.raycast(new RaycastContext(before, at, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ball));
                if (wall.getType() != HitResult.Type.MISS) { burst(world, wall.getPos(), null); return false; }
                if (at.y - REACH * .5 <= cast.arena().floor() || !cast.arena().inside(at, 0) || ticks >= MAX_FLIGHT) { burst(world, at, null); return false; }
            }
            ball.setPosition(to);
            return true;
        }

        /** Bursts at a point: the struck body takes a heavy hit, everyone else near a medium one, and the floor burns. */
        private void burst(ServerWorld world, Vec3d at, ServerPlayerEntity struck) {
            burst = true;
            Set<UUID> hurt = new HashSet<>();
            if (struck != null && cast.hit(struck, FirstWizardRules.HEAVY, Cast.Hit.SPELL, ball)) hurt.add(struck.getUuid());
            for (ServerPlayerEntity player : cast.players()) {
                if (player == struck || hurt.contains(player.getUuid())) continue;
                if (player.getBoundingBox().squaredMagnitude(at) <= BURST * BURST) cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, ball);
            }
            ball.setPosition(at);
            ball.aux(at).stage(2).life((int) (cast.now() - ball.start()) + 8);
            Vec3d ground = cast.arena().onFloor(at, FirstWizardRules.EDGE);
            var floor = cast.effect(GROUND_KIND, ground, FirstWizardRules.GROUND_ARM, GROUND_LIFE).size(GROUND_RADIUS);
            cast.linger(GroundZone.disc(cast, ground, GROUND_RADIUS, GROUND_LIFE, FirstWizardRules.GROUND, floor));
            sound(cast, at, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.6f, .7f);
            sound(cast, at, SoundEvents.ITEM_FIRECHARGE_USE, 2f, .6f);
        }

        @Override public void cancel(ServerWorld world) { ball.discard(); }
    }

    /** Whether the ball, centred here, touches a body: the nearest point of its box within REACH. */
    static boolean reaches(Box box, Vec3d centre) { return box.squaredMagnitude(centre) <= REACH * REACH; }

    private static void sound(Cast cast, Vec3d at, net.minecraft.sound.SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
