package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.MinecraftClient;
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
        double dx = state.x - cameraState.pos.x;
        double dy = state.y + yOffset - cameraState.pos.y;
        double dz = state.z - cameraState.pos.z;
        float visibility = SpellViewClearance.opacity(
                !MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson(),
                Math.sqrt(dx * dx + dy * dy + dz * dz), Math.max(width, height) * 0.5);
        if (visibility <= 0.0f) return;
        int color = SpellViewClearance.color(0xFFFFFFFF, visibility);
        matrices.push();
        matrices.translate(0.0f, yOffset, 0.0f);
        // Face the camera before applying a non-uniform local scale. Scaling first
        // skews the camera rotation whenever width and height differ.
        matrices.multiply(cameraState.orientation);
        matrices.scale(width, height, 1.0f);
        queue.submitCustom(matrices, layer, (entry, vertices) -> drawQuad(vertices, entry, state.light, color));
        matrices.pop();
        super.render(state, matrices, queue, cameraState);
    }

    private static void drawQuad(VertexConsumer vertices, MatrixStack.Entry entry, int light, int color) {
        vertex(vertices, entry, light, 0.0f, 0.0f, 0.0f, 1.0f, color);
        vertex(vertices, entry, light, 1.0f, 0.0f, 1.0f, 1.0f, color);
        vertex(vertices, entry, light, 1.0f, 1.0f, 1.0f, 0.0f, color);
        vertex(vertices, entry, light, 0.0f, 1.0f, 0.0f, 0.0f, color);
    }

    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, int light,
            float x, float y, float u, float v, int color) {
        vertices.vertex(entry, x - 0.5f, y - 0.5f, 0.0f)
                .color(color)
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
