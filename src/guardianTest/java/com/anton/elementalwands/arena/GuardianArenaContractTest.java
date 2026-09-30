package com.anton.elementalwands.arena;

import com.anton.elementalwands.entity.GuardianIntro;
import com.anton.elementalwands.entity.GuardianIntroTrack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import static com.anton.elementalwands.arena.GuardianArenaRules.*;
import static com.anton.elementalwands.arena.ShatteredNave.*;

/** World-free checks of the Shattered Nave's geometry and the Guardian's intro cinematic. */
public final class GuardianArenaContractTest {
    public static void run() throws Exception {
        BlockPos c = centre(2, 5);
        require(c.equals(new BlockPos((2 * SLOTS + 5) * SLOT_SPACING, SURFACE_Y, 0)), "Slot centres moved");
        require(SLOT_SPACING > 2 * FOOTPRINT, "Neighbouring slots overlap");
        require(nearestCentre(Vec3d.of(c).add(250, 90, -250)).equals(c), "A point in a slot maps to another slot");

        // Tiles cover the authored hall, which covers the walled floor.
        int from = TILE_ORIGIN, to = TILE_ORIGIN + TILE * TILE_COUNT;
        require(from <= -HALF - 1 && to >= HALF + 1 && -from <= FOOTPRINT && to <= FOOTPRINT, "Tiles and the slot footprint disagree");

        // The walled floor: 128 blocks square, a lid above, and escapes corrected back inside.
        require(inPlay(c, Vec3d.of(c).add(-HALF + .5, 1, HALF - .5), .4), "The floor's corner is out of play");
        for (Vec3d escape : List.of(Vec3d.of(c).add(100, 1, 0), Vec3d.of(c).add(0, -20, 0), Vec3d.of(c).add(0, CEILING + 20, 0),
                new Vec3d(Double.NaN, 0, 0), new Vec3d(c.getX(), SURFACE_Y + 5, Double.POSITIVE_INFINITY))) {
            require(!inPlay(c, escape, .4), "Escape destination accepted: " + escape);
            if (Double.isFinite(escape.z) && Double.isFinite(escape.x))
                require(inPlay(c, clampToPlay(c, escape), .4), "Boundary correction is itself outside");
        }
        require(inPlay(c, Vec3d.of(c).add(30, 30, -30), .4), "Valid aerial Wind/Space movement was blocked");

        // Where players arrive, watch and fight all lie on the floor.
        require(inPlay(c, arrival(c), 1) && inPlay(c, seat(c), 1) && inPlay(c, gallery(c), 1), "Arrival, seat or gallery is outside the floor");
        require(arrival(c).z > seat(c).z && ARRIVAL_YAW == 180, "Players do not arrive facing the seat");
        require(RISE_DELAY > 0 && VICTORY_DELAY > WIPE_DELAY, "Arrival or return timing is broken");
        require(ease(-5) == 0 && ease(9) == 1 && ease(.5) == .5, "Motion easing overshoots its endpoints");

        for (double[] shaft : ShatteredNaveShafts.SHAFTS)
            require(shaft.length == 7 && shaft[3] > CEILING && shaft[5] > 0, "A light shaft is malformed");
        intro(c);
        System.out.println("Guardian nave checks passed: slot spacing, tile coverage, walled floor, escape correction, arrival, light shafts and the intro's script.");
    }

    /** The intro's beats, its baked track and both actors' clips on one clock, and where its camera and zombie go. */
    private static void intro(BlockPos c) throws Exception {
        int[] beats = {0, GuardianIntro.SKIP_AFTER, GuardianIntro.STOP, GuardianIntro.TURN, GuardianIntro.LOOK, GuardianIntro.FALL,
                GuardianIntro.POV, GuardianIntro.IMPACT, GuardianIntro.RISE, GuardianIntro.GRIP, GuardianIntro.SHOW, GuardianIntro.GRAB, GuardianIntro.TEAR,
                GuardianIntro.TOSS, GuardianIntro.HURL, GuardianIntro.PLAYERS, GuardianIntro.POINT, GuardianIntro.LENGTH};
        for (int i = 1; i < beats.length; i++) require(beats[i] > beats[i - 1], "Intro beats out of order at " + i);
        require(GuardianIntro.LENGTH <= 20 * 25, "Intro too long to sit through every fight");
        require(GuardianIntro.TITLE > GuardianIntro.POINT && GuardianIntro.TITLE_END <= GuardianIntro.LENGTH
                && GuardianIntro.RETURN > GuardianIntro.POINT && GuardianIntro.RETURN < GuardianIntro.LENGTH, "Title or hand-back outside the scene");

        // The track is baked from the same beats as the Java scene.
        GuardianIntroTrack track = GuardianIntroTrack.get();
        require(track.length() == GuardianIntro.LENGTH, "The camera track is off the scene's clock");
        for (var beat : List.of(new Object[]{"stop", GuardianIntro.STOP}, new Object[]{"turn", GuardianIntro.TURN}, new Object[]{"look", GuardianIntro.LOOK},
                new Object[]{"fall", GuardianIntro.FALL}, new Object[]{"pov", GuardianIntro.POV}, new Object[]{"impact", GuardianIntro.IMPACT},
                new Object[]{"rise", GuardianIntro.RISE}, new Object[]{"grip", GuardianIntro.GRIP}, new Object[]{"show", GuardianIntro.SHOW}, new Object[]{"grab", GuardianIntro.GRAB},
                new Object[]{"tear", GuardianIntro.TEAR}, new Object[]{"toss", GuardianIntro.TOSS}, new Object[]{"hurl", GuardianIntro.HURL},
                new Object[]{"players", GuardianIntro.PLAYERS}, new Object[]{"point", GuardianIntro.POINT}, new Object[]{"title", GuardianIntro.TITLE},
                new Object[]{"title_end", GuardianIntro.TITLE_END}, new Object[]{"ret", GuardianIntro.RETURN}, new Object[]{"length", GuardianIntro.LENGTH}))
            require(track.beat((String)beat[0]) == (int)beat[1], "The baked track's " + beat[0] + " beat differs from the scene's");

        // Arriving players face the Guardian; the camera stays in the walled hall; the zombie's halves
        // leave the shot before the fight, the top half clearing the players' heads.
        Vec3d seat = seat(c), feet = arrival(c);
        float face = GuardianIntro.facing(feet, seat);
        require(Math.abs(face - ARRIVAL_YAW) < .01 || Math.abs(Math.abs(face) - 180) < .01, "Arriving players do not face the Guardian");
        for (int t = 0; t <= GuardianIntro.LENGTH; t++) {
            Vec3d eye = GuardianIntro.place(seat, 0, track.eye(t));
            require(inPlay(c, eye, .2) && eye.y > SURFACE_Y + 1.2, "The camera leaves the hall's air at tick " + t + ": " + eye);
            require(track.fov(t) > 10 && track.fov(t) < 100 && Math.abs(track.pitch(t)) <= 90, "An impossible camera at tick " + t);
        }
        double over = track.event("over_players");
        Vec3d half = GuardianIntro.place(seat, 0, track.upper(over));
        require(Math.abs(half.z - feet.z) < 1.5 && half.y > feet.y + 2.5, "The thrown half does not clear the players' heads: " + half);
        require(track.event("legs_gone") < GuardianIntro.PLAYERS && track.event("half_gone") < GuardianIntro.POINT,
                "A half of the zombie is still in the air when the Guardian points");

        // Both actors' clips run on the scene's clock.
        for (var clip : List.of(new String[]{"fractured_guardian", "animation.fractured_guardian.intro"},
                new String[]{"guardian_intro_zombie", "animation.guardian_intro_zombie.intro"})) {
            try (var in = GuardianArenaContractTest.class.getClassLoader().getResourceAsStream("assets/elementalwands/geckolib/animations/" + clip[0] + ".animation.json")) {
                require(in != null, clip[0] + " animations are not on the classpath");
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonObject("animations").getAsJsonObject(clip[1]);
                require(json != null && Math.abs(json.get("animation_length").getAsDouble() - GuardianIntro.LENGTH / 20.0) < 1e-6,
                        "The installed " + clip[1] + " clip is missing or off the scene's clock");
            }
        }
    }

    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
