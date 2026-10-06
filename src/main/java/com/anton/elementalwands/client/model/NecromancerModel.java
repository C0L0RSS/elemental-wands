package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerTransformTrack;
import net.minecraft.util.Identifier;
import com.anton.elementalwands.client.renderer.NecromancerRenderState;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class NecromancerModel extends GeoModel<NecromancerEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/hollow_necromancer.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/hollow_necromancer.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/hollow_necromancer.animation.json");
    private static final String TRANSFORM = "animation.hollow_necromancer.transform";

    @Override
    public void setCustomAnimations(AnimationState<NecromancerEntity> animationState) {
        if (!(animationState.renderState() instanceof NecromancerRenderState state)) return;
        // Only the transformation shows both bodies; each form otherwise hides the other. A client
        // that began tracking the boss mid-scene never got the clip and shows the form it is.
        boolean transforming = state.transformTime >= 0 && playingTransform(animationState);
        getBone("robe").ifPresent(bone -> bone.setHidden(state.colossus && !transforming));
        getBone("colossus").ifPresent(bone -> bone.setHidden(!state.colossus && !transforming));
        // The cinematic's own parts (snapped staff, claws, rags, smoke) show only in it. Its souls'
        // sockets never do: the Soul Harvest ghosts (TransformSoulEntity) fly there instead.
        for (String name : NecromancerTransformTrack.get().transformOnly())
            getBone(name).ifPresent(bone -> bone.setHidden(!transforming || name.startsWith("soul_")));
        // The camera looks out of his eyes from inside the hood.
        getBone("hood").ifPresent(bone -> bone.setHidden(state.povHood));
        // With the soul torn free the ribcage stands empty.
        getBone("soul_core").ifPresent(bone -> bone.setHidden(state.split));
    }

    private static boolean playingTransform(AnimationState<NecromancerEntity> animationState) {
        var controller = animationState.manager() == null ? null : animationState.manager().getAnimationControllers().get(NecromancerEntity.CONTROLLER);
        var clip = controller == null ? null : controller.getTriggeredAnimation();
        return clip != null && !clip.getAnimationStages().isEmpty() && TRANSFORM.equals(clip.getAnimationStages().get(0).animationName());
    }

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(NecromancerEntity animatable) { return ANIMATION; }
}
