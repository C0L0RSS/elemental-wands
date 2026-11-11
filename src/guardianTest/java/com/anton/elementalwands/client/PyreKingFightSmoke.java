package com.anton.elementalwands.client;

import com.anton.elementalwands.client.renderer.PyreKingRenderer;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.entity.pyreking.PyreKingRules;
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
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
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
 * The Pyre King's fight, move by move, against a Survival player who stands where each case puts
 * them: what every blow deals, what dodges it (range, a sidestep after the aim freezes, a jump, a
 * crouch), where a thrown sword lands, where the chain drags a caught player and where it sets
 * him down, and that the drawn sword is where the server sweeps it. A free fight then runs
 * through the phase change to his death against a player who stands, backs away, circles and
 * jumps, runs far off, eats, climbs out of reach and gets behind him. The window stays hidden and
 * the game muted. A camera off to one side takes the screenshots; an armor stand stands in for the
 * player, whom the game does not draw for another camera. Every case runs; the failures are
 * reported together at the end.
 */
public final class PyreKingFightSmoke implements ClientModInitializer {
    /** A move against a player standing at an offset from him (he faces -Z). expected &lt; 0 skips the damage check. */
    private record Case(String name, PyreKingRules.Move move, double x, double y, double z, boolean hover, boolean sneak,
                        int ticks, float expected, int... shots) {}

    private static final float SWORD = PyreKingRules.SWORD, CHAIN = PyreKingRules.CHAIN;
    private static final List<Case> CASES = List.of(
            new Case("royal-cut", PyreKingRules.Move.ROYAL_CUT, 0, 0, -3.5, false, false, 70, SWORD, 14, 22, 40),
            new Case("royal-cut-out-of-reach", PyreKingRules.Move.ROYAL_CUT, 0, 0, -9.5, false, false, 60, 0),
            // A raised shield stops the first cut and is knocked aside; the backhand lands.
            new Case("royal-cut-shielded", PyreKingRules.Move.ROYAL_CUT, 0, 0, -3.5, false, false, 70, SWORD, 24),
            new Case("thrust", PyreKingRules.Move.THRUST, 0, 0, -7.5, false, false, 50, SWORD, 12, 21),
            new Case("thrust-sidestepped", PyreKingRules.Move.THRUST, 0, 0, -7, false, false, 50, 0),
            new Case("turning-cut", PyreKingRules.Move.TURNING_CUT, 0, 0, 3.5, false, false, 60, SWORD, 9, 17, 19),
            new Case("overhead", PyreKingRules.Move.OVERHEAD, 0, 0, -6, false, false, 50, SWORD, 12, 18, 20),
            new Case("scythe-low", PyreKingRules.Move.SCYTHE_LOW, 0, 0, -10, false, false, 60, CHAIN, 13, 19, 22),
            new Case("scythe-low-jumped", PyreKingRules.Move.SCYTHE_LOW, 0, 1, -10, true, false, 60, 0, 20),
            new Case("scythe-high", PyreKingRules.Move.SCYTHE_HIGH, 10, 0, 0, false, false, 60, CHAIN, 17, 21),
            new Case("scythe-high-crouched", PyreKingRules.Move.SCYTHE_HIGH, 10, 0, 0, false, true, 60, 0, 21),
            new Case("cast", PyreKingRules.Move.CAST, 0, 0, -20, false, false, 100, CHAIN, 14, 24, 29, 50),
            new Case("cast-reel", PyreKingRules.Move.CAST, 0, 0, -20, false, false, 100, CHAIN * 2, 62, 74, 77),
            new Case("zip", PyreKingRules.Move.ZIP, 0, 0, -24, false, false, 132, CHAIN, 62, 76, 80, 86, 100),
            new Case("hook", PyreKingRules.Move.HOOK, 0, 0, -30, false, false, 70, PyreKingRules.TUG, 16, 26, 36, 50),
            new Case("lunge", PyreKingRules.Move.LUNGE, 0, 0, -13, false, false, 76, SWORD, 12, 20, 28, 36, 40),
            new Case("drag-down", PyreKingRules.Move.DRAG_DOWN, 0, 8, -7.5, true, false, 56, PyreKingRules.YANK, 14, 22, 28),
            // A pillar stands between them in the cases named for it (5 across, 8 to 12 blocks in front of him).
            new Case("cast-at-a-pillar", PyreKingRules.Move.CAST, 0, 0, -20, false, false, 100, 0, 29, 50),
            new Case("hook-at-a-pillar", PyreKingRules.Move.HOOK, 0, 0, -30, false, false, 70, 0, 26),
            new Case("rounds-a-pillar", null, 0, 0, -20, false, false, 240, -1, 40, 80, 120, 160, 200),
            new Case("unbound", PyreKingRules.Move.UNBOUND, 0, 0, -8, false, false, 96, PyreKingRules.UNBOUND_SWEEP, 16, 28, 37, 44, 58, 70),
            new Case("unbound-behind-a-pillar", PyreKingRules.Move.UNBOUND, 0, 0, -10.5, false, false, 96, 0, 37));
    private static final int FIGHT_TICKS = 1240;
    /** -PpyreFightRecord: also save a frame of the free fight every other tick, for a review clip. */
    private static final boolean RECORD = Boolean.getBoolean("pyreFightRecord");

    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks, floor, index = -1, began = -1000;
    private UUID king;
    private volatile UUID camera;
    private UUID standIn;
    private final List<String> report = new ArrayList<>(), failures = new ArrayList<>();
    // What the server thread measured for the case in hand.
    private volatile float healthBefore;
    private volatile Vec3d[] serverBlade;
    private volatile double[][] clientBlade;
    private final Set<String> movesSeen = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile boolean sawSecondPhase, sawUnbound, kingGone;
    private volatile float fightDamage;
    private volatile double nearest;
    private final StringBuffer timeline = new StringBuffer();
    private String lastMove = "";
    private Vec3d focus;
    private double span;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 12000) throw new AssertionError("Pyre King fight timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.createIntegratedServerLoader().createAndStart("pyre-king-fight-" + System.currentTimeMillis(),
                        new LevelInfo("Pyre King fight verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
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
            // Seen from the side by a camera that stands off the line between them.
            c.options.setPerspective(Perspective.FIRST_PERSON);
            if (camera != null && c.world.getEntity(camera) != null && c.getCameraEntity() != c.world.getEntity(camera)) c.setCameraEntity(c.world.getEntity(camera));
            boolean fight = index == CASES.size();
            Case scene = index < 0 || fight ? null : CASES.get(index);
            int length = fight ? FIGHT_TICKS : scene == null ? 0 : scene.ticks;
            if (index < 0 || ticks - began > length) {
                if (scene != null) server.execute(() -> guard(() -> verify(server, uuid, scene)));
                if (fight) { finishFight(); finish(c); return; }
                index++;
                began = ticks + 50;   // time for the last case to be judged, and for both to take their places
                int next = index;
                c.options.sneakKey.setPressed(next < CASES.size() && CASES.get(next).sneak);
                c.options.useKey.setPressed(next < CASES.size() && CASES.get(next).name.contains("shielded"));
                server.execute(() -> guard(() -> stage(server, uuid, next)));
                return;
            }
            int t = ticks - began;
            server.execute(() -> guard(() -> shadow(server, uuid)));
            if (fight) { fightTick(c, server, uuid, t); return; }
            if (t == 0) {
                require(c.getEntityRenderDispatcher().getRenderer(clientKing(c)) instanceof PyreKingRenderer, "Wrong renderer for the Pyre King");
                server.execute(() -> guard(() -> {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                    healthBefore = p.getHealth();
                    if (scene.move == null) king(server).startFight();
                    else king(server).testMove(p, scene.move);
                }));
            }
            during(server, uuid, scene, t);
            for (int shot : scene.shots) if (t == shot + 2) shot(c, "fight-" + scene.name + "-" + shot + ".png");
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("PYRE_KING_FIGHT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void guard(Runnable work) {
        try { work.run(); } catch (Throwable e) { e.printStackTrace(); serverFailure = e.toString(); }
    }

    // ── The cases ────────────────────────────────────────────────────────────

    /** What a case does to the player while the move plays, and what it measures along the way. */
    private void during(MinecraftServer server, UUID uuid, Case scene, int t) {
        switch (scene.name) {
            // The aim froze 0.35 s in; a step aside after that beats it.
            case "thrust-sidestepped" -> { if (t == 13) server.execute(() -> guard(() -> place(server, uuid, 3.5, 0, -7, false))); }
            // Struck by the throw, then well clear of the fire it leaves and of the pull-back.
            case "cast" -> {
                if (t == 31) server.execute(() -> guard(() -> place(server, uuid, 7, 0, -20, false)));
                if (t == 44) server.execute(() -> guard(() -> serverBlade = king(server).sweptBlade()));
                if (t == 45) clientBlade = PyreKingRenderer.drawnBlade();
            }
            // Struck by the throw, then standing on the line the sword comes back along.
            case "cast-reel" -> { if (t == 31) server.execute(() -> guard(() -> place(server, uuid, 0, 0, -9, false))); }
            case "cast-at-a-pillar" -> {
                if (t == 44) server.execute(() -> guard(() -> serverBlade = king(server).sweptBlade()));
                if (t == 45) clientBlade = PyreKingRenderer.drawnBlade();
            }
            // He closes on a player who never moves; his first blow would knock them away, so the fixture watches how near he got.
            case "rounds-a-pillar" -> server.execute(() -> guard(() -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                Vec3d spot = home().add(0, 0, -20);
                nearest = t <= 1 ? 99 : Math.min(nearest, king(server).getEntityPos().subtract(spot).multiply(1, 0, 1).length());
                if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            }));
            case "zip" -> {
                if (t == 31) server.execute(() -> guard(() -> place(server, uuid, 8, 0, -24, false)));
                if (t == 104) server.execute(() -> guard(() -> serverBlade = king(server).sweptBlade()));
                if (t == 105) clientBlade = PyreKingRenderer.drawnBlade();
            }
            case "lunge" -> {
                if (t == 52) server.execute(() -> guard(() -> serverBlade = king(server).sweptBlade()));
                if (t == 53) clientBlade = PyreKingRenderer.drawnBlade();
            }
            // Swept by the chains, then out of the hall's fire walls before they light.
            case "unbound" -> {
                if (t == 46) server.execute(() -> guard(() -> place(server, uuid, 0, 0, -22, false)));
                if (t == 62) server.execute(() -> guard(() -> { if (king(server).status().contains("fires 6")) sawUnbound = true; }));
            }
            default -> {}
        }
    }

    private void verify(MinecraftServer server, UUID uuid, Case scene) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        PyreKingEntity k = king(server);
        float dealt = healthBefore - p.getHealth();
        Vec3d from = home(), at = k.getEntityPos(), player = p.getEntityPos();
        List<String> notes = new ArrayList<>();
        if (scene.expected >= 0 && Math.abs(dealt - scene.expected) > .01f) fail(scene, "dealt " + dealt + ", expected " + scene.expected);
        switch (scene.name) {
            case "royal-cut-shielded" -> {
                boolean aside = p.getItemCooldownManager().isCoolingDown(p.getOffHandStack());
                notes.add(aside ? "the shield was knocked aside" : "the shield was not knocked aside");
                if (!aside) fail(scene, "a blocked blow should knock the shield aside");
            }
            case "turning-cut" -> {
                float turned = Math.abs(MathHelper.wrapDegrees(k.getBodyYaw() - 0));
                notes.add("ends facing " + Math.round(k.getBodyYaw()));
                if (turned > 5) fail(scene, "he should end turned about, facing yaw 0; he faces " + k.getBodyYaw());
            }
            case "cast" -> agree(scene, notes, new Vec3d(from.x, floor, from.z - 20), 1.8);
            case "zip" -> {
                double short_ = at.subtract(from.x, at.y, from.z - 24).length();
                notes.add("landed " + round(short_) + " from where the sword stuck");
                if (short_ < .6 || short_ > 3.6) fail(scene, "he should land beside his sword, 24 blocks out; he is " + round(short_) + " from it at " + at);
                agree(scene, notes, null, 0);
            }
            case "hook" -> {
                double left = player.subtract(at).multiply(1, 0, 1).length();
                notes.add("dragged to " + round(left) + " from him");
                if (left > 6.5) fail(scene, "the hooked player should be dragged to his feet; they are " + round(left) + " away");
            }
            case "lunge" -> {
                // The blow knocks the player back, so measure from where they stood.
                double left = at.subtract(from.x, at.y, from.z - 13).length();
                notes.add("landed " + round(left) + " from where the player stood");
                if (left < 2 || left > 5) fail(scene, "he should land a sword's length from the player; he is " + round(left) + " away");
                agree(scene, notes, null, 0);
            }
            case "drag-down" -> {
                notes.add("player ends " + round(player.y - floor) + " above the floor");
                if (player.y - floor > .8) fail(scene, "the yanked player should be on the floor; they are " + round(player.y - floor) + " up");
            }
            case "cast-at-a-pillar" -> {
                Vec3d[] swept = serverBlade; double[][] drawn = clientBlade;
                agree(scene, notes, null, 0);
                if (swept != null) {
                    // The blade is driven in at a slant, so its point runs on under the floor: measure the hilt.
                    double reached = from.z - swept[0].z;
                    notes.add("the sword stuck " + round(reached) + " blocks out; the pillar's face is 7.5 out");
                    if (reached > 7.2) fail(scene, "the thrown sword should stop short of the pillar; it stuck " + round(reached) + " blocks out");
                }
            }
            case "rounds-a-pillar" -> {
                notes.add("got within " + round(nearest) + " of the player");
                if (nearest > 7) fail(scene, "he should run round the pillar to the player; he got no nearer than " + round(nearest));
            }
            case "unbound" -> {
                if (!sawUnbound) fail(scene, "six walls of fire should burn after the hooks catch");
                if (!k.isSecondPhase()) fail(scene, "he should be in phase two after Unbound");
            }
            default -> {}
        }
        report.add(String.format(Locale.ROOT, "%-24s dealt %5.1f%s%s", scene.name, dealt, scene.expected >= 0 ? " (expected " + scene.expected + ")" : "",
                notes.isEmpty() ? "" : "; " + String.join("; ", notes)));
    }

    /** The drawn sword must be where the server sweeps it, and (given a target) the thrown sword must have landed on it. */
    private void agree(Case scene, List<String> notes, Vec3d target, double within) {
        Vec3d[] swept = serverBlade; double[][] drawn = clientBlade;
        serverBlade = null; clientBlade = null;
        if (swept == null || drawn == null) { fail(scene, "the blade was not measured"); return; }
        double pommel = swept[0].distanceTo(new Vec3d(drawn[0][0], drawn[0][1], drawn[0][2])), tip = swept[1].distanceTo(new Vec3d(drawn[1][0], drawn[1][1], drawn[1][2]));
        notes.add("drawn sword off the swept one by " + round(pommel) + " (pommel) and " + round(tip) + " (tip)");
        if (pommel > .35 || tip > .5) fail(scene, "the drawn sword is not where the server sweeps it: pommel " + round(pommel) + ", tip " + round(tip)
                + "; swept " + swept[0] + " to " + swept[1] + ", drawn " + java.util.Arrays.toString(drawn[0]) + " to " + java.util.Arrays.toString(drawn[1]));
        if (target != null) {
            // The blade is driven in at a slant: where it meets the floor is the aimed point.
            Vec3d a = swept[0], b = swept[1];
            double f = a.y - floor <= 0 || b.y - floor >= 0 ? 1 : (a.y - floor) / (a.y - b.y);
            Vec3d landed = a.add(b.subtract(a).multiply(f));
            double miss = landed.subtract(target).multiply(1, 0, 1).length();
            notes.add("thrown sword landed " + round(miss) + " from where the player stood");
            if (miss > within) fail(scene, "the thrown sword should land on the player; it landed " + round(miss) + " away at " + landed);
        }
    }

    // ── The free fight ───────────────────────────────────────────────────────

    private void fightTick(MinecraftClient c, MinecraftServer server, UUID uuid, int t) {
        if (t == 0) server.execute(() -> guard(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            healthBefore = p.getHealth();
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLDEN_APPLE, 64));
            king(server).startFight();
        }));
        c.options.useKey.setPressed(t >= 560 && t < 700);      // eating in the open
        if (t > 0) server.execute(() -> guard(() -> {
            if (!(server.getOverworld().getEntity(king) instanceof PyreKingEntity k) || !k.isAlive()) { kingGone = true; return; }
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            String move = k.moveName();
            movesSeen.add(move);
            if (!move.equals(lastMove)) { timeline.append(t).append(' ').append(move).append(", "); lastMove = move; }
            if (k.isSecondPhase()) sawSecondPhase = true;
            // Minutes under his sword would kill anyone: count each wound and heal it.
            if (p.getHealth() < p.getMaxHealth()) { fightDamage += p.getMaxHealth() - p.getHealth(); p.setHealth(p.getMaxHealth()); }
            // The player never fights back, so the fixture wounds him: to half health, then to death.
            if (t == 320) k.damage(server.getOverworld(), server.getOverworld().getDamageSources().playerAttack(p), 400);
            if (t >= 1120 && t % 12 == 0) k.damage(server.getOverworld(), server.getOverworld().getDamageSources().playerAttack(p), 400);
            act(server, p, k, t);
            follow(server, k.getEntityPos(), p.getEntityPos());
        }));
        if (t > 0 && t % 45 == 0) shot(c, String.format(Locale.ROOT, "fight-free-%04d.png", t));
        if (RECORD && t > 0 && t % 2 == 0) shot(c, String.format(Locale.ROOT, "record-%05d.png", t / 2));
    }

    /** What the player does through the free fight, one stretch after another. */
    private void act(MinecraftServer server, ServerPlayerEntity p, PyreKingEntity k, int t) {
        Vec3d king = k.getEntityPos(), at = p.getEntityPos(), away = at.subtract(king).multiply(1, 0, 1);
        double apart = away.length();
        Vec3d out = apart < .1 ? new Vec3d(0, 0, -1) : away.normalize();
        Vec3d next = null;
        boolean hover = false;
        if (t < 120) return;                                                             // stands and takes it
        else if (t < 260) next = at.add(out.multiply(4.3 / 20));                         // backs straight away at a walk
        else if (t < 420) {                                                              // circles him at a sprint, hopping
            double angle = Math.atan2(out.z, out.x) + 5.6 / 10 / 20;
            int hop = t % 36;
            next = king.add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10).add(0, hop < 12 ? 1.25 * Math.sin(Math.PI * hop / 12) : 0, 0);
            hover = hop < 12;
        }
        else if (t < 560) { if (apart < 30) next = at.add(out.multiply(5.6 / 20)); }     // runs far off
        else if (t < 700) return;                                                        // eats where they stand
        else if (t < 820) { next = new Vec3d(at.x, floor + 6, at.z); hover = true; }     // climbs out of reach
        else if (t < 960) {                                                              // gets behind him and stays there
            float yaw = k.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
            next = king.add(MathHelper.sin(yaw) * 3.2, 0, -MathHelper.cos(yaw) * 3.2);
        }
        else return;
        if (next == null) return;
        double y = hover ? next.y : floor;
        next = new Vec3d(MathHelper.clamp(next.x, -36, 37), y, MathHelper.clamp(next.z, -44, 24));
        Vec3d look = king.subtract(next);
        p.setNoGravity(hover);
        p.fallDistance = 0;
        p.networkHandler.requestTeleport(next.x, next.y, next.z, (float) Math.toDegrees(Math.atan2(-look.x, look.z)), 5);
    }

    /** The camera keeps both of them in frame, easing after them. */
    private void follow(MinecraftServer server, Vec3d king, Vec3d player) {
        if (!(server.getOverworld().getEntity(camera) instanceof net.minecraft.entity.decoration.ArmorStandEntity stand)) return;
        Vec3d between = player.subtract(king).multiply(1, 0, 1);
        double apart = between.length();
        Vec3d middle = king.add(between.multiply(.5));
        if (focus == null) { focus = middle; span = apart; }
        focus = focus.add(middle.subtract(focus).multiply(.06));
        span += (apart - span) * .04;
        double back = Math.max(15, span * .95 + 6);
        Vec3d eye = new Vec3d(focus.x + back * .8, floor + 5 + back * .3, focus.z + back * .75), look = focus.add(0, 1.8, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        stand.refreshPositionAndAngles(eye.x, eye.y - stand.getStandingEyeHeight(), eye.z, yaw, pitch);
        stand.setHeadYaw(yaw); stand.setBodyYaw(yaw);
    }

    private void finishFight() {
        Set<String> moves = new LinkedHashSet<>(movesSeen);
        moves.remove("free");
        report.add("free fight: moves used " + moves + "; the player took " + fightDamage);
        report.add("free fight timeline (tick move): " + timeline);
        if (moves.size() < 7) failures.add("free fight: he should use at least seven different moves in a minute; he used " + moves);
        if (!moves.contains("scythe_low") && !moves.contains("scythe_high")) failures.add("free fight: a player circling at 10 blocks should draw the Chain Scythe");
        if (!moves.contains("cast") && !moves.contains("hook") && !moves.contains("lunge")) failures.add("free fight: a player far off should draw a throw or a Lunge");
        if (!moves.contains("drag_down")) failures.add("free fight: a player out of reach overhead should draw Drag Down");
        if (!moves.contains("unbound")) failures.add("free fight: Unbound should start once he is wounded to half health");
        if (!sawSecondPhase) failures.add("free fight: he should reach phase two");
        if (fightDamage <= 0) failures.add("free fight: the player should be hit");
        if (!kingGone) failures.add("free fight: he should die when his health is gone");
    }

    private void finish(MinecraftClient c) throws java.io.IOException {
        String body = String.join("\n", report) + "\n";
        Files.writeString(Path.of("PYRE_KING_FIGHT_REPORT.txt"), body + (failures.isEmpty() ? "" : "\nFAILURES\n" + String.join("\n", failures) + "\n"));
        if (failures.isEmpty()) Files.writeString(Path.of("PYRE_KING_FIGHT_PASSED.txt"), "Pyre King fight passed: every move against a standing Survival player "
                + "(damage, reach, the sidestep, the jump, the crouch, the thrown sword's landing, the drags), the drawn sword where the server sweeps it, "
                + "and a free fight through Unbound to his death. Human Lunar review pending.\n\n" + body);
        else Files.writeString(Path.of("PYRE_KING_FIGHT_FAILED.txt"), String.join("\n", failures) + "\n");
        done = true; c.scheduleStop();
    }

    private void fail(Case scene, String why) { failures.add(scene.name + ": " + why); }

    // ── Staging ──────────────────────────────────────────────────────────────

    private Vec3d home() { return new Vec3d(.5, floor, .5); }

    private void arrange(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        for (int x = -40; x <= 40; x++) for (int z = -48; z <= 28; z++) {
            w.setBlockState(new BlockPos(x, floor - 1, z), ((x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.BLACKSTONE).getDefaultState());
            for (int y = 0; y < 20; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        w.setTimeOfDay(6000); w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        w.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
        var p = server.getPlayerManager().getPlayer(uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 100000, 0, false, false));
        // Unarmored and very healthy: every blow shows as its raw damage, and none of them kills.
        p.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1000);
        p.setHealth(1000);
        summon(server);
    }

    private void summon(MinecraftServer server) {
        var w = server.getOverworld();
        if (king != null && w.getEntity(king) != null) w.getEntity(king).discard();
        PyreKingEntity mob = ModEntities.PYRE_KING.spawn(w, BlockPos.ofFloored(.5, floor, .5), SpawnReason.COMMAND);
        require(mob != null, "Could not spawn the Pyre King");
        mob.stopFight();
        mob.refreshPositionAndAngles(.5, floor, .5, 180, 0);
        mob.setBodyYaw(180); mob.setHeadYaw(180);
        king = mob.getUuid();
    }

    private PyreKingEntity king(MinecraftServer server) {
        if (server.getOverworld().getEntity(king) instanceof PyreKingEntity k) return k;
        throw new AssertionError("The Pyre King is gone");
    }

    /** Both take their places for the next case: he stands passive on his mark facing -Z; the free fight gets a fresh king. */
    private void stage(MinecraftServer server, UUID uuid, int next) {
        boolean fight = next >= CASES.size();
        if (fight) summon(server);
        PyreKingEntity k = king(server);
        k.stopFight();
        k.refreshPositionAndAngles(.5, floor, .5, 180, 0);
        k.setBodyYaw(180); k.setHeadYaw(180);
        k.setVelocity(Vec3d.ZERO);
        k.setHealth(k.getMaxHealth());
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        p.setHealth(p.getMaxHealth());
        p.extinguish();
        boolean shield = !fight && CASES.get(next).name.contains("shielded");
        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        p.setStackInHand(net.minecraft.util.Hand.OFF_HAND, shield ? new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHIELD) : net.minecraft.item.ItemStack.EMPTY);
        p.getItemCooldownManager().remove(net.minecraft.util.Identifier.ofVanilla("shield"));
        pillar(server, !fight && CASES.get(next).name.contains("pillar"), !fight && CASES.get(next).name.startsWith("unbound") ? -8 : -12);
        if (fight) { place(server, uuid, 0, 0, -15, false); watch(server, 0, -15); }
        else { Case scene = CASES.get(next); place(server, uuid, scene.x, scene.y, scene.z, scene.hover); watch(server, scene.x, scene.z); }
    }

    /** The camera is not the player, so the game does not draw them: an armor stand stands in, wherever they are. */
    private void shadow(MinecraftServer server, UUID uuid) {
        var w = server.getOverworld();
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        net.minecraft.entity.decoration.ArmorStandEntity stand = standIn != null && w.getEntity(standIn) instanceof net.minecraft.entity.decoration.ArmorStandEntity old ? old : null;
        if (stand == null) {
            stand = new net.minecraft.entity.decoration.ArmorStandEntity(net.minecraft.entity.EntityType.ARMOR_STAND, w);
            stand.setNoGravity(true); stand.setInvulnerable(true); stand.setSilent(true);
            stand.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLDEN_HELMET));
            stand.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
            w.spawnEntity(stand);
            standIn = stand.getUuid();
        }
        stand.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0);
        stand.setHeadYaw(p.getYaw()); stand.setBodyYaw(p.getYaw());
    }

    /** Stands the camera off to one side of the two of them, far enough back to hold both and the sword's reach. */
    private void watch(MinecraftServer server, double x, double z) {
        var w = server.getOverworld();
        double apart = Math.hypot(x, z);
        Vec3d middle = home().add(x * .5, 0, z * .5), along = apart < .1 ? new Vec3d(0, 0, -1) : new Vec3d(x / apart, 0, z / apart);
        Vec3d side = new Vec3d(-along.z, 0, along.x);
        double back = Math.max(13, apart * .85);
        Vec3d eye = middle.add(side.multiply(back)).add(along.multiply(-back * .25)).add(0, 4 + back * .22, 0), look = middle.add(0, 1.8, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        net.minecraft.entity.decoration.ArmorStandEntity stand = camera != null && w.getEntity(camera) instanceof net.minecraft.entity.decoration.ArmorStandEntity old ? old : null;
        if (stand == null) {
            stand = new net.minecraft.entity.decoration.ArmorStandEntity(net.minecraft.entity.EntityType.ARMOR_STAND, w);
            stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true); stand.setSilent(true);
            w.spawnEntity(stand);
            camera = stand.getUuid();
        }
        stand.refreshPositionAndAngles(eye.x, eye.y - stand.getStandingEyeHeight(), eye.z, yaw, pitch);
        stand.setHeadYaw(yaw); stand.setBodyYaw(yaw);
    }

    /** A pillar 5 across and 8 tall in front of his mark, its far face at block z = far (8 to 12 blocks out, or 4 to 8 for Unbound); or none. */
    private void pillar(MinecraftServer server, boolean stands, int far) {
        var w = server.getOverworld();
        for (int x = -2; x <= 2; x++) for (int z = -12; z <= -4; z++) for (int y = 0; y < 8; y++)
            w.setBlockState(new BlockPos(x, floor + y, z), (stands && z >= far && z < far + 5 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR).getDefaultState());
    }

    /** Stands the player at an offset from his mark, facing him. */
    private void place(MinecraftServer server, UUID uuid, double x, double y, double z, boolean hover) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        Vec3d at = home().add(x, y, z);
        float yaw = (float) Math.toDegrees(Math.atan2(x, -z));
        p.setNoGravity(hover);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0;
        p.networkHandler.requestTeleport(at.x, at.y, at.z, yaw, hover && y > 3 ? 40 : 5);
    }

    private static PyreKingEntity clientKing(MinecraftClient c) {
        for (var e : c.world.getEntities()) if (e instanceof PyreKingEntity k) return k;
        throw new AssertionError("The Pyre King never reached the client");
    }
    private static String round(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
