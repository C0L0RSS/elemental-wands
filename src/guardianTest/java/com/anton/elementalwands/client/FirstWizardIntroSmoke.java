package com.anton.elementalwands.client;

import com.anton.elementalwands.client.blockink.CopyForming;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.end.EndArena;
import com.anton.elementalwands.end.EndArenaManager;
import com.anton.elementalwands.end.EndEncounter;
import com.anton.elementalwands.end.EndIntro;
import com.anton.elementalwands.end.EndIntroTrack;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * The First Wizard's intro in a disposable integrated world, hidden and muted. A Survival player is taken to the End's
 * island by {@code /ew end begin} with the real boss, and the full scene plays through its own camera, a still at each
 * beat (and with -PwizardIntroVideo a frame every other tick). It checks: the player is held and unhurt; the pool is
 * overhead before and gone once the fight runs; the dragon is drawn during the scene and gone after it; the copy wears
 * the player's profile, is unformed until the pour, shows the player's skin from its first boot, and cannot be hurt
 * until the fight starts; the dragon's death is saved. Then a wipe brings the pool back; a second run plays only the
 * condensing and a skip by its only watcher ends it at once with the fight started; {@code /ew end intro full} plays
 * the dragon again, and a reset and the boss's removal mid-scene each leave nobody held; a win leaves the sky empty.
 */
public final class FirstWizardIntroSmoke implements ClientModInitializer {
    private static final int EVERY = Integer.getInteger("wizardIntroEvery", 0);

    private boolean started, done;
    private int ticks, scene, stage, frames, lastStill = -1;
    private volatile String serverFailure;
    private volatile boolean flag, judged;
    private volatile String note = "";
    private final List<String> report = new ArrayList<>();
    private boolean sawDragon, sawCinematic, sawPoolBefore, checkedSkin, dragonGone;
    private int stillsTaken;
    private float startHealth;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 24000) throw new AssertionError("First Wizard intro timeout at stage " + stage + " (" + EndEncounter.status() + ")");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                // Muted before any world loads.
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(12);
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.options.write();
                c.createIntegratedServerLoader().createAndStart("first-wizard-intro-" + System.currentTimeMillis(),
                        new LevelInfo("First Wizard intro verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(4019L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean inEnd = c.world.getRegistryKey() == EndArena.WORLD;
            switch (stage) {
                case 0 -> { // into the End as a visitor, until its arena is laid out and its terrain built
                    int t = scene++;
                    if (t > 2400) throw new AssertionError("Never reached the End's arena: " + EndEncounter.status());
                    if (!inEnd) {
                        if (t % 60 == 20) onServer(server, () -> {
                            var p = player(server, uuid);
                            p.setAttached(EWAttachments.WELCOME_SEEN, true);
                            run(server, p, "ew end enter");
                        });
                        return;
                    }
                    if (arrivedAt < 0) arrivedAt = t;
                    if (t - arrivedAt < 160 || !c.worldRenderer.isTerrainRenderComplete()) return;
                    sawPoolBefore = FirstWizardIntroClient.waitingPool(0) > .99 && FirstWizardIntroClient.scene() == null;
                    still(c, "first-wizard-intro-pool-waiting.png");
                    require(sawPoolBefore, "The pool should hang over the island before the first fight");
                    require(!EndArenaManager.dragonDead(server), "A new world's dragon should be alive");
                    next();
                }
                case 1 -> { // the full scene for a Survival player
                    if (scene++ == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        p.changeGameMode(GameMode.SURVIVAL);
                        p.setHealth(p.getMaxHealth());
                        run(server, p, "ew end begin");
                    });
                    if (scene > 600) throw new AssertionError("The intro never began: " + EndEncounter.status());
                    if (EndIntro.running() == null || FirstWizardIntroClient.scene() == null) return;
                    require(EndIntro.running().full(), "The first intro in a world should be the full one");
                    startHealth = c.player.getHealth();
                    onServer(server, () -> {
                        var p = player(server, uuid);
                        FirstWizardEntity copy = EndIntro.running().copy();
                        require(EndIntro.watching(p), "The player is not held by the intro");
                        require(copy.profile().getGameProfile().id().equals(p.getUuid()), "The copy should wear the first player's profile; it wears " + copy.profile());
                        require(copy.formed() < .01f, "The copy should not have formed before the pour; formed " + copy.formed());
                    });
                    next();
                }
                case 2 -> { // through the full scene's own camera
                    EndIntroTrack.Scene track = EndIntroTrack.get().scene(true);
                    double st = FirstWizardIntroClient.sceneTime(0);
                    int t = (int) Math.floor(st);
                    if (FirstWizardIntroClient.cinematic()) sawCinematic = true;
                    if (st >= 0) {
                        if (FirstWizardIntroDragon.shown()) sawDragon = true;
                        stills(c, track, t, true);
                        if (EVERY > 0 && t % EVERY == 0 && t != lastFrame) { lastFrame = t; shot(c, String.format(Locale.ROOT, "intro-%05d.png", t / EVERY)); frames++; }
                        if (t == 40 && !flag) { flag = true; checkHeld(server, uuid, true); }
                        if (t == track.beat("breath")) require(FirstWizardIntroDragon.shown(), "The dragon should be drawn as it breathes into the pool");
                        if (t >= track.beat("burst") + 20 && t < track.beat("burst") + 24) {
                            require(!FirstWizardIntroDragon.shown(), "The dragon should be gone after it bursts");
                            dragonGone = true;
                        }
                        if (t == track.beat("death") + 10) onServer(server, () -> require(EndArenaManager.dragonDead(server), "The dragon's death should be saved"));
                        if (t == track.beat("form") + 4 && !checkedSkin) { checkedSkin = true; checkSkin(c); }
                        if (t == track.beat("form") + 60) { flag = false; checkHeld(server, uuid, true); }
                        if (t == track.beat("formed") - 30) tryHurtCopy(server, uuid);
                    }
                    // Over: the fight runs, the player is let go, the sky is empty.
                    if (EndIntro.running() == null && FirstWizardIntroClient.scene() == null && st < 0) {
                        require(sawCinematic, "The intro never took this client's camera");
                        require(sawDragon && dragonGone, "The dragon should be drawn during the scene and gone after it");
                        require(stillsTaken >= 20, "Too few stills were saved: " + stillsTaken);
                        require(c.player.getHealth() >= startHealth, "The player was hurt during the intro");
                        onServer(server, () -> {
                            var p = player(server, uuid);
                            require(!EndIntro.watching(p), "The intro did not let go");
                            require(EndEncounter.boss() instanceof FirstWizardEntity copy && copy.isBossAggressive() && copy.formed() >= 1,
                                    "The fight should follow the intro with the copy whole");
                        });
                        report.add("full scene " + track.length() + " ticks played through its own camera, " + stillsTaken + " stills");
                        next();
                    }
                    if (scene++ > track.length() + 600) throw new AssertionError("The full intro never ended");
                }
                case 3 -> { // the fight runs: the sky is empty; then a wipe, and the pool returns
                    int t = scene++;
                    if (t == 10) {
                        require(FirstWizardIntroClient.waitingPool(0) == 0 && FirstWizardIntroClient.scene() == null, "The sky should be empty while the fight runs");
                        require(!FirstWizardIntroClient.cinematic() && !BossIntroCamera.frozen(), "The camera should be the player's once the fight runs");
                        still(c, "first-wizard-intro-fight-sky-empty.png");
                    }
                    if (t == 30) onServer(server, () -> player(server, uuid).changeGameMode(GameMode.CREATIVE));    // nobody left standing: a wipe
                    if (t == 120) {
                        require(!EndEncounter.running(), "The wipe did not end the fight");
                        require(FirstWizardIntroClient.waitingPool(0) > .5, "The pool should be back over the island after a wipe");
                    }
                    if (t == 160) { still(c, "first-wizard-intro-pool-after-wipe.png"); report.add("sky empty during the fight; pool back after a wipe"); next(); }
                }
                case 4 -> { // a later attempt: the short scene, then skipped by its only watcher
                    int t = scene++;
                    if (t == 0) onServer(server, () -> { var p = player(server, uuid); p.changeGameMode(GameMode.SURVIVAL); run(server, p, "ew end begin"); });
                    EndIntroTrack.Scene track = EndIntroTrack.get().scene(false);
                    double st = FirstWizardIntroClient.sceneTime(0);
                    if (st >= 0) {
                        require(EndIntro.running() != null && !EndIntro.running().full(), "A later attempt should play only the condensing");
                        require(!FirstWizardIntroDragon.shown(), "No dragon in a later attempt");
                        stills(c, track, (int) Math.floor(st), false);
                        if (st >= 230 && !flag) {
                            flag = true;
                            onServer(server, () -> EndIntro.skip(player(server, uuid)));
                            note = "skip sent at tick " + (int) st;
                        }
                    }
                    if (flag && !judged && EndIntro.running() == null) {
                        judged = true;
                        onServer(server, () -> {
                            var p = player(server, uuid);
                            require(!EndIntro.watching(p), "A skip by the only watcher should end the intro");
                            require(EndEncounter.boss() instanceof FirstWizardEntity copy && copy.isBossAggressive() && copy.formed() >= 1,
                                    "A skipped intro should start the fight with the copy whole");
                        });
                        scene = 1000;
                    }
                    if (judged && scene == 1010) {
                        require(!FirstWizardIntroClient.cinematic(), "The skipped intro still holds the camera");
                        report.add("short scene " + track.length() + " ticks, skipped (" + note + ") and ended at once with the fight started");
                        onServer(server, () -> player(server, uuid).changeGameMode(GameMode.CREATIVE));
                    }
                    if (judged && scene == 1100) { require(!EndEncounter.running(), "The wipe did not end the fight"); next(); }
                    if (t > 1500 && !judged) throw new AssertionError("The short intro was never skipped: " + EndEncounter.status());
                }
                case 5 -> { // /ew end intro full, cut short by a reset
                    int t = scene++;
                    if (t == 0) onServer(server, () -> { var p = player(server, uuid); p.changeGameMode(GameMode.SURVIVAL); run(server, p, "ew end intro full"); });
                    if (!flag && t > 900) throw new AssertionError("/ew end intro full never reached its dragon: " + EndEncounter.status());
                    if (!flag && EndIntro.running() != null && EndIntro.running().clock() > 200 && FirstWizardIntroDragon.shown()) {
                        require(EndIntro.running().full(), "/ew end intro full should play the dragon again");
                        flag = true;
                        scene = 1000;
                        onServer(server, () -> run(server, player(server, uuid), "ew end reset"));
                    }
                    if (scene == 1040) cutShortLeftNobodyHeld(c, server, uuid, "a reset");
                    if (scene == 1060) next();
                }
                case 6 -> { // the boss removed mid-scene
                    int t = scene++;
                    if (t == 0) onServer(server, () -> { var p = player(server, uuid); p.changeGameMode(GameMode.SURVIVAL); run(server, p, "ew end begin"); });
                    if (!flag && t > 900) throw new AssertionError("The intro did not start again: " + EndEncounter.status());
                    if (!flag && EndIntro.running() != null && EndIntro.running().clock() > 100) {
                        flag = true;
                        scene = 1000;
                        onServer(server, () -> EndIntro.running().copy().discard());
                    }
                    if (scene == 1040) cutShortLeftNobodyHeld(c, server, uuid, "the boss's removal");
                    if (scene == 1060) next();
                }
                case 7 -> { // a win: the sky stays empty
                    int t = scene++;
                    if (t == 0) onServer(server, () -> { var p = player(server, uuid); p.changeGameMode(GameMode.SURVIVAL); run(server, p, "ew end begin"); });
                    if (!judged && t > 1500) throw new AssertionError("The fight to win never started: " + EndEncounter.status());
                    if (!flag && EndIntro.running() != null && EndIntro.running().clock() > EndIntro.SKIP_AFTER + 5) {
                        flag = true;
                        onServer(server, () -> EndIntro.skip(player(server, uuid)));
                    }
                    if (flag && !judged && EndIntro.running() == null && EndEncounter.boss() instanceof FirstWizardEntity copy && copy.isBossAggressive()) {
                        judged = true;
                        scene = 1000;
                        onServer(server, () -> {
                            FirstWizardEntity won = (FirstWizardEntity) EndEncounter.boss();
                            won.damage((ServerWorld) won.getEntityWorld(), won.getDamageSources().outOfWorld(), Float.MAX_VALUE);
                        });
                    }
                    if (scene == 1100) {
                        require(EndArenaManager.defeated(), "The win was not saved: " + EndEncounter.status());
                        require(FirstWizardIntroClient.waitingPool(0) == 0 && FirstWizardIntroClient.scene() == null, "The sky should stay empty after the win");
                        still(c, "first-wizard-intro-after-win.png");
                        report.add("after the win the sky stays empty");
                        finish(c);
                    }
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("FIRST_WIZARD_INTRO_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true; c.scheduleStop();
        }
    }

    private int lastFrame = -1, arrivedAt = -1;

    /** A still a little into each shot, and at the moments that carry the story. */
    private void stills(MinecraftClient c, EndIntroTrack.Scene track, int t, boolean full) {
        if (t == lastStill) return;
        String[] full_ = {"arrive+40", "under+50", "turn+60", "breath+40", "bloom_full", "back+6", "catch+12", "death+50", "death+120",
                "burst+2", "burst+30", "quiet+50", "sag+50", "touch+4", "form+30", "form+90", "form+170", "form+240", "strand+18", "raise+30", "title+40", "return+20"};
        String[] short_ = {"waiting+60", "sag+50", "form+90", "strand+18"};
        for (String spec : full ? full_ : short_) {
            String[] parts = spec.split("\\+");
            int at = track.beat(parts[0]) + (parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
            if (t == at) {
                lastStill = t;
                still(c, "first-wizard-intro-" + (full ? "" : "short-") + spec.replace('+', '-') + ".png");
            }
        }
        if (full && t == 20) { lastStill = t; still(c, "first-wizard-intro-island.png"); }
    }

    private void still(MinecraftClient c, String name) { shot(c, name); stillsTaken++; }

    private void checkHeld(MinecraftServer server, UUID uuid, boolean expect) {
        onServer(server, () -> {
            var p = player(server, uuid);
            require(EndIntro.watching(p) == expect, "The player should " + (expect ? "" : "not ") + "be held by the intro");
            require(p.getHealth() >= p.getMaxHealth(), "The player was hurt during the intro: " + p.getHealth());
            require(p.getEntityPos().distanceTo(EndArena.arrival()) < .3, "The player should be held at the arrival; they are at " + p.getEntityPos());
        });
    }

    /** At its first boot the copy already wears the player's own skin, loaded. */
    private void checkSkin(MinecraftClient c) {
        FirstWizardEntity copy = c.world.getEntityById(FirstWizardIntroClient.sceneCopy()) instanceof FirstWizardEntity e ? e : null;
        require(copy != null, "The copy is not on this client at its first boot");
        float formed = CopyForming.formed(copy, 0);
        require(formed > 0 && formed < .1f, "The copy should be at its first boot; formed " + formed);
        var future = c.getPlayerSkinCache().getFuture(copy.profile());
        require(future.isDone(), "The copy's skin is still loading at its first boot");
        var skin = c.getPlayerSkinCache().get(copy.profile()).getTextures().body().texturePath();
        require(skin.equals(c.player.getSkin().body().texturePath()), "The copy's first boot wears " + skin + ", not the player's " + c.player.getSkin().body().texturePath());
        report.add("the copy's first boot wears the player's skin (" + skin + ")");
    }

    /** During the scene the copy cannot be hurt, even by the player it copies. */
    private void tryHurtCopy(MinecraftServer server, UUID uuid) {
        onServer(server, () -> {
            var p = player(server, uuid);
            FirstWizardEntity copy = EndIntro.running().copy();
            float before = copy.getHealth();
            boolean hurt = copy.damage((ServerWorld) copy.getEntityWorld(), copy.getDamageSources().playerAttack(p), 50);
            hurt |= copy.damage((ServerWorld) copy.getEntityWorld(), copy.getDamageSources().magic(), 50);
            require(!hurt && copy.getHealth() == before, "The copy was hurt during the intro");
        });
    }

    private void cutShortLeftNobodyHeld(MinecraftClient c, MinecraftServer server, UUID uuid, String how) {
        require(!BossIntroCamera.frozen() && !FirstWizardIntroClient.cinematic(), "Cut short by " + how + ", the client is still held");
        onServer(server, () -> {
            var p = player(server, uuid);
            require(EndIntro.running() == null && !EndIntro.watching(p), "Cut short by " + how + ", the intro still holds the player");
            require(!EndEncounter.running(), "Cut short by " + how + ", the fight should end as a wipe does");
            p.changeGameMode(GameMode.CREATIVE);
        });
        report.add("cut short by " + how + ": nobody held, the fight over");
    }

    private void finish(MinecraftClient c) throws Exception {
        Files.writeString(Path.of("FIRST_WIZARD_INTRO_PASSED.txt"), "The First Wizard's intro: " + String.join("; ", report)
                + (frames > 0 ? "; " + frames + " video frames (intro-*.png)" : "") + ". Stills in screenshots/.\n");
        done = true; c.scheduleStop();
    }

    private void next() { stage++; scene = 0; flag = false; judged = false; lastStill = -1; }

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
