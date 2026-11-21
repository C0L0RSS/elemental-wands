package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.ThornbiteSpell;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Thornbite: the player's stepped flytrap and braided vines (as {@code ThornbiteVisual} draws
 * them) at boss size, from Nature's atlas. It gathers out of ink over the copy's wand, takes Nature's green, lunges,
 * and snaps; a bite sends a pulse of gold back along the vines into the copy and a spiral of sap and gold round it.
 * Drawing back, the vines and then the mouth cool through purple into ink, which falls off where the player's would
 * fade.
 */
final class ThornbiteFx {
    /** The player's flytrap is authored half a block across; the boss's mouth is this many of its units per block. */
    private static final double[] ROWS = {.14, .23, .27, .23, .14};
    private static final double UNIT_WIDTH = .54;

    private ThornbiteFx() {}

    /** {@link ThornbiteSpell#KIND}. */
    static void draw(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double seed = e.getId() * 9.1, t = f.now();
        Entity owner = f.world().getEntityById(e.ownerId());
        Vec3d raw = e.dir(), d = new Vec3d(raw.x, 0, raw.z);
        d = d.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : d.normalize();
        Vec3d right = new Vec3d(-d.z, 0, d.x);
        if (owner instanceof LivingEntity body && body.getMainArm() == Arm.LEFT) right = right.multiply(-1);
        int stage = e.stage();
        if (stage == ThornbiteSpell.FORMING) {
            if (owner == null) return;
            Vec3d feet = owner.getLerpedPos(delta);
            Vec3d mouth = feet.add(d.multiply(ThornbiteSpell.FORM_AHEAD)).add(0, ThornbiteSpell.FORM_HEIGHT, 0);
            if (!f.near(mouth, 160)) return;
            double formed = e.formed(delta), colour = smoothstep((formed - .15) / .5);
            double radius = ThornbiteSpell.MOUTH_RADIUS * ThornbiteSpell.formingScale(formed);
            Vec3d hand = feet.add(right.multiply(.36)).add(0, 2.2, 0).add(d.multiply(.1));
            Vec3d face = d.add(0, -.3, 0).normalize();
            vines(f, hand, mouth.subtract(face.multiply(radius * .85)), radius / ThornbiteSpell.MOUTH_RADIUS, colour, 0, -1, seed, t);
            jaws(f, mouth, face, radius, smoothstep((formed - .35) / .55) * .85, colour, 0, seed, t);
            // The ink it gathers from: a coat that thins as the colour takes it, dripping to the floor.
            if (colour < .98) InkKit.blob(f.ink(), mouth, radius * .55 * (1 - colour) + .08, InkKit.INK, InkKit.GLOSS, seed, t);
            if (f.embers()) drips(f, mouth, feet.y, radius, seed, t);
            return;
        }
        Vec3d launch = e.auxPoint();
        double floor = launch.y - ThornbiteSpell.FORM_HEIGHT, total = Math.hypot(raw.x, raw.z), drop = raw.y;
        double age = e.stageAge(delta), s, open = 1, cool = 0, shrink = 1, pulse = -1;
        int hold = ThornbiteSpell.HOLD, retract = ThornbiteSpell.RETRACT;
        if (stage == ThornbiteSpell.LUNGING) s = Math.min(total, ThornbiteSpell.SPEED * e.stageAge(delta));
        else {
            double k = clamp((age - hold) / retract);
            s = total * (1 - k);
            open = 1 - smoothstep(age / 2.5);
            cool = smoothstep((age - hold * .4) / (hold + retract * .9));
            shrink = 1 - smoothstep((k - .5) / .5);
            if (stage == ThornbiteSpell.BIT) pulse = age / ThornbiteSpell.PULSE;
        }
        Vec3d feet = owner != null ? owner.getLerpedPos(delta) : new Vec3d(launch.x, floor, launch.z).subtract(d.multiply(ThornbiteSpell.FORM_AHEAD));
        Vec3d root = feet.add(right.multiply(.36)).add(0, 1.35, 0).add(d.multiply(.45));
        double gone = hold + retract;
        if (age < gone || stage == ThornbiteSpell.LUNGING) {
            Vec3d mouth = ThornbiteSpell.mouth(launch, d, s, drop, floor);
            Vec3d ahead = ThornbiteSpell.mouth(launch, d, s + .4, drop, floor);
            Vec3d face = ahead.subtract(mouth).normalize();
            double radius = ThornbiteSpell.MOUTH_RADIUS * shrink;
            if (f.near(mouth, 160) || f.near(root, 160)) {
                vines(f, root, mouth.subtract(face.multiply(radius * .85)), 1, 1, cool, pulse, seed, t);
                jaws(f, mouth, face, radius, open, 1, cool, seed, t);
                if (f.embers()) drips(f, mouth, floor, radius, seed, t);
                // The snap: petals off its lips; a bite throws gold.
                if (stage != ThornbiteSpell.LUNGING && age < 8) snap(f, mouth, age / 20, stage == ThornbiteSpell.BIT, seed);
            }
        }
        // Drawn back into the wand: what is left falls off as ink.
        if (stage != ThornbiteSpell.LUNGING && age >= gone - 2 && age < gone + 16 && f.near(root, 160))
            InkKit.splash(f.ink(), root, floor, (age - gone + 2) / 20, 8, 2.2, .2, seed + 30);
        // The heal: once the pulse reaches the copy, sap and gold spiral up round it and cool into ink.
        if (stage == ThornbiteSpell.BIT && pulse >= 1 && owner != null) heal(f, feet, (age - ThornbiteSpell.PULSE) / 20, seed);
    }

    /**
     * Three vines braided from the root to the mouth's base on a slight arc, with leaves and thorns. colour 0 is all ink
     * (just gathered), 1 all Nature; cool 0 is green, 1 all ink, cooling from the root outward. pulse, from 0 to 1, is
     * a bite's gold running back from the mouth to the root (negative for none).
     */
    private static void vines(InkFrame f, Vec3d root, Vec3d end, double scale, double colour, double cool, double pulse, double seed, double t) {
        Vec3d along = end.subtract(root);
        double length = along.length();
        if (length < .2) return;
        Vec3d axis = along.multiply(1 / length);
        Vec3d side = axis.crossProduct(UP);
        side = side.lengthSquared() < 1e-4 ? X : side.normalize();
        Vec3d up = side.crossProduct(axis).normalize();
        Vec3d bow = UP.multiply(length * .12);
        int n = (int) clamp(Math.round(length / .38), 6, 48);
        double thick = .3 * scale, twist = .2 * scale;
        int[] rows = {NatureInk.LEAF, NatureInk.DARKLEAF, NatureInk.LEAF};
        for (int i = 0; i < n; i++) {
            double u = (i + .5) / n, u2 = (i + 1.5) / n;
            Vec3d c = point(root, along, bow, u), next = point(root, along, bow, u2);
            Vec3d tangent = next.subtract(c);
            if (tangent.lengthSquared() < 1e-8) tangent = axis;
            double local = clamp(cool * 1.6 - u * .6);
            double w = thick * (1 - u * .3);
            for (int j = 0; j < 3; j++) {
                double phase = u * Math.PI * 7 + j * TAU / 3 + t * .8;
                double r = twist * (.45 + .55 * Math.sin(Math.PI * Math.min(1, u * 3)));
                Vec3d p = c.add(side.multiply(Math.cos(phase) * r)).add(up.multiply(Math.sin(phase) * r));
                Vec3d[] axes = basis(tangent, phase);
                double k = seed + i * 3.7 + j * 1.3;
                piece(f, p, w, length / n * 1.25, w, rows[j], k, axes, colour, local);
                // A bite's gold running home.
                if (pulse >= 0 && pulse < 1 && Math.abs(u - (1 - pulse)) < .07)
                    NatureInk.cube(f, p, w * 1.7, NatureInk.GOLD, k, axes);
                else if (pulse >= 0 && pulse < 1 && u > 1 - pulse && u < 1 - pulse + .2 && j == 0)
                    NatureInk.cube(f, p, w * 1.25, NatureInk.SAP, k, axes);
            }
            // Leaves off the first vine, thorns off the others.
            if (i % 5 == 2) {
                Vec3d out = side.multiply(i % 10 == 2 ? 1 : -1).add(up.multiply(.4)).normalize();
                Vec3d[] axes = basis(out, 0);
                axes = new Vec3d[]{axes[0], axes[2].multiply(-1), axes[1]};
                Vec3d leafAt = c.add(out.multiply(.5 * scale));
                if (colour > rnd(seed + i) || local > .1) leafPiece(f, leafAt, .85 * scale, seed + i, axes, local);
            } else if (i % 3 == 0) {
                Vec3d out = up.multiply(i % 2 == 0 ? 1 : -1).add(side.multiply(.5 * (rnd(seed + i * 2.1) - .5))).normalize();
                piece(f, c.add(out.multiply(thick * .9)), .09 * scale + .04, .32 * scale, .09 * scale + .04, NatureInk.CREAM, seed + i * 5.3, basis(out, 0), colour, local);
            }
        }
    }

    private static Vec3d point(Vec3d root, Vec3d along, Vec3d bow, double u) {
        return root.add(along.multiply(u)).add(bow.multiply(4 * u * (1 - u)));
    }

    private static void leafPiece(InkFrame f, Vec3d p, double size, double seed, Vec3d[] axes, double cool) {
        if (cool <= .2 + .5 * rnd(seed * 1.31 + .4)) NatureInk.leaf(f, p, size, NatureInk.LEAF, seed, axes);
        else {
            // A leaf gone to ink: the same three boxes, cooled.
            int row = InkKit.cool(cool);
            InkKit.cube(f.ink(), p, size * .65, size * .18, size, row, seed, axes, 1);
            InkKit.cube(f.ink(), p.add(axes[2].multiply(size * .58)), size * .38, size * .18, size * .3, row, seed + 1, axes, 1);
        }
    }

    /**
     * The mouth: two stepped jaws hinged behind its centre, green outside, pink and violet inside, gold teeth. open 0
     * is shut, 1 gaping. radius is half its width.
     */
    private static void jaws(InkFrame f, Vec3d centre, Vec3d face, double radius, double open, double colour, double cool, double seed, double t) {
        if (radius < .05) return;
        double k = radius * 2 / UNIT_WIDTH;
        Vec3d F = face.normalize(), U = UP.subtract(F.multiply(F.y));
        U = U.lengthSquared() < 1e-6 ? X : U.normalize();
        Vec3d S = U.crossProduct(F).normalize();
        Vec3d hinge = centre.subtract(F.multiply(.22 * k));
        Vec3d[] base = {S, U, F};
        // The stem joint.
        piece(f, hinge.subtract(F.multiply(.04 * k)), .15 * k, .14 * k, .24 * k, NatureInk.DARKLEAF, seed + 90, base, colour, cool);
        double angle = Math.toRadians(4 + 39 * open);
        for (int jaw : new int[]{-1, 1}) {
            double a = jaw * angle;
            Vec3d Fj = F.multiply(Math.cos(a)).add(U.multiply(Math.sin(a))), Uj = U.multiply(Math.cos(a)).subtract(F.multiply(Math.sin(a)));
            Vec3d h = hinge.add(U.multiply(jaw * .018 * k));
            Vec3d[] axes = {S, Uj, Fj};
            for (int row = 0; row < ROWS.length; row++) {
                double z = .045 + row * .077, half = ROWS[row], rs = seed + jaw * 31 + row * 7;
                piece(f, at(h, S, Uj, Fj, 0, jaw * .034, z, k), half * 2 * k, .065 * k, .088 * k, row % 2 == 0 ? NatureInk.LEAF : NatureInk.DARKLEAF, rs, axes, colour, cool);
                piece(f, at(h, S, Uj, Fj, 0, -jaw * .008, z, k), (half * 2 - .075) * k, .022 * k, .068 * k, row == 0 ? NatureInk.VIOLET : NatureInk.PINK, rs + 1, axes, colour, cool);
                if (row == 0) continue;
                for (int side : new int[]{-1, 1}) {
                    double offset = jaw == 1 ? -.012 : .012;
                    piece(f, at(h, S, Uj, Fj, side * (half - .012), -jaw * .075, z + offset, k), .032 * k, .13 * k, .031 * k, NatureInk.GOLD, rs + side * 3, axes, colour, cool);
                    piece(f, at(h, S, Uj, Fj, side * (half - .024), -jaw * .145, z + offset, k), .02 * k, .04 * k, .021 * k, NatureInk.CREAM, rs + side * 5, axes, colour, cool);
                }
            }
            for (int tooth = -1; tooth <= 1; tooth++)
                piece(f, at(h, S, Uj, Fj, tooth * .085 + jaw * .015, -jaw * .075, .415, k), .028 * k, .13 * k, .03 * k, NatureInk.GOLD, seed + jaw * 13 + tooth, axes, colour, cool);
        }
    }

    private static Vec3d at(Vec3d h, Vec3d S, Vec3d U, Vec3d F, double x, double y, double z, double k) {
        return h.add(S.multiply(x * k)).add(U.multiply(y * k)).add(F.multiply(z * k));
    }

    /**
     * One cube of the spell: ink while the colour has not reached it (it is still gathering), Nature's row once it has,
     * and cooling back into ink by {@code cool}.
     */
    private static void piece(InkFrame f, Vec3d p, double sx, double sy, double sz, int row, double seed, Vec3d[] axes, double colour, double cool) {
        if (colour < .999 && colour <= rnd(seed * 2.17 + .3)) {
            InkKit.cube(f.ink(), p, sx, sy, sz, rnd(seed + .9) < .2 ? InkKit.RIM : rnd(seed + 1.7) < .3 ? InkKit.GLOSS : InkKit.INK, seed, axes, 1);
            return;
        }
        NatureInk.cooling(f, p, sx, sy, sz, row, seed, axes, cool);
    }

    /** Ink dropping off the underside of the mouth to the floor. */
    private static void drips(InkFrame f, Vec3d mouth, double floor, double radius, double seed, double t) {
        for (int i = 0; i < 3; i++) {
            double life = .9 + rnd(seed + i * 1.7) * .5, u = (t + rnd(seed + i) * life) % life;
            double a = rnd(seed + i * 3.3 + Math.floor((t + rnd(seed + i) * life) / life)) * TAU;
            Vec3d from = mouth.add(Math.cos(a) * radius * .5, -radius * .45, Math.sin(a) * radius * .5);
            InkKit.drip(f.ink(), from, floor, u, life, .14, seed + i * 5);
        }
    }

    /** Petals torn off its lips as it snaps; a bite also throws gold kernels. age in seconds. */
    private static void snap(InkFrame f, Vec3d c, double age, boolean bit, double seed) {
        double keep = 1 - smoothstep((age - .15) / .25);
        for (int i = 0; i < 10; i++) {
            double a = i * 2.39996 + seed, r = 1.3 * (.3 + Math.sin(Math.min(1, age / .4) * Math.PI / 2)) * (.6 + .4 * rnd(seed + i));
            Vec3d p = c.add(Math.cos(a) * r, Math.sin(age * 6) * .3 + (rnd(seed + i * 2) - .5) * .8, Math.sin(a) * r);
            Vec3d[] axes = tumble(seed + i, age, 6);
            if (bit && i % 3 == 0) NatureInk.cube(f, p, .26 * keep, NatureInk.GOLD, seed + i, axes);
            else NatureInk.petal(f, p, .5 * keep, i % 2 == 0 ? NatureInk.PINK : NatureInk.VIOLET, seed + i, axes);
        }
    }

    /**
     * The heal reaching the copy: sap leaves and gold kernels spiral up round it, as the player's shared heal does,
     * at boss size, cooling into ink as they rise. age in seconds.
     */
    static void heal(InkFrame f, Vec3d feet, double age, double seed) {
        double life = 1.1;
        if (age < 0 || age >= life || !f.near(feet, 160)) return;
        double keep = (1 - smoothstep((age - .6) / .5)) * smoothstep(age / .1), cool = smoothstep((age - .35) / .7);
        for (int i = 0; i < 8; i++) {
            double a = i * TAU / 8 + age * 2.2 + seed, r = .85 + age * .25;
            Vec3d p = feet.add(Math.cos(a) * r, .3 + age * 2.1 + (i % 2) * .3, Math.sin(a) * r);
            Vec3d[] axes = tumble(seed + i, age, 3);
            if (cool < .4 + .3 * rnd(seed + i)) NatureInk.leaf(f, p, .55 * keep, NatureInk.SAP, seed + i, axes);
            else InkKit.cube(f.ink(), p, .4 * keep, .1 * keep, .5 * keep, InkKit.cool(cool), seed + i, axes, 1);
        }
        for (int i = 0; i < 6; i++) {
            double a = (i + .5) * TAU / 6 - age * 2.8, r = .6;
            Vec3d p = feet.add(Math.cos(a) * r, .7 + age * 2.6, Math.sin(a) * r);
            NatureInk.cube(f, p, .3 * keep * (1 - cool * .5), NatureInk.GOLD, seed + 20 + i, tumble(seed + i + 20, age, 4));
        }
    }

}
