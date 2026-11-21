package com.anton.elementalwands.client.blockink;

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
 * The First Wizard's marks on the floor as flat decals, one frame's worth: continuous textured quads laid just above the
 * floor along a mark's path, in the floor-ink textures (art/first_wizard/floor_ink/, 16 texels to a block). A line or an
 * arc is the strip texture repeated along it with a rounded cap at each open end; a ring is the strip closed on itself;
 * a circle is the pool. Each has a glow layer (its violet rim and veins) drawn full-bright over it, as strong as the
 * mark's flare. Four frames of shimmer, stacked in each sheet, run from the clock. Positions are camera-relative.
 */
public final class FloorDecal {
    private static final String ROOT = "textures/misc/first_wizard_floor_";
    private static final Identifier STRIP = id("strip"), STRIP_GLOW = id("strip_glow"), CAP = id("cap"), CAP_GLOW = id("cap_glow"),
            POOL = id("pool"), POOL_GLOW = id("pool_glow");
    private static final Identifier[] TEXTURES = {STRIP, CAP, POOL, STRIP_GLOW, CAP_GLOW, POOL_GLOW};
    /** Over the floor: the mark, and its glow a little above it, so neither fights the floor or each other. */
    private static final double LIFT = .018, GLOW_LIFT = .024;
    /** Blocks the strip's sheet covers along its length at its own width (64 by 32 texels: 4 blocks by 2). */
    private static final double STRIP_LENGTH = 4, STRIP_WIDTH = 2;

    private static Identifier id(String name) { return Identifier.of("elementalwands", ROOT + name + ".png"); }

    private final Vec3d camera;
    private final BlockRenderView world;
    private final int frame;
    private final Buffer[] buffers = new Buffer[TEXTURES.length];

    public FloorDecal(Vec3d camera, double time, BlockRenderView world) {
        this.camera = camera; this.world = world;
        frame = Math.floorMod((int) Math.floor(time * 6), 4);
        for (int i = 0; i < buffers.length; i++) buffers[i] = new Buffer();
    }

    // ── Shapes ───────────────────────────────────────────────────────────────

    /**
     * A straight mark from a to b on the floor at a.y, half wide each side: the strip along it, a cap at each end
     * inside its length, glowing by flare (0 dormant, 1 flared). alpha fades the whole mark.
     */
    public void line(Vec3d a, Vec3d b, double half, double flare, double alpha) {
        Vec3d along = b.subtract(a).multiply(1, 0, 1);
        double length = along.length();
        if (length < .05) return;
        Vec3d dir = along.multiply(1 / length);
        double cap = Math.min(half * 2, length / 2);
        Vec3d s0 = a.add(dir.multiply(cap)), s1 = b.subtract(dir.multiply(cap));
        double uPerBlock = STRIP_WIDTH / (2 * half) / STRIP_LENGTH;
        path(new Vec3d[]{s0, s1}, half, 0, flare, alpha, uPerBlock, false);
        cap(a, dir, cap, half, flare, alpha, false);
        cap(b, dir.multiply(-1), cap, half, flare, alpha, false);
    }

    /**
     * An arc round centre at this radius from angle a0 to a1 (radians, atan2(z, x)), half wide; capped at its ends
     * unless it closes into a ring.
     */
    public void arc(Vec3d centre, double radius, double a0, double a1, double half, double flare, double alpha) {
        arc(centre, radius, a0, a1, half, flare, alpha, false);
    }

    /** The same; glowOnly draws only its glow layer (a wider rim of light laid over a flaring mark). */
    public void arc(Vec3d centre, double radius, double a0, double a1, double half, double flare, double alpha, boolean glowOnly) {
        double span = a1 - a0;
        boolean ring = Math.abs(span) >= Math.PI * 2 - 1e-3;
        double capAngle = ring ? 0 : Math.min(Math.abs(span) / 2, half * 2 / radius) * Math.signum(span);
        double from = a0 + capAngle, to = a1 - capAngle;
        int n = Math.max(2, (int) Math.ceil(Math.abs(to - from) * radius / .4));
        Vec3d[] points = new Vec3d[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = from + (to - from) * i / n;
            points[i] = centre.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
        }
        // A ring's texture must meet itself: as many whole repeats round it as fit.
        double uPerBlock = STRIP_WIDTH / (2 * half) / STRIP_LENGTH;
        if (ring) {
            double around = Math.PI * 2 * radius * uPerBlock;
            uPerBlock *= Math.max(1, Math.round(around)) / around;
        }
        path(points, half, 0, flare, alpha, uPerBlock, glowOnly);
        if (!ring && !glowOnly) {
            Vec3d start = centre.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), end = centre.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            Vec3d tStart = new Vec3d(-Math.sin(a0), 0, Math.cos(a0)).multiply(Math.signum(span)), tEnd = new Vec3d(-Math.sin(a1), 0, Math.cos(a1)).multiply(-Math.signum(span));
            cap(start, tStart, Math.abs(capAngle) * radius, half, flare, alpha, true);
            cap(end, tEnd, Math.abs(capAngle) * radius, half, flare, alpha, true);
        }
    }

    /** A pool of ink, radius blocks across from its middle, turned by its own angle. */
    public void pool(Vec3d centre, double radius, double turn, double flare, double alpha) {
        // The pool's splat fills its sheet edge to edge, about 8 blocks across at 16 texels to a block.
        Vec3d x = new Vec3d(Math.cos(turn), 0, Math.sin(turn)).multiply(radius), z = new Vec3d(-Math.sin(turn), 0, Math.cos(turn)).multiply(radius);
        Vec3d[] corners = {centre.subtract(x).subtract(z), centre.add(x).subtract(z), centre.add(x).add(z), centre.subtract(x).add(z)};
        double[] u = {0, 1, 1, 0}, v = {0, 0, 1, 1};
        quad(2, corners, u, v, alpha, false);
        quad(5, corners, u, v, alpha * glow(flare), true);
    }

    /** How strongly the glow shows at a flare: always a little, clearly when flared. */
    private static double glow(double flare) { return .3 + .7 * Math.clamp(flare, 0, 1); }

    /** The strip laid along a polyline, its texture running on along it. */
    private void path(Vec3d[] p, double half, double u0, double flare, double alpha, double uPerBlock, boolean glowOnly) {
        double u = u0;
        for (int i = 0; i + 1 < p.length; i++) {
            Vec3d a = p[i], b = p[i + 1];
            Vec3d sa = side(p, i), sb = side(p, i + 1);
            double length = b.subtract(a).horizontalLength(), u1 = u + length * uPerBlock;
            // Split where the texture repeats, so every face reads its u within the sheet (no reliance on wrapping).
            double from = 0;
            while (from < 1 - 1e-9) {
                double uFrom = u + (u1 - u) * from, next = Math.floor(uFrom + 1e-9) + 1;
                double to = Math.min(1, u1 - u < 1e-9 ? 1 : (next - u) / (u1 - u));
                double uTo = u + (u1 - u) * to, base = Math.floor(uFrom + 1e-9);
                Vec3d pa = a.lerp(b, from), pb = a.lerp(b, to), qa = sa.lerp(sb, from).normalize(), qb = sa.lerp(sb, to).normalize();
                Vec3d[] corners = {pa.add(qa.multiply(half)), pb.add(qb.multiply(half)), pb.subtract(qb.multiply(half)), pa.subtract(qa.multiply(half))};
                double[] us = {uFrom - base, uTo - base, uTo - base, uFrom - base}, vs = {0, 0, 1, 1};
                if (!glowOnly) quad(0, corners, us, vs, alpha, false);
                quad(3, corners, us, vs, alpha * glow(flare), true);
                from = to;
            }
            u = u1;
        }
    }

    /** The sideways unit vector at point i of a polyline, mitred between its neighbours. */
    private static Vec3d side(Vec3d[] p, int i) {
        Vec3d d = p[Math.min(p.length - 1, i + 1)].subtract(p[Math.max(0, i - 1)]).multiply(1, 0, 1);
        if (d.lengthSquared() < 1e-9) d = new Vec3d(1, 0, 0);
        d = d.normalize();
        return new Vec3d(-d.z, 0, d.x);
    }

    /**
     * A rounded end at point `at`, its open side facing `inward` (toward the rest of the mark), length long: the cap's
     * right edge joins the strip's left edge.
     */
    private void cap(Vec3d at, Vec3d inward, double length, double half, double flare, double alpha, boolean onArc) {
        if (length < .02) return;
        Vec3d d = inward.multiply(1, 0, 1).normalize(), s = new Vec3d(-d.z, 0, d.x);
        Vec3d inner = at.add(d.multiply(length));
        Vec3d[] corners = {at.add(s.multiply(half)), inner.add(s.multiply(half)), inner.subtract(s.multiply(half)), at.subtract(s.multiply(half))};
        double[] u = {0, 1, 1, 0}, v = {0, 0, 1, 1};
        quad(1, corners, u, v, alpha, false);
        quad(4, corners, u, v, alpha * glow(flare), true);
    }

    private void quad(int texture, Vec3d[] corners, double[] u, double[] v, double alpha, boolean glow) {
        if (alpha <= .01) return;
        Buffer out = buffers[texture];
        double lift = glow ? GLOW_LIFT : LIFT;
        Vec3d mid = corners[0].add(corners[2]).multiply(.5);
        int light = glow ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light(mid);
        int a = (int) Math.round(Math.clamp(alpha, 0, 1) * 255);
        int color = a << 24 | 0xFFFFFF;
        float v0 = frame / 4f;
        for (int i = 0; i < 4; i++)
            out.add((float) (corners[i].x - camera.x), (float) (corners[i].y + lift - camera.y), (float) (corners[i].z - camera.z),
                    (float) u[i], v0 + (float) Math.clamp(v[i], 0, 1) / 4f, color, light);
    }

    private int light(Vec3d at) {
        if (world == null) return LightmapTextureManager.MAX_LIGHT_COORDINATE;
        return WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at.add(0, .3, 0)));
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    public boolean isEmpty() {
        for (Buffer b : buffers) if (b.size > 0) return false;
        return true;
    }

    /** Draws every layer, marks before their glow; entry carries only the camera's rotation. */
    public void emit(net.minecraft.client.render.VertexConsumerProvider consumers, MatrixStack.Entry entry) {
        for (int i = 0; i < TEXTURES.length; i++) {
            Buffer b = buffers[i];
            if (b.size == 0) continue;
            RenderLayer layer = RenderLayer.getEntityTranslucent(TEXTURES[i]);
            VertexConsumer out = consumers.getBuffer(layer);
            for (int k = 0, j = 0; k < b.size; k += 5, j += 2)
                out.vertex(entry, b.data[k], b.data[k + 1], b.data[k + 2]).color(b.ints[j]).texture(b.data[k + 3], b.data[k + 4])
                        .overlay(OverlayTexture.DEFAULT_UV).light(b.ints[j + 1]).normal(entry, 0, 1, 0);
            if (consumers instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
        }
    }

    private static final class Buffer {
        float[] data = new float[5 * 64];
        int[] ints = new int[2 * 64];
        int size, count;
        void add(float x, float y, float z, float u, float v, int color, int light) {
            if (size + 5 > data.length) { data = Arrays.copyOf(data, data.length * 2); ints = Arrays.copyOf(ints, ints.length * 2); }
            data[size++] = x; data[size++] = y; data[size++] = z; data[size++] = u; data[size++] = v;
            ints[count++] = color; ints[count++] = light;
        }
    }
}
