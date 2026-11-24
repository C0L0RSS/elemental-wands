package com.anton.elementalwands.client.blockink;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.smoothstep;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.spell.*;
import com.google.common.collect.MapMaker;
import java.util.Map;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The copy's hips in its facing frame, and the two legs relative to them. Angles are degrees, travel is in blocks.
 * Positive pitch tips the chest forward, positive twist turns it to the wound, and positive leg pitch trails a foot.
 * The skin and its world-space sockets sample the same pose at the same partial tick.
 */
public record WizardCastPose(double pitch, double twist, double bank, double rise, double advance,
                             double leftLeg, double rightLeg, double skyward) {
    public static final WizardCastPose REST = pose(0, 0, 0, 0, 0, 0, 0);
    private static final double HIP = .75;
    private static final Map<FirstWizardEntity, Playback> COPIES = new MapMaker().weakKeys().makeMap();

    private static WizardCastPose pose(double pitch, double twist, double bank, double rise, double advance,
                                       double leftLeg, double rightLeg) {
        return new WizardCastPose(pitch, twist, bank, rise, advance, leftLeg, rightLeg, 0);
    }

    private WizardCastPose lookingUp(double amount) {
        return new WizardCastPose(pitch, twist, bank, rise, advance, leftLeg, rightLeg, amount);
    }

    private WizardCastPose toward(WizardCastPose b, double t) {
        if (t <= 0) return this;
        if (t >= 1) return b;
        return pose(MathHelper.lerp(t, pitch, b.pitch), twist + MathHelper.wrapDegrees(b.twist - twist) * t,
                MathHelper.lerp(t, bank, b.bank), MathHelper.lerp(t, rise, b.rise),
                MathHelper.lerp(t, advance, b.advance), MathHelper.lerp(t, leftLeg, b.leftLeg),
                MathHelper.lerp(t, rightLeg, b.rightLeg)).lookingUp(MathHelper.lerp(t, skyward, b.skyward));
    }

    private record Stroke(WizardCastPose wind, WizardCastPose hit) {
        WizardCastPose play(double age, double release) {
            double load = smoothstep(age / Math.min(12, release - 2));
            return REST.toward(wind, load).toward(hit, smoothstep((age - release + 3) / 6));
        }
    }

    // Each hand stroke has an opposing hip and a weighted foot. The grounded copy uses a shallower version.
    private static final Stroke SOW = new Stroke(pose(31, -32, 22, -.22, .12, -68, 27), pose(-14, 46, -24, .16, .23, 42, -28));
    private static final Stroke LIFT = new Stroke(pose(36, 8, -13, -.20, .10, -22, -37), pose(-23, -15, 9, .23, -.12, 32, 18));
    private static final Stroke STAMP = new Stroke(pose(-18, 29, -22, .18, -.14, -70, 15), pose(40, -27, 18, -.20, .24, 9, 43));
    private static final Stroke DASH = new Stroke(pose(-25, 30, 20, -.10, -.23, -48, 25), pose(47, -14, -16, .04, .25, 49, 27));
    private static final Stroke BITE = new Stroke(pose(-26, -34, -18, .10, -.22, 22, -29), pose(42, 16, 13, -.13, .34, -36, 48));
    private static final Stroke FOLD = new Stroke(pose(32, 46, -25, -.17, -.12, -53, -31), pose(-22, -39, 19, .16, .12, 21, -23));

    static WizardCastPose choreography(Move move, double age, double release, boolean opening) {
        if (move == null || age < 0) return REST;
        return switch (move) {
            case SLAM -> {
                WizardCastPose lifted = pose(-39, 39, -21, .18, -.25, -44, 28);
                WizardCastPose impact = pose(52, -28, 19, -.23, .37, 39, -52);
                WizardCastPose recoil = pose(12, -12, 6, -.04, .10, 17, -23);
                yield REST.toward(lifted, smoothstep(age / SlamSpell.TIMING.aimLock()))
                        .toward(impact, smoothstep((age - release + 7) / 7))
                        .toward(recoil, smoothstep((age - release - 2) / 12));
            }
            case SWEEP -> {
                double swing = opening ? PhaseChange.SWEEP : SweepSpell.SWING;
                double turn = opening
                        ? PhaseChange.angleAt(age + PhaseChange.TELL, 0, 1) / (2 * Math.PI)
                        : smoothstep((age - release) / swing);
                double stride = opening ? Math.cos(2 * Math.PI * turn) : 1 - 2 * turn;
                double follow = smoothstep((age - release - swing) / 10);
                WizardCastPose whip = pose(13 + 9 * Math.sin(Math.PI * turn), 60 - (opening ? 360 : 128) * turn - 12 * follow,
                        -26 * stride, -.11 + .12 * follow, .12, -38 * stride + 5, 31 * stride + 8);
                yield REST.toward(whip, smoothstep(age / (release - 3)));
            }
            case LEAP -> {
                double flight = age - release, land = age - LeapSpell.lands();
                yield REST.toward(pose(39, 30, -18, -.11, .12, -64, -24), smoothstep(age / (release - 2)))
                        .toward(pose(46, -43, 27, .12, .22, 68, 38), smoothstep(flight / 5))
                        .toward(pose(-20, 18, -19, .10, -.07, -63, 27), smoothstep((flight - 5) / 7))
                        .toward(pose(43, -8, 11, -.18, .19, -37, -48), smoothstep((land + 4) / 4))
                        .toward(pose(8, 8, 0, 0, 0, -8, 17), smoothstep(land / 9));
            }
            case FLAME -> {
                double puff = .5 - .5 * Math.cos(Math.max(0, age - release) * Math.PI / 6);
                yield REST.toward(pose(29, 24, -16, -.11, .18, -38, 36), smoothstep(age / release))
                        .toward(pose(14 + 16 * puff, 25 - 20 * puff, -17 + 11 * puff, -.03 - .1 * puff, .10 + .14 * puff, -32 + 8 * puff, 43 - 12 * puff), smoothstep((age - release) / 5));
            }
            case HOLLOW_PURPLE -> REST
                    .toward(pose(-14, -21, 13, .02, -.08, -57, 26).lookingUp(.90), smoothstep(age / 20))
                    .toward(pose(-18, 11, -5, .12, -.05, 15, 34).lookingUp(1), smoothstep((age - 18) / 23))
                    .toward(pose(36, 15, 16, -.07, .21, -52, 23), smoothstep((age - HollowPurpleSpell.TIMING.aimLock()) / 13))
                    .toward(pose(-21, -26, 10, .05, -.21, 32, -17), smoothstep((age - release) / 8));
            case SKY_SHEAR -> {
                WizardCastPose cut = pose(-10, 25, -12, .06, -.07, -14, 18);
                for (int blade = 0; blade < SkyShearSpell.COUNT; blade++) {
                    double beat = age - SkyShearSpell.leaves(blade);
                    double accent = smoothstep((beat + 5) / 6) * (1 - smoothstep((beat - 2) / 7));
                    WizardCastPose follow = switch (blade) {
                        case 0 -> pose(17, -45, 24, -.08, .18, 34, -28);
                        case 1 -> pose(42, 7, -8, -.18, .26, -46, 25);
                        default -> pose(16, 48, -27, .05, .20, -22, 39);
                    };
                    cut = cut.toward(follow, accent);
                }
                yield REST.toward(cut, smoothstep(age / 8));
            }
            case GRAVITY_WELL -> REST.toward(pose(12, -26, 13, .13, -.15, -25, 38), smoothstep(age / 6))
                    .toward(pose(29, 67, -31, -.14, .10, -45, 18), smoothstep((age - 5) / 11))
                    .toward(pose(-19, -48, 24, .15, -.19, 28, -37), smoothstep((age - release + 2) / 7));
            case FIREBALL -> REST.toward(pose(21, 17, 9, -.10, -.08, -54, 17), smoothstep(age / 5))
                    .toward(pose(-22, -15, -7, .12, -.11, 16, -32).lookingUp(1), smoothstep((age - 2) / 11))
                    .toward(pose(32, 22, 15, -.05, .24, 49, -25), smoothstep((age - release + 2) / 6));
            case METEOR -> REST.toward(pose(31, -39, 25, -.17, -.16, -43, 24), smoothstep(age / 13))
                    .toward(pose(-39, 22, -16, .18, .04, 22, -48).lookingUp(.74), smoothstep((age - MeteorSpell.TIMING.aimLock() + 4) / 6))
                    .toward(pose(17, 32, -8, .01, .19, 20, -19), smoothstep((age - release + 3) / 8));
            case SENTRY_BLOOM -> SOW.play(age, release);
            case STONE_WALL -> LIFT.play(age, 12)
                    .toward(pose(25, 4, -7, -.05, .31, -41, 29), smoothstep((age - release + 3) / 7));
            case FAULTLINE -> STAMP.play(age, release)
                    .toward(pose(16, 13, -8, -.08, .12, -24, 29), smoothstep((age - release - 1) / 7));
            case WAYLAY_DASH -> DASH.play(age, release)
                    .toward(pose(-30, 22, 18, .07, -.23, -52, 27), smoothstep((age - release - WaylayDashSpell.DASH + 2) / 6));
            case THORNBITE -> BITE.play(age, release)
                    .toward(pose(-32, -41, -17, .09, -.27, 37, -44), smoothstep((age - release - 5) / 11));
            case BLINK_RIFT -> FOLD.play(age, release - 1)
                    .toward(pose(8, -61, 25, .07, .22, -37, 48), smoothstep((age - release) / 5));
        };
    }

    public static WizardCastPose sample(FirstWizardEntity copy, float delta) {
        double change = WizardBody.change(copy, delta);
        if (change >= 0 && change < PhaseChange.TELL) {
            COPIES.remove(copy);
            return REST;
        }
        boolean opening = change >= PhaseChange.TELL;
        Move move = opening ? Move.SWEEP : Move.byId(copy.castMove());
        double age = opening ? change - PhaseChange.TELL : copy.castAge(delta);
        double release = opening ? PhaseChange.SWEEP_FROM - PhaseChange.TELL : copy.castRelease();
        double tick = copy.getEntityWorld().getTime() + (double) delta;
        Playback playback = COPIES.computeIfAbsent(copy, unused -> new Playback());
        return playback.sample(tick, move, age, release, opening, copy.torn(), copy.getMainArm() == Arm.LEFT);
    }

    private static final class Playback {
        private long cast = Long.MIN_VALUE;
        private double time = Double.NEGATIVE_INFINITY, changed = Double.NEGATIVE_INFINITY;
        private WizardCastPose outgoing = REST, current = REST;

        WizardCastPose sample(double tick, Move move, double age, double release, boolean opening, boolean torn, boolean leftHanded) {
            long next = move == null ? Long.MIN_VALUE : Math.round(tick - age);
            if (tick == time && next == cast) return current;
            if (tick < time || tick - time > 21) changed = Double.NEGATIVE_INFINITY;
            else if (next != cast) { outgoing = current; changed = tick; }
            cast = next;
            time = tick;
            WizardCastPose target = choreography(move, age, release, opening);
            if (!torn && move != null) {
                double hand = leftHanded ? -1 : 1;
                target = pose(target.pitch * .78, target.twist * .85 * hand, target.bank * .70 * hand,
                        Math.clamp(target.rise, -.035, .035), target.advance * .75,
                        (leftHanded ? target.rightLeg : target.leftLeg) * .68, (leftHanded ? target.leftLeg : target.rightLeg) * .68).lookingUp(target.skyward);
            }
            current = outgoing.toward(target, smoothstep((tick - changed) / (move == null ? 16 : 6)));
            return current;
        }
    }

    private Quaternionf rotation() {
        return new Quaternionf().rotationYXZ((float) Math.toRadians(twist), (float) Math.toRadians(pitch), (float) Math.toRadians(bank));
    }

    private double lateral() { return -.19 * Math.sin(Math.toRadians(bank)); }

    /** Same hip pivot, order of rotations and translation as the skin matrix. */
    Vec3d point(Vec3d local) {
        if (this == REST) return local;
        Vector3f rotated = rotation().transform(new Vector3f((float) local.x, (float) (local.y - HIP), (float) local.z));
        return new Vec3d(rotated.x + lateral(), rotated.y + HIP + rise, rotated.z + advance);
    }

    public void apply(MatrixStack matrices, float facing) {
        if (this == REST) return;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-facing));
        matrices.translate(lateral(), HIP + rise, advance);
        matrices.multiply(rotation());
        matrices.translate(0, -HIP, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(facing));
    }
}
