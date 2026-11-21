package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;

import com.anton.elementalwands.end.EndIntroTrack;
import com.anton.elementalwands.entity.firstwizard.FirstWizardChangeTrack;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.FlameSpell;
import com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell;
import com.anton.elementalwands.entity.firstwizard.spell.LeapSpell;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The torn copy's body as clients draw it, from its synced clocks alone, so the player model
 * ({@code FirstWizardRenderer}), the tentacles ({@link TendrilFx}) and the change's ink ({@link ChangeFx}) agree: whether
 * the arm is still on and how it is wrenched, where the torn arm lies, how far the flank has swollen, whether it has
 * burst, how far the head is bent, how high the raised body hangs, how it staggers, and where the wound is in the
 * world. During the change everything comes from its baked track ({@link FirstWizardChangeTrack}) on the change's
 * clock; after it, from phase two's rest. The entity itself stays on the floor; only what is drawn is raised.
 *
 * <p>Points of the change's frame are in blocks: {@code x} her left, {@code y} up from her feet, {@code z} ahead of her.
 * The model's own points are pixels: x her left, y down from {@link #MODEL_TOP}, z back.</p>
 */
public final class WizardBody {
    private WizardBody() {}

    /** The wound's height over the drawn body's feet and how far out to its side it is. */
    public static final double WOUND_Y = 1.3, WOUND_OUT = .24;
    /** The player model's origin over its feet, in blocks. */
    public static final double MODEL_TOP = 1.501;

    // ── The change's clock and channels ──────────────────────────────────────

    /** Ticks into the change, with the partial tick; -1 when it is not changing. */
    public static double change(FirstWizardEntity e, float delta) { return e.phase() == FirstWizardEntity.CHANGING ? e.phaseAge(delta) : -1; }

    private static EndIntroTrack.Scene track() { return FirstWizardChangeTrack.get().scene(); }

    /** A number from the change's track at its tick (clamped to the end: the state phase two starts in). */
    public static double value(String channel, double t) { return track().value(channel, Math.max(0, t)); }
    /** A point from the change's track at its tick. */
    public static Vec3d point(String channel, double t) { return track().point(channel, Math.max(0, t)); }

    /** Its left arm is off: from the moment the strands snap. */
    public static boolean armGone(FirstWizardEntity e, float delta) {
        if (!e.torn()) return false;
        double t = change(e, delta);
        return t < 0 || t >= PhaseChange.SNAP;
    }

    /** The flank has burst open (and stays so through phase two): the torso is the torn wound the tentacles leave from. */
    public static boolean burst(FirstWizardEntity e, float delta) {
        if (!e.torn()) return false;
        double t = change(e, delta);
        return t < 0 || t >= PhaseChange.BURST;
    }

    /** The side of the wound: always her left (+1). */
    public static double side(FirstWizardEntity e) { return SweepSpell.woundSide(); }

    /** How far the head is bent to her right, radians: the change's, then phase two's (with a twitch now and then). */
    public static double bend(FirstWizardEntity e, float delta) {
        if (!e.torn()) return 0;
        double t = change(e, delta);
        if (t >= 0 && t < PhaseChange.CINEMATIC) return value("bend", t);
        double c = twitchClock(e, delta);
        return FirstWizardChangeTrack.get().bendTwo() + (c < 10 ? .1 * Math.exp(-c / 2.4) * Math.cos(c * 1.4) : 0);
    }

    /** A jerk of the bent head to one side: the change's, then phase two's now and then. */
    public static double headTwitch(FirstWizardEntity e, float delta) {
        if (!e.torn()) return 0;
        double t = change(e, delta);
        if (t >= 0 && t < PhaseChange.CINEMATIC) return value("head_yaw", t);
        double c = twitchClock(e, delta);
        return c < 10 ? .12 * Math.exp(-c / 2.4) * Math.cos(c * 1.6) : 0;
    }

    /** Ticks since its head last twitched in phase two: about every eight to eleven seconds, never in step with another copy. */
    private static double twitchClock(FirstWizardEntity e, float delta) {
        double now = e.getEntityWorld().getTime() + delta + e.getId() * 37;
        double period = 160 + (e.getId() % 5) * 13;
        return now % period;
    }

    /**
     * How high the drawn body floats: it lifts off the floor late in the change; in phase two it hovers with a slow bob,
     * sinks a little as it winds itself up for a leap or braces for the flame or the finisher, and dips as it lands.
     */
    public static double lift(FirstWizardEntity e, float delta) {
        if (!e.torn()) return 0;
        double lift = FirstWizardRules.LIFT;
        double bob = .07 * Math.sin((e.getEntityWorld().getTime() + delta) / 20.0 * 2.4 + e.getId());
        if (e.phase() == FirstWizardEntity.CHANGING) return (lift + bob) * value("lift", change(e, delta));
        String move = e.castMove();
        double age = e.castAge(delta);
        if (age < 0) return lift + bob;
        switch (move) {
            case "leap" -> {
                // Sinks as it winds up; lands low and floats up again.
                if (age < LeapSpell.TIMING.release()) return lift * (1 - .5 * smoothstep(age / LeapSpell.TIMING.release()));
                double landed = age - LeapSpell.lands();
                if (landed >= 0) return lift * (.4 + .6 * smoothstep(landed / 10)) + bob * smoothstep(landed / 10);
                return lift;
            }
            case "flame" -> { return lift * (1 - .3 * smoothstep(age / 6) * (1 - smoothstep((age - FlameSpell.ends()) / 6))); }
            case "hollow_purple" -> { return lift * (1 - .25 * smoothstep(age / 10) * (1 - smoothstep((age - HollowPurpleSpell.TIMING.release()) / 6))); }
            default -> { return lift + bob; }
        }
    }

    /** The stagger as the blow lands: how far it is thrown back, in radians. */
    public static double staggerBack(FirstWizardEntity e, float delta) {
        double t = change(e, delta);
        return t < 0 || t > PhaseChange.STAGGER + 2 ? 0 : value("stagger", t);
    }

    /** And how far toward the wound's side. */
    public static double staggerSide(FirstWizardEntity e, float delta) {
        double t = change(e, delta);
        return t < 0 || t > PhaseChange.STAGGER + 2 ? 0 : value("stagger_side", t) * side(e);
    }

    // ── Where things are ─────────────────────────────────────────────────────

    /** The body's yaw, radians, drawn. */
    public static double yaw(FirstWizardEntity e, float delta) { return MathHelper.lerpAngleDegrees(delta, e.lastBodyYaw, e.bodyYaw) * MathHelper.RADIANS_PER_DEGREE; }

    /** Forward and to its left, flat. */
    public static Vec3d forward(double yaw) { return new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)); }
    public static Vec3d left(double yaw) { return new Vec3d(Math.cos(yaw), 0, Math.sin(yaw)); }

    /** A point of the change's frame (blocks: her left, up, ahead), from her drawn feet, in the world. */
    public static Vec3d place(FirstWizardEntity e, float delta, Vec3d local) {
        double yaw = yaw(e, delta);
        return e.getLerpedPos(delta).add(left(yaw).multiply(local.x)).add(0, local.y, 0).add(forward(yaw).multiply(local.z));
    }

    /** A point of the player model (pixels: her left, down from the model's origin, back) in the change's frame. */
    public static Vec3d frameOf(double x, double y, double z) { return new Vec3d(x / 16, MODEL_TOP - y / 16, -z / 16); }

    /** A vector turned by the model's angles, applied as a ModelPart applies them (Z, then Y, then X, outermost first). */
    public static Vec3d turn(Vec3d v, double pitch, double yaw, double roll) {
        double cx = Math.cos(pitch), sx = Math.sin(pitch), cy = Math.cos(yaw), sy = Math.sin(yaw), cz = Math.cos(roll), sz = Math.sin(roll);
        double x = v.x, y = v.y * cx - v.z * sx, z = v.y * sx + v.z * cx;
        double x2 = x * cy + z * sy, z2 = -x * sy + z * cy;
        return new Vec3d(x2 * cz - y * sz, x2 * sz + y * cz, z2);
    }

    /** Where the tendrils leave the wound, in the world. */
    public static Vec3d wound(FirstWizardEntity e, float delta) {
        double yaw = yaw(e, delta), back = staggerBack(e, delta);
        Vec3d feet = e.getLerpedPos(delta).add(0, lift(e, delta), 0);
        Vec3d at = feet.add(left(yaw).multiply(side(e) * WOUND_OUT)).add(0, WOUND_Y, 0);
        // Thrown back with the stagger, round its hips.
        return at.add(forward(yaw).multiply(-Math.sin(back) * (WOUND_Y - .75)));
    }

    /** Where its human (wand) hand is, in the world: at its side, or held out before it while it casts a flame or the orb. */
    public static Vec3d hand(FirstWizardEntity e, float delta) {
        double yaw = yaw(e, delta);
        Vec3d feet = e.getLerpedPos(delta).add(0, lift(e, delta), 0);
        return feet.add(left(yaw).multiply(-side(e) * .38)).add(forward(yaw).multiply(.55)).add(0, 1.38, 0);
    }
}
