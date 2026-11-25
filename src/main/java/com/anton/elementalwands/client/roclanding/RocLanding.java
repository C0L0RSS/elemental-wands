package com.anton.elementalwands.client.roclanding;

import com.anton.elementalwands.summit.StormRocIntroTrack;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

/**
 * An element's landing in the Storm Roc intro, ported from its browser study
 * (art/storm_roc/intro/landing_&lt;element&gt;.js, or animatic.js for Nature's). Each frame the
 * scene asks it for its effects over the summit and for anything it lays over the screen. It only
 * draws: the track, baked by art/storm_roc/intro/&lt;element&gt;_landing.py, already moves the
 * camera and both actors, and plays the sounds.
 */
public interface RocLanding {
    /** Adds this frame's effects to s.mesh(). */
    void draw(Scene s);

    /**
     * Draws anything this frame's draw built outside the mesh (a landing reusing a spell's own
     * mesh, such as client.blockfire.BlockFireMesh), after the mesh. Positions camera-relative.
     */
    default void emit(net.minecraft.client.render.VertexConsumerProvider consumers, net.minecraft.client.util.math.MatrixStack.Entry entry) {}

    /** Lays this frame's screen effects over the view (vignettes, speed lines, flashes); shown fades them with the hand-back. */
    default void hud(DrawContext context, Scene s, int w, int h, double shown) {}

    /**
     * One frame of the scene, as the animatic's sample s: the scene time t in game ticks (the
     * track's), the story clock ft that effects age on (slowing with any slow motion), and the
     * track's columns by name at t.
     */
    record Scene(StormRocIntroTrack track, double t, double ft, LandingMesh mesh) {
        public float col(String name) { return track.column(name, t); }
        public float colAt(String name, double at) { return track.column(name, at); }
        public double[] pt(String name) { return vec(track.points(name, t)); }
        public double[] ptAt(String name, double at) { return vec(track.points(name, at)); }
        /** The player model's origin (their feet) in the summit's frame. */
        public double[] player() { return vec(track.playerPos(t)); }
        public double[] playerAt(double at) { return vec(track.playerPos(at)); }
        /** The landing's own facts from its bake. */
        public JsonObject facts() { return track.facts(); }
        /** The summit's snow under the arrival. */
        public double ground() { return track.arrival().y; }
        /** The story clock at scene time at (game ticks), for the age of something that happened then. */
        public double clockAt(double at) { return track.hasColumn("n_clock") ? track.column("n_clock", at) : at; }
        public double fxAge(double tick) { return ft - clockAt(tick); }

        public static double[] vec(Vec3d p) { return new double[]{p.x, p.y, p.z}; }
        public static double[] vec(JsonArray a) { return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()}; }
    }
}
