package com.anton.elementalwands.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.CollisionEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A texture-independent replacement for temporary vanilla fire. It keeps the
 * old trail's essential contact behavior without spreading or persisting after
 * {@code TemporaryBlockManager} restores the original block.
 */
public final class InfernoFlameBlock extends Block {

    public static final MapCodec<InfernoFlameBlock> CODEC = createCodec(InfernoFlameBlock::new);
    private static final VoxelShape OUTLINE_SHAPE = Block.createColumnShape(16.0, 0.0, 1.0);

    public InfernoFlameBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    /**
     * Match vanilla fire's one-pixel-high selection footprint instead of
     * presenting an invisible full-cube obstruction around the crossed sprite.
     */
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos,
            Entity entity, EntityCollisionHandler handler, boolean intersects) {
        handler.addEvent(CollisionEvent.CLEAR_FREEZE);
        handler.addEvent(CollisionEvent.FIRE_IGNITE);
        handler.addPostCallback(CollisionEvent.FIRE_IGNITE, colliding -> {
            AbstractFireBlock.igniteEntity(colliding);
            colliding.serverDamage(world.getDamageSources().inFire(), 1.0f);
        });
    }
}
