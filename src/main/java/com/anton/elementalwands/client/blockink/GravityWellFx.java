package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockspace.BlockSpaceKit;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** A lobbed orb opens into a floating core that draws airborne fragments inward before the burst. */
final class GravityWellFx {
    private GravityWellFx() {}

    /** {@link GravityWellSpell#KIND}: stage 0 forming over the head, 1 in flight. */
    static void orb(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double seed = e.getId() * 12.1, t = f.now();
        if (e.stage() == 0) {
            Entity owner = f.world().getEntityById(e.ownerId());
            Vec3d feet = owner != null ? owner.getLerpedPos(delta) : e.getLerpedPos(delta).subtract(0, GravityWellSpell.HEIGHT, 0);
            double formed = e.formed(delta), r = GravityWellSpell.formingRadius(formed);
            Vec3d c = feet.add(0, 1.95 + r, 0);
            if (!f.near(c, 160)) return;
            draw(f, c, r, smoothstep((formed - .2) / .5), seed, t);
            return;
        }
        double u = e.stageAge(delta) / GravityWellSpell.FLIGHT;
        Vec3d launch = e.auxPoint(), landing = launch.add(e.dir());
        Vec3d c = GravityWellSpell.arc(launch, landing, u);
        if (!f.near(c, 160)) return;
        draw(f, c, GravityWellSpell.ORB_RADIUS, 1, seed, t);
        // Ink drips off it as it flies.
        if (f.embers()) for (int i = 1; i <= 5; i++) {
            double back = u - i * .05;
            if (back < 0) continue;
            Vec3d p = GravityWellSpell.arc(launch, landing, back).add((rnd(seed + i) - .5) * .6, -.4 - i * .25, (rnd(seed + i * 2) - .5) * .6);
            InkKit.cube(f.ink(), p, .26 * (1 - i / 6.0), i % 3 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 4));
        }
    }

    /**
     * The orb: a void heart in Space's colours with two rings of its rim band orbiting it, wrapped in ink that the
     * colour has not yet taken. colour 0 is all ink, 1 all Space.
     */
    private static void draw(InkFrame f, Vec3d c, double r, double colour, double seed, double t) {
        AtlasMesh space = f.space();
        if (colour > .05) {
            double heart = r * .8 * colour;
            space.box(c, heart * .5, heart * .5, heart * .5, tumble(seed, t, 1.2), BlockSpaceKit.VOID, 0, 1, 1);
            space.box(c, heart * .42, heart * .42, heart * .42, tumble(seed + 1, t, -1.6), BlockSpaceKit.NEBULA, 1, 1, 1);
            for (int ring = 0; ring < 2; ring++) {
                Vec3d axis = ring == 0 ? new Vec3d(.3, 1, .2).normalize() : new Vec3d(-.4, 1, .3).normalize();
                Vec3d[] b = basis(axis, 0);
                int count = 9;
                for (int i = 0; i < count; i++) {
                    double a = i * TAU / count + t * (ring == 0 ? 2.4 : -1.8) + seed;
                    Vec3d p = c.add(b[0].multiply(Math.cos(a) * r * 1.05)).add(b[2].multiply(Math.sin(a) * r * 1.05));
                    double s = r * .2 * colour;
                    space.box(p, s * .5, s * .5, s * .5, tumble(seed + i, t, 3), BlockSpaceKit.RIM, 0, 1, 1);
                }
            }
        }
        double coat = 1 - colour;
        if (coat > .02) {
            InkKit.blob(f.ink(), c, r * (.45 + .4 * coat), InkKit.INK, InkKit.GLOSS, seed, t);
            for (int i = 0; i < 6; i++) {
                Vec3d dir = rotate(sphereDir(i, 6), UP, t * 1.3 + seed);
                InkKit.cube(f.ink(), c.add(dir.multiply(r * .85)), r * .34 * coat, i % 3 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 2));
            }
        }
    }

    /** {@link GravityWellSpell#WELL_KIND}: stage 0 pulling, 1 the buildup (still pulling), 2 the burst. */
    static void well(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 160)) return;
        double seed = e.getId() * 6.7, t = f.now(), R = e.size(), age = e.age(delta) / 20;
        double open = smoothstep(age / .4);
        int stage = e.stage();
        double build = stage == 1 ? clamp(e.stageAge(delta) / GravityWellSpell.BUILDUP) : stage == 2 ? 1 : 0;
        if (stage == 2) { burst(f, c, e.stageAge(delta) / 20, R, seed, t); return; }
        double speed = 1 + build * 2.5;

        Vec3d core = c.add(0, 1.35 + .65 * build, 0);
        InkKit.blob(f.ink(), core, (.65 + .7 * build) * open, InkKit.DEEP, build > .5 ? InkKit.VIOLET : InkKit.RIM, seed, t);
        // Debris rises inward from throughout the pull. It winds tighter around the brightening core before the burst.
        int count = f.embers() ? 42 : 24;
        for (int i = 0; i < count; i++) {
            double u = fract(age / 2.4 + build * .8 + i / (double) count);
            double r = R * open * (1 - u);
            double a = rnd(seed + i) * TAU + u * 2.8;
            double h = .65 + 2.0 * u + .45 * Math.sin(a);
            Vec3d p = c.add(Math.cos(a) * r, h, Math.sin(a) * r);
            double size = (.28 + .23 * rnd(seed + i * 3)) * Math.sin(Math.PI * u);
            InkKit.cube(f.ink(), p, size, size, size * 1.5,
                    u > .65 || build > .65 ? InkKit.VIOLET : InkKit.RIM, seed + i, tumble(seed + i, t, speed), 1);
        }
    }

    /** The burst: a ring of ink thrown out to the burst's edge, flat, cooling from magenta to black; ink splashes wide. */
    private static void burst(InkFrame f, Vec3d c, double age, double R, double seed, double t) {
        double k = clamp(age / .35), r = GravityWellSpell.BURST * (.2 + .9 * smoothstep(k)), fade = 1 - smoothstep((age - .3) / .5);
        if (fade > 0) {
            int count = (int) Math.ceil(TAU * r / .45);
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + seed;
                Vec3d p = c.add(Math.cos(a) * r, .1 + .3 * (1 - k), Math.sin(a) * r);
                InkKit.cube(f.ink(), p, .8 * fade, .45 * fade + .05, .45 * fade, InkKit.cool(k * .8 + age), seed + i, yawAxes(a + Math.PI / 2), 1);
                // A lower, slower wave of ink behind it.
                Vec3d q = c.add(Math.cos(a) * r * .7, .05, Math.sin(a) * r * .7);
                InkKit.cube(f.ink(), q, .6 * fade, .12, .5 * fade, i % 3 == 0 ? InkKit.GLOSS : InkKit.INK, seed + i + 300, yawAxes(a + Math.PI / 2), 1);
            }
        }
        if (age < .15) f.space().box(c.add(0, .9, 0), .5 * (1 - age / .15), .5 * (1 - age / .15), .5 * (1 - age / .15), tumble(seed, t, 4), BlockSpaceKit.RIM, 0, 1, 1);
        InkKit.splash(f.ink(), c.add(0, .9, 0), c.y, age, f.embers() ? 18 : 8, 6, .3, seed + 7);
        InkKit.pool(f.ink(), c.add(0, .01, 0), 2.2, 1 - smoothstep(age / .8), t, seed);
    }
}
