package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.StoneZombieModel;
import com.anton.elementalwands.entity.StoneZombieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class StoneZombieRenderer extends GeoEntityRenderer<StoneZombieEntity, StoneZombieRenderState> {

    public StoneZombieRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new StoneZombieModel());
    }

    @Override
    public StoneZombieRenderState createRenderState(StoneZombieEntity entity, Void obj) {
        return new StoneZombieRenderState();
    }
}
