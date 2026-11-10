package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.PyreKingModel;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.math.Box;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class PyreKingRenderer extends GeoEntityRenderer<PyreKingEntity, PyreKingRenderState> {
    /** How far a clip carries his sword and chains from where he stands: the Hook's throw. */
    private static final double REACH = 30;

    public PyreKingRenderer(EntityRendererFactory.Context context) {
        super(context, new PyreKingModel());
        this.shadowRadius = 1.1f;
        withRenderLayer(AutoGlowingGeoLayer::new);   // Mask eyes, chest shard, crown gem, hot blade, embers in the armor.
        withRenderLayer(PyreKingEffectsLayer::new);   // Blade fire, the sword's chain and Unbound's burning chains.
    }

    @Override
    public void updateRenderState(PyreKingEntity entity, PyreKingRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.id = entity.getId();
    }

    /** The thrown sword, its chain and Unbound's chains reach far outside his hitbox. */
    @Override
    protected Box getBoundingBox(PyreKingEntity entity) {
        return new Box(entity.getX() - REACH, entity.getY() - 1, entity.getZ() - REACH,
                entity.getX() + REACH, entity.getY() + 12, entity.getZ() + REACH);
    }

    @Override
    public PyreKingRenderState createRenderState(PyreKingEntity entity, Void relatedObject) {
        return new PyreKingRenderState();
    }
}
