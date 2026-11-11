package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.SentryBloomEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws a Sentry Bloom's head on its stem, turned toward what the flower is aiming at. The
 * head meshes are authored mouth-up with their pivot at the origin; the base is a block model.
 */
public final class SentryBloomRenderer extends EntityRenderer<SentryBloomEntity, SentryBloomRenderer.State> {
    /** Stem top of each growth stage of the base block model, and the bud's size on it. */
    private static final float[] STEM_TOP = {0.28125f, 0.4375f, 0.5625f, SentryBloomEntity.PIVOT_HEIGHT};
    private static final float[] SIZE = {0.2f, 0.45f, 0.75f, 1f};
    public static final class State extends EntityRenderState { float yaw, tilt, grown, recoil, offsetX, offsetZ, pivot, scale; boolean wilting; }
    public SentryBloomRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(SentryBloomEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta);
        state.yaw = entity.headYaw(delta); state.tilt = entity.headTilt(delta);
        state.grown = entity.grown(delta); state.recoil = entity.recoil(delta); state.wilting = entity.wilting();
        state.offsetX = entity.offsetX(); state.offsetZ = entity.offsetZ(); state.pivot = entity.pivotHeight(); state.scale = entity.scale();
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.invisible) return;
        // The block's stem rises through four stages as it unfurls; the bud sits on that stage's
        // stem top and swells with it.
        int stage = Math.min(3, (int) (state.grown * 3));
        float size = SIZE[stage] * state.scale, kick = state.recoil;
        String mesh = state.wilting ? "sentry_head_wilt" : kick > 0.3f ? "sentry_head_fired" : "sentry_head";
        int light = state.light;
        matrices.push();
        // A grown flower's heads sit on its stem and its branch; a head that opens there rises to its place as it swells.
        matrices.translate(state.offsetX, state.pivot * STEM_TOP[stage] / SentryBloomEntity.PIVOT_HEIGHT, state.offsetZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(state.tilt));
        // A shot kicks the head back along its own axis and squashes it for a moment.
        matrices.translate(0, -0.06f * kick, 0);
        matrices.scale(size * (1 + 0.08f * kick), size * (1 - 0.12f * kick), size * (1 + 0.08f * kick));
        queue.submitCustom(matrices, NatureMeshes.LAYER, (entry, out) -> NatureMeshes.draw(mesh, out, entry, light, 1, -1, null, false));
        matrices.pop();
        super.render(state, matrices, queue, camera);
    }
}
