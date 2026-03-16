package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.FireSpiritEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class FireSpiritModel extends GeoModel<FireSpiritEntity> {

    private static final Identifier MODEL   = Identifier.of("elementalwands", "geckolib/models/fire_spirit.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/fire_spirit.png");
    private static final Identifier ANIM    = Identifier.of("elementalwands", "geckolib/animations/fire_spirit.animation.json");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FireSpiritEntity animatable) {
        return ANIM;
    }
}
