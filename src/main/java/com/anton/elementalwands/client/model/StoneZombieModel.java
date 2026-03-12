package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.StoneZombieEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class StoneZombieModel extends GeoModel<StoneZombieEntity> {

    private static final Identifier MODEL   = Identifier.of("elementalwands", "geckolib/models/stone_zombie.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/stone_zombie.png");
    private static final Identifier ANIM    = Identifier.of("elementalwands", "geckolib/animations/stone_zombie.animation.json");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(StoneZombieEntity animatable) {
        return ANIM;
    }
}
