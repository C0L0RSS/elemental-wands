package com.anton.elementalwands.world;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.StructureWorldAccess;

/**
 * The land around a Hollow graveyard is dead and dying, so the yard is easy to spot. Within
 * {@link #DEAD} blocks of its footprint every tree stands bare and the ground is scarred with
 * coarse dirt, podzol, dry grass, dead bushes and fallen leaves; out to {@link #FADE} the canopy
 * thins and the ground recovers. The edge wanders a few blocks so it doesn't follow the yard's
 * rectangle.
 *
 * <p>It runs as the graveyard's post-placement in every chunk the structure reaches, and the
 * structure generates after trees. Each chunk also treats a margin of its neighbours, so leaves a
 * neighbour's tree spilled across the border are caught whichever chunk generates first. Every
 * choice hashes the block position, so repeating a column changes nothing.
 */
final class GraveyardBlight {
    static final int DEAD = 18, FADE = 34;
    /** How far past its footprint the structure must reach so every blighted chunk places it. */
    static final int REACH = FADE + 6;
    private static final int MARGIN = 8, BELOW = 16, ABOVE = 48;

    private GraveyardBlight() {}

    /** Blight the columns of one chunk (and its margin) around a graveyard footprint. */
    static void apply(StructureWorldAccess world, BlockBox chunk, BlockBox footprint, int floor) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int top = Math.min(world.getTopYInclusive(), floor + ABOVE), bottom = Math.max(world.getBottomY(), floor - BELOW);
        for (int x = chunk.getMinX() - MARGIN; x <= chunk.getMaxX() + MARGIN; x++) {
            for (int z = chunk.getMinZ() - MARGIN; z <= chunk.getMaxZ() + MARGIN; z++) {
                int dx = Math.max(Math.max(footprint.getMinX() - x, x - footprint.getMaxX()), 0);
                int dz = Math.max(Math.max(footprint.getMinZ() - z, z - footprint.getMaxZ()), 0);
                if (dx == 0 && dz == 0) {
                    // The template clears its own box, but a neighbour generated later can spill leaves into
                    // it, and a tall tree rooted in the yard can rise above it. The template holds no leaves
                    // or vines, and its own dead tree stays below the box top.
                    for (int y = floor + 1; y <= top; y++) {
                        BlockState state = world.getBlockState(pos.set(x, y, z));
                        if (y > footprint.getMaxY() ? tree(state) : state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.VINE))
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                    continue;
                }
                double wobble = 4 * Math.sin(x * .13 + z * .07) * Math.cos(z * .11 - x * .05);
                double wither = MathHelper.clamp((FADE - Math.sqrt(dx * dx + dz * dz) - wobble) / (FADE - DEAD), 0, 1);
                if (wither > 0) column(world, pos, x, z, top, bottom, wither);
            }
        }
    }

    private static void column(StructureWorldAccess world, BlockPos.Mutable pos, int x, int z, int top, int bottom, double wither) {
        boolean clearPlants = roll(x, 0, z, 1) < wither * .85;
        for (int y = top; y >= bottom; y--) {
            BlockState state = world.getBlockState(pos.set(x, y, z));
            if (state.isAir() || state.isIn(BlockTags.LOGS)) continue;
            if (state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.VINE)) {
                if (roll(x, y, z, 2) < wither) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }
            if (!state.getFluidState().isEmpty()) return;
            if (state.isReplaceable() || state.isIn(BlockTags.FLOWERS)) {
                // Both halves of a tall plant share the column's roll.
                if (clearPlants && !state.isOf(Blocks.SNOW)) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }
            ground(world, pos, x, y, z, state, wither);
            return;
        }
    }

    /** Scar the ground and strew it with what a dying wood leaves behind. */
    private static void ground(StructureWorldAccess world, BlockPos.Mutable pos, int x, int y, int z, BlockState state, double wither) {
        double scar = roll(x, 0, z, 3);
        if (state.isOf(Blocks.GRASS_BLOCK) && scar < wither * .55)
            world.setBlockState(pos, (scar < wither * .35 ? Blocks.COARSE_DIRT : Blocks.PODZOL).getDefaultState(), Block.NOTIFY_LISTENERS);
        if (!state.isIn(BlockTags.DIRT) || !world.getBlockState(pos.set(x, y + 1, z)).isAir()) return;
        double litter = roll(x, 0, z, 4);
        BlockState cover;
        if (litter < wither * .04) cover = Blocks.DEAD_BUSH.getDefaultState();
        else if (litter < wither * .14) cover = Blocks.SHORT_DRY_GRASS.getDefaultState();
        else if (litter < wither * .24) cover = Blocks.LEAF_LITTER.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.fromHorizontalQuarterTurns((int) (roll(x, 1, z, 5) * 4)))
                .with(Properties.SEGMENT_AMOUNT, 1 + (int) (roll(x, 2, z, 5) * 4));
        else return;
        world.setBlockState(pos, cover, Block.NOTIFY_LISTENERS);
    }

    private static boolean tree(BlockState state) {
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) || state.isOf(Blocks.VINE);
    }

    /** A stable pseudo-random number in [0, 1) for a position and purpose. */
    private static double roll(int x, int y, int z, int salt) {
        return (HashCommon.mix(MathHelper.hashCode(x, y, z) + salt * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
