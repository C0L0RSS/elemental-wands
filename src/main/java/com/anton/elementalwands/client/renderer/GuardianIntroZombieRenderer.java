package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianIntroZombieEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The zombie in the Guardian's intro: a vanilla zombie in the game's own zombie skin (so resource
 * packs reach it too), turned to face as the Guardian does. It is hidden until its scene starts,
 * then always drawn: GeckoLib only runs a clip while its entity renders, and both actors' clips must
 * start on the same frame to stay in step.
 */
public final class GuardianIntroZombieRenderer extends GeoEntityRenderer<GuardianIntroZombieEntity, GuardianIntroZombieRenderer.State> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/guardian_intro_zombie.geo.json");
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/zombie/zombie.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/guardian_intro_zombie.animation.json");

    public static final class State extends EntityRenderState implements GeoRenderState {
        private final Map<DataTicket<?>, Object> data = new HashMap<>();
        float yaw;
        @Override public <D> void addGeckolibData(DataTicket<D> ticket, D value) { data.put(ticket, value); }
        @Override public boolean hasGeckolibData(DataTicket<?> ticket) { return data.containsKey(ticket); }
        @SuppressWarnings("unchecked") @Override public <D> D getGeckolibData(DataTicket<D> ticket) { return (D)data.get(ticket); }
        @Override public Map<DataTicket<?>, Object> getDataMap() { return data; }
    }

    public GuardianIntroZombieRenderer(EntityRendererFactory.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
            @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
            @Override public Identifier getAnimationResource(GuardianIntroZombieEntity animatable) { return ANIMATION; }
        });
        shadowRadius = 0;
    }

    @Override public boolean shouldRender(GuardianIntroZombieEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.sceneTime(0) >= 0;
    }

    @Override public State createRenderState(GuardianIntroZombieEntity entity, Void relatedObject) { return new State(); }

    @Override public void updateRenderState(GuardianIntroZombieEntity entity, State state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.yaw = entity.getYaw();
    }

    @Override protected void applyRotations(State state, MatrixStack matrices, float nativeScale, CameraRenderState camera) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
    }
}
