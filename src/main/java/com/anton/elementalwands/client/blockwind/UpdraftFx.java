package com.anton.elementalwands.client.blockwind;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.X;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockwind.BlockWindKit.*;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Updraft (art/wind/blockwind/scenes/updraft.js): twenty cloud puffs roll out across the floor from the launch
 * point while a double coil winds up the launch column, short speed lines hang under the rising player's feet, and a
 * small soft puff marks the one safe landing. The floor and the coil stay where the launch happened.
 */
final class UpdraftFx implements BlockWindEffects.Fx {
    private static final int PUFFS = 20;

    private final ClientWorld world;
    private final int entity;
    private final Vec3d floor;
    private final double t0, seed;
    private double lastY, lastT, rise, landed = Double.NaN;
    private Vec3d landAt;
    private boolean high;

    UpdraftFx(ClientWorld world, int entity, Vec3d feet, double now) {
        this.world = world; this.entity = entity; this.floor = feet; t0 = now; lastT = now; lastY = feet.y;
        seed = com.anton.elementalwands.client.blockfire.BlockFireEffects.seed(feet, now);
    }

    @Override public boolean draw(BlockWindEffects.Frame f) {
        double now = f.now(), age = now - t0;
        if (age > 8 || (!Double.isNaN(landed) && now > landed + .6)) return false;
        Entity e = world.getEntityById(entity);
        Vec3d feet = e != null && !e.isRemoved() ? e.getLerpedPos(f.delta()) : null;
        double y = feet == null ? 0 : Math.max(0, feet.y - floor.y), v = 0;
        if (feet != null && now > lastT + 1e-4) { v = (feet.y - lastY) / (now - lastT); lastY = feet.y; lastT = now; rise = v; }
        else v = rise;
        if (y > 1.5) high = true;
        if (high && Double.isNaN(landed) && feet != null && e.isOnGround()) { landed = now; landAt = feet; }
        if (!BlockWindEffects.near(f, floor, 96)) return true;
        BlockWindMesh m = f.m();
        // Floor: twenty puffs rolling outward, low and spreading.
        for (int i = 0; i < PUFFS; i++) {
            double a = TAU * i / PUFFS + rnd(i) * .2, r = .35 + .15 * rnd(i + 3);
            Vec3d d = new Vec3d(Math.cos(a), 0, Math.sin(a));
            puff(m, floor.add(d.multiply(r)).add(0, .22, 0), age - rnd(i + 7) * .05, .42 + .12 * rnd(i + 5), seed + i * 3, .85,
                    d.multiply(1.9 + .4 * rnd(i + 9)).add(0, .25, 0), 3, AIR_ALPHA);
        }
        ring(m, floor.add(0, .08, 0), UP, lerp(.3, 1.7, smoothstep(age / .3)), 18, .08 * (1 - smoothstep(age / .35)), GALE, .9, age, seed);
        // Coil: winds up with the player for the first 0.45 s, then unwinds from the bottom.
        if (age < 1.3) {
            double top = Math.max(.5, Math.min(y + .8, 5)), t = f.m().time;
            spiral(m, floor.add(0, .1, 0), .7, top, 2, 26, .15, t * 6, clamp((age - .45) / .6), seed + 11, .7);
            spiral(m, floor.add(0, .1, 0), .7, top, 2, 26, .11, t * 6 + Math.PI, clamp((age - .5) / .6), seed + 41, .55);
        }
        // Under-feet speed lines while rising fast.
        if (feet != null && v > 4 && Double.isNaN(landed)) {
            double l = Math.min(1.8, v * .08);
            for (int i = 0; i < 3; i++) {
                double a = TAU * i / 3 + f.m().time * 4;
                Vec3d o = feet.add(Math.cos(a) * .2, -.05, Math.sin(a) * .2);
                streak(m, o, o.subtract(0, l, 0), .11, i > 0 ? STREAK : GALE, i > 0 ? .65 : 1, seed + i, X);
            }
        }
        motes(m, floor.add(0, .4, 0), age, 10, .8, 2, .8, seed + 4, UP);
        if (m.embers) for (int i = 0; i < 4; i++) {
            double a = i * 1.57;
            wisp(m, floor.add(Math.cos(a) * .5, .3, Math.sin(a) * .5), new Vec3d(Math.cos(a), .8, Math.sin(a)), age - .05 * i, .08, seed + 30 + i, .6, .6);
        }
        // Safe landing: a soft, small puff ring under the player.
        if (landAt != null) {
            double land = now - landed;
            for (int i = 0; i < 6; i++) {
                double a = TAU * i / 6;
                puff(m, landAt.add(Math.cos(a) * .35, .15, Math.sin(a) * .35), land, .26, seed + 60 + i, .5,
                        new Vec3d(Math.cos(a) * .7, .05, Math.sin(a) * .7), 2, AIR_ALPHA);
            }
        }
        return true;
    }
}
