package com.anton.elementalwands.hall;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The king's seal on the chains of the Burned Gate. Touching it twice takes the players by it to the Pyre King;
 * {@link PyreGate} decides that on the server. {@code FACING} points from the seal toward where players stand.
 * It is {@code LIT} while it answers a touch and while the fight it opened goes on; a lit seal asks now and then
 * whether that is still so, and goes cold if not (after a restart, say).
 */
public final class PyreSealBlock extends HorizontalFacingBlock {
    public static final MapCodec<PyreSealBlock> CODEC = createCodec(PyreSealBlock::new);
    public static final BooleanProperty LIT = Properties.LIT;
    /** Ticks between a lit seal's checks that something still holds it lit. */
    static final int CHECK = 40;
    private static final VoxelShape ALONG_X = Block.createCuboidShape(1, 1, 6.5, 15, 15, 9.5), ALONG_Z = Block.createCuboidShape(6.5, 1, 1, 9.5, 15, 15);

    public PyreSealBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.SOUTH).with(LIT, false));
    }

    @Override protected MapCodec<PyreSealBlock> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, LIT); }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.Z ? ALONG_X : ALONG_Z;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && player instanceof ServerPlayerEntity toucher) {
            String message = PyreGate.touch(toucher, pos);
            if (message != null) toucher.sendMessage(Text.literal(message), false);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(LIT)) return;
        if (PyreGate.holdsLit(world, pos)) world.scheduleBlockTick(pos, this, CHECK);
        else PyreGate.light(world, pos, false);
    }
}
