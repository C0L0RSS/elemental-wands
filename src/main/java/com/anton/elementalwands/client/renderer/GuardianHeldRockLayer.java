package com.anton.elementalwands.client.renderer;

import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianCombatRules;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/** Draw at the live animated grip, without a separately interpolated item entity. */
final class GuardianHeldRockLayer extends GeoRenderLayer<FracturedGuardianEntity, Void, FracturedGuardianRenderState> {
    GuardianHeldRockLayer(GeoRenderer<FracturedGuardianEntity, Void, FracturedGuardianRenderState> renderer) { super(renderer); }

    @Override public void submitRenderTask(FracturedGuardianRenderState state, MatrixStack matrices, BakedGeoModel model,
            OrderedRenderCommandQueue queue, CameraRenderState camera, int light, int overlay, int color, boolean rendered) {
        if (!rendered || !state.holdingRock || state.deathTime > 0) return;
        // GeckoLib 5 submits the mesh for deferred rendering. Pose this instance now,
        // before sampling its bones, rather than reading the previous rendered entity's pose.
        getGeoModel().handleAnimations(renderer.createAnimationState(state));
        GeoBone hand = model.getBone("right_hand").orElse(null);
        if (hand == null) return;
        var chain = new ArrayList<GeoBone>();
        for (GeoBone bone = hand; bone != null; bone = bone.getParent()) chain.add(bone);
        Collections.reverse(chain);
        matrices.push();
        // The input is the model render pose. Apply parents before children, as the main mesh does.
        for (GeoBone bone : chain) RenderUtil.prepMatrixForBone(matrices, bone);
        // GeckoLib mirrors source X. This is the same model-space grip baked for server release.
        matrices.translate(34/16.0, 3/16.0, -22/16.0);
        float size = (float)(GuardianCombatRules.ROCK_RADIUS*2);
        matrices.scale(size, size, size);
        matrices.translate(-.5, -.5, -.5);
        queue.submitBlock(matrices, Blocks.COBBLESTONE.getDefaultState(), light, overlay, state.outlineColor);
        matrices.pop();
    }
}
