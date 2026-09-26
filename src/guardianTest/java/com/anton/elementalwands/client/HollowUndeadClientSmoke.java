package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.HollowUndeadRenderer;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.undead.HollowUndeadEntity;
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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
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
 * Native GeckoLib screenshots of the Hollow undead: rise, idle front/side, walking, each
 * attack at its key frame, and death. Invisible, frozen iron golems under Resistance V are the targets
 * so the real AI walks and strikes toward the camera. The window stays hidden.
 */
public final class HollowUndeadClientSmoke implements ClientModInitializer {
    private static final double[] LANES = {-3.5, .5, 4.5};
    /** Screenshot offsets into each attack clip: crawler rake, archer full draw, brute windup and impact. */
    private static final int[][] ATTACK_SHOTS = {{12}, {45}, {28, 43}};
    private static final String[] NAMES = {"crawler", "archer", "brute"};
    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks, scene, floor, killed = -1;
    private final int[] strike = {-1, -1, -1};
    private final List<UUID> mobs = new ArrayList<>(), stands = new ArrayList<>();

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 2400) throw new AssertionError("Hollow undead client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(60);
                c.createIntegratedServerLoader().createAndStart("undead-" + System.currentTimeMillis(),
                        new LevelInfo("Hollow undead verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
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
            int t = ++scene;
            c.setScreen(null); WandWelcome.reset();
            c.options.hudHidden = true;
            if (t < 125 || t > 135) look(c, 0, 8);
            if (t == 16) shot(c, "undead-rise-claws.png");
            if (t == 45) shot(c, "undead-rise-haul.png");
            if (t == 100) {
                for (var e : c.world.getEntities()) if (e instanceof HollowUndeadEntity)
                    require(c.getEntityRenderDispatcher().getRenderer(e) instanceof HollowUndeadRenderer<?>, "Wrong renderer for " + e.getType());
                require(clientMobs(c).size() == 3, "Not all three undead reached the client");
                require(clientMobs(c).stream().noneMatch(HollowUndeadEntity::isRising), "A rise never ended on the client");
                shot(c, "undead-idle-front.png");
            }
            if (t == 105) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(10.5, floor, 6.5, 90, 8));
            if (t > 106 && t < 125) look(c, 90, 8);
            if (t == 122) shot(c, "undead-idle-side.png");
            if (t == 125) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5, floor, -3.5, 0, 8));
            // Release the AI: each body walks toward its target, then strikes.
            if (t == 140) server.execute(() -> hunt(server));
            if (t == 158) shot(c, "undead-walk.png");
            if (t > 140 && killed < 0) {
                List<HollowUndeadEntity> list = clientMobs(c);
                for (HollowUndeadEntity mob : list) {
                    int i = mobs.indexOf(mob.getUuid());
                    if (i < 0) continue;
                    if (strike[i] < 0 && mob.isStriking()) strike[i] = t;
                    if (strike[i] >= 0) for (int offset : ATTACK_SHOTS[i])
                        if (t == strike[i] + HollowUndeadEntity.BLEND + offset) shot(c, "undead-attack-" + NAMES[i] + "-" + offset + ".png");
                }
                boolean allShot = true;
                for (int i = 0; i < 3; i++) allShot &= strike[i] >= 0 && t > strike[i] + HollowUndeadEntity.BLEND + ATTACK_SHOTS[i][ATTACK_SHOTS[i].length - 1];
                if (allShot || t == 900) {
                    for (int i = 0; i < 3; i++) require(strike[i] >= 0, NAMES[i] + " never attacked its target");
                    killed = t;
                    server.execute(() -> kill(server, uuid));
                }
            }
            if (killed > 0 && t == killed + 14) shot(c, "undead-death.png");
            if (killed > 0 && t == killed + 36) {
                shot(c, "undead-dead.png");
                Files.writeString(Path.of("UNDEAD_CLIENT_PASSED.txt"), "Hollow undead native client passed: GeckoLib renderer for all three, rise, idle, walk, "
                        + "each attack observed from its synced clip, death; screenshots in the run directory. Human Lunar review pending.\n");
                done = true; c.scheduleStop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("UNDEAD_CLIENT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void arrange(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        for (int x = -12; x <= 14; x++) for (int z = -10; z <= 16; z++) {
            w.setBlockState(new BlockPos(x, floor - 1, z), ((x + z) % 2 == 0 ? Blocks.COARSE_DIRT : Blocks.PODZOL).getDefaultState());
            for (int y = 0; y < 9; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        w.setTimeOfDay(18000); w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        var p = server.getPlayerManager().getPlayer(uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false));
        p.networkHandler.requestTeleport(.5, floor, -1.5, 0, 8);
        List<EntityType<? extends HollowUndeadEntity>> types = List.of(ModEntities.HOLLOW_CRAWLER, ModEntities.HOLLOW_ARCHER, ModEntities.HOLLOW_BRUTE);
        for (int i = 0; i < 3; i++) {
            HollowUndeadEntity mob = types.get(i).spawn(w, BlockPos.ofFloored(LANES[i], floor, 6.5), SpawnReason.COMMAND);
            require(mob != null, "Could not spawn " + NAMES[i]);
            mob.refreshPositionAndAngles(LANES[i], floor, 6.5, 180, 0);
            mob.setBodyYaw(180); mob.setHeadYaw(180);
            mob.setAiDisabled(true);
            mobs.add(mob.getUuid());
        }
    }

    private void hunt(MinecraftServer server) {
        var w = server.getOverworld();
        for (int i = 0; i < 3; i++) {
            var stand = new IronGolemEntity(EntityType.IRON_GOLEM, w);
            stand.refreshPositionAndAngles(LANES[i], floor, 1, 0, 0);
            stand.setAiDisabled(true);
            // Resistance V, not invulnerability: an invulnerable body is never a valid target.
            stand.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100000, 4, false, false));
            stand.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 100000, 0, false, false));
            w.spawnEntity(stand); stands.add(stand.getUuid());
            if (w.getEntity(mobs.get(i)) instanceof HollowUndeadEntity mob) { mob.setAiDisabled(false); mob.setTarget(stand); }
        }
    }

    private void kill(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld();
        for (UUID id : stands) if (w.getEntity(id) != null) w.getEntity(id).discard();
        for (UUID id : mobs) if (w.getEntity(id) instanceof HollowUndeadEntity mob)
            mob.damage(w, w.getDamageSources().playerAttack(server.getPlayerManager().getPlayer(uuid)), 1000);
    }

    private List<HollowUndeadEntity> clientMobs(MinecraftClient c) {
        List<HollowUndeadEntity> list = new ArrayList<>();
        for (var e : c.world.getEntities()) if (e instanceof HollowUndeadEntity mob) list.add(mob);
        return list;
    }
    private static void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setPitch(pitch); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
