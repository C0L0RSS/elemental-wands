package com.anton.elementalwands.client.blockwind;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.X;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rotate;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.sphereDir;
import static com.anton.elementalwands.client.blockwind.BlockWindKit.*;

import com.anton.elementalwands.registry.ModItems;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Zephyr Strike (art/wind/blockwind/scenes/zephyr.js): a pressure ring and bursting floor puffs at launch, streams
 * pouring off the wing roots while rising, compression rings tightening around the body in a dive, and a landing of
 * a quick dome of air and a rolling wall of cloud racing out to the burst's radius. The worn wings are 3D feathered
 * wings drawn here for every player wearing Zephyr wings; the wings item names no equipment model, so the flat
 * Elytra never draws.
 */
final class ZephyrFx {
    private static final int LANDING_TICKS = 8;
    /** Casters whose landing the server has reported; their flight effect ends. */
    private static final Set<Integer> LANDED = new HashSet<>();
    private ZephyrFx() {}

    static void clear() { LANDED.clear(); }
    static void landed(int entity) { LANDED.add(entity); }

    /** Movement over the last tick in blocks, which also works for other players moved by position packets. */
    private static Vec3d perTick(Entity e) { return new Vec3d(e.getX() - e.lastX, e.getY() - e.lastY, e.getZ() - e.lastZ); }

    /** The launch: a big pressure ring along the launch line and puffs bursting off the ground. */
    static final class Launch implements BlockWindEffects.Fx {
        private final Vec3d feet, dir;
        private final double t0, seed;
        Launch(Vec3d feet, Vec3d look, double now) {
            this.feet = feet; t0 = now;
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            dir = (flat.lengthSquared() < 1e-6 ? UP : flat.normalize().add(UP)).normalize();
            seed = com.anton.elementalwands.client.blockfire.BlockFireEffects.seed(feet, now);
        }
        @Override public boolean draw(BlockWindEffects.Frame f) {
            double a = f.now() - t0;
            if (a > 1) return false;
            if (!BlockWindEffects.near(f, feet, 96)) return true;
            BlockWindMesh m = f.m();
            ring(m, feet.add(0, .9, 0).add(dir.multiply(.4)), dir, lerp(.6, 2.2, smoothstep(a / .4)), 20, .16 * (1 - smoothstep(a / .5)), CLOUD, AIR_ALPHA, a, seed + 1);
            for (int i = 0; i < 10; i++) {
                double ang = TAU * i / 10;
                Vec3d d = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                puff(m, feet.add(d.multiply(.4)).add(0, .2, 0), a - rnd(i) * .06, .45, seed + i * 5, .9, d.multiply(2.4).add(0, .3, 0), 3, AIR_ALPHA);
            }
            return true;
        }
    }

    /** Streams while rising and compression rings while diving, from the caster's own movement until they land. */
    static BlockWindEffects.Fx flight(ClientWorld world, int entity, double now) {
        LANDED.remove(entity);
        return f -> {
            double age = f.now() - now;
            Entity e = world.getEntityById(entity);
            if (age > 20 || e == null || e.isRemoved() || LANDED.remove(entity)) return false;
            if (age > .5 && !(e instanceof PlayerEntity p && wearsWings(p))) return false;
            Vec3d p = e.getLerpedPos(f.delta());
            if (!BlockWindEffects.near(f, p, 128)) return true;
            BlockWindMesh m = f.m();
            Vec3d v = perTick(e);
            double speed = v.length(), t = m.time;
            if (v.y > .18 && speed > .18) {
                Vec3d dir = v.normalize();
                Vec3d[] axes = torso(e, f.delta(), -.15);
                Vec3d back = back(p, axes), s = axes[2];
                for (int side = -1; side <= 1; side += 2) for (int lane = 0; lane < 4; lane++) {
                    Vec3d root = back.add(s.multiply(side * (.55 + lane * .14)));
                    double l = .9 + lane * .35 + speed * .25, off = .2 + lane * .3;
                    streak(m, root.subtract(dir.multiply(off)), root.subtract(dir.multiply(off + l)), .14 - lane * .02,
                            lane % 2 == 1 ? STREAK : GALE, lane % 2 == 1 ? .65 : .9, lane * 2 + side);
                }
                if (m.embers) for (int i = 0; i < 3; i++)
                    wisp(m, back.add(s.multiply((i - 1) * .7)), dir.multiply(-1), (age * 3 + i / 3.0) % 1, .08, i + 40, 1, .7);
            } else if (v.y < -.5) {
                Vec3d c = p.add(0, .95, 0);
                for (int k = 0; k < 3; k++) {
                    double cyc = (age * 3.2 + k / 3.0) % 1;
                    ring(m, c.add(0, lerp(-.6, 1.1, cyc), 0), UP, lerp(1.9, .55, cyc), 16, .1 * Math.sin(cyc * Math.PI), k % 2 == 1 ? STREAK : CLOUD,
                            .8, t * 3.6 * (k % 2 == 1 ? -1 : 1), 500 + k);
                }
                streak(m, c.add(0, 1.4, 0), c.add(0, 4.2, 0), .1, GALE, .9, 7, X);
                for (int s = -1; s <= 1; s += 2) streak(m, c.add(0, 1, s * .5), c.add(0, 3.4, s * .7), .08, STREAK, .6, s, X);
            }
            return true;
        };
    }

    /** The landing: a quick dome of air, a rolling wall of cloud out to the visual radius, a crisp inner ring and a sinking column. */
    static final class Landing implements BlockWindEffects.Fx {
        private final Vec3d at;
        private final double radius, t0, seed;
        Landing(Vec3d at, double radius, double now) {
            this.at = at; this.radius = radius; t0 = now;
            seed = com.anton.elementalwands.client.blockfire.BlockFireEffects.seed(at, now);
        }
        @Override public boolean draw(BlockWindEffects.Frame f) {
            double a = f.now() - t0;
            if (a > 1.4) return false;
            if (!BlockWindEffects.near(f, at, 128)) return true;
            BlockWindMesh m = f.m();
            double p = clamp(a / (LANDING_TICKS / 20.0)), R = radius * (.12 + .88 * (1 - (1 - p) * (1 - p))), fade = 1 - smoothstep((a - .35) / .6);
            double dome = clamp(a / .2);
            if (dome < 1) for (int i = 0; i < 34; i++) {
                Vec3d d = sphereDir(i, 68);
                if (d.y < 0) continue;
                cube(m, at.add(d.multiply(.4 + 2.4 * smoothstep(dome))), .32 * (1 - dome), i % 3 == 0 ? GALE : CLOUD, seed + i, flowAxes(d), .7);
            }
            final int n = 30;
            for (int i = 0; i < n; i++) {
                double ang = TAU * (i + rnd(seed + i) * .4) / n + a * .17, jitter = .35 * (rnd(seed + i + 5) - .5);
                Vec3d d = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                Vec3d c = at.add(d.multiply(R + jitter)).add(0, .38 + .18 * rnd(seed + i + 1), 0);
                puff(m, c, a - rnd(seed + i + 2) * .05, (.7 + .3 * rnd(seed + i + 3)) * (1 - p * .25), seed + i * 7, 1.05, new Vec3d(0, .35, 0), .35, 4, .68, CLOUD);
                if (i % 3 == 0) streak(m, at.add(d.multiply(R * .5)).add(0, .22, 0), at.add(d.multiply(R * .88)).add(0, .28, 0), .13 * fade,
                        i % 2 == 1 ? STREAK : GALE, .75, seed + i + 200);
            }
            ring(m, at.add(0, .12, 0), UP, R * .62, 26, .11 * fade, GALE, .95, -a * .72, seed + 300);
            double h = .45 + (1 - p) * 3.4;
            for (int i = 0; i < 4; i++)
                ring(m, at.add(0, .3 + h * i / 4, 0), UP, .55 + .25 * i + p * .6, 10, .1 * fade * (1 - p * .5), i % 2 == 1 ? STREAK : CLOUD, .75,
                        a * (4 + i) * (i % 2 == 1 ? -1 : 1), seed + 320 + i * 7);
            motes(m, at.add(0, .6, 0), a, 16, 2, 4, .9, seed + 9, null);
            if (m.embers) for (int i = 0; i < 8; i++) {
                double ang = TAU * i / 8;
                wisp(m, at.add(Math.cos(ang) * R * .5, .4, Math.sin(ang) * R * .5), new Vec3d(Math.cos(ang), .25, Math.sin(ang)), a - .05, .1, seed + 400 + i, .6, .9);
            }
            return true;
        }
    }

    // ---- Wings -----------------------------------------------------------------------------------------

    static boolean wearsWings(PlayerEntity p) { return p.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.ZEPHYR_WINGS); }
    static boolean anyWings(ClientWorld w) {
        for (PlayerEntity p : w.getPlayers()) if (wearsWings(p)) return true;
        return false;
    }

    /** The body's forward, up and right, turned by its lerped body yaw and tipped forward by lean (radians). */
    private static Vec3d[] torso(Entity e, float delta, double lean) {
        float bodyYaw = e instanceof PlayerEntity p ? MathHelper.lerpAngleDegrees(delta, p.lastBodyYaw, p.bodyYaw) : e.getYaw(delta);
        double yaw = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d f = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), r = side(f);
        return new Vec3d[]{rotate(f, r, -lean), rotate(UP, r, -lean), r};
    }
    /** The shoulder-blade point the wings grow from; the body pivots at the feet like the vanilla model. */
    private static Vec3d back(Vec3d feet, Vec3d[] axes) { return feet.add(axes[1].multiply(1.37)).add(axes[0].multiply(-.16)); }

    /** 3D wings on every player wearing Zephyr wings (not the camera's own body in first person). */
    static void drawWings(BlockWindEffects.Frame f) {
        for (PlayerEntity p : f.world().getPlayers()) {
            if (!wearsWings(p) || p == f.cameraEntity() || p.isInvisible()) continue;
            Vec3d feet = p.getLerpedPos(f.delta());
            if (!BlockWindEffects.near(f, feet, 96)) continue;
            double vy = perTick(p).y, spread, flap = 0, lean;
            if (p.isGliding()) { lean = Math.toRadians(clamp(90 + p.getPitch(f.delta()), 0, 135)); spread = 1; }
            else if (vy < -.5) { lean = .5; spread = 1; flap = Math.sin(f.m().time * 14) * .4; }
            else if (vy > .18) { lean = -.15; spread = .25; }
            else { lean = 0; spread = .8; }
            Vec3d[] axes = torso(p, f.delta(), lean);
            wings(f.m(), back(feet, axes), axes, spread, flap, p.getId() % 7 * 5);
        }
    }
}
