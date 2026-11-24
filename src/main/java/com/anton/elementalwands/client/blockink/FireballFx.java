package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireKit;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Fireball, drawn from its effect entities: the ball forming over the copy's head out of ink, in
 * flight, its burst, and the burning ground it leaves. Fire from the Fire kit, cooling into ink at every tip.
 */
final class FireballFx {
    private FireballFx() {}

    /** {@link FireballSpell#KIND}: stage 0 forming over the head, 1 in flight, 2 burst. */
    static void ball(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double seed = e.getId() * 13.7, t = f.now();
        switch (e.stage()) {
            case 0 -> {
                Entity owner = f.world().getEntityById(e.ownerId());
                double formed = e.formed(delta), r = FireballSpell.formingRadius(formed);
                Vec3d c = owner instanceof FirstWizardEntity copy
                        ? copy.getLerpedPos(delta).add(0, FireballSpell.formingHeight(copy.torn(), formed), 0)
                        : e.getLerpedPos(delta);
                if (!f.near(c, 160)) return;
                // Ink gathers first; the element's colour takes it from a fifth of the way in.
                double colour = smoothstep((formed - .18) / .55);
                draw(f, c, r, colour, null, seed, t);
            }
            case 1 -> {
                double age = e.stageAge(delta);
                Vec3d vel = e.dir(), c = e.auxPoint().add(vel.multiply(age));
                if (!f.near(c, 160)) return;
                draw(f, c, FireballSpell.RADIUS, 1, vel, seed, t);
                // Ink sheds off it as it flies and falls away behind.
                if (f.embers()) for (int i = 0; i < 6; i++) {
                    double back = (i + fract(age / 2)) * 2;     // ticks behind the ball
                    if (back > age) continue;
                    Vec3d at = e.auxPoint().add(vel.multiply(age - back)).add(0, -FireballSpell.RADIUS * .6, 0);
                    double fallen = back / 20;
                    Vec3d p = at.add((rnd(seed + i) - .5) * .8, -7 * fallen * fallen, (rnd(seed + i * 2) - .5) * .8);
                    InkKit.cube(f.ink(), p, .22 * (1 - back / 14), InkKit.INK, seed + i, tumble(seed + i, t, 4));
                }
            }
            default -> {
                double age = e.stageAge(delta) / 20;
                Vec3d c = e.auxPoint();
                if (!f.near(c, 160)) return;
                BlockFireKit.burst(f.fire(), c, age, new BlockFireKit.Burst(FireballSpell.BURST, 22, .5, .4, seed).smoke(false));
                InkKit.splash(f.ink(), c, InkKit.floorBelow(f.world(), c), age, 10, 4, .22, seed + 40);
            }
        }
    }

    /**
     * The ball: a fire heart, tongues of flame all round it that cool to purple tips and ink, and a skin of ink that
     * the colour has not yet taken. colour 0 is all ink (it has just gathered), 1 all fire. In flight the tongues
     * stream back from vel.
     */
    private static void draw(InkFrame f, Vec3d c, double r, double colour, Vec3d vel, double seed, double t) {
        Vec3d back = vel == null || vel.lengthSquared() < 1e-6 ? null : vel.normalize().multiply(-1);
        if (colour > .05) {
            double heart = r * .62 * colour;
            for (int i = 0; i < 4; i++) {
                Vec3d off = sphereDir(i, 4).multiply(heart * .3);
                BlockFireKit.cube(f.fire(), c.add(off), heart * (1 - i * .12), heat(4 - i * .7), seed + i, tumble(seed + i, t, 2.5));
            }
            int tongues = 14;
            for (int i = 0; i < tongues; i++) {
                Vec3d dir = sphereDir(i, tongues);
                if (back != null) dir = dir.add(back.multiply(1.6)).normalize();
                double spin = t * .6 + seed;
                dir = rotate(dir, UP, spin);
                Vec3d root = c.add(dir.multiply(r * .35));
                InkKit.coolingFlame(f, root, dir, r * .42 * colour, r * (back != null ? 1.1 : .75) * colour, seed + i * 7.3, 1, .5);
            }
        }
        // The ink it is made of: a coat that thins as the colour takes it.
        double coat = 1 - colour;
        if (coat > .02) {
            InkKit.blob(f.ink(), c, r * (.45 + .35 * coat), InkKit.INK, InkKit.GLOSS, seed, t);
            for (int i = 0; i < 6; i++) {
                Vec3d dir = rotate(sphereDir(i, 6), UP, t * 1.3 + seed);
                InkKit.cube(f.ink(), c.add(dir.multiply(r * .8)), r * .35 * coat, i % 3 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 2));
            }
        }
    }

    /** {@link FireballSpell#GROUND_KIND}: licks of flame while it arms, then burning ground over ink, low to the floor. */
    static void ground(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        if (!f.near(c, 128)) return;
        double age = e.age(delta) / 20, life = e.life() / 20.0, arm = e.form() / 20.0, R = e.size();
        double seed = e.getId() * 7.9, t = f.now();
        double grow = smoothstep(age / Math.max(.05, arm)), fade = smoothstep((life - age) / .8);
        // Scorched ink under the fire, spreading out from the burst and shrinking last.
        InkKit.pool(f.ink(), c.add(0, .01, 0), R, Math.min(smoothstep(age / .3), smoothstep((life - age) / .5) + .2), t, seed);
        int count = (int) Math.ceil(R * R * 2.2);
        for (int i = 0; i < count; i++) {
            double a = rnd(seed + i * 1.3) * TAU, d = Math.sqrt(rnd(seed + i * 2.1)) * R * .92;
            Vec3d root = c.add(Math.cos(a) * d, .05, Math.sin(a) * d);
            double size = .32 + .18 * rnd(seed + i * 3.3);
            double length = (.5 + .6 * rnd(seed + i * 4.1)) * (age < arm ? .45 * grow : 1) * fade;
            InkKit.coolingFlame(f, root, UP, size * (.6 + .4 * fade), length, seed + i * 5.1, .85, .45);
        }
    }
}
