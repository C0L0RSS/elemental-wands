package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockspace.BlockSpaceKit;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * The copy's Hollow Purple, the finisher: the players' spell, bigger, in the boss's manner (it starts in its element's
 * colours and cools to ink). While it forms over three seconds a blue and a red star circle out of the human hand,
 * closing in as they rise, and merge into a purple orb with a heart of ink; rolling, the orb is a shell of purple plates
 * round that heart, two broken orbits of blue and red, and a wake that cools through purple to ink. Solid cubes only:
 * not Space's void glass. Its lane is ink on the floor with violet edges that flare at the aim lock.
 */
final class HollowPurpleFx {
    private HollowPurpleFx() {}

    private static void star(InkFrame f, Vec3d c, double s, int row, double seed, double t) {
        f.space().box(c, s * .5, s * .5, s * .5, tumble(seed, t, 2.5), row, (int) (t * 6 + seed) & 3, 1, 1);
    }

    /** {@link HollowPurpleSpell#KIND}: stage 0 forming over the copy, 1 rolling, 2 collapsing at the end of its lane. */
    static void orb(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double t = f.now(), seed = e.getId() * 7.1, R = e.size();
        if (e.stage() == 0) {
            Entity owner = f.world().getEntityById(e.ownerId());
            if (!(owner instanceof FirstWizardEntity copy)) return;
            Vec3d hand = WizardBody.hand(copy, delta);
            Vec3d c = copy.getLerpedPos(delta).add(0, HollowPurpleSpell.FORM_HEIGHT + WizardBody.lift(copy, delta), 0)
                    .add(WizardBody.forward(WizardBody.yaw(copy, delta)).multiply(.6));
            if (!f.near(c, 160)) return;
            double k = e.formed(delta);
            Vec3d right = WizardBody.left(WizardBody.yaw(copy, delta)).multiply(-1);
            // Two stars rise out of the hand and circle in toward the orb's heart; they merge in the last fifth.
            double merge = smoothstep((k - .78) / .22);
            for (int s = -1; s <= 1; s += 2) {
                double a = t * 3.2 + (s > 0 ? 0 : Math.PI), spread = lerp(2.6, .2, k) * (1 - merge);
                Vec3d rise = hand.lerp(c, smoothstep(k / .3));
                Vec3d at = rise.add(right.multiply(Math.cos(a) * spread)).add(0, Math.sin(a) * spread * .4, 0);
                star(f, at, .28 + .22 * k, s > 0 ? BlockSpaceKit.BLUE : BlockSpaceKit.RED, seed + s, t);
                for (int i = 1; i <= 4; i++) {
                    double b = a - i * .22;
                    Vec3d trail = rise.add(right.multiply(Math.cos(b) * spread)).add(0, Math.sin(b) * spread * .4, 0);
                    InkKit.cube(f.ink(), trail, (.4 - i * .07) * (.6 + k * .4), i < 2 ? InkKit.VIOLET : i < 3 ? InkKit.PURPLE : InkKit.DEEP, seed + s * 10 + i, tumble(seed + i, t, 3));
                }
            }
            double r = R / 3 * smoothstep((k - .15) / .85);
            if (r > .05) draw(f, c, r, null, seed, t, merge);
            // Ink streams up from the hand into it.
            InkKit.strand(f.ink(), hand, c, t, 8, .2 + .15 * k, seed + 30);
            return;
        }
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 180)) return;
        Vec3d vel = e.dir(), origin = e.auxPoint();
        Vec3d dir = vel.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : vel.normalize();
        double travelled = Math.max(0, c.subtract(origin).horizontalLength());
        double r = HollowPurpleSpell.radius(travelled);
        if (e.stage() == 2) {
            double age = e.stageAge(delta) / HollowPurpleSpell.COLLAPSE;
            if (age > 1.3) return;
            draw(f, c, r * (1 - smoothstep(age)), dir, seed, t, 1);
            InkKit.splash(f.ink(), c, c.y - HollowPurpleSpell.HEIGHT, age * .6, 26, 6, .35, seed + 40);
            return;
        }
        draw(f, c, r, dir, seed, t, 1);
        // The wake: plates left where it passed, falling to the floor and cooling to ink.
        for (int i = 1; i <= 14; i++) {
            double back = i * .9;
            if (back > travelled) break;
            double k = i / 14.0;
            Vec3d p = c.subtract(dir.multiply(back)).add((rnd(seed + i) - .5) * r * 1.2, -k * HollowPurpleSpell.HEIGHT * .8 + (rnd(seed + i * 2) - .5) * r * .6, (rnd(seed + i * 3) - .5) * r * 1.2);
            InkKit.cube(f.ink(), p, (.7 - k * .45) * r / 4, InkKit.cool(k), seed + i, tumble(seed + i, t, 1.2));
        }
    }

    /** The orb: a heart of ink with a violet rim, a shell of purple plates, and a blue and a red orbit. */
    private static void draw(InkFrame f, Vec3d c, double r, Vec3d dir, double seed, double t, double merged) {
        InkKit.blob(f.ink(), c, r * .32, InkKit.INK, InkKit.GLOSS, seed, t);
        InkKit.cube(f.ink(), c, r * .5, InkKit.DEEP, seed + 2, tumble(seed + 2, t, .8));
        int plates = (int) Math.clamp(Math.round(r * 9), 10, 64);
        for (int i = 0; i < plates; i++) {
            Vec3d d = rotate(sphereDir(i, plates), UP, t * .7 + seed);
            double fall = fract(t * .4 + rnd(seed + i));
            Vec3d p = c.add(d.multiply(r * (1 - .35 * fall)));
            int row = i % 5 == 0 ? InkKit.MAGENTA : i % 3 == 0 ? InkKit.VIOLET : i % 2 == 0 ? InkKit.PURPLE : InkKit.DEEP;
            InkKit.cube(f.ink(), p, r * .16 * (1 - fall * .5), r * .05 + .04, r * .16 * (1 - fall * .5), row, seed + i, basis(d, rnd(seed + i) * TAU), 1);
        }
        if (merged < .5) return;
        Vec3d axis = dir == null ? UP : UP.add(dir.multiply(.5)).normalize();
        for (int s = -1; s <= 1; s += 2) {
            Vec3d tilt = rotate(axis, dir == null ? X : dir, s * .5);
            Vec3d u = tilt.crossProduct(X).lengthSquared() < 1e-4 ? Z : tilt.crossProduct(X).normalize(), v = tilt.crossProduct(u).normalize();
            int n = 16;
            for (int i = 0; i < n; i++) {
                if (i % 4 == 3) continue;       // broken orbits
                double a = i * TAU / n + s * t * 2;
                Vec3d p = c.add(u.multiply(Math.cos(a) * r * .72)).add(v.multiply(Math.sin(a) * r * .72));
                star(f, p, r * .09 + .08, s > 0 ? BlockSpaceKit.BLUE : BlockSpaceKit.RED, seed + s * 20 + i, t);
            }
        }
    }

    /** {@link HollowPurpleSpell#LANE_KIND}: the lane across the arena, ink with violet edges that flare at the lock. */
    static void lane(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d a = e.getLerpedPos(delta), b = e.auxPoint();
        if (!f.near(a, 160)) return;
        Entity owner = f.world().getEntityById(e.ownerId());
        if (e.stage() == 0 && owner != null) a = owner.getLerpedPos(delta);
        Vec3d dir = e.dir().multiply(1, 0, 1);
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
        if (e.stage() == 0) b = a.add(dir.multiply(b.subtract(a).horizontalLength()));
        double half = e.size(), t = f.now(), formed = e.formed(delta), seed = e.getId();
        double length = b.subtract(a).horizontalLength() * smoothstep(formed / .3);
        boolean flare = e.stage() >= 1;
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        Vec3d[] axes = yawAxes(Math.atan2(dir.x, dir.z));
        // Past the orb, the lane drains away behind it.
        double drained = e.stage() >= 2 ? e.stageAge(delta) * HollowPurpleSpell.SPEED - 2 : -1;
        for (double s = 1.5; s < length; s += .7) {
            if (s < drained) continue;
            Vec3d mid = a.add(dir.multiply(s)).add(0, .02, 0);
            int i = (int) (s / .7);
            for (int e2 = -1; e2 <= 1; e2 += 2) {
                int row = flare ? (i % 2 == 0 ? InkKit.VIOLET : InkKit.MAGENTA) : i % 3 == 2 ? InkKit.INK : InkKit.RIM;
                InkKit.cube(f.ink(), mid.add(side.multiply(e2 * half)), .26, flare ? .12 : .07, .6, row, seed + i * 2 + e2, axes, 1);
            }
            // Dashes of ink across it, running along the lane toward the far end.
            if ((i + (int) (t * 8)) % 4 == 0) InkKit.cube(f.ink(), mid, half * 1.4, .04, .3, InkKit.DEEP, seed + i, axes, 1);
        }
    }
}
