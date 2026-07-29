package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.block.MovingBlockRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.math.Direction;

/** Render-only state for the five-flame Inferno Wave front. */
public final class FireWaveRenderState extends EntityRenderState {

    public final MovingBlockRenderState fire = new MovingBlockRenderState() {
        @Override
        public float getBrightness(Direction direction, boolean shaded) {
            return 1.0f;
        }
    };
    public float rightX = 1.0f;
    public float rightZ = 0.0f;
}
