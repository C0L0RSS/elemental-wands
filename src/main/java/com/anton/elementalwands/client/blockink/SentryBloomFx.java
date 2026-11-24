package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.InkBloomEntity;
import com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** The lobbed bud, petal drips, seeds, healing transfer and break. The living flower has its own renderer. */
final class SentryBloomFx {
    private SentryBloomFx() {}

    /** {@link SentryBloomSpell#BUD_KIND}: stage 0 forming over the head, 1 lobbed. */
    static void bud(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double seed = e.getId() * 7.3, t = f.now();
        if (e.stage() == 0) {
            Entity owner = f.world().getEntityById(e.ownerId());
            Vec3d feet = owner != null ? owner.getLerpedPos(delta) : e.getLerpedPos(delta).subtract(0, SentryBloomSpell.BUD_HEIGHT, 0);
            double formed = e.formed(delta), r = SentryBloomSpell.formingRadius(formed);
            Vec3d c = feet.add(0, 1.95 + r * 1.2, 0);
            if (!f.near(c, 160)) return;
            draw(f, c, r, smoothstep((formed - .2) / .5), t * .8, seed, t);
            return;
        }
        double k = e.stageAge(delta) / SentryBloomSpell.LOB;
        if (k > 1.05) return;
        Vec3d c = SentryBloomSpell.lob(e.auxPoint(), e.dir(), k);
        if (!f.near(c, 160)) return;
        draw(f, c, SentryBloomSpell.BUD_RADIUS, 1, t * 5, seed, t);
        // Ink shed along its arc, falling away behind it.
        if (f.embers()) for (int i = 1; i <= 5; i++) {
            double back = k - i * .05;
            if (back < 0) continue;
            Vec3d p = SentryBloomSpell.lob(e.auxPoint(), e.dir(), back).add(0, -i * i * .03, 0);
            InkKit.cube(f.ink(), p, .2 * (1 - i / 6.0), i % 3 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 4));
        }
    }

    /** A closed bud: five sepals round a pink heart with a gold tip, under a coat of ink the colour has not yet taken. */
    private static void draw(InkFrame f, Vec3d c, double r, double colour, double spin, double seed, double t) {
        if (colour > .05) {
            double g = .35 + .65 * colour;
            NatureInk.cube(f, c.add(0, r * .15, 0), r * .95 * g, r * 1.2 * g, r * .95 * g, NatureInk.PINK, seed, yawAxes(spin));
            NatureInk.cube(f, c.add(0, r * .95 * g, 0), r * .4 * g, NatureInk.GOLD, seed + 1, yawAxes(spin + .4));
            NatureInk.cube(f, c.add(0, -r * .75, 0), r * .7, r * .45, r * .7, NatureInk.DARKLEAF, seed + 2, yawAxes(spin));
            for (int i = 0; i < 5; i++) {
                double a = i * TAU / 5 + spin;
                Vec3d radial = new Vec3d(Math.cos(a), 0, Math.sin(a));
                Vec3d along = UP.subtract(radial.multiply(.4)).normalize();
                Vec3d normal = radial.subtract(along.multiply(radial.dotProduct(along))).normalize();
                Vec3d[] axes = {normal.crossProduct(along), normal, along};
                NatureInk.leaf(f, c.add(radial.multiply(r * .55)).add(0, -r * .1, 0), r * 1.7 * g, i % 2 == 0 ? NatureInk.LEAF : NatureInk.DARKLEAF, seed + 3 + i, axes);
            }
        }
        double coat = 1 - colour;
        if (coat > .02) {
            InkKit.blob(f.ink(), c, r * (.5 + .4 * coat), InkKit.INK, InkKit.GLOSS, seed, t);
            for (int i = 0; i < 5; i++) {
                Vec3d dir = rotate(sphereDir(i, 5), UP, t * 1.1 + seed);
                InkKit.cube(f.ink(), c.add(dir.multiply(r * .85)), r * .4 * coat, i % 2 == 0 ? InkKit.RIM : InkKit.INK, seed + i, tumble(seed + i, t, 2));
            }
        }
    }

    /**
     * {@link SentryBloomSpell#GROUND_KIND}: ink
     * running off its petals; while it delivers, what it stored flowing in an arc into the copy; the heal round the
     * copy when it arrives; or the break, its petals torn off and its stored light falling dark.
     */
    static void ground(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        Vec3d c = e.getLerpedPos(delta);
        double seed = e.getId() * 5.9, t = f.now();
        int stage = e.stage();
        double age = e.stageAge(delta);
        Entity owner = f.world().getEntityById(e.ownerId());
        if (f.near(c, 128)) {
            InkBloomEntity flower = flower(f, c);
            if (flower != null && f.embers() && stage != SentryBloomSpell.BROKEN) drips(f, flower, c.y, delta, seed, t);
        }
        if (stage == SentryBloomSpell.DELIVERING && owner != null) flow(f, e.auxPoint(), owner.getLerpedPos(delta).add(0, 1.1, 0), age, e.size(), seed);
        if (stage == SentryBloomSpell.WILTED && owner != null && e.size() > 0) ThornbiteFx.heal(f, owner.getLerpedPos(delta), age / 20, seed);
        if (stage == SentryBloomSpell.BROKEN) shatter(f, c, e.auxPoint(), age / 20, e.size(), seed);
    }

    private static InkBloomEntity flower(InkFrame f, Vec3d c) {
        List<InkBloomEntity> found = f.world().getEntitiesByClass(InkBloomEntity.class, new Box(c, c).expand(1, 2, 1), x -> !x.isRemoved());
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Ink running off the flower's petals to the floor. */
    private static void drips(InkFrame f, InkBloomEntity flower, double floor, float delta, double seed, double t) {
        double grown = flower.grown(delta);
        if (grown < .6) return;
        Vec3d head = flower.getLerpedPos(delta).add(0, SentryBloomSpell.HEAD_PIVOT + .4, 0);
        int count = flower.bloomStage() == InkBloomEntity.WILTING ? 6 : 4;
        for (int i = 0; i < count; i++) {
            double life = 1.2 + rnd(seed + i * 1.7) * .6, u = (t + rnd(seed + i) * life) % life;
            double a = rnd(seed + i * 3.3 + Math.floor((t + rnd(seed + i) * life) / life)) * TAU;
            InkKit.drip(f.ink(), head.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2), floor, u, life, .16, seed + i * 5);
        }
    }

    /**
     * What it stored, flowing from its head to the copy in an arc: gold off the flower, sap on the way, purple and then
     * ink as it sinks into the copy. share is how much it held (0 to 1); an empty flower sends a thin trickle of ink.
     */
    private static void flow(InkFrame f, Vec3d from, Vec3d to, double age, double share, double seed) {
        int beads = Math.max(3, (int) Math.round(18 * share));
        double span = SentryBloomSpell.DELIVER, travel = 12, apart = from.distanceTo(to);
        for (int i = 0; i < beads; i++) {
            double k = (age - i * (span - travel * .5) / beads) / travel;
            if (k < 0 || k > 1) continue;
            Vec3d p = from.lerp(to, k).add(0, 4 * k * (1 - k) * (1.5 + apart * .08), 0);
            if (!f.near(p, 160)) continue;
            double size = (share > 0 ? .42 : .2) * (1 - .3 * k);
            Vec3d[] axes = tumble(seed + i, age / 20, 4);
            if (share <= 0) InkKit.cube(f.ink(), p, size, InkKit.INK, seed + i, axes);
            else if (k < .45) NatureInk.cube(f, p, size, NatureInk.GOLD, seed + i, axes);
            else if (k < .7) NatureInk.cube(f, p, size, NatureInk.SAP, seed + i, axes);
            else InkKit.cube(f.ink(), p, size, InkKit.cool((k - .7) / .3), seed + i, axes);
        }
    }

    /**
     * The break: petals and leaves torn off its head, falling and going to ink where they land; the light it stored
     * falling from its stem and going dark; ink splashing from the wound. age in seconds.
     */
    private static void shatter(InkFrame f, Vec3d base, Vec3d head, double age, double share, double seed) {
        if (!f.near(base, 160)) return;
        double floor = base.y;
        for (int i = 0; i < 18; i++) {
            double a = i * 2.39996 + seed, out = 2.6 * (.5 + .5 * rnd(seed + i)), up = 2 + 2 * rnd(seed + i * 1.3);
            double y = head.y + up * age - 7 * age * age;
            Vec3d p = new Vec3d(head.x + Math.cos(a) * out * Math.min(1, age * 2.5), Math.max(floor + .1, y), head.z + Math.sin(a) * out * Math.min(1, age * 2.5));
            double landed = y <= floor + .1 ? 1 : 0, cool = Math.min(1, age / 1.2) * .6 + landed * .4;
            double keep = 1 - smoothstep((age - 1.1) / .4);
            Vec3d[] axes = tumble(seed + i, age, landed > 0 ? 0 : 5);
            if (cool < .35 + .3 * rnd(seed + i * 2.2)) {
                if (i % 3 == 0) NatureInk.leaf(f, p, .9 * keep, NatureInk.LEAF, seed + i, axes);
                else NatureInk.petal(f, p, 1.0 * keep, i % 2 == 0 ? NatureInk.PINK : NatureInk.VIOLET, seed + i, axes);
            } else InkKit.cube(f.ink(), p, .6 * keep, .14 * keep, .7 * keep, InkKit.cool(cool), seed + i, axes, 1);
        }
        // The light it held, wasted: its beads fall from the stem and go dark.
        int beads = (int) Math.round(share * 5);
        for (int i = 0; i < beads; i++) {
            double h = .9 + i * .6, y = Math.max(floor + .2, floor + h - 7 * age * age);
            Vec3d p = base.add((rnd(seed + i * 4.1) - .5) * 1.4, y - floor, (rnd(seed + i * 5.1) - .5) * 1.4);
            double dark = smoothstep(age / .8), keep = 1 - smoothstep((age - 1) / .4);
            if (dark < .4) NatureInk.cube(f, p, .5 * keep, NatureInk.GOLD, seed + i, tumble(seed + i, age, 3));
            else InkKit.cube(f.ink(), p, .45 * keep, InkKit.cool(dark), seed + i, tumble(seed + i, age, 3));
        }
        InkKit.splash(f.ink(), head, floor, age, 14, 3.5, .3, seed + 40);
    }

    /** {@link SentryBloomSpell#SEED_KIND}: stage 0 in flight, 1 burst. */
    static void seed(InkFrame f, FirstWizardEffectEntity e) {
        float delta = f.delta();
        double seed = e.getId() * 3.3, t = f.now(), r = SentryBloomSpell.SEED_RADIUS;
        if (e.stage() == 0) {
            double age = e.age(delta);
            Vec3d vel = e.dir(), c = e.auxPoint().add(vel.multiply(age));
            if (!f.near(c, 160)) return;
            Vec3d[] axes = basis(vel.lengthSquared() < 1e-6 ? UP : vel, t * 6);
            // A gold kernel in a green husk, its tail cooling to ink.
            NatureInk.cube(f, c, r * 1.3, r * 1.9, r * 1.3, NatureInk.CREAM, seed, axes);
            NatureInk.cube(f, c.add(axes[1].multiply(r * .5)), r * 1.05, NatureInk.GOLD, seed + 1, axes);
            NatureInk.cube(f, c.subtract(axes[1].multiply(r * .7)), r * 1.5, r * .9, r * 1.5, NatureInk.DARKLEAF, seed + 2, axes);
            Vec3d back = vel.normalize().multiply(-1);
            for (int i = 1; i <= 4; i++) {
                Vec3d p = c.add(back.multiply(i * .45)).add(0, -i * i * .02, 0);
                InkKit.cube(f.ink(), p, r * (1 - i * .18), InkKit.cool(.25 * i), seed + i, tumble(seed + i, t, 5));
            }
            return;
        }
        double age = e.stageAge(delta) / 20;
        Vec3d c = e.auxPoint();
        if (!f.near(c, 160)) return;
        double keep = 1 - smoothstep(age / .4);
        for (int i = 0; i < 7; i++) {
            Vec3d dir = sphereDir(i, 7);
            NatureInk.cube(f, c.add(dir.multiply(.25 + age * 3)), .22 * keep, i % 2 == 0 ? NatureInk.GOLD : NatureInk.SAP, seed + i, tumble(seed + i, age, 6));
        }
        InkKit.splash(f.ink(), c, InkKit.floorBelow(f.world(), c), age, 6, 2, .14, seed + 9);
    }
}
