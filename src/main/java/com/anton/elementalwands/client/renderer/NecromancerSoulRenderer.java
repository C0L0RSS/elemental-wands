package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.NecromancerSoulModel;
import com.anton.elementalwands.entity.necromancer.NecromancerSoulEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Necromancer's soul: the Soul Bolt skull at 2.4×, lit like a soul lantern, glaring at its quarry. */
public final class NecromancerSoulRenderer extends GeoEntityRenderer<NecromancerSoulEntity, NecromancerSoulRenderState> {
    private static final double POOL = 3.25;

    public NecromancerSoulRenderer(EntityRendererFactory.Context context) {
        super(context, new NecromancerSoulModel());
        withScale(2.4f);
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override protected int getBlockLight(NecromancerSoulEntity entity, BlockPos pos) { return 15; }

    @Override public NecromancerSoulRenderState createRenderState(NecromancerSoulEntity entity, Void relatedObject) {
        return new NecromancerSoulRenderState();
    }

    @Override public void updateRenderState(NecromancerSoulEntity entity, NecromancerSoulRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.yaw = entity.getLerpedYaw(partialTick);
        state.pitch = entity.getLerpedPitch(partialTick);
        Vec3d origin = new Vec3d(state.x, state.y, state.z);
        state.pool = SoulLightPool.sample(entity.getEntityWorld(), origin, origin.add(0, entity.getHeight() / 2, 0), POOL, 1);
    }

    @Override public void render(NecromancerSoulRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        super.render(state, matrices, queue, camera);
        SoulLightPool.submit(state.pool, matrices, queue);
    }

    @Override protected Box getBoundingBox(NecromancerSoulEntity entity) { return SoulLightPool.reach(entity.getBoundingBox(), POOL); }

    @Override protected void applyRotations(NecromancerSoulRenderState state, MatrixStack matrices,
            float nativeScale, CameraRenderState camera) {
        // Model front is -Z. Rotate around its centre, including vertical aim.
        matrices.translate(0, .5, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.pitch));
        matrices.translate(0, -.5, 0);
    }
}
