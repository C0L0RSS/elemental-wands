package com.anton.elementalwands.church;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** The one permanent anchor in each generated church. Its facing rotates with the structure. */
public final class GuardianSocketBlock extends BlockWithEntity {
    public static final MapCodec<GuardianSocketBlock> CODEC=createCodec(GuardianSocketBlock::new);
    // Old completed sites retain the original cube; unfinished sites upgrade with their support.
    public static final BooleanProperty PEDESTAL=BooleanProperty.of("pedestal");
    public static final IntProperty RITUAL=IntProperty.of("ritual",0,2); // empty, offered, restored
    private static final VoxelShape BOWL=Block.createCuboidShape(0,0,0,16,6.2,16);
    public GuardianSocketBlock(Settings settings) { super(settings); setDefaultState(getDefaultState()
            .with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(PEDESTAL,false).with(RITUAL,0)); }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(Properties.HORIZONTAL_FACING,PEDESTAL,RITUAL); }
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return state.get(PEDESTAL)?BOWL:net.minecraft.util.shape.VoxelShapes.fullCube();
    }
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation) { return state.with(Properties.HORIZONTAL_FACING,rotation.rotate(state.get(Properties.HORIZONTAL_FACING))); }
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(Properties.HORIZONTAL_FACING))); }
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state) { return new GuardianSocketEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world,BlockState state,BlockEntityType<T> type) {
        return world.isClient()?null:validateTicker(type,GuardianChurchManager.SOCKET_ENTITY,GuardianSocketEntity::tick);
    }
}
