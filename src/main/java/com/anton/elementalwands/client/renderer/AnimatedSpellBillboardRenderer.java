package com.anton.elementalwands.client.renderer;

import java.util.function.Predicate;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Camera-facing spell renderer backed by a sequence of discrete PNG frames.
 *
 * <p>The entity age selects the frame, so no client-only timer can drift from
 * the projectile or spell entity. A tracked entity flag may flip the U axis to
 * give paired effects a true mirrored silhouette without duplicate textures.</p>
 */
public final class AnimatedSpellBillboardRenderer<T extends Entity>
        extends EntityRenderer<T, AnimatedSpellBillboardRenderState> {

    private final RenderLayer[] layers;
    private final float width;
    private final float height;
    private final float yOffset;
    private final Predicate<T> mirroredPredicate;

    public AnimatedSpellBillboardRenderer(EntityRendererFactory.Context context,
            Identifier texturePrefix, int frameCount,
            float width, float height, float yOffset,
            boolean translucent) {
        this(context, texturePrefix, frameCount, width, height, yOffset,
                translucent, entity -> false);
    }

    public AnimatedSpellBillboardRenderer(EntityRendererFactory.Context context,
            Identifier texturePrefix, int frameCount,
            float width, float height, float yOffset,
            boolean translucent, Predicate<T> mirroredPredicate) {
        super(context);
        if (frameCount <= 0) {
            throw new IllegalArgumentException("Animated billboard requires at least one frame");
        }

        this.layers = new RenderLayer[frameCount];
        for (int frame = 0; frame < frameCount; frame++) {
            Identifier texture = Identifier.of(texturePrefix.getNamespace(),
                    texturePrefix.getPath() + "_" + frame + ".png");
            layers[frame] = translucent
                    ? RenderLayer.getEntityTranslucent(texture)
                    : RenderLayer.getEntityCutoutNoCull(texture);
        }
        this.width = width;
        this.height = height;
        this.yOffset = yOffset;
        this.mirroredPredicate = mirroredPredicate;
        this.shadowRadius = 0.0f;
    }

    @Override
    protected int getBlockLight(T entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void updateRenderState(T entity, AnimatedSpellBillboardRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.frame = Math.floorMod((int) Math.floor(state.age), layers.length);
        state.mirrored = mirroredPredicate.test(entity);
    }

    @Override
    public void render(AnimatedSpellBillboardRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        matrices.push();
        matrices.translate(0.0f, yOffset, 0.0f);
        matrices.multiply(cameraState.orientation);
        matrices.scale(width, height, 1.0f);

        RenderLayer layer = layers[Math.floorMod(state.frame, layers.length)];
        boolean mirrored = state.mirrored;
        queue.submitCustom(matrices, layer,
                (entry, vertices) -> drawQuad(vertices, entry, state.light, mirrored));
        matrices.pop();
        super.render(state, matrices, queue, cameraState);
    }

    private static void drawQuad(VertexConsumer vertices, MatrixStack.Entry entry,
            int light, boolean mirrored) {
        float leftU = mirrored ? 1.0f : 0.0f;
        float rightU = mirrored ? 0.0f : 1.0f;
        vertex(vertices, entry, light, 0.0f, 0.0f, leftU, 1.0f);
        vertex(vertices, entry, light, 1.0f, 0.0f, rightU, 1.0f);
        vertex(vertices, entry, light, 1.0f, 1.0f, rightU, 0.0f);
        vertex(vertices, entry, light, 0.0f, 1.0f, leftU, 0.0f);
    }

    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, int light,
            float x, float y, float u, float v) {
        vertices.vertex(entry, x - 0.5f, y - 0.5f, 0.0f)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0f, 1.0f, 0.0f);
    }

    @Override
    public AnimatedSpellBillboardRenderState createRenderState() {
        return new AnimatedSpellBillboardRenderState();
    }
}
