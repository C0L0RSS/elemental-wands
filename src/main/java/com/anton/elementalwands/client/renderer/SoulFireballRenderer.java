package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.SoulFireballModel;
import com.anton.elementalwands.entity.necromancer.SoulFireballEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** A ball of soul fire flying nose first, its flames trailing; fully lit, like the fire it is. */
public final class SoulFireballRenderer extends GeoEntityRenderer<SoulFireballEntity, SoulBoltRenderState> {
    public SoulFireballRenderer(EntityRendererFactory.Context context) {
        super(context, new SoulFireballModel());
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override protected int getBlockLight(SoulFireballEntity entity, BlockPos pos) { return 15; }

    @Override public SoulBoltRenderState createRenderState(SoulFireballEntity entity, Void relatedObject) { return new SoulBoltRenderState(); }

    @Override public void updateRenderState(SoulFireballEntity entity, SoulBoltRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.yaw = entity.getLerpedYaw(partialTick);
        state.pitch = entity.getLerpedPitch(partialTick);
    }

    @Override protected void applyRotations(SoulBoltRenderState state, MatrixStack matrices, float nativeScale, CameraRenderState camera) {
        // The ball's centre rides 0.3 blocks above the scripted path; the model front is -Z.
        matrices.translate(0, .3, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.pitch));
    }

    @Override protected Box getBoundingBox(SoulFireballEntity entity) {
        return entity.getBoundingBox().expand(1.4); // The trailing flames reach well past the hitbox.
    }
}
