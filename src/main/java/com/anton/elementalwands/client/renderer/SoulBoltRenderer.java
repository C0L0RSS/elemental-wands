package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.SoulBoltModel;
import com.anton.elementalwands.entity.necromancer.SoulBoltEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Authored skull and hinged jaw, with emissive eyes and mouth fissures. */
public final class SoulBoltRenderer extends GeoEntityRenderer<SoulBoltEntity, SoulBoltRenderState> {
    public SoulBoltRenderer(EntityRendererFactory.Context context) {
        super(context, new SoulBoltModel());
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override public SoulBoltRenderState createRenderState(SoulBoltEntity entity, Void relatedObject) {
        return new SoulBoltRenderState();
    }

    @Override public void updateRenderState(SoulBoltEntity entity, SoulBoltRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.yaw = entity.getLerpedYaw(partialTick);
        state.pitch = entity.getLerpedPitch(partialTick);
    }

    @Override protected void applyRotations(SoulBoltRenderState state, MatrixStack matrices,
            float nativeScale, CameraRenderState camera) {
        // Model front is -Z. Rotate around its centre, including vertical aim.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.pitch));
    }

    @Override protected Box getBoundingBox(SoulBoltEntity entity) {
        return entity.getBoundingBox().expand(.6); // Include the open mandible.
    }
}
