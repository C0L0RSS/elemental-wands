package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.InfernoWaveEntity;

import net.minecraft.block.Blocks;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Renders Inferno Wave as five instances of Minecraft's real animated fire
 * block model. Collision and movement remain owned by the projectile entity.
 */
public final class FireWaveRenderer
        extends EntityRenderer<InfernoWaveEntity, FireWaveRenderState> {

    private static final float[] OFFSETS = {0.0f, -0.8f, 0.8f, -1.6f, 1.6f};
    private static final float[] SCALES = {1.30f, 1.15f, 1.15f, 0.90f, 0.90f};

    public FireWaveRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    protected Box getBoundingBox(InfernoWaveEntity entity) {
        return super.getBoundingBox(entity).expand(2.2, 1.0, 2.2);
    }

    @Override
    public void updateRenderState(InfernoWaveEntity entity, FireWaveRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);

        Vec3d velocity = entity.getVelocity();
        double horizontalLength = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontalLength > 1.0E-4) {
            state.rightX = (float) (-velocity.z / horizontalLength);
            state.rightZ = (float) (velocity.x / horizontalLength);
        }

        BlockPos entityPos = entity.getBlockPos();
        state.fire.fallingBlockPos = entityPos;
        state.fire.entityBlockPos = entityPos;
        state.fire.blockState = Blocks.FIRE.getDefaultState();
        state.fire.biome = entity.getEntityWorld().getBiome(entityPos);
        state.fire.world = entity.getEntityWorld();
    }

    @Override
    public void render(FireWaveRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        for (int index = 0; index < OFFSETS.length; index++) {
            float offset = OFFSETS[index];
            float scale = SCALES[index];

            matrices.push();
            matrices.translate(
                    state.rightX * offset,
                    -0.70,
                    state.rightZ * offset);
            matrices.scale(scale, scale, scale);
            matrices.translate(-0.5, 0.0, -0.5);
            queue.submitMovingBlock(matrices, state.fire);
            matrices.pop();
        }

        super.render(state, matrices, queue, cameraState);
    }

    @Override
    public FireWaveRenderState createRenderState() {
        return new FireWaveRenderState();
    }
}
