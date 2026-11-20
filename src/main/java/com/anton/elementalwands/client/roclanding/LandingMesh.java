package com.anton.elementalwands.client.roclanding;

import static com.anton.elementalwands.client.roclanding.L.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * One frame of a Storm Roc landing's effects, drawn the way the browser animatic draws them so its
 * code ports line for line: lit solid geometry (boxes, beams, leaves, discs, the game's Nature
 * meshes), see-through flat shapes, soft cloud puffs facing the camera, additive glow sprites and
 * additive ribbons. Points are in the summit's frame, as the track's are; the mesh moves them to
 * the camera. Solids carry the animatic's sun shading in their colour and draw full-bright, like
 * the intro's actors.
 */
public final class LandingMesh {
    private static final Identifier WHITE = Identifier.of("elementalwands", "textures/misc/roc_white.png");
    private static final Identifier PUFF = Identifier.of("elementalwands", "textures/misc/storm_roc_intro_puff.png");
    private static final Identifier GLOW = Identifier.of("elementalwands", "textures/misc/storm_roc_intro_glow.png");
    private static final RenderLayer SOLID_LAYER = RenderLayer.getEntityCutoutNoCull(WHITE);
    private static final RenderLayer SEE_LAYER = RenderLayer.getEntityTranslucent(WHITE);
    private static final RenderLayer PUFF_LAYER = RenderLayer.getEntityTranslucent(PUFF);
    // Light adds: the charged creeper's energy-swirl pipeline is the game's additive one (ONE, ONE),
    // with no texture scroll. (The eyes pipeline blends see-through since 1.21.5, so it darkens.)
    // Both are fed premultiplied colours: the glow texture's RGB already fades with its alpha.
    private static final RenderLayer GLOW_LAYER = RenderLayer.getEnergySwirl(GLOW, 0, 0);
    private static final RenderLayer ADD_LAYER = RenderLayer.getEnergySwirl(WHITE, 0, 0);
    /** The animatic's summit light: the sun's direction and strength. */
    private static final double[] SUN = norm(v(.4, .9, .3));
    private static final int MAX_VERTICES = 400_000;

    /** The camera in the summit's frame, and its right and up. */
    public final double[] eye, right, up;
    /** The summit frame's origin and the camera, in the world: for a landing drawing with the game's own spell meshes too. */
    public final double[] origin, camera;
    private final double ox, oy, oz;
    private final Quads solid = new Quads(), see = new Quads(), puff = new Quads(), glow = new Quads(), add = new Quads();
    /** Textured solids, by texture (block textures such as minecraft:textures/block/stone.png). */
    private final Map<Identifier, Quads> textured = new LinkedHashMap<>();
    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();
    private int vertices;

    /**
     * @param origin the summit frame's origin in the world
     * @param camera the camera in the world
     */
    public LandingMesh(double[] origin, double[] camera, double[] right, double[] up) {
        this.ox = origin[0] - camera[0]; this.oy = origin[1] - camera[1]; this.oz = origin[2] - camera[2];
        this.eye = sub(camera, origin);
        this.origin = origin; this.camera = camera;
        this.right = right; this.up = up;
    }

    public boolean isEmpty() { return solid.size + see.size + puff.size + glow.size + add.size == 0 && textured.isEmpty(); }

    // ------------------------------------------------------------------ solids, as the animatic's

    /** A flat triangle, lit (or see-through when its alpha is under 1). */
    public void tri(double[] a, double[] b, double[] c, double[] col) {
        double[] n = norm(cross(sub(b, a), sub(c, a)));
        tri(a, b, c, n, col);
    }

    /** A triangle with its own normal; faces turned away from the camera are lit as their front. */
    public void tri(double[] a, double[] b, double[] c, double[] n, double[] col) {
        double alpha = col.length > 3 ? Math.min(1, col[3]) : 1;
        if (alpha <= .004) return;
        if (dot(n, sub(eye, a)) < 0) n = scale(n, -1);
        double tone = .5 + .5 * Math.max(0, dot(n, SUN)) + .12 * n[1];
        int color = argb(col[0] * tone, col[1] * tone, col[2] * tone, alpha);
        Quads out = alpha < .999 ? see : solid;
        put(out, a, 0, 0, color); put(out, b, 1, 0, color); put(out, c, 1, 1, color); put(out, c, 1, 1, color);
    }

    /** The animatic's quad: two triangles a b c, a c d. */
    public void quad(double[] a, double[] b, double[] c, double[] d, double[] col) {
        tri(a, b, c, col);
        tri(a, c, d, col);
    }

    /** An axis-aligned box from lo to hi, moved by m (or not, when m is null). */
    public void box(double[] lo, double[] hi, double[] col, double[] m) {
        double x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
        double[][] c = {v(x0, y0, z0), v(x1, y0, z0), v(x1, y1, z0), v(x0, y1, z0), v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1)};
        if (m != null) for (int i = 0; i < 8; i++) c[i] = apply(m, c[i]);
        int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) quad(c[f[0]], c[f[1]], c[f[2]], c[f[3]], col);
    }

    /**
     * An axis-aligned box from lo to hi moved by m, wearing a texture (a 16-pixel block texture
     * reads at a block's scale: a face half a block across shows half of it, from a spot picked by
     * seed), lit by the animatic's sun like the other solids.
     */
    public void box(double[] lo, double[] hi, Identifier texture, double seed, double[] m) {
        double x0 = lo[0], y0 = lo[1], z0 = lo[2], x1 = hi[0], y1 = hi[1], z1 = hi[2];
        double[][] local = {v(x0, y0, z0), v(x1, y0, z0), v(x1, y1, z0), v(x0, y1, z0), v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1)};
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) c[i] = m == null ? local[i] : apply(m, local[i]);
        int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}};
        Quads out = textured.computeIfAbsent(texture, k -> new Quads());
        for (int f = 0; f < 6; f++) {
            int[] q = faces[f];
            double[] n = norm(cross(sub(c[q[1]], c[q[0]]), sub(c[q[2]], c[q[0]])));
            if (dot(n, sub(eye, c[q[0]])) < 0) n = scale(n, -1);
            double tone = .5 + .5 * Math.max(0, dot(n, SUN)) + .12 * n[1];
            int color = argb(tone, tone, tone, 1);
            // The face's own size in blocks along its two edges, as a share of the texture.
            double along = Math.min(1, dist(local[q[1]], local[q[0]])), across = Math.min(1, dist(local[q[3]], local[q[0]]));
            float v0 = (float) ((1 - along) * hash(seed * 3.7 + f)), u0 = (float) ((1 - across) * hash(seed * 5.3 + f * 1.9));
            float v1 = v0 + (float) along, u1 = u0 + (float) across;
            put(out, c[q[0]], u0, v0, color); put(out, c[q[1]], u0, v1, color);
            put(out, c[q[2]], u1, v1, color); put(out, c[q[3]], u1, v0, color);
        }
    }

    /** A square rod from a to b, w across. */
    public void beam(double[] a, double[] b, double w, double[] col) {
        double l = dist(a, b);
        if (l < 1e-4) return;
        box(v(-w / 2, -w / 2, -.01), v(w / 2, w / 2, l + .01), col, along(a, b));
    }

    /** A thin flat blade from a to b, w across. */
    public void leaf(double[] a, double[] b, double w, double[] col) {
        double l = dist(a, b);
        if (l < .01) return;
        box(v(-w / 2, -.012, 0), v(w / 2, .012, l), col, along(a, b));
    }

    /** A radius by angle, for {@link #fan}. */
    public interface Radius { double at(double angle); }

    /** A flat disc at height y round c, its radius by angle. */
    public void fan(double[] c, double y, Radius radius, double[] col, int n) {
        double[] up = v(0, 1, 0);
        for (int i = 0; i < n; i++) {
            double a0 = i / (double) n * Math.PI * 2, a1 = (i + 1) / (double) n * Math.PI * 2, r0 = radius.at(a0), r1 = radius.at(a1);
            tri(v(c[0], y, c[2]), v(c[0] + Math.cos(a1) * r1, y, c[2] + Math.sin(a1) * r1), v(c[0] + Math.cos(a0) * r0, y, c[2] + Math.sin(a0) * r0), up, col);
        }
    }

    /**
     * A mesh in the animatic's solid layout (per vertex: position, normal, red, green, blue, and a
     * fourth value capped at 1 for alpha), moved by m, its normals by n (m when null).
     */
    public void placeMesh(float[] src, double[] m, double[] n) {
        if (n == null) n = m;
        for (int i = 0; i + 29 < src.length; i += 30) {
            double[][] p = new double[3][];
            for (int k = 0; k < 3; k++) p[k] = apply(m, v(src[i + k * 10], src[i + k * 10 + 1], src[i + k * 10 + 2]));
            double[] normal = norm(dir(n, v(src[i + 3], src[i + 4], src[i + 5])));
            tri(p[0], p[1], p[2], normal, new double[]{src[i + 6], src[i + 7], src[i + 8], Math.min(1, src[i + 9])});
        }
    }

    // ------------------------------------------------------------------ light and air

    /**
     * A soft cloud puff facing the camera, size blocks from its centre to its edge, as the
     * animatic's puff program: shaded lighter at the top, faded out near the lens.
     */
    public void puff(double[] c, double size, double[] rgb, double opacity, double seed) {
        double a = opacity * smooth((dist(c, eye) - .5) / 5.5);
        if (a <= .004 || size <= 1e-3) return;
        int cell = (int) Math.floor(hash(seed) * 4) & 3;
        float u0 = (cell & 1) * .5f, v0 = (cell >> 1) * .5f;
        sprite(puff, c, size, argb(rgb[0], rgb[1], rgb[2], a), u0, v0, .5f);
    }

    /** An additive glow facing the camera: rgb times k, faded out near the lens (GLOW_FS). */
    public void glow(double[] c, double[] rgb, double size, double k) {
        k *= smooth((dist(c, eye) - .6) / 2.9);
        if (k <= .003 || size <= 1e-3) return;
        sprite(glow, c, size, argb(rgb[0] * k, rgb[1] * k, rgb[2] * k, 1), 0, 0, 1);
    }

    /** An additive flat quad, its colour times alpha (the animatic's flat_ ribbons, blended SRC_ALPHA, ONE). */
    public void addQuad(double[] a, double[] b, double[] c, double[] d, double[] rgba) {
        double k = rgba.length > 3 ? rgba[3] : 1;
        if (k <= .003) return;
        int color = argb(rgba[0] * k, rgba[1] * k, rgba[2] * k, 1);
        put(add, a, 0, 0, color); put(add, b, 1, 0, color); put(add, c, 1, 1, color); put(add, d, 0, 1, color);
    }

    private void sprite(Quads out, double[] c, double size, int color, float u0, float v0, float cell) {
        double[] r = scale(right, size), u = scale(up, size);
        put(out, sub(sub(c, r), u), u0, v0 + cell, color);
        put(out, sub(add(c, r), u), u0 + cell, v0 + cell, color);
        put(out, add(add(c, r), u), u0 + cell, v0, color);
        put(out, add(sub(c, r), u), u0, v0, color);
    }

    private void put(Quads out, double[] p, float u, float v, int color) {
        if (vertices >= MAX_VERTICES) return;
        vertices++;
        out.add((float) (p[0] + ox), (float) (p[1] + oy), (float) (p[2] + oz), u, v, color);
    }

    private static int argb(double r, double g, double b, double a) {
        return channel(a) << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }
    private static int channel(double c) { return (int) Math.round(clamp(c, 0, 1) * 255); }

    // ------------------------------------------------------------------ drawing

    /** Draws the frame: solids, then see-through shapes and clouds, then light. Positions are camera-relative. */
    public void emit(VertexConsumerProvider consumers, MatrixStack.Entry entry) {
        draw(consumers, entry, SOLID_LAYER, solid);
        for (var e : textured.entrySet())
            draw(consumers, entry, LAYERS.computeIfAbsent(e.getKey(), RenderLayer::getEntityCutoutNoCull), e.getValue());
        draw(consumers, entry, SEE_LAYER, see);
        draw(consumers, entry, PUFF_LAYER, puff);
        draw(consumers, entry, GLOW_LAYER, glow);
        draw(consumers, entry, ADD_LAYER, add);
    }

    private static void draw(VertexConsumerProvider consumers, MatrixStack.Entry entry, RenderLayer layer, Quads quads) {
        if (quads.size == 0) return;
        quads.emit(consumers.getBuffer(layer), entry);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /** Packed vertices: x, y, z, u, v as floats; colour as an int. */
    private static final class Quads {
        float[] data = new float[5 * 4096];
        int[] colors = new int[4096];
        int size, count;
        void add(float x, float y, float z, float u, float v, int color) {
            if (size + 5 > data.length) { data = Arrays.copyOf(data, data.length * 2); colors = Arrays.copyOf(colors, colors.length * 2); }
            data[size++] = x; data[size++] = y; data[size++] = z; data[size++] = u; data[size++] = v;
            colors[count++] = color;
        }
        void emit(VertexConsumer out, MatrixStack.Entry entry) {
            for (int i = 0, j = 0; i < size; i += 5, j++)
                out.vertex(entry, data[i], data[i + 1], data[i + 2]).color(colors[j]).texture(data[i + 3], data[i + 4])
                        .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0, 1, 0);
        }
    }
}
