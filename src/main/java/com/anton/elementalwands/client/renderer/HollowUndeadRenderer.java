package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.undead.HollowUndeadEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Crawler, archer and brute: one GeckoLib model, pixel texture and eye-glow mask each. */
public class HollowUndeadRenderer<T extends HollowUndeadEntity> extends GeoEntityRenderer<T, HollowUndeadRenderer.State> {
    public HollowUndeadRenderer(EntityRendererFactory.Context context, String name, float shadow) {
        super(context, new Model<>(name));
        this.shadowRadius = shadow;
        withRenderLayer(AutoGlowingGeoLayer::new);
    }

    /** The death clip lays the body down itself; skip the vanilla sideways tip. */
    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) { return 0; }

    /** Swings, the drawn bow and the sprawled corpse reach well past the hitbox. */
    @Override
    protected Box getBoundingBox(T entity) {
        return super.getBoundingBox(entity).expand(2.5, 1, 2.5);
    }

    @Override
    public State createRenderState(T entity, Void relatedObject) { return new State(); }

    public static final class State extends LivingEntityRenderState implements GeoRenderState {
        private final Map<DataTicket<?>, Object> data = new HashMap<>();
        @Override public <D> void addGeckolibData(DataTicket<D> ticket, D value) { data.put(ticket, value); }
        @Override public boolean hasGeckolibData(DataTicket<?> ticket) { return data.containsKey(ticket); }
        @SuppressWarnings("unchecked")
        @Override public <D> D getGeckolibData(DataTicket<D> ticket) { return (D)data.get(ticket); }
        @Override public Map<DataTicket<?>, Object> getDataMap() { return data; }
    }

    static final class Model<T extends HollowUndeadEntity> extends GeoModel<T> {
        private final Identifier model, texture, animation;

        Model(String name) {
            model = Identifier.of("elementalwands", "geckolib/models/" + name + ".geo.json");
            texture = Identifier.of("elementalwands", "textures/entity/" + name + ".png");
            animation = Identifier.of("elementalwands", "geckolib/animations/" + name + ".animation.json");
        }

        @Override public Identifier getModelResource(GeoRenderState renderState) { return model; }
        @Override public Identifier getTextureResource(GeoRenderState renderState) { return texture; }
        @Override public Identifier getAnimationResource(T animatable) { return animation; }
    }
}
