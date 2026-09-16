package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.OvergrowthSeedEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** A large stepped acorn, woody cap and three green leaves; no new raster assets. */
public final class OvergrowthSeedRenderer extends EntityRenderer<OvergrowthSeedEntity, OvergrowthSeedRenderer.State> {
    private static final float[] WIDTHS = {.16f,.28f,.36f,.4f,.32f};
    public static final class State extends EntityRenderState { float spin; }
    public OvergrowthSeedRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(OvergrowthSeedEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta); state.spin = (entity.age + delta) * .14f;
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        double distance = camera.pos.distanceTo(new net.minecraft.util.math.Vec3d(state.x,state.y,state.z));
        if (state.invisible || distance < .8) return;
        float emergence = (float)Math.clamp((distance - .8) / .6, 0, 1);
        matrices.push(); matrices.scale(emergence, emergence, emergence); matrices.multiply(RotationAxis.POSITIVE_Y.rotation(state.spin));
        for (int layer=0; layer<5; layer++) {
            float width = WIDTHS[layer];
            matrices.push(); matrices.translate(0, -.17 + layer*.08, 0); matrices.scale(width,.09f,width);
            matrices.translate(-.5,-.5,-.5);
            queue.submitBlock(matrices, (layer>=3 ? Blocks.DARK_OAK_LOG : Blocks.STRIPPED_OAK_LOG).getDefaultState(),state.light,OverlayTexture.DEFAULT_UV,0);
            matrices.pop();
        }
        for (int leaf=0; leaf<3; leaf++) {
            matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(leaf*120));
            matrices.translate(.12,.2,0);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30));
            matrices.scale(.22f,.065f,.11f);matrices.translate(-.5,-.5,-.5);
            queue.submitBlock(matrices,Blocks.MOSS_BLOCK.getDefaultState(),state.light,OverlayTexture.DEFAULT_UV,0);matrices.pop();
        }
        matrices.pop();super.render(state,matrices,queue,camera);
    }
}
