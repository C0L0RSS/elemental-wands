package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.necromancer.IntroSoulEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The intro's souls wear the Soul Harvest model: see-through, lit from within, with the same blue pool below. */
public final class IntroSoulRenderer extends GeoEntityRenderer<IntroSoulEntity, IntroSoulRenderState> {
    private static final float POOL = 2.6f;
    /** It comes out of the chest small and swells to full size over these ticks. */
    private static final double SWELL = 12;

    public IntroSoulRenderer(EntityRendererFactory.Context context) {
        super(context, new GeoModel<>() {
            private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/harvest_soul.geo.json");
            private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/harvest_soul.png");
            private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/harvest_soul.animation.json");
            @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
            @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
            @Override public Identifier getAnimationResource(IntroSoulEntity animatable) { return ANIMATION; }
        });
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override protected int getBlockLight(IntroSoulEntity entity, BlockPos pos) { return 15; }

    @Override public RenderLayer getRenderType(IntroSoulRenderState state, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override public IntroSoulRenderState createRenderState(IntroSoulEntity entity, Void relatedObject) { return new IntroSoulRenderState(); }

    @Override public void updateRenderState(IntroSoulEntity entity, IntroSoulRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.size = (float)(.35 + .65 * com.anton.elementalwands.entity.necromancer.NecromancerIntro.smooth(entity.age(partialTick) / SWELL));
        Vec3d origin = new Vec3d(state.x, state.y, state.z);
        state.pool = SoulLightPool.sample(entity.getEntityWorld(), origin, origin.add(0, entity.getHeight() / 2, 0), POOL, .75f);
    }

    @Override public void render(IntroSoulRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        matrices.push();
        matrices.translate(0, state.height / 2, 0);
        matrices.scale(state.size, state.size, state.size);
        matrices.translate(0, -state.height / 2, 0);
        super.render(state, matrices, queue, camera);
        matrices.pop();
        SoulLightPool.submit(state.pool, matrices, queue);
    }

    @Override protected Box getBoundingBox(IntroSoulEntity entity) { return SoulLightPool.reach(entity.getBoundingBox(), POOL); }
}
