package com.anton.elementalwands.client;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.summit.StormSummit;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The sea of clouds under the Storm Summit: a lit, puffy layer at the cloud top that hides
 * everything below it, and thin wisps drifting just above. Both are tiling sheets from
 * {@code art/storm_summit/build_layout.py}, one pixel per block, sliding slowly with the wind.
 * Presentation only; the world's fog fades them into the horizon.
 */
public final class SummitCloudEffects {
    private static final RenderLayer SEA = RenderLayer.getEntityCutoutNoCull(
            Identifier.of(ElementalWandsMod.MOD_ID, "textures/environment/summit_clouds.png"));
    private static final RenderLayer WISPS = RenderLayer.getEntityTranslucent(
            Identifier.of(ElementalWandsMod.MOD_ID, "textures/environment/summit_wisps.png"));
    /** Each sheet covers this many blocks before it repeats. */
    private static final int PERIOD = 256;
    private static final double WISP_HEIGHT = 3.5;

    /** One textured square at a fixed height, relative to the camera. */
    private record Quad(float x0, float z0, float x1, float z1, float y, float u0, float v0, float u1, float v1) {}
    private record Layers(List<Quad> sea, List<Quad> wisps) {}
    private static final Layers NONE = new Layers(List.of(), List.of());
    private static final RenderStateDataKey<Layers> LAYERS = RenderStateDataKey.create(() -> "Storm Summit sea of clouds");

    private SummitCloudEffects() {}

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            if (context.world() == null || context.world().getRegistryKey() != StormSummit.WORLD) {
                context.worldState().setData(LAYERS, NONE);
                return;
            }
            Vec3d camera = context.camera().getPos();
            double seconds = (context.world().getTime() + context.tickCounter().getTickProgress(false)) / 20.0;
            double radius = MinecraftClient.getInstance().options.getClampedViewDistance() * 16 * 1.6 + 64;
            List<Quad> sea = new ArrayList<>(), wisps = new ArrayList<>();
            // The wind blows from the west, as it does across the summit's snow.
            sheet(sea, camera, StormSummit.CLOUD_Y, seconds * .9, seconds * .35, radius);
            sheet(wisps, camera, StormSummit.CLOUD_Y + WISP_HEIGHT, seconds * 2.2, seconds, radius);
            context.worldState().setData(LAYERS, new Layers(List.copyOf(sea), List.copyOf(wisps)));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            Layers layers = context.worldState().getDataOrDefault(LAYERS, NONE);
            if (layers.sea().isEmpty()) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            draw(consumers.getBuffer(SEA), entry, layers.sea());
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(SEA);
            draw(consumers.getBuffer(WISPS), entry, layers.wisps());
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(WISPS);
        });
    }

    /**
     * Squares cut on the sheet's own repeat, so each maps into one copy of the texture, covering
     * a square of {@code radius} around the camera. {@code driftX/Z} slide the sheet downwind.
     */
    private static void sheet(List<Quad> out, Vec3d camera, double y, double driftX, double driftZ, double radius) {
        double minX = camera.x - radius, maxX = camera.x + radius, minZ = camera.z - radius, maxZ = camera.z + radius;
        double startX = Math.floor((minX - driftX) / PERIOD) * PERIOD + driftX;
        double startZ = Math.floor((minZ - driftZ) / PERIOD) * PERIOD + driftZ;
        for (double x = startX; x < maxX; x += PERIOD)
            for (double z = startZ; z < maxZ; z += PERIOD) {
                double ax = Math.max(x, minX), bx = Math.min(x + PERIOD, maxX);
                double az = Math.max(z, minZ), bz = Math.min(z + PERIOD, maxZ);
                out.add(new Quad((float) (ax - camera.x), (float) (az - camera.z), (float) (bx - camera.x), (float) (bz - camera.z),
                        (float) (y - camera.y), (float) ((ax - x) / PERIOD), (float) ((az - z) / PERIOD),
                        (float) ((bx - x) / PERIOD), (float) ((bz - z) / PERIOD)));
            }
    }

    private static void draw(VertexConsumer out, MatrixStack.Entry entry, List<Quad> quads) {
        for (Quad q : quads) {
            corner(out, entry, q.x0(), q.y(), q.z0(), q.u0(), q.v0());
            corner(out, entry, q.x0(), q.y(), q.z1(), q.u0(), q.v1());
            corner(out, entry, q.x1(), q.y(), q.z1(), q.u1(), q.v1());
            corner(out, entry, q.x1(), q.y(), q.z0(), q.u1(), q.v0());
        }
    }

    private static void corner(VertexConsumer out, MatrixStack.Entry entry, float x, float y, float z, float u, float v) {
        out.vertex(entry, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0, 1, 0);
    }
}
