package com.anton.elementalwands.client;

import com.anton.elementalwands.client.roclanding.FireLanding;
import com.anton.elementalwands.client.roclanding.LandingMesh;
import com.anton.elementalwands.client.roclanding.NatureLanding;
import com.anton.elementalwands.client.roclanding.RocLanding;
import com.anton.elementalwands.client.roclanding.SpaceLanding;
import com.anton.elementalwands.client.roclanding.StoneLanding;
import com.anton.elementalwands.client.roclanding.WindLanding;
import com.anton.elementalwands.summit.StormRocIntroTrack;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import com.anton.elementalwands.registry.ModSounds;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Storm Roc intro's element landing on this client: the effects of whichever
 * {@link RocLanding} the scene plays (its track names it), drawn over the summit each frame and
 * over the screen with the scene's letterbox. Nothing is drawn for the plain crash, at the aerie,
 * or once the scene is skipped or over. A landing draws in the summit's own frame, unturned: it
 * is given the camera brought back into that frame, and its drawing is turned to this player's
 * place in the arrival line as it is emitted.
 */
public final class StormRocLandingEffects {
    private static final Map<String, Supplier<RocLanding>> LANDINGS = Map.of(
            "fire", FireLanding::new, "wind", WindLanding::new, "stone", StoneLanding::new,
            "nature", NatureLanding::new, "space", SpaceLanding::new);
    private static final RenderStateDataKey<LandingMesh> MESH = RenderStateDataKey.create(() -> "Storm Roc landing");
    private static final LandingMesh NONE = new LandingMesh(new double[3], new double[3], new double[]{1, 0, 0}, new double[]{0, 1, 0});
    private static RocLanding current;

    private StormRocLandingEffects() {}

    static void init() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            context.worldState().setData(MESH, NONE);
            if (!showing()) return;
            float delta = context.tickCounter().getTickProgress(false);
            double t = StormRocIntroClient.time(delta);
            StormRocIntroTrack track = StormRocIntroClient.track();
            if (track.half(t) != 1 || StormRocIntroClient.drawnHalf(t) != 1) return;
            Vec3d camera = StormRocIntroClient.unturn(context.camera().getPos()), origin = StormRocIntroClient.summitOrigin();
            Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(StormRocIntroClient.frameYaw(1))).mul(context.camera().getRotation());
            Vector3f right = turn.transform(new Vector3f(1, 0, 0)), up = turn.transform(new Vector3f(0, 1, 0));
            var mesh = new LandingMesh(new double[]{origin.x, origin.y, origin.z}, new double[]{camera.x, camera.y, camera.z},
                    new double[]{right.x, right.y, right.z}, new double[]{up.x, up.y, up.z});
            current.draw(scene(track, t, mesh));
            context.worldState().setData(MESH, mesh);
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            LandingMesh mesh = context.worldState().getDataOrDefault(MESH, NONE);
            if (mesh == NONE || current == null) return;
            var matrices = context.matrices();
            matrices.push();
            matrices.multiply(StormRocIntroClient.turn(1, new Quaternionf()));
            mesh.emit(context.consumers(), matrices.peek());
            current.emit(context.consumers(), matrices.peek());
            matrices.pop();
        });
    }

    /** A landing cue's sound: the element's own spell sounds, so the landing sounds like its magic. */
    record Sound(SoundEvent event, float volume, float pitch) {}
    private static final Map<String, Sound> SOUNDS = Map.ofEntries(
            // Every landing
            Map.entry("catch_yourself", new Sound(ModSounds.CINEMATIC_STORM_ROC_INTRO_FLING, .6f, 1.4f)),
            Map.entry("slowmo_in", new Sound(ModSounds.CINEMATIC_STORM_ROC_INTRO_WIND, .7f, .5f)),
            Map.entry("skid", new Sound(ModSounds.ENTITY_STORM_ROC_STAMPEDE_SKID, .8f, 1.4f)),
            Map.entry("skid_stop", new Sound(ModSounds.ENTITY_STORM_ROC_STAMPEDE_SKID, .6f, 1.2f)),
            Map.entry("slide", new Sound(ModSounds.ENTITY_STORM_ROC_STAMPEDE_SKID, .8f, 1.3f)),
            // Nature
            Map.entry("pod_charge", new Sound(ModSounds.SPELL_NATURE_SEED_CAST, 1f, 1f)),
            Map.entry("pod_pitch", new Sound(ModSounds.SPELL_NATURE_SENTRY_BLOOM_THROW, 1f, 1f)),
            Map.entry("springbloom_burst", new Sound(ModSounds.SPELL_NATURE_SPRINGBLOOM_CAST, 1.2f, 1f)),
            Map.entry("springbloom_bounce", new Sound(ModSounds.SPELL_NATURE_SPRINGBLOOM_LAUNCH, 1.4f, 1f)),
            Map.entry("wand_charge", new Sound(ModSounds.SPELL_NATURE_OVERGROWTH_EMPOWER, 1f, 1f)),
            Map.entry("vine_shoot", new Sound(ModSounds.SPELL_NATURE_THORNBITE_CAST, 1.2f, 1f)),
            Map.entry("vine_bite", new Sound(ModSounds.SPELL_NATURE_THORNBITE_HIT, 1.2f, .9f)),
            Map.entry("vine_swing", new Sound(ModSounds.CINEMATIC_STORM_ROC_INTRO_WIND, .6f, 1.2f)),
            Map.entry("grass_trail", new Sound(ModSounds.SPELL_NATURE_SENTRY_BLOOM_GROW, 1f, 1f)),
            Map.entry("vine_release", new Sound(ModSounds.SPELL_NATURE_OVERGROWTH_END, .8f, 1.1f)),
            // Fire
            Map.entry("thruster_ignite", new Sound(ModSounds.SPELL_FIRE_FIRE_LEAP_LAUNCH, 1.2f, 1f)),
            Map.entry("thruster_roar", new Sound(ModSounds.SPELL_FIRE_FLAMETHROWER_LOOP, 1.2f, .9f)),
            Map.entry("flames_crackle", new Sound(ModSounds.SPELL_FIRE_FLASHOVER_CAST, .8f, 1f)),
            Map.entry("wand_heat", new Sound(ModSounds.SPELL_FIRE_FLASHOVER_CAST, .8f, 1.2f)),
            Map.entry("thruster_cut", new Sound(ModSounds.SPELL_FIRE_FLAMETHROWER_OVERHEAT, 1f, 1f)),
            Map.entry("fire_leap_land", new Sound(ModSounds.SPELL_FIRE_FIRE_LEAP_LAND, 1.2f, 1f)),
            Map.entry("fire_wave", new Sound(ModSounds.SPELL_FIRE_INFERNO_WAVE_CAST, 1f, 1f)),
            Map.entry("snow_hiss", new Sound(ModSounds.SPELL_FIRE_FLAMETHROWER_OVERHEAT, .7f, 1.3f)),
            // Wind
            Map.entry("waylay_dash", new Sound(ModSounds.SPELL_WIND_WAYLAY_DASH_CAST, 1.2f, 1f)),
            Map.entry("waylay_dash_chained", new Sound(ModSounds.SPELL_WIND_WAYLAY_DASH_CAST, 1.3f, 1.15f)),
            Map.entry("skim", new Sound(ModSounds.SPELL_WIND_UPDRAFT_CAST, .8f, 1.2f)),
            Map.entry("light_landing", new Sound(ModSounds.SPELL_WIND_ZEPHYR_STRIKE_LAND, 1f, 1.1f)),
            // Stone
            Map.entry("gathered_mass_cast", new Sound(ModSounds.SPELL_STONE_GATHERED_MASS_GATHER, 1.2f, 1f)),
            Map.entry("ground_tears", new Sound(ModSounds.SPELL_STONE_FAULTLINE_ERUPT, 1.2f, .9f)),
            Map.entry("ground_cracks", new Sound(ModSounds.SPELL_STONE_TITAN_DOME_COLLAPSE_CRACK, 1f, 1f)),
            Map.entry("rocks_pack_on", new Sound(ModSounds.SPELL_STONE_STONE_WALL_SETTLE, 1f, 1f)),
            Map.entry("ball_closes", new Sound(ModSounds.SPELL_STONE_TITAN_DOME_FORMED, 1.2f, .9f)),
            Map.entry("boulder_drop", new Sound(ModSounds.SPELL_STONE_GATHERED_MASS_LAUNCH, 1.2f, .8f)),
            Map.entry("boulder_impact", new Sound(ModSounds.SPELL_STONE_GATHERED_MASS_IMPACT, 1.6f, .8f)),
            Map.entry("ball_cracks", new Sound(ModSounds.SPELL_STONE_TITAN_DOME_COLLAPSE_CRACK, 1.2f, 1f)),
            Map.entry("ball_breaks", new Sound(ModSounds.SPELL_STONE_TITAN_DOME_COLLAPSE_BOOM, 1.2f, 1f)),
            // Space
            Map.entry("rift_open", new Sound(ModSounds.SPELL_SPACE_BLINK_RIFT_CAST, 1.2f, .9f)),
            Map.entry("rift_fold", new Sound(ModSounds.SPELL_SPACE_ASTRAL_DOUBLE_TELEPORT, 1.2f, 1f)),
            Map.entry("rift_unfold", new Sound(ModSounds.SPELL_SPACE_BLINK_RIFT_CAST, 1f, 1.2f)),
            Map.entry("dive_wind", new Sound(ModSounds.CINEMATIC_STORM_ROC_INTRO_WIND, .7f, 1.4f)),
            Map.entry("snow_burst", new Sound(ModSounds.SPELL_SPACE_SINGULARITY_BOLT_IMPACT, .8f, 1.1f)),
            Map.entry("footsteps", new Sound(SoundEvents.BLOCK_SNOW_STEP, .8f, 1f)),
            Map.entry("cuff_tug", new Sound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), .6f, 1.3f)),
            Map.entry("collar_snap", new Sound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), .7f, 1.1f)));

    /** The sound for a landing's own cue, or null for one the scene plays itself (or none). */
    static Sound sound(String cue) { return SOUNDS.get(cue); }

    /** A new scene: the landing its track names, or none for the plain crash. */
    static void start(String landing) {
        Supplier<RocLanding> make = LANDINGS.get(landing);
        current = make == null ? null : make.get();
    }

    private static boolean showing() {
        return current != null && StormRocIntroClient.active() && !StormRocIntroClient.finished() && !StormRocIntroClient.skipped();
    }

    private static RocLanding.Scene scene(StormRocIntroTrack track, double t, LandingMesh mesh) {
        double ft = track.hasColumn("n_clock") ? track.column("n_clock", t) : t;
        return new RocLanding.Scene(track, t, ft, mesh);
    }

    /** The landing's screen effects at scene time t, faded by shown as the view is handed back. */
    static void hud(DrawContext context, double t, int w, int h, double shown) {
        if (!showing()) return;
        current.hud(context, scene(StormRocIntroClient.track(), t, null), w, h, shown);
    }
}
