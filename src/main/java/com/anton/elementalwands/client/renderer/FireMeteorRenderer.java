package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FallingBlockEntityRenderer;
import net.minecraft.client.render.entity.state.FallingBlockEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.Vec3d;

/** Replaces only the internal meteor block's appearance; vanilla falling blocks delegate unchanged. */
public final class FireMeteorRenderer extends FallingBlockEntityRenderer {
    public static final class State extends FallingBlockEntityRenderState { public boolean meteor; }
    public FireMeteorRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(FallingBlockEntity entity, FallingBlockEntityRenderState state, float delta) {
        super.updateRenderState(entity, state, delta);
        ((State)state).meteor = entity.getBlockState().isOf(ModSpellBlocks.METEOR_CORE);
    }
    @Override public boolean shouldRender(FallingBlockEntity entity, Frustum frustum, double x, double y, double z) {
        if (entity.getBlockState().isOf(ModSpellBlocks.METEOR_CORE))
            return frustum.isVisible(entity.getBoundingBox().expand(10, 17, 10));
        return super.shouldRender(entity, frustum, x, y, z);
    }
    @Override public void render(FallingBlockEntityRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (!((State)state).meteor) { super.render(state, matrices, queue, camera); return; }
        if (state.invisible) return;
        FireSpellMeshes.submitMeteor(matrices, queue, state.age / 20.0,
                camera.pos.subtract(new Vec3d(state.x, state.y, state.z)),
                !MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson());
    }
}
