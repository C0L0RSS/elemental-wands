package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.HashMap;
import java.util.Map;

/** The freed soul: a living render state so hits flash it red. */
public class NecromancerSoulRenderState extends LivingEntityRenderState implements GeoRenderState {

    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();
    public float yaw, pitch;
    public SoulLightPool pool;

    @Override
    public <D> void addGeckolibData(DataTicket<D> ticket, D data) {
        geckolibData.put(ticket, data);
    }

    @Override
    public boolean hasGeckolibData(DataTicket<?> ticket) {
        return geckolibData.containsKey(ticket);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <D> D getGeckolibData(DataTicket<D> ticket) {
        return (D) geckolibData.get(ticket);
    }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return geckolibData;
    }
}
