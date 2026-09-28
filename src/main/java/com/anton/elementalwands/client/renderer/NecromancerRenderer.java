package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.NecromancerModel;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class NecromancerRenderer extends GeoEntityRenderer<NecromancerEntity, NecromancerRenderState> {
    /** Both forms carry their own faint soul light, about a soul lantern one block away, so the boss stays findable in the dark crypt. */
    private static final int MIN_BODY_LIGHT = 9;

    public NecromancerRenderer(EntityRendererFactory.Context context) {
        super(context, new NecromancerModel());
        this.shadowRadius = .45f;
        withRenderLayer(AutoGlowingGeoLayer::new); // Eyes, staff focus, soul runes, colossus sockets and bone seams.
    }

    @Override
    protected int getBlockLight(NecromancerEntity entity, BlockPos pos) {
        return Math.max(super.getBlockLight(entity, pos), MIN_BODY_LIGHT);
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
