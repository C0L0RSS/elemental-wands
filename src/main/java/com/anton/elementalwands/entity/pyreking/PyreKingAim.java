package com.anton.elementalwands.entity.pyreking;

import java.util.HashMap;
import java.util.Map;

/**
 * Aiming a thrown sword. The clips throw it at one authored point (11.5 blocks out for the Cast,
 * 27 for the Hook, 8.5 up for Drag Down); the fight aims it at a real one. The difference between
 * the two is the aim offset, in the frame he started the clip in. This says how much of that
 * offset the sword and his own body take at each moment: none while the sword is in his hand, all
 * of it where the sword sticks, and for Zip and Lunge his body takes it as the chain hauls him.
 * The server (hits, his real travel) and the client (the drawn sword) both read it, so they agree.
 */
public final class PyreKingAim {
    private PyreKingAim() {}

    /** A clip that throws the sword, and how its throw is stretched. */
    public static final class Throw {
        private final PyreKingTracks.Track track;
        private final double near, far, low, high;
        private final boolean lifts;
        private final double[] stick;

        private Throw(String clip, double stickTime, double near, boolean lifts) {
            track = PyreKingTracks.get(clip);
            this.near = near;
            this.lifts = lifts;
            double[] pommel = track.pommel(stickTime), tip = track.tip(stickTime);
            far = Math.hypot(pommel[0], pommel[2]);
            low = 2.5;
            high = pommel[1];
            if (tip[1] < 0 && pommel[1] > 0) {      // driven into the floor: where the blade meets it
                double f = pommel[1] / (pommel[1] - tip[1]);
                stick = new double[]{pommel[0] + (tip[0] - pommel[0]) * f, 0, pommel[2] + (tip[2] - pommel[2]) * f};
            } else stick = tip;
        }

        /** Where the clip itself sends the sword (blocks: right, up, ahead). */
        public double[] stick() { return stick.clone(); }

        /** The share of the offset his body has taken: how far along its haul the chain has brought him. */
        public double body(double t) {
            double end = track.travelEnd();
            return end <= 0 ? 0 : Math.clamp(track.travel(t) / end, 0, 1);
        }

        /** The share of the sideways and forward offset the sword has taken. */
        public double sword(double t) {
            double[] pommel = track.pommel(t);
            double out = Math.clamp((Math.hypot(pommel[0], pommel[2]) - near) / (far - near), 0, 1);
            return Math.max(out, body(t));
        }

        /** The share of the upward offset the sword has taken: only Drag Down's throw climbs. */
        public double lift(double t) {
            if (!lifts) return sword(t);
            // His arm raises the sword before he lets go: only a sword that has left his hand climbs.
            double[] pommel = track.pommel(t), wrist = track.wrist(t);
            double loose = Math.clamp((Math.sqrt(square(pommel[0] - wrist[0]) + square(pommel[1] - wrist[1]) + square(pommel[2] - wrist[2])) - .9) / .5, 0, 1);
            return loose * Math.clamp((pommel[1] - low) / (high - low), 0, 1);
        }

        private static double square(double v) { return v * v; }

        /** Where his body is at t, moved by its share of the offset. */
        public double[] bodyOffset(double t, double dx, double dz) {
            double k = body(t);
            return new double[]{k * dx, 0, k * dz};
        }

        /** How far the sword is moved at t. */
        public double[] swordOffset(double t, double dx, double dy, double dz) {
            double s = sword(t);
            return new double[]{s * dx, lift(t) * dy, s * dz};
        }
    }

    private static final Map<String, Throw> THROWS = new HashMap<>();

    static {
        THROWS.put("cast_reel", new Throw("cast_reel", 1.3, 3.0, false));
        THROWS.put("zip", new Throw("zip", 0, 3.0, false));
        THROWS.put("lunge", new Throw("lunge", .85, 3.0, false));
        THROWS.put("hook", new Throw("hook", 1.35, 3.0, false));
        THROWS.put("drag_down", new Throw("drag_down", 1.0, 2.2, true));
    }

    /** The clip's throw; null for clips that keep the sword in hand or swing it at a fixed length. */
    public static Throw get(String clip) { return THROWS.get(clip); }
}
