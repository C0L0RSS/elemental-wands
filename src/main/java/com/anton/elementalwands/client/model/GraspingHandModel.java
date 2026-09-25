package com.anton.elementalwands.client.model;
import com.anton.elementalwands.entity.necromancer.GraspingHandEntity;
import com.anton.elementalwands.client.renderer.GraspingHandRenderState;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
public final class GraspingHandModel extends GeoModel<GraspingHandEntity> {
    private final int variant;
    public GraspingHandModel(int variant) { this.variant = variant; }
    private Identifier asset(String folder, int variant, String suffix) {
        return Identifier.of("elementalwands", folder + "/grasping_hand_" + variant + suffix);
    }
    public Identifier getModelResource(GeoRenderState s) { return asset("geckolib/models", variant, ".geo.json"); }
    public Identifier getTextureResource(GeoRenderState s) { return asset("textures/entity", variant, ".png"); }
    public Identifier getAnimationResource(GraspingHandEntity e) { return asset("geckolib/animations", variant, ".animation.json"); }
}
