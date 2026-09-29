package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.SoulFireballModel;
import com.anton.elementalwands.entity.necromancer.NecromancerRules;
import com.anton.elementalwands.entity.necromancer.SoulFireballEntity;
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

/** A ball of soul fire flying nose first, its flames trailing; fully lit, like the fire it is. */
public final class SoulFireballRenderer extends GeoEntityRenderer<SoulFireballEntity, SoulBoltRenderState> {
    private static final double POOL = 3.25;

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
        // Blue light under the ball, and on its marker in the server light's steps as it closes in.
        Vec3d origin = new Vec3d(state.x, state.y, state.z), target = entity.target();
        state.pool = SoulLightPool.sample(entity.getEntityWorld(), origin, origin.add(0, .3, 0), POOL, 1);
        state.marker = target.lengthSquared() == 0 ? null : SoulLightPool.sample(entity.getEntityWorld(), origin, target.add(0, .5, 0),
                NecromancerRules.RAIN_RADIUS, NecromancerRules.rainGlow(entity.age) / 15f);
    }

    @Override public void render(SoulBoltRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        super.render(state, matrices, queue, camera);
        SoulLightPool.submit(state.pool, matrices, queue);
        SoulLightPool.submit(state.marker, matrices, queue);
    }

    @Override protected void applyRotations(SoulBoltRenderState state, MatrixStack matrices, float nativeScale, CameraRenderState camera) {
        // The ball's centre rides 0.3 blocks above the scripted path; the model front is -Z.
        matrices.translate(0, .3, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.pitch));
    }

    @Override protected Box getBoundingBox(SoulFireballEntity entity) {
        // The trailing flames reach well past the hitbox; the light reaches the ground and the marker.
        Box box = SoulLightPool.reach(entity.getBoundingBox().expand(1.4), POOL);
        Vec3d target = entity.target();
        return target.lengthSquared() == 0 ? box : box.union(new Box(target, target).expand(NecromancerRules.RAIN_RADIUS, 1, NecromancerRules.RAIN_RADIUS));
    }
}
