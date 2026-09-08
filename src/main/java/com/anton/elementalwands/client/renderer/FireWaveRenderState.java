package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.state.EntityRenderState;

/** Render-only basis and lifecycle state for the animated Inferno Wave. */
public final class FireWaveRenderState extends EntityRenderState {

    public int frame;
    public float streamLength = 1.2f;
    public float streamWidth = 0.55f;
    public float frontWidth = 0.85f;

    public float forwardX;
    public float forwardY;
    public float forwardZ = 1.0f;
    public float rightX = 1.0f;
    public float rightY;
    public float rightZ;
    public float upX;
    public float upY = 1.0f;
    public float upZ;
}
