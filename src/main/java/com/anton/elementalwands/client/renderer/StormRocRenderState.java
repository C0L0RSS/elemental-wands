package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import java.util.HashMap;
import java.util.Map;

public final class StormRocRenderState extends LivingEntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> data = new HashMap<>();
    public String pose = "perched";
    public float poseTick, rocYaw;
    /** Phase two: the storm has charged it and its crackle never settles. */
    public boolean charged;
    /** The channels drawn this frame, crossfaded and gaze-turned, with flight banking. */
    public com.anton.elementalwands.client.StormRocMotion.Frame frame;
    @Override public <D> void addGeckolibData(DataTicket<D> ticket, D value) { data.put(ticket, value); }
    @Override public boolean hasGeckolibData(DataTicket<?> ticket) { return data.containsKey(ticket); }
    @Override @SuppressWarnings("unchecked") public <D> D getGeckolibData(DataTicket<D> ticket) { return (D)data.get(ticket); }
    @Override public Map<DataTicket<?>, Object> getDataMap() { return data; }
}
