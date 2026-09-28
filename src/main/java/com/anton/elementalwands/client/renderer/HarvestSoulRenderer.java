package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.HarvestSoulModel;
import com.anton.elementalwands.entity.necromancer.HarvestSoulEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** A drifting soul: slightly see-through, lit from within, facing the ribcage it drifts toward. */
public final class HarvestSoulRenderer extends GeoEntityRenderer<HarvestSoulEntity, HarvestSoulRenderState> {
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
}
