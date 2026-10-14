package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.StormRocModel;
import com.anton.elementalwands.entity.roc.StormRocEntity;
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
        withScale(2.5f); withRenderLayer(AutoGlowingGeoLayer::new); withRenderLayer(StormRocCrackleLayer::new);
        shadowRadius = 0;
    }
    @Override public StormRocRenderState createRenderState(StormRocEntity entity, Void related) { return new StormRocRenderState(); }
    @Override public void updateRenderState(StormRocEntity entity, StormRocRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.pose = entity.poseName(); state.poseTick = entity.poseTicks(partialTick);
        state.rocYaw = entity.getLerpedYaw(partialTick);
    }
    @Override protected void applyRotations(StormRocRenderState state, MatrixStack matrices, float nativeScale, CameraRenderState camera) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.rocYaw));
    }
    @Override protected Box getBoundingBox(StormRocEntity entity) {
        return new Box(entity.getX()-38, entity.getY()-4, entity.getZ()-38, entity.getX()+38, entity.getY()+28, entity.getZ()+38);
    }
}
