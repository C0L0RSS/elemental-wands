package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Native placeholder model, animation, spell VFX and minion renderer screenshots. */
public final class NecromancerClientSmoke implements ClientModInitializer {
    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks, scene, floor;
    private UUID bossId;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 1500) throw new AssertionError("Necromancer client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                c.createIntegratedServerLoader().createAndStart("necromancer-" + System.currentTimeMillis(),
                        new LevelInfo("Necromancer verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            var server = c.getServer(); var uuid = c.player.getUuid();
            if (!arranged) {
                arranged = true;
                server.execute(() -> {
                    try {
                        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
                        for (int x = -12; x <= 12; x++) for (int z = -8; z <= 20; z++) {
                            w.setBlockState(new BlockPos(x, floor - 1, z), ((x + z) % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).getDefaultState());
                            for (int y = 0; y < 9; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
                        }
                        w.setTimeOfDay(13500); w.setWeather(6000, 0, false, false);
                        var p = server.getPlayerManager().getPlayer(uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        var boss = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
                        boss.refreshPositionAndAngles(.5, floor, 4.5, 180, 0);
                        boss.setBodyYaw(180); boss.setHeadYaw(180);
                        w.spawnEntity(boss); boss.stopFight(); bossId = boss.getUuid();
                        p.networkHandler.requestTeleport(.5, floor, .5, 0, 0); ready = true;
                    } catch (Throwable e) { serverFailure = e.toString(); }
                });
                return;
            }
            if (serverFailure != null) throw new AssertionError(serverFailure);
            if (!ready) return;
            int t = ++scene;
            c.setScreen(null); WandWelcome.reset();
            c.options.hudHidden = true;
            if (t < 40) look(c, 0, 12);
            if (t == 35) {
                var boss = boss(c);
                require(boss != null, "Necromancer missing on client");
                require(c.getEntityRenderDispatcher().getRenderer(boss) instanceof com.anton.elementalwands.client.renderer.NecromancerRenderer, "Wrong necromancer renderer");
                shot(c, "necromancer-front.png");
            }
            if (t == 40) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(3.8, floor, 4.5, 90, 10));
            if (t > 42 && t < 60) look(c, 90, 10);
            if (t == 55) shot(c, "necromancer-side.png");
            if (t == 60) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5, floor, .5, 0, 10));
            if (t > 62) look(c, 0, 10);
            if (t == 65) cast(server, uuid, Action.BOLT);
            if (t == 72) shot(c, "necromancer-bolt-windup.png");
            if (t == 84) shot(c, "necromancer-bolt.png");
            if (t == 110) cast(server, uuid, Action.HANDS);
            if (t == 128) shot(c, "necromancer-hands-telegraph.png");
            if (t == 143) shot(c, "necromancer-hands.png");
            if (t == 160) cast(server, uuid, Action.DRAIN);
            if (t == 185) shot(c, "necromancer-drain.png");
            if (t == 250) cast(server, uuid, Action.RAISE);
            if (t == 262) shot(c, "necromancer-raise-cast.png");
            if (t == 284) shot(c, "necromancer-raise-rising.png");
            if (t == 305) {
                boolean skeleton = false, zombie = false;
                for (var e : c.world.getEntities()) {
                    Object renderer = c.getEntityRenderDispatcher().getRenderer(e);
                    skeleton |= renderer instanceof com.anton.elementalwands.client.renderer.SpectralMinionRenderers.Skeleton;
                    zombie |= renderer instanceof com.anton.elementalwands.client.renderer.SpectralMinionRenderers.Zombie;
                }
                require(skeleton || zombie, "No spectral minion rendered");
                shot(c, "necromancer-minions.png");
            }
            if (t == 320) cast(server, uuid, Action.BLINK);
            if (t == 325) shot(c, "necromancer-blink.png");
            if (t == 345) shot(c, "necromancer-curse.png");
            // Phase two: the transformation from a wide front view, then the colossus and its attacks.
            if (t == 355) server.execute(() -> {
                var p = server.getPlayerManager().getPlayer(uuid);
                if (server.getOverworld().getEntity(bossId) instanceof NecromancerEntity boss) {
                    boss.stopFight();
                    boss.refreshPositionAndAngles(.5, floor, 6.5, 180, 0); boss.setBodyYaw(180); boss.setHeadYaw(180);
                    boss.requestTransform();
                }
                p.networkHandler.requestTeleport(.5, floor + 1, -5.5, 0, 8);
            });
            if (t > 357 && t < 470) look(c, 0, 8);
            for (int frame = 0; frame < 8; frame++) if (t == 362 + frame * 13) shot(c, "necromancer-transform-" + frame + ".png");
            if (t == 470) {
                var boss = boss(c);
                require(boss != null && boss.isColossus() && !boss.isTransforming(), "Client did not see the finished colossus");
                require(Math.abs(boss.getWidth() - com.anton.elementalwands.entity.necromancer.NecromancerRules.COLOSSUS_WIDTH) < .01, "Client hitbox did not grow");
                shot(c, "necromancer-colossus-front.png");
            }
            if (t == 472) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(10.5, floor + 1, 6.5, 90, 10));
            if (t > 474 && t < 490) look(c, 90, 10);
            if (t == 488) shot(c, "necromancer-colossus-side.png");
            if (t == 490) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5, floor + 1, -5.5, 0, 8));
            if (t > 492 && t < 592) look(c, 0, 8);
            if (t >= 592) look(c, -25, 12);
            if (t == 495) cast(server, uuid, Action.SWIPE);
            if (t == 509) shot(c, "necromancer-colossus-swipe-windup.png");
            if (t == 514) shot(c, "necromancer-colossus-swipe.png");
            if (t == 540) cast(server, uuid, Action.BOLT);
            if (t == 552) shot(c, "necromancer-colossus-roar.png");
            if (t == 590) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(6.5, floor + 1, -7.5, -25, 12));
            if (t == 595) cast(server, uuid, Action.LUNGE);
            if (t == 608) shot(c, "necromancer-colossus-lunge-mark.png");
            if (t == 624) shot(c, "necromancer-colossus-lunge-air.png");
            if (t >= 650) {
                Files.writeString(Path.of("NECRO_PASSED.txt"), "Hollow Necromancer native client passed: placeholder GeckoLib model and renderer, front/side views, bolt, hands, drain, raise, spectral minion renderers, blink and curse screenshots, transformation sequence, synchronized colossus form and hitbox, swipe, roar and lunge screenshots. Visual review and human Lunar playtest pending.\n");
                done = true; c.scheduleStop();
            }
        } catch (Throwable e) {
            done = true; e.printStackTrace();
            try { Files.writeString(Path.of("NECRO_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            c.scheduleStop();
        }
    }

    private void cast(net.minecraft.server.MinecraftServer server, UUID player, Action action) {
        server.execute(() -> {
            var w = server.getOverworld();
            if (w.getEntity(bossId) instanceof NecromancerEntity boss) boss.testAction(server.getPlayerManager().getPlayer(player), action);
            else serverFailure = "Boss missing for " + action;
        });
    }
    private NecromancerEntity boss(MinecraftClient c) {
        for (var e : c.world.getEntities()) if (e instanceof NecromancerEntity boss) return boss;
        return null;
    }
    private static void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setPitch(pitch); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
