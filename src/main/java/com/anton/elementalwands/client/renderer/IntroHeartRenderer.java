package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianIntro;
import com.anton.elementalwands.entity.IntroHeartEntity;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.Vec3d;

/**
 * The heart in the intro: the Guardian's heart item, always facing the lens and lit from within,
 * in a soft cyan halo that swells as it shakes and burns brightest in flight. It is drawn exactly on
 * its scripted path, so the camera chasing it never sees it lag.
 */
public final class IntroHeartRenderer extends EntityRenderer<IntroHeartEntity, IntroHeartRenderer.State> {
    public static final class State extends EntityRenderState {
        final ItemRenderState item = new ItemRenderState();
        double age;
        Vec3d offset = Vec3d.ZERO;
    }

    private final ItemModelManager items;

    public IntroHeartRenderer(EntityRendererFactory.Context context) {
        super(context);
        items = context.getItemModelManager();
        shadowRadius = 0;
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void updateRenderState(IntroHeartEntity entity, State state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        items.updateForNonLivingEntity(state.item, IntroHeartEntity.LOOK, ItemDisplayContext.GROUND, entity);
        state.age = entity.age(partialTick);
        // Draw on the script itself, not between the last two ticks.
        state.offset = entity.at(state.age).subtract(state.x, state.y + entity.getHeight() / 2, state.z);
    }

    @Override
    public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        double t = state.age, shaking = GuardianIntro.vibration(t), flying = GuardianIntro.smooth((t - GuardianIntro.LAUNCH) / 6);
        float beat = (float)Math.max(0, Math.sin(t * (.25 + .35 * shaking)));
        float size = (float)(.72 + .07 * beat + .3 * flying);
        matrices.push();
        matrices.translate(state.offset.x, state.offset.y + .15, state.offset.z);
        matrices.multiply(camera.orientation);
        matrices.push();
        matrices.scale(size, size, size);
        matrices.translate(0, -.1, 0);
        state.item.render(matrices, queue, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, state.outlineColor);
        matrices.pop();
        float halo = (float)(.18 + .06 * beat + .16 * shaking + .22 * flying);
        int alpha = (int)(60 + 50 * beat + 60 * Math.max(shaking, flying));
        queue.submitCustom(matrices, RenderLayer.getLightning(), (entry, out) -> {
            // A soft disc: bright in the middle, gone at the rim; wound both ways so culling never hides it.
            int sides = 14;
            for (int i = 0; i < sides; i++) {
                double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
                for (int facing = 0; facing < 2; facing++) {
                    double first = facing == 0 ? a : b, second = facing == 0 ? b : a;
                    out.vertex(entry, 0, 0, 0).color(170, 250, 255, alpha);
                    out.vertex(entry, 0, 0, 0).color(170, 250, 255, alpha);
                    out.vertex(entry, (float)Math.cos(second) * halo, (float)Math.sin(second) * halo, 0).color(40, 200, 255, 0);
                    out.vertex(entry, (float)Math.cos(first) * halo, (float)Math.sin(first) * halo, 0).color(40, 200, 255, 0);
                }
            }
        });
        matrices.pop();
        super.render(state, matrices, queue, camera);
    }
}
