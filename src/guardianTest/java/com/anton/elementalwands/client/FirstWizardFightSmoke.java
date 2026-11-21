package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.FirstWizardRenderer;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.BlinkRiftSpell;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * The First Wizard's phase-one fight, spell by spell, against an unarmored Survival player with 1000 health who stands
 * where each case puts them: the Fireball's heavy hit, a sidestep after its aim lock that dodges it, its burning ground
 * burning and then ending, Blink Rift landing on the floor away from a crowding player, and a short free fight through
 * the End's {@code startFight} to its death. The window stays hidden and the game muted. A camera off to one side
 * takes the screenshots; a mannequin wearing the player's profile stands in for the player, whom the game does not
 * draw for another camera. Every case runs; the failures are reported together at the end.
 */
public final class FirstWizardFightSmoke implements ClientModInitializer {
    private record Case(String name, FirstWizardRules.Move move, double x, double z, int ticks, int... shots) {}

    /**
     * {@code -PwizardCases=a,b} (the runner passes it on as {@code wizard.cases}) runs only the cases whose names start
     * with one of those, and the free fight only if "free" is among them. Unset, everything runs. A filtered run's
     * receipt says which cases it ran.
     */
    private static final List<String> ONLY = java.util.Arrays.stream(System.getProperty("wizard.cases", "").split(","))
            .map(String::trim).filter(name -> !name.isEmpty()).toList();
    private static final boolean FREE_FIGHT = ONLY.isEmpty() || ONLY.contains("free");
    /**
     * {@code -PwizardRecord=case[:every],...} ({@code wizard.record}): saves a frame of those cases every tick, or every
     * n ticks, as {@code rec-<case>-<tick>.png} beside the screenshots, for clips (tools/frames_to_gif.py).
     */
    private static final java.util.Map<String, Integer> RECORD = java.util.Arrays.stream(System.getProperty("wizard.record", "").split(","))
            .map(String::trim).filter(each -> !each.isEmpty())
            .collect(java.util.stream.Collectors.toMap(each -> each.split(":")[0], each -> each.contains(":") ? Integer.parseInt(each.split(":")[1]) : 1));

    private static final List<Case> CASES = java.util.stream.Stream.of(
            new Case("fireball-hit", FirstWizardRules.Move.FIREBALL, 0, -14, 70, 6, 14, 22, 30),
            // The same, seen close up: the ink rising off the wand arm and the ball forming over its head.
            new Case("fireball-close", FirstWizardRules.Move.FIREBALL, 0, -14, 60, 4, 10, 16, 22, 26),
            new Case("fireball-sidestep", FirstWizardRules.Move.FIREBALL, 0, -14, 70, 28, 36),
            new Case("fireball-ground", FirstWizardRules.Move.FIREBALL, 0, -14, 170, 45, 60, 90, 130),
            // Meteor and Gravity Well (FirstWizardFireSpaceCases): the forming, the mark, the fall, the impact; the well pulling and its burst.
            new Case("meteor-hit", FirstWizardRules.Move.METEOR, 0, -14, 200, 8, 16, 26, 45, 75, 89, 93, 112),
            new Case("meteor-rim", FirstWizardRules.Move.METEOR, 0, -14, 110, 70, 92),
            new Case("meteor-leave", FirstWizardRules.Move.METEOR, 0, -14, 110, 40, 70, 92),
            new Case("gravity-stand", FirstWizardRules.Move.GRAVITY_WELL, 0, -14, 150, 8, 16, 28, 44, 80, 126, 137),
            new Case("gravity-walk", FirstWizardRules.Move.GRAVITY_WELL, 0, -14, 150, 90),
            new Case("gravity-sprint", FirstWizardRules.Move.GRAVITY_WELL, 0, -14, 150, 90),
            new Case("gravity-dash", FirstWizardRules.Move.GRAVITY_WELL, 0, -14, 150, 60, 64, 70),
            new Case("gravity-meteor", FirstWizardRules.Move.GRAVITY_WELL, 0, -14, 165, 60, 95, 112, 119, 137),
            // Sky Shear and Waylay Dash: the cases are WindSpellCases'.
            new Case("shear-stand", FirstWizardRules.Move.SKY_SHEAR, 0, WindSpellCases.SHEAR_Z, 110, 6, 12, 17, 21, 27, 35, 49, 66, 92),
            new Case("shear-jump", FirstWizardRules.Move.SKY_SHEAR, 0, WindSpellCases.SHEAR_Z, 110, 30, 33, 47, 61),
            new Case("shear-mistimed", FirstWizardRules.Move.SKY_SHEAR, 0, WindSpellCases.SHEAR_Z, 110),
            new Case("dash-hit", FirstWizardRules.Move.WAYLAY_DASH, 0, WindSpellCases.DASH_Z, 50, 3, 6, 9, 11, 13, 15, 19, 26),
            new Case("dash-beside", FirstWizardRules.Move.WAYLAY_DASH, 0, WindSpellCases.DASH_Z, 40, 8, 14),
            new Case("dash-wake", FirstWizardRules.Move.WAYLAY_DASH, 0, WindSpellCases.DASH_Z, 100, 30, 50, 75),
            // Stone Wall and Faultline: the cases are FirstWizardStoneCases'.
            new Case("wall-rise", FirstWizardRules.Move.STONE_WALL, 0, -14, 185, 6, 14, 21, 23, 40, 100, 146, 150, 155, 165),
            new Case("wall-block", FirstWizardRules.Move.STONE_WALL, 0, -14, 120, 50, 75, 90),
            new Case("wall-shove", FirstWizardRules.Move.STONE_WALL, 0, -14, 60, 10, 21, 24, 40),
            new Case("wall-stop", FirstWizardRules.Move.STONE_WALL, 0, -14, 80, 50, 62),
            new Case("wall-fireball", FirstWizardRules.Move.STONE_WALL, 0, -14, 100, 45, 60, 68, 72),
            new Case("faultline-hit", FirstWizardRules.Move.FAULTLINE, 0, -14, 90, 4, 10, 15, 19, 22, 25, 28, 34, 40, 60),
            new Case("faultline-sidestep", FirstWizardRules.Move.FAULTLINE, 0, -14, 70, 15, 25, 30),
            new Case("faultline-jump", FirstWizardRules.Move.FAULTLINE, 0, -14, 80, 26, 30),
            new Case("faultline-wall", FirstWizardRules.Move.FAULTLINE, 0, -14, 70, 25, 35),
            // Thornbite and Sentry Bloom: the cases are FirstWizardNatureCases'.
            new Case("thorn-bite", FirstWizardRules.Move.THORNBITE, 0, -9, 60, 4, 9, 14, 18, 20, 23, 28, 33),
            new Case("thorn-sidestep", FirstWizardRules.Move.THORNBITE, 0, -9, 50, 20, 23),
            new Case("thorn-blink", FirstWizardRules.Move.BLINK_RIFT, 0, -9, 80, 8, 14, 26, 36, 39, 44, 52),
            new Case("thorn-blink-dodge", FirstWizardRules.Move.BLINK_RIFT, 0, -9, 70, 37, 40),
            new Case("bloom-deliver", FirstWizardRules.Move.SENTRY_BLOOM, 0, -14, 420, 6, 12, 26, 34, 45, 58, 75, 96, 168, 260, 318, 330, 345),
            new Case("bloom-dodge", FirstWizardRules.Move.SENTRY_BLOOM, 0, -14, 160, 90, 96),
            new Case("bloom-break", FirstWizardRules.Move.SENTRY_BLOOM, 0, -14, 200, 112, 134, 138, 146),
            new Case("blink-escape", FirstWizardRules.Move.BLINK_RIFT, 0, -2.5, 40, 3, 7, 11, 14, 20),
            new Case("blink-around", FirstWizardRules.Move.BLINK_RIFT, 0, -14, 40, 6, 14),
            new Case("side-by-side", null, 1.4, -1.2, 30, 10, 20),
            // Phase two: the cases are FirstWizardPhaseTwoCases'. The change beat by beat, then each ink move's tell, commit and aftermath.
            // The phase-two body close, standing: from the front, its wound side, its wand side and behind.
            new Case("phase-look", null, 0, -7, 165, 30, 70, 110, 150),
            // Phase two floating at rest while it drifts slowly about, close up: its tentacles winding round it.
            new Case("phase-drift", null, 0, -9, 180, 20, 60, 100, 140),
            new Case("phase-change", null, 5, -2, 470, 2, 10, 33, 50, 80, 110, 140, 170, 200, 230, 260, 290, 300, 310, 330, 350, 370, 380, 390, 400, 410, 420, 440),
            new Case("phase-jump", null, -5, 1.5, 440),
            // The change's cinematic (FirstWizardChangeCases): as the player sees it, stopped, left, joined late, skipped, and filmed close.
            new Case("change-cinematic", null, 0, -6, 470, 20, 45, 70, 100, 125, 140, 150, 160, 185, 200, 230, 260, 275, 285, 300, 330, 345, 372, 395, 420),
            new Case("change-stop", null, 0, -6, 140, 50, 103, 112),
            new Case("change-leave", null, 0, -6, 170, 50, 124, 135),
            new Case("change-late", null, 0, -6, 440, 60, 85, 110, 360),
            new Case("change-skip", null, 0, -6, 200, 30, 50, 60, 70, 90, 110),
            new Case("change-look", null, 0, -12, 430, 10, 30, 50, 70, 90, 100, 110, 116, 122, 128, 140, 160, 175, 190, 205, 220, 240, 255, 262, 268, 275, 290, 310, 330, 360, 380, 400),
            new Case("phase-body", null, 0, -13, 200, 4, 20, 60, 90, 120, 150, 180),
            new Case("phase-death", FirstWizardRules.Move.FLAME, 0, -5, 120),
            new Case("slam-hit", FirstWizardRules.Move.SLAM, 0, -6, 75, 4, 10, 15, 21, 23, 28, 36),
            new Case("slam-dodge", FirstWizardRules.Move.SLAM, 0, -6, 45, 16, 24),
            new Case("sweep-stand", FirstWizardRules.Move.SWEEP, 0, -5, 75, 6, 12, 16, 20, 23, 26, 34),
            new Case("sweep-jump", FirstWizardRules.Move.SWEEP, 0, -5, 50, 20, 22),
            new Case("leap-hit", FirstWizardRules.Move.LEAP, 0, -12, 60, 4, 9, 12, 18, 24, 30, 33, 38),
            new Case("leap-dodge", FirstWizardRules.Move.LEAP, 0, -12, 50, 30, 33),
            new Case("flame-hit", FirstWizardRules.Move.FLAME, 0, -5, 90, 4, 9, 14, 24, 40, 60, 66, 75),
            new Case("flame-behind", null, 0, -10, 140, 34, 48, 60, 80, 100),
            new Case("flame-bottle", FirstWizardRules.Move.FLAME, 0, -5, 150, 70, 100, 125),
            new Case("purple-gate", null, 0, -20, 1100),
            new Case("purple-hit", FirstWizardRules.Move.HOLLOW_PURPLE, 0, -20, 120, 6, 20, 35, 47, 52, 60, 62, 66, 72, 80),
            new Case("purple-dodge", FirstWizardRules.Move.HOLLOW_PURPLE, 0, -20, 110, 48, 64, 75),
            // A spell from the human hand in phase two: the tentacles flare round it as it forms and recoil as it is thrown.
            new Case("phase-fireball", FirstWizardRules.Move.FIREBALL, 0, -12, 50, 6, 14, 20, 24, 28))
            .filter(scene -> ONLY.isEmpty() || ONLY.stream().anyMatch(scene.name::startsWith)).toList();
    private static final int FIGHT_TICKS = 1700;

    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks, floor, index = -1, began = -1000;
    private UUID wizard;
    private volatile UUID camera;
    private UUID standIn;
    private final List<String> report = new ArrayList<>(), failures = new ArrayList<>();
    private final WindSpellCases wind = new WindSpellCases();
    // What the server thread measured in the case in hand.
    private final List<Float> wounds = java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile boolean stepped, movedIn;
    private volatile Vec3d landing, poolAt, groundAt, startedAt;
    private volatile int poolSeenAt = -1, groundEndedAt = -1, lateBurns;
    private volatile boolean movedBeforeRelease;
    // The free fight.
    private final List<String> timeline = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Set<String> movesSeen = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile String lastMove = "free";
    private volatile float fightDamage;
    private volatile double furthest;
    private volatile boolean leftFloor, completeBeforeDeath, completeAfterDeath, effectsAfterStop;
    private static final int FIGHT_HALF = 260, FIGHT_LOW = 1000;
    private volatile boolean heldAtHalf, diedInPhaseTwo;
    private volatile int changeAt = -1, twoAt = -1;

    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 16000) throw new AssertionError("First Wizard fight timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                // Muted before any world loads.
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.createIntegratedServerLoader().createAndStart("first-wizard-fight-" + System.currentTimeMillis(),
                        new LevelInfo("First Wizard fight verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            var server = c.getServer(); var uuid = c.player.getUuid();
            if (!arranged) {
                arranged = true;
                server.execute(() -> { try { arrange(server, uuid); ready = true; } catch (Throwable e) { serverFailure = e.toString(); } });
                return;
            }
            if (serverFailure != null) throw new AssertionError(serverFailure);
            if (!ready) return;
            c.setScreen(null); WandWelcome.reset();
            c.options.hudHidden = true;
            c.options.setPerspective(Perspective.FIRST_PERSON);
            if (camera != null && c.world.getEntity(camera) != null && c.getCameraEntity() != c.world.getEntity(camera)) c.setCameraEntity(c.world.getEntity(camera));
            if (index > CASES.size()) { if (ticks > began) finish(c); return; }
            boolean fight = index == CASES.size();
            Case scene = index < 0 || fight ? null : CASES.get(index);
            int length = fight ? FIGHT_TICKS : scene == null ? 0 : scene.ticks;
            if (index < 0 || ticks - began > length) {
                if (scene != null) server.execute(() -> guard(() -> verify(server, uuid, scene)));
                if (fight) { server.execute(() -> guard(() -> finishFight(server, uuid))); index++; began = ticks + 20; return; }
                index++;
                if (index == CASES.size() && !FREE_FIGHT) { index++; began = ticks + 40; return; }
                began = ticks + 40;     // time for the last case to be judged, and for both to take their places
                int next = index;
                server.execute(() -> guard(() -> stage(server, uuid, next)));
                return;
            }
            int t = ticks - began;
            server.execute(() -> guard(() -> shadow(server, uuid)));
            if (fight) { fightTick(c, server, uuid, t); return; }
            if (t == 0) {
                require(c.getEntityRenderDispatcher().getRenderer(clientWizard(c)) instanceof FirstWizardRenderer, "Wrong renderer for the First Wizard");
                server.execute(() -> guard(() -> {
                    wounds.clear(); stepped = movedIn = movedBeforeRelease = false; landing = poolAt = groundAt = null;
                    poolSeenAt = groundEndedAt = -1; lateBurns = 0;
                    FirstWizardEntity k = wizard(server);
                    startedAt = k.getEntityPos();
                    if (scene.move != null && !k.testMove(server.getPlayerManager().getPlayer(uuid), scene.move)) serverFailure = scene.name + ": the spell would not cast";
                    if (FirstWizardPhaseTwoCases.mine(scene.name)) FirstWizardPhaseTwoCases.start(server, server.getPlayerManager().getPlayer(uuid), k, scene.name);
                    if (FirstWizardChangeCases.mine(scene.name)) FirstWizardChangeCases.start(server, server.getPlayerManager().getPlayer(uuid), k, scene.name);
                }));
            }
            if (t > 0) server.execute(() -> guard(() -> during(server, uuid, scene, t)));
            if (FirstWizardFireSpaceCases.mine(scene.name)) FirstWizardFireSpaceCases.client(c, scene.name, t, scene.ticks);
            for (int shot : scene.shots) if (t == shot) shot(c, "wizard-" + scene.name + "-" + shot + ".png");
            Integer every = RECORD.get(scene.name);
            if (every != null && t % every == 0) shot(c, String.format(Locale.ROOT, "rec-%s-%04d.png", scene.name, t));
            if (WindSpellCases.mine(scene.name)) wind.client(c);
            if (FirstWizardStoneCases.NAMES.contains(scene.name)) FirstWizardStoneCases.client(c);
            if (FirstWizardPhaseTwoCases.mine(scene.name)) FirstWizardPhaseTwoCases.client(c);
            if (FirstWizardChangeCases.mine(scene.name)) FirstWizardChangeCases.client(c);
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("FIRST_WIZARD_FIGHT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void guard(Runnable work) {
        try { work.run(); } catch (Throwable e) { e.printStackTrace(); serverFailure = e.toString(); }
    }

    // ── The cases ────────────────────────────────────────────────────────────

    /** What a case does to the player while the spell plays, and what it measures. Runs on the server thread. */
    private void during(MinecraftServer server, UUID uuid, Case scene, int t) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        FirstWizardEntity k = wizard(server);
        // Every wound is counted and healed, so each shows as its raw damage.
        boolean wounded = p.getHealth() < p.getMaxHealth();
        if (wounded) { wounds.add(p.getMaxHealth() - p.getHealth()); p.setHealth(p.getMaxHealth()); }
        double age = k.castAge(0);
        FirstWizardEffectEntity ground = effect(server, FireballSpell.GROUND_KIND), pool = effect(server, BlinkRiftSpell.POOL_KIND);
        if (ground != null && groundAt == null) groundAt = ground.getEntityPos();
        switch (scene.name) {
            // Struck, then well clear of the burning ground, so the hit stands alone.
            case "fireball-hit", "fireball-close" -> { if (!wounds.isEmpty() && !movedIn) { movedIn = true; place(server, uuid, 9, -14); } }
            // Four blocks aside once the aim has locked and before the release: it must miss, and so must the burst.
            case "fireball-sidestep" -> sidestep(server, uuid, age);
            // Aside as above, then into the burning ground once it has armed, and out again after it has ended.
            case "fireball-ground" -> {
                sidestep(server, uuid, age);
                if (ground != null && !movedIn && ground.age(0) > FirstWizardRules.GROUND_ARM + 2) { movedIn = true; wounds.clear(); place(server, uuid, ground.getX() - .5, ground.getZ() - .5); }
                if (movedIn && ground == null && groundEndedAt < 0) groundEndedAt = t;
                if (groundEndedAt >= 0 && t > groundEndedAt + 2 && wounded) lateBurns++;
            }
            case "blink-escape", "blink-around" -> {
                // Framed to hold both where it stands and where the ink pools.
                if (pool != null && poolAt == null) { poolAt = pool.getEntityPos(); poolSeenAt = t; frame(server, startedAt, poolAt); }
                // Until the release it stands where it was: the pool is the warning.
                if (age >= 0 && age < BlinkRiftSpell.TIMING.release() - 1 && k.getEntityPos().squaredDistanceTo(startedAt) > .01) movedBeforeRelease = true;
            }
            case "meteor-hit", "meteor-rim", "meteor-leave", "gravity-stand", "gravity-walk", "gravity-sprint", "gravity-dash", "gravity-meteor" ->
                    FirstWizardFireSpaceCases.during(server, p, k, camera(server), wounds, scene.name, t, age, home());
            case "shear-stand", "shear-jump", "shear-mistimed", "dash-hit", "dash-beside", "dash-wake" ->
                    wind.during(server.getOverworld(), p, k, scene.name, t, wounded, (x, z) -> place(server, uuid, x, z));
            case "wall-rise", "wall-block", "wall-shove", "wall-stop", "wall-fireball", "faultline-hit", "faultline-sidestep", "faultline-jump", "faultline-wall" ->
                    FirstWizardStoneCases.during(server, p, k, camera(server), wounds, scene.name, t, home(), (x, z) -> place(server, uuid, x, z));
            case "thorn-bite", "thorn-sidestep", "thorn-blink", "thorn-blink-dodge", "bloom-deliver", "bloom-dodge", "bloom-break" ->
                    FirstWizardNatureCases.during(server, p, k, camera(server), wounds, scene.name, t, home(), (x, z) -> place(server, uuid, x, z));
            default -> {
                if (scene.name.equals("phase-drift")) {
                    // A slow figure of eight near its mark, about a block and a half a second; the camera holds still.
                    double w = t / 20.0 * .45;
                    Vec3d at = new Vec3d(home().x + 1.2 * Math.sin(w), home().y, home().z - .4 + .7 * Math.sin(2 * w));
                    k.refreshPositionAndAngles(at.x, at.y, at.z, 180, 0);
                    k.setBodyYaw(180); k.setHeadYaw(180);
                    // One slow turn round her over the case: front, her left, behind, her right.
                    // Close enough that she fills a third of the frame; her tentacles may run off its top.
                    // -PwizardOrbit=<blocks> (wizard.orbit) widens it, to see the tentacles' whole shape: wider, the
                    // camera rises and looks higher, so the striker reared over her stays in frame with her feet.
                    double a = -Math.PI / 2 - 2 * Math.PI * t / 180.0, r = Double.parseDouble(System.getProperty("wizard.orbit", "5.2"));
                    double wider = Math.max(0, r - 5.2) * .45;
                    Vec3d eye = at.add(Math.cos(a) * r, 2.5 + wider, Math.sin(a) * r);
                    aim(camera(server), eye, at.add(0, 1.9 + wider, 0).subtract(eye));
                }
                if (scene.name.equals("phase-look")) {
                    // Round it at 5.5 blocks, eye a little over its head.
                    Vec3d[] eyes = {home().add(0, 2.3, -5.5), home().add(-5.5, 2.3, -.5), home().add(5.5, 2.3, -.5), home().add(0, 2.3, 5.5)};
                    Vec3d eye = eyes[Math.min(3, t / 40)];
                    aim(camera(server), eye, home().add(0, 1.6, 0).subtract(eye));
                }
                if (scene.name.equals("phase-body") && t > 30) follow(server, k.getEntityPos(), p.getEntityPos()); if (FirstWizardPhaseTwoCases.mine(scene.name)) FirstWizardPhaseTwoCases.during(server, p, k, wounds, scene.name, t, home(), (x, z) -> place(server, uuid, x, z));
                if (FirstWizardChangeCases.mine(scene.name)) FirstWizardChangeCases.during(server, p, k, wounds, scene.name, t, home(), (x, z) -> place(server, uuid, x, z)); }
        }
    }

    private void sidestep(MinecraftServer server, UUID uuid, double age) {
        if (!stepped && age >= FireballSpell.TIMING.aimLock() + 1 && age < FireballSpell.TIMING.release()) { stepped = true; place(server, uuid, 4, -14); }
    }

    private void verify(MinecraftServer server, UUID uuid, Case scene) {
        FirstWizardEntity k = wizard(server);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        List<Float> hurt = new ArrayList<>(wounds);
        float dealt = 0;
        for (float w : hurt) dealt += w;
        List<String> notes = new ArrayList<>();
        Vec3d home = home();
        switch (scene.name) {
            case "fireball-hit", "fireball-close" -> {
                notes.add("wounds " + hurt);
                if (hurt.isEmpty() || Math.abs(hurt.getFirst() - FirstWizardRules.HEAVY) > .01f) fail(scene, "a player standing still should take one heavy hit (" + FirstWizardRules.HEAVY + "); took " + hurt);
                if (hurt.size() > 1) fail(scene, "only the one hit: they were moved clear of the ground; took " + hurt);
            }
            case "fireball-sidestep" -> {
                notes.add("stepped " + stepped + ", wounds " + hurt + ", burst at " + fmt(groundAt));
                if (!stepped) fail(scene, "the sidestep never happened");
                if (dealt > 0) fail(scene, "a sidestep after the aim lock should dodge the ball and its burst; took " + hurt);
                if (groundAt == null || Math.hypot(groundAt.x - home.x, groundAt.z - (home.z - 14)) > 3) fail(scene, "the ball should burst where the player stood when the aim locked; burst at " + fmt(groundAt));
            }
            case "fireball-ground" -> {
                long burns = hurt.stream().filter(w -> Math.abs(w - FirstWizardRules.GROUND) < .01f).count();
                notes.add("in the ground: wounds " + hurt + "; ended at tick " + groundEndedAt + "; burns after it ended " + lateBurns + "; lingering " + k.lingering());
                if (!movedIn) fail(scene, "the player was never put in the burning ground");
                if (burns < 3) fail(scene, "standing in the burning ground for its last seconds should burn about once a second; burned " + burns + " times: " + hurt);
                if (hurt.stream().anyMatch(w -> Math.abs(w - FirstWizardRules.GROUND) > .01f)) fail(scene, "the ground should burn for " + FirstWizardRules.GROUND + " a time: " + hurt);
                if (groundEndedAt < 0) fail(scene, "the burning ground should end after about 5 s");
                if (lateBurns > 0) fail(scene, "nothing should burn after the ground has ended");
                if (k.lingering() > 0) fail(scene, "nothing of the Fireball should still be running");
            }
            case "blink-escape", "blink-around" -> {
                Vec3d at = k.getEntityPos();
                double moved = at.subtract(startedAt).horizontalLength(), fromPlayer = at.subtract(p.getEntityPos()).horizontalLength();
                double out = k.arena().fromCentre(at);
                notes.add(String.format(Locale.ROOT, "pool at %s seen at tick %d; landed %s, %.1f from where it stood, %.1f from the player, %.1f from the centre",
                        fmt(poolAt), poolSeenAt, fmt(at), moved, fromPlayer, out));
                if (poolAt == null) fail(scene, "ink should pool at the arrival point first");
                else if (at.subtract(poolAt).horizontalLength() > .6) fail(scene, "it should rise where the ink pooled; pool " + fmt(poolAt) + ", landed " + fmt(at));
                if (movedBeforeRelease) fail(scene, "it should stay put while the ink pools");
                if (Math.abs(at.y - floor) > .05) fail(scene, "it should land on the floor; y " + at.y);
                if (out > k.arena().radius() - FirstWizardRules.EDGE + .01) fail(scene, "it should land inside the floor; " + out + " from the centre");
                if (moved < 4) fail(scene, "it should go somewhere; it moved " + moved);
                if (scene.name.equals("blink-escape") && fromPlayer < 10) fail(scene, "crowded, it should get well away; it is " + fromPlayer + " from the player");
            }
            case "meteor-hit", "meteor-rim", "meteor-leave", "gravity-stand", "gravity-walk", "gravity-sprint", "gravity-dash", "gravity-meteor" ->
                    FirstWizardFireSpaceCases.verify(p, k, scene.name, hurt, notes, why -> fail(scene, why));
            case "shear-stand", "shear-jump", "shear-mistimed", "dash-hit", "dash-beside", "dash-wake" ->
                    wind.verify(scene.name, hurt, k, p, floor, notes, why -> fail(scene, why));
            case "wall-rise", "wall-block", "wall-shove", "wall-stop", "wall-fireball", "faultline-hit", "faultline-sidestep", "faultline-jump", "faultline-wall" ->
                    FirstWizardStoneCases.verify(server, p, k, scene.name, hurt, home(), notes, why -> fail(scene, why));
            case "thorn-bite", "thorn-sidestep", "thorn-blink", "thorn-blink-dodge", "bloom-deliver", "bloom-dodge", "bloom-break" ->
                    FirstWizardNatureCases.verify(server, p, k, scene.name, hurt, home(), notes, why -> fail(scene, why));
            default -> {
                if (FirstWizardPhaseTwoCases.mine(scene.name)) FirstWizardPhaseTwoCases.verify(server, p, k, scene.name, hurt, home(), notes, why -> fail(scene, why));
                if (FirstWizardChangeCases.mine(scene.name)) FirstWizardChangeCases.verify(server, p, k, scene.name, hurt, home(), notes, why -> fail(scene, why));
            }
        }
        report.add(String.format(Locale.ROOT, "%-20s dealt %6.1f%s", scene.name, dealt, notes.isEmpty() ? "" : "; " + String.join("; ", notes)));
    }

    // ── The free fight ───────────────────────────────────────────────────────

    private void fightTick(MinecraftClient c, MinecraftServer server, UUID uuid, int t) {
        if (t == 0) server.execute(() -> guard(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            FirstWizardEntity k = wizard(server);
            // As the End's encounter starts it: its floor, its roster, whose skin it wears.
            k.startFight(BlockPos.ofFloored(home()).down(), FirstWizardRules.FLOOR_RADIUS, List.of(p), p.getUuid());
            completeBeforeDeath = k.fightComplete();
        }));
        if (t > 0 && t < FIGHT_TICKS - 40) server.execute(() -> guard(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            FirstWizardEntity k = wizard(server);
            String move = k.moveName();
            if (!move.equals(lastMove)) { if (!move.equals("free")) { timeline.add(t + " " + move); movesSeen.add(move); } lastMove = move; }
            if (p.getHealth() < p.getMaxHealth()) { fightDamage += p.getMaxHealth() - p.getHealth(); p.setHealth(p.getMaxHealth()); }
            double out = k.arena().fromCentre(k.getEntityPos());
            furthest = Math.max(furthest, out);
            if (out > k.arena().radius()) leftFloor = true;
            act(server, p, k, t);
            follow(server, k.getEntityPos(), p.getEntityPos());
        }));
        // Past half health: held there, then the change; later low enough for the finisher; then its death.
        if (t == FIGHT_HALF) server.execute(() -> guard(() -> {
            FirstWizardEntity k = wizard(server);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            k.damage(server.getOverworld(), server.getOverworld().getDamageSources().playerAttack(p), k.getMaxHealth() * .6f);
            heldAtHalf = Math.abs(k.getHealth() - k.getMaxHealth() / 2) < .01f;
        }));
        if (t == FIGHT_LOW) server.execute(() -> guard(() -> { FirstWizardEntity k = wizard(server); k.setHealth(k.getMaxHealth() * .22f); }));
        if (t > 0 && t < FIGHT_TICKS - 40) server.execute(() -> guard(() -> {
            FirstWizardEntity k = wizard(server);
            if (k.phase() == FirstWizardEntity.CHANGING && changeAt < 0) changeAt = t;
            if (k.inPhaseTwo() && twoAt < 0) twoAt = t;
        }));
        if (t == FIGHT_TICKS - 40) server.execute(() -> guard(() -> {
            FirstWizardEntity k = wizard(server);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            diedInPhaseTwo = k.inPhaseTwo();
            k.damage(server.getOverworld(), server.getOverworld().getDamageSources().playerAttack(p), 100000);
        }));
        if (t == FIGHT_TICKS - 30) server.execute(() -> guard(() -> {
            var w = server.getOverworld();
            // Dead but not yet gone: it must report the fight won.
            completeAfterDeath = w.getEntity(wizard) instanceof FirstWizardEntity k && k.fightComplete();
            effectsAfterStop = !w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home(), home()).expand(80), e -> true).isEmpty()
                    || !w.getEntitiesByClass(net.minecraft.entity.AreaEffectCloudEntity.class, new Box(home(), home()).expand(80), e -> true).isEmpty();
        }));
        if (t > 0 && t % 100 == 0) shot(c, String.format(Locale.ROOT, "wizard-free-%04d.png", t));
    }

    /** What the player does: stands, circles at range, crowds it, runs off. */
    private void act(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, int t) {
        Vec3d boss = k.getEntityPos(), at = p.getEntityPos(), away = at.subtract(boss).multiply(1, 0, 1);
        double apart = away.length();
        Vec3d out = apart < .1 ? new Vec3d(0, 0, -1) : away.normalize();
        Vec3d next;
        if (t < 120 || com.anton.elementalwands.entity.firstwizard.FirstWizardChangeScene.watching(p)) return;   // stands and takes it; held through the change
        t = 120 + (t - 120) % 520;
        if (t < 380) {                                                             // circles it at 14 at a sprint
            double angle = Math.atan2(out.z, out.x) + 5.6 / 14 / 20;
            next = boss.add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14);
        } else if (t < 500) next = apart > 2.5 ? at.add(out.multiply(-5.6 / 20)) : at;   // runs at it and stays on it
        else next = at.add(out.multiply(5.6 / 20));                                     // runs away
        next = k.arena().onFloor(new Vec3d(next.x, floor, next.z), 2);
        Vec3d look = boss.subtract(next);
        p.fallDistance = 0;
        p.networkHandler.requestTeleport(next.x, floor, next.z, (float) Math.toDegrees(Math.atan2(-look.x, look.z)), 5);
    }

    private void finishFight(MinecraftServer server, UUID uuid) {
        report.add("free fight: spells " + movesSeen + "; the player took " + fightDamage + "; furthest from the centre " + String.format(Locale.ROOT, "%.1f", furthest));
        report.add("free fight timeline (tick spell): " + timeline);
        if (!movesSeen.contains("fireball")) failures.add("free fight: it should throw the Fireball");
        if (!movesSeen.contains("blink_rift")) failures.add("free fight: a player who crowds it should draw Blink Rift");
        report.add("free fight: held at half " + heldAtHalf + ", change at t" + changeAt + ", phase two at t" + twoAt + ", died in phase two " + diedInPhaseTwo);
        if (!heldAtHalf) failures.add("free fight: a blow past half should hold it at half");
        if (changeAt < 0 || !movesSeen.contains("phase_change")) failures.add("free fight: it should change to phase two at half health");
        if (twoAt < 0 || !diedInPhaseTwo) failures.add("free fight: it should fight on in phase two to its death");
        long ink = movesSeen.stream().filter(m -> FirstWizardRules.Move.byId(m) != null && !FirstWizardRules.Move.byId(m).spell()).count();
        if (ink < 2) failures.add("free fight: in phase two it should use its tendrils; ink moves seen " + ink);
        for (String move : movesSeen) if (!move.equals("phase_change") && (FirstWizardRules.Move.byId(move) == null || BossSpells.get(FirstWizardRules.Move.byId(move)) == null)) failures.add("free fight: it cast " + move + ", which is not built");
        for (int i = 2; i < timeline.size(); i++) {
            String a = timeline.get(i).split(" ")[1], b = timeline.get(i - 1).split(" ")[1], d = timeline.get(i - 2).split(" ")[1];
            if (a.equals(b) && b.equals(d)) failures.add("free fight: " + a + " three times running at " + timeline.get(i));
        }
        if (fightDamage <= 0) failures.add("free fight: the player should be hurt");
        if (leftFloor) failures.add("free fight: it left its floor (" + furthest + " from the centre)");
        if (completeBeforeDeath) failures.add("free fight: the fight should not be complete before it dies");
        if (!completeAfterDeath) failures.add("free fight: fightComplete should be true once it has died to its roster");
        if (effectsAfterStop) failures.add("free fight: its effects should be cleared when it dies");
    }

    /** The camera keeps both of them in frame, easing after them. */
    private Vec3d focus;
    private double span;
    private void follow(MinecraftServer server, Vec3d boss, Vec3d player) {
        if (!(server.getOverworld().getEntity(camera) instanceof ArmorStandEntity stand)) return;
        Vec3d between = player.subtract(boss).multiply(1, 0, 1);
        double apart = between.length();
        Vec3d middle = boss.add(between.multiply(.5));
        if (focus == null) { focus = middle; span = apart; }
        focus = focus.add(middle.subtract(focus).multiply(.15));
        span += (apart - span) * .1;
        double back = Math.max(14, span * 1.1 + 8);
        Vec3d eye = new Vec3d(focus.x + back * .8, floor + 4 + back * .3, focus.z + back * .75), look = focus.add(0, 1.4, 0).subtract(eye);
        aim(stand, eye, look);
    }

    private void finish(MinecraftClient c) throws java.io.IOException {
        if (serverFailure != null) failures.add("server: " + serverFailure);
        String body = String.join("\n", report) + "\n";
        Files.writeString(Path.of("FIRST_WIZARD_FIGHT_REPORT.txt"), body + (failures.isEmpty() ? "" : "\nFAILURES\n" + String.join("\n", failures) + "\n"));
        String ran = "cases " + CASES.stream().map(Case::name).toList() + (FREE_FIGHT ? " and the free fight through startFight to its death" : "; no free fight")
                + (ONLY.isEmpty() ? "" : " (a filtered run: " + ONLY + ")");
        if (failures.isEmpty()) Files.writeString(Path.of("FIRST_WIZARD_FIGHT_PASSED.txt"), "First Wizard fight passed: " + ran + ". "
                + "Screenshots are not a Lunar look; human review pending.\n\n" + body);
        else Files.writeString(Path.of("FIRST_WIZARD_FIGHT_FAILED.txt"), String.join("\n", failures) + "\n");
        done = true; c.scheduleStop();
    }

    private void fail(Case scene, String why) { failures.add(scene.name + ": " + why); }

    // ── Staging ──────────────────────────────────────────────────────────────

    private Vec3d home() { return new Vec3d(.5, floor, .5); }

    private void arrange(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        // A round floor as wide as the End's, a block wider so its edge is visible.
        for (int x = -47; x <= 47; x++) for (int z = -47; z <= 47; z++) {
            boolean on = x * x + z * z <= 46 * 46;
            w.setBlockState(new BlockPos(x, floor - 1, z), on ? ((x + z) % 2 == 0 ? Blocks.END_STONE_BRICKS : Blocks.END_STONE).getDefaultState() : Blocks.AIR.getDefaultState());
            for (int y = 0; y < 16; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        w.setTimeOfDay(18000);      // midnight: darker than the End, so findability is tested w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        w.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
        var p = server.getPlayerManager().getPlayer(uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 100000, 0, false, false));
        // Unarmored and very healthy: every hit shows as its raw damage, and none of them kills.
        p.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1000);
        p.setHealth(1000);
        summon(server, p);
    }

    private void summon(MinecraftServer server, ServerPlayerEntity p) {
        var w = server.getOverworld();
        if (wizard != null && w.getEntity(wizard) != null) w.getEntity(wizard).discard();
        FirstWizardEntity mob = ModEntities.FIRST_WIZARD.spawn(w, BlockPos.ofFloored(home()), SpawnReason.COMMAND);
        require(mob != null, "Could not spawn the First Wizard");
        mob.stop();
        mob.wear(p);
        mob.refreshPositionAndAngles(home().x, floor, home().z, 180, 0);
        mob.setBodyYaw(180); mob.setHeadYaw(180);
        wizard = mob.getUuid();
    }

    /** The copy last found: a case that kills it is still judged once it has gone. */
    private FirstWizardEntity lastWizard;
    private FirstWizardEntity wizard(MinecraftServer server) {
        if (server.getOverworld().getEntity(wizard) instanceof FirstWizardEntity k) return lastWizard = k;
        if (lastWizard != null && lastWizard.getUuid().equals(wizard) && lastWizard.isDead()) return lastWizard;
        throw new AssertionError("The First Wizard is gone");
    }

    private FirstWizardEffectEntity effect(MinecraftServer server, String kind) {
        var found = server.getOverworld().getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home(), home()).expand(70), e -> e.kind().equals(kind) && e.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Both take their places for the next case: it stands passive on its mark facing -Z; the free fight gets a fresh copy. */
    private void stage(MinecraftServer server, UUID uuid, int next) {
        boolean fight = next >= CASES.size();
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        boolean gone = !(server.getOverworld().getEntity(wizard) instanceof FirstWizardEntity alive && alive.isAlive());
        if (fight || gone || next < CASES.size() && (FirstWizardPhaseTwoCases.fromPhaseOne(CASES.get(next).name) || FirstWizardChangeCases.mine(CASES.get(next).name))) summon(server, p);
        FirstWizardEntity k = wizard(server);
        k.stop();
        k.refreshPositionAndAngles(home().x, floor, home().z, 180, 0);
        k.setBodyYaw(180); k.setHeadYaw(180);
        k.setVelocity(Vec3d.ZERO);
        k.setHealth(k.getMaxHealth());
        // A case may leave the player Creative or spectating (the change's late and leaving players).
        p.changeGameMode(GameMode.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.extinguish();
        p.removeStatusEffect(StatusEffects.ABSORPTION);
        p.setAbsorptionAmount(0);
        if (fight) { place(server, uuid, 0, -15); watch(server, 0, -15); return; }
        Case scene = CASES.get(next);
        // The cases made for looking at (and recording) phase two are lit by day; everything else stays at midnight.
        server.getOverworld().setTimeOfDay(FirstWizardPhaseTwoCases.lit(scene.name) || FirstWizardChangeCases.lit(scene.name) ? 6000 : 18000);
        place(server, uuid, scene.x, scene.z);
        if (FirstWizardPhaseTwoCases.mine(scene.name) && !FirstWizardPhaseTwoCases.fromPhaseOne(scene.name)) k.phaseTwoNow();
        Vec3d[] two = FirstWizardPhaseTwoCases.mine(scene.name) ? FirstWizardPhaseTwoCases.camera(scene.name, home())
                : FirstWizardChangeCases.mine(scene.name) ? FirstWizardChangeCases.camera(scene.name, home()) : null;
        if (two != null) { aim(camera(server), two[0], two[1]); return; }
        if (scene.name.equals("side-by-side")) close(server);
        else if (scene.name.equals("fireball-close")) {
            Vec3d eye = home().add(3.6, 1.9, -4.4);
            aim(camera(server), eye, home().add(0, 2.4, 0).subtract(eye));
        }
        else if (WindSpellCases.camera(scene.name, home()) != null) { Vec3d[] view = WindSpellCases.camera(scene.name, home()); aim(camera(server), view[0], view[1]); }
        else watch(server, scene.x * .5, scene.z);
    }

    /** The camera is not the player, so the game does not draw them: a mannequin in their profile stands in. */
    private void shadow(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld();
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        MannequinEntity stand = standIn != null && w.getEntity(standIn) instanceof MannequinEntity old ? old : null;
        if (stand == null) {
            stand = MannequinEntity.create(EntityType.MANNEQUIN, w);
            stand.setNoGravity(true); stand.setInvulnerable(true); stand.setSilent(true);
            stand.setComponent(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(p.getGameProfile()));
            w.spawnEntity(stand);
            standIn = stand.getUuid();
        }
        stand.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0);
        stand.setHeadYaw(p.getYaw()); stand.setBodyYaw(p.getYaw());
    }

    /** Stands the camera off to one side of the line from it to the player. */
    private void watch(MinecraftServer server, double x, double z) {
        double apart = Math.hypot(x, z);
        Vec3d middle = home().add(x * .5, 0, z * .5), along = apart < .1 ? new Vec3d(0, 0, -1) : new Vec3d(x / apart, 0, z / apart);
        Vec3d side = new Vec3d(-along.z, 0, along.x);
        double back = Math.max(12, apart * .8);
        Vec3d eye = middle.add(side.multiply(back)).add(along.multiply(-back * .2)).add(0, 3.5 + back * .18, 0);
        aim(camera(server), eye, middle.add(0, 1.8, 0).subtract(eye));
    }

    /** Off to one side of two points, far enough back to hold both. */
    private void frame(MinecraftServer server, Vec3d a, Vec3d b) {
        Vec3d along = b.subtract(a).multiply(1, 0, 1), middle = a.add(along.multiply(.5));
        double apart = along.length();
        along = apart < .1 ? new Vec3d(0, 0, -1) : along.multiply(1 / apart);
        Vec3d side = new Vec3d(-along.z, 0, along.x);
        double back = Math.max(10, apart * .75);
        Vec3d eye = middle.add(side.multiply(back)).add(0, 4 + back * .35, 0);
        aim(camera(server), eye, middle.add(0, 1, 0).subtract(eye));
    }

    /** Close in front of the two of them, for a look at the copy beside the player. */
    private void close(MinecraftServer server) {
        Vec3d between = home().add(.7, 0, -.6), eye = between.add(-1.2, 1.7, -4.6);
        aim(camera(server), eye, between.add(0, 1.1, 0).subtract(eye));
    }

    private ArmorStandEntity camera(MinecraftServer server) {
        var w = server.getOverworld();
        ArmorStandEntity stand = camera != null && w.getEntity(camera) instanceof ArmorStandEntity old ? old : null;
        if (stand == null) {
            stand = new ArmorStandEntity(EntityType.ARMOR_STAND, w);
            stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true); stand.setSilent(true);
            w.spawnEntity(stand);
            camera = stand.getUuid();
        }
        return stand;
    }

    private static void aim(ArmorStandEntity stand, Vec3d eye, Vec3d look) {
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        stand.refreshPositionAndAngles(eye.x, eye.y - stand.getStandingEyeHeight(), eye.z, yaw, pitch);
        stand.setHeadYaw(yaw); stand.setBodyYaw(yaw);
    }

    /** Stands the player at an offset from its mark, facing it. */
    private void place(MinecraftServer server, UUID uuid, double x, double z) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        Vec3d at = home().add(x, 0, z);
        float yaw = (float) Math.toDegrees(Math.atan2(x, -z));
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0;
        p.networkHandler.requestTeleport(at.x, at.y, at.z, yaw, 5);
    }

    private static FirstWizardEntity clientWizard(MinecraftClient c) {
        for (var e : c.world.getEntities()) if (e instanceof FirstWizardEntity k) return k;
        throw new AssertionError("The First Wizard never reached the client");
    }
    private static String fmt(Vec3d v) { return v == null ? "none" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
