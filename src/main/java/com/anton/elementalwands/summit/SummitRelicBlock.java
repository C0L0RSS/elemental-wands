package com.anton.elementalwands.summit;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Remains left on the Storm Summit: bones, a rusted helm, a broken sword. Walk-through decoration
 * that can't be broken; the models come from {@code art/storm_summit/build_layout.py}.
 */
public class SummitRelicBlock extends Block {
    public static final MapCodec<SummitRelicBlock> CODEC = createCodec(SummitRelicBlock::new);
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape OUTLINE = Block.createCuboidShape(2, 0, 2, 14, 8, 14);

    public SummitRelicBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends SummitRelicBlock> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return OUTLINE; }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    /** One of the Roc's red and white feathers, lying on the rock or stuck upright in a crack. */
    public static final class Feather extends SummitRelicBlock {
        public static final MapCodec<Feather> CODEC = createCodec(Feather::new);
        public static final BooleanProperty STUCK = BooleanProperty.of("stuck");

        public Feather(Settings settings) {
            super(settings);
            setDefaultState(getDefaultState().with(STUCK, false));
        }

        @Override protected MapCodec<Feather> getCodec() { return CODEC; }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(STUCK);
        }
    }
}
