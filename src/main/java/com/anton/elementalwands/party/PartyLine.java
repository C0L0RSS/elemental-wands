package com.anton.elementalwands.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Where a party is set down together, at a boss's arrival or back home after the fight: side by
 * side, two blocks apart across the way they face (as the Guardian's arrival lines them up), so
 * nobody lands inside anyone else. Every spot must be clear ground level with the given point
 * and in sight of it: a floor under the feet, room for the body, no fluid, fire or powder snow. A
 * spot that isn't clear moves to the nearest that is (along the line, or a row behind or in
 * front); a player the ground has no room for left shares the point itself.
 */
public final class PartyLine {
    /** Blocks between neighbours. */
    public static final double SPACING = 2;
    /** Places either side of the middle; a bigger party shares the ends. */
    public static final int REACH = 8;
    /** Rows tried when a place in the line isn't clear: the line itself, one behind, one in front, two behind. */
    private static final int[] ROWS = {0, -1, 1, -2};
    /** How close two players may stand, horizontally. */
    private static final double APART = 1.5;

    private PartyLine() {}

    /** The i-th of n players' place in a line, in places from its middle (0 for one alone). */
    public static double place(int i, int n) { return Math.clamp(i - (n - 1) / 2.0, -REACH, REACH); }

    /**
     * Spots for n players side by side at a point (their feet's height), facing yaw (Minecraft's):
     * the i-th player gets {@link #place}(i, n). The middle places choose first, so when the ground
     * is short it is the ends that move.
     */
    public static List<Vec3d> spots(World world, Vec3d at, float yaw, int n) {
        Vec3d[] out = new Vec3d[n];
        double a = Math.toRadians(yaw);
        Vec3d right = new Vec3d(-Math.cos(a), 0, -Math.sin(a)), ahead = new Vec3d(-Math.sin(a), 0, Math.cos(a));
        List<Vec3d> taken = new ArrayList<>();
        int[] order = IntStream.range(0, n).boxed().sorted(Comparator.comparingDouble(i -> Math.abs(place(i, n)))).mapToInt(i -> i).toArray();
        int wide = (int) Math.ceil(REACH * SPACING) + 2;
        for (int i : order) {
            Vec3d ideal = at.add(right.multiply(place(i, n) * SPACING)), best = null;
            // The point itself is the boss's own arrival or stand: it is taken as it is.
            if (place(i, n) == 0 || fits(world, at, ideal, taken)) best = ideal;
            else {
                double nearest = Double.MAX_VALUE;
                for (int row : ROWS)
                    for (int k = -wide; k <= wide; k++) {
                        Vec3d spot = at.add(right.multiply(k)).add(ahead.multiply(row * SPACING));
                        double d = spot.squaredDistanceTo(ideal);
                        if (d < nearest && fits(world, at, spot, taken)) { best = spot; nearest = d; }
                    }
            }
            out[i] = best == null ? at : best;
            if (best != null) taken.add(best);
        }
        return List.of(out);
    }

    /** Clear ground for a player at spot, level with at and in sight of it, not on anyone already placed. */
    static boolean fits(World world, Vec3d at, Vec3d spot, List<Vec3d> taken) {
        for (Vec3d other : taken)
            if (Math.hypot(other.x - spot.x, other.z - spot.z) < APART) return false;
        Box body = new Box(spot.x - .3, spot.y + .01, spot.z - .3, spot.x + .3, spot.y + 1.8, spot.z + .3);
        if (!world.isSpaceEmpty(body) || world.containsFluid(body.expand(.2, 0, .2))) return false;
        if (world.getStatesInBox(body.expand(.2, 0, .2)).anyMatch(PartyLine::harmful)) return false;
        // A floor right under the feet, and not one that burns.
        if (world.isSpaceEmpty(new Box(spot.x - .15, spot.y - .25, spot.z - .15, spot.x + .15, spot.y - .01, spot.z + .15))) return false;
        BlockState floor = world.getBlockState(BlockPos.ofFloored(spot.x, spot.y - .1, spot.z));
        if (floor.isOf(Blocks.MAGMA_BLOCK) || floor.isIn(BlockTags.CAMPFIRES) || floor.isIn(BlockTags.FIRE)) return false;
        // In the same open space as the point: nothing in the way at chest height.
        Vec3d from = at.add(0, 1.2, 0), to = spot.add(0, 1.2, 0);
        return from.squaredDistanceTo(to) < 1e-6 || world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent())).getType() == HitResult.Type.MISS;
    }

    private static boolean harmful(BlockState state) {
        return state.isIn(BlockTags.FIRE) || state.isIn(BlockTags.CAMPFIRES) || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.COBWEB);
    }
}
