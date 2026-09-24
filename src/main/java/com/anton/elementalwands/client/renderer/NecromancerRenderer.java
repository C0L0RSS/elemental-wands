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
    public void updateRenderState(NecromancerEntity entity, NecromancerRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.colossus = entity.isColossus();
        state.transformTime = entity.getTransformTime(partialTick);
    }

    @Override
    protected net.minecraft.util.math.Box getBoundingBox(NecromancerEntity entity) {
        // The emerging skeleton and its reared skull extend far beyond either hitbox.
        if (!entity.isColossus() && !entity.isTransforming()) return super.getBoundingBox(entity);
        return new net.minecraft.util.math.Box(entity.getX() - 6, entity.getY() - .5, entity.getZ() - 6,
                entity.getX() + 6, entity.getY() + 9, entity.getZ() + 6);
    }

    @Override
    public NecromancerRenderState createRenderState(NecromancerEntity entity, Void relatedObject) {
        return new NecromancerRenderState();
    }
}
