package com.anton.elementalwands.entity.necromancer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The transformation cinematic's baked script: per tick, where the camera is and where it looks,
 * the flashes, the fade, the crypt's light and the souls' glow; the beats, cuts, sound cues, smoke,
 * stone chips and embers; and every ghost's flight. Everything is in blocks in the scene's frame
 * ({@link NecromancerTransformScene#place}: the landing spot at the origin, +Z ahead of the boss, +X
 * on his left). art/hollow_necromancer/transform/build_scene.py writes it from the boss's
 * transformation clip, so the camera, the ghosts and the body agree. Samples between ticks are
 * interpolated, except across a cut, where a shot holds until it ends.
 */
public final class NecromancerTransformTrack {
    public static final String RESOURCE = "/assets/elementalwands/cinematics/necromancer_transform.json";
    private static NecromancerTransformTrack loaded;

    /** A puff of smoke or dust, as the preview draws it; the game spawns particles instead. */
    public record Smoke(int tick, Vec3d at, String kind, int count, double spread, double rise, double radius) {}
    /** Stone chips where a claw lands, or soul embers. */
    public record Effect(int tick, String kind, Vec3d at, int count) {}
    public record Cue(int tick, String cue) {}
    /**
     * One ghost of the storm: it leaves a broken end of the staff at {@code emerge} and is gone into
     * his face at {@code arrive}. {@code poses} holds, from {@code first}, its root, turn and size per
     * tick (size 0 while it is hidden); between {@code attack[0]} and {@code attack[1]} it bares its mouth.
     */
    public record Soul(int index, int emerge, int arrive, int first, float[][] poses, int[] attack) {
        public int last() { return first + poses.length - 1; }
        public boolean attacking(double t) { return attack != null && t >= attack[0] && t <= attack[1]; }
    }

    private final float[][] eye, glow;
    private final float[] yaw, pitch, fov, flash, black, dark;
    private final boolean[] cut, braziers, pov;
    private final Map<String, Integer> beats = new HashMap<>();
    private final List<Smoke> smoke = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    private final List<Cue> cues = new ArrayList<>();
    private final List<Soul> souls = new ArrayList<>();
    private final Set<String> transformOnly;

    private NecromancerTransformTrack(JsonObject json) {
        JsonObject ticks = json.getAsJsonObject("ticks");
        eye = points(ticks.getAsJsonArray("eye"));
        glow = points(ticks.getAsJsonArray("glow"));
        yaw = values(ticks.getAsJsonArray("yaw"));
        pitch = values(ticks.getAsJsonArray("pitch"));
        fov = values(ticks.getAsJsonArray("fov"));
        flash = values(ticks.getAsJsonArray("flash"));
        black = values(ticks.getAsJsonArray("black"));
        dark = values(ticks.getAsJsonArray("dark"));
        braziers = flags(ticks.getAsJsonArray("braziers"));
        pov = flags(ticks.getAsJsonArray("pov"));
        cut = new boolean[eye.length];
        for (JsonElement c : json.getAsJsonArray("cuts")) cut[c.getAsInt()] = true;
        json.getAsJsonObject("beats").entrySet().forEach(e -> beats.put(e.getKey(), e.getValue().getAsInt()));
        for (JsonElement e : json.getAsJsonArray("smoke")) {
            JsonObject o = e.getAsJsonObject();
            smoke.add(new Smoke(o.get("tick").getAsInt(), vec(o.getAsJsonArray("at")), o.get("kind").getAsString(), o.get("count").getAsInt(),
                    o.get("spread").getAsDouble(), o.get("rise").getAsDouble(), o.get("radius").getAsDouble()));
        }
        for (JsonElement e : json.getAsJsonArray("effects")) {
            JsonObject o = e.getAsJsonObject();
            effects.add(new Effect(o.get("tick").getAsInt(), o.get("kind").getAsString(), vec(o.getAsJsonArray("at")), o.get("count").getAsInt()));
        }
        for (JsonElement e : json.getAsJsonArray("events")) {
            JsonObject o = e.getAsJsonObject();
            cues.add(new Cue(o.get("tick").getAsInt(), o.get("cue").getAsString()));
        }
        JsonArray list = json.getAsJsonArray("souls");
        for (int i = 0; i < list.size(); i++) {
            JsonObject o = list.get(i).getAsJsonObject();
            JsonArray rows = o.getAsJsonArray("poses");
            float[][] poses = new float[rows.size()][];
            for (int k = 0; k < poses.length; k++) poses[k] = values(rows.get(k).getAsJsonArray());
            JsonElement attack = o.get("attack");
            souls.add(new Soul(i, o.get("emerge").getAsInt(), o.get("arrive").getAsInt(), o.get("first").getAsInt(), poses,
                    attack == null || attack.isJsonNull() ? null : new int[]{attack.getAsJsonArray().get(0).getAsInt(), attack.getAsJsonArray().get(1).getAsInt()}));
        }
        Set<String> names = new java.util.HashSet<>();
        for (JsonElement e : json.getAsJsonArray("transformOnly")) names.add(e.getAsString());
        transformOnly = Set.copyOf(names);
    }

    /** The script in the mod's jar; the server and the client read the same file. */
    public static NecromancerTransformTrack get() {
        if (loaded == null) {
            try (InputStream in = NecromancerTransformTrack.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
                loaded = new NecromancerTransformTrack(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return loaded;
    }

    public int length() { return eye.length - 1; }
    public int beat(String name) {
        Integer tick = beats.get(name);
        if (tick == null) throw new IllegalArgumentException("No beat " + name);
        return tick;
    }
    public List<Smoke> smoke() { return smoke; }
    public List<Effect> effects() { return effects; }
    public List<Cue> cues() { return cues; }
    public List<Soul> souls() { return souls; }
    /** Model parts only this cinematic shows; every other clip hides them. */
    public Set<String> transformOnly() { return transformOnly; }

    public Vec3d eye(double t) { return point(eye, t); }
    public float yaw(double t) {
        int a = index(t), b = next(a);
        return a == b ? yaw[a] : MathHelper.lerpAngleDegrees(fraction(t, a, b), yaw[a], yaw[b]);
    }
    public float pitch(double t) { return value(pitch, t); }
    public float fov(double t) { return value(fov, t); }
    /** The soul-white flashes, 0 to 1. */
    public float flash(double t) { return value(flash, t); }
    /** The fade in from black, 1 to 0. */
    public float black(double t) { return value(black, t); }
    /** The crypt's light: 1 as normal, 0 while the braziers are out. */
    public float dark(double t) { return value(dark, t); }
    /** Whether the braziers burn at this tick. */
    public boolean braziers(int t) { return braziers[MathHelper.clamp(t, 0, length())]; }
    /** His point of view: the camera sits in his hood, which it must not see. */
    public boolean pov(double t) { return pov[index(t)]; }
    /** Where the souls' light is, in the scene's frame. */
    public Vec3d glowAt(int t) { float[] g = glow[MathHelper.clamp(t, 0, length())]; return new Vec3d(g[0], g[1], g[2]); }
    /** How bright the souls' light is, 0 to 1. */
    public float glow(int t) { return glow[MathHelper.clamp(t, 0, length())][3]; }

    /** A ghost's root in the scene's frame at scene time t. */
    public static Vec3d soulAt(Soul soul, double t) {
        float[] a = soulPose(soul, Math.floor(t)), b = soulPose(soul, Math.floor(t) + 1);
        float f = (float)(t - Math.floor(t));
        return new Vec3d(MathHelper.lerp(f, a[0], b[0]), MathHelper.lerp(f, a[1], b[1]), MathHelper.lerp(f, a[2], b[2]));
    }

    /** A ghost's turn (GeckoLib's mirrored model space into the scene's frame) at scene time t. */
    public static Quaternionf soulTurn(Soul soul, double t) {
        float[] a = soulPose(soul, Math.floor(t)), b = soulPose(soul, Math.floor(t) + 1);
        return new Quaternionf(a[3], a[4], a[5], a[6]).slerp(new Quaternionf(b[3], b[4], b[5], b[6]), (float)(t - Math.floor(t)));
    }

    /** A ghost's size at scene time t: 0 before it shows, while hidden and once it is gone. */
    public static float soulSize(Soul soul, double t) {
        if (t < soul.first() || t > soul.last()) return 0;
        float[] a = soulPose(soul, Math.floor(t)), b = soulPose(soul, Math.floor(t) + 1);
        return MathHelper.lerp((float)(t - Math.floor(t)), a[7], b[7]);
    }

    private static float[] soulPose(Soul soul, double t) {
        return soul.poses()[MathHelper.clamp((int)t - soul.first(), 0, soul.poses().length - 1)];
    }

    private int index(double t) { return MathHelper.clamp((int)Math.floor(t), 0, length()); }
    /** The sample to blend toward, or the same one when a cut comes next. */
    private int next(int a) { return a >= length() || cut[a + 1] ? a : a + 1; }
    private static float fraction(double t, int a, int b) { return a == b ? 0 : (float)MathHelper.clamp(t - a, 0, 1); }

    private float value(float[] values, double t) {
        int a = index(t), b = next(a);
        return MathHelper.lerp(fraction(t, a, b), values[a], values[b]);
    }

    private Vec3d point(float[][] points, double t) {
        int a = index(t), b = next(a);
        float f = fraction(t, a, b);
        return new Vec3d(MathHelper.lerp(f, points[a][0], points[b][0]), MathHelper.lerp(f, points[a][1], points[b][1]),
                MathHelper.lerp(f, points[a][2], points[b][2]));
    }

    private static Vec3d vec(JsonArray a) { return new Vec3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()); }

    private static float[] values(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();
        return out;
    }

    private static boolean[] flags(JsonArray array) {
        boolean[] out = new boolean[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt() != 0;
        return out;
    }

    private static float[][] points(JsonArray array) {
        float[][] out = new float[array.size()][];
        for (int i = 0; i < out.length; i++) out[i] = values(array.get(i).getAsJsonArray());
        return out;
    }
}
