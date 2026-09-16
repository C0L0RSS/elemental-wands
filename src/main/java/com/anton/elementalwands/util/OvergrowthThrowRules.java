package com.anton.elementalwands.util;

import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Provisional throw and optional flower bonus. Placement never depends on the selected Basic. */
public final class OvergrowthThrowRules {
    public static final double SPEED = 1.1;
    public static final double GRAVITY = .05;
    public static final int FLIGHT_LIMIT = 120;
    public static final double FLOWER_RADIUS = 3;
    public static final int BONUS_TICKS = 100;

    public static boolean validGround(ServerWorld world, BlockPos anchor) {
        if (!world.isChunkLoaded(anchor) || !world.isInBuildLimit(anchor.up(9))
                || !world.getWorldBorder().contains(anchor)) return false;
        if (!world.getBlockState(anchor.down()).isSideSolidFullSquare(world, anchor.down(), Direction.UP)) return false;
        // Keep the central trunk above ground and out of ceilings. Outer branches can skip obstacles.
        for (int dy = 0; dy <= 6; dy++) {
            var state = world.getBlockState(anchor.up(dy));
            if (!state.getFluidState().isEmpty()) return false;
            if (!(state.isAir() || state.isReplaceable() || state.isOf(ModSpellBlocks.NATURE_SEEDLING)
                    || state.isOf(ModSpellBlocks.NATURE_ROOTS))) return false;
        }
        return true;
    }
    private OvergrowthThrowRules() {}
}
