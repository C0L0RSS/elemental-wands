package com.anton.elementalwands.util;

import com.anton.elementalwands.block.SoulGlowBlock;
import com.anton.elementalwands.registry.ModSpellBlocks;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Real block light for glowing spells: an invisible {@link SoulGlowBlock} at the spell that
 * clears itself a few ticks after its last refresh. Only air is ever used, and a refresh that
 * changes nothing writes nothing, since every light change makes clients rebuild chunk sections.
 */
public final class SoulGlow {
    /** Ticks between a glow's expiry checks. */
    public static final int CHECK = 2;
    // When each lit position may go dark. Server thread only; per world instance, so a reloaded
    // or different world starts empty and its leftover glows clear on their first check.
    private static final Map<ServerWorld, Long2LongOpenHashMap> EXPIRY = new WeakHashMap<>();

    private SoulGlow() {}

    /** Lights {@code at} (or the block above, when that one is taken) for {@code ticks} more ticks. */
    public static void light(ServerWorld world, Vec3d at, int level, int ticks) {
        BlockPos pos = BlockPos.ofFloored(at);
        if (!place(world, pos, level, ticks)) place(world, pos.up(), level, ticks);
    }

    private static boolean place(ServerWorld world, BlockPos pos, int level, int ticks) {
        if (world.isOutOfHeightLimit(pos) || !world.isPosLoaded(pos)) return false;
        BlockState state = world.getBlockState(pos);
        boolean lit = state.isOf(ModSpellBlocks.SOUL_GLOW);
        if (!lit && !state.isOf(Blocks.AIR) && !state.isOf(Blocks.CAVE_AIR)) return false;
        var expiry = EXPIRY.computeIfAbsent(world, w -> new Long2LongOpenHashMap());
        long now = world.getTime(), key = pos.asLong(), until = now + ticks;
        if (expiry.size() > 512) expiry.long2LongEntrySet().removeIf(e -> e.getLongValue() < now); // Glows a player built over.
        expiry.put(key, Math.max(until, expiry.get(key)));
        // Two spells on one block share it at the brighter level.
        if (!lit || state.get(SoulGlowBlock.LEVEL) < level)
            world.setBlockState(pos, ModSpellBlocks.SOUL_GLOW.getDefaultState().with(SoulGlowBlock.LEVEL, Math.clamp(level, 1, 15)), Block.NOTIFY_LISTENERS);
        return true;
    }

    /** A glow's check: dark once nothing refreshed it; a live one is checked again when due. */
    public static void check(ServerWorld world, BlockPos pos, boolean scheduled) {
        var expiry = EXPIRY.get(world);
        long left = expiry == null ? 0 : expiry.get(pos.asLong()) - world.getTime();
        if (left > 0) {
            if (scheduled) world.scheduleBlockTick(pos, ModSpellBlocks.SOUL_GLOW, (int)left);
            return;
        }
        if (expiry != null) expiry.remove(pos.asLong());
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }
}
