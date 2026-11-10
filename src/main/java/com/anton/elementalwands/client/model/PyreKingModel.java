package com.anton.elementalwands.client.model;

import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** The approved review model, installed unchanged by art/pyre_king/build_art.py --install. */
public class PyreKingModel extends GeoModel<PyreKingEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/pyre_king.geo.json");
    public static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/pyre_king.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/pyre_king.animation.json");

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(PyreKingEntity animatable) { return ANIMATION; }
}
