package com.anton.elementalwands.hall;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The fire that fills the Burned Gate's arch, so that the gate looks like a portal (the developer's idea): a thin
 * sheet across its block, banked to embers until the seal is touched and a blaze while it is {@code LIT}. It is only
 * to look at. Nothing walks into it (the chains and the open lake are in the way) and it carries nobody anywhere;
 * {@link PyreGate} moves the players. Its look is one picture across the whole arch, as the Necromancer's veil is;
 * {@code PIECE} says which block of it this is, and {@code FACING} points out of the gate toward the bridge.
 */
public final class PyreGateVeilBlock extends Block {
    public static final MapCodec<PyreGateVeilBlock> CODEC = createCodec(PyreGateVeilBlock::new);
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = Properties.LIT;
    public static final IntProperty PIECE = IntProperty.of("piece", 0, PyreGateLayout.VEIL_COLS * PyreGateLayout.VEIL_ROWS - 1);

    public PyreGateVeilBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.SOUTH).with(LIT, false).with(PIECE, 0));
    }

    @Override protected MapCodec<PyreGateVeilBlock> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, LIT, PIECE); }

    /** Nothing to aim at: a player's clicks pass through it. */
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return VoxelShapes.empty(); }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(FACING))); }

    /** Embers drift up out of it, more of them while it blazes, and now and then it crackles. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        boolean lit = state.get(LIT);
        if (random.nextInt(lit ? 2 : 9) != 0) return;
        boolean alongX = state.get(FACING).getAxis() == Direction.Axis.Z;
        double along = random.nextDouble(), off = .5 + (random.nextDouble() - .5) * .3;
        world.addParticleClient(lit ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, pos.getX() + (alongX ? along : off), pos.getY() + random.nextDouble(),
                pos.getZ() + (alongX ? off : along), 0, .02 + random.nextDouble() * .03, 0);
        if (random.nextInt(lit ? 20 : 120) == 0)
            world.playSoundClient(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS,
                    lit ? 1.6f : .7f, .5f + random.nextFloat() * .3f, false);
    }
}
