package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.PyreKingIntroClient;
import com.anton.elementalwands.client.model.PyreKingModel;
import com.anton.elementalwands.entity.pyreking.PyreKingIntroTrack;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class PyreKingRenderer extends GeoEntityRenderer<PyreKingEntity, PyreKingRenderState> {
    /** How far a clip carries his sword and chains from where he stands: the Hook's throw. */
    private static final double REACH = 30;

    public PyreKingRenderer(EntityRendererFactory.Context context) {
        super(context, new PyreKingModel());
        this.shadowRadius = 1.1f;
        // Mask eyes, chest shard, crown gem, hot blade, embers in the armor. In his intro they are dead until he wakes.
        withRenderLayer(renderer -> new AutoGlowingGeoLayer<>(renderer) {
            @Override public void submitRenderTask(PyreKingRenderState state, MatrixStack matrices, BakedGeoModel model, OrderedRenderCommandQueue queue,
                    CameraRenderState camera, int light, int overlay, int colour, boolean reRender) {
                if (state.ember <= .01f) return;
                int c = Math.round(255 * Math.min(1, state.ember));
                super.submitRenderTask(state, matrices, model, queue, camera, light, overlay, 0xFF000000 | c << 16 | c << 8 | c, reRender);
            }
        });
        withRenderLayer(PyreKingEffectsLayer::new);   // Blade fire, the sword's chain and Unbound's burning chains.
    }

    @Override
    public void updateRenderState(PyreKingEntity entity, PyreKingRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.id = entity.getId();
        // His intro: the scene's tick, and from its track how much of his own glow shows and how far the blade burns.
        state.intro = PyreKingIntroClient.sceneTime(entity.getId(), partialTick);
        state.ember = state.intro < 0 ? 1 : PyreKingIntroTrack.get().ember(state.intro);
        state.blade = state.intro < 0 ? 1 : PyreKingIntroTrack.get().blade(state.intro);
        state.clip = entity.clip();
        var aim = entity.aim();
        state.aimRight = aim.x; state.aimUp = aim.y; state.aimAhead = aim.z;
        state.carryRight = state.carryUp = state.carryAhead = 0;
        // His body is drawn on the facing the server sweeps his blows along, not on vanilla's own easing of it.
        state.bodyYaw = entity.facing(partialTick);
        if (entity.clipCarriesHim()) {
            // Zip, Lunge and the Turning Cut move or turn him inside the clip, and the server makes
            // that real as it happens. So the clip is drawn from where it began: facing as he did
            // then, and shifted back there from where he now is (the model's root takes the shift,
            // so his shadow and box stay with his real place). The shift eases in over the first
            // ticks, while this client's copy of him is still arriving at where the clip began.
            float yaw = entity.clipYaw() * MathHelper.RADIANS_PER_DEGREE;
            double sin = MathHelper.sin(yaw), cos = MathHelper.cos(yaw);
            var origin = entity.clipOrigin();
            double dx = origin.x - state.x, dy = origin.y - state.y, dz = origin.z - state.z;
            double since = (entity.getEntityWorld().getTime() + partialTick) - (entity.clipStart() - entity.clipBlend());
            float ease = (float) MathHelper.clamp(since / 3, 0, 1);
            state.carryRight = (float) (-cos * dx - sin * dz) * ease;
            state.carryUp = (float) dy * ease;
            state.carryAhead = (float) (-sin * dx + cos * dz) * ease;
            state.bodyYaw = entity.clipYaw();
        }
        state.addGeckolibData(DataTickets.ENTITY_BODY_YAW, state.bodyYaw);
    }

    /** For fixtures: the pommel and the blade's tip as last drawn, in the world. */
    public static double[][] drawnBlade() { return PyreKingEffectsLayer.drawnBlade; }

    /** He does not topple when he dies: he burns away where he stands. */
    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) { return 0; }

    /** The thrown sword, its chain and Unbound's chains reach far outside his hitbox; in his intro the sword hangs high over him. */
    @Override
    protected Box getBoundingBox(PyreKingEntity entity) {
        boolean throned = entity.clip().equals("throned") || entity.clip().equals("awaken");
        return new Box(entity.getX() - REACH, entity.getY() - 1, entity.getZ() - REACH,
                entity.getX() + REACH, entity.getY() + (throned ? 60 : 12), entity.getZ() + REACH);
    }

    /** His intro's chains run the length of the hall and are drawn with him, so in it he is drawn wherever the camera looks. */
    @Override
    public boolean shouldRender(PyreKingEntity entity, Frustum frustum, double x, double y, double z) {
        return PyreKingIntroClient.sceneTime(entity.getId(), 0) >= 0 || super.shouldRender(entity, frustum, x, y, z);
    }

    /** The statue is cold stone and iron: grey, a little blue, until his own fire is in him again. */
    @Override
    public int getRenderColor(PyreKingEntity entity, Void related, float partialTick) {
        double scene = PyreKingIntroClient.sceneTime(entity.getId(), partialTick);
        if (scene < 0) return super.getRenderColor(entity, related, partialTick);
        float warm = PyreKingIntroTrack.get().ember(scene), v = .5f + .5f * warm;
        return 0xFF000000 | Math.round(255 * v * (.9f + .1f * warm)) << 16 | Math.round(255 * v * (.94f + .06f * warm)) << 8 | Math.round(255 * v);
    }

    @Override
    public PyreKingRenderState createRenderState(PyreKingEntity entity, Void relatedObject) {
        return new PyreKingRenderState();
    }
}
