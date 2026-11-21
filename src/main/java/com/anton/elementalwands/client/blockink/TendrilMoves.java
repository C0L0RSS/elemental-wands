package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.FlameSpell;
import com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell;
import com.anton.elementalwands.entity.firstwizard.spell.LeapSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SlamSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import net.minecraft.util.math.Vec3d;

/**
 * What each of phase two's tentacles does in the move in hand, so the whole body takes part in every move (the
 * developer, Nov 20: "I want the tentacles to be a part of its attacks not just freeze"). Every pose comes from the
 * move's own clock with the partial tick and is alive: waves keep running along it, and in the long holds (the Flame's
 * breath, Hollow Purple's forming) the arms heave, sway and weave slowly and widely enough to read from across the
 * floor, so no two moments half a second apart look alike. {@link TendrilFx} blends each tentacle from its rest pose
 * into the pose asked of it, and back, on its own spring.
 *
 * <ul>
 *   <li><b>Leap:</b> the low and the shoulder-height tentacles plant their tips behind and beside her and load like
 *       springs while the striker rears toward the landing; at the launch the planted two straighten and shove her
 *       off, tips pinned a moment as she rises, then peel away; in the air all three stream behind her like a squid's
 *       arms, then swing forward and spread, reaching down, and slap down round the landing pool as she lands.</li>
 *   <li><b>Flame:</b> two grip the floor round her, one before her and one behind, as an octopus's arms grip
 *       ({@code Ctx.grip}): each rises from her flank into an elbow that heaves and sways, drops onto the floor, lies
 *       along it and curls its tip into a grip. They jolt at each of the breath's half-second burns, twice in the breath
 *       each slips a little further out and grips again, and if she turns far they step to fresh holds. The striker
 *       arches high behind her head like a scorpion's tail, its sting aimed down the cone; while she breathes it weaves
 *       like a cobra's head, side to side and up and down, lags behind her turn, its sting flexing, and pumps forward
 *       into each burn. They rip free as the breath ends, tips first.</li>
 *   <li><b>Hollow Purple:</b> two grip wide and strain harder as the orb grows: three times each slides further out
 *       and grips again, tighter, and their heaving quickens from about half a heave a second to one and a half. The
 *       striker climbs her left side, swaying, and feeds ink into the orb, broad violet swells running up it into the
 *       orb one after another. At the release all three take the recoil, flung and dragged back.</li>
 *   <li><b>Slam:</b> the two not striking plant behind her as counterweight, lean back as the striker rears and jolt
 *       as it lands. <b>Sweep:</b> they rise high out of the low swing's path and turn against it.</li>
 *   <li><b>The ten spells</b> (from the human hand): a lighter touch. They flare out and up while it forms, the
 *       striker's tip curling toward the gathering ink; at the release they recoil and spring back; a dash streams them
 *       behind her.</li>
 * </ul>
 *
 * The striker's own Slam and Sweep strikes are {@link TendrilFx}'s, which match the server's hits.
 */
final class TendrilMoves {
    private TendrilMoves() {}

    /** Ticks a re-planting step takes. */
    private static final double STEP = 6;

    /** What the move in hand asks of the three tentacles this frame. */
    static final class Ask {
        /** Each one's pose (world points from the root to the tip), or null to be at rest. */
        final Vec3d[][] pose = new Vec3d[3][];
        /** Whether each one's spring pulls toward its pose (false: it springs back to rest out of it). */
        final boolean[] want = new boolean[3];
        /**
         * Each one's girth (1 at rest), its ripple (0 to 1 along it, NaN for none), how much faster its waves run, and
         * how much its feeding swells swell it (0: the ripple is the tell's; see {@link TendrilFx}'s tube).
         */
        final double[] girth = {1, 1, 1}, ripple = {Double.NaN, Double.NaN, Double.NaN}, hurry = new double[3], swell = new double[3];
        /** Where ink runs from each one's tip, or null. */
        final Vec3d[] feed = new Vec3d[3];
    }

    /** The poses of the move in hand. rest is each tentacle's rest pose this frame (world), for the spells' lighter touch. */
    static Ask ask(InkFrame f, FirstWizardEntity copy, TendrilFx.Frame fr, TendrilRig rig, String move, double age, Vec3d[][] rest, double t, double dt) {
        Ask ask = new Ask();
        if (age < 0 || !copy.inPhaseTwo()) return ask;
        Ctx c = new Ctx(f, copy, fr, rig, age, t, dt, ask, rest);
        switch (move) {
            case LeapSpell.KIND -> leap(c);
            case FlameSpell.KIND -> flame(c);
            case HollowPurpleSpell.KIND -> purple(c);
            case SlamSpell.KIND -> slam(c);
            case SweepSpell.KIND -> sweep(c);
            default -> {
                FirstWizardRules.Move m = FirstWizardRules.Move.byId(move);
                if (m != null && m.spell()) spell(c);
            }
        }
        return ask;
    }

    // ── Leap ─────────────────────────────────────────────────────────────────

    /** Where the planters plant for the Leap (her left, ahead), and where the three slap down round the pool (degrees from ahead toward her left). */
    private static final double[] LEAP_O = {2.9, 1.5}, LEAP_F = {-1.2, -3.2}, SLAP = {35, 205, 100};
    /** How far from the landing's middle they slap down: at the pool's rim. */
    private static final double SLAP_R = LeapSpell.RADIUS + .1;

    private static void leap(Ctx c) {
        double A = c.age;
        TendrilFx.Frame fr = c.fr;
        int launch = LeapSpell.TIMING.release(), lands = LeapSpell.lands();
        // Where it lands: the mark once it is set (eased, so nothing reaching for it jumps a tick), until then ahead of her.
        FirstWizardEffectEntity mark = TendrilFx.effect(c.f, c.copy, LeapSpell.KIND);
        boolean marked = mark != null && mark.stage() >= 1;
        Vec3d to = marked ? mark.getLerpedPos(c.f.delta()) : c.g(0, 9);
        to = new Vec3d(to.x, marked ? to.y : fr.floor(), to.z);
        c.rig.land = c.rig.land == null ? to : ease(c.rig.land, to, 16, c.dt);
        Vec3d land = c.rig.land;
        Vec3d from = marked ? mark.auxPoint() : new Vec3d(fr.body().x, fr.floor(), fr.body().z);
        // The flight's heading on its arc, from the move's clock: the arms trail the other way.
        double k = Math.clamp((A - launch) / LeapSpell.FLIGHT, 0, 1);
        Vec3d vel = land.subtract(from).multiply(1, 0, 1).add(0, 4 * LeapSpell.HEIGHT * (1 - 2 * k), 0);
        Vec3d trail = vel.lengthSquared() < 1e-6 ? fr.fwd().multiply(-1) : vel.normalize().multiply(-1);
        Vec3d s1 = trail.crossProduct(UP);
        s1 = s1.lengthSquared() < 1e-6 ? fr.out() : s1.normalize();
        if (s1.dotProduct(fr.out()) < 0) s1 = s1.multiply(-1);
        Vec3d s2 = s1.crossProduct(trail).normalize();
        if (s2.y < 0) s2 = s2.multiply(-1);
        Vec3d[] spread = {s1.multiply(.8).subtract(s2.multiply(.6)), s1.add(s2.multiply(.3)), s1.multiply(.45).add(s2.multiply(.6))};
        Vec3d toLand = land.subtract(fr.body()).multiply(1, 0, 1);
        toLand = toLand.lengthSquared() < 1e-4 ? fr.fwd() : toLand.normalize();
        Vec3d w = fr.wound();

        double streamed = smoothstep((A - (launch + 2)) / 5), reach = smoothstep((A - (launch + 8)) / 5);
        double fall = Math.clamp((A - (lands - 3)) / 3, 0, 1), h = 2.2 * (1 - fall * fall);
        double slap = A >= lands ? Math.exp(-(A - lands) / 3) : 0;
        double flat = smoothstep((A - (lands - 1)) / 1.5) * (1 - .6 * smoothstep((A - (lands + 2)) / 4));
        for (int i = 0; i < 3; i++) {
            // In the air: streaming behind her, loosely spread.
            Vec3d[] stream = new Vec3d[6];
            stream[0] = w;
            stream[1] = w.add(fr.out().multiply(.45)).add(trail.multiply(.25));
            for (int j = 2; j < 6; j++) {
                double x = (j - 1) / 4.0;
                stream[j] = w.add(trail.multiply(7.2 * x)).add(spread[i].multiply(1.8 * Math.pow(x, 1.2)));
            }
            // Coming down: spread round the landing, reaching down for the floor at the pool's rim, then slapping onto it.
            double th = Math.toRadians(SLAP[i]);
            Vec3d radial = fr.fwd().multiply(Math.cos(th)).add(fr.out().multiply(Math.sin(th)));
            Vec3d spot = new Vec3d(land.x, land.y + .03, land.z).add(radial.multiply(SLAP_R)), tip = spot.add(0, h, 0);
            Vec3d[] down = c.root(
                    w.lerp(spot, .33).add(0, 2.0 - 1.0 * flat, 0).add(radial.multiply(.25)),
                    w.lerp(spot, .66).add(0, 1.5 - .9 * flat + h * .3, 0).add(radial.multiply(.4)),
                    spot.add(0, .55 * (1 - flat) + h * .85, 0).subtract(radial.multiply(.1)),
                    tip);
            if (A >= lands && !c.rig.slapped[i]) { c.rig.slapped[i] = true; c.splash(i, spot); }

            Vec3d[] ctrl;
            double pinned, amp, shudder;
            if (i < 2) {
                // The planters: down onto their spots behind and beside her, then loading like springs.
                double drive = smoothstep((A - 1 - i) / 5);
                Vec3d at = c.plant(i, c.g(LEAP_O[i], LEAP_F[i]), drive, 1.6, A < launch - 3);
                double load = smoothstep((A - 5) / 11), straight = smoothstep((A - (launch - .5)) / 2.5);
                Vec3d along = at.subtract(w).multiply(1, 0, 1);
                Vec3d dir = along.lengthSquared() < 1e-6 ? fr.out() : along.normalize();
                Vec3d[] loaded = c.root(
                        w.lerp(at, .38).add(0, 1.2 + 1.6 * load, 0).add(fr.out().multiply(.3 + .3 * load)),
                        w.lerp(at, .74).add(0, .8 + 1.2 * load, 0).subtract(dir.multiply(1.1 * load)),
                        at.add(0, .5 + .25 * load, 0).subtract(dir.multiply(.12 * load)),
                        at);
                // Shoving off: straight from the wound to the planted tip, which stays put as she rises.
                Vec3d[] shove = c.root(w.lerp(at, .38).add(0, .3, 0), w.lerp(at, .7).add(0, .16, 0), w.lerp(at, .9).add(0, .06, 0), at);
                ctrl = blend(loaded, shove, straight);
                // Then they peel off the floor from the root out, the tip last, into the stream.
                for (int j = 0; j < 6; j++) ctrl[j] = ctrl[j].lerp(stream[j], smoothstep((A - (launch + 2.5) - 2.5 * j / 5.0) / 2.5));
                pinned = c.pinned(i) * (1 - smoothstep((A - (launch + 3.5)) / 2));
                amp = lerp(.08, .3, streamed);
                shudder = .012 + .045 * load * (1 - straight);
            } else {
                // The striker rears toward the landing, cocks back as the launch nears, and lunges the way she goes.
                double cock = smoothstep((A - 7) / 8), lunge = smoothstep((A - (launch - 1)) / 3);
                Vec3d head = c.b(.6, 5.0 + .4 * cock - .8 * lunge, 0).add(toLand.multiply(1.1 - .9 * cock + 1.6 * lunge));
                Vec3d aim = land.add(0, .5, 0).subtract(head);
                aim = aim.lengthSquared() < 1e-6 ? fr.fwd() : aim.normalize();
                Vec3d[] rear = c.root(c.b(.95, 2.5, -.35), c.b(.85, 4.1, -.55 - .45 * cock).add(toLand.multiply(.25)), head, head.add(aim.multiply(2.3)));
                ctrl = blend(rear, stream, smoothstep((A - (launch + 1)) / 4));
                pinned = 0;
                amp = lerp(.13, .3, streamed);
                shudder = .01 + .03 * cock * (1 - lunge);
            }
            ctrl = blend(ctrl, down, reach);
            pinned = Math.max(pinned, smoothstep((A - (lands - .4)) / .8));
            amp = lerp(amp, .14, reach) * (1 - .4 * smoothstep((A - lands) / 3));
            c.ask.hurry[i] = 1.3 * streamed * (1 - reach);
            c.pose(i, ctrl, amp, shudder + .09 * slap, pinned, A < lands + 3);
        }
    }

    // ── Flame ────────────────────────────────────────────────────────────────

    /** Where the Flame's gripping arms take hold (her left, ahead): one before her, one behind. */
    private static final double[] FLAME_O = {4.6, 3.9}, FLAME_F = {2.3, -3.5};

    private static void flame(Ctx c) {
        double A = c.age;
        TendrilFx.Frame fr = c.fr;
        int rel = FlameSpell.TIMING.release(), end = FlameSpell.ends();
        double rip = smoothstep((A - end) / 4), on = smoothstep((A - rel) / 3) * (1 - rip);
        double strain = smoothstep((A - 8) / 5) * (1 - rip);
        // A jolt at each half-second burn of the breath.
        double jolt = A < end ? burn(A - rel, FirstWizardRules.FLAME_EVERY) * on : 0;
        Vec3d back = fr.fwd().multiply(-1);
        for (int i = 0; i < 2; i++) {
            double d = Math.clamp((A - 3 - 2 * i) / 5, 0, 1), drive = d * d;      // accelerating, slapped down
            Vec3d at = c.plant(i, c.g(FLAME_O[i], FLAME_F[i]), drive, 2.2, A >= rel && A < end - 4);
            Grip g = new Grip();
            g.along = c.along(i, at);
            // Out of step with each other, each slips a little further out twice in the breath and grips again.
            double[] re = regrip(A, .3, rel + 9 + 11 * i, rel + 31 + 11 * i);
            g.spot = at.add(g.along.multiply(re[0])).add(0, 2.2 * rip, 0).add(c.radial(at).multiply(.5 * rip));
            g.hand = i == 0 ? 1 : -1;
            double heave = TAU * .7 * A / 20 + 2.1 * i, sway = TAU * .38 * A / 20 + 1.3 + 2.9 * i, size = .45 + .25 * strain;
            g.curl = lerp(.5 * Math.PI, (1.15 + .85 * strain) * Math.PI + .5 * jolt, c.gripped(i)) * (1 - .8 * rip) - 1.1 * re[1];
            g.tilt = .95 + .25 * Math.sin(sway * .7 + i);
            g.high = 1.05 - .3 * strain;
            g.hump = .28 * re[1];
            g.rise = new Vec3d(0, .55 * size * Math.sin(heave), .15 * Math.sin(sway + .3));
            g.elbow = new Vec3d(.3 * Math.sin(heave * .5 + i), size * Math.sin(heave - .9), .48 * Math.sin(sway));
            g.drop = new Vec3d(.15 * Math.sin(heave * .5 + i - 1), .45 * size * Math.sin(heave - 1.8), .35 * Math.sin(sway - .8));
            g.shove = back.multiply(.5 * jolt).add(0, .35 * jolt, 0);
            c.grip(i, g);
            c.ask.want[i] = A < end + 2;
            c.ask.pose[i] = c.built(i, c.ask.pose[i], .26, .012 * strain, c.pinned(i) * (1 - rip), GRIP_FLOOR);
            c.ask.girth[i] = 1 + .12 * strain * (.5 + .5 * Math.sin(heave - .9)) + .08 * jolt;
            c.ask.hurry[i] = .85 * strain;
        }
        // The striker: a scorpion's tail arched high behind her head, its tip aimed down the cone. It weaves like a
        // cobra's head while she breathes, side to side and up and down, lags behind her as she turns, its tip lashing,
        // and pumps forward and down into each burn.
        double live = smoothstep(A / 8) * (1 - rip);
        double sway = TAU * .42 * A / 20, weave = live * (.8 * Math.sin(sway) + .2 * Math.sin(TAU * .95 * A / 20 + 1));
        double surge = live * .6 * Math.sin(2 * sway + .6);      // a figure of eight with the weave, so it reads from every side
        double rise = live * .6 * Math.sin(TAU * .27 * A / 20 + .8), lag = Math.clamp(-c.rig.turn * 1.1, -.6, .6) * (1 - rip);
        Vec3d head = c.b(.25 + weave, 4.7 + rise - .4 * jolt, .75 + surge + .5 * jolt);
        double yaw = live * .45 * Math.sin(TAU * 1.1 * A / 20 + .5), pitch = -.55 + live * .3 * Math.sin(TAU * .8 * A / 20 + 2) - .4 * jolt;
        Vec3d aim = fr.fwd().rotateY((float) yaw).add(0, pitch, 0).normalize();
        // Its tip flexes like a scorpion's sting, curling under and straightening, and stabs at each burn.
        double flex = live * (.55 * Math.sin(TAU * .9 * A / 20 + 1.7) + .5) - .6 * jolt;
        Vec3d bend = fr.fwd().crossProduct(UP).normalize(), sting = rotate(aim, bend, -flex);
        Vec3d[] tail = c.root(c.b(.8 + .12 * weave, 2.3, -1.0 + .1 * surge), c.b(.65 + .4 * weave, 3.9 + .5 * rise, -1.55 + .35 * surge),
                c.b(.42 + .7 * weave, 5.0 + .8 * rise, -.75 + .7 * surge - .2 * jolt), head, head.add(aim.multiply(1.0)),
                head.add(aim.multiply(1.0)).add(sting.multiply(1.0)));
        for (int k = 2; k < tail.length; k++) tail[k] = c.turned(tail[k], lag * (k - 1) / (tail.length - 2));
        c.pose(2, tail, .22, .01, 0, A < end + 1);
        c.ask.hurry[2] = .7 * live;
    }

    // ── Hollow Purple ────────────────────────────────────────────────────────

    /** Where Hollow Purple's gripping arms take hold (her left, ahead), wide, one before her and one behind, before they spread. */
    private static final double[] PURPLE_O = {4.0, 2.9}, PURPLE_F = {2.3, -3.8};

    private static void purple(Ctx c) {
        double A = c.age;
        TendrilFx.Frame fr = c.fr;
        int rel = HollowPurpleSpell.TIMING.release();
        double k = Math.clamp(A / rel, 0, 1), x = (A - rel) / 2.2, kick = A > rel ? x * Math.exp(1 - x) : 0;
        double drag = smoothstep((A - rel) / 4);
        Vec3d back = fr.fwd().multiply(-1);
        Vec3d orb = c.b(0, HollowPurpleSpell.FORM_HEIGHT, .6);
        double r = HollowPurpleSpell.RADIUS / 3 * smoothstep((k - .15) / .85);
        double strain = smoothstep((A - 8) / 12) * (.45 + .55 * k) * (1 - smoothstep((A - rel - 2) / 4));
        // The heaving quickens as the orb grows, from about half a heave a second to one and a half.
        double heave = chirp(A, rel, .55, 1.6), sway = TAU * .4 * A / 20, size = (.4 + .45 * k) * (1 - .6 * drag);
        for (int i = 0; i < 2; i++) {
            double d = Math.clamp((A - 4 - 3 * i) / 6, 0, 1), drive = d * d;
            Vec3d at = c.plant(i, c.g(PURPLE_O[i], PURPLE_F[i]), drive, 2.0, A >= 14 && A < rel - 2);
            Grip g = new Grip();
            g.along = c.along(i, at);
            // Spreading: three times as it forms, each slides further out and grips again, tighter; the recoil drags
            // them back across the floor.
            double[] re = regrip(A, .45, 16 + 5 * i, 29 + 5 * i, 41 + 4 * i);
            g.spot = at.add(g.along.multiply(re[0])).add(back.multiply(1.4 * drag));
            g.hand = i == 0 ? 1 : -1;
            double h = heave + 2.4 * i, sw = sway + 1.9 * i;
            g.curl = lerp(.5 * Math.PI, (1.2 + 1.4 * k) * Math.PI, c.gripped(i)) * (1 - .55 * kick) - 1.1 * re[1];
            g.tilt = .9 + .2 * Math.sin(sw * .7 + i);
            g.high = 1.1 - .5 * strain + .8 * kick;
            g.hump = .3 * re[1];
            g.rise = new Vec3d(0, .55 * size * Math.sin(h), .15 * Math.sin(sw + .3));
            g.elbow = new Vec3d(.3 * Math.sin(h * .5 + i), size * Math.sin(h - .9), .45 * Math.sin(sw));
            g.drop = new Vec3d(.15 * Math.sin(h * .5 + i - 1), .45 * size * Math.sin(h - 1.8), .32 * Math.sin(sw - .8));
            g.shove = back.multiply(1.2 * kick).add(0, .4 * kick, 0);
            c.grip(i, g);
            c.ask.want[i] = A < rel + 5;
            c.ask.pose[i] = c.built(i, c.ask.pose[i], .22 + .08 * k, .012 + .03 * strain * k + .1 * kick, c.pinned(i), GRIP_FLOOR);
            c.ask.girth[i] = 1 + .16 * strain * k * (.5 + .5 * Math.sin(h - .9));
            c.ask.hurry[i] = (.4 + .9 * k) * (1 - drag);
        }
        // The striker climbs her left side and feeds ink into the forming orb, its stem swaying, violet swells running
        // up it into the orb one after another; the release flings it back.
        Vec3d fling = back.multiply(1.7 * kick).add(0, .7 * kick, 0), fwd = fr.fwd(), out = fr.out();
        double live = smoothstep(A / 8) * (1 - drag);
        double sw = live * .7 * Math.sin(TAU * .45 * A / 20 + .5), so = live * .45 * Math.sin(TAU * .31 * A / 20 + 2), bob = live * .35 * Math.sin(TAU * .6 * A / 20);
        Vec3d[] feed = c.root(c.b(1.5 + .3 * so, 2.3, -.6 + .35 * sw), c.b(1.9 + so, 4.2 + bob, -.3 + sw).add(fling.multiply(.4)),
                orb.add(out.multiply(r + 1.1 + .5 * so)).add(0, .8 + .5 * bob, 0).add(fwd.multiply(.6 * sw)).add(fling.multiply(.8)),
                orb.add(out.multiply(Math.max(.35, r * .8))).add(fling));
        c.pose(2, feed, .14, .01 + .02 * k + .08 * kick, 0, A < rel + 4);
        c.ask.hurry[2] = .5 * live;
        if (A >= 4 && A < rel) {
            c.ask.ripple[2] = (A - 4) / 14;
            c.ask.swell[2] = 2.2 * smoothstep((A - 4) / 3) * (1 - smoothstep((A - (rel - 4)) / 4));
        }
        if (A < rel) c.ask.feed[2] = orb;
    }

    // ── Slam and Sweep: the two not striking ─────────────────────────────────

    /** Where the Slam's counterweights plant (her left, ahead): behind her, against the strike. */
    private static final double[] SLAM_O = {3.4, 1.6}, SLAM_F = {-1.6, -3.6};

    private static void slam(Ctx c) {
        double A = c.age;
        int rel = SlamSpell.TIMING.release();
        double j = A - rel;
        double lean = smoothstep((A - 5) / 15) * (1 - smoothstep((j - 4) / 6));
        double jolt = j >= 0 ? Math.sin(Math.min(Math.PI, j * .8)) * Math.exp(-j / 4) : 0, hit = j >= 0 ? Math.exp(-j / 3) : 0;
        Vec3d fwd = c.fr.fwd();
        for (int i = 0; i < 2; i++) {
            double d = Math.clamp((A - 2 - 2 * i) / 5, 0, 1), drive = d * d;
            Vec3d tip = c.plant(i, c.g(SLAM_O[i], SLAM_F[i]), drive, 1.8, A >= 9 && A < rel - 1);
            // Leaning back as the striker rears; yanked forward as it lands.
            c.anchor(i, tip, drive, lean, .5, fwd.multiply(-.5 * lean + 1.0 * jolt), 0);
            c.ask.want[i] = A < rel + 6;
            c.ask.pose[i] = c.built(i, c.ask.pose[i], .12 - .05 * lean, .012 + .035 * lean + .1 * hit, c.pinned(i));
        }
    }

    private static void sweep(Ctx c) {
        double A = c.age;
        int rel = SweepSpell.TIMING.release();
        double swing = smoothstep((A - rel) / SweepSpell.SWING), pass = Math.sin(Math.PI * swing);
        // Lifted high out of the low swing's path, turning back against it as it passes.
        Vec3d[][] up = {
            c.root(c.b(1.4, 2.4, -.2), c.b(2.2, 3.7, -.65), c.b(2.45, 4.85, -.3), c.b(2.0, 5.5, .3), c.b(1.45, 5.3, .8)),
            c.root(c.b(1.05, 2.6, -1.05), c.b(1.55, 3.9, -2.05), c.b(2.25, 4.85, -2.75), c.b(3.05, 5.2, -2.55), c.b(3.4, 4.9, -1.95)),
        };
        double turn = .5 * swing, cos = Math.cos(turn), sin = Math.sin(turn);
        Vec3d w = c.fr.wound(), out = c.fr.out(), fwd = c.fr.fwd();
        for (int i = 0; i < 2; i++) {
            Vec3d[] ctrl = up[i];
            for (int k = 2; k < ctrl.length; k++) {
                Vec3d v = ctrl[k].subtract(w);
                double o = v.dotProduct(out), f = v.dotProduct(fwd);
                ctrl[k] = w.add(out.multiply(o * cos + f * sin)).add(fwd.multiply(-o * sin + f * cos)).add(0, v.y + .35 * pass * k / 6.0, 0);
            }
            c.pose(i, ctrl, .17, .01 + .02 * pass, 0, A < rel + SweepSpell.SWING + 1);
        }
    }

    // ── The ten spells, from the human hand ──────────────────────────────────

    /** How far each tentacle flares up and out while a spell forms (radians, round her forward). */
    private static final double[] FLARE = {.42, .3, .12};

    private static void spell(Ctx c) {
        double A = c.age;
        TendrilFx.Frame fr = c.fr;
        int rel = c.copy.castRelease();
        double x = (A - rel) / 2.5, kick = A > rel ? x * Math.exp(1 - x) : 0;
        Vec3d focus = c.copy.focus();
        if (focus != null) c.rig.focus = c.rig.focus == null ? focus : ease(c.rig.focus, focus, 10, c.dt);
        double speed = c.rig.vel.length();
        Vec3d away = speed < 1e-3 ? Vec3d.ZERO : c.rig.vel.multiply(-1 / speed * Math.min(1, speed / 14) * 3.2);
        Vec3d w = fr.wound(), axis = fr.fwd(), back = fr.fwd().multiply(-1);
        for (int i = 0; i < 3; i++) {
            Vec3d[] r = c.rest[i];
            int n = r.length - 1;
            double cos = Math.cos(FLARE[i]), sin = Math.sin(FLARE[i]);
            Vec3d[] p = new Vec3d[n + 1];
            for (int k = 0; k <= n; k++) {
                Vec3d v = r[k].subtract(w);
                // Turned up and out round her forward (Rodrigues), a little longer.
                Vec3d turned = v.multiply(cos).add(axis.crossProduct(v).multiply(sin)).add(axis.multiply(axis.dotProduct(v) * (1 - cos)));
                p[k] = w.add(turned.multiply(1.05));
            }
            // The striker's tip curls toward the ink the spell gathers, beside it.
            if (i == 2 && c.rig.focus != null) {
                Vec3d reach = c.rig.focus.add(fr.out().multiply(.7)).add(0, .3, 0).subtract(p[n]);
                for (int k = 0; k <= n; k++) p[k] = p[k].add(reach.multiply(.75 * Math.pow(smoothstep((k / (double) n - .45) / .55), 1.5)));
            }
            for (int k = 0; k <= n; k++) {
                double s = k / (double) n;
                p[k] = p[k].add(back.multiply(1.5 * kick * Math.pow(s, 1.5))).add(0, .45 * kick * s, 0).add(away.multiply(s * s));
            }
            TendrilCurve.overFloor(p, fr.floor());
            for (int k = 0; k <= n; k++) p[k] = TendrilFx.clear(fr, p[k], k / (double) n);
            c.ask.pose[i] = p;
            c.ask.want[i] = A < rel + 2 || speed > 6;
        }
    }

    // ── Gripping the floor (Flame, Hollow Purple) ────────────────────────────

    /** Rings of a gripping arm: its arch down to the floor, the part lying along the floor, and its curled tip. */
    private static final int[] GRIP_RINGS = {54, 18, 24};
    /** Its control points where those parts meet: the wound, where it meets the floor, where it begins to curl, the tip. */
    private static final int[] GRIP_AT = {0, 5, 7, 15};
    /** How far along a gripping arm (0 to 1) it meets the floor: from there on it holds still in the world. */
    private static final double GRIP_FLOOR = GRIP_RINGS[0] / (double) TendrilFx.RINGS;
    /** How long a gripping arm lies along the floor before it curls, and its curl's radius where it begins. */
    private static final double LIE = 1.8, CURL_R = .55;

    /** How a gripping arm ({@link Ctx#grip}) lies and moves this frame. */
    private static final class Grip {
        /** Where its curl begins on the floor (world), and the way it lies along the floor to there (level, unit, outward). */
        Vec3d spot, along;
        /** Which way its tip curls (1 or -1), how far round (radians), and how far its curl rises off the floor (radians, 0 flat). */
        double hand = 1, curl = Math.PI, tilt = .9;
        /** How high its elbow rises over the wound, and how high the part on the floor lifts in its middle (a re-grip). */
        double high = 1, hump;
        /** Its arch's three points' heave (blocks: along its arch, up, across), and a push on all three (world: a jolt, a recoil). */
        Vec3d rise = Vec3d.ZERO, elbow = Vec3d.ZERO, drop = Vec3d.ZERO, shove = Vec3d.ZERO;
    }

    /** A jolt at each of the flame's burns, j ticks since the first and every ticks apart: 0 as it lands, 1 a tick and a half later, dying away before the next. */
    private static double burn(double j, int every) {
        if (j < 0) return 0;
        double x = (j % every) / 1.5;
        return x * Math.exp(1 - x);
    }

    /**
     * Re-grips at these ticks: at each a gripping arm slides size blocks further along over 6 ticks, its curl opening
     * and its middle lifting off the floor as it goes, then gripping again. {blocks slid so far, how open (0 to 1)}.
     */
    private static double[] regrip(double A, double size, double... at) {
        double slid = 0, open = 0;
        for (double t0 : at) {
            slid += size * smoothstep((A - t0) / 6);
            double u = (A - t0) / 10;
            if (u > 0 && u < 1) open += Math.pow(Math.sin(Math.PI * u), 2);
        }
        return new double[] {slid, open};
    }

    /** The phase (radians) of a rhythm A ticks in that quickens steadily from f0 to f1 a second over the first span ticks, then holds. */
    private static double chirp(double A, double span, double f0, double f1) {
        double a = Math.clamp(A, 0, span);
        return TAU / 20 * (f0 * a + (f1 - f0) * a * a / (2 * span) + f1 * Math.max(0, A - span));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Vec3d ease(Vec3d from, Vec3d to, double rate, double dt) { return from.add(to.subtract(from).multiply(1 - Math.exp(-rate * dt))); }

    /** An arch point's heave (along the arch, up, across) as a world offset. */
    private static Vec3d arch(Vec3d a, Vec3d b, Vec3d m) { return a.multiply(m.x).add(0, m.y, 0).add(b.multiply(m.z)); }

    /** Two poses' control points blended, point by point. */
    private static Vec3d[] blend(Vec3d[] a, Vec3d[] b, double k) {
        if (k <= 0) return a.clone();
        if (k >= 1) return b.clone();
        Vec3d[] r = new Vec3d[a.length];
        for (int j = 0; j < a.length; j++) r[j] = a[j].lerp(b[j], k);
        return r;
    }

    /** The move in hand, this frame. */
    private record Ctx(InkFrame f, FirstWizardEntity copy, TendrilFx.Frame fr, TendrilRig rig, double age, double t, double dt, Ask ask, Vec3d[][] rest) {
        /** A point of her frame from her body (her feet lifted by the float): o to her left, u up, fw ahead. */
        Vec3d b(double o, double u, double fw) { return fr.body().add(fr.out().multiply(o)).add(0, u, 0).add(fr.fwd().multiply(fw)); }

        /** A point on the floor round her: o to her left, fw ahead. */
        Vec3d g(double o, double fw) { Vec3d p = b(o, 0, fw); return new Vec3d(p.x, fr.floor() + .03, p.z); }

        /** Out from her across the floor toward a point. */
        Vec3d radial(Vec3d at) {
            Vec3d d = at.subtract(fr.body()).multiply(1, 0, 1);
            return d.lengthSquared() < 1e-6 ? fr.out() : d.normalize();
        }

        /** A pose's control points: the wound, a little out of her flank, then these. */
        Vec3d[] root(Vec3d... then) {
            Vec3d[] c = new Vec3d[then.length + 2];
            c[0] = fr.wound();
            c[1] = fr.wound().add(fr.out().multiply(.45)).add(0, .12, 0);
            System.arraycopy(then, 0, c, 2, then.length);
            return c;
        }

        /** Tentacle i's pose through these control points, alive (see {@link TendrilCurve#live}), over the floor and off her front. */
        void pose(int i, Vec3d[] ctrl, double amp, double shudder, double pinned, boolean want) {
            ask.pose[i] = built(i, ctrl == null ? null : TendrilCurve.through(ctrl, TendrilFx.RINGS), amp, shudder, pinned);
            ask.want[i] = want;
        }

        Vec3d[] built(int i, Vec3d[] p, double amp, double shudder, double pinned) { return built(i, p, amp, shudder, pinned, 1); }

        /** As above, held still from floor along it (0 to 1) when pinned: a gripping arm from where it meets the floor. */
        Vec3d[] built(int i, Vec3d[] p, double amp, double shudder, double pinned, double floor) {
            if (p == null) return null;
            if (floor >= 1) TendrilCurve.live(p, fr, amp, rig.phase[i], shudder, pinned, t, i * 2.3 + 1);
            else TendrilCurve.live(p, fr, amp, rig.phase[i], shudder, pinned, t, i * 2.3 + 1, floor - .12, .12);
            TendrilCurve.overFloor(p, fr.floor());
            for (int k = 0; k < p.length; k++) p[k] = TendrilFx.clear(fr, p[k], k / (double) (p.length - 1));
            return p;
        }

        /**
         * A gripping arm, as an octopus's holding the floor (Flame, Hollow Purple): out of the wound it rises into an
         * elbow that bulges over the floor and heaves, drops steeply onto the floor, lies along it sucker side down for
         * a couple of blocks, and curls its tip round into a grip that rises off the floor like a fern's curl and
         * tightens toward the tip. Across the arch it bends in a loose S. The part on the floor and the curl are built
         * from g's spot and its way along the floor alone, so they hold still in the world while she moves.
         */
        void grip(int i, Grip g) {
            Vec3d w = fr.wound(), u = g.along, side = u.crossProduct(UP).normalize().multiply(g.hand);
            Vec3d[] c = new Vec3d[GRIP_AT[3] + 1];
            // The curl: a spiral round a centre beside where it begins, in a plane tilted up off the floor.
            Vec3d n = side.multiply(Math.cos(g.tilt)).add(0, Math.sin(g.tilt), 0), centre = g.spot.add(n.multiply(CURL_R));
            c[7] = g.spot;
            for (int j = 1; j <= 8; j++) {
                double ph = g.curl * j / 8, r = CURL_R * Math.max(.3, 1 - .5 * ph / TAU);
                c[7 + j] = centre.add(n.multiply(-r * Math.cos(ph))).add(u.multiply(r * Math.sin(ph)));
            }
            // Lying along the floor to it; held over the floor (coming down, ripping free) its tip is the highest part.
            double up = Math.max(0, g.spot.y - (fr.floor() + .03));
            Vec3d down = g.spot.subtract(u.multiply(LIE)).add(0, -.55 * up, 0);
            c[5] = down;
            c[6] = g.spot.subtract(u.multiply(LIE * .5)).add(side.multiply(.22)).add(0, g.hump - .3 * up, 0);
            // The arch: out of the wound, up into the elbow, then steeply down onto the floor.
            Vec3d to = down.subtract(w).multiply(1, 0, 1);
            double reach = Math.max(.5, to.length());
            Vec3d a = to.lengthSquared() < 1e-6 ? fr.out() : to.normalize(), b = a.crossProduct(UP).normalize().multiply(g.hand);
            c[0] = w;
            c[1] = w.add(fr.out().multiply(.45)).add(0, .12, 0);
            c[2] = w.add(a.multiply(.24 * reach)).add(0, .5, 0).add(b.multiply(.1)).add(arch(a, b, g.rise)).add(g.shove);
            c[3] = w.add(a.multiply(.52 * reach)).add(0, g.high, 0).add(b.multiply(-.22)).add(arch(a, b, g.elbow)).add(g.shove.multiply(.7));
            c[4] = down.subtract(a.multiply(.16 * reach)).add(0, .95, 0).add(b.multiply(.2)).add(arch(a, b, g.drop)).add(g.shove.multiply(.3));
            ask.pose[i] = TendrilCurve.through(c, GRIP_AT, GRIP_RINGS);
        }

        /** The way tentacle i lies along the floor from at: out from her, kept from when it planted until it lifts again. */
        Vec3d along(int i, Vec3d at) {
            Vec3d now = radial(at);
            if (rig.plant[i] == null || !Double.isNaN(rig.stepAt[i])) { rig.grip[i] = null; return now; }
            if (rig.grip[i] == null) rig.grip[i] = now;
            return rig.grip[i];
        }

        /** How tightly tentacle i grips: 0 in the air, curling in over the 5 ticks after it plants, letting go as it steps. */
        double gripped(int i) {
            if (rig.plant[i] == null) return 0;
            if (!Double.isNaN(rig.stepAt[i])) return 1 - smoothstep((age - rig.stepAt[i]) / 3);
            return smoothstep((age - rig.plantedAt[i]) / 5);
        }

        /** A point turned round her (about the upright through her body) by angle radians, toward her left. */
        Vec3d turned(Vec3d p, double angle) { return p.subtract(fr.body()).rotateY((float) angle).add(fr.body()); }

        /**
         * An anchor: out of the wound in a long arch over toward its spot (lower and tauter as it strains), then down into
         * the floor there at a slant, like an arm driven in (drive 0 to 1 brings it down onto it). shove moves its middle
         * (a lean, a jolt, a recoil); lift raises its arch.
         */
        void anchor(int i, Vec3d tip, double drive, double strain, double pulse, Vec3d shove, double lift) {
            Vec3d w = fr.wound(), along = tip.subtract(w).multiply(1, 0, 1);
            double reach = along.length();
            Vec3d dir = reach < 1e-3 ? fr.out() : along.multiply(1 / reach);
            double up = 1 - drive, floor = Math.min(tip.y, fr.floor() + .03);
            Vec3d[] ctrl = root(
                    w.add(dir.multiply(.48 * reach)).add(0, .85 - .3 * strain + .08 * pulse * strain + .6 * up + lift, 0).add(fr.out().multiply(.15)).add(shove),
                    new Vec3d(w.x, floor, w.z).add(dir.multiply(.8 * reach)).add(0, 1.15 - .15 * strain + .6 * up + .5 * lift + (tip.y - floor), 0).add(shove.multiply(.55)),
                    tip.subtract(dir.multiply(.3)).add(0, .45 + .3 * up, 0).add(shove.multiply(.12)),
                    tip);
            ask.pose[i] = TendrilCurve.through(ctrl, TendrilFx.RINGS);
        }

        /**
         * Tentacle i's tip coming down on a spot on the floor (world): drive 0 to 1 brings it down from hover blocks over
         * where it belongs in her frame now; at 1 it plants and stays put in the world while she moves or turns. If she
         * turns far from it and step allows, it lifts and plants again where it belongs, one tentacle at a time.
         */
        Vec3d plant(int i, Vec3d ideal, double drive, double hover, boolean step) {
            // Coming in from above and outside it, reaching wide before it strikes down.
            if (drive < 1) { rig.plant[i] = null; rig.stepAt[i] = Double.NaN; return ideal.add(0, hover * (1 - drive), 0).add(radial(ideal).multiply(.8 * (1 - drive))); }
            if (rig.plant[i] == null) { rig.plant[i] = ideal; rig.plantedAt[i] = age; splash(i, ideal); }
            if (!Double.isNaN(rig.stepAt[i])) {
                double u = (age - rig.stepAt[i]) / STEP;
                if (u < 1) return rig.stepFrom[i].lerp(ideal, smoothstep(u)).add(0, 1.3 * Math.sin(Math.PI * u), 0).add(radial(ideal).multiply(.6 * Math.sin(Math.PI * u)));
                rig.plant[i] = ideal; rig.plantedAt[i] = age; rig.stepAt[i] = Double.NaN;
                splash(i, ideal);
            }
            boolean other = false;
            for (int j = 0; j < 3; j++) if (j != i && !Double.isNaN(rig.stepAt[j])) other = true;
            if (step && !other && rig.plant[i].subtract(ideal).horizontalLength() > 1.4) { rig.stepAt[i] = age; rig.stepFrom[i] = rig.plant[i]; }
            return rig.plant[i];
        }

        /** How firmly tentacle i's tip is planted: 0 in the air or stepping, easing to 1 just after it plants. */
        double pinned(int i) {
            if (rig.plant[i] == null) return 0;
            if (!Double.isNaN(rig.stepAt[i])) return 1 - smoothstep((age - rig.stepAt[i]) / 1.5);
            return smoothstep((age - rig.plantedAt[i]) / 2);
        }

        /** Ink thrown up where tentacle i's tip strikes the floor. */
        void splash(int i, Vec3d at) { rig.splashAt[i] = at; rig.splashTime[i] = t; }
    }
}
