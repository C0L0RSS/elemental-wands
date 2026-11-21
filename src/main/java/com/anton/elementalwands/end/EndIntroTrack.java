package com.anton.elementalwands.end;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * The First Wizard's intro as art/first_wizard/intro/build_intro.py bakes it, read by the server and every client:
 * two scenes, {@code full} (the dragon's death and the condensing, the first time in a world) and {@code short} (the
 * condensing alone). Each holds its beats, its camera cuts, and per tick named channels: numbers (the camera's yaw,
 * pitch and field of view, the fade, the dragon's turn and wing beat, the pool's size, how far the copy has formed) and
 * points (the camera's eye, the dragon, its mouth, where the stream back is aimed).
 *
 * <p>Points are relative to the arena's centre: +X east, +Z south, y up from the floor's top, so a point's place in
 * the world is {@link #world}. Samples between ticks are interpolated; the camera holds across a cut.</p>
 */
public final class EndIntroTrack {
    public static final String RESOURCE = "/assets/elementalwands/cinematics/first_wizard_intro.json";
    private static EndIntroTrack loaded;

    /** One version of the scene. */
    public static final class Scene {
        public final String name;
        private final Map<String, Integer> beats = new HashMap<>();
        private final Map<String, float[]> values = new HashMap<>();
        private final Map<String, float[][]> points = new HashMap<>();
        private final boolean[] cut;
        private final int length;
        private final int[] hits;

        private Scene(String name, JsonObject json) {
            this.name = name;
            json.getAsJsonObject("beats").entrySet().forEach(e -> beats.put(e.getKey(), e.getValue().getAsInt()));
            length = beats.get("length");
            cut = new boolean[length + 2];
            for (JsonElement c : json.getAsJsonArray("cuts")) cut[c.getAsInt()] = true;
            JsonArray h = json.getAsJsonArray("hits");
            hits = new int[h.size()];
            for (int i = 0; i < hits.length; i++) hits[i] = h.get(i).getAsInt();
            for (var e : json.getAsJsonObject("ticks").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                if (a.size() > 0 && a.get(0).isJsonArray()) {
                    float[][] out = new float[a.size()][];
                    for (int i = 0; i < out.length; i++) {
                        JsonArray p = a.get(i).getAsJsonArray();
                        out[i] = new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()};
                    }
                    points.put(e.getKey(), out);
                } else {
                    float[] out = new float[a.size()];
                    for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
                    values.put(e.getKey(), out);
                }
            }
        }

        /** A scene in this format from another script (the First Wizard's change, entity/firstwizard/FirstWizardChangeTrack). */
        public static Scene read(String name, JsonObject json) { return new Scene(name, json); }

        public boolean full() { return name.equals("full"); }
        public int length() { return length; }
        public int beat(String name) { Integer b = beats.get(name); return b == null ? -1 : b; }
        /** The ticks the dragon is struck by its own breath (full scene only). */
        public int[] hits() { return hits; }
        public boolean has(String channel) { return values.containsKey(channel) || points.containsKey(channel); }

        private int index(double t) { return MathHelper.clamp((int) Math.floor(t), 0, length); }
        private static float fraction(double t, int a) { return (float) MathHelper.clamp(t - a, 0, 1); }
        /** The camera's next sample, or the same one when a cut comes next. */
        private int nextShot(int a) { return a >= length || cut[a + 1] ? a : a + 1; }

        /** A number at scene tick t, or 0 when the scene has no such channel. Smooth across cuts. */
        public float value(String channel, double t) {
            float[] v = values.get(channel);
            if (v == null) return 0;
            int a = index(t), b = Math.min(a + 1, length);
            return MathHelper.lerp(fraction(t, a), v[a], v[b]);
        }

        /** A point at scene tick t, relative to the centre, or null when the scene has no such channel. */
        public Vec3d point(String channel, double t) {
            float[][] p = points.get(channel);
            if (p == null) return null;
            int a = index(t), b = Math.min(a + 1, length);
            float f = fraction(t, a);
            return new Vec3d(MathHelper.lerp(f, p[a][0], p[b][0]), MathHelper.lerp(f, p[a][1], p[b][1]), MathHelper.lerp(f, p[a][2], p[b][2]));
        }

        /** The camera's eye (relative to the centre), yaw, pitch and field of view: each shot holds until its cut. */
        public Vec3d eye(double t) {
            float[][] p = points.get("eye");
            int a = index(t), b = nextShot(a);
            float f = a == b ? 0 : fraction(t, a);
            return new Vec3d(MathHelper.lerp(f, p[a][0], p[b][0]), MathHelper.lerp(f, p[a][1], p[b][1]), MathHelper.lerp(f, p[a][2], p[b][2]));
        }
        public float yaw(double t) { return shot("yaw", t); }
        public float pitch(double t) { return shot("pitch", t); }
        public float fov(double t) { return shot("fov", t); }
        private float shot(String channel, double t) {
            float[] v = values.get(channel);
            int a = index(t), b = nextShot(a);
            return MathHelper.lerp(a == b ? 0 : fraction(t, a), v[a], v[b]);
        }
    }

    private final Scene full, brief;
    private final Vec3d form, arrival, bloom;
    private final double poolLevel, poolRadius, poolDepth;

    private EndIntroTrack(JsonObject json) {
        full = new Scene("full", json.getAsJsonObject("full"));
        brief = new Scene("short", json.getAsJsonObject("short"));
        form = vec(json.getAsJsonArray("form"));
        arrival = vec(json.getAsJsonArray("arrival"));
        bloom = vec(json.getAsJsonArray("bloom"));
        JsonObject pool = json.getAsJsonObject("pool");
        poolLevel = pool.get("level").getAsDouble();
        poolRadius = pool.get("radius").getAsDouble();
        poolDepth = pool.get("depth").getAsDouble();
    }

    public static EndIntroTrack get() {
        if (loaded == null) {
            try (InputStream in = EndIntroTrack.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
                loaded = new EndIntroTrack(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return loaded;
    }

    public Scene scene(boolean full) { return full ? this.full : brief; }
    /** Where the copy forms, facing the arrival: relative to the centre. */
    public Vec3d form() { return form; }
    public Vec3d arrival() { return arrival; }
    /** Where the dragon's breath strikes the pool, and the stream back leaves it. */
    public Vec3d bloom() { return bloom; }
    /** The pool's middle height, full radius and the depth of its belly, in blocks (the builder's numbers). */
    public double poolLevel() { return poolLevel; }
    public double poolRadius() { return poolRadius; }
    public double poolDepth() { return poolDepth; }

    /** The underside of a pool of this radius at r from its middle: the builder's {@code underside}. */
    public double underside(double r, double radius) {
        double k = radius > 0 ? Math.min(1, (r / radius) * (r / radius)) : 1;
        return poolLevel - 1 - poolDepth * (1 - k) * Math.sqrt(Math.max(0, radius) / poolRadius);
    }

    /** A track point in the world. */
    public static Vec3d world(Vec3d rel) {
        return new Vec3d(EndArena.CENTRE.getX() + .5 + rel.x, EndArena.CENTRE.getY() + 1 + rel.y, EndArena.CENTRE.getZ() + .5 + rel.z);
    }

    private static Vec3d vec(JsonArray p) { return new Vec3d(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()); }
}
