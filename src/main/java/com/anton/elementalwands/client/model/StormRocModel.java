package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.entity.roc.StormRocPose;
import com.anton.elementalwands.client.renderer.StormRocRenderState;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Samples the server clock directly, including when first tracked midway through an attack. */
public final class StormRocModel extends GeoModel<StormRocEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/storm_roc.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/storm_roc.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/storm_roc.animation.json");
    @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
    @Override public Identifier getAnimationResource(StormRocEntity entity) { return ANIMATION; }
    @Override public void setCustomAnimations(AnimationState<StormRocEntity> animation) {
        if (!(animation.renderState() instanceof StormRocRenderState state)) return;
        var channels = state.frame != null ? state.frame.channels() : StormRocPose.pose(state.pose, state.poseTick);
        channels.forEach((name, p) -> getBone(name).ifPresent(bone -> {
            var initial = bone.getInitialSnapshot();
            float radians = (float)(Math.PI / 180);
            bone.setRotX(initial.getRotX() - p[0] * radians);
            bone.setRotY(initial.getRotY() - p[1] * radians);
            bone.setRotZ(initial.getRotZ() + p[2] * radians);
            bone.setPosX(p[3]); bone.setPosY(p[4]); bone.setPosZ(p[5]);
            bone.setScaleX(p[6]); bone.setScaleY(p[7]); bone.setScaleZ(p[8]);
        }));
    }
}
