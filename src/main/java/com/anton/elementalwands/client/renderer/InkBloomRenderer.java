package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.firstwizard.InkBloomEntity;
import com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Sentry Bloom: the player's flower at boss size, stained with ink. Its head is the player's own
 * Sentry Bloom head mesh ({@link NatureMeshes}), about 3.4 times its size, turned toward whom it is about to shoot; its
 * stem, leaves and roots are the player's flower's square beams in the same grain and greens. Ink soaks up it from
 * the floor, with a violet meniscus at the line, and climbs it as it wilts.
 *
 * <p>Its store is read from across the arena: five sockets up the stem, each lit gold (full-bright) by a hit and
 * climbing from the roots to its place as it fills. While it delivers they leave from the top; broken, the spell's
 * drawer drops them dark.</p>
 */
public final class InkBloomRenderer extends EntityRenderer<InkBloomEntity, InkBloomRenderer.State> {
    private static final Identifier VEINS = Identifier.of("elementalwands", "textures/misc/first_wizard_bloom_grain.png");
    private static final RenderLayer GRAIN = RenderLayer.getEntityCutoutNoCull(VEINS);
    private static final RenderLayer PETALS = RenderLayer.getEntityTranslucent(VEINS);
    private static final int BEADS = 5;
    private static final int STEM = 0x2f9e1f, STEM_LIGHT = 0x41b82a, LEAF = 0x78e330, LEAF_DARK = 0x30b821;
    private static final int INK = 0x120d18, RIM = 0x7a45e0, GOLD = 0xffcc26, GOLD_CORE = 0xfff2a4, SOCKET = 0x1d1528;
    private static final int[][][] FACES = {
            {{1,-1,1},{1,-1,-1},{1,1,-1},{1,1,1}}, {{-1,-1,-1},{-1,-1,1},{-1,1,1},{-1,1,-1}},
            {{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}}, {{1,-1,-1},{-1,-1,-1},{-1,1,-1},{1,1,-1}},
            {{-1,1,1},{1,1,1},{1,1,-1},{-1,1,-1}}, {{-1,-1,-1},{1,-1,-1},{1,-1,1},{-1,-1,1}}};
    private static final int[][] NORMALS = {{0,1},{0,-1},{2,1},{2,-1},{1,1},{1,-1}};
    private static final float[] US = {0, 1, 1, 0}, VS = {1, 1, 0, 0};

    public static final class State extends EntityRenderState {
        float yaw, tilt, recoil, hurt, sinceStored, bodyYaw;
        double grown, stageAge, store, time;
        int stage;
    }

    public InkBloomRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }

    @Override public void updateRenderState(InkBloomEntity entity, State state, float delta) {
        super.updateRenderState(entity, state, delta);
        state.yaw = entity.headYaw(delta); state.tilt = entity.headTilt(delta); state.recoil = entity.recoil(delta);
        state.hurt = entity.hurtTime > 0 ? (entity.hurtTime - delta) / Math.max(1, entity.maxHurtTime) : 0;
        state.sinceStored = entity.sinceStored(delta);
        state.bodyYaw = entity.getYaw() * (float) (Math.PI / 180);
        state.grown = entity.grown(delta); state.stageAge = entity.stageAge(delta); state.store = entity.storeShare();
        state.stage = entity.bloomStage();
        state.time = (entity.getEntityWorld().getTime() + delta) / 20.0;
    }

    @Override public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (s.invisible) return;
        // Never lit below block light 9: it must read at midnight.
        int light = LightmapTextureManager.pack(Math.max(9, LightmapTextureManager.getBlockLightCoordinates(s.light)), LightmapTextureManager.getSkyLightCoordinates(s.light));
        double g = s.grown, grow = 1 - Math.pow(1 - g, 2.2);
        double wilt = s.stage == InkBloomEntity.WILTING ? Math.clamp(s.stageAge / SentryBloomSpell.WILT, 0, 1) : 0;
        double height = SentryBloomSpell.HEAD_PIVOT * (.12 + .88 * grow) * (1 - .25 * wilt);
        double stain = height * (.2 + .8 * wilt) + .08 * Math.sin(s.time * 1.7);
        Vec3d pivot = stem(height, height, s.time, wilt);
        queue.submitCustom(matrices, GRAIN, (entry, out) -> body(out, entry, s, light, height, stain, grow, wilt));
        // The head: the player's mesh at boss size, turned to its target, kicked back by a shot.
        double size = SentryBloomSpell.HEAD_SCALE * (.25 + .75 * grow), kick = s.recoil;
        String mesh = s.stage == InkBloomEntity.WILTING ? "sentry_head_wilt" : kick > .3f ? "sentry_head_fired" : "sentry_head";
        double headStain = .3 + .8 * wilt;
        matrices.push();
        matrices.translate(pivot.x, pivot.y, pivot.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(s.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(s.tilt));
        matrices.translate(0, -.06f * kick * size, 0);
        matrices.scale((float) (size * (1 + .08f * kick)), (float) (size * (1 - .12f * kick)), (float) (size * (1 + .08f * kick)));
        float hurt = s.hurt;
        queue.submitCustom(matrices, PETALS, (entry, out) -> NatureMeshes.draw(mesh, new Stain(out, headStain, hurt), entry, light, 1, -1, null, false));
        matrices.pop();
        super.render(s, matrices, queue, camera);
    }

    /** The stem's centre at a height: a slow, slight S-curve that sways, and droops as it wilts. */
    private static Vec3d stem(double y, double height, double time, double wilt) {
        double k = height <= 0 ? 0 : y / height;
        double sway = .06 * Math.sin(time * .9) * k;
        return new Vec3d(.22 * Math.sin(k * 2.6) + sway + .5 * wilt * k * k, y, .14 * Math.sin(k * 3.1 + 1) + sway * .6);
    }

    private void body(VertexConsumer out, MatrixStack.Entry e, State s, int light, double height, double stain, double grow, double wilt) {
        double hurt = s.hurt, t = s.time;
        // Roots in a bulb of ink, and four broad leaves round them, stained from the root.
        box(out, e, new Vec3d(0, .12, 0), AXES, .55, .14, .55, tint(INK, hurt), light);
        for (int i = 0; i < 4; i++) {
            double a = s.bodyYaw + i * Math.PI / 2 + .4;
            leaf(out, e, new Vec3d(0, .2, 0), a, .45 + .1 * Math.sin(t * .8 + i), 2.3 * grow, .95 * grow, .45, hurt, light, wilt);
        }
        // The stem in square beams, green above the ink line.
        int n = 12;
        for (int i = 0; i < n; i++) {
            double y0 = height * i / n, y1 = height * (i + 1) / n;
            Vec3d a = stem(y0, height, t, wilt), b = stem(y1, height, t, wilt);
            Vec3d mid = a.add(b).multiply(.5), along = b.subtract(a);
            double w = .44 - .1 * i / n;
            int colour = mid.y < stain - .12 ? INK : mid.y < stain + .1 ? RIM : i % 2 == 0 ? STEM : STEM_LIGHT;
            box(out, e, mid, axesAlong(along), w * .5, along.length() * .5 + .02, w * .5, tint(colour, hurt), light);
        }
        // Two leaves up the stem.
        for (int i = 0; i < 2; i++) {
            double y = height * (.37 + .28 * i);
            leaf(out, e, stem(y, height, t, wilt), s.bodyYaw + i * Math.PI + 1.2, .6 - .2 * wilt, 1.5 * grow, .7 * grow, y < stain ? 1 : .15, hurt, light, wilt);
        }
        // The store: five sockets up the stem, lit gold as it fills; the newest light climbs from the roots.
        int lit = (int) Math.round(s.store * BEADS);
        if (s.stage == InkBloomEntity.DELIVERING) lit = (int) Math.ceil(lit * (1 - Math.clamp(s.stageAge / SentryBloomSpell.DELIVER, 0, 1)));
        if (s.stage == InkBloomEntity.WILTING) lit = 0;
        for (int i = 0; i < BEADS; i++) {
            double y = height * (.3 + .14 * i);
            Vec3d at = stem(y, height, t, wilt);
            Vec3d[] axes = yawed(Math.PI / 4 + s.bodyYaw);
            boolean on = i < lit;
            if (on && i == lit - 1 && s.sinceStored < 12) {
                double k = s.sinceStored / 12.0;
                k = k * k * (3 - 2 * k);
                at = stem(.2 + (y - .2) * k, height, t, wilt);
            }
            if (grow < .9) continue;
            if (on) {
                double pulse = 1 + .08 * Math.sin(t * 6 + i);
                box(out, e, at, axes, .34 * pulse, .3 * pulse, .34 * pulse, GOLD, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                box(out, e, at, axes, .2, .38 * pulse, .2, GOLD_CORE, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            } else {
                // An empty socket: a dark nub in the stem and a thin violet ring round it.
                box(out, e, at, axes, .24, .14, .24, tint(SOCKET, hurt), light);
                box(out, e, at, axes, .28, .035, .28, RIM, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }
        }
    }

    /**
     * One of the player's stepped leaves at boss size: a blade, its tip and a rib, out from {@code root} at yaw,
     * pitched up by lift. The first {@code inked} of its length is stained ink.
     */
    private static void leaf(VertexConsumer out, MatrixStack.Entry e, Vec3d root, double yaw, double lift, double length, double width,
                             double inked, double hurt, int light, double wilt) {
        if (length < .05) return;
        double pitch = lift - 1.1 * wilt;
        Vec3d along = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
        Vec3d side = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d normal = side.crossProduct(along).normalize();
        Vec3d[] axes = {side.multiply(-1), normal, along};
        double stained = Math.min(1, inked + wilt);
        Vec3d dark = root.add(along.multiply(length * stained * .5));
        if (stained > .02) box(out, e, dark, axes, width * .5, .07, length * stained * .5, tint(INK, hurt), light);
        if (stained < .98) {
            double rest = 1 - stained;
            box(out, e, root.add(along.multiply(length * (stained + rest * .5))), axes, width * .5, .065, length * rest * .5, tint(LEAF, hurt), light);
        }
        box(out, e, root.add(along.multiply(length * 1.05)), axes, width * .3, .06, length * .15, tint(stained > .9 ? RIM : LEAF, hurt), light);
        box(out, e, root.add(along.multiply(length * .45)).add(normal.multiply(.06)), axes, .07, .06, length * .4, tint(stained > .5 ? RIM : LEAF_DARK, hurt), light);
    }

    private static final Vec3d[] AXES = {new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1)};

    private static Vec3d[] yawed(double yaw) {
        Vec3d x = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        return new Vec3d[]{x, new Vec3d(0, 1, 0), x.crossProduct(new Vec3d(0, 1, 0))};
    }

    /** A right-handed basis whose second axis runs along a direction. */
    private static Vec3d[] axesAlong(Vec3d dir) {
        Vec3d y = dir.lengthSquared() < 1e-8 ? new Vec3d(0, 1, 0) : dir.normalize();
        Vec3d ref = Math.abs(y.y) > .92 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d x = y.crossProduct(ref).normalize(), z = x.crossProduct(y);
        return new Vec3d[]{x, y, z};
    }

    /** A hurt flash: a moment of red over its colour. */
    private static int tint(int rgb, double hurt) {
        if (hurt <= 0) return rgb;
        double k = .45 * hurt;
        int r = (int) Math.round(((rgb >> 16) & 255) * (1 - k) + 255 * k), g = (int) Math.round(((rgb >> 8) & 255) * (1 - k) + 50 * k), b = (int) Math.round((rgb & 255) * (1 - k) + 50 * k);
        return r << 16 | g << 8 | b;
    }

    /** One oriented box in the entity's frame, with the kits' face shading. */
    private static void box(VertexConsumer out, MatrixStack.Entry e, Vec3d c, Vec3d[] axes, double hx, double hy, double hz, int rgb, int light) {
        double[] half = {hx, hy, hz};
        for (int f = 0; f < 6; f++) {
            Vec3d n = axes[NORMALS[f][0]].multiply(NORMALS[f][1]);
            double tone = .80 + .20 * n.y + .07 * n.z + .03 * n.x;
            int r = (int) Math.round(((rgb >> 16) & 255) * tone), g = (int) Math.round(((rgb >> 8) & 255) * tone), b = (int) Math.round((rgb & 255) * tone);
            int color = 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
            for (int i = 0; i < 4; i++) {
                int[] k = FACES[f][i];
                double x = c.x, y = c.y, z = c.z;
                for (int j = 0; j < 3; j++) {
                    double sgn = k[j] * half[j];
                    x += axes[j].x * sgn; y += axes[j].y * sgn; z += axes[j].z * sgn;
                }
                out.vertex(e, (float) x, (float) y, (float) z).color(color).texture(US[i], VS[i])
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, (float) n.x, (float) n.y, (float) n.z);
            }
        }
    }

    /**
     * Stains the player's head mesh with ink: soaked up from its base to {@code level} of its height (the mesh runs
     * from y -0.11 to 0.64), a violet line at the front, and the rest a little darkened. Vertices are stained by where
     * they sit in the mesh, before the pose moves them.
     */
    private static final class Stain implements VertexConsumer {
        private final VertexConsumer out;
        private final double level, hurt;
        private double y;

        Stain(VertexConsumer out, double level, double hurt) { this.out = out; this.level = level; this.hurt = hurt; }

        @Override public VertexConsumer vertex(MatrixStack.Entry entry, float x, float y, float z) {
            this.y = (y + .11) / .75;
            out.vertex(entry, x, y, z);
            return this;
        }
        @Override public VertexConsumer vertex(float x, float y, float z) { out.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            double soak = Math.clamp((level - y) / .12, 0, 1), front = Math.clamp(1 - Math.abs(level - y) / .05, 0, 1) * (1 - soak);
            double dark = .18 + .74 * soak;
            double rr = r * (1 - dark) + 18 * dark, gg = g * (1 - dark) + 13 * dark, bb = b * (1 - dark) + 24 * dark;
            rr = rr * (1 - front) + 122 * front; gg = gg * (1 - front) + 69 * front; bb = bb * (1 - front) + 224 * front;
            double k = .45 * hurt;
            rr = rr * (1 - k) + 255 * k; gg = gg * (1 - k) + 50 * k; bb = bb * (1 - k) + 50 * k;
            out.color((int) Math.round(rr), (int) Math.round(gg), (int) Math.round(bb), a);
            return this;
        }
        @Override public VertexConsumer texture(float u, float v) { out.texture(u, v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { out.overlay(u, v); return this; }
        @Override public VertexConsumer light(int u, int v) { out.light(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { out.normal(x, y, z); return this; }
        @Override public VertexConsumer normal(MatrixStack.Entry entry, float x, float y, float z) { out.normal(entry, x, y, z); return this; }
    }
}
