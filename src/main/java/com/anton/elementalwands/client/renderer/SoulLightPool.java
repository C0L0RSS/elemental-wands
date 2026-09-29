package com.anton.elementalwands.client.renderer;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * The colour half of soul light: a stepped soul-fire pool on the ground under a glowing spell.
 * Block light has no colour, so the server's soul glow lights the terrain and this tints it blue.
 * Each patch lies on its own block top, so the pool follows steps and graves instead of floating.
 */
public final class SoulLightPool {
    // Translucent, emissive and without depth writes: it tints what is under it, walls hide it.
    private static final RenderLayer LAYER = RenderLayer.getEyes(Identifier.of("elementalwands", "textures/misc/soul_light_pool.png"));
    /** A light this far above the ground casts no pool. */
    public static final double REACH = 7;
    private static final float LIFT = .02f; // Clear of the surface, so it never z-fights.
    private static final int STRIDE = 10; // x0, z0, x1, z1, y, u0, v0, u1, v1, alpha
    private final float[] patches;
    private final int count;

    private SoulLightPool(float[] patches, int count) { this.patches = patches; this.count = count; }

    /**
     * Samples the surfaces under a light at {@code source}, relative to the entity drawn at
     * {@code origin}. Brighter and a little wider close to the ground; nothing for a light buried
     * in a block or beyond {@link #REACH}.
     */
    public static SoulLightPool sample(World world, Vec3d origin, Vec3d source, double radius, float strength) {
        double below = source.y - surface(world, MathHelper.floor(source.x), MathHelper.floor(source.z), source.y);
        if (strength <= 0 || !(below >= 0 && below < REACH)) return null;
        // Soul light is a night effect, as the block light is: sunlight washes most of it out.
        BlockPos at = BlockPos.ofFloored(source);
        float daylight = world.getLightLevel(LightType.SKY, at) / 15f * (1 - world.getAmbientDarkness() / 11f);
        strength *= 1 - .6f * MathHelper.clamp(daylight, 0, 1);
        radius *= 1 - .25 * below / REACH;
        int x0 = MathHelper.floor(source.x - radius), x1 = MathHelper.floor(source.x + radius);
        int z0 = MathHelper.floor(source.z - radius), z1 = MathHelper.floor(source.z + radius);
        float[] patches = new float[(x1 - x0 + 1) * (z1 - z0 + 1) * STRIDE];
        int count = 0;
        double left = source.x - radius, back = source.z - radius, size = radius * 2;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            double nx = MathHelper.clamp(source.x, x, x + 1) - source.x, nz = MathHelper.clamp(source.z, z, z + 1) - source.z;
            if (nx * nx + nz * nz >= radius * radius) continue; // The cell misses the circle.
            double top = surface(world, x, z, source.y), height = source.y - top;
            if (!(height >= 0 && height < REACH)) continue;
            float alpha = strength * (float)Math.pow(MathHelper.clamp(1 - (height - 1) / (REACH - 1), 0, 1), 1.2);
            if (alpha < .02f) continue;
            double ax = Math.max(x, left), bx = Math.min(x + 1, left + size), az = Math.max(z, back), bz = Math.min(z + 1, back + size);
            int o = count++ * STRIDE;
            patches[o] = (float)(ax - origin.x); patches[o + 1] = (float)(az - origin.z);
            patches[o + 2] = (float)(bx - origin.x); patches[o + 3] = (float)(bz - origin.z);
            patches[o + 4] = (float)(top - origin.y) + LIFT;
            patches[o + 5] = (float)((ax - left) / size); patches[o + 6] = (float)((az - back) / size);
            patches[o + 7] = (float)((bx - left) / size); patches[o + 8] = (float)((bz - back) / size);
            patches[o + 9] = alpha;
        }
        return count == 0 ? null : new SoulLightPool(patches, count);
    }

    /**
     * The first collidable top under a light in one column: NaN when a block beside the light
     * rises past it (the light is next to that block, not above it) or the ground is out of reach.
     */
    private static double surface(World world, int x, int z, double lightY) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = MathHelper.floor(lightY), bottom = MathHelper.floor(lightY - REACH); y >= bottom; y--) {
            BlockState state = world.getBlockState(pos.set(x, y, z));
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) continue;
            double top = y + shape.getMax(Direction.Axis.Y);
            return top <= lightY ? top : Double.NaN;
        }
        return Double.NaN;
    }

    /** Widens a render-culling box to the ground a pool may reach, so it is not culled with its spell off screen. */
    public static Box reach(Box box, double radius) { return box.stretch(0, -REACH, 0).expand(radius, 0, radius); }

    public static void submit(SoulLightPool pool, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (pool == null) return;
        float[] p = pool.patches;
        queue.submitCustom(matrices, LAYER, (entry, out) -> {
            for (int i = 0; i < pool.count; i++) {
                int o = i * STRIDE, color = ((int)(MathHelper.clamp(p[o + 9], 0, 1) * 255) << 24) | 0xFFFFFF;
                // Counter-clockwise seen from above, so the face points up.
                vertex(out, entry, p[o], p[o + 4], p[o + 1], p[o + 5], p[o + 6], color);
                vertex(out, entry, p[o], p[o + 4], p[o + 3], p[o + 5], p[o + 8], color);
                vertex(out, entry, p[o + 2], p[o + 4], p[o + 3], p[o + 7], p[o + 8], color);
                vertex(out, entry, p[o + 2], p[o + 4], p[o + 1], p[o + 7], p[o + 6], color);
            }
        });
    }

    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int color) {
        out.vertex(entry, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0, 1, 0);
    }
}
