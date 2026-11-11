package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.HashMap;
import java.util.Map;

public class PyreKingRenderState extends LivingEntityRenderState implements GeoRenderState {
    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();
    /** Which king this is: his fire and chains remember where they were from frame to frame. */
    public int id;
    /** The clip the server has him in, and the thrown sword's real target less the clip's authored one (blocks: right, up, ahead). */
    public String clip = "idle";
    public float aimRight, aimUp, aimAhead;
    /** For a clip that carries him: where the clip began less where he is drawn, in the clip's own axes (blocks). */
    public float carryRight, carryUp, carryAhead;

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
