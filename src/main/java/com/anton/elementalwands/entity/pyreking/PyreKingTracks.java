package com.anton.elementalwands.entity.pyreking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Where the Pyre King's weapon is at every moment of every attack clip: his wrist shackle, the
 * sword's pommel and its tip, sampled from the baked clips by art/pyre_king/build_art.py
 * (pyre_king_tracks.json). Points are in the frame he started the clip in, in blocks: x to his
 * right, y up, z ahead of him. The server sweeps these for hits and the client reads them to aim
 * a thrown sword, so what is drawn is what strikes.
 */
public final class PyreKingTracks {
    private PyreKingTracks() {}

    /** One clip's samples. Every accessor takes seconds into the clip and clamps to its ends. */
    public static final class Track {
        public final int samples;
        public final double seconds;
        private final float[] wrist, pommel, tip, travel, chest;
        private final float[][] chains;

        private Track(JsonObject json) {
            samples = json.get("samples").getAsInt();
            seconds = (samples - 1) / (double) RATE;
            wrist = floats(json.getAsJsonArray("wrist"));
            pommel = floats(json.getAsJsonArray("pommel"));
            tip = floats(json.getAsJsonArray("tip"));
            travel = json.has("travel") ? floats(json.getAsJsonArray("travel")) : null;
            chest = json.has("chest") ? floats(json.getAsJsonArray("chest")) : null;
            if (json.has("chains")) {
                JsonArray all = json.getAsJsonArray("chains");
                chains = new float[all.size()][];
                for (int i = 0; i < chains.length; i++) chains[i] = floats(all.get(i).getAsJsonArray());
            } else chains = new float[0][];
        }

        public double[] wrist(double t) { return point(wrist, t); }
        public double[] pommel(double t) { return point(pommel, t); }
        public double[] tip(double t) { return point(tip, t); }
        /** Where Unbound's chains leave his chest. */
        public double[] chest(double t) { return point(chest, t); }
        public int chains() { return chains.length; }
        public double[] chainEnd(int chain, double t) { return point(chains[chain], t); }
        /** How far forward the chain has hauled him (Zip, Lunge); 0 for every other clip. */
        public double travel(double t) {
            if (travel == null) return 0;
            double at = at(t);
            int i = (int) at;
            if (i >= samples - 1) return travel[samples - 1];
            return travel[i] + (travel[i + 1] - travel[i]) * (at - i);
        }
        public double travelEnd() { return travel == null ? 0 : travel[samples - 1]; }

        private double at(double t) { return Math.clamp(t * RATE, 0, samples - 1); }

        private double[] point(float[] from, double t) {
            double at = at(t);
            int i = Math.min((int) at, samples - 2);
            double f = at - i;
            int a = i * 3, b = a + 3;
            return new double[]{from[a] + (from[b] - from[a]) * f, from[a + 1] + (from[b + 1] - from[a + 1]) * f,
                    from[a + 2] + (from[b + 2] - from[a + 2]) * f};
        }
    }

    public static final int RATE;
    public static final double SWORD_BLOCKS;
    private static final Map<String, Track> TRACKS = new HashMap<>();

    static {
        try (var in = PyreKingTracks.class.getResourceAsStream("/data/elementalwands/pyre_king_tracks.json")) {
            if (in == null) throw new IllegalStateException("pyre_king_tracks.json is missing: run art/pyre_king/build_art.py --install");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            RATE = root.get("rate").getAsInt();
            SWORD_BLOCKS = root.get("sword_blocks").getAsDouble();
            JsonObject clips = root.getAsJsonObject("clips");
            for (String name : clips.keySet()) TRACKS.put(name, new Track(clips.getAsJsonObject(name)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read pyre_king_tracks.json", e);
        }
    }

    /** The clip's track; null for clips that are not attacks (idle, walk, run). */
    public static Track get(String clip) { return TRACKS.get(clip); }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsFloat();
        return out;
    }
}
