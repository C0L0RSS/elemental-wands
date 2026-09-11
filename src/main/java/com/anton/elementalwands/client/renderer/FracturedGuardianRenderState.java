package com.anton.elementalwands.client.renderer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class FracturedGuardianRenderState extends LivingEntityRenderState implements GeoRenderState {
    public float fanTime=-1, fanPitch, fanYaw;
    public boolean unstable;
    public float phaseTime = -1, magicTime;
    public int guardCracks;
    public float guardTime = -1;
    public float beamTime = -1;
    public boolean holdingRock, arenaHidden;
    public float leapTime = -1;
    public java.util.List<GuardianLeapVisual.Mark> leapMarks = java.util.List.of();
    public java.util.List<GuardianWaveVisual.Stone> waveStones = java.util.List.of();
    public float beamPitch;
    public net.minecraft.util.math.Vec3d beamOrigin = net.minecraft.util.math.Vec3d.ZERO;
    public net.minecraft.util.math.Vec3d beamEnd = net.minecraft.util.math.Vec3d.ZERO;
    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();

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
