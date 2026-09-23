package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.wand.WandMesh;
import com.anton.elementalwands.entity.ThornLashEntity;
import com.anton.elementalwands.mixin.ThornbiteProjectionAccessor;
import com.anton.elementalwands.util.ThornLashRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Authored stepped flytrap and three braided stems. All geometry is decorative. */
public final class ThornbiteVisual {
    // Match the approved seed/flower/root colors in art/nature/workshop/engine.js.
    // Neutral grain keeps the foliage and petals from being darkened by colored block textures.
    private static final RenderLayer GRAIN = RenderLayer.getEntityCutoutNoCull(NatureMeshes.TEXTURE);
    public record Snapshot(Vec3d contact, Vec3d direction, float time, float biteTime, boolean healed) {
        public float retract() { return Math.clamp((time - biteTime - ThornLashRules.BITE_TICKS) / ThornLashRules.RETRACT_TICKS, 0, 1); }
        public float travel() { return Math.clamp(time / Math.max(.01f, biteTime), 0, 1) * (1 - retract()); }
        public float size() { return .68f * Math.clamp(time / .6f, 0, 1) * Math.clamp((1 - retract()) * 1.8f, 0, 1); }
    }
    public static Snapshot snapshot(ThornLashEntity entity, float delta) {
        float time = entity.elapsed(delta);
        return new Snapshot(entity.tipPosition(entity.biteTime()), Vec3d.fromPolar(entity.getPitch(), entity.getYaw()), time, entity.biteTime(), entity.healed());
    }
    public static Snapshot forHolder(PlayerEntity player, float delta) {
        for (var entity : player.getEntityWorld().getEntitiesByClass(ThornLashEntity.class, player.getBoundingBox().expand(10), e -> e.casterEntityId() == player.getId()))
            if (entity.elapsed(delta) <= entity.biteTime() + ThornLashRules.BITE_TICKS + ThornLashRules.RETRACT_TICKS) return snapshot(entity, delta);
        return null;
    }

    /** Called inside the actual held-wand draw, so the base shares its exact pose, handedness and bob. */
    public static void render(Snapshot s, ItemDisplayContext display, MatrixStack itemMatrices, OrderedRenderCommandQueue queue, int light) {
        if (s == null || s.size() <= 0) return;
        var client = MinecraftClient.getInstance();
        var camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        itemMatrices.push();
        WandMesh.transform(itemMatrices, false);
        Vector3f socket = itemMatrices.peek().getPositionMatrix().transformPosition(0, WandMesh.DATA.headY() + WandMesh.DATA.headScale() * .89f, 0, new Vector3f());
        itemMatrices.pop();
        boolean first = display == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || display == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        // Item matrices are camera-relative world coordinates, but hands use their own FOV.
        Matrix4f worldToHand = new Matrix4f();
        if (first) {
            float delta = client.getRenderTickCounter().getTickProgress(false);
            var projection = (ThornbiteProjectionAccessor)client.gameRenderer;
            float handFov = projection.elementalwands$getFov(camera, delta, false);
            float worldFov = projection.elementalwands$getFov(camera, delta, true);
            float scale = (float)(Math.tan(Math.toRadians(handFov) / 2) / Math.tan(Math.toRadians(worldFov) / 2));
            worldToHand.rotation(camera.getRotation()).scale(scale, scale, 1).rotate(new Quaternionf(camera.getRotation()).conjugate());
            new Matrix4f(worldToHand).invert().transformPosition(socket);
        }
        Vec3d root = new Vec3d(socket).add(cameraPos);
        Vec3d outward = s.direction().crossProduct(new Vec3d(0, 1, 0));
        if (outward.lengthSquared() < .01) outward = new Vec3d(1, 0, 0);
        outward = outward.normalize();
        if (display == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || display == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) outward = outward.negate();
        var pose = pose(s, root, outward, first ? cameraPos : null);
        Vec3d end = pose.base();
        var matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(worldToHand);
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Vec3d axis = end.subtract(root).normalize();
        Vec3d side = axis.crossProduct(new Vec3d(0, 1, 0));
        if (side.lengthSquared() < .01) side = new Vec3d(1, 0, 0);
        side = side.normalize();
        Vec3d up = side.crossProduct(axis).normalize();
        for (int strand = 0; strand < 3; strand++) {
            for (int segment = 0; segment < 28; segment++) {
                double u = segment / 28.0, v = (segment + 1) / 28.0;
                Vec3d a = braid(root, end, side, up, u, strand, pose.size());
                Vec3d b = braid(root, end, side, up, v, strand, pose.size());
                // The hand pass has no world depth buffer. Explicitly clip hidden pieces.
                if (first && client.world != null && client.player != null && client.world.raycast(new RaycastContext(
                        cameraPos, a.lerp(b, .5), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player)).getType() != HitResult.Type.MISS) continue;
                float width = (float)((.043 - u * .012) * pose.size());
                int tint = strand == 0 ? 0x78e330 : strand == 1 ? 0x2e851a : 0x30b821;
                if (s.healed() && Math.abs(u - (1 - s.retract())) < .11) tint = 0xffcc26;
                branch(matrices, queue, a, b, width, tint, light);
                if (strand == 0 && (segment == 6 || segment == 16 || segment == 23)) {
                    Vec3d leaf = a.add(side.multiply(segment % 2 == 0 ? .13 : -.13)).add(0, .07, 0);
                    branch(matrices, queue, a, leaf, .065f * pose.size(), 0x78e330, light);
                }
            }
        }
        // Submit the mouth in the same draw as its stem. Both now use this frame's real wand tip.
        if (!first || visible(cameraPos, pose.tip().subtract(s.direction().multiply(.22 * pose.size())))) {
            matrices.push(); matrices.translate(pose.tip().x, pose.tip().y, pose.tip().z);
            head(s, pose.size(), matrices, queue, light);
            matrices.pop();
        }
    }
    public record Pose(Vec3d base, Vec3d tip, float size) {}
    public static Pose pose(Snapshot s, Vec3d root, Vec3d outward, Vec3d firstPersonCamera) {
        float travel = s.travel(), size = s.size();
        // Start and finish with the flytrap's stem joint at the socket, not its front teeth.
        Vec3d destination = s.contact().subtract(s.direction().multiply(.52 * size));
        Vec3d base = root.lerp(destination, travel)
                .add(outward.multiply(.42 * Math.sin(Math.PI * travel)))
                .add(0, .12 * Math.sin(Math.PI * travel), 0);
        Vec3d tip = base.add(s.direction().multiply(.52 * size));
        if (firstPersonCamera != null) {
            float clearance = SpellViewClearance.opacity(true, tip.distanceTo(firstPersonCamera), .20 * size);
            size *= clearance;
            tip = base.add(s.direction().multiply(.52 * size));
        }
        return new Pose(base, tip, size);
    }
    private static boolean visible(Vec3d camera, Vec3d point) {
        var client = MinecraftClient.getInstance();
        return client.world != null && client.player != null && client.world.raycast(new RaycastContext(
                camera, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player)).getType() == HitResult.Type.MISS;
    }
    private static Vec3d braid(Vec3d root, Vec3d end, Vec3d side, Vec3d up, double u, int strand, float size) {
        double phase = u * Math.PI * 5 + strand * Math.PI * 2 / 3;
        double radius = .043 * Math.sin(Math.PI * u) * size;
        return root.lerp(end, u).add(0, .20 * Math.sin(Math.PI * u) * size, 0)
                .add(side.multiply(Math.cos(phase) * radius)).add(up.multiply(Math.sin(phase) * radius));
    }
    private static void branch(MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d a, Vec3d b, float width, int color, int light) {
        Vec3d direction = b.subtract(a);
        if (direction.lengthSquared() < 1E-8) return;
        matrices.push();
        Vec3d mid = a.lerp(b, .5); matrices.translate(mid.x, mid.y, mid.z);
        matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.normalize().toVector3f()));
        box(matrices, queue, GRAIN, 0, 0, 0, width, width, (float)direction.length() + .018f, color, light);
        matrices.pop();
    }
    private static void head(Snapshot s, float size, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        if (size <= 0) return;
        matrices.push();
        matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), s.direction().toVector3f()));
        matrices.scale(size, size, size);
        float open = 1 - Math.clamp((s.time() - s.biteTime()) / .7f, 0, 1);
        box(matrices, queue, GRAIN, 0, 0, -.46f, .15f, .14f, .24f, 0x2e851a, light);
        for (int jaw : new int[]{-1, 1}) {
            matrices.push(); matrices.translate(0, jaw * .018, -.42);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-jaw * (4 + 39 * open)));
            float[] widths = {.14f, .23f, .27f, .23f, .14f};
            for (int row = 0; row < 5; row++) {
                float z = .045f + row * .077f, half = widths[row];
                box(matrices, queue, GRAIN, 0, jaw * .034f, z, half * 2, .065f, .088f, row % 2 == 0 ? 0x78e330 : 0x30b821, light);
                box(matrices, queue, GRAIN, 0, -jaw * .008f, z, half * 2 - .075f, .022f, .068f, row == 0 ? 0x9945db : 0xf0338f, light);
                box(matrices, queue, GRAIN, 0, -jaw * .022f, z, .025f, .018f, .080f, 0xff73b0, light);
                if (row > 0) for (int side : new int[]{-1, 1}) {
                    float offset = jaw == 1 ? -.012f : .012f;
                    box(matrices, queue, GRAIN, side * (half - .012f), -jaw * .075f, z + offset, .032f, .13f, .031f, 0xffcc26, light);
                    box(matrices, queue, GRAIN, side * (half - .024f), -jaw * .145f, z + offset, .020f, .040f, .021f, 0xfff2a4, light);
                }
            }
            for (int tooth = -1; tooth <= 1; tooth++)
                box(matrices, queue, GRAIN, tooth * .085f + jaw * .015f, -jaw * .075f, .415f, .028f, .13f, .030f, 0xffcc26, light);
            matrices.pop();
        }
        matrices.pop();
    }
    private static void box(MatrixStack matrices, OrderedRenderCommandQueue queue, RenderLayer layer,
                            float x, float y, float z, float width, float height, float depth, int tint, int light) {
        matrices.push(); matrices.translate(x, y, z); matrices.scale(width, height, depth);
        queue.submitCustom(matrices, layer, (entry, out) -> WandMesh.cube(out, entry, .5f, tint, light, OverlayTexture.DEFAULT_UV, 0, 0, .375f, .375f, false));
        matrices.pop();
    }
    private ThornbiteVisual() {}
}
