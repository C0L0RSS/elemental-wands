package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.entity.pyreking.PyreKingIntro;
import com.anton.elementalwands.entity.pyreking.PyreKingIntroTrack;
import com.anton.elementalwands.hall.PyreHall;
import com.anton.elementalwands.hall.PyreHallManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * The Pyre King's intro in a disposable integrated world, hidden and muted: a Survival player
 * enters the hall, an operator command starts the scene, and the whole of it plays through the
 * scene's own camera. Checks that the hall goes dark and is lit again, that he ends on his mark
 * and fights, and that a skip ends it at once with the hall lit. Saves a still at each beat, and
 * with -PpyreIntroVideo a frame every other tick (intro-*.png) for a review clip.
 */
public final class PyreKingIntroSmoke implements ClientModInitializer {
    private static final int EVERY = Integer.getInteger("pyreIntroEvery", 0);

    private boolean started, done;
    private int ticks, scene, stage, frames;
    private volatile String serverFailure;
    private volatile boolean entered, began, judged, skipJudged;
    private volatile int firesDark = -1, firesLit = -1, skipTicks = -1;
    private volatile String ending = "";
    private boolean sawCinematic;
    private int length;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 9000) throw new AssertionError("Pyre King intro timeout at stage " + stage);
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(16);
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.options.write();
                c.createIntegratedServerLoader().createAndStart("pyre-king-intro-" + System.currentTimeMillis(),
                        new LevelInfo("Pyre King intro verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(4013L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean inHall = c.world.getRegistryKey() == PyreHall.WORLD;
            length = PyreKingIntroTrack.get().length();
            switch (stage) {
                case 0 -> {
                    if (++scene < 20) return;
                    onServer(server, () -> {
                        var p = player(server, uuid);
                        p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        run(server, p, "ew hall enter");
                    });
                    next();
                }
                case 1 -> { // the slot is laid out, the player is in, and the client has built what it will draw
                    if (++scene > 900) throw new AssertionError("Never entered the hall, or its terrain never finished building; " + PyreHallManager.status());
                    if (!inHall) return;
                    if (!entered) { onServer(server, () -> { if (PyreHallManager.slotReady(0)) entered = true; }); return; }
                    if (scene < 160 || !c.worldRenderer.isTerrainRenderComplete()) return;
                    onServer(server, () -> {
                        var p = player(server, uuid);
                        p.changeGameMode(GameMode.SURVIVAL);
                        run(server, p, "ew pyreking intro");
                        ServerWorld hall = (ServerWorld) p.getEntityWorld();
                        require(king(hall, p).inIntro(), "/ew pyreking intro did not start his intro");
                        require(PyreKingIntro.watching(p), "The player is not held by the intro");
                        firesDark = fires(hall, PyreHall.nearestCentre(p.getEntityPos()));
                        began = true;
                    });
                    next();
                }
                case 2 -> { // the whole scene, through its own camera
                    int t = scene++;
                    if (!began) { if (t > 60) throw new AssertionError("The intro never began"); scene = 0; return; }
                    if (PyreKingIntroClient.cinematic()) sawCinematic = true;
                    if (t == 30) {
                        require(sawCinematic, "The intro never took this client's camera");
                        require(firesDark == 0, "The hall's fires should be out as the intro begins; " + firesDark + " still burn in the braziers");
                    }
                    PyreKingIntroTrack track = PyreKingIntroTrack.get();
                    for (String beat : new String[]{"pulse", "burn", "fall", "struck", "grasp", "ignite", "lit", "title", "landed"})
                        if (t == track.beat(beat) + 6) shot(c, "pyre-king-intro-" + beat + ".png");
                    for (int still : new int[]{60, 160, 290, 350, 480, 560, 700, 730, 990, 1120}) if (t == still) shot(c, "pyre-king-intro-t" + still + ".png");
                    if (EVERY > 0 && t % EVERY == 0 && t <= length + 10) { shot(c, String.format(Locale.ROOT, "intro-%05d.png", t / EVERY)); frames++; }
                    if (t == length + 40) require(!PyreKingIntroClient.cinematic(), "The intro still holds the camera 2 s after it should have ended");
                    // Judged the tick it ends: a moment later he is fighting, and running at the player.
                    if (t >= length - 20 && !judged) {
                        onServer(server, () -> {
                            if (judged || king((ServerWorld) player(server, uuid).getEntityWorld(), player(server, uuid)).inIntro()) return;
                            var p = player(server, uuid);
                            ServerWorld hall = (ServerWorld) p.getEntityWorld();
                            BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                            PyreKingEntity k = king(hall, p);
                            Vec3d mark = PyreHall.king(centre);
                            double off = k.getEntityPos().distanceTo(mark);
                            require(!k.inIntro() && !PyreKingIntro.watching(p), "The intro did not let go");
                            require(off < 1.5, "He should end on his mark; he is " + off + " from it at " + k.getEntityPos());
                            require(k.isBossAggressive(), "The fight should follow the intro");
                            firesLit = fires(hall, centre);
                            require(firesLit >= 10 * 25, "The hall should be lit again after the intro; only " + firesLit + " flames burn in the braziers");
                            ending = "he ended " + String.format(Locale.ROOT, "%.2f", off) + " from his mark, fighting (" + k.status() + ")";
                            // The second pass, the skip, is watched from Creative so that the fight he has just begun does not kill the watcher.
                            p.changeGameMode(GameMode.CREATIVE);
                            p.setHealth(p.getMaxHealth());
                            judged = true;
                        });
                    }
                    if (t > length + 40 && judged) next();
                }
                case 3 -> { // again, and skipped: it ends at once, the hall lit and he on his mark
                    int t = scene++;
                    if (t == 5) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                        Vec3d arrival = PyreHall.arrival(centre);
                        p.networkHandler.requestTeleport(arrival.x, arrival.y, arrival.z, PyreHall.ARRIVAL_YAW, 0);
                        run(server, p, "ew pyreking intro");
                    });
                    if (t == 5 + PyreKingIntro.SKIP_AFTER + 40) onServer(server, () -> PyreKingIntro.skip(player(server, uuid)));
                    if (t == 5 + PyreKingIntro.SKIP_AFTER + 60) onServer(server, () -> {
                        var p = player(server, uuid);
                        ServerWorld hall = (ServerWorld) p.getEntityWorld();
                        BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                        PyreKingEntity k = king(hall, p);
                        require(!k.inIntro() && !PyreKingIntro.watching(p), "A skip by the only watcher should end the intro");
                        require(k.getEntityPos().distanceTo(PyreHall.king(centre)) < 1.5, "Skipped, he should be on his mark; he is at " + k.getEntityPos());
                        int lit = fires(hall, centre);
                        require(lit >= 10 * 25, "The hall should be lit after a skip; only " + lit + " flames burn");
                        skipJudged = true;
                    });
                    if (t > 5 + PyreKingIntro.SKIP_AFTER + 60 && skipJudged) {
                        require(!PyreKingIntroClient.cinematic(), "The skipped intro still holds the camera");
                        Files.writeString(Path.of("PYRE_KING_INTRO_PASSED.txt"), "The Pyre King's intro played for a Survival player in the Burned Throne Hall: " + length
                                + " ticks through the scene's own camera, the braziers dark at the start and " + firesLit + " flames burning after; " + ending
                                + ". Played again and skipped, it ended at once with the hall lit and him on his mark. Stills at each beat in screenshots/"
                                + (frames > 0 ? ", and " + frames + " video frames (intro-*.png)" : "") + ".\n");
                        done = true; c.scheduleStop();
                    }
                    if (t > 400) throw new AssertionError("The skip pass never finished");
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("PYRE_KING_INTRO_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true; c.scheduleStop();
        }
    }

    /** Flames burning in the great braziers' bowls, as the hall's own check counts them. */
    private static int fires(ServerWorld hall, BlockPos centre) {
        int fires = 0;
        for (int x = -49; x <= 49; x++)
            for (int z = -49; z <= 49; z++) {
                double r = Math.hypot(x, z);
                if (r > 48.5 || r <= 41.5) continue;
                for (int y = 15; y <= 20; y++) if (hall.getBlockState(PyreHall.cell(centre, x, y, z)).isOf(Blocks.FIRE)) fires++;
            }
        return fires;
    }

    private static PyreKingEntity king(ServerWorld hall, ServerPlayerEntity p) {
        var kings = hall.getEntitiesByClass(PyreKingEntity.class, PyreHall.footprint(PyreHall.nearestCentre(p.getEntityPos())), e -> true);
        require(kings.size() == 1, "Expected one Pyre King in the hall, found " + kings.size());
        return kings.get(0);
    }

    private void next() { stage++; scene = 0; }

    private void onServer(MinecraftServer server, Runnable action) {
        server.execute(() -> {
            try { action.run(); }
            catch (Throwable e) { serverFailure = e.toString(); }
        });
    }

    private static void run(MinecraftServer server, ServerPlayerEntity p, String command) {
        server.getCommandManager().parseAndExecute(p.getCommandSource(), command);
    }
    private static ServerPlayerEntity player(MinecraftServer server, UUID id) { return server.getPlayerManager().getPlayer(id); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
