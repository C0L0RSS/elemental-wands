package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.entity.roc.StormRocPose;
import com.anton.elementalwands.entity.roc.StormRocRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Presentation layered on the Roc's authored clips, on clients only: a short crossfade whenever
 * the server changes clip, a bird's gaze that holds and then snaps to the player it is striking
 * at (or to you), a heavy head that tracks its prey through the Thunder Bite's windup, and banking
 * through its turns in flight and in the Thunder Charge's run. Server hitboxes keep the plain clips.
 */
public final class StormRocMotion {
    /** Ticks a clip change takes to blend in. */
    private static final float FADE = 5;
    /** Clip changes that continue the same motion, frame for frame, so they cut without a blend. */
    private static final Set<String> CHAINED = Set.of("stampede>stampede_run", "stampede_run>stampede_skid", "stampede>stampede_skid", "spire_flight>land", "land>call");
    /** The spire hop's head stays on the next spire through its launch; only then may its gaze settle on a player below. */
    private static final int SPIRE_LOOK = 26;
    /** The landing's ticks before it has settled and glares at its challenger. */
    private static final int LAND_GLARE = 37;
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
        boolean wasFlying;
        int flightTarget = -1;
        long focusUntil, nextFocus;
        float bank, prevBank, climb, prevClimb, lastYaw = Float.NaN;
        double lastY;
    }

    /** What one frame draws: blended, gaze-turned channels, plus the banking applied around the body. */
    public record Frame(Map<String, float[]> channels, float bank, float climb, float pivot) {}

    private static final Map<StormRocEntity, State> STATES = new WeakHashMap<>();
    private static final Map<String, Vector3d> NEUTRAL = new HashMap<>();
    /** Entities compare by their network id, which a new world reuses: its Rocs start afresh. */
    private static ClientWorld world;

    private StormRocMotion() {}

    /** Its long flight and its hop between spires. */
    private static boolean flying(String pose) { return pose.equals("flight") || pose.equals("spire_flight"); }

    public static void tick(MinecraftClient client) {
        if (client.world != world) {
            STATES.clear();
            world = client.world;
        }
        if (client.world == null || client.isPaused()) return;
        for (Entity e : client.world.getEntities()) if (e instanceof StormRocEntity roc) tick(client, roc);
    }

    private static void tick(MinecraftClient client, StormRocEntity roc) {
        State s = STATES.computeIfAbsent(roc, r -> new State());
        long now = roc.getEntityWorld().getTime();
        String pose = roc.poseName();
        boolean flying = flying(pose);
        // Crouched on its spire for the hop's push-off, it doesn't bank yet.
        boolean aloft = flying && !(pose.equals("spire_flight") && roc.poseTicks(0) < StormRocRules.SPIRE_PUSH);
        Entity flightTarget = null;
        if (flying) {
            if (!s.wasFlying) {
                s.flightTarget = -1;
                s.focusUntil = 0;
                s.nextFocus = now + (pose.equals("spire_flight") ? Math.max(8, SPIRE_LOOK - (long) roc.poseTicks(0)) : 8);
            }
            flightTarget = flightTarget(client, roc, s, now);
        }
        s.wasFlying = flying;
        // Gaze: perched and between brawl attacks it watches; in flight, now and then; never mid-attack.
        s.prevYaw = s.yaw;
        s.prevPitch = s.pitch;
        s.prevWeight = s.weight;
        float wanted = switch (pose) {
            case "perched", "hunched" -> 1;
            // Landing, its head holds on the rock; once it has settled it turns slowly to glare.
            case "land" -> roc.poseTicks(0) >= LAND_GLARE ? 1 : 0;
            // Calling, its head is on the sky until it whips down onto the player its first strike
            // is aimed at, as that strike lands, and holds there until the second.
            case "call" -> {
                float after = roc.poseTicks(0) - StormRocRules.CALL_VOLLEY - StormRocRules.LARGE_WARNING;
                yield after >= -2 && after < StormRocRules.BOLT_GAP + 2 ? 1 : 0;
            }
            case "flight", "spire_flight" -> flightTarget != null ? 1 : 0;
            // The bite's windup keeps its head on its prey until the aim locks, then lets go
            // before the lunge, which bites where the server aimed.
            case "bite", "bite_leap" -> roc.poseTicks(0) < StormRocRules.Action.BITE.lock ? 1 : 0;
            // So does the Raptor Strike's stalk: its head stays on its prey until it coils to leap.
            case "pounce" -> roc.poseTicks(0) < StormRocRules.Action.POUNCE.lock ? 1 : 0;
            // Charging, its head leads it round after its prey; once its skid stops, its head whips
            // round to the player it missed.
            case "stampede_run" -> .5f;
            case "stampede_skid" -> {
                float after = roc.poseTicks(0) - StormRocRules.STAMPEDE_SKID_TICKS;
                yield after >= -2 && after < 16 ? 1 : 0;
            }
            default -> 0;
        };
        s.weight += MathHelper.clamp(wanted - s.weight, -.15f, .15f);
        Entity target = roc.lookTarget() >= 0 ? roc.getEntityWorld().getEntityById(roc.lookTarget()) : null;
        if (target == null || !target.isAlive()) target = client.player;
        if (flying) target = flightTarget;
        if (target != null) {
            Vec3d d = target.getEyePos().subtract(roc.socket("head"));
            double a = Math.toRadians(roc.getYaw()), c = Math.cos(a), sn = Math.sin(a);
            // Into the model's own space, which faces -z.
            double x = d.x * c + d.z * sn, z = d.x * sn - d.z * c;
            float yaw = MathHelper.clamp((float) Math.toDegrees(Math.atan2(x, -z)), -80, 80);
            float pitch = MathHelper.clamp((float) Math.toDegrees(Math.atan2(d.y, Math.hypot(x, z))), flying ? -75 : -50, 35);
            float off = Math.max(Math.abs(yaw - s.yaw), Math.abs(pitch - s.pitch));
            if (pose.startsWith("bite") || pose.equals("pounce") || pose.equals("stampede_run") || pose.equals("land")) {
                // Rearing up for the bite, stalking, charging or settling from a landing, its head is
                // heavy: it follows its prey without snaps.
                s.yaw += (yaw - s.yaw) * .3f;
                s.pitch += (pitch - s.pitch) * .3f;
            } else {
                // A bird holds its gaze dead still, then snaps to where it wants to look.
                if (!s.snapping && (now >= s.holdUntil && off > 6 || off > 35)) s.snapping = true;
                if (s.snapping) {
                    // 1 - sqrt(1 - .45) gives half the previous flight turn speed.
                    float speed = flying ? .25838f : .55f;
                    s.yaw += (yaw - s.yaw) * speed;
                    s.pitch += (pitch - s.pitch) * speed;
                    if (Math.abs(yaw - s.yaw) < 2 && Math.abs(pitch - s.pitch) < 2) {
                        s.snapping = false;
                        s.holdUntil = now + 6 + roc.getRandom().nextInt(18);
                    }
                }
            }
        }
        // It banks into its turns and pitches with its climb in flight, and leans into the Thunder
        // Charge's curve as it runs.
        float turn = Float.isNaN(s.lastYaw) ? 0 : MathHelper.wrapDegrees(roc.getYaw() - s.lastYaw);
        double rise = roc.getY() - s.lastY;
        s.lastYaw = roc.getYaw();
        s.lastY = roc.getY();
        s.prevBank = s.bank;
        s.prevClimb = s.climb;
        float lean = aloft ? MathHelper.clamp(turn * 3.5f, -38, 38) : pose.equals("stampede_run") ? MathHelper.clamp(turn * 6, -15, 15) : 0;
        s.bank += (lean - s.bank) * .2f;
        s.climb += ((aloft ? MathHelper.clamp((float) rise * 25, -25, 25) : 0) - s.climb) * .2f;
    }

    /** Briefly watch a living player below, then release back into the authored ground scan. */
    private static Entity flightTarget(MinecraftClient client, StormRocEntity roc, State s, long now) {
        if (now < s.focusUntil) {
            Entity current = client.world.getEntityById(s.flightTarget);
            if (canWatch(roc, current)) return current;
            s.focusUntil = now;
            s.nextFocus = now + 8;
        }
        if (now < s.nextFocus) return null;
        var players = client.world.getPlayers().stream().filter(p -> canWatch(roc, p)).toList();
        if (players.isEmpty()) {
            s.nextFocus = now + 10;
            return null;
        }
        var choices = players.stream().filter(p -> p.getId() != s.flightTarget).toList();
        Entity selected = (choices.isEmpty() ? players : choices).get(roc.getRandom().nextInt(choices.isEmpty() ? players.size() : choices.size()));
        s.flightTarget = selected.getId();
        s.snapping = true;
        s.focusUntil = now + 18 + roc.getRandom().nextInt(11);
        s.nextFocus = s.focusUntil + 12 + roc.getRandom().nextInt(15);
        return selected;
    }

    private static boolean canWatch(StormRocEntity roc, Entity entity) {
        if (!(entity instanceof PlayerEntity player) || !player.isAlive() || player.isSpectator() || player.isCreative()) return false;
        Vec3d d = player.getEyePos().subtract(roc.socket("head"));
        if (d.y >= -2 || d.lengthSquared() > 128 * 128) return false;
        double a = Math.toRadians(roc.getYaw());
        double x = d.x * Math.cos(a) + d.z * Math.sin(a), z = d.x * Math.sin(a) - d.z * Math.cos(a);
        return Math.abs(Math.toDegrees(Math.atan2(x, -z))) < 95;
    }

    /** The channels to draw this frame, shared by the model and its crackle so neither drifts from the other. */
    public static Frame frame(StormRocEntity roc, float partial) {
        State s = STATES.computeIfAbsent(roc, r -> new State());
        double now = roc.getEntityWorld().getTime() + partial;
        String pose = roc.poseName();
        long at = roc.poseStart();
        // Seen at render time, not on the next tick, so the new clip never pops in for a frame.
        if (!pose.equals(s.pose) || at != s.poseAt) {
            if (CHAINED.contains(s.pose + ">" + pose)) {
                s.fromPose = null;
                s.switchedAt = Double.NEGATIVE_INFINITY;
            } else if (!s.pose.isEmpty()) {
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
                // A turn has two Euler forms, (x, y, z) and (x + 180, 180 - y, z + 180); blend toward
                // the nearer one, or the part spins through the crossfade.
                float[] twin = {to[0] + 180, 180 - to[1], to[2] + 180};
                float near = 0, far = 0;
                for (int i = 0; i < 3; i++) {
                    near += Math.abs(MathHelper.wrapDegrees(to[i] - a[i]));
                    far += Math.abs(MathHelper.wrapDegrees(twin[i] - a[i]));
                }
                float[] goal = far < near ? twin : to;
                for (int i = 0; i < 9; i++) {
                    float d = (i < 3 ? goal[i] : to[i]) - a[i];
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
        // The bite's and the stalk's clips already aim the head at their prey: turn from there to it.
        boolean flight = flying(pose);
        Vector3d rest = flight || pose.equals("land") || pose.equals("call") || pose.startsWith("bite") || pose.equals("pounce") || pose.startsWith("stampede") ? bill(channels) : NEUTRAL.computeIfAbsent(pose, StormRocMotion::rest);
        double restYaw = Math.toDegrees(Math.atan2(rest.x, -rest.z)), restPitch = Math.toDegrees(Math.asin(MathHelper.clamp(rest.y, -1, 1)));
        // In flight its authored glances swing far to either side, so the gaze, already held within
        // reach, turns all the way from wherever the clip has the head.
        double turnYaw = (flight ? MathHelper.wrapDegrees(yaw - restYaw) : MathHelper.clamp(MathHelper.wrapDegrees(yaw - restYaw), -70, 70)) * weight;
        double turnPitch = (flight ? pitch - restPitch : MathHelper.clamp(pitch - restPitch, -40, 40)) * weight;
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

    private static Vector3d rest(String pose) { return bill(StormRocPose.pose(pose, 0)); }

    /** Where the bill points with these channels, in the model's space. */
    static Vector3d bill(Map<String, float[]> channels) {
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
