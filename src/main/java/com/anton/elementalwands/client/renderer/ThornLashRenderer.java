package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.ThornLashEntity;
import com.anton.elementalwands.util.ThornLashRules;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/** Textured, tapered 3D vine with alternating woody thorns and a curling tip. */
public final class ThornLashRenderer extends EntityRenderer<ThornLashEntity, ThornLashRenderer.State> {
    public static final class State extends EntityRenderState { public float time, yaw, pitch; }
    public ThornLashRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(ThornLashEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta);
        state.time = entity.age + delta; state.yaw = entity.getYaw(); state.pitch = entity.getPitch();
    }
    @Override public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (s.invisible) return;
        double retract = s.time <= ThornLashRules.SWEEP_TICKS ? 1
                : Math.max(0, (ThornLashRules.LIFETIME - s.time) / (ThornLashRules.LIFETIME - ThornLashRules.SWEEP_TICKS));
        for (int i = 0; i < 28; i++) {
            Vec3d a = ThornLashRules.point(s.yaw, s.pitch, s.time, i / 28.0).multiply(retract);
            Vec3d b = ThornLashRules.point(s.yaw, s.pitch, s.time, (i + 1) / 28.0).multiply(retract);
            Vec3d midpoint = a.lerp(b, .5);
            if (camera.pos.squaredDistanceTo(new Vec3d(s.x, s.y, s.z).add(midpoint)) < .55 * .55) continue;
            Vec3d direction = b.subtract(a);
            float width = (float)((.12 - i * .0026) * retract);
            matrices.push(); matrices.translate(midpoint.x, midpoint.y, midpoint.z);
            matrices.multiply(new Quaternionf().rotationTo(0, 0, 1, (float)direction.x, (float)direction.y, (float)direction.z));
            matrices.push(); matrices.scale(width, width, (float)direction.length() + .025f);
            matrices.translate(-.5, -.5, -.5);
            queue.submitBlock(matrices, Blocks.MOSS_BLOCK.getDefaultState(), s.light, OverlayTexture.DEFAULT_UV, 0);
            matrices.pop();
            if (i % 3 == 1) {
                for (int step = 0; step < 3; step++) {
                    float side = i % 2 == 0 ? 1 : -1;
                    matrices.push(); matrices.translate(side * (width * .45 + step * .035), step * .012, -.025 * step);
                    float taper = (.08f - step * .024f) * (float)retract;
                    matrices.scale(taper, taper, .11f - step * .026f); matrices.translate(-.5, -.5, -.5);
                    queue.submitBlock(matrices, Blocks.OAK_LOG.getDefaultState(), s.light, OverlayTexture.DEFAULT_UV, 0);
                    matrices.pop();
                }
            }
            matrices.pop();
        }
        super.render(s, matrices, queue, camera);
    }
}
