package com.anton.elementalwands.client.blockfire;

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
 * One frame of block fire: oriented cubes textured from the shared atlas, stored camera-relative.
 * Mirrors the browser study's Mesh.box (art/fire/blockfire/engine.js): the Minecraft-style face shade is
 * baked into the vertex colour, and in first person cubes near the lens shrink away like SpellViewClearance.
 * Fire is opaque and full-bright; smoke is see-through and takes the world's light.
 */
public final class BlockFireMesh {
    public static final Identifier ATLAS = Identifier.of("elementalwands", "textures/misc/blockfire.png");
    static final RenderLayer FIRE = RenderLayer.getEntityCutoutNoCull(ATLAS);
    static final RenderLayer SMOKE = RenderLayer.getEntityTranslucent(ATLAS);
    private static final int COLUMNS = 4, ROWS = 8;
    /** A frame never draws more than this many cubes, whatever is on screen. */
    private static final int MAX_CUBES = 6000;
    // Corner signs of each face along the cube's axes, in the browser's order and winding.
    private static final int[][][] FACES = {
            {{1,-1,1},{1,-1,-1},{1,1,-1},{1,1,1}}, {{-1,-1,-1},{-1,-1,1},{-1,1,1},{-1,1,-1}},
            {{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}}, {{1,-1,-1},{-1,-1,-1},{-1,1,-1},{1,1,-1}},
            {{-1,1,1},{1,1,1},{1,1,-1},{-1,1,-1}}, {{-1,-1,-1},{1,-1,-1},{1,-1,1},{-1,-1,1}}};
    /** Outward normal of each face as (axis, sign). */
    private static final int[][] NORMALS = {{0,1},{0,-1},{2,1},{2,-1},{1,1},{1,-1}};

    public final Vec3d camera;
    public final boolean lens, embers;
    /** Seconds on the shared clock that drives atlas frames and default flicker. */
    public final double time;
    private final BlockRenderView world;
    private final Quads fire = new Quads(), smoke = new Quads();
    private int cubes;

    public BlockFireMesh(Vec3d camera, boolean lens, boolean embers, double time, BlockRenderView world) {
        this.camera = camera; this.lens = lens; this.embers = embers; this.time = time; this.world = world;
    }

    public boolean isEmpty() { return fire.size == 0 && smoke.size == 0; }
    public int cubes() { return cubes; }

    /**
     * An oriented cuboid: axes are three orthonormal, right-handed vectors and h holds the half extents.
     * row and frame pick the atlas cell (texture "up" follows axes[1]); tint multiplies the colour.
     */
    void box(Vec3d c, double hx, double hy, double hz, Vec3d[] axes, int row, int frame, double tint, double alpha, boolean see) {
        if (cubes >= MAX_CUBES) return;
        if (lens) {
            double d = c.distanceTo(camera), h = Math.max(hx, Math.max(hy, hz));
            double hidden = .35 + h * .9, visible = hidden + Math.max(.75, h * .85);
            double s = BlockFireKit.smoothstep((d - hidden) / (visible - hidden));
            if (s <= 0) return;
            hx *= s; hy *= s; hz *= s;
        }
        if (!(hx > 1e-4 && hy > 1e-4 && hz > 1e-4)) return;
        cubes++;
        Quads out = see ? smoke : fire;
        double[] half = {hx, hy, hz};
        double ox = c.x - camera.x, oy = c.y - camera.y, oz = c.z - camera.z;
        float u0 = frame / (float) COLUMNS, v0 = row / (float) ROWS, u1 = u0 + 1f / COLUMNS, v1 = v0 + 1f / ROWS;
        float[] us = {u0, u1, u1, u0}, vs = {v1, v1, v0, v0};
        int light = see ? WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(c)) : LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int a = (int) Math.round(Math.clamp(alpha, 0, 1) * 255);
        for (int f = 0; f < 6; f++) {
            Vec3d n = axes[NORMALS[f][0]];
            double sign = NORMALS[f][1];
            double tone = see ? 1 : .80 + .20 * n.y * sign + .07 * n.z * sign + .03 * n.x * sign;
            int g = (int) Math.round(Math.clamp(tone * tint, 0, 1) * 255);
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

    /** Draws the frame; positions are camera-relative, so entry must carry only the camera's rotation. */
    public void emitFire(VertexConsumer out, MatrixStack.Entry entry) { fire.emit(out, entry); }
    public void emitSmoke(VertexConsumer out, MatrixStack.Entry entry) { smoke.emit(out, entry); }
    public boolean hasFire() { return fire.size > 0; }
    public boolean hasSmoke() { return smoke.size > 0; }

    /** Packed vertices: x, y, z, u, v as floats; colour and light as ints. */
    private static final class Quads {
        float[] data = new float[5 * 24 * 64];
        int[] ints = new int[2 * 24 * 64];
        int size, count;
        void add(float x, float y, float z, float u, float v, int color, int light) {
            if (size + 5 > data.length) { data = Arrays.copyOf(data, data.length * 2); ints = Arrays.copyOf(ints, ints.length * 2); }
            data[size++] = x; data[size++] = y; data[size++] = z; data[size++] = u; data[size++] = v;
            ints[count++] = color; ints[count++] = light;
        }
        void emit(VertexConsumer out, MatrixStack.Entry entry) {
            for (int i = 0, j = 0; i < size; i += 5, j += 2)
                out.vertex(entry, data[i], data[i + 1], data[i + 2]).color(ints[j]).texture(data[i + 3], data[i + 4])
                        .overlay(OverlayTexture.DEFAULT_UV).light(ints[j + 1]).normal(entry, 0, 1, 0);
        }
    }
}
