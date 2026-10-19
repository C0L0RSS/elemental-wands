package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.entity.roc.StormRocPose;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Presentation layered on the Roc's authored clips, on clients only: a short crossfade whenever
 * the server changes clip, a bird's gaze that holds and then snaps to the player it is striking
 * at (or to you), and banking through its turns in flight. Server hitboxes keep the plain clips.
 */
public final class StormRocMotion {
    /** Ticks a clip change takes to blend in. */
    private static final float FADE = 5;
    private static final String[] NECK = {"root", "body", "neck_base", "neck_lower", "neck_mid", "neck_upper"};
    /** The bill's direction in the head's own frame. */
    private static final Vector3d BILL = new Vector3d(0, -.0285, -.9996).normalize();
    /** Share of a head turn the top of the neck takes; the head takes the rest. */
    private static final double NECK_SHARE = .35;

    private static final class State {
        String pose = "";
        long poseAt;
        String fromPose;
        float fromTick;
        double switchedAt = Double.NEGATIVE_INFINITY;
        float yaw, pitch, prevYaw, prevPitch, weight, prevWeight;
        boolean snapping;
        long holdUntil;
        float bank, prevBank, climb, prevClimb, lastYaw = Float.NaN;
        double lastY;
    }

    /** What one frame draws: blended, gaze-turned channels, plus the banking applied around the body. */
    public record Frame(Map<String, float[]> channels, float bank, float climb, float pivot) {}

    private static final Map<StormRocEntity, State> STATES = new WeakHashMap<>();
    private static final Map<String, Vector3d> NEUTRAL = new HashMap<>();

    private StormRocMotion() {}

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.isPaused()) return;
        for (Entity e : client.world.getEntities()) if (e instanceof StormRocEntity roc) tick(client, roc);
    }

    private static void tick(MinecraftClient client, StormRocEntity roc) {
        State s = STATES.computeIfAbsent(roc, r -> new State());
        long now = roc.getEntityWorld().getTime();
        String pose = roc.poseName();
        // Gaze: perched and between brawl attacks it watches; in flight, now and then; never mid-attack.
        s.prevYaw = s.yaw;
        s.prevPitch = s.pitch;
        s.prevWeight = s.weight;
        float wanted = switch (pose) {
            case "perched", "hunched" -> 1;
            case "land" -> .6f;
            case "flight" -> .55f;
            default -> 0;
        };
        s.weight += MathHelper.clamp(wanted - s.weight, -.15f, .15f);
        Entity target = roc.lookTarget() >= 0 ? roc.getEntityWorld().getEntityById(roc.lookTarget()) : null;
        if (target == null || !target.isAlive()) target = client.player;
        if (target != null) {
            Vec3d d = target.getEyePos().subtract(roc.socket("head"));
            double a = Math.toRadians(roc.getYaw()), c = Math.cos(a), sn = Math.sin(a);
            // Into the model's own space, which faces -z.
            double x = d.x * c + d.z * sn, z = d.x * sn - d.z * c;
            float yaw = MathHelper.clamp((float) Math.toDegrees(Math.atan2(x, -z)), -80, 80);
            float pitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(d.y, Math.hypot(x, z))), -50, 35);
            float off = Math.max(Math.abs(yaw - s.yaw), Math.abs(pitch - s.pitch));
            // A bird holds its gaze dead still, then snaps to where it wants to look.
            if (!s.snapping && (now >= s.holdUntil && off > 6 || off > 35)) s.snapping = true;
            if (s.snapping) {
                s.yaw += (yaw - s.yaw) * .55f;
                s.pitch += (pitch - s.pitch) * .55f;
                if (Math.abs(yaw - s.yaw) < 2 && Math.abs(pitch - s.pitch) < 2) {
                    s.snapping = false;
                    s.holdUntil = now + 6 + roc.getRandom().nextInt(18);
                }
            }
        }
        // It banks into its turns and pitches with its climb, in flight only.
        float turn = Float.isNaN(s.lastYaw) ? 0 : MathHelper.wrapDegrees(roc.getYaw() - s.lastYaw);
        double rise = roc.getY() - s.lastY;
        s.lastYaw = roc.getYaw();
        s.lastY = roc.getY();
        s.prevBank = s.bank;
        s.prevClimb = s.climb;
        boolean flying = pose.equals("flight");
        s.bank += ((flying ? MathHelper.clamp(turn * 3.5f, -38, 38) : 0) - s.bank) * .2f;
        s.climb += ((flying ? MathHelper.clamp((float) rise * 25, -25, 25) : 0) - s.climb) * .2f;
    }

    /** The channels to draw this frame, shared by the model and its crackle so neither drifts from the other. */
    public static Frame frame(StormRocEntity roc, float partial) {
        State s = STATES.computeIfAbsent(roc, r -> new State());
        double now = roc.getEntityWorld().getTime() + partial;
        String pose = roc.poseName();
        long at = roc.poseStart();
        // Seen at render time, not on the next tick, so the new clip never pops in for a frame.
        if (!pose.equals(s.pose) || at != s.poseAt) {
            if (!s.pose.isEmpty()) {
                s.fromPose = s.pose;
                s.fromTick = (float) (now - s.poseAt);
                s.switchedAt = now;
            }
            s.pose = pose;
            s.poseAt = at;
        }
        float tick = roc.poseTicks(partial);
        Map<String, float[]> channels = StormRocPose.pose(pose, tick);
        float blend = (float) MathHelper.clamp((now - s.switchedAt) / FADE, 0, 1);
        if (blend < 1 && s.fromPose != null) {
            Map<String, float[]> from = StormRocPose.pose(s.fromPose, s.fromTick);
            float f = blend * blend * (3 - 2 * blend);
            channels.replaceAll((bone, to) -> {
                float[] a = from.get(bone);
                if (a == null) return to;
                float[] out = new float[9];
                for (int i = 0; i < 9; i++) {
                    float d = to[i] - a[i];
                    if (i < 3) d = MathHelper.wrapDegrees(d);
                    out[i] = a[i] + d * f;
                }
                return out;
            });
        }
        float weight = MathHelper.lerp(partial, s.prevWeight, s.weight);
        if (weight > .01f) gaze(channels, pose, MathHelper.lerp(partial, s.prevYaw, s.yaw), MathHelper.lerp(partial, s.prevPitch, s.pitch), weight);
        Vec3d body = StormRocPose.socket(pose, tick, "body");
        return new Frame(channels, MathHelper.lerp(partial, s.prevBank, s.bank), MathHelper.lerp(partial, s.prevClimb, s.climb), (float) body.y);
    }

    /**
     * Turns the bill toward a gaze, given as yaw and pitch in the model's space, on top of whatever
     * the clip's head is doing: the turn is measured from where this clip's bill rests, so the
     * clip's own head cocks and bobs still play. The math runs in the art study's frame; channels
     * store x as is and y and z negated.
     */
    private static void gaze(Map<String, float[]> channels, String pose, float yaw, float pitch, float weight) {
        Vector3d rest = NEUTRAL.computeIfAbsent(pose, StormRocMotion::rest);
        double restYaw = Math.toDegrees(Math.atan2(rest.x, -rest.z)), restPitch = Math.toDegrees(Math.asin(MathHelper.clamp(rest.y, -1, 1)));
        double turnYaw = MathHelper.clamp(MathHelper.wrapDegrees(yaw - restYaw), -70, 70) * weight;
        double turnPitch = MathHelper.clamp(pitch - restPitch, -40, 40) * weight;
        Vector3d goal = direction(restYaw + turnYaw, restPitch + turnPitch);
        Quaterniond turn = new Quaterniond().rotationTo(rest, goal);
        if (Math.abs(turn.angle()) < 1e-4) return;
        Quaterniond neckTurn = new Quaterniond().slerp(turn, NECK_SHARE);
        Matrix3d parent = new Matrix3d();
        for (String bone : NECK) parent.mul(rotation(channels.get(bone)));
        Matrix3d tip = rotation(channels.get("neck_tip")), head = rotation(channels.get("head"));
        // The top of the neck turns by its share; the head then takes the rest of the turn.
        Matrix3d newTip = new Matrix3d(parent).transpose().mul(new Matrix3d().set(neckTurn)).mul(parent).mul(tip);
        Matrix3d headParent = new Matrix3d(parent).mul(newTip);
        Matrix3d newHead = new Matrix3d(headParent).transpose().mul(new Matrix3d().set(turn)).mul(parent).mul(tip).mul(head);
        store(channels, "neck_tip", newTip);
        store(channels, "head", newHead);
    }

    private static Vector3d rest(String pose) {
        Map<String, float[]> channels = StormRocPose.pose(pose, 0);
        Matrix3d m = new Matrix3d();
        for (String bone : NECK) m.mul(rotation(channels.get(bone)));
        m.mul(rotation(channels.get("neck_tip"))).mul(rotation(channels.get("head")));
        return m.transform(new Vector3d(BILL)).normalize();
    }

    private static Vector3d direction(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector3d(Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p));
    }

    /** A channel's rotation in the study's frame: Rz(-z) Ry(y) Rx(-x), in degrees. */
    private static Matrix3d rotation(float[] p) {
        if (p == null) return new Matrix3d();
        return new Matrix3d().rotationZ(Math.toRadians(-p[2])).rotateY(Math.toRadians(p[1])).rotateX(Math.toRadians(-p[0]));
    }

    private static void store(Map<String, float[]> channels, String bone, Matrix3d m) {
        float[] p = channels.get(bone);
        if (p == null) return;
        Vector3d e = m.getEulerAnglesZYX(new Vector3d());
        float[] out = p.clone();
        out[0] = (float) -Math.toDegrees(e.x);
        out[1] = (float) Math.toDegrees(e.y);
        out[2] = (float) -Math.toDegrees(e.z);
        channels.put(bone, out);
    }
}
