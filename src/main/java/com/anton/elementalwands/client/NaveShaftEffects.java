package com.anton.elementalwands.client;

import com.anton.elementalwands.arena.ShatteredNave;
import com.anton.elementalwands.arena.ShatteredNaveShafts;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Coloured light falling into the Shattered Nave from windows nobody can see. Minecraft light has
 * no colour, so the shafts are drawn here (the floor pools under them are invisible light blocks).
 * Presentation only; they fade with distance the way the nave's haze does.
 */
public final class NaveShaftEffects {
    /** Additive (src-alpha, one) without depth writes, so shafts glow and overlap softly. */
    private static final RenderLayer GLOW = RenderLayer.getDragonRays();
    private static final double FADE_START = 50, FADE_END = 150;
    private static final int SEGMENTS = 10;
    private record Vertex(float x, float y, float z, int color) {}
    private static final RenderStateDataKey<List<Vertex>> TRIANGLES = RenderStateDataKey.create(() -> "Shattered Nave light shafts");

    private NaveShaftEffects() {}

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            List<Vertex> out = new ArrayList<>();
            if (context.world() != null && context.world().getRegistryKey() == ShatteredNave.WORLD) {
                Vec3d camera = context.camera().getPos();
                BlockPos centre = ShatteredNave.nearestCentre(camera);
                for (double[] shaft : ShatteredNaveShafts.SHAFTS) shaft(out, context.frustum(), camera, centre, shaft);
            }
            context.worldState().setData(TRIANGLES, List.copyOf(out));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            List<Vertex> vertices = context.worldState().getDataOrDefault(TRIANGLES, List.of());
            if (vertices.isEmpty()) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            var glow = consumers.getBuffer(GLOW);
            for (Vertex v : vertices) glow.vertex(entry, v.x(), v.y(), v.z()).color(v.color());
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(GLOW);
        });
    }

    /** Offsets from {@link ShatteredNaveShafts}: bottom x, z; top x, y, z; width; colour. */
    private static void shaft(List<Vertex> out, Frustum frustum, Vec3d camera, BlockPos centre, double[] s) {
        Vec3d bottom = new Vec3d(centre.getX() + s[0], ShatteredNave.SURFACE_Y + .5, centre.getZ() + s[1]);
        Vec3d top = new Vec3d(centre.getX() + s[2], ShatteredNave.SURFACE_Y + s[3], centre.getZ() + s[4]);
        double width = s[5];
        int rgb = (int) s[6];
        if (bottom.distanceTo(camera) > FADE_END + 60 || !frustum.isVisible(new Box(bottom, top).expand(width * 1.3))) return;
        // A wide faint veil around a brighter core, as in the approved preview.
        ribbon(out, camera, top, bottom, width * 2.6, rgb, .045);
        ribbon(out, camera, top, bottom, width * 1.4, rgb, .07);
        ribbon(out, camera, top, bottom, width * .6, rgb, .08);
    }

    /** A strip turned toward the camera around the shaft's axis, bright down the middle, clear at the edges. */
    private static void ribbon(List<Vertex> out, Vec3d camera, Vec3d top, Vec3d bottom, double width, int rgb, double strength) {
        Vec3d axis = bottom.subtract(top);
        Vec3d[] middle = new Vec3d[SEGMENTS + 1], left = new Vec3d[SEGMENTS + 1], right = new Vec3d[SEGMENTS + 1];
        double[] alpha = new double[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double v = i / (double) SEGMENTS;
            Vec3d p = top.add(axis.multiply(v));
            Vec3d side = axis.crossProduct(camera.subtract(p));
            if (side.lengthSquared() < 1e-6) return;
            side = side.normalize().multiply(width / 2);
            // Faint where it leaves the dark overhead, full on the way down, a little softer at the floor.
            double fade = smooth(0, .55, v) * (1 - .35 * smooth(.9, 1, v));
            double haze = 1 - smooth(FADE_START, FADE_END, p.distanceTo(camera));
            middle[i] = p.subtract(camera);
            left[i] = middle[i].subtract(side);
            right[i] = middle[i].add(side);
            alpha[i] = strength * fade * haze;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            if (alpha[i] <= 0 && alpha[i + 1] <= 0) continue;
            quad(out, left[i], 0, middle[i], alpha[i], middle[i + 1], alpha[i + 1], left[i + 1], 0, rgb);
            quad(out, middle[i], alpha[i], right[i], 0, right[i + 1], 0, middle[i + 1], alpha[i + 1], rgb);
        }
    }

    private static void quad(List<Vertex> out, Vec3d a, double aa, Vec3d b, double ba, Vec3d c, double ca, Vec3d d, double da, int rgb) {
        vertex(out, a, aa, rgb); vertex(out, b, ba, rgb); vertex(out, c, ca, rgb);
        vertex(out, a, aa, rgb); vertex(out, c, ca, rgb); vertex(out, d, da, rgb);
    }

    private static void vertex(List<Vertex> out, Vec3d p, double alpha, int rgb) {
        int a = (int) Math.round(Math.clamp(alpha, 0, 1) * 255);
        out.add(new Vertex((float) p.x, (float) p.y, (float) p.z, a << 24 | rgb));
    }

    private static double smooth(double from, double to, double x) {
        double t = Math.clamp((x - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
