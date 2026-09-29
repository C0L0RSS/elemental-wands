package com.anton.elementalwands.crypt;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * One cell of the mausoleum's carved doorway frame. The frame is a single authored model sliced
 * into block models ({@link MausoleumModel}); each piece collides and outlines as its carving.
 * Unbreakable, like the rest of the mausoleum.
 */
public final class MausoleumArchBlock extends Block {
    public static final MapCodec<MausoleumArchBlock> CODEC = createCodec(MausoleumArchBlock::new);
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PIECE = IntProperty.of("piece", 0, MausoleumModel.ARCH_CELLS.length - 1);
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][MausoleumModel.ARCH_CELLS.length];

    static {
        for (Direction facing : Direction.Type.HORIZONTAL)
            for (int piece = 0; piece < MausoleumModel.ARCH_CELLS.length; piece++)
                SHAPES[facing.getHorizontalQuarterTurns()][piece] = shape(MausoleumModel.ARCH_BOXES[piece], facing);
    }

    public MausoleumArchBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(PIECE, 0));
    }

    /** Model-pixel boxes of a north-facing piece, turned the way the block state turns its model. */
    static VoxelShape shape(double[][] boxes, Direction facing) {
        int turns = (facing.getHorizontalQuarterTurns() + 2) % 4; // north 0, east 1, south 2, west 3
        VoxelShape shape = VoxelShapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int i = 0; i < turns; i++) {
                double nx0 = 16 - z1, nx1 = 16 - z0;
                z0 = x0; z1 = x1; x0 = nx0; x1 = nx1;
            }
            shape = VoxelShapes.union(shape, Block.createCuboidShape(x0, b[1], z0, x1, b[4], z1));
        }
        return shape.simplify();
    }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, PIECE); }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(FACING).getHorizontalQuarterTurns()][state.get(PIECE)];
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    /** Structures place the graveyard turned, never mirrored, so the frame's pieces keep their places. */
    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}
