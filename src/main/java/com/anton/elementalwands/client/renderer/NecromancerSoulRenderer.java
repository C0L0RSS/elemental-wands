package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.NecromancerSoulModel;
import com.anton.elementalwands.entity.necromancer.NecromancerSoulEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Necromancer's soul: the Soul Bolt skull at 2.4×, lit like a soul lantern, glaring at its quarry. */
public final class NecromancerSoulRenderer extends GeoEntityRenderer<NecromancerSoulEntity, NecromancerSoulRenderState> {
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
    }

    @Override protected void applyRotations(NecromancerSoulRenderState state, MatrixStack matrices,
            float nativeScale, CameraRenderState camera) {
        // Model front is -Z. Rotate around its centre, including vertical aim.
        matrices.translate(0, .5, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.pitch));
        matrices.translate(0, -.5, 0);
    }
}
