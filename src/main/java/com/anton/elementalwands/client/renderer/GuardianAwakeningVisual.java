package com.anton.elementalwands.client.renderer;

import static com.anton.elementalwands.entity.GuardianIntro.*;

import java.util.Random;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Lightning coming off the Guardian as the heart wakes it: jagged arcs that crawl over the stone
 * and jump to the floor, re-striking every two ticks. They flare with the heart's strike and each
 * thunder crack, gather on the fists as it winds up, and burst outward from the slam. Arcs face
 * the camera, so they read from every shot.
 */
final class GuardianAwakeningVisual {
    private static final int[] CRACKS = {WAKE, WAKE + 7, WAKE + 15, WAKE + 26, WAKE + 34, RISE + 8, RISE + 21};

    private GuardianAwakeningVisual() {}

    /** Still stone: no glowing eyes or veins until the heart seats, then they stutter alight. */
    static boolean dormant(float t) {
        if (t < 0) return false;
        if (t < IMPACT) return true;
        int tick = (int)t;
        return t < IMPACT + 14 && (tick % 5 == 2 || tick % 7 == 4);
    }

    /** How much lightning is on the stone at tick t of the scene. */
    static float power(float t) {
        if (t < IMPACT) return 0;
        double p = t < IMPACT + 8 ? .35 + 1.1 * (1 - (t - IMPACT) / 8) : t < WAKE ? .3 : t < RISE + 20 ? 1 : .25;
        for (int crack : CRACKS) if (t >= crack) p += .7 * Math.exp(-(t - crack) / 2);
        if (t >= WIND) p = .35 + .6 * smooth((t - WIND) / (CLAP - WIND));
        if (t >= CLAP) p = 1.7 * Math.exp(-(t - CLAP) / 6) + .2 * (1 - smooth((t - CLAP) / (RELEASE + 12 - CLAP)));
        return (float)p;
    }

    static void submit(FracturedGuardianRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        float t = state.introTime, power = power(t);
        if (power < .02f) return;
        Vec3d eye = camera.pos.subtract(state.x, state.y, state.z);
        double yaw = Math.toRadians(state.bodyYaw), cos = Math.cos(yaw), sin = Math.sin(yaw);
        double rise = smooth((t - RISE) / (STAND - RISE));
        boolean fists = t >= WIND && t < CLAP, burst = t >= CLAP;
        long strike = (long)Math.floor(t / 2);
        queue.submitCustom(matrices, RenderLayer.getLightning(), (entry, out) -> {
            int strands = Math.round(3 + 9 * Math.min(power, 1.4f) + (burst ? 5 * Math.min(power, 1.2f) : 0));
            for (int i = 0; i < strands; i++) {
                Random random = new Random(strike * 7919 + i * 104729L);
                if (random.nextFloat() > .45 + .5 * Math.min(power, 1)) continue;
                Vec3d from, to;
                if (fists || burst) {
                    // On the fists: out at the spread hands while it winds up, then from where they meet.
                    Vec3d fist = burst ? FISTS : new Vec3d(random.nextBoolean() ? 3.1 : -3.1, 3.4, .25);
                    Vec3d dir = direction(random).add(0, burst ? 0 : .3, 0);
                    // The slam throws its bolts out to the sides, up and down, never into the lens in front.
                    if (burst) dir = new Vec3d(dir.x * 1.6, dir.y, Math.min(dir.z, .15));
                    dir = dir.normalize();
                    from = fist.add(dir.multiply(.3));
                    double reach = burst ? 2.2 + 3.2 * random.nextDouble() * Math.min(power, 1.2) : .8 + 1.4 * random.nextDouble();
                    to = from.add(dir.multiply(reach));
                    if (burst && random.nextFloat() < .4) to = new Vec3d(to.x * 1.3, .05, Math.min(to.z, FISTS.z));
                } else {
                    // Crawling out over the body, a third of them striking the floor.
                    Vec3d centre = new Vec3d(0, 1.7 + .9 * rise, .3), radii = new Vec3d(1.6, 1.5 + .7 * rise, 1.1);
                    Vec3d dir = direction(random);
                    from = centre.add(dir.x * radii.x * .9, dir.y * radii.y * .9, dir.z * radii.z * .9);
                    double reach = 1 + 2.6 * random.nextDouble() * Math.min(power, 1.3);
                    to = random.nextFloat() < .33 ? new Vec3d(from.x + dir.x * reach, .05, from.z + dir.z * reach)
                            : from.add(dir.add(0, .25, 0).normalize().multiply(reach));
                }
                arc(out, entry, turn(from, cos, sin), turn(to, cos, sin), eye, random, power);
            }
        });
    }

    /** A jagged bolt with a bright core in a wider glow, and now and then a fork. */
    private static void arc(VertexConsumer out, MatrixStack.Entry entry, Vec3d from, Vec3d to, Vec3d eye, Random random, float power) {
        int steps = 9;
        double length = from.distanceTo(to);
        Vec3d[] points = new Vec3d[steps + 1];
        Vec3d along = to.subtract(from).normalize(), across = along.crossProduct(new Vec3d(0, 1, 0));
        if (across.lengthSquared() < 1e-4) across = new Vec3d(1, 0, 0);
        across = across.normalize();
        Vec3d lift = across.crossProduct(along).normalize();
        for (int k = 0; k <= steps; k++) {
            double s = k / (double)steps, taper = Math.sin(Math.PI * s), jag = .5 * length / steps;
            points[k] = from.lerp(to, s).add(across.multiply(random.nextGaussian() * jag * taper)).add(lift.multiply(random.nextGaussian() * jag * taper));
        }
        float glow = .032f + .018f * Math.min(power, 1.5f), core = .009f + .005f * Math.min(power, 1.5f);
        int glowAlpha = (int)(50 + 40 * Math.min(power, 1.5f)), coreAlpha = (int)(190 + 50 * Math.min(power, 1f));
        for (int k = 0; k < steps; k++) {
            ribbon(out, entry, points[k], points[k + 1], eye, glow, 40, 190, 255, glowAlpha);
            ribbon(out, entry, points[k], points[k + 1], eye, core, 225, 255, 255, coreAlpha);
        }
        if (random.nextFloat() < .35) {
            Vec3d fork = points[3], end = fork.add(along.multiply(length * .3)).add(across.multiply(random.nextGaussian() * length * .25));
            ribbon(out, entry, fork, fork.lerp(end, .5).add(lift.multiply(.1)), eye, core, 225, 255, 255, coreAlpha / 2);
            ribbon(out, entry, fork.lerp(end, .5).add(lift.multiply(.1)), end, eye, core * .7f, 225, 255, 255, coreAlpha / 3);
        }
    }

    /** One segment as a flat strip turned to face the camera, wound toward it (back faces are culled). */
    private static void ribbon(VertexConsumer out, MatrixStack.Entry entry, Vec3d a, Vec3d b, Vec3d eye, float width, int r, int g, int bl, int alpha) {
        Vec3d side = b.subtract(a).crossProduct(eye.subtract(a));
        if (side.lengthSquared() < 1e-8) return;
        side = side.normalize().multiply(width);
        out.vertex(entry, (float)(a.x + side.x), (float)(a.y + side.y), (float)(a.z + side.z)).color(r, g, bl, alpha);
        out.vertex(entry, (float)(b.x + side.x), (float)(b.y + side.y), (float)(b.z + side.z)).color(r, g, bl, alpha);
        out.vertex(entry, (float)(b.x - side.x), (float)(b.y - side.y), (float)(b.z - side.z)).color(r, g, bl, alpha);
        out.vertex(entry, (float)(a.x - side.x), (float)(a.y - side.y), (float)(a.z - side.z)).color(r, g, bl, alpha);
    }

    private static Vec3d direction(Random random) {
        Vec3d d = new Vec3d(random.nextGaussian(), random.nextGaussian() * .7, random.nextGaussian());
        return d.lengthSquared() < 1e-6 ? new Vec3d(0, 1, 0) : d.normalize();
    }

    /** From the Guardian's own frame (+Z ahead) into world axes around its feet. */
    private static Vec3d turn(Vec3d local, double cos, double sin) {
        return new Vec3d(local.x * cos - local.z * sin, local.y, local.x * sin + local.z * cos);
    }
}
