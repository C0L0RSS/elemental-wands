package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Blink Rift: upright folds of ink close at the arrival point for half a second, then the copy sinks away and rises there, anywhere on
 * the floor. The example of a spell that forms where it appears. It does no damage.
 *
 * <ul>
 *   <li><b>Crowded</b> (someone within {@code CROWD}): it gets away, to the spot on the floor furthest from everyone.</li>
 *   <li><b>Otherwise, with Thornbite built</b>: behind its target, 5 to 7 blocks past them from where it stands, to
 *       set up Thornbite (the design's combination; Thornbite is a follow-up in the rules).</li>
 *   <li><b>Otherwise</b>: round its target to the side, at its middle distance.</li>
 * </ul>
 */
public final class BlinkRiftSpell implements BossSpell {
    public static final String POOL_KIND = "blink_pool", DEPART_KIND = "blink_depart";
    /** The pool forms from tick 0 and its spot is fixed by tick 1; it arrives at 0.5 s and is free 0.2 s later. */
    public static final Timing TIMING = new Timing(0, 1, 10, 14);
    /** The pool's radius at the arrival point. */
    public static final double POOL_RADIUS = 1.3;
    /** Ticks the pool and the ink left behind take to drain after the arrival. */
    public static final int DRAIN = 20;
    /** An escape lands at least this far from every player when the floor allows. */
    public static final double ESCAPE_CLEAR = 14;

    public enum Mode { ESCAPE, FLANK, AROUND }

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.BLINK_RIFT; }
    @Override public Timing timing() { return TIMING; }

    /** Which kind of blink it casts now. */
    public static Mode mode(boolean crowded, boolean thornbiteBuilt) {
        return crowded ? Mode.ESCAPE : thornbiteBuilt ? Mode.FLANK : Mode.AROUND;
    }

    /**
     * Arrival spots for a blink, best first, all inside the floor's margin. Pure, so the contract check can hold them
     * to the floor. {@code players} are the feet of everyone the copy fights; {@code target} is the one it means.
     */
    public static List<Vec3d> spots(Mode mode, Arena arena, Vec3d from, Vec3d target, List<Vec3d> players, DoubleSupplier random) {
        List<Vec3d> spots = new ArrayList<>();
        double margin = FirstWizardRules.EDGE + 1;
        switch (mode) {
            case ESCAPE -> {
                // Rings over the whole floor; the one furthest from everyone wins, with a little chance between near equals.
                List<double[]> scored = new ArrayList<>();
                for (double ring : new double[]{0, .3, .5, .7, .88}) {
                    int count = ring == 0 ? 1 : (int) Math.ceil(ring * 24);
                    double turn = random.getAsDouble() * Math.PI * 2;
                    for (int i = 0; i < count; i++) {
                        double angle = turn + i * Math.PI * 2 / count, r = ring * (arena.radius() - margin);
                        Vec3d spot = new Vec3d(arena.centre().x + Math.cos(angle) * r, arena.floor(), arena.centre().z + Math.sin(angle) * r);
                        double clear = players.stream().mapToDouble(p -> Math.hypot(p.x - spot.x, p.z - spot.z)).min().orElse(99);
                        scored.add(new double[]{spot.x, spot.z, Math.min(clear, 26) + random.getAsDouble() * 3});
                    }
                }
                scored.sort(Comparator.comparingDouble(s -> -s[2]));
                for (double[] s : scored) spots.add(new Vec3d(s[0], arena.floor(), s[1]));
            }
            case FLANK, AROUND -> {
                Vec3d away = new Vec3d(target.x - from.x, 0, target.z - from.z);
                if (away.lengthSquared() < 1e-4) away = new Vec3d(0, 0, 1);
                away = away.normalize();
                double side = random.getAsDouble() < .5 ? -1 : 1;
                for (int i = 0; i < 12; i++) {
                    // Behind them first (or to the side, for AROUND), then swinging round either way until a spot fits.
                    double base = mode == Mode.FLANK ? 0 : 90, swing = (i / 2) * 25 * (i % 2 == 0 ? side : -side);
                    double angle = Math.toRadians(base * side + swing), reach = mode == Mode.FLANK ? 5 + random.getAsDouble() * 2 : 11;
                    Vec3d dir = away.rotateY((float) angle);
                    Vec3d spot = new Vec3d(target.x + dir.x * reach, arena.floor(), target.z + dir.z * reach);
                    if (arena.inside(spot, margin)) spots.add(spot);
                }
                if (spots.isEmpty()) spots.add(arena.onFloor(target, margin));
            }
        }
        return spots;
    }

    @Override public Casting begin(Cast cast) {
        ServerPlayerEntity target = cast.target();
        Vec3d from = cast.boss().getEntityPos();
        Vec3d aimAt = target != null ? target.getEntityPos() : cast.arena().centre();
        Mode mode = mode(cast.crowded() || target == null, BossSpells.get(FirstWizardRules.Move.THORNBITE) != null);
        List<Vec3d> feet = cast.players().stream().map(ServerPlayerEntity::getEntityPos).toList();
        Vec3d spot = null;
        for (Vec3d each : spots(mode, cast.arena(), from, aimAt, feet, cast.random()::nextDouble)) {
            if (each.squaredDistanceTo(from) < 16) continue;
            // It must fit where it rises.
            if (cast.world().isSpaceEmpty(cast.boss(), cast.boss().getBoundingBox().offset(each.subtract(from)).contract(.02))) { spot = each; break; }
        }
        if (spot == null) return null;
        return new Rift(cast, spot);
    }

    private static final class Rift implements Casting {
        private final Cast cast;
        private final Vec3d spot;
        private final FirstWizardEffectEntity pool;

        Rift(Cast cast, Vec3d spot) {
            this.cast = cast; this.spot = spot;
            cast.aim(spot);
            cast.focus(null);
            pool = cast.effect(POOL_KIND, spot, TIMING.release(), TIMING.release() + DRAIN).size(POOL_RADIUS);
            cast.world().playSound(null, spot.x, spot.y, spot.z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 1.4f, .6f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            if (t < TIMING.release() && target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            if (t == TIMING.release()) {
                Vec3d from = cast.boss().getEntityPos();
                cast.effect(DEPART_KIND, from, 0, DRAIN).size(POOL_RADIUS).dir(spot.subtract(from));
                cast.teleport(spot);
                if (target != null) cast.face(target.getEntityPos(), 180);
                pool.stage(1);
                cast.world().playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.2f, .5f);
                cast.world().playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.2f, .6f);
            }
        }
    }
}
