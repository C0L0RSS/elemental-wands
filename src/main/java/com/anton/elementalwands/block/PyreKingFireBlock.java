package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The fire the Pyre King leaves on the floor: vanilla fire to look at (its models and textures, its light, its
 * crackle and smoke), but his own to burn by. It does not spread, does not set anything alight and does no damage
 * itself; {@code PyreKingCombat} places it, burns whoever stands in it, and takes it away. Should he be gone before
 * he can, it clears itself (see {@link #LIFE}).
 */
public final class PyreKingFireBlock extends Block {

    public static final MapCodec<PyreKingFireBlock> CODEC = createCodec(PyreKingFireBlock::new);
    /** Ticks after which a fire nobody has taken away puts itself out. His longest fire burns for 4 s. */
    public static final int LIFE = 200;

    public PyreKingFireBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    /** Nothing to aim at: a player's clicks pass through to whatever is behind it. */
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient()) world.scheduleBlockTick(pos, this, LIFE);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.removeBlock(pos, false);
    }

    /** Vanilla fire's own crackle and smoke. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(24) == 0)
            world.playSoundClient(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS,
                    1 + random.nextFloat(), random.nextFloat() * .7f + .3f, false);
        for (int i = 0; i < 3; i++)
            world.addParticleClient(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * .5 + .5,
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
    }
}
