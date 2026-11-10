package com.anton.elementalwands.client;

import com.anton.elementalwands.hall.PyreHall;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

/**
 * Haze lying on the Burned Throne Hall's lava sea, so the sea fades into the sky instead of ending
 * in a line. Minecraft's fog is linear: it reaches full strength at its end distance with a sudden
 * stop, and on a bright flat sea seen at a grazing angle that stop reads as a hard horizon. This
 * draws a ring of fog-coloured haze just above the lava around the camera, clear near the crag and
 * easing to opaque before the fog's end, so the sea's last stretch blends smoothly into the haze.
 * Anything standing out of the sea (spires, piers, the crag) rises through it and keeps the plain
 * fog. It also covers the sea beyond the fog wherever chunks are missing. Presentation only.
 */
public final class PyreSeaHaze {
    /** The hall's fog (blocks), applied by {@code NaveFogMixin}; the end comes in with a short render distance. */
    public static final float FOG_START = 24, FOG_END = 170;
    /** The haze starts clear at this fraction of the fog's end and is opaque from the second. */
    private static final double CLEAR = .3, OPAQUE = .9;
    /** Above the lava's surface (a source block's top is 8/9 up), far enough to never fight it for depth. */
    private static final double LIFT = 8 / 9.0 + .3;
    private static final int SEGMENTS = 96, RINGS = 24;
    private static final RenderLayer HAZE = RenderLayer.getDebugQuads();
    private record Vertex(float x, float y, float z, int color) {}
    private static final Vertex[] NONE = {};
    private static final RenderStateDataKey<Vertex[]> QUADS = RenderStateDataKey.create(() -> "Burned Throne Hall sea haze");
    /** This frame's fog colour, as the fog renderer handed it to the world renderer. */
    private static volatile Vector4f fogColor;

    private PyreSeaHaze() {}

    /** The hall fog's end for a render distance in blocks (the client's, capped by the server's). */
    public static float fogEnd(float viewDistanceBlocks) { return Math.min(FOG_END, viewDistanceBlocks * .95f); }

    public static void fogColor(Vector4f color) { fogColor = new Vector4f(color); }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            Vertex[] out = NONE;
            Vector4f fog = fogColor;
            if (context.world() != null && context.world().getRegistryKey() == PyreHall.WORLD && fog != null) {
                Vec3d camera = context.camera().getPos();
                double y = PyreHall.SURFACE_Y + PyreHall.LAVA_Y + LIFT;
                if (camera.y > y + .5) out = ring(y - camera.y, fog);
            }
            context.worldState().setData(QUADS, out);
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            Vertex[] quads = context.worldState().getDataOrDefault(QUADS, NONE);
            if (quads.length == 0) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            var haze = consumers.getBuffer(HAZE);
            for (Vertex v : quads) haze.vertex(entry, v.x(), v.y(), v.z()).color(v.color());
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(HAZE);
        });
    }

    /** The haze ring around the camera, camera-relative, from clear to opaque and on past the fog's end. */
    private static Vertex[] ring(double dy, Vector4f fog) {
        float end = fogEnd(MinecraftClient.getInstance().options.getClampedViewDistance() * 16f);
        double[] radius = new double[RINGS + 2];
        int[] alpha = new int[RINGS + 2];
        for (int i = 0; i <= RINGS; i++) {
            double t = i / (double) RINGS;
            radius[i] = end * (CLEAR + (OPAQUE - CLEAR) * t);
            alpha[i] = (int) Math.round(255 * t * t * t * (t * (6 * t - 15) + 10));
        }
        radius[RINGS + 1] = end + 48;
        alpha[RINGS + 1] = 255;
        int rgb = Math.round(Math.clamp(fog.x, 0, 1) * 255) << 16 | Math.round(Math.clamp(fog.y, 0, 1) * 255) << 8
                | Math.round(Math.clamp(fog.z, 0, 1) * 255);
        Vertex[] out = new Vertex[SEGMENTS * (RINGS + 1) * 4];
        int n = 0;
        for (int s = 0; s < SEGMENTS; s++) {
            double a0 = 2 * Math.PI * s / SEGMENTS, a1 = 2 * Math.PI * (s + 1) / SEGMENTS;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            for (int i = 0; i <= RINGS; i++) {
                double r0 = radius[i], r1 = radius[i + 1];
                int in = alpha[i] << 24 | rgb, outer = alpha[i + 1] << 24 | rgb;
                out[n++] = vertex(c0 * r0, dy, s0 * r0, in);
                out[n++] = vertex(c0 * r1, dy, s0 * r1, outer);
                out[n++] = vertex(c1 * r1, dy, s1 * r1, outer);
                out[n++] = vertex(c1 * r0, dy, s1 * r0, in);
            }
        }
        return out;
    }

    private static Vertex vertex(double x, double y, double z, int color) { return new Vertex((float) x, (float) y, (float) z, color); }
}
