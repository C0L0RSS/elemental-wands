package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.summit.StormAerie;
import com.anton.elementalwands.summit.StormAerieLayout;
import com.anton.elementalwands.summit.StormGongBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.processing.AnimationController;

/**
 * Native look at naturally generated Storm aeries and at the gong in every rotation. Creates a
 * normal world with the seed the worldgen fixture used (-Daerie.seed) and, for each
 * {@code label@x,y,z} gong anchor in -Daerie.gongs (separated by ';'), photographs the aerie from the
 * slope below, from the stand point, in three-quarter view and from far off, then strikes it from
 * the server and photographs the swing. Then it places the template in all four rotations on stone
 * pads high above the land and photographs each gong at rest and mid-swing from the same spot in its
 * own frame, so every rotation should look alike. Each swing is caught twice by the animation's
 * own clock: swinging away from the striker, and swinging back. A second strike one second in must
 * restart the swing: the client then plays the other strike from near zero, photographed as well. Silent and hidden; screenshots land in
 * build/aerie-client-smoke/screenshots/. They are not a substitute for a Lunar look.
 */
public final class AerieClientSmoke implements ClientModInitializer {
    private static final BlockRotation[] ROTATIONS = {BlockRotation.NONE, BlockRotation.CLOCKWISE_90, BlockRotation.CLOCKWISE_180, BlockRotation.COUNTERCLOCKWISE_90};
    /** Ticks a view holds before its screenshot, so the land around it has drawn. */
    private static final int HOLD = 110;

    /** One gong to photograph: its label, anchor, and the cameras worked out on the server. */
    private static final class Target {
        final String label;
        final BlockPos anchor;
        final boolean natural;
        /** Camera per view: x, y, z (feet), yaw, pitch; the swing's camera; set on the server, views last. */
        volatile String[] names;
        volatile double[] swingView;
        volatile List<double[]> views;
        Target(String label, BlockPos anchor, boolean natural) { this.label = label; this.anchor = anchor; this.natural = natural; }
    }

    private boolean started, done;
    private int ticks, stage, scene, target, swingSeen, swingShots;
    /** The strike animation the first swing played, so the second strike's restart can be told apart. */
    private String firstSwing = "";
    private volatile String serverFailure;
    /** Natural aeries first; the server adds the four placed rotations. */
    private final List<Target> targets = new CopyOnWriteArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 24000) throw new AssertionError("Aerie client timeout at stage " + stage + " target " + target);
            if (!started) {
                // Silent before any world loads; this run has its own options file.
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                c.getSoundManager().stopAll();
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.options.pauseOnLostFocus = false;
                c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(14);
                c.options.write();
                started = true;
                for (String entry : System.getProperty("aerie.gongs", "").split(";")) {
                    if (entry.isBlank()) continue;
                    String[] parts = entry.split("@"), at = parts[1].split(",");
                    targets.add(new Target(parts[0].trim(), new BlockPos(Integer.parseInt(at[0].trim()), Integer.parseInt(at[1].trim()), Integer.parseInt(at[2].trim())), true));
                }
                if (targets.isEmpty()) throw new AssertionError("Pass -PaerieGongs=label@x,y,z[;label@x,y,z] (gong anchors from the worldgen fixture)");
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                long seed = Long.parseLong(System.getProperty("aerie.seed", "12345"));
                c.createIntegratedServerLoader().createAndStart("aerie-" + System.currentTimeMillis(),
                        new LevelInfo("Aerie verification", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(seed, true, false), WorldPresets::createDemoOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            MinecraftServer server = c.getServer();
            UUID id = c.player.getUuid();
            c.setScreen(null);
            WandWelcome.reset();
            c.options.hudHidden = true;
            switch (stage) {
                case 0 -> { // steady noon light, then the template in four rotations on pads in the sky
                    if (++scene < 20) return;
                    BlockPos first = targets.getFirst().anchor;
                    onServer(server, () -> {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                        p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                        ServerWorld world = p.getEntityWorld();
                        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                        world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                        world.setTimeOfDay(6000);
                        world.setWeather(12000, 0, false, false);
                        StructureTemplate template = world.getStructureTemplateManager().getTemplate(Identifier.of("elementalwands", "storm_aerie")).orElseThrow();
                        for (int i = 0; i < ROTATIONS.length; i++) {
                            BlockPos origin = new BlockPos(first.getX() + 480 + 48 * i, 280, first.getZ());
                            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) world.getChunk((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z);
                            StructurePlacementData data = new StructurePlacementData().setRotation(ROTATIONS[i]).setIgnoreEntities(true);
                            BlockBox box = template.calculateBoundingBox(data, origin);
                            int floor = origin.getY() + StormAerieLayout.FLOOR;
                            for (BlockPos pos : BlockPos.iterate(box.getMinX() - 2, floor - 1, box.getMinZ() - 2, box.getMaxX() + 2, floor, box.getMaxZ() + 2))
                                world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                            if (!template.place(world, origin, origin, data, world.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE))
                                throw new AssertionError("Template did not place in " + ROTATIONS[i]);
                            int[] g = StormAerieLayout.GONG;
                            BlockPos anchor = origin.add(StructureTemplate.transform(data, new BlockPos(g[0], g[1], g[2])));
                            targets.add(new Target("rotation-" + ROTATIONS[i].name().toLowerCase(), anchor, false));
                        }
                    });
                    next();
                }
                case 1 -> { // carry the player to the target and let the land generate around it
                    if (scene++ == 0) {
                        Target t = targets.get(target);
                        onServer(server, () -> {
                            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                            ServerWorld world = p.getEntityWorld();
                            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) world.getChunk((t.anchor.getX() >> 4) + x, (t.anchor.getZ() >> 4) + z);
                            p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                            p.networkHandler.requestTeleport(t.anchor.getX() + .5, t.anchor.getY() + 30, t.anchor.getZ() + .5, 0, 90);
                        });
                    }
                    if (scene < 40) return;
                    next();
                }
                case 2 -> { // the gong exists on the server and the client; work out the views
                    Target t = targets.get(target);
                    if (++scene == 1) onServer(server, () -> {
                        ServerWorld world = server.getPlayerManager().getPlayer(id).getEntityWorld();
                        StormAerie.Gong gong = StormAerie.gong(world, t.anchor);
                        if (gong == null || !gong.anchor().equals(t.anchor))
                            throw new AssertionError(t.label + ": no gong anchor at " + t.anchor + " in seed " + world.getSeed() + ": " + world.getBlockState(t.anchor));
                        if (!(world.getBlockEntity(t.anchor) instanceof StormGongBlockEntity)) throw new AssertionError(t.label + ": the anchor has no gong block entity");
                        plan(world, gong, t);
                    });
                    if (t.views == null || !(c.world.getBlockEntity(t.anchor) instanceof StormGongBlockEntity)
                            || !c.world.getBlockState(t.anchor.up(3)).isOf(ModBlocks.STORM_GONG_PART)) {
                        if (scene < 600) return;
                        throw new AssertionError(t.label + ": the client never received the gong at " + t.anchor);
                    }
                    next();
                }
                case 3 -> { // a view every HOLD ticks: teleport, hold the camera, wait for terrain, shoot
                    Target t = targets.get(target);
                    int view = scene / HOLD, tickInView = scene % HOLD;
                    scene++;
                    if (view >= t.views.size()) { next(); return; }
                    double[] v = t.views.get(view);
                    if (tickInView == 0) teleport(server, id, v);
                    look(c, (float) v[3], (float) v[4]);
                    if (tickInView == HOLD - 5) shoot(c, t, view + 1, t.names[view], v, "");
                }
                case 4 -> { // the swing: the server strikes the gong, and the client plays it
                    Target t = targets.get(target);
                    double[] v = t.swingView;
                    if (scene == 0) { teleport(server, id, v); swingSeen = -1; }
                    look(c, (float) v[3], (float) v[4]);
                    scene++;
                    if (scene == HOLD - 30) onServer(server, () -> {
                        ServerWorld world = server.getPlayerManager().getPlayer(id).getEntityWorld();
                        ((StormGongBlockEntity) world.getBlockEntity(t.anchor)).strike();
                    });
                    if (scene < HOLD - 30) return;
                    var controller = swing(c, t);
                    if (swingSeen < 0) {
                        if (controller.getTriggeredAnimation() != null) { swingSeen = scene; swingShots = 0; }
                        else if (scene > HOLD + 40) throw new AssertionError(t.label + ": the client never played the strike");
                        return;
                    }
                    // The controller blends into the strike over a few ticks; then two frames of the swing
                    // itself: away from the striker (its first peak, 0.1 s in) and back (0.45 s in).
                    var playing = controller.getCurrentAnimation();
                    String name = playing == null ? "-" : playing.animation().name();
                    boolean running = controller.getAnimationState() == AnimationController.State.RUNNING && controller.getTriggeredAnimation() != null;
                    double at = controller.getCurrentAnimationSeconds();
                    if (swingShots == 0 && running && at >= .06 && at <= .3) {
                        shoot(c, t, t.views.size() + 1, "swing", v, String.format(" %s at %.2f s (first swing away from the striker peaks at 0.10 s)", name, at));
                        firstSwing = name;
                        swingShots = 1;
                    } else if (swingShots == 1 && running && at >= .38 && at <= .6) {
                        shoot(c, t, t.views.size() + 2, "swing-back", v, String.format(" %s at %.2f s (swing back peaks at 0.45 s)", name, at));
                        swingShots = 2;
                    }
                    if (scene == swingSeen + 30 && swingShots < 2)
                        throw new AssertionError(t.label + ": caught " + swingShots + " of 2 swing frames; the strike was at " + at + " s, " + controller.getAnimationState());
                    // A second strike a second after the first, while the first swing is still playing,
                    // must start the swing afresh: the other strike, its clock back near zero.
                    if (scene == swingSeen + 20) onServer(server, () -> {
                        ServerWorld world = server.getPlayerManager().getPlayer(id).getEntityWorld();
                        ((StormGongBlockEntity) world.getBlockEntity(t.anchor)).strike();
                    });
                    if (swingShots == 2 && scene > swingSeen + 20 && running && !name.equals(firstSwing) && at >= .06 && at <= .3) {
                        shoot(c, t, t.views.size() + 3, "restrike", v, String.format(" %s at %.2f s, %d ticks after %s began", name, at, scene - swingSeen, firstSwing));
                        swingShots = 3;
                    }
                    if (scene == swingSeen + 45 && swingShots < 3)
                        throw new AssertionError(t.label + ": a strike 1 s into the swing did not restart it: playing " + name + " at " + at + " s (" + controller.getAnimationState()
                                + "), first swing " + firstSwing);
                    if (scene == swingSeen + 100) {
                        if (controller.getTriggeredAnimation() != null) throw new AssertionError(t.label + ": the second strike did not end within 3.5 s");
                        target++;
                        stage = target < targets.size() ? 1 : 5;
                        scene = 0;
                    }
                }
                case 5 -> {
                    if (++scene < 30) return;
                    Files.writeString(Path.of("AERIE_CLIENT_PASSED.txt"), "Seed " + System.getProperty("aerie.seed") + "; " + targets.size()
                            + " gongs found on server and client, struck from the server and seen swinging on the client; " + notes.size() + " screenshots:\n"
                            + String.join("\n", notes) + "\n");
                    done = true;
                    c.scheduleStop();
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("AERIE_CLIENT_FAILED.txt"), e + "\n" + String.join("\n", notes) + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true;
            c.scheduleStop();
        }
    }

    /** The anchor's strike controller as the client plays it. */
    private static AnimationController<?> swing(MinecraftClient c, Target t) {
        if (!(c.world.getBlockEntity(t.anchor) instanceof StormGongBlockEntity gong)) throw new AssertionError(t.label + ": the client lost the gong");
        return gong.getAnimatableInstanceCache().getManagerForId(0).getAnimationControllers().get(StormGongBlockEntity.CONTROLLER);
    }

    /**
     * Camera placements around a gong, on the server where the terrain is known. A natural aerie
     * gets the slope, stand, three-quarter and far views; a placed rotation the three-quarter view
     * only. The swing is photographed from the three-quarter view again, to compare with its rest.
     */
    private static void plan(ServerWorld world, StormAerie.Gong gong, Target t) {
        BlockPos foot = gong.foot();
        Direction forward = gong.facing(), right = forward.rotateYClockwise();
        int floor = foot.getY() - 1;
        Vec3d plate = Vec3d.ofCenter(gong.anchor()).add(0, 1.5, 0);
        // In three-quarter view: in front of the plate and off to its right, a little above a player's eyes.
        Vec3d quarter = Vec3d.ofBottomCenter(foot.offset(forward, 7).offset(right, 5)).add(0, 1, 0);
        double[] threeQuarter = camera(quarter, plate.add(0, -.5, 0));
        List<double[]> views = new ArrayList<>();
        t.swingView = threeQuarter;
        if (!t.natural) {
            views.add(threeQuarter);
            t.names = new String[]{"three-quarter"};
            t.views = views;
            return;
        }
        // 1. Climbing up from below the peak: a spot on the ground 6-40 blocks under the floor from which
        // a standing player's eyes can see the top of the gong frame over the rim.
        Vec3d frameTop = Vec3d.ofCenter(gong.anchor().up(5));
        double[] climb = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            for (int r = 24; r <= 64; r += 4) {
                int x = foot.getX() + (int) Math.round(Math.cos(a) * r), z = foot.getZ() + (int) Math.round(Math.sin(a) * r);
                int feet = surface(world, x, z), drop = floor - feet;
                if (drop < 6 || drop > 40) continue;
                Vec3d eye = new Vec3d(x + .5, feet + 1.62, z + .5);
                var hit = world.raycast(new net.minecraft.world.RaycastContext(eye, frameTop, net.minecraft.world.RaycastContext.ShapeType.VISUAL,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
                boolean clear = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS || hit.getPos().distanceTo(frameTop) < 4;
                if (!clear) continue;
                // Prefer the struck side, so the plate's face shows.
                Vec3d to = new Vec3d(x - foot.getX(), 0, z - foot.getZ()).normalize();
                double front = to.x * forward.getOffsetX() + to.z * forward.getOffsetZ();
                double score = -Math.abs(drop - 16) - Math.abs(r - 36) / 4.0 + 6 * front;
                if (score > bestScore) { bestScore = score; climb = camera(new Vec3d(x + .5, feet, z + .5), plate); }
            }
        }
        if (climb == null) {
            BlockPos p = foot.offset(forward, 36);
            climb = camera(new Vec3d(p.getX() + .5, Math.max(surface(world, p.getX(), p.getZ()), floor - 12), p.getZ() + .5), plate);
        }
        views.add(climb);
        // 2. At the stand point, facing the gong.
        views.add(camera(Vec3d.ofBottomCenter(StormAerie.cell(gong, StormAerieLayout.STAND)), plate));
        // 3. Three-quarter view.
        views.add(threeQuarter);
        // 4. The whole peak from 110 blocks off, 35 above the floor.
        Vec3d far = Vec3d.ofCenter(foot).add(Vec3d.of(forward.getVector()).add(Vec3d.of(right.getVector())).normalize().multiply(110));
        views.add(camera(new Vec3d(far.x, Math.max(floor + 35, surface(world, (int) far.x, (int) far.z) + 4), far.z), Vec3d.ofCenter(foot).add(0, -6, 0)));
        t.names = new String[]{"slope", "stand", "three-quarter", "far"};
        t.views = views;
    }

    /** Feet position and the yaw/pitch that point a standing player's eyes at a target. */
    private static double[] camera(Vec3d feet, Vec3d target) {
        Vec3d d = target.subtract(feet.add(0, 1.62, 0));
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        double pitch = -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        return new double[]{feet.x, feet.y, feet.z, yaw, pitch};
    }

    private static int surface(ServerWorld world, int x, int z) {
        world.getChunk(x >> 4, z >> 4);
        return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private void next() { stage++; scene = 0; }

    private void teleport(MinecraftServer server, UUID id, double[] v) {
        onServer(server, () -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            p.getAbilities().flying = true; p.sendAbilitiesUpdate();
            p.networkHandler.requestTeleport(v[0], v[1], v[2], (float) v[3], (float) v[4]);
        });
    }

    private void shoot(MinecraftClient c, Target t, int index, String view, double[] v, String extra) {
        String name = "aerie-" + t.label + "-" + index + "-" + view + ".png";
        notes.add(name + " camera feet=" + String.format("%.1f,%.1f,%.1f yaw=%.0f pitch=%.0f", v[0], v[1], v[2], v[3], v[4])
                + " anchor=" + t.anchor.toShortString() + " terrainComplete=" + c.worldRenderer.isTerrainRenderComplete() + extra);
        ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, ignored -> {});
    }

    private void onServer(MinecraftServer server, Runnable action) {
        server.execute(() -> {
            try { action.run(); }
            catch (Throwable e) { serverFailure = e.toString(); e.printStackTrace(); }
        });
    }

    private static void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setPitch(pitch); c.player.setHeadYaw(yaw); }
}
