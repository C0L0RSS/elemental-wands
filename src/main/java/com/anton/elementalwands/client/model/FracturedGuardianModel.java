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
        if (animationState.renderState() instanceof FracturedGuardianRenderState guardState && guardState.unstable) {
            // After the shell break the core throbs inside its closed chest; a core pulse swells it.
            float charge = com.anton.elementalwands.entity.GuardianPulseRules.charge(guardState.pulseTime);
            float pulse = (1.04f + .035f*(float)Math.sin(guardState.magicTime*1.7)) * (1 + .35f*charge);
            getBone("core").ifPresent(core -> {
                core.setScaleX(core.getScaleX()*pulse); core.setScaleY(core.getScaleY()*pulse);
                core.setScaleZ(core.getScaleZ()*pulse);
            });
        }
        if (animationState.renderState() instanceof FracturedGuardianRenderState fanState && fanState.fanTime >= 0)
            getBone("head").ifPresent(head -> head.setRotX(head.getRotX()-(float)Math.toRadians(fanState.fanPitch*.7)));
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
        if (renderState instanceof FracturedGuardianRenderState state && state.cracks > 0)
            return Identifier.of("elementalwands", "textures/entity/fractured_guardian_cracks_"+state.cracks+".png");
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FracturedGuardianEntity animatable) {
        return ANIMATION;
    }
}
