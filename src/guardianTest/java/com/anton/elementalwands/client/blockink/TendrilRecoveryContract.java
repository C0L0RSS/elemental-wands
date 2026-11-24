package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpell;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** A finished cast and a queued follow-up must both start at the last visible joint, even when the copy moves. */
public final class TendrilRecoveryContract {
    private TendrilRecoveryContract() {}

    public static void run() {
        bodyAttachments();
        for (Move move : new Move[]{Move.FIREBALL, Move.HOLLOW_PURPLE}) {
            var clock = move == Move.FIREBALL ? FireballSpell.TIMING : HollowPurpleSpell.TIMING;
            if (WizardCastPose.choreography(move, clock.aimLock(), clock.release(), false).skyward() < .95)
                throw new AssertionError("Clone lost sight of the forming " + move);
        }
        Vec3d high = new Vec3d(1, 73.8, 4), lane = new Vec3d(-1, 0, 0);
        near(HollowPurpleSpell.formingCenter(high, 72, lane, HollowPurpleSpell.TIMING.aimLock()),
                high.add(0, HollowPurpleSpell.FORM_HEIGHT, 0));
        near(HollowPurpleSpell.formingCenter(high, 72, lane, HollowPurpleSpell.TIMING.release()),
                new Vec3d(1, 72 + HollowPurpleSpell.HEIGHT, 4).add(lane.multiply(HollowPurpleSpell.START)));
        var pose = new TendrilRecovery();
        Vec3d forward = new Vec3d(0, 0, 1), left = new Vec3d(1, 0, 0);
        var body = new TendrilFx.Frame(Vec3d.ZERO, Vec3d.ZERO, forward, left, 0);
        Vec3d strike = new Vec3d(0, .3, 10), rest = new Vec3d(4, 2, 0);
        int tip = TendrilFx.LINKS - 1;
        pose.begin(30, 30);
        pose.joint(2, tip, body, strike, .36);
        pose.begin(31, -1);
        near(pose.joint(2, tip, body, rest, .11), strike);
        if (Math.abs(pose.width(2, tip) - .36) > .0001) throw new AssertionError("Hammer width reset at cast end");
        pose.begin(36, -1);
        Vec3d between = pose.joint(2, tip, body, rest, .11);
        if (between.z >= strike.z || between.z <= rest.z) throw new AssertionError("Tip skipped its recovery");

        Vec3d socket = new Vec3d(30, 6, 80);
        var blink = new TendrilFx.Frame(socket, socket, left, forward.negate(), 0);
        Vec3d carried = socket.add(blink.out().multiply(between.x)).add(0, between.y, 0).add(blink.fwd().multiply(between.z));
        Vec3d next = socket.add(0, 5, 3);
        pose.begin(37, 0);
        near(pose.joint(2, tip, blink, next, .11), carried);
        pose.begin(45, 8);
        near(pose.joint(2, tip, blink, next, .11), next);
        pose.begin(90, -1);
        near(pose.joint(2, tip, body, rest, .11), rest);
    }

    private static void bodyAttachments() {
        Vec3d socket = new Vec3d(WizardBody.WOUND_OUT, WizardBody.WOUND_Y, 0);
        for (BossSpell spell : BossSpells.ALL) {
            int release = spell.timing().release();
            WizardCastPose cast = WizardCastPose.choreography(spell.move(), release, release, false);
            if (cast.point(socket).squaredDistanceTo(socket) < .001 || Math.abs(cast.leftLeg() - cast.rightLeg()) < 1)
                throw new AssertionError("Missing body or leg movement at release for " + spell.move());
            for (float yaw : new float[]{0, 1.1f, -2.6f}) {
                Vec3d left = WizardBody.left(yaw), forward = WizardBody.forward(yaw);
                var skin = new MatrixStack();
                cast.apply(skin, yaw);
                Vec3d base = left.multiply(socket.x).add(0, socket.y, 0).add(forward.multiply(socket.z));
                Vector3f drawn = skin.peek().getPositionMatrix().transformPosition(base.toVector3f());
                Vec3d posed = cast.point(socket);
                Vec3d ink = left.multiply(posed.x).add(0, posed.y, 0).add(forward.multiply(posed.z));
                near(new Vec3d(drawn), ink);
            }
        }
    }

    private static void near(Vec3d actual, Vec3d expected) {
        if (actual.squaredDistanceTo(expected) > 1e-8)
            throw new AssertionError("Expected joint at " + expected + ", got " + actual);
    }
}
