package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.InfernoWaveEntity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Renders Inferno Wave as a one-shot, velocity-aligned firebending blast.
 *
 * <p>A longitudinal stream supplies the compressed-to-extended motion while a
 * velocity-perpendicular front cap preserves the old broad gameplay-facing
 * silhouette. The crossed stream planes remain legible from the side, above,
 * or below without using upright block-fire models.</p>
 */
public final class FireWaveRenderer
        extends EntityRenderer<InfernoWaveEntity, FireWaveRenderState> {

    private static final int FRAME_COUNT = 10;
    private static final float[] STREAM_LENGTHS = {
            1.20f, 1.75f, 2.45f, 3.20f, 3.75f,
            4.00f, 4.00f, 3.72f, 3.20f, 2.55f
    };
    private static final float[] STREAM_WIDTHS = {
            0.55f, 0.72f, 0.94f, 1.18f, 1.40f,
            1.52f, 1.58f, 1.48f, 1.30f, 1.05f
    };
    private static final float[] FRONT_WIDTHS = {
            0.85f, 1.10f, 1.48f, 1.95f, 2.45f,
            2.90f, 3.20f, 3.12f, 2.82f, 2.30f
    };

    private final RenderLayer[] streamLayers = new RenderLayer[FRAME_COUNT];
    private final RenderLayer[] frontLayers = new RenderLayer[FRAME_COUNT];

    public FireWaveRenderer(EntityRendererFactory.Context context) {
        super(context);
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            streamLayers[frame] = RenderLayer.getEntityTranslucent(
                    Identifier.of("elementalwands", "textures/entity/inferno_stream_" + frame + ".png"));
            frontLayers[frame] = RenderLayer.getEntityTranslucent(
                    Identifier.of("elementalwands", "textures/entity/inferno_front_" + frame + ".png"));
        }
        this.shadowRadius = 0.0f;
    }

    @Override
    protected Box getBoundingBox(InfernoWaveEntity entity) {
        return super.getBoundingBox(entity).expand(4.4, 2.2, 4.4);
    }

    @Override
    protected int getBlockLight(InfernoWaveEntity entity, net.minecraft.util.math.BlockPos pos) {
        return 15;
    }

    @Override
    public void updateRenderState(InfernoWaveEntity entity, FireWaveRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);

        int frame = Math.max(0, Math.min(FRAME_COUNT - 1, (int) Math.floor(state.age)));
        state.frame = frame;
        state.streamLength = STREAM_LENGTHS[frame];
        state.streamWidth = STREAM_WIDTHS[frame];
        state.frontWidth = FRONT_WIDTHS[frame];

        Vec3d velocity = entity.getVelocity();
        double length = velocity.length();
        if (length < 1.0E-5) {
            velocity = new Vec3d(0.0, 0.0, 1.0);
            length = 1.0;
        }
        float forwardX = (float) (velocity.x / length);
        float forwardY = (float) (velocity.y / length);
        float forwardZ = (float) (velocity.z / length);
        state.forwardX = forwardX;
        state.forwardY = forwardY;
        state.forwardZ = forwardZ;

        double horizontal = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        float rightX;
        float rightY = 0.0f;
        float rightZ;
        if (horizontal > 1.0E-4) {
            rightX = (float) (forwardZ / horizontal);
            rightZ = (float) (-forwardX / horizontal);
        } else {
            rightX = 1.0f;
            rightZ = 0.0f;
        }
        state.rightX = rightX;
        state.rightY = rightY;
        state.rightZ = rightZ;

        // forward x right yields an up-like axis for ordinary trajectories.
        float upX = forwardY * rightZ - forwardZ * rightY;
        float upY = forwardZ * rightX - forwardX * rightZ;
        float upZ = forwardX * rightY - forwardY * rightX;
        float upLength = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLength < 1.0E-4f) {
            upX = 0.0f;
            upY = 1.0f;
            upZ = 0.0f;
        } else {
            upX /= upLength;
            upY /= upLength;
            upZ /= upLength;
        }
        state.upX = upX;
        state.upY = upY;
        state.upZ = upZ;
    }

    @Override
    public void render(FireWaveRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        boolean firstPerson = !MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson();
        Vec3d origin = new Vec3d(state.x, state.y, state.z);
        Vec3d forward = new Vec3d(state.forwardX, state.forwardY, state.forwardZ);
        Vec3d head = origin.add(forward.multiply(0.62));
        Vec3d tail = origin.add(forward.multiply(0.62 - state.streamLength));
        float ribbonOpacity = SpellViewClearance.opacity(firstPerson,
                SpellViewClearance.distanceToSegment(cameraState.pos, tail, head), state.streamWidth * 0.5);
        float frontOpacity = SpellViewClearance.opacity(firstPerson,
                cameraState.pos.distanceTo(origin.add(forward.multiply(0.68))), state.frontWidth * 0.5);
        int ribbonColor = SpellViewClearance.color(0xFFFFFFFF, ribbonOpacity);
        int crossColor = SpellViewClearance.color(0xD8FFFFFF, ribbonOpacity);
        int frontColor = SpellViewClearance.color(0xFFFFFFFF, frontOpacity);
        int frame = state.frame;
        RenderLayer streamLayer = streamLayers[frame];
        RenderLayer frontLayer = frontLayers[frame];

        queue.submitCustom(matrices, streamLayer,
                (entry, vertices) -> drawRibbon(vertices, entry, state.light,
                        state, state.upX, state.upY, state.upZ,
                        state.streamWidth * 0.50f, ribbonColor));
        queue.submitCustom(matrices, streamLayer,
                (entry, vertices) -> drawRibbon(vertices, entry, state.light,
                        state, state.rightX, state.rightY, state.rightZ,
                        state.streamWidth * 0.36f, crossColor));
        queue.submitCustom(matrices, frontLayer,
                (entry, vertices) -> drawFront(vertices, entry, state.light, state, frontColor));

        super.render(state, matrices, queue, cameraState);
    }

    private static void drawRibbon(VertexConsumer vertices, MatrixStack.Entry entry, int light,
            FireWaveRenderState state, float crossX, float crossY, float crossZ,
            float halfWidth, int color) {
        float headDistance = 0.62f;
        float tailDistance = headDistance - state.streamLength;

        float tailX = state.forwardX * tailDistance;
        float tailY = state.forwardY * tailDistance;
        float tailZ = state.forwardZ * tailDistance;
        float headX = state.forwardX * headDistance;
        float headY = state.forwardY * headDistance;
        float headZ = state.forwardZ * headDistance;

        vertex(vertices, entry, light,
                tailX - crossX * halfWidth, tailY - crossY * halfWidth, tailZ - crossZ * halfWidth,
                0.0f, 1.0f, color);
        vertex(vertices, entry, light,
                headX - crossX * halfWidth, headY - crossY * halfWidth, headZ - crossZ * halfWidth,
                1.0f, 1.0f, color);
        vertex(vertices, entry, light,
                headX + crossX * halfWidth, headY + crossY * halfWidth, headZ + crossZ * halfWidth,
                1.0f, 0.0f, color);
        vertex(vertices, entry, light,
                tailX + crossX * halfWidth, tailY + crossY * halfWidth, tailZ + crossZ * halfWidth,
                0.0f, 0.0f, color);
    }

    private static void drawFront(VertexConsumer vertices, MatrixStack.Entry entry, int light,
            FireWaveRenderState state, int color) {
        float distance = 0.68f;
        float centerX = state.forwardX * distance;
        float centerY = state.forwardY * distance;
        float centerZ = state.forwardZ * distance;
        float halfWidth = state.frontWidth * 0.50f;
        float halfHeight = halfWidth * 0.82f;

        vertex(vertices, entry, light,
                centerX - state.rightX * halfWidth - state.upX * halfHeight,
                centerY - state.rightY * halfWidth - state.upY * halfHeight,
                centerZ - state.rightZ * halfWidth - state.upZ * halfHeight,
                0.0f, 1.0f, color);
        vertex(vertices, entry, light,
                centerX + state.rightX * halfWidth - state.upX * halfHeight,
                centerY + state.rightY * halfWidth - state.upY * halfHeight,
                centerZ + state.rightZ * halfWidth - state.upZ * halfHeight,
                1.0f, 1.0f, color);
        vertex(vertices, entry, light,
                centerX + state.rightX * halfWidth + state.upX * halfHeight,
                centerY + state.rightY * halfWidth + state.upY * halfHeight,
                centerZ + state.rightZ * halfWidth + state.upZ * halfHeight,
                1.0f, 0.0f, color);
        vertex(vertices, entry, light,
                centerX - state.rightX * halfWidth + state.upX * halfHeight,
                centerY - state.rightY * halfWidth + state.upY * halfHeight,
                centerZ - state.rightZ * halfWidth + state.upZ * halfHeight,
                0.0f, 0.0f, color);
    }

    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, int light,
            float x, float y, float z, float u, float v, int color) {
        vertices.vertex(entry, x, y, z)
                .color(color)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0.0f, 1.0f, 0.0f);
    }

    @Override
    public FireWaveRenderState createRenderState() {
        return new FireWaveRenderState();
    }
}
