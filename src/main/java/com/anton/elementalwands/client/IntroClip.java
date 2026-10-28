package com.anton.elementalwands.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A baked GeckoLib clip sampled at any time we ask for, rather than played by an animation
 * controller on its own clock: a cinematic's actors follow the scene's clock, which can pause.
 * Channels are linearly interpolated between keys; a bare [x, y, z] is constant. Values are as in
 * the file (degrees, model pixels, scale).
 */
public final class IntroClip {
    private record Channel(double[] time, float[][] value) {
        float[] at(double t) {
            int i = Arrays.binarySearch(time, t);
            if (i >= 0) return value[i];
            i = -i - 1;
            if (i == 0) return value[0];
            if (i >= time.length) return value[value.length - 1];
            float f = (float)((t - time[i - 1]) / (time[i] - time[i - 1]));
            float[] v = new float[3];
            for (int a = 0; a < 3; a++) v[a] = value[i - 1][a] + f * (value[i][a] - value[i - 1][a]);
            return v;
        }
    }

    /** A bone's channels at one moment: rotation (degrees), position (pixels), scale. */
    public record Pose(float[] rotation, float[] position, float[] scale) {}

    private final Map<String, Channel[]> bones = new LinkedHashMap<>();
    private final double ticks;

    private IntroClip(JsonObject clip) {
        ticks = clip.get("animation_length").getAsDouble() * 20;
        clip.getAsJsonObject("bones").entrySet().forEach(b -> {
            JsonObject ch = b.getValue().getAsJsonObject();
            bones.put(b.getKey(), new Channel[]{channel(ch.get("rotation"), 0), channel(ch.get("position"), 0), channel(ch.get("scale"), 1)});
        });
    }

    /** The named clip from a GeckoLib animation file in the mod's jar. */
    public static IntroClip load(String resource, String name) {
        try (InputStream in = IntroClip.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing " + resource);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new IntroClip(json.getAsJsonObject("animations").getAsJsonObject(name));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
    }

    public double ticks() { return ticks; }

    /** Every bone the clip moves, posed at this tick. */
    public Map<String, Pose> pose(double tick) {
        double t = Math.clamp(tick, 0, ticks);
        Map<String, Pose> out = new LinkedHashMap<>();
        bones.forEach((bone, ch) -> out.put(bone, new Pose(ch[0].at(t), ch[1].at(t), ch[2].at(t))));
        return out;
    }

    private static Channel channel(JsonElement e, float fallback) {
        if (e == null) return new Channel(new double[]{0}, new float[][]{{fallback, fallback, fallback}});
        if (e.isJsonArray()) return new Channel(new double[]{0}, new float[][]{vector(e)});
        TreeMap<Double, float[]> sorted = new TreeMap<>();
        e.getAsJsonObject().entrySet().forEach(k -> sorted.put(Double.parseDouble(k.getKey()) * 20, vector(k.getValue())));
        return new Channel(sorted.keySet().stream().mapToDouble(Double::doubleValue).toArray(), sorted.values().toArray(float[][]::new));
    }

    private static float[] vector(JsonElement e) {
        var a = e.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
