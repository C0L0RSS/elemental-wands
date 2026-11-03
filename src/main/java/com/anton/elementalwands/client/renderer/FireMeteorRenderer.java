package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FallingBlockEntityRenderer;
import net.minecraft.client.render.entity.state.FallingBlockEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.FallingBlockEntity;

/** Hides only the internal meteor block (BlockFireEffects draws it); vanilla falling blocks delegate unchanged. */
public final class FireMeteorRenderer extends FallingBlockEntityRenderer {
    public static final class State extends FallingBlockEntityRenderState { public boolean meteor; }
    public FireMeteorRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(FallingBlockEntity entity, FallingBlockEntityRenderState state, float delta) {
        super.updateRenderState(entity, state, delta);
        ((State)state).meteor = entity.getBlockState().isOf(ModSpellBlocks.METEOR_CORE);
    }
    @Override public void render(FallingBlockEntityRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (!((State)state).meteor) { super.render(state, matrices, queue, camera); return; }
        // The meteor itself is drawn as block fire by BlockFireEffects.
    }
}
