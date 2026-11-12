package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.SentryBloomPodEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Draws a thrown Sentry Bloom bud: the flower's own head at half size, tumbling end over end. */
public final class SentryBloomPodRenderer extends EntityRenderer<SentryBloomPodEntity, SentryBloomPodRenderer.State> {
    private static final float SIZE = 0.5f;
    public static final class State extends EntityRenderState { float spin; }
    public SentryBloomPodRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(SentryBloomPodEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta);
        state.spin = (entity.age + delta) * 0.45f;
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        // It leaves from beside the caster's eye: fade it in over its first half block so it never fills the view.
        float alpha = SpellViewClearance.opacity(!MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson(),
                camera.pos.distanceTo(new Vec3d(state.x, state.y, state.z)), 0.44);
        if (alpha <= 0 || state.invisible) return;
        int light = state.light;
        matrices.push();
        matrices.translate(0, 0.15f, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.spin * 0.4f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(state.spin));
        matrices.scale(SIZE, SIZE, SIZE);
        // The head mesh is authored with its pivot at the stem top; centre it so it tumbles about its middle.
        matrices.translate(0, -0.27f, 0);
        queue.submitCustom(matrices, NatureMeshes.LAYER, (entry, out) -> NatureMeshes.draw("sentry_head", out, entry, light, alpha, -1, null, false));
        matrices.pop();
        super.render(state, matrices, queue, camera);
    }
}
