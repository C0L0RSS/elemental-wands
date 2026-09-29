package com.anton.elementalwands.crypt;

import com.anton.elementalwands.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The veil in the mausoleum doorway: one cell of the dark vortex that fills the opening. It has
 * no collision; a player who walks into it starts the graveyard ritual (see
 * {@link HollowCryptManager#enterVeil}). Faint motes drift into it on the client.
 */
public final class MausoleumVeilBlock extends Block {
    public static final MapCodec<MausoleumVeilBlock> CODEC = createCodec(MausoleumVeilBlock::new);
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PIECE = IntProperty.of("piece", 0, MausoleumModel.VEIL_COLS * MausoleumModel.VEIL_ROWS - 1);
    /** The panel sits one pixel behind the cell's front face. */
    private static final VoxelShape[] PANELS = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Type.HORIZONTAL)
            PANELS[facing.getHorizontalQuarterTurns()] = MausoleumArchBlock.shape(new double[][]{{0, 0, 1, 16, 16, 2}}, facing);
    }

    public MausoleumVeilBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(PIECE, 0));
    }

    /** The veil's bottom-centre cell, which identifies the graveyard. */
    public static BlockPos anchor(BlockState state, BlockPos pos) {
        int piece = state.get(PIECE), column = piece % MausoleumModel.VEIL_COLS, row = piece / MausoleumModel.VEIL_COLS;
        // Seen from the yard, arch-left is clockwise of the way the veil faces.
        Direction left = state.get(FACING).rotateYClockwise();
        return pos.down(row).offset(left, column - MausoleumModel.VEIL_COLS / 2);
    }

    /** The vortex's eye on the veil's face, where the motes drift to. */
    public static Vec3d eye(BlockPos anchor, Direction facing) {
        return Vec3d.ofBottomCenter(anchor).add(0, MausoleumModel.VEIL_EYE, 0).add(Vec3d.of(facing.getVector()).multiply(.5 - 1 / 16.0));
    }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, PIECE); }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return PANELS[state.get(FACING).getHorizontalQuarterTurns()];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean pastDue) {
        if (entity instanceof ServerPlayerEntity player && !world.isClient())
            HollowCryptManager.enterVeil(player, anchor(state, pos), state.get(FACING));
    }

    /**
     * A slow trickle of motes: each appears a few blocks out in front and is drawn into the eye
     * over three to six seconds. Every cell of the veil contributes, so the rate is per veil.
     */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextFloat() > .45f) return;
        Direction facing = state.get(FACING);
        Vec3d eye = eye(anchor(state, pos), facing);
        double yaw = Math.toRadians(facing.getPositiveHorizontalDegrees()) + (random.nextDouble() - .5) * Math.PI * .9;
        double reach = 2.2 + random.nextDouble() * 2.8;
        // Minecraft yaw: 0 faces south (+z), 90 faces west (-x).
        double dx = -Math.sin(yaw) * reach, dz = Math.cos(yaw) * reach, dy = -1.6 + random.nextDouble() * 3.4;
        world.addParticleClient(ModParticles.MAUSOLEUM_MOTE, eye.x, eye.y, eye.z, dx, dy, dz);
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}
