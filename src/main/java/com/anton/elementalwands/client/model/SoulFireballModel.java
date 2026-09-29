package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.necromancer.SoulFireballEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class SoulFireballModel extends GeoModel<SoulFireballEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/soul_fireball.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/soul_fireball.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/soul_fireball.animation.json");

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(SoulFireballEntity animatable) { return ANIMATION; }
}
