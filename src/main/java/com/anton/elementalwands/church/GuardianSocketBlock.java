package com.anton.elementalwands.church;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** The one permanent anchor in each generated church. Its facing rotates with the structure. */
public final class GuardianSocketBlock extends BlockWithEntity {
    public static final MapCodec<GuardianSocketBlock> CODEC=createCodec(GuardianSocketBlock::new);
    public GuardianSocketBlock(Settings settings) { super(settings); setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH)); }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(Properties.HORIZONTAL_FACING); }
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation) { return state.with(Properties.HORIZONTAL_FACING,rotation.rotate(state.get(Properties.HORIZONTAL_FACING))); }
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING))); }
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state) { return new GuardianSocketEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world,BlockState state,BlockEntityType<T> type) {
        return world.isClient()?null:validateTicker(type,GuardianChurchManager.SOCKET_ENTITY,GuardianSocketEntity::tick);
    }
}
