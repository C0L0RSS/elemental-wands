package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.spell.FlameSpell;
import com.anton.elementalwands.entity.firstwizard.spell.LeapSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Tendrils;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * What phase two marks on the floor and breathes, drawn from its effect entities: the ring round the change's full
 * sweep, the Sweep's arc, the Slam's line, the Leap's landing circle, and the Flame's cone of the copy's own fire.
 * Marks are ink on the floor with a violet edge that flares when the aim locks; they lie flat, so they never hide a
 * body. The tendrils themselves are {@link TendrilFx}'s.
 */
final class InkMovesFx {
    private InkMovesFx() {}

    // ── Floor marks ──────────────────────────────────────────────────────────



    /** A flare that pulses as it nears its strike, so it reads at a distance: 0 dormant, 1 flared. */
    private static double pulse(double flare, double t) { return flare <= 0 ? 0 : Math.clamp(flare * (.78 + .22 * Math.sin(t * 13)), 0, 1); }

    /** {@code PhaseChange.RING_KIND}: the full sweep's reach, ringed in ink while it lowers; it flares as the sweep starts. */
    static void phaseRing(InkFrame f, FirstWizardEffectEntity e) {
        Vec3d c = e.getLerpedPos(f.delta());
        if (!f.near(c, 128)) return;
        double formed = e.formed(f.delta()), t = f.now();
        double fade = e.stage() >= 1 ? 1 - smoothstep(e.stageAge(f.delta()) / 34) : 1;
        if (fade <= .02) return;
        double flare = pulse(e.stage() >= 1 ? fade : smoothstep((formed - .7) / .3), t);
        double half = .45 * (1 + .12 * flare);
        f.floor().arc(c, e.size() * smoothstep(formed / .5), 0, TAU * fade, half, flare, 1);
        f.floor().pool(c, Tendrils.INNER * 1.15, e.getId(), flare * .5, smoothstep(formed / .4) * fade);
    }

    /** {@link SweepSpell#KIND}: the arc it will swing, once fixed, at its reach; it flares as the swing starts. */
    static void sweep(InkFrame f, FirstWizardEffectEntity e) {
        if (e.stage() < 1) return;
        Vec3d c = e.getLerpedPos(f.delta());
        if (!f.near(c, 128)) return;
        double t = f.now(), side = e.auxPoint().x, aim = Tendrils.angle(e.dir());
        double from = SweepSpell.start(aim, side), to = SweepSpell.end(aim, side);
        double fade = e.stage() >= 2 ? 1 - smoothstep((e.stageAge(f.delta()) - SweepSpell.SWING) / 10) : 1;
        if (fade <= .02) return;
        double flare = pulse(e.stage() >= 2 ? 1 : .55, t);
        double end = from + (to - from) * fade;
        f.floor().arc(c, e.size(), from, end, .5 * (1 + .2 * flare), flare, 1);
        // A wider rim of light over it while it flares, so the arc reads as plainly as the Slam's line from afar.
        if (flare > .3) f.floor().arc(c, e.size(), from, end, .5 * (1.35 + .3 * flare), flare, flare, true);
    }

    /** {@code SlamSpell.KIND}: the line it will come down on; it flares at the aim lock; it lands in a splash. */
    static void slam(InkFrame f, FirstWizardEffectEntity e) {
        Vec3d a = e.getLerpedPos(f.delta()), b = e.auxPoint();
        if (!f.near(a, 128)) return;
        double t = f.now(), formed = e.formed(f.delta());
        double fade = e.stage() >= 2 ? 1 - smoothstep((e.stageAge(f.delta()) - 10) / 10) : 1;
        if (fade <= .02) return;
        Vec3d along = b.subtract(a).multiply(1, 0, 1);
        if (along.lengthSquared() < 1e-4) return;
        double flare = pulse(e.stage() >= 1 ? 1 : 0, t);
        Vec3d start = a.add(along.normalize().multiply(.8));
        // Grows out along the line from the copy while the tentacle rears.
        Vec3d end = start.lerp(b, smoothstep(formed / .6));
        f.floor().line(start, end, e.size() * (1 + .1 * flare), flare, fade);
        if (e.stage() >= 2) {
            double age = e.stageAge(f.delta()) / 20;
            for (int i = 0; i < 4; i++) {
                Vec3d at = a.add(along.multiply((i + 1) / 4.0));
                InkKit.splash(f.ink(), at.add(0, .2, 0), at.y, age, 6, 3.5, .18, e.getId() + i * 11);
            }
        }
    }

    /** {@link LeapSpell#KIND}: the landing circle, its pool filling in as the copy comes down; a splash as it lands. */
    static void leap(InkFrame f, FirstWizardEffectEntity e) {
        if (e.stage() < 1) return;
        Vec3d c = e.getLerpedPos(f.delta());
        if (!f.near(c, 128)) return;
        double t = f.now(), r = e.size();
        double coming = e.stage() == 1 ? 0 : e.stage() == 2 ? smoothstep(e.stageAge(f.delta()) / LeapSpell.FLIGHT) : 1;
        double fade = e.stage() >= 3 ? 1 - smoothstep(e.stageAge(f.delta()) / 12) : 1;
        if (fade <= .02) return;
        double flare = pulse(e.stage() == 2 ? .3 + .7 * smoothstep((coming - .4) / .4) : .2, t);
        f.floor().pool(c, r * .95, e.getId(), flare, (.3 + .7 * coming) * fade);
        f.floor().arc(c, r, 0, TAU, .38 * (1 + .12 * flare), flare, fade);
        if (e.stage() >= 3) InkKit.splash(f.ink(), c.add(0, .3, 0), c.y, e.stageAge(f.delta()) / 20, 18, 5, .22, e.getId() + 9);
    }

    // ── The flame ────────────────────────────────────────────────────────────

    /**
     * {@link FlameSpell#KIND}: fire gathering in the human hand, then a cone of the copy's own fire from it (orange at
     * the base, purple at the tips, cooling to ink where smoke would be), and when it stops, ink rising off the cone.
     */
    static void flame(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Entity owner = f.world().getEntityById(e.ownerId());
        if (!(owner instanceof FirstWizardEntity copy)) return;
        Vec3d hand = WizardBody.hand(copy, delta);
        if (!f.near(hand, 128)) return;
        double t = f.now(), seed = e.getId() * 3.3;
        Vec3d dir = e.dir();
        if (dir.lengthSquared() < 1e-6) return;
        dir = dir.normalize();
        Vec3d down = dir.add(0, -.12, 0).normalize();
        if (e.stage() == 0) {
            double formed = e.formed(delta);
            for (int i = 0; i < 5; i++) {
                Vec3d d = rotate(sphereDir(i, 5), UP, t * 2 + seed);
                InkKit.coolingFlame(f, hand, d.add(dir).normalize(), .18 + .2 * formed, .3 + .4 * formed, seed + i, 1, .55);
            }
            return;
        }
        double age = e.stageAge(delta);
        double on = e.stage() == 1 ? smoothstep(age / 3) : 1 - smoothstep(age / 8);
        if (on <= .02) return;
        double reach = FlameSpell.REACH * on, half = Math.toRadians(FlameSpell.HALF_ANGLE);
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        // Tongues fanned across the cone, each one the block-fire flame cooling to purple tips and ink.
        int tongues = 11;
        for (int i = 0; i < tongues; i++) {
            double k = (i + .5) / tongues - .5, spread = k * 2 * half * (.85 + .15 * Math.sin(t * 6 + i));
            Vec3d d = down.multiply(Math.cos(spread)).add(side.multiply(Math.sin(spread))).normalize().add(0, .05 * Math.sin(t * 5 + i * 2), 0);
            double len = reach * (.75 + .25 * noise(t * 3 + i * 1.7 + seed));
            InkKit.coolingFlame(f, hand.add(d.multiply(.25)), d, .5 + .15 * Math.abs(k) * 4, len * .55, seed + i * 7, 1, .45);
            InkKit.coolingFlame(f, hand.add(d.multiply(len * .45)), d.add(0, .08, 0).normalize(), .9, len * .55, seed + i * 7 + 3, .8, .3);
        }
        // Where it licks the floor: low flame and scorched ink along the cone.
        for (int i = 0; i < 6; i++) {
            double k = (i + .5) / 6, spread = (rnd(seed + i) - .5) * 2 * half;
            Vec3d at = new Vec3d(hand.x, copy.getY() + .05, hand.z).add(dir.multiply(Math.cos(spread) * reach * (.35 + .6 * k))).add(side.multiply(Math.sin(spread) * reach * (.35 + .6 * k)));
            InkKit.coolingFlame(f, at, UP, .35, .55 * on, seed + i * 13, .8, .4);
        }
        if (e.stage() == 2) {
            // Spent: the cooled breath hangs as ink and purple where the cone was (the cloud a bottle collects).
            for (int i = 0; i < 6; i++) {
                double u = fract(age / 20 + i / 6.0);
                Vec3d p = hand.add(dir.multiply(2 + i * 1.1)).add(0, u * 1.2, 0);
                InkKit.cube(f.ink(), p, .35 * (1 - u), i % 2 == 0 ? InkKit.PURPLE : InkKit.INK, seed + i, tumble(seed + i, t, 1.5));
            }
        }
    }
}
