package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.spell.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Cast choreography in world space. Both windup and contact can be sampled directly from the server's cast clock. */
final class TendrilMoves {
    /** Four hinged spans, each drawn as two successively smaller blocks. */
    record Shape(Vec3d shoulder, Vec3d elbow, Vec3d wrist, Vec3d tip) {
        Vec3d point(Vec3d root, double s) {
            double part = s * 4;
            if (part <= 1) return root.lerp(shoulder, part);
            if (part <= 2) return shoulder.lerp(elbow, part - 1);
            if (part <= 3) return elbow.lerp(wrist, part - 2);
            return wrist.lerp(tip, part - 3);
        }
        Shape toward(Shape other, double k) {
            return new Shape(shoulder.lerp(other.shoulder, k), elbow.lerp(other.elbow, k),
                    wrist.lerp(other.wrist, k), tip.lerp(other.tip, k));
        }
    }

    private final TendrilFx.Frame body;
    private final Vec3d feet, forward, left;
    private final Move move;
    private final boolean fullSweep;
    private final double age, release, free;
    private final FirstWizardEffectEntity effect;

    TendrilMoves(InkFrame f, FirstWizardEntity copy, TendrilFx.Frame body, double change) {
        this.body = body;
        feet = copy.getLerpedPos(f.delta());
        fullSweep = change >= PhaseChange.TELL;
        move = fullSweep ? Move.SWEEP : Move.byId(copy.castMove());
        age = fullSweep ? change - PhaseChange.TELL : copy.castAge(f.delta());
        release = fullSweep ? PhaseChange.SWEEP_FROM - PhaseChange.TELL : copy.castRelease();
        free = fullSweep ? PhaseChange.TIMING.free() - PhaseChange.TELL : copy.castFree();

        // Only these moves need a world-space target independent of where the drawn skin is looking.
        String kind = fullSweep ? PhaseChange.RING_KIND : move == null ? null : switch (move) {
            case SLAM, SWEEP, LEAP, FLAME, WAYLAY_DASH, FAULTLINE, STONE_WALL -> move.id;
            case HOLLOW_PURPLE -> HollowPurpleSpell.LANE_KIND;
            default -> null;
        };
        effect = kind == null ? null : current(f, copy, kind, age);
        Vec3d aim = body.fwd();
        if (effect != null && move != Move.STONE_WALL && move != Move.LEAP) {
            Vec3d dir = effect.dir().multiply(1, 0, 1);
            if (dir.lengthSquared() > .001) aim = dir.normalize();
        }
        forward = aim;
        left = new Vec3d(aim.z, 0, -aim.x);
    }

    private static FirstWizardEffectEntity current(InkFrame f, FirstWizardEntity copy, String kind, double age) {
        for (Entity entity : f.world().getEntities()) {
            if (entity instanceof FirstWizardEffectEntity fx && fx.ownerId() == copy.getId() && !fx.isRemoved()
                    && fx.kind().equals(kind) && Math.abs(fx.age(f.delta()) - age) < 2) return fx;
        }
        return null;
    }

    double age() { return move == null ? -1 : age; }

    double weight(int arm, double s) {
        // The socket sets off first. Stay committed throughout contact; the tips can finish recovering after free.
        return smoothstep(age / (5 + arm * .65 + s * 4));
    }

    /** A narrow flash travels along the black links, then lingers at the tip through release. */
    double charge(int arm, double s) {
        if (move == null || age < 0) return 0;
        double commit = switch (move) {
            case SKY_SHEAR -> SkyShearSpell.leaves(arm);
            case SLAM -> SlamSpell.TIMING.aimLock();
            case HOLLOW_PURPLE -> HollowPurpleSpell.TIMING.aimLock();
            default -> release;
        };
        double load = clamp(age / Math.max(1, commit - 4));
        double pulse = 1 - smoothstep(Math.abs(load - s) / .20);
        double tip = smoothstep((s - .80) / .20) * smoothstep((load - .68) / .30);
        return Math.max(pulse, tip) * weight(arm, s) * smoothstep((free - age) / 6);
    }

    Shape pose(int arm) {
        if (move == null || age < 0) return null;
        return switch (move) {
            case SLAM -> slam();
            case SWEEP -> sweep();
            case LEAP -> leap(arm);
            case FLAME -> flame(arm);
            case HOLLOW_PURPLE -> purple(arm);
            case SKY_SHEAR -> shear(arm);
            case FIREBALL -> fireball(arm);
            case METEOR -> meteor(arm);
            case SENTRY_BLOOM -> bloom(arm);
            case GRAVITY_WELL -> well(arm);
            case FAULTLINE -> faultline(arm);
            case STONE_WALL -> wall(arm);
            case WAYLAY_DASH -> dash(arm);
            case THORNBITE -> bite(arm);
            case BLINK_RIFT -> rift(arm);
        };
    }

    /** A point relative to the entity's feet, facing the cast's aim. */
    private Vec3d p(double out, double up, double ahead) {
        return feet.add(left.multiply(out)).add(0, up, 0).add(forward.multiply(ahead));
    }

    private Shape shape(int arm, Vec3d elbow, Vec3d wrist, Vec3d tip) {
        Vec3d shoulder = body.wound().add(body.out().multiply(.68 + arm * .07))
                .add(0, (arm - 1) * .34, 0).add(body.fwd().multiply((1 - arm) * .23));
        return new Shape(shoulder, elbow, wrist, tip);
    }

    double thickness(double s) {
        if (move == Move.SLAM)
            return 1 + 1.65 * smoothstep((s - .65) / .28) * smoothstep(age / SlamSpell.TIMING.aimLock())
                    * (1 - smoothstep((age - release - 2) / 11));
        return 1;
    }

    /** Centre of the three-strand hammer. The elbow leads the fall; the tips meet the locked endpoint together. */
    private Shape slam() {
        Vec3d end = effect == null ? p(0, 0, SlamSpell.LENGTH) : effect.auxPoint();
        Vec3d shaft = end.subtract(feet).multiply(1, 0, 1);
        double lift = smoothstep(age / SlamSpell.TIMING.aimLock());
        double recoil = smoothstep((age - release - 1) / 13);
        Vec3d elbow = p(1.9 - lift * .6, 3.4 + lift * 1.5, -.8);
        Vec3d wrist = p(2.7 - lift * 1.9, 4.1 + lift * 1.9, .2 - lift * .6);
        Vec3d tip = p(3.2 - lift * 3, 3.5 + lift * 2.1, 2.8);
        Vec3d shoulder = body.wound().add(body.out().multiply(.55)).add(0, .48 - recoil * .28, 0);
        return new Shape(shoulder, elbow.lerp(feet.add(shaft.multiply(.24 - .07 * recoil)).add(0, .65 + 1.5 * recoil, 0), smoothstep((age - release + 8) / 8)),
                wrist.lerp(feet.add(shaft.multiply(.65 - .22 * recoil)).add(0, .34 + 2.1 * recoil, 0), smoothstep((age - release + 6) / 6)),
                tip.lerp(end.subtract(shaft.multiply(.37 * recoil)).add(0, .26 + recoil, 0), smoothstep((age - release + 5) / 5)));
    }

    private double sweepAngle() {
        double angle = Tendrils.angle(forward);
        if (fullSweep) {
            double start = effect == null ? angle - Math.PI / 2 : angle;
            return PhaseChange.angleAt(age + PhaseChange.TELL, start, SweepSpell.woundSide());
        }
        return SweepSpell.angleAt(age, angle, SweepSpell.woundSide());
    }

    private double contactEnd() {
        if (move == Move.SLAM) return release;
        return release + (fullSweep ? PhaseChange.SWEEP : SweepSpell.SWING);
    }

    private Shape sweep() {
        double load = smoothstep(age / (release - 2));
        double coast = smoothstep((age - contactEnd()) / 10);
        Vec3d ray = Tendrils.dir(sweepAngle() + SweepSpell.woundSide() * .30 * coast);
        Vec3d side = new Vec3d(ray.z, 0, -ray.x);
        double curl = 1 - load + coast;
        Vec3d shoulder = body.wound().add(body.out().multiply(.55)).add(ray.multiply(.25)).add(0, -.38, 0);
        return new Shape(shoulder, feet.add(ray.multiply(Tendrils.INNER)).add(0, .55 + curl * 1.2, 0),
                feet.add(ray.multiply(Tendrils.REACH * (.60 - .13 * curl))).add(0, .39 + curl * 1.4, 0),
                feet.add(ray.multiply(Tendrils.REACH * (.48 + .52 * load - .25 * coast)))
                        .add(side.multiply(-1.7 * curl)).add(0, .28 + 2.1 * curl, 0));
    }

    /** Wrap three visibly separate square strands around the same strike, with a moving shoulder-to-tip twist. */
    Vec3d point(Shape shape, int arm, double s) {
        Vec3d root = body.wound(), center = shape.point(root, s);
        if (move != Move.SLAM && move != Move.SWEEP) {
            // Tension travels through the supporting joints, leaving the authored socket and spell contact tip exact.
            double bend = Math.sin(Math.PI * s);
            double wave = age * .22 + arm * TAU / 3 - 3.8 * s;
            return center.add(body.out().multiply(.30 * bend * Math.sin(wave)))
                    .add(0, .22 * bend * Math.cos(wave), 0);
        }
        double tie = smoothstep((age - s * 3 - arm * .55) / (release - 9));
        double peel = smoothstep((age - contactEnd() - 1 - 3 * s) / 11);
        double turns = arm / 3.0 + s * (.50 + .68 * tie) + .32 * tie - .30 * peel;
        double radius = (.14 + .19 * (1 - s) + .65 * (1 - tie) + .66 * peel) * smoothstep(s / .26);
        if (move == Move.SLAM) radius += .11 * smoothstep((s - .65) / .30) * tie * (1 - peel);
        Vec3d guide = left;
        if (move == Move.SWEEP) {
            Vec3d ray = Tendrils.dir(sweepAngle());
            guide = new Vec3d(ray.z, 0, -ray.x);
        }
        // A transverse frame tied to the swing plane keeps the strands from flipping as the hammer passes vertical.
        Vec3d tangent = shape.point(root, Math.min(1, s + .06)).subtract(shape.point(root, s - .06)).normalize();
        Vec3d normal = tangent.crossProduct(guide).normalize();
        Vec3d across = normal.crossProduct(tangent);
        return center.add(across.multiply(radius * Math.cos(TAU * turns))).add(normal.multiply(radius * Math.sin(TAU * turns)));
    }

    private Shape leap(int arm) {
        double flight = clamp((age - release) / LeapSpell.FLIGHT);
        double angle = (.08 + arm / 3.0) * TAU;
        Vec3d radial = left.multiply(Math.cos(angle)).add(forward.multiply(Math.sin(angle)));
        Vec3d origin = effect != null && effect.stage() >= 1 ? effect.auxPoint() : feet;
        Vec3d landing = effect != null && effect.stage() >= 1 ? effect.getEntityPos() : feet;
        Vec3d plant = origin.add(radial.multiply(2.1 + arm * .55)).add(0, .26, 0);
        Shape cock = shape(arm, p(1.5 + arm * .35, 3.1, -.6), plant.add(0, 1.7, 0), plant);
        double corkscrew = angle + 1.7 * Math.sin(Math.PI * flight);
        Shape flightPose = shape(arm, p(1.4 + .8 * Math.cos(corkscrew), 2.8, -.8),
                p(2.4 * Math.cos(corkscrew), 3.4 + Math.sin(corkscrew), -2.2),
                p(3.6 * Math.cos(corkscrew), 3.8 + 1.9 * Math.sin(corkscrew), -3.4));
        Vec3d claw = landing.add(radial.multiply(LeapSpell.RADIUS)).add(0, .24 + 2.2 * (1 - flight), 0);
        Shape catchPose = shape(arm, feet.add(radial.multiply(1.6)).add(0, 3, 0),
                claw.add(radial.multiply(-.4)).add(0, 1.8, 0), claw);
        return cock.toward(flightPose, smoothstep(flight / .35)).toward(catchPose, smoothstep((flight - .38) / .58));
    }

    private Shape flame(int arm) {
        Vec3d mouth = flameMouth(feet, forward);
        double breath = Math.max(0, age - release);
        double side = arm == 0 ? 1 : -1;
        double draw = smoothstep(age / release);
        double pump = .5 + .5 * Math.cos(TAU * breath / 12 + arm * Math.PI);
        double squeeze = draw * (.25 + .75 * pump);
        if (arm == 2) return shape(arm, p(1.4, 3.7 + .22 * pump, -.8), mouth.add(0, .9, 0).subtract(forward.multiply(1.7)), mouth);
        // Two bellows move in opposition. The third limb conveys their stroke to a steady nozzle.
        return shape(arm, p(side * 2.2, 2.1, -1.1),
                p(side * (3.7 - 1.65 * squeeze), 2.65 + .7 * squeeze, -.7),
                p(side * (2.1 - 1.2 * squeeze), 1.35 + .65 * squeeze, .75 + 1.05 * squeeze));
    }

    static Vec3d flameMouth(Vec3d feet, Vec3d direction) {
        return feet.add(direction.multiply(.8)).add(0, 1.85, 0);
    }

    private double beat(double from, double duration) { return smoothstep((age - from) / duration); }

    // Three spreading jets push down during takeoff, then flare upward, well outside the fire.
    private Shape fireball(int arm) {
        double a = (arm / 3.0 + .085) * TAU;
        double open = beat(3 + arm, 11), throwBeat = beat(release - 2, 6);
        double x = Math.cos(a), z = Math.sin(a);
        return shape(arm, p(x * 1.9, 1.15 + .4 * open, z * 1.9),
                p(x * (2.5 + open), .65 + 1.5 * open, z * (2.5 + open)),
                p(x * (2.7 + 1.35 * open + .65 * throwBeat), .38 + 3.7 * open - 2.6 * throwBeat,
                        z * (2.7 + 1.35 * open) - 1.35 * throwBeat));
    }

    // A wider, slower fan than Fireball. All three tips turn skyward and stay apart through fusion.
    private Shape purple(int arm) {
        double spread = beat(arm * 2, 25), crown = beat(23 + arm * 2, 18);
        double send = beat(HollowPurpleSpell.TIMING.aimLock() + 3, 12);
        double side = arm == 0 ? 1 : arm == 1 ? -1 : .15;
        double back = arm == 2 ? -1 : .24;
        double reach = 2 + spread * (arm == 2 ? 2.3 : 2.8);
        return shape(arm, p(side * 1.55, 1.6, back * 1.65),
                p(side * reach, 1.3 + 1.15 * spread + .75 * crown, back * reach),
                p(side * (reach + .5 + .45 * send), 2 + 1.45 * spread + 1.7 * crown - 1.6 * send,
                        back * (reach + .75) + 1.4 * send));
    }

    private Shape shear(int arm) {
        double launch = SkyShearSpell.leaves(arm);
        double pull = beat(launch - 13, 10), cut = beat(launch - 2, 5), follow = beat(launch + 3, 9);
        double level = SkyShearSpell.STACK + arm * SkyShearSpell.STACK_STEP;
        double side = arm == 0 ? 1 : arm == 2 ? -1 : .12;
        double high = arm == 1 ? 2.3 : .3;
        return shape(arm, p(1.25 + side * .8, level + high * .32, -1),
                p(side * (2.3 + pull - 3.4 * cut), level + high * pull - 1.2 * cut, -.6 + 2.3 * cut),
                p(side * (1.6 + 2 * pull - 5.3 * cut), level + high * pull - 2.2 * cut + .5 * follow,
                        .8 - .7 * pull + 3.65 * cut - .9 * follow));
    }

    private Shape meteor(int arm) {
        double wind = beat(0, 14), toss = beat(MeteorSpell.TIMING.aimLock() - 3, 5), settle = beat(24, 10);
        if (arm == 2) {
            // A sling cups the knot, snaps vertically and draws back as the rock climbs away.
            return shape(arm, p(1.8, 2.3, -1.3), p(2.5 - toss, 2.3 + 2.8 * toss, -1.8 + 2.3 * toss),
                    p(.25 + .6 * settle, MeteorSpell.KNOT - .35 + 3.4 * toss - settle, -.2 + .95 * toss));
        }
        double side = arm == 0 ? 1 : -1;
        return shape(arm, p(1.1 + side, 2.8 - .9 * wind, -.6),
                p(side * (2.5 + .5 * toss), 1.45 + .85 * toss, -2.25 + arm * 2),
                p(side * (3.2 + .25 * settle), .18 + .6 * settle, -2.7 + arm * 3.8));
    }

    private Shape bloom(int arm) {
        double cup = beat(0, 9), unfurl = beat(release - 5 - arm * .7, 7), follow = beat(release + 1, 7);
        if (arm == 2) return shape(arm, p(1.9, 1.0, -.9), p(.6, 1.3 + 1.4 * unfurl, -1.5 + 2.7 * unfurl),
                p(.1, SentryBloomSpell.BUD_HEIGHT - .55 + 1.15 * unfurl, -.1 + 2.5 * unfurl + .6 * follow));
        double side = arm == 0 ? 1 : -1;
        return shape(arm, p(side * 1.65, 1.4, -.95), p(side * (2.5 + .65 * unfurl), 2 + .45 * cup, .1),
                p(side * (1.9 - 1.25 * cup + 3 * unfurl), 2.15 + .7 * cup + 1.2 * unfurl - .5 * follow, .35 + 1.15 * unfurl));
    }

    private Shape well(int arm) {
        double wind = beat(0, release - 2), throwBeat = beat(release - 2, 7);
        if (arm == 2) return shape(arm, p(1.85, 1.35, -1.2), p(1, 2, -1.6 + 2.5 * throwBeat),
                p(.1, GravityWellSpell.HEIGHT - .6, 3.8 * throwBeat));
        double a = (arm == 0 ? 1 : -1) * (.45 + 3.8 * wind + .65 * throwBeat) + arm * Math.PI;
        double radius = 3.5 - .95 * wind + .75 * throwBeat;
        // Opposed cranks pass on separate levels; the third arm drives the core out between them.
        return shape(arm, p(1.3 + arm * .6, 1.6 + 1.4 * arm, -.9),
                p(radius * Math.cos(a - .8), 1.65 + 1.45 * arm, radius * Math.sin(a - .8)),
                p(radius * Math.cos(a), 2 + 1.35 * arm, radius * Math.sin(a) + throwBeat));
    }

    private Shape faultline(int arm) {
        double hammer = beat(release - 5 + arm * 1.3, 4);
        double echo = beat(release + arm + 1, 7);
        double side = (arm - 1) * 1.45;
        return shape(arm, p(1.4 + arm * .48, 2.8 + .38 * arm, -.65),
                p(side + .5, 4.4 - 3.6 * hammer + .85 * echo, .7 + 1.35 * hammer),
                p(side, 4.15 - 3.93 * hammer + 1.1 * echo, 1.15 + (1.35 + arm * 1.2) * hammer));
    }

    private Shape wall(int arm) {
        Vec3d rail = effect != null && effect.dir().horizontalLengthSquared() > .01 ? effect.dir().normalize() : left;
        double hook = beat(0, 7), hoist = beat(7 + arm, 8), shove = beat(release - 3, 6);
        double side = arm - 1;
        Vec3d grip = p(0, 1.15 + 1.65 * hook + .5 * hoist + .65 * shove, 3.25 * shove)
                .add(rail.multiply(side * (.64 + 1.25 * shove)));
        return shape(arm, p(2.25 + side * .55, 1.1 + .65 * hoist, -.85),
                grip.add(rail.multiply(side * .55)).add(0, -.9, 0).subtract(forward.multiply(.65)), grip);
    }

    private Shape dash(int arm) {
        double launch = beat(release - 2, 4), brake = beat(release + WaylayDashSpell.DASH - 2, 5);
        double side = arm == 0 ? 1 : arm == 1 ? -1 : .35;
        Shape start = shape(arm, p(side * 1.6, 2.0, -.5), p(side * 2.7, 1.4 + arm * .45, -1.3),
                arm == 2 ? p(.2, 1.7, 3.8) : p(side * 3.25, .22, -2.5));
        return start.toward(shape(arm, p(side * 1.5, 2.3, -.6), p(side * 2, 1.7 + arm, -2.6),
                p(side * (2.25 + 2.2 * brake), 1.4 + arm * .9, -5.2 + 5 * brake)), launch);
    }

    private Shape bite(int arm) {
        double open = beat(0, 11), bite = beat(release - 1, 5), reel = beat(release + 7, 10);
        if (arm == 2) return shape(arm, p(1.75, 2.1, -1), p(.75, 1.8, .8 + 2 * bite),
                p(.1, ThornbiteSpell.FORM_HEIGHT - .4 - 1.15 * bite, 1.1 + 4.4 * bite - 2.6 * reel));
        double jaw = arm == 0 ? 1 : -1;
        return shape(arm, p(1.4 + arm * .7, 2.65 + jaw * .6, -.7),
                p(jaw * .9, 3.15 + jaw * (1.1 + .8 * open), 1.2 + 1.65 * bite),
                p(jaw * .36, ThornbiteSpell.FORM_HEIGHT + jaw * (1.5 * open - 1.25 * bite) - bite,
                        ThornbiteSpell.FORM_AHEAD + 3.4 * bite - 1.4 * reel));
    }

    private Shape rift(int arm) {
        double tear = beat(2, release - 2), pass = beat(release, 5);
        // Two hooks pull a vertical slit wide; the long third limb reaches through, then streams out behind.
        if (arm == 2) return shape(arm, p(1.7, 2.5, -.6), p(.65 + pass, 2.15, .75 + tear - 3 * pass),
                p(.1 + 2.2 * pass, 2.0 + .7 * pass, 1.8 + 2.1 * tear - 7.4 * pass));
        double side = arm == 0 ? 1 : -1;
        return shape(arm, p(side * 1.7, 2.4 + side * .7, -.65),
                p(side * (1.3 + 1.75 * tear), 2.1 + side * 1.25, .6 - pass),
                p(side * (.28 + 3.45 * tear), 2.6 + side * (1.2 + .55 * tear), 1.65 - 2.35 * pass));
    }

}
