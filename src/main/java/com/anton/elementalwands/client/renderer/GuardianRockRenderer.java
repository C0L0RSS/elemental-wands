package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianRockEntity;
import com.anton.elementalwands.entity.GuardianCombatRules;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;

/** Full block geometry at the same size as the held stone and swept collision volume. */
public final class GuardianRockRenderer extends EntityRenderer<GuardianRockEntity, GuardianRockRenderer.State> {
    public static final class State extends EntityRenderState { boolean held, shard; }
    public GuardianRockRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(GuardianRockEntity entity, State state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.held = entity.isHeld(); state.shard=entity.isShard();
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.held || state.invisible) return;
        matrices.push();
        float size = (float)((state.shard?com.anton.elementalwands.entity.GuardianFanRules.RADIUS:GuardianCombatRules.ROCK_RADIUS)*2);
        matrices.scale(size,size,size);
        matrices.translate(-.5,-.5,-.5);
        queue.submitBlock(matrices, Blocks.COBBLESTONE.getDefaultState(), state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
        matrices.pop();
        super.render(state,matrices,queue,camera);
    }
}
