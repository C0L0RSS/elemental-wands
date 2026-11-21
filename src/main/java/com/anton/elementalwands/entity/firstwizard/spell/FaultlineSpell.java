package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/**
 * Faultline: ink runs along the floor from the copy toward its target, showing the whole wave's shape; its aim locks a
 * quarter of a second before the release, and a wave of stone spikes runs down it to the floor's edge. Whoever it
 * reaches takes a medium hit and is thrown high, for the copy to hit again (a follow-up in the rules); the fall from
 * that throw is forgiven.
 *
 * <ul>
 *   <li><b>The shape</b> is {@link #HALF_NEAR} each side at the copy, widening to {@link #HALF_FAR} at
 *       {@link #FAR}, and runs to the floor's edge. It follows the target while it forms, then locks: a sidestep after
 *       the lock dodges it. A jump does not: the spikes are taller than a jump ({@link #REACH_UP}).</li>
 *   <li><b>The wave</b> runs at {@link #SPEED} in {@link #LANES} lanes; a lane ends at anything solid in its way (a
 *       wall, the copy's own Stone Wall) and at the floor's edge, and the client draws the same lanes from the same
 *       blocks ({@link #laneEnd}).</li>
 *   <li><b>A player it reaches</b> takes {@link FirstWizardRules#MEDIUM} once per cast and is thrown up at
 *       {@link #THROW} (about 7 blocks). Their fall damage is held off until they have landed.</li>
 * </ul>
 */
public final class FaultlineSpell implements BossSpell {
    public static final String KIND = "faultline";
    /**
     * Forms from tick 0 (0.9 s), aim locks at 0.6 s, released at 0.9 s, free 0.3 s later. From 10 blocks out (its row's
     * nearest) a sprint from the wave's middle that starts at the lock clears its edge before it arrives.
     */
    public static final Timing TIMING = new Timing(0, 12, 18, 24);
    /** Half its width at the copy and at FAR blocks out (the player's: 1 and 4 at 20). */
    public static final double HALF_NEAR = 2.5, HALF_FAR = 6, FAR = 50;
    /** Blocks per tick: 30 a second. */
    public static final double SPEED = 30 / 20.0;
    /** Lanes across its width; the client draws a spike per lane per block. */
    public static final int LANES = 11;
    /** It ends this far inside the floor's edge. */
    public static final double MARGIN = .5;
    /** A body whose feet are this close above the floor is reached: the spikes stand taller than a jump. */
    public static final double REACH_UP = 2.4;
    /** Upward speed of the throw, blocks per tick: about 7 blocks high. */
    public static final double THROW = 1.1;
    /** Ticks a thrown player's fall is forgiven at most, if they never land. */
    public static final int FORGIVE = 100;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.FAULTLINE; }
    @Override public Timing timing() { return TIMING; }

    /** Half its width this far out from the copy. */
    public static double half(double along) { return HALF_NEAR + (HALF_FAR - HALF_NEAR) * Math.clamp(along / FAR, 0, 1.5); }

    /** How far the wave runs from origin along a unit direction: to the floor's edge. */
    public static double length(Arena arena, Vec3d origin, Vec3d dir) {
        Vec3d far = origin.add(dir.multiply(arena.radius() * 2.2));
        return origin.distanceTo(far) * arena.exit(origin, far, MARGIN);
    }

    /** A lane's offset across the wave, -1 at one side to 1 at the other. */
    public static double laneSide(int lane) { return LANES == 1 ? 0 : lane * 2.0 / (LANES - 1) - 1; }

    /** Where a lane is at this distance out, on the floor. */
    public static Vec3d lanePoint(Vec3d origin, Vec3d dir, int lane, double along) {
        Vec3d across = new Vec3d(-dir.z, 0, dir.x);
        return origin.add(dir.multiply(along)).add(across.multiply(laneSide(lane) * half(along)));
    }

    /**
     * How far a lane runs before it meets something solid at foot or head height, or leaves the floor; at most
     * {@code length}. The server and the client both use this, from the blocks they see; the server knows the floor
     * ({@code onFloor} is its arena), a client only the blocks under the lane.
     */
    public static double laneEnd(Predicate<BlockPos> solid, Vec3d origin, Vec3d dir, int lane, double length, Predicate<Vec3d> onFloor) {
        int floorY = (int) Math.floor(origin.y + 1e-6);
        double last = .5;
        for (double along = 1; ; along += .5) {
            boolean end = along >= length - 1e-6;
            if (end) along = length;
            Vec3d at = lanePoint(origin, dir, lane, along);
            if (!onFloor.test(at)) return last;
            for (int dy = 0; dy < 2; dy++) if (solid.test(BlockPos.ofFloored(at.x, floorY + dy, at.z))) return last;
            last = along;
            if (end) return length;
        }
    }

    /** Whether a block in a world stops a lane: anything with a collision shape. */
    public static Predicate<BlockPos> solid(BlockView world) {
        return pos -> !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    /**
     * The lane nearest a point and how far across the wave the point is (in half-widths), from origin along dir. A
     * point is in the wave when |side| <= 1 plus its own half width over the wave's half width.
     */
    public static double side(Vec3d origin, Vec3d dir, Vec3d point) {
        Vec3d across = new Vec3d(-dir.z, 0, dir.x);
        double along = (point.x - origin.x) * dir.x + (point.z - origin.z) * dir.z;
        double off = (point.x - origin.x) * across.x + (point.z - origin.z) * across.z;
        return off / half(Math.max(0, along));
    }

    /** Whether a body's footprint is inside the wave's shape at its distance out (horizontal test only). */
    public static boolean inShape(Vec3d origin, Vec3d dir, Vec3d feet, double halfWidth) {
        Vec3d across = new Vec3d(-dir.z, 0, dir.x);
        double along = (feet.x - origin.x) * dir.x + (feet.z - origin.z) * dir.z;
        double off = Math.abs((feet.x - origin.x) * across.x + (feet.z - origin.z) * across.z);
        return along >= -halfWidth && off <= half(Math.max(0, along)) + halfWidth;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Forming(cast);
    }

    /** The unit direction from the copy toward a point, flat. */
    static Vec3d toward(Vec3d from, Vec3d to) {
        Vec3d d = new Vec3d(to.x - from.x, 0, to.z - from.z);
        return d.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : d.normalize();
    }

    private static final class Forming implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity effect;
        private Vec3d origin, dir;
        private double length;

        Forming(Cast cast) {
            this.cast = cast;
            cast.focus(null);
            origin = cast.arena().onFloor(cast.boss().getEntityPos(), 0);
            dir = toward(origin, cast.target().getEntityPos());
            length = length(cast.arena(), origin, dir);
            effect = cast.effect(KIND, origin, TIMING.release(), TIMING.release() + (int) Math.ceil(length / SPEED) + 30)
                    .dir(dir.multiply(length));
            cast.world().playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.5f, .45f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            if (t < TIMING.aimLock()) {
                // While it forms the ink follows the target, as the copy turns to it.
                if (target != null) {
                    cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
                    aimAt(target.getEntityPos());
                }
            } else if (t == TIMING.aimLock()) {
                // Locked: from here a sidestep dodges it.
                if (target != null) aimAt(target.getEntityPos());
                cast.face(origin.add(dir), 180);
                cast.aim(origin.add(dir.multiply(Math.min(length, target != null ? target.getEntityPos().distanceTo(origin) : 12))));
            }
            if (t == TIMING.release()) {
                effect.stage(1).life((int) (cast.now() - effect.start()) + (int) Math.ceil(length / SPEED) + 30);
                cast.linger(new Wave(cast, effect, origin, dir, length));
                cast.world().playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 2f, .5f);
            }
        }

        private void aimAt(Vec3d point) {
            dir = toward(origin, point);
            length = length(cast.arena(), origin, dir);
            effect.dir(dir.multiply(length));
        }
    }

    /** The wave running down the locked line, then the thrown players' falls held off until they land. */
    private static final class Wave implements Lingering {
        private final Cast cast;
        private final FirstWizardEffectEntity effect;
        private final Vec3d origin, dir;
        private final double length;
        private final double[] ends = new double[LANES];
        private final Set<UUID> struck = new HashSet<>();
        /** Thrown players and the tick their fall stops being forgiven. */
        private final Map<UUID, Long> thrown = new HashMap<>();
        private final Map<UUID, Boolean> leftGround = new HashMap<>();
        private final long start;
        private double front;

        Wave(Cast cast, FirstWizardEffectEntity effect, Vec3d origin, Vec3d dir, double length) {
            this.cast = cast; this.effect = effect; this.origin = origin; this.dir = dir; this.length = length;
            this.start = cast.now();
            for (int lane = 0; lane < LANES; lane++) ends[lane] = laneEnd(solid(cast.world()), origin, dir, lane, length, at -> cast.arena().inside(at, MARGIN));
        }

        @Override public boolean tick(ServerWorld world, long now) {
            double from = front;
            front = Math.min(length, (now - start + 1) * SPEED);
            if (from < length) reach(world, from, front);
            forgive(now);
            return front < length || !thrown.isEmpty();
        }

        /** Everyone the front passed over this tick, in a lane still running there, with their feet low enough. */
        private void reach(ServerWorld world, double from, double to) {
            double floor = cast.arena().floor();
            for (ServerPlayerEntity player : cast.players()) {
                if (struck.contains(player.getUuid())) continue;
                Box body = player.getBoundingBox();
                if (body.minY > floor + REACH_UP || body.maxY < floor - .5) continue;
                Vec3d feet = player.getEntityPos();
                double half = player.getWidth() / 2;
                double along = (feet.x - origin.x) * dir.x + (feet.z - origin.z) * dir.z;
                if (along + half < from - .5 || along - half > to) continue;
                if (!inShape(origin, dir, feet, half)) continue;
                int lane = (int) Math.round((Math.clamp(side(origin, dir, feet), -1, 1) + 1) / 2 * (LANES - 1));
                if (Math.max(0, along - half) > ends[lane]) continue;
                struck.add(player.getUuid());
                cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, effect);
                // Thrown up whether or not a shield took the blow: the ground itself heaves.
                player.setVelocity(dir.x * .2, THROW, dir.z * .2);
                player.velocityModified = true;
                player.fallDistance = 0;
                thrown.put(player.getUuid(), cast.now() + FORGIVE);
                world.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 1.6f, .6f);
            }
        }

        /** A thrown player's fall is forgiven until they are back on the ground (or FORGIVE ticks pass). */
        private void forgive(long now) {
            thrown.entrySet().removeIf(entry -> {
                ServerPlayerEntity player = cast.world().getServer().getPlayerManager().getPlayer(entry.getKey());
                if (player == null || !player.isAlive() || now > entry.getValue()) { leftGround.remove(entry.getKey()); return true; }
                player.fallDistance = 0;
                boolean airborne = !player.isOnGround();
                if (airborne) leftGround.put(entry.getKey(), true);
                // Landed after leaving the ground: one more tick of grace, then done.
                if (!airborne && leftGround.getOrDefault(entry.getKey(), false)) { leftGround.remove(entry.getKey()); return true; }
                return false;
            });
        }

        @Override public void cancel(ServerWorld world) {
            for (UUID id : thrown.keySet()) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
                if (player != null) player.fallDistance = 0;
            }
            thrown.clear();
            effect.discard();
        }

        @Override public String toString() { return "faultline " + Arrays.toString(ends); }
    }
}
