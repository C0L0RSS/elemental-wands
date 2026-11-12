package com.anton.elementalwands.entity.pyreking;

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
 * The Pyre King's intro as art/pyre_king/intro/build_intro.py bakes it: per tick the camera, how dark
 * the hall is, how much of his own glow shows and how much of the blade burns; the web of chains
 * with the moments each heats, parts and is ash; and the hall's fires with the tick each catches.
 * Points are hall cells (+X east, +Z south, the fight floor's top at y = 1): the world's place for
 * one is the slot's centre + (x, SURFACE_Y + y, z). The server and every client read the same file.
 * Samples between ticks are interpolated, except across a cut, where a shot holds until it ends.
 */
public final class PyreKingIntroTrack {
    public static final String RESOURCE = "/assets/elementalwands/cinematics/pyre_king_intro.json";
    private static PyreKingIntroTrack loaded;

    /** One chain of the web: heat starts at a on `heat` and reaches b `run` ticks later; it parts `part` of the way along. */
    public record Chain(Vec3d a, Vec3d b, double sag, int heat, int run, double part, int parts, int gone, boolean sword) {}
    /** A fire block, or (level above 0) a light block that carries one, and the tick it catches. */
    public record Flame(int x, int y, int z, int level, int tick) {}

    private final float[][] eye, sword;
    private final float[] yaw, pitch, fov, black, dark, ember, blade;
    private final boolean[] cut;
    private final Map<String, Integer> beats = new HashMap<>();
    private final Chain[] chains;
    private final Flame[] flames;
    private final Vec3d seat, mark;

    private PyreKingIntroTrack(JsonObject json) {
        JsonObject ticks = json.getAsJsonObject("ticks");
        eye = points(ticks.getAsJsonArray("eye"));
        sword = points(ticks.getAsJsonArray("sword"));
        yaw = values(ticks.getAsJsonArray("yaw"));
        pitch = values(ticks.getAsJsonArray("pitch"));
        fov = values(ticks.getAsJsonArray("fov"));
        black = values(ticks.getAsJsonArray("black"));
        dark = values(ticks.getAsJsonArray("dark"));
        ember = values(ticks.getAsJsonArray("ember"));
        blade = values(ticks.getAsJsonArray("blade"));
        cut = new boolean[eye.length];
        for (var c : json.getAsJsonArray("cuts")) cut[c.getAsInt()] = true;
        json.getAsJsonObject("beats").entrySet().forEach(e -> beats.put(e.getKey(), e.getValue().getAsInt()));
        JsonArray web = json.getAsJsonArray("chains");
        chains = new Chain[web.size()];
        for (int i = 0; i < chains.length; i++) {
            JsonObject c = web.get(i).getAsJsonObject();
            chains[i] = new Chain(vec(c.getAsJsonArray("a")), vec(c.getAsJsonArray("b")), c.get("sag").getAsDouble(), c.get("heat").getAsInt(),
                    c.get("run").getAsInt(), c.get("part").getAsDouble(), c.get("parts").getAsInt(), c.get("gone").getAsInt(), c.has("sword"));
        }
        JsonArray fires = json.getAsJsonArray("fires"), lights = json.getAsJsonArray("lights");
        flames = new Flame[fires.size() + lights.size()];
        for (int i = 0; i < fires.size(); i++) {
            JsonArray f = fires.get(i).getAsJsonArray();
            flames[i] = new Flame(f.get(0).getAsInt(), f.get(1).getAsInt(), f.get(2).getAsInt(), 0, f.get(3).getAsInt());
        }
        for (int i = 0; i < lights.size(); i++) {
            JsonArray f = lights.get(i).getAsJsonArray();
            flames[fires.size() + i] = new Flame(f.get(0).getAsInt(), f.get(1).getAsInt(), f.get(2).getAsInt(), f.get(3).getAsInt(), f.get(4).getAsInt());
        }
        seat = vec(json.getAsJsonArray("seat"));
        mark = vec(json.getAsJsonArray("mark"));
    }

    public static PyreKingIntroTrack get() {
        if (loaded == null) {
            try (InputStream in = PyreKingIntroTrack.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
                loaded = new PyreKingIntroTrack(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return loaded;
    }

    public int length() { return eye.length - 1; }
    public int beat(String name) { return beats.get(name); }
    public Chain[] chains() { return chains; }
    public Flame[] flames() { return flames; }
    /** Where he stands for the statue and the waking, and his mark at the hall's centre, in hall cells. */
    public Vec3d seat() { return seat; }
    public Vec3d mark() { return mark; }

    public Vec3d eye(double t) { return point(eye, t); }
    /** His sword's pommel: hanging, falling, struck, in his fist. */
    public Vec3d sword(double t) { return point(sword, t); }
    public float yaw(double t) {
        int a = index(t), b = next(a);
        return a == b ? yaw[a] : MathHelper.lerpAngleDegrees(fraction(t, a, b), yaw[a], yaw[b]);
    }
    public float pitch(double t) { return value(pitch, t); }
    public float fov(double t) { return value(fov, t); }
    /** The fade in from black, 1 to 0. */
    public float black(double t) { return value(black, t); }
    /** How dark the hall is, 1 until the blade lights and 0 once its fires have caught. */
    public float dark(double t) { return smooth(dark, t); }
    /** How much of his own glow shows (eyes, shard, the hot blade): 0 for the statue, 1 awake. */
    public float ember(double t) { return smooth(ember, t); }
    /** How far up the blade the fire has run from his fist, 0 to 1. */
    public float blade(double t) { return smooth(blade, t); }

    private int index(double t) { return MathHelper.clamp((int) Math.floor(t), 0, length()); }
    /** The sample to blend toward, or the same one when a cut comes next. */
    private int next(int a) { return a >= length() || cut[a + 1] ? a : a + 1; }
    private static float fraction(double t, int a, int b) { return a == b ? 0 : (float) MathHelper.clamp(t - a, 0, 1); }

    private float value(float[] values, double t) {
        int a = index(t), b = next(a);
        return MathHelper.lerp(fraction(t, a, b), values[a], values[b]);
    }

    /** Light does not cut with the camera. */
    private float smooth(float[] values, double t) {
        int a = index(t), b = Math.min(a + 1, length());
        return MathHelper.lerp(fraction(t, a, b), values[a], values[b]);
    }

    private Vec3d point(float[][] points, double t) {
        int a = index(t), b = points == sword ? Math.min(a + 1, length()) : next(a);
        float f = fraction(t, a, b);
        return new Vec3d(MathHelper.lerp(f, points[a][0], points[b][0]), MathHelper.lerp(f, points[a][1], points[b][1]),
                MathHelper.lerp(f, points[a][2], points[b][2]));
    }

    private static Vec3d vec(JsonArray p) { return new Vec3d(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()); }

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
