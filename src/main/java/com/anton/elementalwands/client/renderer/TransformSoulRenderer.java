package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.necromancer.NecromancerTransformScene;
import com.anton.elementalwands.entity.necromancer.NecromancerTransformTrack;
import com.anton.elementalwands.entity.necromancer.TransformSoulEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The transformation's ghosts wear the Soul Harvest model, see-through and lit from within, posed
 * exactly as the scene's track flies them: its turn and size replace the entity's own facing.
 */
public final class TransformSoulRenderer extends GeoEntityRenderer<TransformSoulEntity, TransformSoulRenderer.State> {
    public static final class State extends HarvestSoulRenderState {
        Quaternionf turn = new Quaternionf();
        float size, frame;
        boolean attacking;
        Vec3d offset = Vec3d.ZERO;
    }

    public TransformSoulRenderer(EntityRendererFactory.Context context) {
        super(context, new GeoModel<>() {
            private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/harvest_soul.geo.json");
            private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/harvest_soul.png");
            private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/harvest_soul.animation.json");
            @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
            @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
            @Override public Identifier getAnimationResource(TransformSoulEntity animatable) { return ANIMATION; }

            /** The two souls that floor him bare their mouths as they lunge (the preview's attack pose). */
            @Override public void setCustomAnimations(AnimationState<TransformSoulEntity> animationState) {
                if (!(animationState.renderState() instanceof State state) || !state.attacking) return;
                getBone("head").ifPresent(bone -> bone.setRotX((float)Math.toRadians(-18)));
                getBone("mouth").ifPresent(bone -> { bone.setScaleX(1.15f); bone.setScaleY(2); });
                getBone("wisp_left").ifPresent(bone -> { bone.setRotX((float)Math.toRadians(25)); bone.setRotZ((float)Math.toRadians(-15)); });
                getBone("wisp_right").ifPresent(bone -> { bone.setRotX((float)Math.toRadians(25)); bone.setRotZ((float)Math.toRadians(15)); });
            }
        });
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override protected int getBlockLight(TransformSoulEntity entity, BlockPos pos) { return 15; }

    @Override public RenderLayer getRenderType(State state, Identifier texture) { return RenderLayer.getEntityTranslucent(texture); }

    @Override public State createRenderState(TransformSoulEntity entity, Void relatedObject) { return new State(); }

    /** Each ghost is on the scene's clock, wherever the camera is; GeckoLib only animates what it draws. */
    @Override public boolean shouldRender(TransformSoulEntity entity, Frustum frustum, double x, double y, double z) { return entity.soul() != null; }

    @Override public void updateRenderState(TransformSoulEntity entity, State state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        NecromancerTransformTrack.Soul soul = entity.soul();
        if (soul == null) { state.size = 0; return; }
        double t = entity.time(partialTick);
        state.size = NecromancerTransformTrack.soulSize(soul, t);
        state.turn = NecromancerTransformTrack.soulTurn(soul, t);
        state.frame = entity.frame();
        state.attacking = soul.attacking(t);
        // Exactly on its flight this frame, whatever the entity's own smoothing did.
        Vec3d exact = NecromancerTransformScene.place(entity.centre(), entity.frame(), NecromancerTransformTrack.soulAt(soul, t));
        state.offset = exact.subtract(state.x, state.y, state.z);
    }

    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.size <= .001f) return;
        matrices.push();
        matrices.translate(state.offset.x, state.offset.y, state.offset.z);
        super.render(state, matrices, queue, camera);
        matrices.pop();
    }

    /** The track's turn, from GeckoLib's model space into the scene's frame and then the world, in place of a facing. */
    @Override public void adjustRenderPose(State state, MatrixStack poseStack, BakedGeoModel model, CameraRenderState cameraState) {
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.frame));
        poseStack.multiply(state.turn);
        poseStack.scale(state.size, state.size, state.size);
    }
}
