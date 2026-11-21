package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.end.EndIntroTrack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.util.math.Vec3d;

/**
 * The change to phase two as art/first_wizard/change/build_change.py bakes it, read by every client (and the contract
 * check): one scene in the intro's format ({@link EndIntroTrack.Scene}), on the change's own clock (its tick 0 is the
 * change's first, {@link FirstWizardEntity#phaseAge}). Its beats are {@link com.anton.elementalwands.entity.firstwizard.spell.PhaseChange}'s.
 *
 * <p>It holds the camera's shots and the body's channels: {@code stagger}, {@code stagger_side}, the arm's
 * {@code wrench} (model pitch, yaw, roll while it is still on) and {@code gap}, the torn arm's middle {@code arm},
 * {@code arm_rot} (pitch, twist, roll), {@code arm_turn}, {@code flat} and {@code melt}, the seam's {@code seep}, the
 * {@code strands} and the {@code socket}'s ink, the {@code bulge} and its {@code veins}, the head's {@code bend} and
 * {@code head_yaw}, and the {@code lift}. Points are in the copy's frame at the change's start (+X her left, +Z ahead of
 * her, y up from her feet: {@link #place}); angles are the player model's.</p>
 */
public final class FirstWizardChangeTrack {
    public static final String RESOURCE = "/assets/elementalwands/cinematics/first_wizard_change.json";
    private static FirstWizardChangeTrack loaded;

    private final EndIntroTrack.Scene scene;
    private final double bendTwo;

    private FirstWizardChangeTrack(JsonObject json) {
        scene = EndIntroTrack.Scene.read("change", json.getAsJsonObject("scene"));
        bendTwo = json.get("bend_two").getAsDouble();
    }

    public static FirstWizardChangeTrack get() {
        if (loaded == null) {
            try (InputStream in = FirstWizardChangeTrack.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
                loaded = new FirstWizardChangeTrack(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return loaded;
    }

    public EndIntroTrack.Scene scene() { return scene; }
    /** The head's bend (radians, to her right) through phase two: a little less than the cinematic's peak. */
    public double bendTwo() { return bendTwo; }

    /** A point of the change's frame in the world: +Z ahead of the copy (facing yaw), +X on its left. */
    public static Vec3d place(Vec3d centre, float yaw, Vec3d local) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        return centre.add(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    }
}
