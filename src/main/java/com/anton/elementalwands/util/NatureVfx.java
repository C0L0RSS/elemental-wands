package com.anton.elementalwands.util;

import com.anton.elementalwands.registry.ModParticles;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Geometry helpers for the Nature wand's Fairy Bloom visual language.
 *
 * <p>The spell managers still own timing and gameplay state. This class only turns those
 * state changes into deliberate particle silhouettes: paired stems, flower rings, root spokes,
 * and seed-energy ribbons. Keeping that geometry in one place makes the effects consistent and
 * prevents high-frequency manager ticks from accidentally spawning unbounded particle clouds.
 */
public final class NatureVfx {

    private static final double TAU = Math.PI * 2.0;

    private NatureVfx() {
    }

    public static void line(ServerWorld world, ParticleEffect effect, Vec3d from, Vec3d to,
            double spacing, int maxPoints) {
        double distance = from.distanceTo(to);
        int points = Math.max(2, Math.min(maxPoints, (int) Math.ceil(distance / spacing) + 1));
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            Vec3d p = from.lerp(to, t);
            world.spawnParticles(effect, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void ring(ServerWorld world, ParticleEffect effect, Vec3d center,
            double radius, int points, double yOffset, double phase) {
        for (int i = 0; i < points; i++) {
            double angle = phase + TAU * i / points;
            world.spawnParticles(effect,
                    center.x + Math.cos(angle) * radius,
                    center.y + yOffset,
                    center.z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A paired ground-hugging vine, with flowers appearing along the wake. */
    public static void pairedTendril(ServerWorld world, Vec3d from, Vec3d to, int age) {
        Vec3d direction = to.subtract(from);
        Vec3d horizontal = new Vec3d(direction.x, 0.0, direction.z);
        Vec3d side = horizontal.lengthSquared() < 0.0001
                ? new Vec3d(0.18, 0.0, 0.0)
                : new Vec3d(-horizontal.z, 0.0, horizontal.x).normalize().multiply(0.18);
        double sway = Math.sin(age * 0.85) * 0.08;
        Vec3d swayOffset = side.normalize().multiply(sway);

        // Vine sprites live long enough to bridge successive heads. Emitting one
        // clustered knot per strand preserves the paired silhouette without a
        // packet for every point along this tick's short segment.
        Vec3d first = to.add(side).subtract(swayOffset).add(0.0, 0.08, 0.0);
        Vec3d second = to.subtract(side).add(swayOffset).add(0.0, 0.1, 0.0);
        world.spawnParticles(ModParticles.NATURE_VINE,
                first.x, first.y, first.z, 2, 0.035, 0.025, 0.035, 0.0);
        world.spawnParticles(ModParticles.NATURE_VINE,
                second.x, second.y, second.z, 2, 0.035, 0.025, 0.035, 0.0);

        world.spawnParticles(age % 3 == 0 ? ModParticles.NATURE_BLOOM : ModParticles.NATURE_LEAF,
                to.x, to.y + 0.16, to.z, 1, 0.06, 0.03, 0.06, 0.0);
    }

    public static void seedlingPulse(ServerWorld world, BlockPos anchor, int stage, boolean amplified,
            int now) {
        Vec3d center = Vec3d.ofCenter(anchor).add(0.0, 0.42, 0.0);
        int clampedStage = Math.max(1, Math.min(6, stage));
        double radius = 0.18 + clampedStage * 0.055;
        int petals = 3 + clampedStage;
        ring(world, ModParticles.NATURE_LEAF, center, radius, petals, 0.03,
                now * 0.12);
        world.spawnParticles(ModParticles.NATURE_BLOOM,
                center.x, center.y + 0.18 + clampedStage * 0.035, center.z,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                center.x, center.y + 0.3, center.z,
                amplified ? 7 : 3, radius, 0.16, radius, amplified ? 0.02 : 0.008);
        if (clampedStage >= 3) {
            ring(world, ModParticles.NATURE_BLOOM, center,
                    0.38 + clampedStage * 0.035, clampedStage >= 6 ? 6 : 4,
                    -0.12, now * -0.08);
        }
    }

    public static void growthRing(ServerWorld world, BlockPos center, int radius, int now) {
        Vec3d c = Vec3d.ofCenter(center);
        int points = Math.max(12, radius * 8);
        for (int i = 0; i < points; i++) {
            double angle = TAU * i / points;
            double x = c.x + Math.cos(angle) * radius;
            double z = c.z + Math.sin(angle) * radius;
            ParticleEffect effect = i % 4 == 0 ? ModParticles.NATURE_BLOOM : ModParticles.NATURE_VINE;
            world.spawnParticles(effect, x, c.y + 0.08, z, 1, 0.04, 0.02, 0.04, 0.0);
        }
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                c.x, c.y + 0.35, c.z, 6 + radius * 2,
                radius * 0.42, 0.18, radius * 0.42, 0.015);
    }

    public static void fairyRipple(ServerWorld world, Vec3d center, double radius, int now) {
        ring(world, ModParticles.NATURE_LEAF, center, radius, 8, 0.04, now * 0.09);
        ring(world, ModParticles.NATURE_POLLEN, center, radius * 0.72, 6,
                0.16, now * -0.07);
        world.spawnParticles(ModParticles.NATURE_BLOOM,
                center.x, center.y + 0.08, center.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Ten luminous root spokes establish the ultimate's gameplay radius. */
    public static void rootSpokes(ServerWorld world, BlockPos center, double radius) {
        Vec3d c = Vec3d.ofCenter(center).add(0.0, 0.08, 0.0);
        for (int spoke = 0; spoke < 10; spoke++) {
            double angle = TAU * spoke / 10.0;
            Vec3d tip = c.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            line(world, ModParticles.NATURE_VINE, c, tip, 1.05, 11);
            world.spawnParticles(ModParticles.NATURE_BLOOM,
                    tip.x, tip.y + 0.08, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        ring(world, ModParticles.NATURE_BLOOM, c, radius, 20, 0.06, Math.PI / 20.0);
    }

    /** Moves one visible knot of seed energy toward the future heart each growth tick. */
    public static void convergence(ServerWorld world, BlockPos source, Vec3d heart,
            double previousProgress, double progress, int streamIndex) {
        Vec3d start = Vec3d.ofCenter(source).add(0.0, 0.45, 0.0);
        double easedPrevious = 1.0 - Math.pow(1.0 - previousProgress, 2.0);
        double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
        Vec3d from = start.lerp(heart, easedPrevious);
        Vec3d to = start.lerp(heart, eased);
        double arc = Math.sin(progress * Math.PI) * (0.65 + (streamIndex % 3) * 0.18);
        Vec3d arcOffset = new Vec3d(0.0, arc, 0.0);
        Vec3d knot = from.lerp(to, 0.62).add(arcOffset);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                knot.x, knot.y, knot.z, 3, 0.08, 0.05, 0.08, 0.008);
        int phase = (int) Math.round(progress * 12.0) + streamIndex;
        if ((phase & 1) == 0) {
            Vec3d destination = to.add(arcOffset);
            world.spawnParticles(ModParticles.NATURE_HEART,
                    destination.x, destination.y, destination.z,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public static void treeHeart(ServerWorld world, Vec3d heart, int now, float healthRatio) {
        boolean severe = healthRatio <= 0.34f;
        if (!severe || ((now / 4) & 3) != 1) {
            world.spawnParticles(ModParticles.NATURE_HEART,
                    heart.x, heart.y, heart.z, 1,
                    severe ? 0.08 : 0.025, severe ? 0.12 : 0.04,
                    severe ? 0.08 : 0.025, 0.0);
        }
        int pollen = healthRatio > 0.66f ? 5 : (healthRatio > 0.34f ? 3 : 1);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                heart.x, heart.y, heart.z, pollen, 0.45, 0.65, 0.45, 0.01);
    }

    public static void floweringCanopy(ServerWorld world, Vec3d center, int now,
            float healthRatio) {
        double phase = now * 0.09;
        int petals = healthRatio > 0.66f ? 5 : (healthRatio > 0.34f ? 8 : 12);
        for (int i = 0; i < petals; i++) {
            double angle = phase + TAU * i / petals;
            double radius = 2.2 + (i % 3) * 0.65;
            double y = center.y + 6.0 + (i % 4) * 0.55;
            world.spawnParticles(i % 3 == 0 ? ModParticles.NATURE_BLOOM : ModParticles.NATURE_PETAL,
                    center.x + Math.cos(angle) * radius, y,
                    center.z + Math.sin(angle) * radius,
                    1, 0.08, 0.12, 0.08, healthRatio < 0.67f ? 0.025 : 0.008);
        }
    }
}
