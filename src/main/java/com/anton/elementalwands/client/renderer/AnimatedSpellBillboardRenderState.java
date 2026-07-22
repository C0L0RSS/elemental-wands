package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.state.EntityRenderState;

/** Per-frame state for short-lived, texture-sequenced spell billboards. */
public final class AnimatedSpellBillboardRenderState extends EntityRenderState {
    int frame;
    boolean mirrored;
}
