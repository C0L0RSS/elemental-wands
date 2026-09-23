package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.ThornLashEntity;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;

/** The entity synchronizes combat; its complete visual is attached to the held wand's render pose. */
public final class ThornLashRenderer extends EntityRenderer<ThornLashEntity, EntityRenderState> {
    public ThornLashRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
}
