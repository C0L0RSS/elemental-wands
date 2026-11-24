package com.anton.elementalwands.client;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Hosts a server fixture inside a singleplayer world, for a machine with no dedicated-server
 * EULA on file. The fixture is the same class the dedicated runner loads; this only supplies a
 * server for it and closes the client once the fixture writes its receipt. Fixtures written
 * for the flat test world stage their scene around y=100, so that box is cleared to air here.
 * The one real player watches from far away as a spectator: a real client cannot track the
 * fixtures' simulated players, and must not stand in their scene.
 */
public final class IntegratedFixtureHost implements ClientModInitializer {
    private static final String WORLDGEN_SEED = System.getProperty("ew.fixture.worldgenSeed");
    private static final String WORLD_NAME = System.getProperty("ew.fixture.world");
    private static final boolean RESUME = Boolean.getBoolean("ew.fixture.resume");
    private int ticks, left;
    private boolean started, joined, done;
    public void onInitializeClient() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (WORLDGEN_SEED != null || RESUME) return;
            var world = server.getOverworld();
            for (int x = -48; x <= 80; x++) for (int z = -24; z <= 48; z++) for (int y = 96; y <= 180; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            player.changeGameMode(GameMode.SPECTATOR);
            player.teleport(server.getOverworld(), 4000.5, 200, 4000.5, java.util.Set.of(), 0, 0, false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        String receipt = System.getProperty("ew.fixture.receipt", "NATURE");
        if (!started) {
            started = true; c.options.pauseOnLostFocus = false;
            var loader = c.createIntegratedServerLoader();
            String name = WORLD_NAME == null ? "fixture-host-" + System.currentTimeMillis() : "fixture-host-" + WORLD_NAME;
            if (RESUME) {
                loader.start(name, c::scheduleStop);
                return;
            }
            if (WORLD_NAME != null && Files.exists(c.runDirectory.toPath().resolve("saves").resolve(name))) {
                throw new IllegalStateException("Choose a new disposable fixture world name: " + name);
            }
            boolean worldgen = WORLDGEN_SEED != null;
            loader.createAndStart(name,
                    // Survival, as on the test server: a Creative attacker would damage invulnerable targets.
                    new LevelInfo("Server fixture host", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                            new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(worldgen ? Long.parseLong(WORLDGEN_SEED) : 813L, worldgen, false),
                    worldgen ? WorldPresets::createDemoOptions : WorldPresets::createTestOptions, null);
            return;
        }
        if (c.world != null) joined = true;
        // The fixture stops the server when it finishes; a world lost without a receipt is a failure.
        if (joined && c.world == null) left++;
        if (Files.exists(Path.of(receipt + "_PASSED.txt")) || Files.exists(Path.of(receipt + "_FAILED.txt"))
                || left > 100 || ++ticks > 12000) {
            done = true; c.scheduleStop();
        }
    }
}
