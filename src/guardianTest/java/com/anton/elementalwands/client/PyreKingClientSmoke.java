package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.PyreKingRenderer;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.pyreking.PyreKingClips;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Native screenshots of the Pyre King's body: his guard from the front, side and close up, then
 * every clip at its key moments from a camera that holds the thrown sword and the chains.
 * Each clip is played by the same rehearsal the /ew pyreking command uses. The window stays
 * hidden and the game muted. This shows how he draws in the game; it judges nothing about a fight.
 */
public final class PyreKingClientSmoke implements ClientModInitializer {
    /** A clip, the camera it is seen from, and the ticks after its start at which to take a picture. */
    private record Scene(String clip, String view, int... shots) {}

    private static final List<Scene> SCENES = List.of(
            new Scene("idle", "front", 20), new Scene("idle", "side", 20), new Scene("idle", "close", 20),
            new Scene("walk", "side", 12), new Scene("run", "side", 12),
            new Scene("royal_cut", "close", 18, 20, 38), new Scene("royal_cut", "front", 20, 38),
            new Scene("kingsguard_thrust", "side", 16, 19), new Scene("turning_cut", "front", 12, 15, 17),
            new Scene("overhead_sentence", "side", 14, 16, 17), new Scene("royal_cut_three", "side", 53, 55),
            new Scene("cast_reel", "wide", 16, 26, 76), new Scene("chain_scythe_low", "wide", 12, 18, 21),
            new Scene("chain_scythe_high", "wide", 12, 18, 21), new Scene("drag_down", "wide", 15, 20, 26),
            new Scene("hook", "wide", 18, 24, 36), new Scene("zip", "wide", 10, 17, 28), new Scene("lunge", "wide", 9, 22, 34),
            new Scene("haul", "wide", 5, 10, 14, 18), new Scene("snare", "wide", 5, 8, 12, 30),
            new Scene("unbound", "wide", 13, 26, 35, 46, 60), new Scene("unbound", "close", 26, 35));

    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks, floor, index = -1, began = -1000;
    private UUID king;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 6000) throw new AssertionError("Pyre King client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(60);
                c.createIntegratedServerLoader().createAndStart("pyre-king-" + System.currentTimeMillis(),
                        new LevelInfo("Pyre King verification", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            var server = c.getServer(); var uuid = c.player.getUuid();
            if (!arranged) {
                arranged = true;
                server.execute(() -> {
                    try { arrange(server, uuid); ready = true; } catch (Throwable e) { serverFailure = e.toString(); }
                });
                return;
            }
            if (serverFailure != null) throw new AssertionError(serverFailure);
            if (!ready) return;
            c.setScreen(null); WandWelcome.reset();
            c.options.hudHidden = true;
            if (index < 0) {
                // The clips the server will name must all be in the baked animation file.
                var baked = software.bernie.geckolib.cache.GeckoLibResources.getBakedAnimations().get(Identifier.of("elementalwands", "pyre_king"));
                require(baked != null, "The Pyre King's animations were not loaded");
                for (PyreKingClips.Clip clip : PyreKingClips.ALL) require(baked.getAnimation(clip.animation()) != null, "Missing clip " + clip.animation());
            }
            Scene scene = index < 0 ? null : SCENES.get(index);
            int length = scene == null ? 0 : scene.shots[scene.shots.length - 1] + 12;
            if (scene == null || ticks - began > length) {
                if (++index >= SCENES.size()) {
                    Files.writeString(Path.of("PYRE_KING_CLIENT_PASSED.txt"), "Pyre King native client passed: GeckoLib renderer, every clip baked and "
                            + "rehearsed, screenshots of each in the run directory. He has no fight yet. Human Lunar review pending.\n");
                    done = true; c.scheduleStop();
                    return;
                }
                Scene next = SCENES.get(index);
                began = ticks + 30;   // time for the camera to arrive and the last clip to end
                server.execute(() -> stage(server, uuid, next));
                return;
            }
            int t = ticks - began;
            if (t == 0) {
                var entity = clientKing(c);
                require(entity != null, "The Pyre King never reached the client");
                require(c.getEntityRenderDispatcher().getRenderer(entity) instanceof PyreKingRenderer, "Wrong renderer for the Pyre King");
                server.execute(() -> { if (server.getOverworld().getEntity(king) instanceof PyreKingEntity k) k.rehearse(scene.clip); });
            }
            // The rehearsal reaches the client a tick or two after the server starts it.
            for (int shot : scene.shots) if (t == shot + 2) shot(c, "pyre-king-" + scene.clip + "-" + scene.view + "-" + shot + ".png");
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("PYRE_KING_CLIENT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void arrange(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        for (int x = -34; x <= 34; x++) for (int z = -40; z <= 24; z++) {
            w.setBlockState(new BlockPos(x, floor - 1, z), ((x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.BLACKSTONE).getDefaultState());
            for (int y = 0; y < 16; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        w.setTimeOfDay(13000); w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        var p = server.getPlayerManager().getPlayer(uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false));
        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
        PyreKingEntity mob = ModEntities.PYRE_KING.spawn(w, BlockPos.ofFloored(.5, floor, .5), SpawnReason.COMMAND);
        require(mob != null, "Could not spawn the Pyre King");
        // Facing north: his clips throw the sword toward -Z.
        mob.refreshPositionAndAngles(.5, floor, .5, 180, 0);
        mob.setBodyYaw(180); mob.setHeadYaw(180);
        king = mob.getUuid();
    }

    /** Stop the last clip and fly the camera to this scene's view of him. */
    private void stage(MinecraftServer server, UUID uuid, Scene scene) {
        if (server.getOverworld().getEntity(king) instanceof PyreKingEntity k) k.stopRehearsal();
        double[] eye = switch (scene.view) {
            case "front" -> new double[]{.5, 2.2, -9, .5, 2.6, .5};
            case "side" -> new double[]{-10, 2.4, -1.5, .5, 2.6, -1.5};
            case "close" -> new double[]{-4, 3.2, -5, .5, 3.0, -.5};
            default -> new double[]{-13, 7, 6, .5, 1.5, -4};   // wide: the throw line and the chains' swing
        };
        double dx = eye[3] - eye[0], dy = eye[4] - eye[1], dz = eye[5] - eye[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        var p = server.getPlayerManager().getPlayer(uuid);
        p.getAbilities().flying = true; p.sendAbilitiesUpdate();
        // The eye stands 1.62 above the feet.
        p.networkHandler.requestTeleport(eye[0], floor + eye[1] - 1.62, eye[2], yaw, pitch);
    }

    private static PyreKingEntity clientKing(MinecraftClient c) {
        List<PyreKingEntity> list = new ArrayList<>();
        for (var e : c.world.getEntities()) if (e instanceof PyreKingEntity k) list.add(k);
        return list.isEmpty() ? null : list.get(0);
    }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
