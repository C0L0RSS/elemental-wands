package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.PyreKingModel;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
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
        withRenderLayer(AutoGlowingGeoLayer::new);   // Mask eyes, chest shard, crown gem, hot blade, embers in the armor.
        withRenderLayer(PyreKingEffectsLayer::new);   // Blade fire, the sword's chain and Unbound's burning chains.
    }

    @Override
    public void updateRenderState(PyreKingEntity entity, PyreKingRenderState state, float partialTick) {
        super.updateRenderState(entity, state, partialTick);
        state.id = entity.getId();
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

    /** The thrown sword, its chain and Unbound's chains reach far outside his hitbox. */
    @Override
    protected Box getBoundingBox(PyreKingEntity entity) {
        return new Box(entity.getX() - REACH, entity.getY() - 1, entity.getZ() - REACH,
                entity.getX() + REACH, entity.getY() + 12, entity.getZ() + REACH);
    }

    @Override
    public PyreKingRenderState createRenderState(PyreKingEntity entity, Void relatedObject) {
        return new PyreKingRenderState();
    }
}
