package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.HarvestSoulModel;
import com.anton.elementalwands.entity.necromancer.HarvestSoulEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** A drifting soul: slightly see-through, lit from within, facing the ribcage it drifts toward. */
public final class HarvestSoulRenderer extends GeoEntityRenderer<HarvestSoulEntity, HarvestSoulRenderState> {
    private static final double POOL = 2;

    public HarvestSoulRenderer(EntityRendererFactory.Context context) {
        super(context, new HarvestSoulModel());
        shadowRadius = 0;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    @Override protected int getBlockLight(HarvestSoulEntity entity, BlockPos pos) { return 15; }

    @Override public RenderLayer getRenderType(HarvestSoulRenderState state, Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override public HarvestSoulRenderState createRenderState(HarvestSoulEntity entity, Void relatedObject) { return new HarvestSoulRenderState(); }

    @Override public void updateRenderState(HarvestSoulEntity entity, HarvestSoulRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        Vec3d origin = new Vec3d(state.x, state.y, state.z);
        state.pool = SoulLightPool.sample(entity.getEntityWorld(), origin, origin.add(0, entity.getHeight() / 2, 0), POOL, .75f);
    }

    @Override public void render(HarvestSoulRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        super.render(state, matrices, queue, camera);
        SoulLightPool.submit(state.pool, matrices, queue);
    }

    @Override protected Box getBoundingBox(HarvestSoulEntity entity) { return SoulLightPool.reach(entity.getBoundingBox(), POOL); }
}
