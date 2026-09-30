package com.anton.elementalwands.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The Guardian intro's baked script: per tick, where the camera is and where it looks, the zombie's
 * two halves and the Guardian's fists, in blocks in the scene's frame ({@link GuardianIntro#place}:
 * +Z ahead of the Guardian, +X on its left). art/fractured_guardian/intro/build_intro.py writes it
 * from the same performance as both actors' clips, so the camera, the sounds and the models agree.
 * Samples between ticks are interpolated, except across a cut, where a shot holds until it ends.
 */
public final class GuardianIntroTrack {
    public static final String RESOURCE = "/assets/elementalwands/cinematics/guardian_intro.json";
    private static GuardianIntroTrack loaded;

    private final float[][] eye, upper, lower, fists;
    private final float[] yaw, pitch, fov, flash, black;
    private final boolean[] cut;
    private final Map<String, Integer> beats = new HashMap<>(), events = new HashMap<>();

    private GuardianIntroTrack(JsonObject json) {
        JsonObject ticks = json.getAsJsonObject("ticks");
        eye = points(ticks.getAsJsonArray("eye"));
        upper = points(ticks.getAsJsonArray("upper"));
        lower = points(ticks.getAsJsonArray("lower"));
        fists = points(ticks.getAsJsonArray("fists"));
        yaw = values(ticks.getAsJsonArray("yaw"));
        pitch = values(ticks.getAsJsonArray("pitch"));
        fov = values(ticks.getAsJsonArray("fov"));
        flash = values(ticks.getAsJsonArray("flash"));
        black = values(ticks.getAsJsonArray("black"));
        cut = new boolean[eye.length];
        for (var c : json.getAsJsonArray("cuts")) cut[c.getAsInt()] = true;
        json.getAsJsonObject("beats").entrySet().forEach(e -> beats.put(e.getKey(), e.getValue().getAsInt()));
        json.getAsJsonObject("events").entrySet().forEach(e -> events.put(e.getKey(), e.getValue().getAsInt()));
    }

    /** The script in the mod's jar; the server and the client read the same file. */
    public static GuardianIntroTrack get() {
        if (loaded == null) {
            try (InputStream in = GuardianIntroTrack.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
                loaded = new GuardianIntroTrack(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return loaded;
    }

    public int length() { return eye.length - 1; }
    public int beat(String name) { return beats.get(name); }
    public int event(String name) { return events.get(name); }

    public Vec3d eye(double t) { return point(eye, t); }
    public float yaw(double t) {
        int a = index(t), b = next(a);
        return a == b ? yaw[a] : MathHelper.lerpAngleDegrees(fraction(t, a, b), yaw[a], yaw[b]);
    }
    public float pitch(double t) { return value(pitch, t); }
    public float fov(double t) { return value(fov, t); }
    /** The white-out as the fists land, 0 to 1. */
    public float flash(double t) { return value(flash, t); }
    /** The fade in from black, 1 to 0. */
    public float black(double t) { return value(black, t); }
    /** The middle of the zombie's top half (head, body and arms). */
    public Vec3d upper(double t) { return point(upper, t); }
    /** The middle of the zombie's legs. */
    public Vec3d lower(double t) { return point(lower, t); }
    /** Between the Guardian's fists. */
    public Vec3d fists(double t) { return point(fists, t); }

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

    private static float[] values(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();
        return out;
    }

    private static float[][] points(JsonArray array) {
        float[][] out = new float[array.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray p = array.get(i).getAsJsonArray();
            out[i] = new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()};
        }
        return out;
    }
}
