package com.anton.elementalwands.client.model;

import com.anton.elementalwands.summit.StormGongBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** The aerie's gong, as art/storm_aerie/build_layout.py installs it. */
public final class StormGongModel extends GeoModel<StormGongBlockEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/storm_gong.geo.json");
    private static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/block/storm_gong.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/storm_gong.animation.json");

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(StormGongBlockEntity animatable) { return ANIMATION; }
}
