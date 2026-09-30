package com.anton.elementalwands.arena;

import com.anton.elementalwands.entity.GuardianIntro;
import com.anton.elementalwands.entity.necromancer.NecromancerIntro;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.util.Arm;
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

    /** The intro's beats, the heart's flight from the caller's hand into the core, and the baked clip. */
    private static void intro(BlockPos c) throws Exception {
        int[] beats = {0, GuardianIntro.SKIP_AFTER, GuardianIntro.LIFT, GuardianIntro.LAUNCH, GuardianIntro.IMPACT, GuardianIntro.WAKE,
                GuardianIntro.HEAD, GuardianIntro.RISE, GuardianIntro.STAND, GuardianIntro.WIND, GuardianIntro.CLAP, GuardianIntro.RELEASE,
                GuardianIntro.LENGTH};
        for (int i = 1; i < beats.length; i++) require(beats[i] > beats[i - 1], "Intro beats out of order at " + i);
        require(GuardianIntro.TITLE_END - GuardianIntro.TITLE == NecromancerIntro.TITLE_END - NecromancerIntro.TITLE,
                "The Guardian title does not hold as long as the Necromancer title");
        require(GuardianIntro.LENGTH - GuardianIntro.RETURN == 24, "The Guardian camera hand-back must last 24 ticks");
        require(GuardianIntro.TITLE > GuardianIntro.CLAP && GuardianIntro.TITLE_END <= GuardianIntro.RETURN
                && GuardianIntro.RETURN > GuardianIntro.CLAP && GuardianIntro.RETURN < GuardianIntro.LENGTH, "Title or hand-back outside the scene");

        // From the middle of the arrival line, facing the seat (north), the heart rests ahead of the
        // caller's right shoulder, which is on the east side.
        Vec3d seat = seat(c), feet = arrival(c);
        float face = GuardianIntro.facing(feet, seat);
        require(Math.abs(face - ARRIVAL_YAW) < .01 || Math.abs(Math.abs(face) - 180) < .01, "Arriving players do not face the Guardian");
        Vec3d hand = GuardianIntro.handOf(feet, face, Arm.RIGHT), core = GuardianIntro.place(seat, 0, GuardianIntro.CORE);
        require(hand.z < feet.z && hand.x > feet.x && Math.abs(hand.y - feet.y - GuardianIntro.HAND.y) < .01, "The heart is not on the right hand, held out ahead");
        require(GuardianIntro.handOf(feet, face, Arm.LEFT).x < feet.x, "A left-handed caller holds it in the right hand");
        require(GuardianIntro.heartAt(hand, core, 0).distanceTo(hand) < .06, "The heart does not start in the hand");
        require(GuardianIntro.heartAt(hand, core, GuardianIntro.IMPACT).distanceTo(core) < .01, "The heart does not reach the core on the strike");
        double previous = 0;
        for (int t = GuardianIntro.LAUNCH; t <= GuardianIntro.IMPACT; t++) {
            Vec3d at = GuardianIntro.heartAt(hand, core, t);
            require(inPlay(c, at, .4) && at.y > SURFACE_Y + 1.5, "The heart's flight leaves the floor's air: " + at);
            double flown = at.distanceTo(GuardianIntro.heartAt(hand, core, GuardianIntro.LAUNCH));
            require(flown >= previous - 1e-6, "The heart turns back in flight");
            previous = flown;
        }
        require(previous > 25, "The heart's flight is shorter than the nave's arrival line");

        // The clip must share the scene's clock, and the points the scene aims at come from the clip.
        try (var in = GuardianArenaContractTest.class.getClassLoader().getResourceAsStream("assets/elementalwands/geckolib/animations/fractured_guardian.animation.json")) {
            require(in != null, "Guardian animations are not on the classpath");
            JsonObject clip = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations").getAsJsonObject("animation.fractured_guardian.intro");
            require(clip != null && Math.abs(clip.get("animation_length").getAsDouble() - GuardianIntro.LENGTH / 20.0) < 1e-6,
                    "The installed intro clip is missing or off the scene's clock");
        }
        Path points = Path.of("art/fractured_guardian/intro/intro-points.json");
        if (Files.exists(points)) {
            JsonObject json = JsonParser.parseString(Files.readString(points)).getAsJsonObject(), beat = json.getAsJsonObject("beats");
            require(beat.get("length").getAsInt() == GuardianIntro.LENGTH && beat.get("impact").getAsInt() == GuardianIntro.IMPACT
                    && beat.get("wake").getAsInt() == GuardianIntro.WAKE && beat.get("head").getAsInt() == GuardianIntro.HEAD
                    && beat.get("rise").getAsInt() == GuardianIntro.RISE && beat.get("stand").getAsInt() == GuardianIntro.STAND
                    && beat.get("wind").getAsInt() == GuardianIntro.WIND && beat.get("clap").getAsInt() == GuardianIntro.CLAP
                    && beat.get("release").getAsInt() == GuardianIntro.RELEASE, "The authored clip's beats differ from the scene's");
            for (var point : List.of(new Object[]{"core_kneeling", GuardianIntro.CORE}, new Object[]{"fists_at_clap", GuardianIntro.FISTS},
                    new Object[]{"head_kneeling", GuardianIntro.HEAD_KNEELING}, new Object[]{"head_standing", GuardianIntro.HEAD_STANDING})) {
                var xyz = json.getAsJsonArray((String)point[0]);
                Vec3d measured = new Vec3d(xyz.get(0).getAsDouble(), xyz.get(1).getAsDouble(), xyz.get(2).getAsDouble());
                require(measured.distanceTo((Vec3d)point[1]) < .01, "Re-measure " + point[0] + " from the clip: " + measured);
            }
        }
    }

    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
