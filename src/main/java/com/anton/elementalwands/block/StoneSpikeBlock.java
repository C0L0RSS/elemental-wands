package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * One temporary spell block carries the complete Earthen Maw lifecycle.
 * Stage {@code 0} is a flat, non-colliding fault telegraph; stages {@code 1-3}
 * are progressively taller low-poly rock teeth. Keeping every stage on the
 * same block type lets {@code TemporaryBlockManager} restore the terrain after
 * the scheduler cracks the teeth back down into the ground.
 */
public final class StoneSpikeBlock extends Block {

    public static final MapCodec<StoneSpikeBlock> CODEC = createCodec(StoneSpikeBlock::new);
    public static final IntProperty STAGE = IntProperty.of("stage", 0, 3);

    private static final VoxelShape FAULT_SHAPE = VoxelShapes.empty();
    private static final VoxelShape SHORT_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.createCuboidShape(5.0, 0.0, 5.0, 11.0, 9.0, 11.0));
    private static final VoxelShape MEDIUM_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1.0, 0.0, 2.0, 15.0, 3.0, 14.0),
            Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 13.0, 12.0));
    private static final VoxelShape TALL_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 3.0, 15.0),
            Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 16.0, 12.0));

    public StoneSpikeBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(STAGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    private static VoxelShape shapeFor(BlockState state) {
        return switch (state.get(STAGE)) {
            case 1 -> SHORT_SHAPE;
            case 2 -> MEDIUM_SHAPE;
            case 3 -> TALL_SHAPE;
            default -> FAULT_SHAPE;
        };
    }
}
