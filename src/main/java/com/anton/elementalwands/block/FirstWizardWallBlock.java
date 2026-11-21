package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The First Wizard's Stone Wall: the player's stone veined with its ink. Solid, unbreakable and blast-proof while it
 * stands, wherever the copy was summoned. {@code StoneWallSpell} places it (only into air, through the fight's
 * {@code Cast.placeBlock}), holds it while it stands ({@link #hold}) and takes it down.
 *
 * <p>Should the fight lose track of it (a crash, or a stop that saved the chunk before the fight could clear it), it
 * clears itself: a block tick scheduled when it appears is saved with the chunk, and when it comes due a block that no
 * running wall holds turns back into the air it replaced. Holds live only in memory, so after a crash none remain. A
 * held block's tick is put off again instead, so the tick of an earlier wall on the same cell never takes down a newer
 * one.</p>
 */
public final class FirstWizardWallBlock extends Block {
    public static final MapCodec<FirstWizardWallBlock> CODEC = createCodec(FirstWizardWallBlock::new);
    /** Ticks after which a wall block nobody holds clears itself. The spell takes it down within about 8 s. */
    public static final int LIFE = 240;
    private static final Set<GlobalPos> HELD = ConcurrentHashMap.newKeySet();

    public FirstWizardWallBlock(AbstractBlock.Settings settings) { super(settings); }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }

    /** A running wall stands here: the block's own clearing waits. */
    public static void hold(World world, BlockPos pos) { HELD.add(GlobalPos.create(world.getRegistryKey(), pos.toImmutable())); }

    /** The wall here has come down or been cancelled. */
    public static void release(World world, BlockPos pos) { HELD.remove(GlobalPos.create(world.getRegistryKey(), pos.toImmutable())); }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient()) world.scheduleBlockTick(pos, this, LIFE);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (HELD.contains(GlobalPos.create(world.getRegistryKey(), pos))) world.scheduleBlockTick(pos, this, LIFE);
        else world.removeBlock(pos, false);
    }
}
