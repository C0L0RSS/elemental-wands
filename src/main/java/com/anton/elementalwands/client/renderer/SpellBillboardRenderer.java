package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Lightweight camera-facing renderer for spell entities that do not need a
 * Blockbench model. The entity still owns collision and movement; this class
 * only gives it a crisp, full-bright textured silhouette.
 */
public final class SpellBillboardRenderer<T extends Entity>
        extends EntityRenderer<T, EntityRenderState> {

    private final RenderLayer layer;
    private final float width;
    private final float height;
    private final float yOffset;

    public SpellBillboardRenderer(EntityRendererFactory.Context context, Identifier texture,
            float width, float height, float yOffset) {
        this(context, texture, width, height, yOffset, false);
    }

    public SpellBillboardRenderer(EntityRendererFactory.Context context, Identifier texture,
            float width, float height, float yOffset, boolean translucent) {
        super(context);
        this.layer = translucent
                ? RenderLayer.getEntityTranslucent(texture)
                : RenderLayer.getEntityCutoutNoCull(texture);
        this.width = width;
        this.height = height;
        this.yOffset = yOffset;
        this.shadowRadius = 0.0f;
    }

    @Override
    protected int getBlockLight(T entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(EntityRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        matrices.push();
        matrices.translate(0.0f, yOffset, 0.0f);
        // Face the camera before applying a non-uniform local scale. Scaling first
        // skews the camera rotation whenever width and height differ.
        matrices.multiply(cameraState.orientation);
        matrices.scale(width, height, 1.0f);
        queue.submitCustom(matrices, layer, (entry, vertices) -> drawQuad(vertices, entry, state.light));
        matrices.pop();
        super.render(state, matrices, queue, cameraState);
    }

    private static void drawQuad(VertexConsumer vertices, MatrixStack.Entry entry, int light) {
        vertex(vertices, entry, light, 0.0f, 0.0f, 0.0f, 1.0f);
        vertex(vertices, entry, light, 1.0f, 0.0f, 1.0f, 1.0f);
        vertex(vertices, entry, light, 1.0f, 1.0f, 1.0f, 0.0f);
        vertex(vertices, entry, light, 0.0f, 1.0f, 0.0f, 0.0f);
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
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
