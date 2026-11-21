package com.anton.elementalwands.util;

import java.util.Arrays;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Dragon's Pyre's path over the ground, shared by the server's scheduler and the client's drawing so both
 * put the wall in the same cells.
 *
 * <p>The wall runs flat along the caster's facing, whatever their pitch, starting from the block they stand on.
 * Each of its nine half-block lanes finds its own footing one block at a time: level ground first, then a step
 * down, a one-block step up, or a drop of up to three through open air. A lane crosses a hole too deep for that
 * unseen, at the height it had, and lands again beyond it. Anything taller than a one-block step, or water or lava
 * where the fire would burn, fall or stand (a waterlogged slab), ends it. Each slice is traced as the front reaches it, so the path follows
 * ground that changes while the wall runs.
 */
public final class PyreWaveRules {
    private PyreWaveRules() {}

    /** One slice a tick for two seconds. */
    public static final int LENGTH = 40;
    public static final int LANES = 9;
    private static final double LANE_STEP = .5, HALF_SPAN = 2;
    /** No footing in this slice: a gap the lane crosses, the rest of a lane a wall ended, or not traced yet. */
    public static final int NONE = Integer.MIN_VALUE;
    /** The order a lane tries heights relative to its last footing: level, down one, up one, down two, down three. */
    private static final int[] SEARCH = {0, -1, 1, -2, -3};
    /** How far below the caster's feet the start looks for ground, so a cast mid-jump still lands. */
    private static final int START_DROP = 4;
    /** The caster's centre, then the corners of their 0.6-block footprint, so a cast from a ledge's lip starts on it. */
    private static final double[][] FOOTPRINT = {{0, 0}, {-.29, -.29}, {.29, -.29}, {-.29, .29}, {.29, .29}};

    /** What the path needs to know of one block cell. */
    public interface Ground {
        /** Top of the cell's collision, 0 when it has none. */
        double top(BlockPos pos);

        /** Whether the cell holds water, lava or another fluid. */
        boolean wet(BlockPos pos);

        static Ground of(BlockView world) {
            return new Ground() {
                @Override public double top(BlockPos pos) {
                    VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
                    return shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
                }
                @Override public boolean wet(BlockPos pos) { return !world.getFluidState(pos).isEmpty(); }
            };
        }
    }

    /** The caster's facing with the pitch removed. */
    public static Vec3d heading(float yaw) {
        float r = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(r), 0, MathHelper.cos(r));
    }

    /** To the caster's right of a flat heading. */
    public static Vec3d right(Vec3d heading) { return new Vec3d(-heading.z, 0, heading.x); }

    /** A lane's offset to the right of the centre line, from -2 to 2 blocks. */
    public static double lateral(int lane) { return lane * LANE_STEP - HALF_SPAN; }

    /** The nearest lane to a sideways offset. */
    public static int laneAt(double side) {
        return MathHelper.clamp((int) Math.round((side + HALF_SPAN) / LANE_STEP), 0, LANES - 1);
    }

    /** The block column a lane is over at a step, at the caster's feet height. */
    public static BlockPos column(Vec3d start, Vec3d heading, int step, int lane) {
        return BlockPos.ofFloored(start.add(heading.multiply(step)).add(right(heading).multiply(lateral(lane))));
    }

    /**
     * One cast's path, from the caster's feet along a flat heading. Slices are traced in order and only once, each
     * from the world as it is when {@link #advance} reaches it; the scheduler advances with its front, a client with
     * the front it draws.
     */
    public static final class Tracer {
        private final Ground world;
        private final Vec3d start, heading;
        /** {@code ground[step][lane]}: the y of the block the fire sits on, or {@link #NONE}. Step 0 is unused. */
        private final int[][] ground = new int[LENGTH + 1][LANES];
        /** Each lane's last footing, and the step a wall ended it at ({@link #LENGTH} + 1 while it runs on). */
        private final int[] surface = new int[LANES], end = new int[LANES];
        private int traced;

        public Tracer(Ground world, Vec3d start, Vec3d heading) {
            this.world = world;
            this.start = start;
            this.heading = heading;
            for (int[] slice : ground) Arrays.fill(slice, NONE);
            Arrays.fill(surface, footing());
            Arrays.fill(end, LENGTH + 1);
        }

        /** Traces every slice up to {@code step} not traced yet. */
        public void advance(int step) {
            for (int s = traced + 1; s <= Math.min(step, LENGTH); s++) {
                for (int lane = 0; lane < LANES; lane++) if (s < end[lane]) slice(s, lane);
                traced = s;
            }
        }

        /** The last slice traced. */
        public int traced() { return traced; }

        /** The y of the block the fire sits on in a lane's slice, or {@link #NONE}. */
        public int ground(int step, int lane) {
            return step >= 1 && step <= traced ? ground[step][lane] : NONE;
        }

        /**
         * Whether the fire at a step covers a target at {@code at}, {@code side} blocks right of the centre line. It
         * goes by the target's own block column against the lanes lit over it in this slice or the one before, so a
         * pillar shields what stands behind it and nothing beside it. A column no lane passes over (a heading between
         * the axes leaves a few) goes by the nearest lane.
         */
        public boolean covers(int step, Vec3d at, double side) {
            int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
            boolean passed = false;
            for (int s = Math.max(1, step - 1); s <= step; s++) {
                for (int lane = 0; lane < LANES; lane++) {
                    BlockPos c = column(start, heading, s, lane);
                    if (c.getX() != x || c.getZ() != z) continue;
                    if (ground(s, lane) != NONE) return true;
                    passed = true;
                }
            }
            return !passed && ground(step, laneAt(side)) != NONE;
        }

        private void slice(int step, int lane) {
            BlockPos column = column(start, heading, step, lane);
            int from = surface[lane];
            for (int k : SEARCH) {
                int y = from + k;
                // The fire needs open air from where it lands up to the height it arrives at.
                if (stands(column.withY(y)) && open(column, y + 1, Math.max(y + 1, from + 1))) {
                    // A waterlogged slab or stair is water at ground level: it ends the lane like a pool.
                    if (world.wet(column.withY(y))) {
                        end[lane] = step;
                        return;
                    }
                    surface[lane] = y;
                    ground[step][lane] = y;
                    return;
                }
            }
            // A wall or fence in the way, or a pool where the fire would burn or fall, ends the lane.
            // Anything else is a hole too deep to step into, crossed at the height the lane had.
            if (!open(column, from + 1, from + 1) || pool(column, from)) end[lane] = step;
        }

        /** The block the caster stands on, or the first ground a short way below a jump. */
        private int footing() {
            for (int k = 0; k <= START_DROP; k++) {
                for (double[] o : FOOTPRINT) {
                    BlockPos p = BlockPos.ofFloored(start.x + o[0], start.y, start.z + o[1]).down(k);
                    if (stands(p) && open(p.up())) return p.getY();
                }
            }
            return BlockPos.ofFloored(start).getY() - 1;
        }

        /** Fire can sit on it: at least a slab high and no taller than a block, so fences and walls are not floors. */
        private boolean stands(BlockPos pos) {
            double h = world.top(pos);
            return h >= .5 && h <= 1;
        }

        /** Fire can burn in it: nothing taller than a carpet, and no water or lava. */
        private boolean open(BlockPos pos) { return world.top(pos) < .5 && !world.wet(pos); }

        private boolean open(BlockPos column, int from, int to) {
            for (int y = from; y <= to; y++) if (!open(column.withY(y))) return false;
            return true;
        }

        /** Water or lava at or below the lane's height, down as far as it could drop, before anything solid. */
        private boolean pool(BlockPos column, int from) {
            for (int y = from; y >= from + SEARCH[SEARCH.length - 1]; y--) {
                BlockPos p = column.withY(y);
                if (world.wet(p)) return true;
                if (world.top(p) >= .5) return false;
            }
            return false;
        }
    }
}
