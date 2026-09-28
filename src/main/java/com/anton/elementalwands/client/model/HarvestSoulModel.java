package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.necromancer.HarvestSoulEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class HarvestSoulModel extends GeoModel<HarvestSoulEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/harvest_soul.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/harvest_soul.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/harvest_soul.animation.json");

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(HarvestSoulEntity animatable) { return ANIMATION; }
}
