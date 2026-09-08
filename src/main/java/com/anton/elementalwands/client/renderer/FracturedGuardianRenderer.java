package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.FracturedGuardianModel;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.math.Box;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class FracturedGuardianRenderer extends GeoEntityRenderer<FracturedGuardianEntity, FracturedGuardianRenderState> {
    public FracturedGuardianRenderer(EntityRendererFactory.Context context) {
        super(context, new FracturedGuardianModel());
        this.shadowRadius = 1.5f;
        withRenderLayer(AutoGlowingGeoLayer::new);
        withRenderLayer(GuardianHeldRockLayer::new);
    }

    @Override
    protected Box getBoundingBox(FracturedGuardianEntity entity) {
        // Raised hands can remain visible when the smaller collision box leaves the camera.
        Box body = new Box(entity.getX()-6, entity.getY()-.25, entity.getZ()-6,
                entity.getX()+6, entity.getY()+10, entity.getZ()+6);
        for (int slot=0;slot<2;slot++) if (entity.getWaveTime(0,slot)>=0) {
            var center=entity.getEntityPos().add(entity.getWaveOrigin(slot));
            body=body.union(new Box(center,center).expand(20,4,20));
        }
        if (entity.getLeapTime(0)>=0) body=body.union(new Box(entity.getLeapTarget(),entity.getLeapTarget()).expand(7,4,7));
        if (entity.getBeamTime(0) >= 0) {
            return body.union(new Box(entity.getEntityPos().add(entity.getBeamOrigin()),
                    entity.getEntityPos().add(entity.getBeamEnd())).expand(1));
        }
        return body;
    }

    @Override
    public void updateRenderState(FracturedGuardianEntity entity, FracturedGuardianRenderState state, float partialTick) {
        super.updateRenderState(entity,state,partialTick);
        state.beamTime = entity.getBeamTime(partialTick);
        state.holdingRock = entity.isHoldingRock();
        state.waveStones = GuardianWaveVisual.prepare(entity,partialTick);
        state.leapTime = entity.getLeapTime(partialTick);
        state.leapMarks = GuardianLeapVisual.prepare(entity,partialTick);
        state.beamPitch = entity.getBeamPitch();
        state.beamOrigin = entity.getBeamOrigin();
        state.beamEnd = entity.getBeamEnd();
    }

    @Override
    public void render(FracturedGuardianRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        if (state.deathTime == 0 && !state.invisibleToPlayer) GuardianBeamVisual.submit(state,matrices,queue);
        if (state.deathTime == 0 && !state.invisibleToPlayer) GuardianWaveVisual.submit(state,matrices,queue);
        if (state.deathTime == 0 && !state.invisibleToPlayer) GuardianLeapVisual.submit(state,matrices,queue);
        super.render(state,matrices,queue,cameraState);
    }

    @Override
    public FracturedGuardianRenderState createRenderState(FracturedGuardianEntity entity, Void object) {
        return new FracturedGuardianRenderState();
    }
}
