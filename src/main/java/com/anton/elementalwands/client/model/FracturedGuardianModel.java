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
        if (animationState.renderState() instanceof FracturedGuardianRenderState guardState) {
            // The ribs burst open at the shell break and stay open; a core pulse flares them wider.
            float charge = com.anton.elementalwands.entity.GuardianPulseRules.charge(guardState.pulseTime);
            float open = com.anton.elementalwands.entity.GuardianShellRules.openness(guardState.phaseTime, guardState.unstable) * (1 + .3f*charge);
            float seconds = guardState.magicTime / 20;
            if (guardState.unstable) {
                float pulse = (1.04f + .035f*(float)Math.sin(guardState.magicTime*1.7)) * (1 + .35f*charge);
                getBone("core").ifPresent(core -> {
                    core.setScaleX(core.getScaleX()*pulse); core.setScaleY(core.getScaleY()*pulse);
                    core.setScaleZ(core.getScaleZ()*pulse);
                });
            }
            // Timestamp-driven ribs/core stay in sync for late trackers and reloads.
            for (int side : new int[]{-1, 1}) {
                final int sign = side;
                getBone("chest_plate_"+side).ifPresent(bone -> {
                    bone.setRotY(bone.getRotY() + (float)Math.toRadians(68*sign*open));
                    bone.setPosX(bone.getPosX() + 3*sign*open);
                    bone.setPosZ(bone.getPosZ() - 2*open);
                });
            }
            // The freed core shakes hard as it breaks out, then settles to a tremor that a pulse stirs up again.
            float jitter = open * (guardState.phaseTime >= 0 ? 1 : .35f + .65f*charge);
            getBone("core").ifPresent(core -> {
                core.setPosX(core.getPosX() + (float)Math.sin(seconds*31)*.5f*jitter);
                core.setPosY(core.getPosY() + (float)Math.sin(seconds*21)*.4f*jitter);
                core.setPosZ(core.getPosZ() - 12*open);
                core.setRotY(core.getRotY() - (float)Math.toRadians(Math.sin(seconds*17)*8*jitter));
                core.setRotZ(core.getRotZ() + (float)Math.toRadians(Math.sin(seconds*23)*7*jitter));
                core.setScaleX(core.getScaleX() * (1+.65f*open));
                core.setScaleY(core.getScaleY() * (1+.3f*open));
                core.setScaleZ(core.getScaleZ() * (1+1.4f*open));
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
