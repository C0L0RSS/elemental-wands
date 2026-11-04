package com.anton.elementalwands.client.blockspace;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.TAU;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.rnd;
import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;
import static com.anton.elementalwands.client.blockspace.BlockSpaceKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireEffects;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.entity.AstralOrbEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Astral Double (art/space/blockspace/scenes/astral.js). The thrown orb is an amethyst crystal in a tumbling void-glass
 * cube. The double pulls the floor's glass up into itself as it forms, then four nebula plates slowly circle its waist
 * with rising stars; its void-glass shell over the owner's skin is AstralDoubleRenderer's. A destroyed, expired or
 * consumed double (or a lost orb) dissolves in a poof from the POOF message.
 */
final class AstralFx {
    private AstralFx() {}

    static final class Orb implements BlockSpaceEffects.Fx {
        private final AstralOrbEntity orb;
        private final double seed;
        Orb(AstralOrbEntity orb) { this.orb = orb; seed = orb.getId() % 89 * 9.0; }
        @Override public boolean draw(BlockSpaceEffects.Frame f) {
            if (orb.isRemoved()) return false;
            Vec3d p = orb.getLerpedPos(f.delta()).add(0, orb.getHeight() / 2, 0);
            if (!BlockSpaceEffects.near(f, p, 96)) return true;
            astralOrb(f.m(), p, seed);
            stars(f.m(), p, f.now(), 3, .4, .4, seed + 2, .04, true);
            return true;
        }
    }

    static final class Copy implements BlockSpaceEffects.Fx {
        /** How long the formation stream runs after the double appears. */
        static final double FORM = .55;
        private final AstralDoubleEntity copy;
        private final double born, seed;
        Copy(AstralDoubleEntity copy, double now) {
            this.copy = copy; born = now - copy.age / 20.0; seed = copy.getId() % 83 * 7.0;
        }
        @Override public boolean draw(BlockSpaceEffects.Frame f) {
            if (copy.isRemoved()) return false;
            Vec3d feet = copy.getLerpedPos(f.delta());
            if (!BlockSpaceEffects.near(f, feet, 96)) return true;
            BlockSpaceMesh m = f.m();
            double now = f.now(), age = now - born;
            // Formation: the floor's void glass is pulled up into the body as it assembles.
            if (age < FORM) inflow(m, feet.add(0, .9, 0), age, 12, 1.3, .45, seed + 33, .2, true);
            double grow = smoothstep((age - .25) / .3);
            for (int i = 0; i < 4; i++) {
                double a = now * .9 + i * TAU / 4, r = .72 + .06 * Math.sin(now * 2 + i);
                Vec3d c = feet.add(Math.cos(a) * r, .95 + .25 * Math.sin(now * 1.3 + i * 1.7), Math.sin(a) * r);
                double s = .34 * grow * (.8 + .2 * rnd(i));
                if (s < .02) continue;
                plate(m, c, new Vec3d(Math.cos(a), .25, Math.sin(a)), s, s * .75, .05, i % 2 == 1 ? NEBULA : GLASS, seed + 40 + i, now * .6 + i, DEFAULT);
            }
            stars(m, feet.add(0, 1, 0), now, 6, 1, 1.6, seed + 45, .05, false);
            return true;
        }
    }

    static final class Poof implements BlockSpaceEffects.Fx {
        private final Vec3d at;
        private final double t0, seed;
        Poof(Vec3d at, double now) { this.at = at; t0 = now; seed = BlockFireEffects.seed(at, now); }
        @Override public boolean draw(BlockSpaceEffects.Frame f) {
            double age = f.now() - t0;
            if (age > .7) return false;
            if (BlockSpaceEffects.near(f, at, 96)) poof(f.m(), at, age, seed, 1.2);
            return true;
        }
    }
}
