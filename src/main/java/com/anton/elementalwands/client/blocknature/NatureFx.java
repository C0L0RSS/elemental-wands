package com.anton.elementalwands.client.blocknature;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blocknature.BlockNatureKit.*;

import com.anton.elementalwands.client.blocknature.BlockNatureEffects.Frame;
import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.util.FullBloomManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/** The Nature effects, one per scene of the browser study (art/nature/blocknature/scenes). */
final class NatureFx {
    private NatureFx() {}

    /** A burst of pieces at a fixed point: planting, shots, impacts, removals. */
    record Pop(Vec3d at, double start, double seed, Burst look) implements BlockNatureEffects.Fx {
        @Override public boolean draw(Frame f) {
            double age = f.now() - start;
            if (age >= look.duration()) return false;
            if (BlockNatureEffects.near(f, at, BlockNatureEffects.REACH)) burst(f.m(), at, age, look, seed);
            return true;
        }
    }

    /** The shared heal cue around a player who just gained health; it follows them while it plays. */
    static final class Heal implements BlockNatureEffects.Fx {
        private final Entity body;
        private final double start;
        private Vec3d feet;
        Heal(Entity body, Vec3d feet, double start) { this.body = body; this.feet = feet; this.start = start; }
        @Override public boolean draw(Frame f) {
            double age = f.now() - start;
            if (age >= HEAL_TIME) return false;
            if (body != null && !body.isRemoved()) feet = body.getLerpedPos(f.delta());
            if (BlockNatureEffects.near(f, feet, BlockNatureEffects.REACH)) heal(f.m(), feet, age);
            return true;
        }
    }

    /**
     * Full Bloom (scenes/full-bloom.js): a petal burst at the caster, a low open wreath of 48 petals with leaves and
     * pollen that rolls out to the bloom's radius in twelve ticks, then three petals falling off the caster every
     * .4 seconds for as long as the bloom lasts. Each drift burst starts where the caster stood when it fell.
     */
    static final class Bloom implements BlockNatureEffects.Fx {
        private static final double WAVE = FullBloomManager.WAVE_TICKS / 20.0, LENGTH = FullBloomManager.DURATION / 20.0, DRIFT = .4;
        private static final Burst CAST = Burst.petals(14, .4, 1.3), FALL = new Burst(3, .23, .48, .75, PETAL, PINK, true);
        private final Vec3d origin;
        private final double radius, start;
        private final Entity caster;
        private final List<Vec3d> drops = new ArrayList<>();
        private boolean stopped;
        Bloom(Vec3d origin, double radius, Entity caster, double start) {
            this.origin = origin; this.radius = radius; this.caster = caster; this.start = start;
            stopped = caster == null;
        }
        @Override public boolean draw(Frame f) {
            double age = f.now() - start;
            var m = f.m();
            if (BlockNatureEffects.near(f, origin, BlockNatureEffects.REACH + radius)) {
                burst(m, origin.add(0, 1, 0), age, CAST, 0);
                if (age >= 0 && age < WAVE * 2) wave(m, age);
            }
            if (!stopped && (caster.isRemoved() || caster instanceof LivingEntity body && !body.isAlive())) stopped = true;
            while (!stopped && (drops.size() + 1) * DRIFT <= age && (drops.size() + 1) * DRIFT < LENGTH)
                drops.add(caster.getLerpedPos(f.delta()).add(0, 1.08, 0));
            for (int n = 0; n < drops.size(); n++) {
                Vec3d p = drops.get(n);
                if (BlockNatureEffects.near(f, p, BlockNatureEffects.REACH)) burst(m, p, age - (n + 1) * DRIFT, FALL, n + 1);
            }
            double last = stopped ? drops.size() * DRIFT : LENGTH;
            return age < Math.max(last + FALL.duration(), WAVE * 2);
        }
        private void wave(BlockNatureMesh m, double age) {
            double r = radius * clamp(age / WAVE), keep = 1 - clamp((age - WAVE) / WAVE);
            for (int i = 0; i < 48; i++) {
                double a = i * TAU / 48, x = origin.x + Math.cos(a) * r, z = origin.z + Math.sin(a) * r;
                piece(m, new Vec3d(x, origin.y + .35 + rnd(i) * .9, z), (.35 + rnd(i + 7) * .13) * keep, PETAL, i % 3 != 0 ? PINK : VIOLET, i, age);
                if (i % 3 == 0) piece(m, new Vec3d(x, origin.y + .2, z), .38 * keep, LEAF_PIECE, LEAF, i, age);
                if (m.embers && i % 6 == 0) piece(m, new Vec3d(x, origin.y + .8, z), .22 * keep, POLLEN, GOLD, i, age);
            }
        }
    }

    /**
     * A flower's seed in flight leaves two small gold kernels per tick, each shrinking away over .3 seconds where it
     * was dropped. The caster's own Seed leaves no trail: the developer asked for it to fly clean (the browser
     * study's scenes/seed.js still shows the fuller trail it once had).
     */
    static final class SeedTrail implements BlockNatureEffects.Fx {
        private static final double GAP = .025, LIFE = .3;
        private static final int SLOTS = 16;
        private final SeedProjectileEntity seed;
        private final double[] times = new double[SLOTS];
        private final Vec3d[] points = new Vec3d[SLOTS];
        private int samples;
        private double lastTime = Double.NaN, next, end = Double.NaN;
        private Vec3d lastPos;
        SeedTrail(SeedProjectileEntity seed) { this.seed = seed; }
        @Override public boolean draw(Frame f) {
            double now = f.now();
            if (Double.isNaN(end) && seed.isRemoved()) end = now;
            if (Double.isNaN(end)) {
                Vec3d p = seed.getLerpedPos(f.delta());
                if (Double.isNaN(lastTime)) { lastTime = now; lastPos = p; next = now; }
                for (; next <= now; next += GAP) {
                    double span = now - lastTime, k = span > 1e-6 ? clamp((next - lastTime) / span) : 1;
                    times[samples % SLOTS] = next; points[samples % SLOTS] = lastPos.lerp(p, k);
                    samples++;
                }
                lastTime = now; lastPos = p;
            } else if (now > end + LIFE) return false;
            var m = f.m();
            for (int n = Math.max(0, samples - SLOTS); n < samples; n++) {
                double a = now - times[n % SLOTS];
                if (a < 0 || a >= LIFE) continue;
                Vec3d p = points[n % SLOTS];
                if (!BlockNatureEffects.near(f, p, BlockNatureEffects.REACH)) continue;
                double k = 1 - a / LIFE;
                piece(m, p.add(0, -.1 * a, 0), .17 * k, POLLEN, GOLD, n, a);
            }
            return true;
        }
    }

    /** A Springbloom rider sheds a pair of leaves every four ticks until they land (scenes/springbloom.js). */
    static final class Flight implements BlockNatureEffects.Fx {
        private static final double GAP = .2, LIMIT = 8;
        private static final Burst PAIR = new Burst(2, .24, .2, .4, LEAF_PIECE, LEAF, true);
        private final Entity rider;
        private final double start;
        private final List<Vec3d> drops = new ArrayList<>();
        private double ended = Double.NaN;
        Flight(Entity rider, double start) { this.rider = rider; this.start = start; if (rider == null) ended = 0; }
        @Override public boolean draw(Frame f) {
            double age = f.now() - start;
            if (Double.isNaN(ended) && (rider.isRemoved() || age > LIMIT || age > .15 && rider.isOnGround()
                    || rider.isTouchingWater() || rider.isInLava())) ended = age;
            while (Double.isNaN(ended) && drops.size() * GAP <= age) drops.add(rider.getLerpedPos(f.delta()).add(0, .15, 0));
            for (int n = 0; n < drops.size(); n++) {
                Vec3d p = drops.get(n);
                if (BlockNatureEffects.near(f, p, BlockNatureEffects.REACH)) burst(f.m(), p, age - n * GAP, PAIR, n * 3.7);
            }
            return Double.isNaN(ended) || age < ended + PAIR.duration();
        }
    }
}
