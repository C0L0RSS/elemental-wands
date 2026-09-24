package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import net.minecraft.util.Identifier;
import com.anton.elementalwands.client.renderer.NecromancerRenderState;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class NecromancerModel extends GeoModel<NecromancerEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/hollow_necromancer.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/hollow_necromancer.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/hollow_necromancer.animation.json");

    @Override
    public void setCustomAnimations(AnimationState<NecromancerEntity> animationState) {
        if (!(animationState.renderState() instanceof NecromancerRenderState state)) return;
        // Only the transformation shows both bodies; each form otherwise hides the other.
        boolean transforming = state.transformTime >= 0;
        getBone("robe").ifPresent(bone -> bone.setHidden(state.colossus && !transforming));
        getBone("colossus").ifPresent(bone -> bone.setHidden(!state.colossus && !transforming));
    }

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(NecromancerEntity animatable) { return ANIMATION; }
}
