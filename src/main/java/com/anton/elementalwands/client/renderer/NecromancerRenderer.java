package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.NecromancerModel;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class NecromancerRenderer extends GeoEntityRenderer<NecromancerEntity, NecromancerRenderState> {
    /** Both forms carry their own faint soul light, about a soul lantern one block away, so the boss stays findable in the dark crypt. */
    private static final int MIN_BODY_LIGHT = 9;

    public NecromancerRenderer(EntityRendererFactory.Context context) {
        super(context, new NecromancerModel());
        this.shadowRadius = .45f;
        withRenderLayer(AutoGlowingGeoLayer::new); // Eyes, staff focus, soul runes, colossus sockets and bone seams.
    }

    @Override
    protected int getBlockLight(NecromancerEntity entity, BlockPos pos) {
        return Math.max(super.getBlockLight(entity, pos), MIN_BODY_LIGHT);
    }

    @Override
    public void updateRenderState(NecromancerEntity entity, NecromancerRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.colossus = entity.isColossus();
        state.transformTime = entity.getTransformTime(partialTick);
        state.split = entity.isSplit();
        state.buried = entity.isBuried();
    }

    @Override
    public void addRenderData(NecromancerEntity entity, Void relatedObject, NecromancerRenderState state, float partialTick) {
        if (!entity.inIntro()) return;
        // A stationary mob's vanilla body turn lags its head. The cinematic turns the whole
        // caster together, so follow the synchronized entity yaw during the staff slam too.
        state.bodyYaw = entity.getLerpedYaw(partialTick);
        state.relativeHeadYaw = 0;
        state.addGeckolibData(DataTickets.ENTITY_BODY_YAW, state.bodyYaw);
        state.addGeckolibData(DataTickets.ENTITY_YAW, 0f);
    }

    @Override
    public void render(NecromancerRenderState state, net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue, net.minecraft.client.render.state.CameraRenderState camera) {
        if (state.buried) return; // Tunnelling: only the trail it leaves on the surface shows.
        super.render(state, matrices, queue, camera);
    }

    /**
     * GeckoLib only advances animations while the entity renders; the intro keeps him off-screen
     * for its first shots, so he is never culled then and his clip stays on the scene's clock.
     */
    @Override
    public boolean shouldRender(NecromancerEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
        return entity.inIntro() || super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    protected float getShadowRadius(NecromancerRenderState state) {
        return state.buried ? 0 : super.getShadowRadius(state);
    }

    @Override
    protected net.minecraft.util.math.Box getBoundingBox(NecromancerEntity entity) {
        // The emerging skeleton and its reared skull extend far beyond either hitbox.
        if (!entity.isColossus() && !entity.isTransforming()) return super.getBoundingBox(entity);
        return new net.minecraft.util.math.Box(entity.getX() - 6, entity.getY() - .5, entity.getZ() - 6,
                entity.getX() + 6, entity.getY() + 9, entity.getZ() + 6);
    }

    @Override
    public NecromancerRenderState createRenderState(NecromancerEntity entity, Void relatedObject) {
        return new NecromancerRenderState();
    }
}
