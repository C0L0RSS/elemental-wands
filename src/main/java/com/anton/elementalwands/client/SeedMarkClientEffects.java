package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.NatureMeshes;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The seed mark: one winged seed turning above the enemy this player's flowers are aiming at.
 * Only the flowers' owner is told of it, so a crowded fight shows each player one marker.
 */
public final class SeedMarkClientEffects {
    private record Marker(Vec3d relative, float spin, float scale, float opacity) {}
    private static final RenderStateDataKey<Marker> MARKER = RenderStateDataKey.create(() -> "Nature seed mark");
    private static final int FADE_TICKS = 15;

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            context.worldState().setData(MARKER, null);
            int id = ClientPlayerData.seedMarkEntity();
            long left = ClientPlayerData.seedMarkUntil() - context.world().getTime();
            if (id < 0 || left <= 0) return;
            var client = MinecraftClient.getInstance();
            if (!(context.world().getEntityById(id) instanceof LivingEntity target) || !target.isAlive()
                    || target.isInvisible() || target == client.getCameraEntity()) return;
            float delta = context.tickCounter().getTickProgress(false);
            float time = context.world().getTime() + delta;
            Vec3d above = target.getLerpedPos(delta).add(0, target.getHeight() + 0.45 + MathHelper.sin(time * 0.16f) * 0.06, 0);
            // Large enough to read over a boss, never so large that it hides a zombie's head.
            float scale = MathHelper.clamp(target.getWidth() * 0.75f, 0.8f, 1.8f);
            context.worldState().setData(MARKER, new Marker(above.subtract(context.camera().getPos()),
                    time * 0.14f, scale, MathHelper.clamp(left / (float) FADE_TICKS, 0, 1)));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            Marker marker = context.worldState().getData(MARKER);
            if (marker == null) return;
            var out = context.consumers().getBuffer(NatureMeshes.LAYER);
            var matrices = context.matrices();
            matrices.push();
            matrices.translate(marker.relative.x, marker.relative.y, marker.relative.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(marker.spin));
            matrices.scale(marker.scale, marker.scale, marker.scale);
            NatureMeshes.draw("seed", out, matrices.peek(), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    marker.opacity, -1, null, false);
            matrices.pop();
        });
    }

    private SeedMarkClientEffects() {}
}
