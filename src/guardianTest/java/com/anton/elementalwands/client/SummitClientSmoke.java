package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.summit.StormSummit;
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
 * Native look at the Storm Summit: enters a slot, photographs the summit, the spires and the sea of
 * clouds, checks that the remains were placed, that a survival player can't break the rock and that
 * one who falls off the rim is set back on the summit, then leaves. Screenshots land in
 * build/summit-client-smoke/screenshots/; they are not a substitute for a Lunar look.
 */
public final class SummitClientSmoke implements ClientModInitializer {
    private boolean started, done;
    private int ticks, scene, stage;
    private volatile String serverFailure;
    private volatile boolean rescued;

    /** Viewpoints on slot 0, relative to its centre: x, z, yaw, pitch, y above the summit surface. */
    private static final double[][] VIEWS = {
            {.5, 32.5, 180, -8, 1}, {.5, .5, 180, -16, 1}, {52.5, 20.5, -63, 8, 1}, {56.5, 21.5, -70, 62, 6},
            {-38.5, 7.5, 114, 12, 2.4}, {25.5, 21.5, -146, 35, 1.4}, {64.5, 64.5, 135, 26, 40}};
    private static final String[] NAMES = {"arrival", "centre", "rim", "over-edge", "shrine", "remains", "high"};

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 6000) throw new AssertionError("Summit client timeout at stage " + stage);
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(16);
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.createIntegratedServerLoader().createAndStart("summit-" + System.currentTimeMillis(),
                        new LevelInfo("Summit verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(4011L, true, false), WorldPresets::createDemoOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean inRealm = c.world.getRegistryKey() == StormSummit.WORLD;
            switch (stage) {
                case 0 -> {
                    if (++scene < 20) return;
                    onServer(server, () -> {
                        var p = player(server, uuid);
                        p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        p.getAbilities().invulnerable = true; p.sendAbilitiesUpdate();
                        run(server, p, "ew summit enter");
                    });
                    next();
                }
                case 1 -> { // a viewpoint every 50 ticks; the first waits for chunks to arrive
                    if (!inRealm) return;
                    int view = (++scene - 80) / 50, at = (scene - 80) % 50;
                    if (scene < 80) return;
                    if (view >= VIEWS.length) { next(); return; }
                    double[] v = VIEWS[view];
                    if (at == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = StormSummit.nearestCentre(p.getEntityPos());
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = v[4] > 1; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(centre.getX() + v[0], StormSummit.SURFACE_Y + v[4], centre.getZ() + v[1], (float) v[2], (float) v[3]);
                    });
                    look(c, (float) v[2], (float) v[3]);
                    if (at == 45) shot(c, "summit-" + NAMES[view] + ".png");
                }
                case 2 -> { // the layout, protection and the fall rescue
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        var realm = server.getWorld(StormSummit.WORLD);
                        BlockPos centre = StormSummit.nearestCentre(p.getEntityPos());
                        var relics = BlockPos.stream(centre.add(-60, 1, -60), centre.add(60, 1, 60))
                                .filter(pos -> ModBlocks.SUMMIT_RELICS.contains(realm.getBlockState(pos).getBlock())).count();
                        require(relics >= 10, "Expected the summit's remains, found " + relics);
                        BlockPos rock = centre.add(0, 0, 5);
                        require(!realm.getBlockState(rock).isAir(), "No rock at the summit surface");
                        p.getAbilities().flying = false; p.getAbilities().allowFlying = false; p.sendAbilitiesUpdate();
                        p.interactionManager.tryBreakBlock(rock);
                        require(!realm.getBlockState(rock).isAir(), "A survival player broke the summit");
                        // Off the east rim and dropping: the wind should set them back on the summit.
                        p.networkHandler.requestTeleport(centre.getX() + 75.5, StormSummit.SURFACE_Y - 8, centre.getZ() + .5, -90, 0);
                    });
                    if (scene == 30) onServer(server, () -> {
                        var p = player(server, uuid);
                        var arrival = StormSummit.arrival(StormSummit.nearestCentre(p.getEntityPos()));
                        rescued = p.getEntityPos().distanceTo(arrival) < 3;
                        require(rescued, "A player off the rim was not set back on the summit: " + p.getEntityPos());
                    });
                    if (scene == 40) { onServer(server, () -> run(server, player(server, uuid), "ew summit leave")); next(); }
                }
                case 3 -> {
                    if (inRealm) return;
                    if (++scene < 20) return;
                    Files.writeString(Path.of("SUMMIT_CLIENT_PASSED.txt"), "Entered the summit, photographed " + VIEWS.length
                            + " viewpoints, found its remains, kept its rock, caught a fall and left.\n");
                    done = true; c.scheduleStop();
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("SUMMIT_CLIENT_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
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
