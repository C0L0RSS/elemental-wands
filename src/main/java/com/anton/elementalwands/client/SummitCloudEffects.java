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
 * everything below it, and thin wisps drifting just above. In phase two's storm the sea darkens,
 * and a heavy ceiling of storm cloud closes over the summit with torn scud racing beneath it; a
 * lightning flash lights them all. Every layer is a tiling sheet from
 * {@code art/storm_summit/build_layout.py}, one pixel per block, sliding with the wind.
 * Presentation only; the world's fog fades them into the horizon.
 */
public final class SummitCloudEffects {
    private static final RenderLayer SEA = RenderLayer.getEntityCutoutNoCull(texture("summit_clouds"));
    private static final RenderLayer WISPS = RenderLayer.getEntityTranslucent(texture("summit_wisps"));
    private static final RenderLayer STORM = RenderLayer.getEntityTranslucent(texture("summit_storm"));
    private static final RenderLayer SCUD = RenderLayer.getEntityTranslucent(texture("summit_scud"));
    private static final RenderLayer CAP = RenderLayer.getEntityTranslucent(texture("summit_cap"));
    /** Each sheet covers this many blocks before it repeats. */
    private static final int PERIOD = 256;
    private static final double WISP_HEIGHT = 3.5, SCUD_DROP = 11;
    /** The cap cloud is solid out to CAP_SOLID from the summit's centre and frays away by CAP_EDGE. */
    private static final double CAP_SOLID = 60, CAP_EDGE = 125;
    /** Cells of the cap's grid divide the sheet's repeat, so none spans two copies of the texture. */
    private static final int CAP_CELL = 16;

    /** One textured square at a fixed height, relative to the camera. */
    private record Quad(float x0, float z0, float x1, float z1, float y, float u0, float v0, float u1, float v1) {}
    private record Sheet(List<Quad> quads, int color, boolean below) {}
    /** Phase one's cap cloud around the summit's centre, relative to the camera; alpha 0 draws nothing. */
    private record Cap(double x, double y, double z, double driftX, double driftZ, float alpha, float light) {}
    private record Layers(Sheet sea, Sheet wisps, Sheet storm, Sheet scud, Cap cap) {}
    private static final Sheet EMPTY = new Sheet(List.of(), 0, false);
    private static final Cap CLEAR = new Cap(0, 0, 0, 0, 0, 0, 0);
    private static final Layers NONE = new Layers(EMPTY, EMPTY, EMPTY, EMPTY, CLEAR);
    private static final RenderStateDataKey<Layers> LAYERS = RenderStateDataKey.create(() -> "Storm Summit sea of clouds");

    private SummitCloudEffects() {}

    private static Identifier texture(String name) {
        return Identifier.of(ElementalWandsMod.MOD_ID, "textures/environment/" + name + ".png");
    }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            if (context.world() == null || context.world().getRegistryKey() != StormSummit.WORLD) {
                context.worldState().setData(LAYERS, NONE);
                return;
            }
            Vec3d camera = context.camera().getPos();
            float tick = context.tickCounter().getTickProgress(false);
            double seconds = (context.world().getTime() + tick) / 20.0;
            double radius = MinecraftClient.getInstance().options.getClampedViewDistance() * 16 * 1.6 + 64;
            float storm = SummitStormClient.level(tick);
            // A flash lights the clouds for the two ticks the sky flashes.
            float flash = context.world().getLightningTicksLeft() > 0 ? 1 : 0;
            float dim = storm * (1 - .55f * flash);
            // The wind blows from the west, as it does across the summit's snow.
            Sheet sea = new Sheet(sheet(camera, StormSummit.CLOUD_Y, seconds * .9, seconds * .35, radius),
                    tint(1 - .5f * dim, 1 - .45f * dim, 1 - .35f * dim, 1), false);
            Sheet wisps = new Sheet(sheet(camera, StormSummit.CLOUD_Y + WISP_HEIGHT, seconds * 2.2, seconds, radius),
                    tint(1 - .55f * dim, 1 - .5f * dim, 1 - .4f * dim, 1), false);
            Sheet ceiling = EMPTY, scud = EMPTY;
            if (storm > 0) {
                // Dim between flashes, so a flash can light the clouds from inside.
                float light = .66f + .34f * flash;
                ceiling = new Sheet(sheet(camera, StormSummit.STORM_CEILING, seconds * 2.6, seconds * 1.1, radius),
                        tint(light, light, light, Math.min(1, storm * 1.25f)), true);
                scud = new Sheet(sheet(camera, StormSummit.STORM_CEILING - SCUD_DROP, seconds * 7.5, seconds * 2.8, radius),
                        tint(light * 1.1f, light * 1.1f, light * 1.15f, storm), true);
            }
            // Phase one's cap gives way to the storm's ceiling as it rolls in.
            Cap cap = CLEAR;
            float capLevel = SummitStormClient.cap(tick) * (1 - storm);
            if (capLevel > 0) {
                var centre = StormSummit.nearestCentre(camera);
                cap = new Cap(centre.getX() + .5 - camera.x, StormSummit.CAP_Y - camera.y, centre.getZ() + .5 - camera.z,
                        seconds * 1.6, seconds * .6, capLevel, Math.min(1, .82f + .3f * flash));
            }
            context.worldState().setData(LAYERS, new Layers(sea, wisps, ceiling, scud, cap));
        });
        // The storm sheets go down before entities. Lightning effects don't write depth, so sheets
        // drawn after them would paint over every bolt in the sky; drawn first, they still hide
        // whatever reaches up inside the clouds.
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            Layers layers = context.worldState().getDataOrDefault(LAYERS, NONE);
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            if (layers.cap().alpha() > 0) {
                cap(consumers.getBuffer(CAP), entry, layers.cap());
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(CAP);
            }
            if (layers.storm().quads().isEmpty()) return;
            draw(consumers, entry, STORM, layers.storm());
            draw(consumers, entry, SCUD, layers.scud());
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            Layers layers = context.worldState().getDataOrDefault(LAYERS, NONE);
            if (layers.sea().quads().isEmpty()) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            draw(consumers, entry, SEA, layers.sea());
            draw(consumers, entry, WISPS, layers.wisps());
        });
    }

    private static int tint(float r, float g, float b, float a) {
        return Math.round(Math.clamp(a, 0, 1) * 255) << 24 | Math.round(Math.clamp(r, 0, 1) * 255) << 16
                | Math.round(Math.clamp(g, 0, 1) * 255) << 8 | Math.round(Math.clamp(b, 0, 1) * 255);
    }

    /**
     * Squares cut on the sheet's own repeat, so each maps into one copy of the texture, covering
     * a square of {@code radius} around the camera. {@code driftX/Z} slide the sheet downwind.
     */
    private static List<Quad> sheet(Vec3d camera, double y, double driftX, double driftZ, double radius) {
        List<Quad> out = new ArrayList<>();
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
        return List.copyOf(out);
    }

    private static void draw(VertexConsumerProvider consumers, MatrixStack.Entry entry, RenderLayer layer, Sheet sheet) {
        if (sheet.quads().isEmpty()) return;
        VertexConsumer out = consumers.getBuffer(layer);
        int c = sheet.color();
        for (Quad q : sheet.quads()) {
            // Seen from underneath, the storm sheets wind the other way. Every normal points up:
            // per-face entity lighting would leave a downward one at its dim ambient term.
            if (sheet.below()) {
                corner(out, entry, q.x0(), q.y(), q.z0(), q.u0(), q.v0(), c);
                corner(out, entry, q.x1(), q.y(), q.z0(), q.u1(), q.v0(), c);
                corner(out, entry, q.x1(), q.y(), q.z1(), q.u1(), q.v1(), c);
                corner(out, entry, q.x0(), q.y(), q.z1(), q.u0(), q.v1(), c);
            } else {
                corner(out, entry, q.x0(), q.y(), q.z0(), q.u0(), q.v0(), c);
                corner(out, entry, q.x0(), q.y(), q.z1(), q.u0(), q.v1(), c);
                corner(out, entry, q.x1(), q.y(), q.z1(), q.u1(), q.v1(), c);
                corner(out, entry, q.x1(), q.y(), q.z0(), q.u1(), q.v0(), c);
            }
        }
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /**
     * The cap cloud: a grid of cells over the summit, each corner faded by its distance from the
     * centre so the deck frays out into clear sky. It is seen from below, like the storm's ceiling.
     */
    private static void cap(VertexConsumer out, MatrixStack.Entry entry, Cap cap) {
        int repeat = PERIOD / CAP_CELL;
        long fromX = (long) Math.floor((-CAP_EDGE - cap.driftX()) / CAP_CELL), toX = (long) Math.ceil((CAP_EDGE - cap.driftX()) / CAP_CELL);
        long fromZ = (long) Math.floor((-CAP_EDGE - cap.driftZ()) / CAP_CELL), toZ = (long) Math.ceil((CAP_EDGE - cap.driftZ()) / CAP_CELL);
        for (long i = fromX; i < toX; i++)
            for (long j = fromZ; j < toZ; j++) {
                double x0 = cap.driftX() + i * CAP_CELL, z0 = cap.driftZ() + j * CAP_CELL, x1 = x0 + CAP_CELL, z1 = z0 + CAP_CELL;
                double nearest = Math.hypot(Math.max(0, Math.max(x0, -x1)), Math.max(0, Math.max(z0, -z1)));
                if (nearest >= CAP_EDGE) continue;
                float u0 = Math.floorMod(i, repeat) / (float) repeat, v0 = Math.floorMod(j, repeat) / (float) repeat;
                float u1 = u0 + 1f / repeat, v1 = v0 + 1f / repeat;
                capCorner(out, entry, cap, x0, z0, u0, v0);
                capCorner(out, entry, cap, x1, z0, u1, v0);
                capCorner(out, entry, cap, x1, z1, u1, v1);
                capCorner(out, entry, cap, x0, z1, u0, v1);
            }
    }

    private static void capCorner(VertexConsumer out, MatrixStack.Entry entry, Cap cap, double x, double z, float u, float v) {
        double f = Math.clamp((Math.hypot(x, z) - CAP_SOLID) / (CAP_EDGE - CAP_SOLID), 0, 1);
        float alpha = cap.alpha() * (float) (1 - f * f * (3 - 2 * f));
        int color = tint(cap.light(), cap.light(), cap.light(), alpha);
        corner(out, entry, (float) (cap.x() + x), (float) cap.y(), (float) (cap.z() + z), u, v, color);
    }

    private static void corner(VertexConsumer out, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int color) {
        out.vertex(entry, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0, 1, 0);
    }
}
