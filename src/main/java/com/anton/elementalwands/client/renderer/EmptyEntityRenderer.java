package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;

public class EmptyEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

    public EmptyEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void updateRenderState(T entity, EntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
    }
}
