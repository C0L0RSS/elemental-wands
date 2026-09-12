package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.PyreFrontEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class PyreFrontRenderer extends EntityRenderer<PyreFrontEntity, PyreFrontRenderer.State> {
    public static final class State extends EntityRenderState { public float yaw; }
    public PyreFrontRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius = 0; }
    @Override public State createRenderState() { return new State(); }
    @Override protected Box getBoundingBox(PyreFrontEntity entity) { return entity.getBoundingBox().expand(3, 3, 3); }
    @Override public void updateRenderState(PyreFrontEntity entity, State state, float delta) {
        super.updateRenderState(entity,state,delta); state.yaw=entity.getLerpedYaw(delta);
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.invisible) return;
        FireSpellMeshes.submitPyre(matrices,queue,state.age/20.0,state.yaw,
                camera.pos.subtract(new Vec3d(state.x,state.y,state.z)),
                !MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson());
    }
}
