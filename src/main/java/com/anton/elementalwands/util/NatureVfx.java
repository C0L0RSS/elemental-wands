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
