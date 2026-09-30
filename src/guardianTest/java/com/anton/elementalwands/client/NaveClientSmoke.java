package com.anton.elementalwands.client;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.arena.ShatteredNave;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianIntro;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Native look at the Shattered Nave: enters a slot, photographs the hall from the floor (its fog,
 * light and shafts), then plays the Guardian's intro with this player holding the heart, checks it
 * hands over to the fight on the seat, and leaves. Screenshots land in
 * build/nave-client-smoke/screenshots/ for review; they are not a substitute for a Lunar look.
 * -PintroVideo hides the test window and saves every tick of the intro for review (ffmpeg -framerate 20).
 */
public final class NaveClientSmoke implements ClientModInitializer {
    private boolean started, done;
    private int ticks, scene, stage;
    private volatile String serverFailure;

    /** Viewpoints on slot 0's floor, relative to its centre: x, z, yaw, pitch, y above the floor. */
    private static final double[][] VIEWS = {
            {.5, 28.5, 180, -6, 1}, {.5, 28.5, 180, -68, 1}, {.5, -58.5, 180, -10, 1},
            {-58.5, 58.5, 45, -12, 1}, {7.5, 8.5, -143, -4, 1}, {40.5, 40.5, 135, 38, 36}};
    private static final String[] NAMES = {"arrival", "look-up", "edge", "corner", "seat", "high"};
    private static final boolean INTRO_VIDEO = Boolean.getBoolean("nave.introVideo");
    /** Scene ticks after the summon: early beats, slam, fully visible title, late title hold and camera return. */
    private static final int[] STILLS = {30, 62, 86, 104, 113, 136, 168, 206, 220,
            GuardianIntro.TITLE + 16, GuardianIntro.TITLE_END - 16, GuardianIntro.RETURN + 6};

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 8000) throw new AssertionError("Nave client timeout at stage " + stage);
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                if (INTRO_VIDEO) GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(16);
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.createIntegratedServerLoader().createAndStart("nave-" + System.currentTimeMillis(),
                        new LevelInfo("Nave verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(4011L, true, false), WorldPresets::createDemoOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean inRealm = c.world.getRegistryKey() == ShatteredNave.WORLD;
            switch (stage) {
                case 0 -> {
                    if (++scene < 20) return;
                    onServer(server, () -> {
                        var p = player(server, uuid);
                        p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        p.getAbilities().invulnerable = true; p.sendAbilitiesUpdate();
                        run(server, p, "ew nave enter");
                    });
                    next();
                }
                case 1 -> { // a viewpoint every 50 ticks; the first waits for chunks to arrive
                    if (!inRealm) return;
                    int view = (++scene - 60) / 50, at = (scene - 60) % 50;
                    if (scene < 60) return;
                    if (view >= VIEWS.length) { next(); return; }
                    double[] v = VIEWS[view];
                    if (at == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = ShatteredNave.nearestCentre(p.getEntityPos());
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = v[4] > 1; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(centre.getX() + v[0], ShatteredNave.SURFACE_Y + v[4], centre.getZ() + v[1], (float) v[2], (float) v[3]);
                    });
                    look(c, (float) v[2], (float) v[3]);
                    if (at == 45) shot(c, "nave-" + NAMES[view] + ".png");
                }
                case 2 -> { // the Guardian's intro, with this player holding out the heart, then the fight on the seat
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = ShatteredNave.nearestCentre(p.getEntityPos());
                        p.getAbilities().flying = false; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(centre.getX() + .5, ShatteredNave.SURFACE_Y + 1, centre.getZ() + 28.5, 180, 0);
                    });
                    if (scene == 20) onServer(server, () -> run(server, player(server, uuid), "ew nave summon"));
                    int t = scene - 20, end = GuardianIntro.LENGTH + 30;
                    if (t > 0 && t <= GuardianIntro.LENGTH + 6) {
                        c.options.hudHidden = false;
                        if (t == 24) require(GuardianIntroClient.cinematic(), "The intro did not take the camera");
                        if (t == 24) require(GuardianIntroClient.reach(c.player.getId()) > .99f, "The caller's arm is not held out with the heart");
                        if (INTRO_VIDEO) shot(c, String.format("nave-intro-video-%03d.png", t));
                        for (int i = 0; i < STILLS.length; i++) if (t == STILLS[i]) shot(c, "nave-intro-" + (i + 1) + ".png");
                    }
                    if (t == GuardianIntro.LENGTH + 20) require(!GuardianIntroClient.cinematic(), "The intro never handed the camera back");
                    if (t == end) onServer(server, () -> {
                        var p = player(server, uuid);
                        var guardian = server.getWorld(ShatteredNave.WORLD).getEntitiesByClass(FracturedGuardianEntity.class,
                                p.getBoundingBox().expand(60), e -> e.isAlive()).stream().findFirst().orElseThrow();
                        require(!guardian.inIntro() && GuardianArenaManager.isFighting(guardian), "The intro never handed over to the fight");
                        require(guardian.getEntityPos().distanceTo(ShatteredNave.seat(ShatteredNave.nearestCentre(p.getEntityPos()))) < 1.5,
                                "The Guardian woke off its seat");
                        guardian.stopReview();
                    });
                    if (t == end + 10) { look(c, 180, -8); shot(c, "nave-guardian.png"); }
                    if (t == end + 15) { onServer(server, () -> run(server, player(server, uuid), "ew nave leave")); next(); }
                }
                case 3 -> {
                    if (inRealm) return;
                    if (++scene < 20) return;
                    Files.writeString(Path.of("NAVE_CLIENT_PASSED.txt"), "Entered the nave, photographed six viewpoints, played the Guardian's intro into the fight and left.\n");
                    done = true; c.scheduleStop();
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("NAVE_CLIENT_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true; c.scheduleStop();
        }
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
    private static void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setPitch(pitch); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
