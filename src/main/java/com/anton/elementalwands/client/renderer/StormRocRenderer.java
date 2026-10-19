package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.StormRocMotion;
import com.anton.elementalwands.client.model.StormRocModel;
import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.summit.StormSummitLayout;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class StormRocRenderer extends GeoEntityRenderer<StormRocEntity, StormRocRenderState> {
    public StormRocRenderer(EntityRendererFactory.Context context) {
        super(context, new StormRocModel());
        withScale((float) StormSummitLayout.ROC_SCALE); withRenderLayer(AutoGlowingGeoLayer::new); withRenderLayer(StormRocCrackleLayer::new);
        shadowRadius = 0;
    }
    @Override public StormRocRenderState createRenderState(StormRocEntity entity, Void related) { return new StormRocRenderState(); }
    @Override public void updateRenderState(StormRocEntity entity, StormRocRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.pose = entity.poseName(); state.poseTick = entity.poseTicks(partialTick);
        state.rocYaw = entity.getLerpedYaw(partialTick);
        state.charged = entity.charged();
        state.frame = StormRocMotion.frame(entity, partialTick);
    }
    @Override protected void applyRotations(StormRocRenderState state, MatrixStack matrices, float nativeScale, CameraRenderState camera) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.rocYaw));
        var frame = state.frame;
        if (frame == null || Math.abs(frame.bank()) < .01f && Math.abs(frame.climb()) < .01f) return;
        // Banked around its chest, not its feet. This runs after the model is scaled.
        float pivot = frame.pivot() / (float) StormSummitLayout.ROC_SCALE;
        matrices.translate(0, pivot, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-frame.bank()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(frame.climb()));
        matrices.translate(0, -pivot, 0);
    }
    @Override protected Box getBoundingBox(StormRocEntity entity) {
        return new Box(entity.getX()-20, entity.getY()-4, entity.getZ()-20, entity.getX()+20, entity.getY()+16, entity.getZ()+20);
    }
}
