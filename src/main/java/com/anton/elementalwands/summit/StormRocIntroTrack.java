package com.anton.elementalwands.summit;

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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The Storm Roc intro's baked script, in game ticks: per tick, which half of the scene it is in
 * (0 at the aerie, 1 over the summit), the camera (eye, yaw, pitch, roll, lens), where the Roc and
 * the player are and how they are turned, and the overlays (cloud, storm dimming, wing shadow,
 * wand flash, how dark the Roc is drawn, letterbox, lightning). Plus the beats, cuts, lightning
 * strikes and sound cues. art/storm_roc/intro/build_intro.py writes it from the same performance as
 * both actors' clips; its "conventions" entry describes the frames. Samples between ticks are
 * interpolated, except across a cut, where a shot holds until it ends.
 */
public final class StormRocIntroTrack {
    public static final String RESOURCE = "/assets/elementalwands/cinematics/storm_roc_intro.json";
    private static StormRocIntroTrack loaded;

    /** A lightning strike: a bolt from the summit's cap, a sheet lighting the cloud, or sparks round the Roc's head. */
    public record Lightning(int tick, int half, String kind, Vec3d at) {}
    /** A sound to play: what, and where from ("roc_head", "roc", "player", "hand", "camera", "gong", or a point). */
    public record Cue(int tick, String cue, String from, Vec3d at) {}

    private final int[] half, pov;
    private final float[][] eye, roc, player, rocHead, hand;
    private final float[] yaw, pitch, roll, fov, white, dim, shade, beat, wand, dark, bars, flash;
    private final boolean[] cut;
    private final Map<String, Integer> beats = new HashMap<>();
    private final List<Lightning> lightning = new ArrayList<>();
    private final List<Cue> cues = new ArrayList<>();
    private final List<String> povHides = new ArrayList<>();
    private final double cloudBase;
    private final Vec3d arrival, homePerch;

    private StormRocIntroTrack(JsonObject json) {
        JsonObject t = json.getAsJsonObject("ticks");
        half = ints(t.getAsJsonArray("half"));
        pov = ints(t.getAsJsonArray("pov"));
        eye = rows(t.getAsJsonArray("eye"));
        roc = rows(t.getAsJsonArray("roc"));
        player = rows(t.getAsJsonArray("player"));
        rocHead = rows(t.getAsJsonArray("roc_head"));
        hand = rows(t.getAsJsonArray("hand"));
        yaw = values(t.getAsJsonArray("yaw"));
        pitch = values(t.getAsJsonArray("pitch"));
        roll = values(t.getAsJsonArray("roll"));
        fov = values(t.getAsJsonArray("fov"));
        white = values(t.getAsJsonArray("white"));
        dim = values(t.getAsJsonArray("dim"));
        shade = values(t.getAsJsonArray("shade"));
        beat = values(t.getAsJsonArray("beat"));
        wand = values(t.getAsJsonArray("wand"));
        dark = values(t.getAsJsonArray("dark"));
        bars = values(t.getAsJsonArray("bars"));
        flash = values(t.getAsJsonArray("flash"));
        cut = new boolean[eye.length];
        for (JsonElement c : json.getAsJsonArray("cuts")) cut[c.getAsInt()] = true;
        json.getAsJsonObject("beats").entrySet().forEach(e -> beats.put(e.getKey(), e.getValue().getAsInt()));
        for (JsonElement e : json.getAsJsonArray("lightning")) {
            JsonObject o = e.getAsJsonObject();
            lightning.add(new Lightning(o.get("tick").getAsInt(), o.get("half").getAsInt(), o.get("kind").getAsString(), vec(o.getAsJsonArray("at"))));
        }
        for (JsonElement e : json.getAsJsonArray("cues")) {
            JsonObject o = e.getAsJsonObject();
            JsonElement from = o.get("from");
            boolean point = from.isJsonArray();
            cues.add(new Cue(o.get("tick").getAsInt(), o.get("cue").getAsString(), point ? "point" : from.getAsString(),
                    point ? vec(from.getAsJsonArray()) : Vec3d.ZERO));
        }
        for (JsonElement e : json.getAsJsonArray("pov_hides")) povHides.add(e.getAsString());
        cloudBase = json.getAsJsonObject("aerie").get("cloud_base").getAsDouble();
        JsonObject summit = json.getAsJsonObject("summit");
        arrival = vec(summit.getAsJsonArray("arrival"));
        homePerch = vec(summit.getAsJsonArray("home_perch"));
    }

    /** The script in the mod's jar; the server and the client read the same file. */
    public static StormRocIntroTrack get() {
        if (loaded == null) {
            try (InputStream in = StormRocIntroTrack.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
                loaded = new StormRocIntroTrack(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return loaded;
    }

    public int length() { return eye.length - 1; }
    public int beat(String name) {
        Integer tick = beats.get(name);
        if (tick == null) throw new IllegalArgumentException("No beat " + name + " in " + RESOURCE);
        return tick;
    }
    public List<Lightning> lightning() { return lightning; }
    public List<Cue> cues() { return cues; }
    /** The player model's bones hidden while the camera is in their eyes. */
    public List<String> povHides() { return povHides; }
    /** The aerie's storm cloud starts this high above where the player struck the gong. */
    public double cloudBase() { return cloudBase; }
    /** Where the fight expects the player and the Roc, in the summit's frame. */
    public Vec3d arrival() { return arrival; }
    public Vec3d homePerch() { return homePerch; }

    /** 0 while the scene is at the aerie, 1 over the summit. */
    public int half(double t) { return half[index(t)]; }
    /** The camera is in the player's own eyes: their head, body and arms are not drawn. */
    public boolean pov(double t) { return pov[index(t)] == 1; }
    public Vec3d eye(double t) { return point(eye, t); }
    public float yaw(double t) { return angle(yaw, t); }
    public float pitch(double t) { return value(pitch, t); }
    /** Degrees; positive lowers the view's right side. */
    public float roll(double t) { return value(roll, t); }
    public float fov(double t) { return value(fov, t); }
    public Vec3d rocPos(double t) { return point(roc, t); }
    public Quaternionf rocTurn(double t) { return turn(roc, t); }
    public Vec3d playerPos(double t) { return point(player, t); }
    public Quaternionf playerTurn(double t) { return turn(player, t); }
    public Vec3d rocHead(double t) { return point(rocHead, t); }
    public Vec3d hand(double t) { return point(hand, t); }
    /** The storm cloud closing round the camera, 0 to about .2 (the client adds fog and cloud). */
    public float white(double t) { return value(white, t); }
    /** The storm darkening the aerie, 0 to about .45. */
    public float dim(double t) { return value(dim, t); }
    /** The Roc's wing shadow crossing the view on a downstroke, and how far down it has crossed (0 top, 1 bottom). */
    public float shade(double t) { return value(shade, t); }
    public float shadeAcross(double t) { return value(beat, t); }
    /** The wand strike's flash, 1 at the strike and dying away. */
    public float wand(double t) { return value(wand, t); }
    /** How near black the Roc is drawn, 0 (its own colours) to about .85. */
    public float dark(double t) { return value(dark, t); }
    /** The letterbox, 0 to 1. */
    public float bars(double t) { return value(bars, t); }
    /** How bright lightning is lighting everything, 0 to 1. */
    public float flash(double t) { return value(flash, t); }

    private int index(double t) { return MathHelper.clamp((int)Math.floor(t), 0, length()); }
    /** The sample to blend toward, or the same one when a cut comes next. */
    private int next(int a) { return a >= length() || cut[a + 1] ? a : a + 1; }
    private static float fraction(double t, int a, int b) { return a == b ? 0 : (float)MathHelper.clamp(t - a, 0, 1); }

    private float value(float[] values, double t) {
        int a = index(t), b = next(a);
        return MathHelper.lerp(fraction(t, a, b), values[a], values[b]);
    }

    private float angle(float[] values, double t) {
        int a = index(t), b = next(a);
        return a == b ? values[a] : MathHelper.lerpAngleDegrees(fraction(t, a, b), values[a], values[b]);
    }

    private Vec3d point(float[][] rows, double t) {
        int a = index(t), b = next(a);
        float f = fraction(t, a, b);
        return new Vec3d(MathHelper.lerp(f, rows[a][0], rows[b][0]), MathHelper.lerp(f, rows[a][1], rows[b][1]), MathHelper.lerp(f, rows[a][2], rows[b][2]));
    }

    /** The rotation in an actor's row ([x, y, z, w, qx, qy, qz]), slerped; consecutive rows keep a positive dot. */
    private Quaternionf turn(float[][] rows, double t) {
        int a = index(t), b = next(a);
        Quaternionf q = new Quaternionf(rows[a][4], rows[a][5], rows[a][6], rows[a][3]);
        return a == b ? q : q.slerp(new Quaternionf(rows[b][4], rows[b][5], rows[b][6], rows[b][3]), fraction(t, a, b));
    }

    private static Vec3d vec(JsonArray a) { return new Vec3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()); }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    private static float[] values(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static float[][] rows(JsonArray a) {
        float[][] out = new float[a.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray row = a.get(i).getAsJsonArray();
            out[i] = new float[row.size()];
            for (int j = 0; j < row.size(); j++) out[i][j] = row.get(j).getAsFloat();
        }
        return out;
    }
}
