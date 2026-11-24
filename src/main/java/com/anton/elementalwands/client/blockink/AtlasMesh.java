package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.client.blockfire.BlockFireKit;
import java.util.Arrays;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;

/**
 * One frame of cubes textured from a 4 x 8 effect atlas (the block kits' layout), stored camera-relative: the same
 * box as the block-fire and void-glass meshes, for any atlas. Solid cubes take the baked face shade and draw with a
 * cutout; see-through ones are sorted back to front. In first person, cubes near the lens shrink away as the kits'
 * do. The ink effects use one for their own atlas and one for the Space atlas's colours.
 */
public final class AtlasMesh {
    private static final int COLUMNS = 4, ROWS = 8;
    private static final int MAX_CUBES = 5000;
    private static final int[][][] FACES = {
            {{1,-1,1},{1,-1,-1},{1,1,-1},{1,1,1}}, {{-1,-1,-1},{-1,-1,1},{-1,1,1},{-1,1,-1}},
            {{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}}, {{1,-1,-1},{-1,-1,-1},{-1,1,-1},{1,1,-1}},
            {{-1,1,1},{1,1,1},{1,1,-1},{-1,1,-1}}, {{-1,-1,-1},{1,-1,-1},{1,-1,1},{-1,-1,1}}};
    private static final int[][] NORMALS = {{0,1},{0,-1},{2,1},{2,-1},{1,1},{1,-1}};

    public final RenderLayer solidLayer, seeLayer;
    public final Vec3d camera;
    public final boolean lens;
    public final double time;
    private final BlockRenderView world;
    private final Quads solid = new Quads(), see = new Quads();
    private int cubes;

    public AtlasMesh(Identifier atlas, Vec3d camera, boolean lens, double time, BlockRenderView world) {
        this.solidLayer = RenderLayer.getEntityCutoutNoCull(atlas);
        this.seeLayer = RenderLayer.getEntityTranslucent(atlas);
        this.camera = camera; this.lens = lens; this.time = time; this.world = world;
    }

    public boolean isEmpty() { return solid.size == 0 && see.size == 0; }
    public int cubes() { return cubes; }

    /**
     * An oriented cuboid: axes are three orthonormal, right-handed vectors, h the half extents. row and frame pick the
     * atlas cell (texture "up" follows axes[1]). glow 1 is full-bright, 0 lit by the world. Below alpha 1 it is see-through.
     */
    public void box(Vec3d c, double hx, double hy, double hz, Vec3d[] axes, int row, int frame, double glow, double alpha) {
        if (cubes >= MAX_CUBES) return;
        if (lens) {
            double d = c.distanceTo(camera), h = Math.max(hx, Math.max(hy, hz));
            double hidden = .35 + h * .9, visible = hidden + Math.max(.75, h * .85);
            double s = BlockFireKit.smoothstep((d - hidden) / (visible - hidden));
            if (s <= 0) return;
            hx *= s; hy *= s; hz *= s;
        }
        if (!(hx > 1e-4 && hy > 1e-4 && hz > 1e-4) || alpha <= .01) return;
        cubes++;
        boolean through = alpha < .995;
        Quads out = through ? see : solid;
        double[] half = {hx, hy, hz};
        double ox = c.x - camera.x, oy = c.y - camera.y, oz = c.z - camera.z;
        float u0 = frame / (float) COLUMNS, v0 = row / (float) ROWS, u1 = u0 + 1f / COLUMNS, v1 = v0 + 1f / ROWS;
        float[] us = {u0, u1, u1, u0}, vs = {v1, v1, v0, v0};
        int light = light(c, glow);
        int a = (int) Math.round(Math.clamp(alpha, 0, 1) * 255);
        out.begin(ox * ox + oy * oy + oz * oz);
        for (int f = 0; f < 6; f++) {
            Vec3d n = axes[NORMALS[f][0]];
            double sign = NORMALS[f][1];
            double tone = .80 + .20 * n.y * sign + .07 * n.z * sign + .03 * n.x * sign;
            if (through) tone = 1 - (1 - tone) * .4;
            int g = (int) Math.round(Math.clamp(tone, 0, 1) * 255);
            int color = (a << 24) | (g << 16) | (g << 8) | g;
            for (int i = 0; i < 4; i++) {
                int[] k = FACES[f][i];
                double x = ox, y = oy, z = oz;
                for (int j = 0; j < 3; j++) {
                    double s = k[j] * half[j];
                    x += axes[j].x * s; y += axes[j].y * s; z += axes[j].z * s;
                }
                out.add((float) x, (float) y, (float) z, us[i], vs[i], color, light);
            }
        }
    }

    private int light(Vec3d c, double glow) {
        if (glow >= 1 || world == null) return LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int packed = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(c));
        int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(packed), (int) Math.round(glow * 15));
        return LightmapTextureManager.pack(block, LightmapTextureManager.getSkyLightCoordinates(packed));
    }

    public boolean hasSolid() { return solid.size > 0; }
    public boolean hasSee() { return see.size > 0; }
    /** Draws the frame; positions are camera-relative, so entry must carry only the camera's rotation. */
    public void emitSolid(VertexConsumer out, MatrixStack.Entry entry) { solid.emit(out, entry, false); }
    public void emitSee(VertexConsumer out, MatrixStack.Entry entry) { see.emit(out, entry, true); }

    /** Packed vertices (x, y, z, u, v as floats; colour and light as ints), grouped per cube for sorting. */
    private static final class Quads {
        private static final int FLOATS = 5 * 24, INTS = 2 * 24;
        float[] data = new float[FLOATS * 64];
        int[] ints = new int[INTS * 64];
        double[] depth = new double[64];
        int size, count, boxes;
        void begin(double distance) {
            if (boxes == depth.length) depth = Arrays.copyOf(depth, boxes * 2);
            depth[boxes++] = distance;
        }
        void add(float x, float y, float z, float u, float v, int color, int light) {
            if (size + 5 > data.length) { data = Arrays.copyOf(data, data.length * 2); ints = Arrays.copyOf(ints, ints.length * 2); }
            data[size++] = x; data[size++] = y; data[size++] = z; data[size++] = u; data[size++] = v;
            ints[count++] = color; ints[count++] = light;
        }
        void emit(VertexConsumer out, MatrixStack.Entry entry, boolean sorted) {
            if (!sorted) { for (int b = 0; b < boxes; b++) emitBox(out, entry, b); return; }
            Integer[] order = new Integer[boxes];
            for (int b = 0; b < boxes; b++) order[b] = b;
            Arrays.sort(order, (p, q) -> Double.compare(depth[q], depth[p]));
            for (int b : order) emitBox(out, entry, b);
        }
        private void emitBox(VertexConsumer out, MatrixStack.Entry entry, int b) {
            for (int i = b * FLOATS, j = b * INTS, end = i + FLOATS; i < end; i += 5, j += 2)
                out.vertex(entry, data[i], data[i + 1], data[i + 2]).color(ints[j]).texture(data[i + 3], data[i + 4])
                        .overlay(OverlayTexture.DEFAULT_UV).light(ints[j + 1]).normal(entry, 0, 1, 0);
        }
    }
}
