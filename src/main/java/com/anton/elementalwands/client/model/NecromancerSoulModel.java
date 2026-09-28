package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.necromancer.NecromancerSoulEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** The freed soul wears the Soul Bolt's skull: every bolt was a shard of it. */
public class NecromancerSoulModel extends GeoModel<NecromancerSoulEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/soul_bolt.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/soul_bolt.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/soul_bolt.animation.json");

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(NecromancerSoulEntity animatable) { return ANIMATION; }
}
