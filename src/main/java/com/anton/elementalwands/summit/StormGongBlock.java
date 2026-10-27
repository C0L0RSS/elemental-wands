package com.anton.elementalwands.summit;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The aerie's summoning gong. The plate itself is a GeckoLib model that the {@link Anchor}'s block
 * entity draws; the anchor, at the gong's foot, and the {@link Part} blocks across its face draw
 * nothing and carry only the plate's own shape, so a right-click anywhere on the visible gong, and
 * nowhere else, strikes it. {@code FACING} points from the plate toward where players strike it, the
 * same on all its blocks. Striking is decided on the server by {@link StormAerie}; the client only
 * swings the hand.
 */
public abstract class StormGongBlock extends Block {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    /** Which of the plate's cells ({@link StormAerieLayout#GONG_CELLS}) a part is; the anchor's is 0. */
    public static final IntProperty CELL = IntProperty.of("cell", 0, StormAerieLayout.GONG_CELLS.length - 1);
    /** Each cell's shape by horizontal facing: the plate's stepped outline in it, 3 px thick through its middle. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][StormAerieLayout.GONG_CELLS.length];

    static {
        for (Direction facing : Direction.Type.HORIZONTAL)
            for (int cell = 0; cell < StormAerieLayout.GONG_CELLS.length; cell++)
                SHAPES[facing.getHorizontalQuarterTurns()][cell] = plate(facing, StormAerieLayout.GONG_CELLS[cell]);
    }

    /**
     * One cell of the plate as its facing places it. The table's spans run along the gong's left
     * (its facing turned counterclockwise): along x for north or south, along z for east or west,
     * mirrored where that left points toward negative coordinates.
     */
    private static VoxelShape plate(Direction facing, int[] cell) {
        boolean alongX = facing.getAxis() == Direction.Axis.Z;
        boolean flipped = facing.rotateYCounterclockwise().getDirection() == Direction.AxisDirection.NEGATIVE;
        VoxelShape shape = VoxelShapes.empty();
        for (int row = 0; row < 4; row++) {
            int u0 = cell[2 + 2 * row], u1 = cell[3 + 2 * row];
            if (u1 <= u0) continue;
            double a = flipped ? 16 - u1 : u0, b = flipped ? 16 - u0 : u1;
            shape = VoxelShapes.union(shape, alongX ? Block.createCuboidShape(a, 4 * row, 6.5, b, 4 * row + 4, 9.5)
                    : Block.createCuboidShape(6.5, 4 * row, a, 9.5, 4 * row + 4, b));
        }
        return shape;
    }

    protected StormGongBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    /** Which of the plate's cells this block is. */
    protected abstract int cell(BlockState state);

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(FACING).getHorizontalQuarterTurns()][cell(state)];
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && player instanceof ServerPlayerEntity striker) {
            String message = StormAerie.ring(striker, pos);
            if (message != null) striker.sendMessage(Text.literal(message), false);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    /** The block at the gong's foot whose block entity draws and swings the whole gong. */
    public static final class Anchor extends StormGongBlock implements BlockEntityProvider {
        public static final MapCodec<Anchor> CODEC = createCodec(Anchor::new);

        public Anchor(Settings settings) { super(settings); }

        @Override protected MapCodec<Anchor> getCodec() { return CODEC; }

        @Override protected int cell(BlockState state) { return 0; }

        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new StormGongBlockEntity(pos, state); }
    }

    /** A click target on the plate's face; it strikes the anchor in its plane with the same facing. */
    public static final class Part extends StormGongBlock {
        public static final MapCodec<Part> CODEC = createCodec(Part::new);

        public Part(Settings settings) { super(settings); }

        @Override protected MapCodec<Part> getCodec() { return CODEC; }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(CELL);
        }

        @Override protected int cell(BlockState state) { return state.get(CELL); }
    }
}
