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
 * colours and cools to ink). While the three tendrils spread, two stars orbit
 * above the rising copy and fuse at aim lock. A light seam crosses the stepped violet shell. Broken blue and red rings
 * follow it down the lane, with a wake that cools through purple to ink. Solid cubes only:
 * not Space's void glass.
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
            double age = e.age(delta), k = e.formed(delta);
            Vec3d forward = e.dir().normalize();
            Vec3d c = HollowPurpleSpell.formingCenter(copy.getLerpedPos(delta), e.auxPoint().y, forward, age);
            if (!f.near(c, 160)) return;
            Vec3d left = new Vec3d(forward.z, 0, -forward.x);
            double lock = HollowPurpleSpell.TIMING.aimLock();
            double merge = smoothstep((age - lock + 10) / 10);
            for (int arm = 0; arm < 2; arm++) {
                double side = arm == 0 ? 1 : -1;
                double angle = 2.8 * smoothstep(age / lock);
                double radius = side * (2.55 - .3 * k) * (1 - merge);
                Vec3d at = c.add(left.multiply(radius * Math.cos(angle))).add(forward.multiply(radius * Math.sin(angle)))
                        .add(0, (side * .85 * Math.sin(angle + .4) - .8 * (1 - k)) * (1 - merge), 0);
                star(f, at, (.42 + .36 * k) * (1 - merge), arm == 0 ? BlockSpaceKit.BLUE : BlockSpaceKit.RED, seed + arm, t);
            }
            double r = R / 3 * smoothstep((k - .15) / .85);
            if (r > .05) draw(f, c, r, forward, seed, t, merge);
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

    /** Fit the corner-clipped courses together in one tilted shell. A single light seam marks the fusion. */
    private static final double[] SHELL_SPANS = {.79, .75, .60, .36};

    private static void draw(InkFrame f, Vec3d c, double r, Vec3d dir, double seed, double t, double merged) {
        if (r <= .01) return;
        double body = r * (.37 + .63 * merged);
        Vec3d[] shell = yawAxes(seed + t * .72);
        shell[1] = rotate(UP, shell[0], .54);
        shell[2] = shell[0].crossProduct(shell[1]);
        double step = body * .235;
        for (int tier = -3; tier <= 3; tier++) {
            Vec3d at = c.add(shell[1].multiply(tier * step));
            double span = body * SHELL_SPANS[Math.abs(tier)];
            shellBand(f, at, span, step, InkKit.PURPLE, seed + tier, shell);
        }
        // A wafer just outside the equator catches the light. Its depth leaves the painted caps exposed.
        shellBand(f, c, body * .806, step * .28, InkKit.VIOLET, seed, shell);
        if (merged <= .01) return;
        // Two inclined broken rings stay outside the shell. Short flat strokes preserve the gaps as they turn.
        Vec3d axis = UP.add(dir.multiply(.45)).normalize();
        for (int side = -1; side <= 1; side += 2) {
            Vec3d tilt = rotate(axis, dir, side * .60);
            Vec3d across = tilt.crossProduct(X).normalize(), along = tilt.crossProduct(across);
            for (int i = 0; i < 12; i++) {
                if (i % 4 == 3) continue;
                double a = i * TAU / 12 + side * t * 1.65;
                Vec3d ray = across.multiply(Math.cos(a)).add(along.multiply(Math.sin(a)));
                Vec3d tangent = along.multiply(Math.cos(a)).subtract(across.multiply(Math.sin(a)));
                Vec3d at = c.add(ray.multiply(body * .97));
                f.space().box(at, body * .045 * merged, body * .17 * merged, body * .045 * merged,
                        basis(tangent), side > 0 ? BlockSpaceKit.BLUE : BlockSpaceKit.RED, 0, 1, 1);
            }
        }
    }

    private static void shellBand(InkFrame f, Vec3d center, double span, double height, int row,
                                  double seed, Vec3d[] axes) {
        InkKit.cube(f.ink(), center, 2 * span, height, span, row, seed, axes, 1);
        for (int side = -1; side <= 1; side += 2) {
            Vec3d cap = center.add(axes[2].multiply(side * span * .73));
            InkKit.cube(f.ink(), cap, span * 1.42, height, span * .46, row, seed, axes, 1);
        }
    }
}
