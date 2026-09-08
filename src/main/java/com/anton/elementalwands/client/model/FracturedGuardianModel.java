package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianBeamTiming;
import net.minecraft.util.Identifier;
import com.anton.elementalwands.client.renderer.FracturedGuardianRenderState;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class FracturedGuardianModel extends GeoModel<FracturedGuardianEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/fractured_guardian.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/fractured_guardian.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/fractured_guardian.animation.json");

    @Override
    public void setCustomAnimations(AnimationState<FracturedGuardianEntity> animationState) {
        if (animationState.renderState() instanceof FracturedGuardianRenderState state
                && state.beamTime >= 0 && state.beamTime < GuardianBeamTiming.END) {
            float blend = Math.min(1,state.beamTime/4) * Math.min(1,(GuardianBeamTiming.END-state.beamTime)/8);
            getBone("head").ifPresent(head -> head.setRotX(head.getRotX() - (float)Math.toRadians(state.beamPitch*blend)));
        }
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FracturedGuardianEntity animatable) {
        return ANIMATION;
    }
}
