package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.spell.LeapSpell;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import com.anton.elementalwands.entity.firstwizard.spell.SlamSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Tendrils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Phase two's three huge ink tentacles, drawn every frame as smooth tubes: a ring of faces swept along each tentacle's
 * curve, shaded smoothly round it, textured from the ink atlas, with a violet line down its back and a row of pale violet
 * suckers along its underside. They are the developer's (Nov 18 to 19): octopus arms, huge, plainly growing out of her
 * left flank, moving like snakes round the floating copy. Not Space's void glass (no stars, no panes).
 *
 * <ul>
 *   <li><b>At rest</b> ({@link TendrilRest}) the three part as they leave her left flank, each in a space of its own:
 *       one hangs low and sweeps out near the floor, one reaches out and back at shoulder height, and one, the striker,
 *       rears over her shoulder and hooks forward. Slow waves travel along each, and the tips curl and quest.</li>
 *   <li><b>In every move the whole body works</b> (the developer, Nov 20): each tentacle springs from its rest pose
 *       into the pose the move asks of it ({@link TendrilMoves}: throwing her into the Leap and slapping down round its
 *       landing, gripping the floor like an octopus's arms for the Flame and Hollow Purple, counterweight and clearance
 *       for the Slam and Sweep, flaring for the human hand's spells) and back, each on its own spring that overshoots a
 *       little and settles. The blend is of how each tentacle bends along its length ({@link TendrilCurve#blend}), so
 *       it sweeps from one pose into a very different one rather than folding into a loop near her. Nothing holds
 *       still: held poses keep their waves and heave, and straining ones shudder. Planted tips stay put in the world
 *       while she moves.</li>
 *   <li><b>A Slam or Sweep</b>: the striker rears, the violet ripple runs to its tip (the tell), it strikes and lies
 *       there, then springs back into its loop, on a spring of its own over whatever the other two do.</li>
 *   <li><b>The change:</b> from {@link PhaseChange#GROW_FROM} they burst out of her left flank and unfurl to full size.</li>
 * </ul>
 *
 * Every point comes from the move's own clock with the partial tick (and the effects' marks, eased), never from a value
 * the server sets once a tick. The motion is the client's own (a per-copy {@link TendrilRig}); what the tentacles strike
 * is the server's.
 */
final class TendrilFx {
    private TendrilFx() {}

    /** Rings along each tentacle, and faces round each ring. */
    static final int RINGS = 96, SIDES = 10;
    /** Every spring (the strike's and each tentacle's pose): stiffness and damping ratio (below 1, so it overshoots a little and settles). */
    private static final double STIFF = 160, DAMP = .55;
    /** Seconds a tentacle takes to pass from one move's pose to the next's when the moves follow straight on. */
    private static final double FADE = .25;
    /** How far a feeding swell reaches either side of its middle (0 to 1 along the tentacle). */
    private static final double FEED_WIDTH = .09;
    /** The rest pose: the game's own unless a design study picks another (see {@link TendrilPose#pick}). */
    private static final TendrilPose POSE = TendrilPose.pick(new TendrilRest());

    private static final Map<Integer, TendrilRig> RIGS = new HashMap<>();

    static void draw(InkFrame f, FirstWizardEntity copy) {
        float delta = f.delta();
        if (!copy.torn()) { RIGS.remove(copy.getId()); return; }
        Vec3d feet = copy.getLerpedPos(delta);
        double floor = InkKit.floorBelow(f.world(), feet.add(0, .5, 0));
        double t = f.now(), seed = copy.getId() * 5.3;
        if (RIGS.size() > 32) RIGS.clear();
        TendrilRig rig = RIGS.computeIfAbsent(copy.getId(), id -> new TendrilRig());
        double dt = !rig.started ? 0 : Math.clamp(t - rig.time, 0, .1);
        if (!rig.started) for (int i = 0; i < 3; i++) { rig.phase[i] = rnd(seed + i) * 7; rig.quest[i] = rnd(seed + i * 3) * 7; }
        rig.started = true;
        rig.time = t;

        double yaw = WizardBody.yaw(copy, delta), side = WizardBody.side(copy), lift = WizardBody.lift(copy, delta);
        Vec3d fwd = WizardBody.forward(yaw), out = WizardBody.left(yaw).multiply(side);
        Vec3d wound = WizardBody.wound(copy, delta), body = feet.add(0, lift, 0);
        String move = copy.castMove();
        double age = copy.castAge(delta);
        boolean changing = copy.phase() == FirstWizardEntity.CHANGING;
        double change = changing ? copy.phaseAge(delta) : 1e9;
        Frame fr = new Frame(body, wound, fwd, out, floor);

        // Her velocity, eased (a blink's jump is not a velocity), for the arms to stream behind a dash.
        if (rig.lastBody != null && dt > 0) {
            Vec3d step = body.subtract(rig.lastBody);
            rig.vel = step.length() > 3 ? Vec3d.ZERO : ease(rig.vel, step.multiply(1 / dt), 10, dt);
        }
        rig.lastBody = body;
        // How fast she turns, eased, for an arm to lag behind her turn.
        if (rig.lastFwd != null && dt > 0) {
            double turned = Math.atan2(rig.lastFwd.crossProduct(fwd).y, rig.lastFwd.dotProduct(fwd));
            rig.turn = ease(rig.turn, Math.abs(turned) > .8 ? 0 : turned / dt, 6, dt);
        }
        rig.lastFwd = fwd;
        // A new move (or the end of one): its plants and targets start afresh.
        if (!move.equals(rig.move) || (age >= 0 && age < rig.age - .5)) rig.begin(t);
        rig.move = move;
        rig.age = age;

        // ── The strike, from the move's own clock (with the partial tick) ────
        Strike strike = strike(f, copy, fr, move, age, changing, change, rig, dt);
        if (strike != null) rig.last = strike;
        double[] strikeSpring = {rig.lash, rig.lashSpeed};
        spring(strikeSpring, strike != null ? 1 : 0, dt);
        rig.lash = strikeSpring[0];
        rig.lashSpeed = strikeSpring[1];

        // ── Ripple: the tell ─────────────────────────────────────────────────
        double ripple = Double.NaN;
        boolean rippleAll = false;
        if (changing) ripple = window(change, PhaseChange.RIPPLE_FROM, PhaseChange.RIPPLE_TO);
        else if (move.equals("slam")) ripple = window(age, SlamSpell.RIPPLE_FROM, SlamSpell.RIPPLE_TO);
        else if (move.equals("sweep")) ripple = window(age, SweepSpell.RIPPLE_FROM, SweepSpell.RIPPLE_TO);
        else if (move.equals("leap")) { ripple = window(age, LeapSpell.RIPPLE_FROM, LeapSpell.RIPPLE_TO); rippleAll = true; }

        // ── The tentacles: their rest pose, never held still ─────────────────
        double burst = changing ? change - PhaseChange.GROW_FROM : 1e9;
        if (burst < 0) return;
        for (int i = 0; i < 3; i++) {
            rig.phase[i] += dt * (POSE.crawl(i) + rig.hurry[i]) * TAU;
            rig.quest[i] += dt * POSE.quest(i) * TAU;
        }
        Vec3d[][] rest = new Vec3d[3][];
        double[] length = new double[3];
        for (int i = 0; i < 3; i++) {
            // Bursting out: each shoots to full length in a few ticks, curled tight, and unfurls with a thrash.
            double b = burst - i * 2;
            if (b <= 0) continue;
            length[i] = b > 1e8 ? 1 : 1 - Math.exp(-b / 3.2);
            double unfurl = b > 1e8 ? 1 : Math.clamp(1 - Math.exp(-b / 9) * Math.cos(b / 3.5), 0, 1.15);
            rest[i] = new Vec3d[RINGS + 1];
            for (int k = 0; k <= RINGS; k++) rest[i][k] = POSE.rest(fr, i, k / (double) RINGS * length[i], rig.phase[i], rig.quest[i], 0, unfurl, b);
        }

        // ── What the move in hand asks of each, blended in and out on its own spring ──
        TendrilMoves.Ask ask = changing || rest[0] == null || rest[1] == null || rest[2] == null
                ? new TendrilMoves.Ask() : TendrilMoves.ask(f, copy, fr, rig, move, age, rest, t, dt);
        for (int i = 0; i < 3; i++) {
            rig.hurry[i] = ease(rig.hurry[i], ask.hurry[i], 6, dt);
            double[] w = {rig.w[i], rig.wSpeed[i]};
            spring(w, ask.want[i] ? 1 : 0, dt);
            rig.w[i] = w[0];
            rig.wSpeed[i] = w[1];
            if (ask.pose[i] != null) {
                // Straight on from another move's pose: it passes from that one into this one.
                if (rig.from[i] != null) {
                    double k = smoothstep((t - rig.fadeAt[i]) / FADE);
                    ask.pose[i] = TendrilCurve.blend(world(fr, rig.from[i]), ask.pose[i], k);
                    if (k >= 1) rig.from[i] = null;
                }
                rig.held[i] = local(fr, ask.pose[i]);
            } else if (Math.abs(rig.w[i]) < .002 && Math.abs(rig.wSpeed[i]) < .01) rig.held[i] = null;
        }

        for (int i = 0; i < 3; i++) {
            if (rest[i] == null) continue;
            Vec3d[] pose = ask.pose[i] != null ? ask.pose[i] : rig.held[i] != null ? world(fr, rig.held[i]) : null;
            double w = pose != null ? rig.w[i] : 0, lash = i == 2 && rig.last != null ? rig.lash : 0;
            // Into the pose and out of it by its bends, so it sweeps across rather than folding into a loop.
            Vec3d[] points = Math.abs(w) > .002 ? TendrilCurve.blend(rest[i], pose, w) : rest[i].clone();
            // The striker's strike, over whatever else it does.
            if (Math.abs(lash) > .002) {
                Vec3d[] struck = new Vec3d[RINGS + 1];
                for (int k = 0; k <= RINGS; k++) struck[k] = strikePoint(fr, rig.last, k / (double) RINGS * length[i]);
                points = TendrilCurve.blend(points, struck, lash);
            }
            // Partway between two poses, still off her front (each pose is already).
            double between = Math.max(4 * w * (1 - w), 4 * lash * (1 - lash));
            if (between > 1e-3) for (int k = 0; k <= RINGS; k++) points[k] = points[k].lerp(clear(fr, points[k], k / (double) RINGS), Math.min(1, between));
            TendrilCurve.overFloor(points, floor);
            boolean told = (rippleAll || i == 2) && !Double.isNaN(ripple);
            double own = Double.isNaN(ask.ripple[i]) || Math.abs(w) < .3 ? Double.NaN : ask.ripple[i];
            double girth = 1 + (ask.girth[i] - 1) * Math.clamp(w, 0, 1);
            tube(f, points, length[i], seed + i * 31, told ? ripple : own, girth, told ? 0 : ask.swell[i] * Math.clamp((Math.abs(w) - .3) / .4, 0, 1));
            if (ask.feed[i] != null && w > .5) InkKit.strand(f.ink(), points[RINGS], ask.feed[i], t, 5, .15, seed + 90 + i);
            // Ink thrown up where a tip struck the floor.
            if (rig.splashAt[i] != null && t - rig.splashTime[i] < 1.3)
                InkKit.splash(f.ink(), rig.splashAt[i].add(0, .1, 0), floor, t - rig.splashTime[i], f.embers() ? 9 : 4, 2.4, .12, seed + i * 13 + Math.floor(rig.splashTime[i] * 7));
        }
        wound(f, fr, seed, t, burst);
    }

    /** Where she is this frame: her body (feet lifted by the float), the wound, her forward, her left (out), the floor's height. */
    record Frame(Vec3d body, Vec3d wound, Vec3d fwd, Vec3d out, double floor) {}

    /** A strike's shape: its tip, and two points the tentacle passes on its way there (a 3D curve, not a flat one). */
    record Strike(Vec3d tip, Vec3d bend, Vec3d near) {}

    private static double ease(double from, double to, double rate, double dt) { return from + (to - from) * (1 - Math.exp(-rate * dt)); }

    private static Vec3d ease(Vec3d from, Vec3d to, double rate, double dt) { return from.add(to.subtract(from).multiply(1 - Math.exp(-rate * dt))); }

    /**
     * A spring {value, speed} toward a target over dt seconds, sub-stepped so a slow frame cannot blow it up: it
     * overshoots a little and settles.
     */
    private static void spring(double[] v, double target, double dt) {
        for (double left = dt; left > 1e-6; left -= .008) {
            double h = Math.min(.008, left);
            double accel = STIFF * (target - v[0]) - 2 * DAMP * Math.sqrt(STIFF) * v[1];
            v[1] += accel * h;
            v[0] += v[1] * h;
        }
        v[0] = Math.clamp(v[0], -.1, 1.2);
    }

    /** Points in her frame (her left, up, ahead, from her body), from the world. */
    private static Vec3d[] local(Frame fr, Vec3d[] p) {
        Vec3d[] l = new Vec3d[p.length];
        for (int k = 0; k < p.length; k++) {
            Vec3d d = p[k].subtract(fr.body);
            l[k] = new Vec3d(d.dotProduct(fr.out), d.y, d.dotProduct(fr.fwd));
        }
        return l;
    }

    /** Points of her frame, in the world. */
    private static Vec3d[] world(Frame fr, Vec3d[] l) {
        Vec3d[] p = new Vec3d[l.length];
        for (int k = 0; k < l.length; k++) p[k] = fr.body.add(fr.out.multiply(l[k].x)).add(0, l[k].y, 0).add(fr.fwd.multiply(l[k].z));
        return p;
    }

    /** 0 to 1 through a ripple's window, or NaN outside it. */
    private static double window(double age, int from, int to) {
        if (age < from || age > to) return Double.NaN;
        return (age - from) / Math.max(1, to - from);
    }

    /** The copy's live effect of a kind, or null. */
    static FirstWizardEffectEntity effect(InkFrame f, FirstWizardEntity copy, String kind) {
        for (Entity e : f.world().getEntities())
            if (e instanceof FirstWizardEffectEntity fx && !fx.isRemoved() && fx.ownerId() == copy.getId() && fx.kind().equals(kind)) return fx;
        return null;
    }

    /**
     * Keeps her readable: below the top of her head and not behind her, nothing comes nearer her left side than a
     * little way out from it, so no coil crosses in front of her torso or face or down her right side. Behind her
     * back and over her head they may pass. Smooth, so a coil slides round her rather than bending sharply.
     */
    static Vec3d clear(Frame fr, Vec3d at, double s) {
        Vec3d rel = at.subtract(fr.body);
        double o = rel.dotProduct(fr.out), fw = rel.dotProduct(fr.fwd), h = rel.y;
        double inFront = smoothstep((fw + .7) / .5), below = 1 - smoothstep((h - 2.05) / .45), fromRoot = smoothstep((s - .05) / .1);
        double w = inFront * below * fromRoot;
        if (w <= 1e-4) return at;
        double edge = .8, k = .35;
        double pushed = (o + edge + Math.sqrt((o - edge) * (o - edge) + k * k)) / 2;      // a smooth max(o, edge)
        return at.add(fr.out.multiply((pushed - o) * w));
    }

    /**
     * Where the striking tentacle is at s, along a smooth 3D curve: out of her left flank and up, through the strike's
     * bend, past its near point, to its tip. A gentle swing sideways gives it depth.
     */
    private static Vec3d strikePoint(Frame fr, Strike st, double s) {
        Vec3d p0 = fr.wound, p1 = fr.wound.add(fr.out.multiply(1.2)).add(0, 1.1, 0);
        Vec3d[] c = {p0, p0, p1, st.bend(), st.near(), st.tip(), st.tip()};
        double x = s * (c.length - 3);
        int seg = Math.min(c.length - 4, (int) Math.floor(x));
        double u = x - seg;
        return catmull(c[seg], c[seg + 1], c[seg + 2], c[seg + 3], u);
    }

    private static Vec3d catmull(Vec3d a, Vec3d b, Vec3d c, Vec3d d, double u) {
        double u2 = u * u, u3 = u2 * u;
        return b.multiply(2).add(c.subtract(a).multiply(u)).add(a.multiply(2).subtract(b.multiply(5)).add(c.multiply(4)).subtract(d).multiply(u2))
                .add(d.subtract(a).add(b.multiply(3)).subtract(c.multiply(3)).multiply(u3)).multiply(.5);
    }

    /**
     * The strike in hand, on this client's own clock, or null. Its points move smoothly between frames: they follow the
     * move's timing with the partial tick and the effect's marks, never a value the server sets once a tick.
     */
    private static Strike strike(InkFrame f, FirstWizardEntity copy, Frame fr, String move, double age, boolean changing, double change, TendrilRig rig, double dt) {
        if (move.equals("slam") && age >= 0) {
            FirstWizardEffectEntity line = effect(f, copy, SlamSpell.KIND);
            if (line == null) return null;
            Vec3d dir = line.dir().multiply(1, 0, 1);
            if (dir.lengthSquared() < 1e-6) dir = fr.fwd;
            dir = dir.normalize();
            // The aim eases after the line as it follows its target, so the reared tentacle never jumps a tick.
            Vec3d end = line.auxPoint();
            rig.aim = rig.aim == null ? end : rig.aim.add(end.subtract(rig.aim).multiply(1 - Math.exp(-dt * 18)));
            end = new Vec3d(rig.aim.x, fr.floor + .1, rig.aim.z);
            int release = SlamSpell.TIMING.release(), free = SlamSpell.TIMING.free();
            if (age >= free - 6) return null;
            Vec3d reared = fr.wound.add(0, 2.4 + .4 * smoothstep(age / 12), 0).add(fr.out.multiply(1.4)).subtract(dir.multiply(1.2));
            double k = Math.clamp((age - (release - 3)) / 3, 0, 1);
            k = k * k;      // accelerating down
            Vec3d tip = reared.lerp(end, k).add(0, 1.6 * Math.sin(Math.PI * k), 0);
            Vec3d along = end.subtract(fr.wound).multiply(1, 0, 1);
            Vec3d bend = k < 1 ? fr.wound.add(fr.out.multiply(1.6)).add(0, 2.6 - k, 0).add(dir.multiply(.6 + 2 * k))
                    : new Vec3d(fr.wound.x, fr.floor + .9, fr.wound.z).add(along.multiply(.3)).add(fr.out.multiply(.4));
            Vec3d near = k < 1 ? reared.lerp(tip, .5).add(0, .9 * (1 - k), 0).add(fr.out.multiply(.6))
                    : new Vec3d(fr.wound.x, fr.floor + .25, fr.wound.z).add(along.multiply(.66));
            return new Strike(tip, bend, near);
        }
        FirstWizardEffectEntity sweep = move.equals("sweep") ? effect(f, copy, SweepSpell.KIND) : changing ? effect(f, copy, PhaseChange.RING_KIND) : null;
        boolean sweeping = move.equals("sweep") ? age >= 0 && age < SweepSpell.TIMING.release() + SweepSpell.SWING + 4
                : changing && change >= PhaseChange.TELL && change < PhaseChange.SWEEP_FROM + PhaseChange.SWEEP + 4;
        if (sweeping) {
            double s = sweep != null && sweep.stage() >= 1 ? sweep.auxPoint().x : 1;
            double aim = sweep != null && sweep.stage() >= 1 ? Tendrils.angle(sweep.dir()) : Tendrils.angle(fr.fwd);
            double angle, lifted;
            if (move.equals("sweep")) {
                angle = SweepSpell.angleAt(age, aim, s);
                lifted = 1 - smoothstep((age - (SweepSpell.TIMING.release() - 4)) / 4);
            } else {
                angle = PhaseChange.angleAt(change, aim, s);
                lifted = 1 - smoothstep((change - (PhaseChange.SWEEP_FROM - 4)) / 4);
            }
            // The sweep's direction of travel, so the tentacle trails behind its tip as it swings.
            double ahead = (move.equals("sweep") ? SweepSpell.angleAt(age + 1, aim, s) : PhaseChange.angleAt(change + 1, aim, s)) - angle;
            Vec3d centre = new Vec3d(fr.body.x, fr.floor, fr.body.z);
            Vec3d tip = centre.add(Tendrils.dir(angle).multiply(Tendrils.REACH)).add(0, .14 + 1.4 * lifted, 0);
            double trail = -Math.signum(ahead) * Math.min(.5, Math.abs(ahead) * 3 + .12);
            Vec3d near = centre.add(Tendrils.dir(angle + trail * .6).multiply(Tendrils.REACH * .62)).add(0, .3 + 1.2 * lifted, 0);
            Vec3d bend = centre.add(Tendrils.dir(angle + trail).multiply(Tendrils.REACH * .28)).add(0, 1.4 + .6 * lifted, 0).add(fr.out.multiply(.6));
            return new Strike(tip, bend, near);
        }
        rig.aim = null;
        return null;
    }

    /**
     * One tentacle as a smooth tube: a ring of faces swept along its points on a twist-free frame, tapering from the
     * root to the tip, each corner shaded by which way it faces; a violet line down its back, pale violet suckers along
     * its underside, and a ripple (0 to 1 along it) that swells and glows violet as it runs to the tip. length below 1
     * is a tentacle not yet full length. swell above 0 makes the ripple a feeding one instead of the tell: ripple then
     * counts the swells sent from the root (each reaches the tip as it passes the next whole number, a new one leaving
     * half way), and each is broad and swells it to 1 + swell times its girth, so it reads from across the floor.
     */
    private static void tube(InkFrame f, Vec3d[] p, double length, double seed, double ripple, double girth, double swell) {
        AtlasMesh m = f.ink();
        int n = p.length - 1;
        Vec3d[] tangents = new Vec3d[n + 1], normals = new Vec3d[n + 1];
        for (int k = 0; k <= n; k++) {
            Vec3d d = p[Math.min(n, k + 1)].subtract(p[Math.max(0, k - 1)]);
            tangents[k] = d.lengthSquared() < 1e-12 ? (k > 0 ? tangents[k - 1] : UP) : d.normalize();
        }
        // A frame carried along the curve without twisting: each normal is the last one, turned with the tangent.
        Vec3d first = UP.subtract(tangents[0].multiply(UP.dotProduct(tangents[0])));
        normals[0] = first.lengthSquared() < 1e-6 ? X : first.normalize();
        for (int k = 1; k <= n; k++) {
            Vec3d nk = normals[k - 1].subtract(tangents[k].multiply(normals[k - 1].dotProduct(tangents[k])));
            normals[k] = nk.lengthSquared() < 1e-8 ? normals[k - 1] : nk.normalize();
        }
        double[] radius = new double[n + 1];
        int[] rowAt = new int[n + 1];
        for (int k = 0; k <= n; k++) {
            double s = k / (double) n;
            double r = POSE.radius(s) * girth;
            r *= .45 + .55 * Math.min(1, length * 1.2);
            int row = InkKit.INK;
            if (!Double.isNaN(ripple) && swell > 0) {
                double near = Math.abs(s - fract(ripple));
                if (ripple >= .5) near = Math.min(near, Math.abs(s - fract(ripple - .5)));
                double bump = near < FEED_WIDTH ? swell * (.5 + .5 * Math.cos(Math.PI * near / FEED_WIDTH)) : 0;
                r *= 1 + bump;
                row = bump > .5 ? InkKit.VIOLET : bump > .15 ? InkKit.MAGENTA : InkKit.INK;
            } else if (!Double.isNaN(ripple)) {
                double near = Math.abs(s - ripple);
                if (near < .07) { r *= 1 + .45 * (1 - near / .07); row = near < .03 ? InkKit.VIOLET : InkKit.MAGENTA; }
            }
            radius[k] = r;
            rowAt[k] = row;
        }
        double frameT = f.now() * 6 + rnd(seed) * 4;
        int frame = Math.floorMod((int) Math.floor(frameT), 4);
        Vec3d[] corners = new Vec3d[4];
        double[] us = new double[4], vs = new double[4], tones = new double[4];
        for (int k = 0; k < n; k++) {
            Vec3d n0 = normals[k], b0 = tangents[k].crossProduct(n0).normalize();
            Vec3d n1 = normals[k + 1], b1 = tangents[k + 1].crossProduct(n1).normalize();
            double v0 = k % 2, v1 = 1 - v0;      // mirrored along its length, so the texture meets itself
            for (int j = 0; j < SIDES; j++) {
                double a0 = TAU * j / SIDES, a1 = TAU * (j + 1) / SIDES;
                Vec3d d00 = n0.multiply(Math.cos(a0)).add(b0.multiply(Math.sin(a0))), d01 = n0.multiply(Math.cos(a1)).add(b0.multiply(Math.sin(a1)));
                Vec3d d10 = n1.multiply(Math.cos(a0)).add(b1.multiply(Math.sin(a0))), d11 = n1.multiply(Math.cos(a1)).add(b1.multiply(Math.sin(a1)));
                corners[0] = p[k].add(d00.multiply(radius[k]));
                corners[1] = p[k + 1].add(d10.multiply(radius[k + 1]));
                corners[2] = p[k + 1].add(d11.multiply(radius[k + 1]));
                corners[3] = p[k].add(d01.multiply(radius[k]));
                double u0 = j % 2, u1 = 1 - u0;
                us[0] = u0; us[1] = u0; us[2] = u1; us[3] = u1;
                vs[0] = v0; vs[1] = v1; vs[2] = v1; vs[3] = v0;
                tones[0] = tone(d00); tones[1] = tone(d10); tones[2] = tone(d11); tones[3] = tone(d01);
                // Its back (the face nearest its frame's normal) carries the violet line.
                int row = rowAt[k] != InkKit.INK ? rowAt[k] : j == 0 ? InkKit.RIM : (j + k) % 11 == 0 ? InkKit.GLOSS : InkKit.INK;
                m.quad(corners, us, vs, tones, row, frame, InkKit.glow(row), 1);
            }
            // Pale violet suckers along its underside, from a little way out to near the tip.
            double s = k / (double) n;
            if (k % 3 == 0 && s > .2 && s < .93 && radius[k] > .035) {
                Vec3d under = n0.multiply(-1);
                double size = radius[k] * .55;
                Vec3d[] axes = {b0, under, tangents[k]};
                InkKit.cube(m, p[k].add(under.multiply(radius[k] * .92)), size, size * .3, size, InkKit.VIOLET, seed + k, axes, 1);
            }
        }
    }

    /** A corner's baked shade, as a box face's: lit from above, a little from the south and east. */
    private static double tone(Vec3d n) { return .78 + .22 * n.y + .07 * n.z + .03 * n.x; }

    /**
     * The root: a swell of ink at her left flank the tentacles leave from, and as they burst out a splash of ink thrown
     * from it. burst is ticks since they burst out (huge once they are long out).
     */
    private static void wound(InkFrame f, Frame fr, double seed, double t, double burst) {
        AtlasMesh m = f.ink();
        Vec3d[] axes = yawAxes(Math.atan2(fr.fwd.x, fr.fwd.z));
        for (int i = 0; i < 3; i++) {
            double k = i / 2.0, wob = .025 * Math.sin(t * 3.1 + i * 1.7 + seed);
            Vec3d p = fr.wound.add(fr.out.multiply(.08 + .04 * Math.sin(t * 2 + i))).add(0, .1 - k * .45, 0);
            double s = .42 - k * .1 + wob;
            InkKit.cube(m, p, s * .9, s, s * 1.1, i == 0 ? InkKit.RIM : rnd(seed + i) < .3 ? InkKit.GLOSS : InkKit.INK, seed + i * 2.1, axes, 1);
        }
        double life = 1.4, a = (t + rnd(seed)) % life;
        InkKit.drip(m, fr.wound.add(fr.out.multiply(.2)).add(0, -.3, 0), fr.floor, a, life, .1, seed + Math.floor((t + rnd(seed)) / life));
        if (burst < 40) InkKit.splash(m, fr.wound.add(fr.out.multiply(.3)), fr.floor, burst / 20, 22, 6, .24, seed + 60);
    }
}
