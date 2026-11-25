package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.summit.StormAerie;
import com.anton.elementalwands.summit.StormRocEncounter;
import com.anton.elementalwands.summit.StormRocIntroTrack;
import com.anton.elementalwands.summit.StormSummit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * The Storm Roc's intro in a real client. Creates a world with the aerie fixture's seed, stands the
 * player at a natural aerie's gong (-DrocIntro.gong=x,y,z, its anchor), rings it twice from the
 * server as a survival player, and watches the scene: it must take the camera, move the player into
 * the summit realm without the vanilla loading screen ever opening, hold in the storm cloud until the
 * summit has loaded, hand the view back at the arrival, and start the fight. Stills at the scene's
 * beats (and, with -DrocIntro.video=true, a frame every -DrocIntro.every ticks) land in
 * build/roc-intro-client-smoke/screenshots/. With -DrocIntro.skipAt=<beat> it holds Sneak from that
 * beat on, and the skip must hand the view back and start the fight just the same. With
 * -DrocIntro.element=<element> the player has that affinity, so the scene plays its landing; with
 * -DrocIntro.gong=locate the nearest natural aerie from spawn is used. With -DrocIntro.place=<n> the
 * lone player is given place n in the arrival line, as the n-th member out from the middle of a
 * party would be: their summit scene is turned to it, and the scene must end exactly where the
 * server set them down. With -DrocIntro.armour=true they wear a mixed set (a diamond helmet, a dyed
 * leather chestplate, enchanted iron leggings and netherite boots) for the scene's player to wear. Silent and hidden.
 */
public final class RocIntroClientSmoke implements ClientModInitializer {
    private static final boolean VIDEO = Boolean.getBoolean("rocIntro.video");
    private static final int EVERY = Integer.getInteger("rocIntro.every", 2);
    private static final String SKIP_AT = System.getProperty("rocIntro.skipAt", "");
    private static final String ELEMENT = System.getProperty("rocIntro.element", "none").toUpperCase(java.util.Locale.ROOT);
    private static final String LANDING = StormRocIntroTrack.landingFor(com.anton.elementalwands.data.WizardAffinity.valueOf(ELEMENT));
    private static final double PLACE = Double.parseDouble(System.getProperty("rocIntro.place", "0"));
    private static final boolean ARMOUR = Boolean.getBoolean("rocIntro.armour");
    /** A video runs on through the fight's opening summon, as the player sees it from the arrival. */
    private static final int AFTER = VIDEO ? com.anton.elementalwands.entity.roc.StormRocRules.SUMMON_TICKS + 30 : 40;

    private boolean started, done, tookCamera, sawLoading, reachedRealm;
    private int ticks, stage, scene, frames, sceneTicks, realmAt = -1, finishedAt = -1, heldMost;
    private volatile String serverFailure;
    private volatile boolean fighting, placed, rung;
    private volatile int struckAt = -1;
    private volatile BlockPos anchor;
    private final List<String> notes = new ArrayList<>();

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 30000) throw new AssertionError("Roc intro client timeout at stage " + stage + " scene " + scene);
            if (!started) {
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                c.getSoundManager().stopAll();
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.options.pauseOnLostFocus = false;
                c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(Integer.getInteger("rocIntro.viewDistance", 12));
                c.options.write();
                started = true;
                String[] at = System.getProperty("rocIntro.gong", "").split(",");
                if (at.length == 3) anchor = new BlockPos(Integer.parseInt(at[0].trim()), Integer.parseInt(at[1].trim()), Integer.parseInt(at[2].trim()));
                else if (!System.getProperty("rocIntro.gong", "").equals("locate"))
                    throw new AssertionError("Pass -ProcIntroGong=x,y,z (a gong anchor from the worldgen fixture) or -ProcIntroGong=locate");
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                long seed = Long.parseLong(System.getProperty("aerie.seed", "12345"));
                c.createIntegratedServerLoader().createAndStart("roc-intro-" + System.currentTimeMillis(),
                        new LevelInfo("Roc intro verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(seed, true, false), WorldPresets::createDemoOptions, null);
                return;
            }
            if (c.world == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            MinecraftServer server = c.getServer();
            if (c.player == null) return;
            UUID id = c.player.getUuid();
            WandWelcome.reset();
            // The scene must never show vanilla's loading screen while it plays.
            if (StormRocIntroClient.active() && c.currentScreen instanceof LevelLoadingScreen) sawLoading = true;
            if (c.currentScreen != null && !(c.currentScreen instanceof LevelLoadingScreen)) c.setScreen(null);
            switch (stage) {
                case 0 -> { // noon, no weather cycle, the player flown to the aerie while its land loads
                    if (++scene == 1) onServer(server, () -> {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                        p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        p.setAttached(EWAttachments.AFFINITY, ELEMENT);
                        com.anton.elementalwands.network.ModNetworking.syncPlayerData(p);
                        if (ARMOUR) {
                            var chest = new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER_CHESTPLATE);
                            chest.set(net.minecraft.component.DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(0xB02E26));
                            var legs = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_LEGGINGS);
                            legs.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                            p.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
                            p.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, chest);
                            p.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, legs);
                            p.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_BOOTS));
                            notes.add("wearing a diamond helmet, a red leather chestplate, enchanted iron leggings and netherite boots");
                        }
                        ServerWorld world = p.getEntityWorld();
                        if (anchor == null) anchor = locate(world);
                        notes.add("aerie gong anchor " + anchor.toShortString() + ", landing " + (LANDING.isEmpty() ? "plain crash" : LANDING));
                        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                        world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                        world.setTimeOfDay(6000);
                        world.setWeather(12000, 0, false, false);
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) world.getChunk((anchor.getX() >> 4) + x, (anchor.getZ() >> 4) + z);
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(anchor.getX() + .5, anchor.getY() + 2, anchor.getZ() + 6.5, 180, 0);
                        placed = true;
                    });
                    // Generating the land round the aerie can hold the server up for a while.
                    if (!placed || scene < 160) return;
                    next();
                }
                case 1 -> { // a survival player strikes the gong twice
                    if (++scene == 1) onServer(server, () -> {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                        p.changeGameMode(GameMode.SURVIVAL);
                        p.getAbilities().flying = false; p.sendAbilitiesUpdate();
                        String first = StormAerie.ring(p, anchor);
                        notes.add("first strike: " + first);
                        struckAt = server.getTicks();
                    });
                    // A strike under half a second after the last counts as the same held click.
                    if (struckAt < 0 || server.getTicks() < struckAt + 20) { scene = Math.min(scene, 2); return; }
                    if (scene == 3) onServer(server, () -> {
                        String second = StormAerie.ring(server.getPlayerManager().getPlayer(id), anchor);
                        notes.add("second strike: " + second);
                        if (PLACE != 0) {
                            // As if they were the PLACE-th member out from the middle of the line.
                            try {
                                var watching = com.anton.elementalwands.summit.StormRocIntro.class.getDeclaredField("WATCHING");
                                watching.setAccessible(true);
                                Object watcher = ((java.util.Map<?, ?>) watching.get(null)).get(id);
                                var place = watcher.getClass().getDeclaredField("place");
                                place.setAccessible(true);
                                place.setDouble(watcher, PLACE);
                                notes.add("given place " + PLACE + " in the arrival line");
                            } catch (ReflectiveOperationException e) { throw new AssertionError("Could not give the player a place in the line", e); }
                        }
                        rung = true;
                    });
                    if (!rung) return;
                    next();
                }
                case 2 -> { // the scene, frame by frame
                    scene++;
                    // The server ends the scene when the fight starts, right after everyone lands.
                    if (!StormRocIntroClient.active() && sceneTicks > 0 && finishedAt < 0) ended(c);
                    if (!StormRocIntroClient.active() && sceneTicks == 0) {
                        if (scene > 200) throw new AssertionError("The intro never started: " + notes);
                        return;
                    }
                    sceneTicks++;
                    if (sceneTicks == 1) onServer(server, () -> {
                        // The intro sets the player on the aerie's stand, facing the gong: where a party comes home.
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                        notes.add(com.anton.elementalwands.party.PartyLineCheck.require(p.getEntityWorld(), p.getEntityPos(), p.getYaw(), "home before the gong"));
                    });
                    if (sceneTicks == 1 && PLACE != 0) {
                        try {
                            var turn = StormRocIntroClient.class.getDeclaredField("summitTurn");
                            turn.setAccessible(true);
                            turn.setFloat(null, StormSummit.lineTurn(PLACE));
                        } catch (ReflectiveOperationException e) { throw new AssertionError("Could not turn the scene to the player's place", e); }
                    }
                    c.options.hudHidden = false;
                    if (StormRocIntroClient.cinematic()) tookCamera = true;
                    if (!reachedRealm && c.world.getRegistryKey() == StormSummit.WORLD) { reachedRealm = true; realmAt = sceneTicks; }
                    heldMost = Math.max(heldMost, StormRocIntroClient.heldFor());
                    // What a long hold is waiting for, on both sides.
                    if (StormRocIntroClient.holding() && sceneTicks % 40 == 0) {
                        String waiting = StormRocIntroClient.waitingFor(c);
                        onServer(server, () -> {
                            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                            String note = "hold at scene tick " + sceneTicks + ": client waits for " + waiting + "; client in " + c.world.getRegistryKey().getValue()
                                    + "; server has the player in " + p.getEntityWorld().getRegistryKey().getValue() + " at " + p.getBlockPos().toShortString();
                            notes.add(note);
                            System.out.println("[roc intro smoke] " + note);
                        });
                    }
                    StormRocIntroTrack track = StormRocIntroTrack.get(LANDING);
                    double t = StormRocIntroClient.time(0);
                    if (!SKIP_AT.isEmpty()) c.options.sneakKey.setPressed(t >= track.beat(SKIP_AT) && !StormRocIntroClient.finished());
                    if (VIDEO && sceneTicks % EVERY == 0) shot(c, String.format("roc-intro-video-%04d.png", frames++));
                    for (String beat : new String[]{"caw", "grip", "look_up", "scream", "cloud", "hold", "summit", "strike", "throw", "crash", "landing", "back", "ret"})
                        if (track.hasBeat(beat) && (int)t == track.beat(beat) + 2 && (int)StormRocIntroClient.time(0) != (int)prevTime) shot(c, "roc-intro-" + beat + ".png");
                    prevTime = t;
                    if (StormRocIntroClient.finished() && finishedAt < 0) ended(c);
                    if (finishedAt >= 0 && sceneTicks == finishedAt + 30) onServer(server, () -> fighting = StormRocEncounter.enrolled(server.getPlayerManager().getPlayer(id)));
                    if (finishedAt >= 0 && sceneTicks == finishedAt + AFTER) {
                        if (!SKIP_AT.isEmpty()) require(StormRocIntroClient.skipped(), "Holding Sneak from " + SKIP_AT + " never skipped the scene");
                        require(tookCamera, "The intro never took the camera");
                        require(reachedRealm, "The player never reached the summit realm");
                        require(!sawLoading, "The vanilla loading screen showed during the intro");
                        require(!StormRocIntroClient.cinematic(), "The intro never handed the camera back");
                        require(fighting, "The fight never started after the intro");
                        shot(c, "roc-intro-after.png");
                        next();
                    }
                    if (sceneTicks > track.length() + 1200) throw new AssertionError("The intro never finished (scene time " + t + ")");
                }
                default -> {
                    done = true;
                    Files.writeString(Path.of("ROC_INTRO_CLIENT_PASSED.txt"), String.format((SKIP_AT.isEmpty() ? "" : "Skipped from " + SKIP_AT + ". ")
                            + "Intro played in %d client ticks (track %d); reached the summit realm at tick %d; held in the cloud up to %d ticks; "
                                    + "no loading screen; handed back and the fight started.%n%s%n", sceneTicks, StormRocIntroTrack.get(LANDING).length(),
                            realmAt, heldMost, String.join("\n", notes)));
                    c.scheduleStop();
                }
            }
        } catch (Throwable e) {
            done = true;
            try { Files.writeString(Path.of("ROC_INTRO_CLIENT_FAILED.txt"), e + "\n" + notes + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            c.scheduleStop();
        }
    }

    private double prevTime = -1;

    /**
     * The scene is over (the client finished it, or the server ended it as the fight began). Its
     * last shot is from the player's eyes at their place in the line: handing back moves nothing.
     */
    private void ended(MinecraftClient c) {
        finishedAt = sceneTicks;
        if (!SKIP_AT.isEmpty()) return;
        StormRocIntroTrack track = StormRocIntroTrack.get(LANDING);
        Vec3d end = StormRocIntroClient.place(1, track.eye(track.length())), eye = c.player.getCameraPosVec(1);
        float yaw = track.yaw(track.length()) + StormRocIntroClient.frameYaw(1);
        notes.add(String.format("scene ends %.3f blocks and %.2f degrees from the player's own view", end.distanceTo(eye), MathHelper.wrapDegrees(yaw - c.player.getYaw())));
        require(end.distanceTo(eye) < .15 && Math.abs(MathHelper.wrapDegrees(yaw - c.player.getYaw())) < 1,
                "The scene ended at " + end + " yaw " + yaw + ", not at the player's view " + eye + " yaw " + c.player.getYaw());
    }

    /** The gong of the nearest natural aerie to spawn, its land generated. */
    private static BlockPos locate(ServerWorld world) {
        var id = net.minecraft.util.Identifier.of("elementalwands", "storm_aerie");
        var registry = world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.STRUCTURE);
        var entry = registry.getEntry(id).orElseThrow(() -> new AssertionError("No structure " + id));
        var found = world.getChunkManager().getChunkGenerator().locateStructure(world, net.minecraft.registry.entry.RegistryEntryList.of(entry),
                world.getServer().getSpawnPoint().getPos(), 100, false);
        if (found == null) throw new AssertionError("No natural aerie within reach of spawn");
        BlockPos at = found.getFirst();
        var start = world.getChunk(at.getX() >> 4, at.getZ() >> 4, net.minecraft.world.chunk.ChunkStatus.STRUCTURE_STARTS).getStructureStart(entry.value());
        if (start == null || !start.hasChildren()) throw new AssertionError("No aerie start at " + at.toShortString());
        var box = start.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++)
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.getChunk(cx, cz);
        for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()))
            if (world.getBlockState(pos).isOf(com.anton.elementalwands.registry.ModBlocks.STORM_GONG)) return pos.toImmutable();
        throw new AssertionError("No gong in the aerie at " + box);
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private void next() { stage++; scene = 0; }

    private void shot(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, ignored -> {});
    }

    private void onServer(MinecraftServer server, Runnable action) {
        server.execute(() -> {
            try { action.run(); }
            catch (Throwable e) { serverFailure = e.toString(); e.printStackTrace(); }
        });
    }
}
