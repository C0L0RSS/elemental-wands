package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.FireSpiritModel;
import com.anton.elementalwands.entity.FireSpiritEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FireSpiritRenderer extends GeoEntityRenderer<FireSpiritEntity, FireSpiritRenderState> {

    public FireSpiritRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FireSpiritModel());
    }

    @Override
    public FireSpiritRenderState createRenderState(FireSpiritEntity entity, Void obj) {
        return new FireSpiritRenderState();
    }
}
