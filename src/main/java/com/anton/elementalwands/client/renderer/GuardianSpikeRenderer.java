package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianScarRules;
import com.anton.elementalwands.entity.GuardianSpikeEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/**
 * One column of the Scar's wall: a tall stepped stone tooth with a shorter one beside it, drawn in
 * code on the Stone spells' spike texture. Each column differs a little in height, lean and turn, so a
 * row of them reads as one jagged wall. It shoots up, stands, and crumbles into the floor.
 */
public final class GuardianSpikeRenderer extends EntityRenderer<GuardianSpikeEntity, GuardianSpikeRenderer.State> {
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/block/stone_spike.png");
    private static final int STEPS = 5;
    public static final class State extends EntityRenderState { float time; int life, seed; float yaw; }
    public GuardianSpikeRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(GuardianSpikeEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta);
        state.time = entity.elapsed(delta); state.life = entity.life(); state.seed = entity.getId(); state.yaw = entity.getYaw();
    }
    /** A steady pseudo-random value in 0..1 for this column, so it looks the same every frame. */
    private static float vary(int seed, int salt) {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12;
        return (h & 0xFFFF) / 65535f;
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.invisible || state.life <= 0) return;
        float rise = Math.clamp(state.time / GuardianSpikeEntity.RISE, 0, 1);
        float crumble = Math.clamp((state.time - (state.life - GuardianSpikeEntity.CRUMBLE)) / GuardianSpikeEntity.CRUMBLE, 0, 1);
        // It overshoots a little as it bursts out, then settles.
        float shown = (rise < 1 ? rise * 1.12f : 1 + .12f * Math.max(0, 1 - (state.time - GuardianSpikeEntity.RISE) / 3)) * (1 - crumble);
        if (shown <= 0) return;
        float angle = (float)Math.toRadians(state.yaw + (vary(state.seed, 1) - .5f) * 50);
        int light = state.light, seed = state.seed;
        queue.submitCustom(matrices, RenderLayer.getEntityCutoutNoCull(TEXTURE), (entry, out) -> {
            for (int tooth = 0; tooth < 2; tooth++) {
                boolean main = tooth == 0;
                float height = (float)GuardianScarRules.HEIGHT * (main ? .88f + .2f * vary(seed, 2) : .42f + .22f * vary(seed, 3));
                float base = main ? .5f : .34f;
                // The shorter tooth sits half a block along the wall, filling the gap to the next column.
                float x = main ? 0 : (vary(seed, 4) < .5f ? -.5f : .5f), z = main ? 0 : (vary(seed, 5) - .5f) * .3f;
                float leanX = (vary(seed, 6 + tooth) - .5f) * .5f, leanZ = (vary(seed, 8 + tooth) - .5f) * .5f;
                for (int step = 0; step < STEPS; step++) {
                    float low = step / (float)STEPS, high = (step + 1) / (float)STEPS;
                    float lower = base * (1 - low * .82f), upper = Math.max(.02f, base * (1 - high * .82f) - .05f);
                    Vector3f[] lo = ring(lower, low * height * shown, x + leanX * low, z + leanZ * low, angle);
                    Vector3f[] hi = ring(upper, high * height * shown, x + leanX * high, z + leanZ * high, angle);
                    for (int side = 0; side < 4; side++) quad(out, entry, lo[side], lo[(side + 1) % 4], hi[(side + 1) % 4], hi[side], light);
                    quad(out, entry, hi[3], hi[2], hi[1], hi[0], light);
                }
            }
        });
        super.render(state, matrices, queue, camera);
    }
    private static Vector3f[] ring(float radius, float y, float x, float z, float angle) {
        Vector3f[] result = new Vector3f[4];
        for (int i = 0; i < 4; i++)
            result[i] = new Vector3f(x + (i == 0 || i == 3 ? -radius : radius), y, z + (i < 2 ? -radius : radius)).rotateY(angle);
        return result;
    }
    private static void quad(VertexConsumer out, MatrixStack.Entry entry, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int light) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() < 1e-10f) return;
        normal.normalize();
        int shade = (int)(255 * (.62 + .3 * Math.max(0, normal.y))), color = 0xFF000000 | shade << 16 | shade << 8 | shade;
        vertex(out, entry, a, 0, 1, normal, color, light); vertex(out, entry, b, 1, 1, normal, color, light);
        vertex(out, entry, c, 1, 0, normal, color, light); vertex(out, entry, d, 0, 0, normal, color, light);
    }
    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, Vector3f point, float u, float v, Vector3f n, int color, int light) {
        out.vertex(entry, point.x, point.y, point.z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, n.x, n.y, n.z);
    }
}
