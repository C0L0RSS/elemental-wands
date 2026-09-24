package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.NecromancerModel;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class NecromancerRenderer extends GeoEntityRenderer<NecromancerEntity, NecromancerRenderState> {
    public NecromancerRenderer(EntityRendererFactory.Context context) {
        super(context, new NecromancerModel());
        this.shadowRadius = .45f;
        withRenderLayer(AutoGlowingGeoLayer::new); // Hood eyes and staff focus.
    }

    @Override
    public NecromancerRenderState createRenderState(NecromancerEntity entity, Void relatedObject) {
        return new NecromancerRenderState();
    }
}
