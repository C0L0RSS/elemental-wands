package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.GraspingHandModel;
import com.anton.elementalwands.entity.necromancer.GraspingHandEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Six independently articulated skeletal claws around each marked area. */
public final class GraspingHandRenderer extends GeoEntityRenderer<GraspingHandEntity, GraspingHandRenderState> {
    // Each geometry needs its own animation processor. Queued renders execute after
    // submission, so sharing one processor across three rigs leaves earlier rigs unposed.
    private final GraspingHandModel[] models = {new GraspingHandModel(0), new GraspingHandModel(1), new GraspingHandModel(2)};
    private int selected;
    @Override public GraspingHandModel getGeoModel() { return models[selected]; }

    public GraspingHandRenderer(EntityRendererFactory.Context context) {
        super(context, new GraspingHandModel(0));
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override public GraspingHandRenderState createRenderState(GraspingHandEntity entity, Void relatedObject) {
        return new GraspingHandRenderState();
    }

    @Override public void updateRenderState(GraspingHandEntity entity, GraspingHandRenderState state, float partialTick) {
        selected = entity.variant();
        super.updateRenderState(entity, state, partialTick);
        state.yaw = entity.getLerpedYaw(partialTick);
        state.variant = entity.variant(); state.scale = entity.scale(); state.mirror = entity.mirror();
    }

    @Override public void submitRenderTasks(GraspingHandRenderState state, MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue, CameraRenderState camera,
            software.bernie.geckolib.renderer.base.RenderModelPositioner<GraspingHandRenderState> positioner) {
        selected = state.variant;
        super.submitRenderTasks(state, matrices, queue, camera, positioner);
    }

    @Override protected void applyRotations(GraspingHandRenderState state, MatrixStack matrices,
            float nativeScale, CameraRenderState camera) {
        // Model front is -Z. Rotate around its centre, including vertical aim.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
        matrices.scale(state.mirror ? -state.scale : state.scale, state.scale, state.scale);
    }

    @Override protected Box getBoundingBox(GraspingHandEntity entity) {
        return entity.getBoundingBox().expand(2.5); // Include the rising fingers.
    }
}
