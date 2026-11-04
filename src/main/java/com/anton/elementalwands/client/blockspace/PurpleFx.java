package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.UP;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.X;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.basis;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.clamp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.lerp;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockspace.BlockSpaceKit.*;

import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Hollow Purple (art/space/blockspace/scenes/purple.js). The charge follows the caster from the CHARGE message: a blue
 * and a red star circle three blocks above them, closing in as they rise, then merge. The orb follows its entity: a
 * white launch flash, then a ten-block sphere of falling nebula plates around a huge eclipse core, a blue and a red
 * broken orbit, a purple double-helix trail and rings sealing the tunnel behind it, and finally the collapse the
 * COLLAPSE message reports.
 */
final class PurpleFx {
    private static final double ASCENT = 3, HOLD = .5, ANCHOR = 3;
    private static final double ORB_SPEED = 1.3 * 20, ORB_RADIUS = 5, COLLAPSE = .6;
    private PurpleFx() {}

    /** The charge: two stars circling closer above the caster, their wakes pulled after them, orbits closing in. */
    static final class Charge implements BlockSpaceEffects.Fx {
        private final ClientWorld world;
        private final int caster;
        private final double t0, seed;
        private boolean stopped;

        Charge(ClientWorld world, int caster, double now) {
            this.world = world; this.caster = caster; t0 = now; seed = caster % 71 * 5.0;
        }
        void stop() { stopped = true; }

        /** Where the stars sit relative to the anchor: they orbit at 2.4 rad/s, closing from 3.35 to 2.15 blocks, then merge. */
        private static Vec3d offset(double age, Vec3d right) {
            double spread = age < ASCENT ? lerp(3.35, 2.15, age / ASCENT) : lerp(2.15, 0, clamp((age - ASCENT) / HOLD));
            double a = age * 2.4;
            return right.multiply(Math.cos(a) * spread).add(UP.multiply(Math.sin(a) * spread * .34));
        }

        @Override public boolean draw(BlockSpaceEffects.Frame f) {
            double now = f.now(), age = now - t0;
            Entity e = world.getEntityById(caster);
            if (stopped || age >= ASCENT + HOLD || e == null || e.isRemoved()) return false;
            Vec3d c = e.getLerpedPos(f.delta()).add(0, ANCHOR, 0);
            if (!BlockSpaceEffects.near(f, c, 128)) return true;
            Vec3d look = e.getRotationVec(f.delta()), flat = new Vec3d(look.x, 0, look.z);
            Vec3d right = flat.lengthSquared() < 1e-6 ? X : flat.normalize().crossProduct(UP).normalize();
            BlockSpaceMesh m = f.m();
            double progress = clamp(age / ASCENT), hold = clamp((age - ASCENT) / HOLD), grow = smoothstep(age / .3);
            for (int s = -1; s <= 1; s += 2) {
                int band = s > 0 ? BLUE : RED, side = s;
                double sd = seed + (s > 0 ? 3 : 7);
                Path at = time -> c.add(offset(time - t0, right).multiply(side));
                star(m, at.at(now), (.38 + .12 * progress) * grow * (1 - hold * .35), band, sd);
                chase(m, at, now, t0, Double.POSITIVE_INFINITY, .035, .4, .3, .2, sd + 1, band);
            }
            ring(m, c, new Vec3d(.3, 1, .2), lerp(1.6, .7, progress) * grow, 10, .07, DEFAULT, 1, RIM, DEFAULT, now * 2.5, seed + 11, .35, false);
            ring(m, c, new Vec3d(-.35, 1, -.25), lerp(2.1, 1, progress) * grow, 12, .06, DEFAULT, 1, GLASS, DEFAULT, -now * 1.8, seed + 13, .4, false);
            if (m.embers) inflow(m, c, now, 10, 2.4, 1.1, seed + 15, .07, 2.4, null, 0, STARLIGHT, DEFAULT, 0, .12, false);
            if (hold >= .7) piece(m, c, .18 + .1 * Math.sin(now * 40), STARLIGHT, seed + 17, basis(UP, Math.PI / 4));
            return true;
        }
    }

    /** The orb, its launch flash, its trail and its collapse, following one HollowPurpleOrbEntity. */
    static final class Orb implements BlockSpaceEffects.Fx {
        private static final double TRAIL_LIFE = 1.1, HELIX_STEP = .6, TWIST = TAU / 14, SEAL_STEP = 4.5;

        private final HollowPurpleOrbEntity orb;
        private final Vec3d origin;
        private Vec3d dir, right, above;
        private final double start, seed;
        private final Track track = new Track(1);
        private final Glide glide = new Glide();
        private double travelled, collapseAt = Double.NaN, endedAt = Double.NaN;
        private Vec3d collapsePos;

        Orb(HollowPurpleOrbEntity orb, double now) {
            this.orb = orb;
            Vec3d v = orb.getVelocity();
            start = now - orb.age / 20.0;
            origin = orb.getEntityPos().subtract(v.multiply(orb.age));
            setDir(v.lengthSquared() < 1e-8 ? new Vec3d(1, 0, 0) : v);
            seed = orb.getId() % 67 * 7.0;
        }
        private void setDir(Vec3d v) {
            dir = v.normalize();
            Vec3d flat = new Vec3d(dir.x, 0, dir.z);
            right = flat.lengthSquared() < 1e-6 ? X : flat.normalize().crossProduct(UP).normalize();
            above = right.crossProduct(dir).normalize();
        }

        @Override public boolean draw(BlockSpaceEffects.Frame f) {
            double now = f.now(), age = now - start;
            if (Double.isNaN(collapseAt)) {
                Vec3d at = BlockSpaceEffects.takeCollapse(orb.getId());
                if (at != null) { collapseAt = now; collapsePos = at; endedAt = now; }
            }
            boolean flying = Double.isNaN(collapseAt) && !orb.isRemoved();
            if (!flying && Double.isNaN(endedAt)) endedAt = now;
            if (!Double.isNaN(endedAt) && now > endedAt + Math.max(TRAIL_LIFE, COLLAPSE + .2)) return false;
            Vec3d c = flying ? glide.at(orb, now) : collapsePos != null ? collapsePos : track.isEmpty() ? origin : track.last();
            if (flying) {
                // The orb flies straight, so once it has moved its path gives the direction even if velocity never synced.
                if (c.squaredDistanceTo(origin) > 1) setDir(c.subtract(origin));
                track.add(now, c);
                travelled = Math.max(travelled, c.subtract(origin).dotProduct(dir));
            }
            BlockSpaceMesh m = f.m();
            launch(m, age);
            if (flying && BlockSpaceEffects.near(f, c, 160)) orb(m, c, now, age);
            trail(m, now);
            if (!Double.isNaN(collapseAt)) collapse(m, collapsePos, now - collapseAt);
            return true;
        }

        /** Launch: the merged stars flash white and a ring blows out as the orb opens around the anchor. */
        private void launch(BlockSpaceMesh m, double age) {
            if (age < 0 || age > .45) return;
            double k = age / .45;
            piece(m, origin, 1.6 * (1 - smoothstep(k * 1.6)), STARLIGHT, seed + 19, basis(UP, Math.PI / 4 + age * 3));
            ring(m, origin, toEye(m, origin), lerp(1, 7, smoothstep(k)), 20, .22 * (1 - k), DEFAULT, 1, RIM, DEFAULT, .4, seed + 21, 0, true);
        }

        /** The orb in flight: a sparse shell of nebula plates falling into a big eclipse core, two broken orbits, a wake. */
        private void orb(BlockSpaceMesh m, Vec3d c, double t, double age) {
            double open = smoothstep(age / .2);
            core(m, c, 2.1 * open, seed + 23, .8);
            shell(m, c, ORB_RADIUS * lerp(.5, 1, open), 48, 1.15, .12, seed + 25, .5, NEBULA, .75);
            inflow(m, c, t, 26, ORB_RADIUS * 1.05, .85, seed + 27, .85, 1.2, null, 0, GLASS, DEFAULT, 0, 2, false);
            ring(m, c, UP.add(dir.multiply(.5)), 3.2 * open, 16, .16, .12, 1, BLUE, DEFAULT, t * 2.2, seed + 29, .3, false);
            ring(m, c, UP.add(dir.multiply(-.6)), 3.8 * open, 18, .16, .12, 1, RED, DEFAULT, -t * 1.9, seed + 31, .3, false);
            chase(m, track, t, start, Double.POSITIVE_INFINITY, .03, .45, 3.4, .7, seed + 33, GLASS);
            stars(m, c, t, 12, ORB_RADIUS * 1.3, .8, seed + 35, .12, true);
        }

        /**
         * The trail: two purple strands (bright violet rim and lavender amethyst) unwind behind the orb as a helix around
         * its path, and rings seal the torn tunnel every few blocks. Every piece stays where the orb passed, then falls
         * toward the path and shrinks away, so the trail tapers back from the orb.
         */
        private void trail(BlockSpaceMesh m, double t) {
            if (t < start + .1) return;
            int from = Math.max(0, (int) Math.floor(((t - TRAIL_LIFE) - start) * ORB_SPEED / HELIX_STEP));
            for (int j = from; j * HELIX_STEP <= travelled; j++) {
                double s = j * HELIX_STEP, k = (t - (start + s / ORB_SPEED)) / TRAIL_LIFE;
                if (k < 0 || k >= 1) continue;
                double size = .62 * smoothstep(k / .06) * (1 - smoothstep((k - .3) / .7));
                if (size < .02) continue;
                double r = ORB_RADIUS * .85 * (1 - easeIn(k) * .8);
                Vec3d axis = origin.add(dir.multiply(s));
                for (int strand = 0; strand < 2; strand++) {
                    double a = s * TWIST + strand * Math.PI;
                    Vec3d out = right.multiply(Math.cos(a)).add(above.multiply(Math.sin(a)));
                    Vec3d along = dir.add(right.multiply(-Math.sin(a)).add(above.multiply(Math.cos(a))).multiply(r * TWIST));
                    piece(m, axis.add(out.multiply(r)), HELIX_STEP * 1.5, size * .55, size, strand == 0 ? RIM : AMETHYST, j * 2 + strand + seed,
                            flowAxes(along, out, 0), DEFAULT);
                }
            }
            for (int j = Math.max(1, (int) Math.floor(((t - .7) - start) * ORB_SPEED / SEAL_STEP)); j * SEAL_STEP <= travelled; j++) {
                double s = j * SEAL_STEP, k = (t - (start + s / ORB_SPEED)) / .7;
                if (k < 0 || k >= 1) continue;
                ring(m, origin.add(dir.multiply(s)), dir, ORB_RADIUS * lerp(1, .08, smoothstep(k)), 16, .2 * (1 - k * .8), .14, 1,
                        j % 2 == 1 ? RIM : NEBULA, DEFAULT, j * .7, seed + 50 + j, 0, true);
            }
        }

        /** Final collapse (12 ticks): the shell rushes into the core, the core pinches to a point, a lens ring blows out. */
        private void collapse(BlockSpaceMesh m, Vec3d c, double age) {
            if (age < 0 || age > COLLAPSE + .2) return;
            core(m, c, 2.1 * (1 - smoothstep(age / .25)), seed + 23, 3);
            inflow(m, c, age, 34, ORB_RADIUS, .25, seed + 37, .9, true);
            pinpoint(m, c, .9, (age - .25) / .15, seed + 39);
            double lens = (age - .35) / .45;
            if (lens > 0 && lens < 1) ring(m, c, toEye(m, c), lerp(.5, 8, smoothstep(lens)), 24, .24 * (1 - lens), DEFAULT, 1, RIM, DEFAULT, .2, seed + 41, 0, true);
            if (m.embers && age > .35) spill(m, c, age - .35, 14, 10, .4, seed + 43, .4, NEBULA, null);
        }
    }
}
