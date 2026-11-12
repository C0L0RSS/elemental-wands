package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.hall.PyreHall;
import com.anton.elementalwands.hall.PyreHallManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/**
 * Native look at the Burned Throne Hall through its real commands: /ew hall enter, waits for the slot
 * to be laid out, /ew hall summon, checks the realm, the floor under the arrival and the king, the
 * barrier ring and lid, the lava sea and the king on his mark, photographs ten viewpoints, then the
 * sea again at render distances 8, 12 and 16 and under a server that sends only 8 (each once the
 * client has built what it draws), a brazier from the gallery, then /ew hall leave and checks the player is back where they started.
 * Then in again: a Survival player cannot so much as start mining the floor, and ten seconds after the Pyre King dies the
 * hall sends the player back where they entered. Hidden window, muted before the
 * world loads. Screenshots land in build/pyre-hall-client-smoke/screenshots/; they are not a Lunar look.
 */
public final class PyreHallClientSmoke implements ClientModInitializer {
    /** A camera: eye position and look target in layout coordinates (y relative to the floor block), or a fixed pitch. */
    private record View(String name, double ex, double ey, double ez, double tx, double ty, double tz, Float pitch) {
        View(String name, double ex, double ey, double ez, double tx, double ty, double tz) { this(name, ex, ey, ez, tx, ty, tz, null); }
    }

    private static final View[] VIEWS = {
            new View("arrival", .5, 2.62, 33.5, .5, 5, -40),
            new View("king-from-below", .5, 2.62, 8.5, .5, 5.6, .5),
            // The pillar at azimuth 60: (20 cos 60, 20 sin 60) = (10, 17.3).
            new View("pillar", 20.5, 2.62, 26.5, 10.5, 8, 17.8),
            new View("centre-to-throne", .5, 2.62, -3.5, .5, 7, -44),
            new View("throne-dais", .5, 9.62, -43, .5, 2, .5),
            new View("straight-up", 6.5, 2.62, 6.5, 6.5, 40, 6.5, -90f),
            new View("overhead", .5, 31.62, .5, .5, 0, .5, 90f),
            new View("causeway", .5, 5.62, 70.5, .5, 6, .5),
            // In the east breach (azimuth 22.5, (48, 20)), looking out over the lava sea into the haze.
            new View("breach-to-sea", 48.5, 9.62, 20.5, 110.5, -10, 46.5),
            // Out on the south causeway, looking east along the shore and the sea.
            new View("causeway-east-shore", .5, 5.62, 80.5, 100.5, -8, 66.5),
    };

    /** Last, from the gallery in the gateway, looking up at the brazier east of the gate for scale. */
    private static final View BRAZIER = new View("brazier", -2.5, 4.62, 46.5, 13.5, 11, 43.5);

    /** The breach-to-sea view again: {client render distance, server view distance (0 = the client's)}. */
    private static final int[][] SEA = {{8, 0}, {12, 0}, {16, 0}, {16, 8}};

    private boolean started, done;
    private int ticks, scene, stage, sea, ready;
    private volatile String pillarNote = "";
    private final java.util.List<String> seaNotes = new java.util.ArrayList<>();
    private volatile String serverFailure;
    private volatile boolean entered, checked, home, summoned, won;
    private volatile int wonAt;
    private volatile String victoryNote = "";
    private volatile String startDimension, lightNote = "";
    private volatile Vec3d start;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 6000) throw new AssertionError("Pyre Hall client timeout at stage " + stage);
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                // Muted before any world loads.
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.options.getViewDistance().setValue(16);
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.options.write();
                c.createIntegratedServerLoader().createAndStart("pyre-hall-" + System.currentTimeMillis(),
                        new LevelInfo("Pyre Hall verification", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(4013L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean inHall = c.world.getRegistryKey() == PyreHall.WORLD;
            switch (stage) {
                case 0 -> {
                    if (++scene < 20) return;
                    onServer(server, () -> {
                        var p = player(server, uuid);
                        p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        startDimension = p.getEntityWorld().getRegistryKey().getValue().toString();
                        start = p.getEntityPos();
                        run(server, p, "ew hall enter");
                    });
                    next();
                }
                case 1 -> { // the slot is laid out and the player taken in
                    if (++scene > 600) throw new AssertionError("Never entered the hall; is structure/pyre_hall installed? " + PyreHallManager.status());
                    if (!inHall) return;
                    if (!entered) {
                        onServer(server, () -> {
                            var p = player(server, uuid);
                            require(p.getEntityWorld().getRegistryKey() == PyreHall.WORLD, "The server has the player in " + p.getEntityWorld().getRegistryKey().getValue());
                            if (PyreHallManager.slotReady(0)) entered = true;
                        });
                        return;
                    }
                    onServer(server, () -> run(server, player(server, uuid), "ew hall summon"));
                    next();
                }
                case 2 -> { // the layout, the king, and the realm's numbers
                    if (++scene == 20) onServer(server, () -> { check(server, uuid); checked = true; });
                    if (scene > 20 && checked) {
                        require(c.world.getRegistryKey() == PyreHall.WORLD, "The client is not in elementalwands:pyre_hall");
                        boolean seen = false;
                        for (var e : c.world.getEntities()) if (e instanceof PyreKingEntity) seen = true;
                        require(seen, "The Pyre King never reached the client");
                        next();
                    }
                }
                case 3 -> { // a viewpoint every 60 ticks; the first holds longer while the hall's chunks build
                    int lead = 100, every = 60, t = scene++;
                    int view = t < lead + every ? 0 : 1 + (t - lead - every) / every;
                    int at = view == 0 ? t : (t - lead - every) % every, length = view == 0 ? lead + every : every;
                    if (view >= VIEWS.length) { next(); return; }
                    View v = VIEWS[view];
                    float[] look = look(v);
                    if (at == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(centre.getX() + v.ex, PyreHall.SURFACE_Y + v.ey - 1.62, centre.getZ() + v.ez, look[0], look[1]);
                    });
                    c.player.setYaw(look[0]); c.player.setPitch(look[1]);
                    if (at == length - 5) shot(c, "pyre-hall-" + (view + 1) + "-" + v.name + ".png");
                }
                case 4 -> { // the sea from the breach at other render distances, each after a normal load
                    if (sea >= SEA.length) { c.options.setServerViewDistance(0); c.options.getViewDistance().setValue(16); next(); return; }
                    int[] setting = SEA[sea]; View v = VIEWS[8]; float[] look = look(v);
                    int t = scene++;
                    if (t == 0) {
                        c.options.getViewDistance().setValue(setting[0]);
                        c.options.setServerViewDistance(setting[1]);   // a server that sends fewer chunks than the client draws
                        onServer(server, () -> {
                            var p = player(server, uuid);
                            BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                            p.networkHandler.requestTeleport(centre.getX() + v.ex, PyreHall.SURFACE_Y + v.ey - 1.62, centre.getZ() + v.ez, look[0], look[1]);
                        });
                        ready = -1;
                    }
                    c.player.setYaw(look[0]); c.player.setPitch(look[1]);
                    int expected = setting[1] > 0 ? Math.min(setting[0], setting[1]) : setting[0];
                    // At least five seconds, then until the client has every section it means to draw built.
                    if (ready < 0 && t >= 100 && c.options.getClampedViewDistance() == expected && c.worldRenderer.isTerrainRenderComplete()) ready = t;
                    if (ready < 0 && t >= 600) throw new AssertionError("Terrain never finished building for the sea at " + seaName(setting));
                    if (ready >= 0 && t == ready + 20) {
                        shot(c, "pyre-hall-" + (VIEWS.length + 1 + sea) + "-sea-" + seaName(setting) + ".png");
                        seaReach(c, v, seaName(setting));
                        sea++; scene = 0;
                    }
                }
                case 5 -> { // the brazier from the gallery
                    float[] look = look(BRAZIER);
                    if (scene == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                        p.networkHandler.requestTeleport(centre.getX() + BRAZIER.ex, PyreHall.SURFACE_Y + BRAZIER.ey - 1.62, centre.getZ() + BRAZIER.ez, look[0], look[1]);
                    });
                    c.player.setYaw(look[0]); c.player.setPitch(look[1]);
                    if (++scene == 55) { shot(c, "pyre-hall-" + (VIEWS.length + SEA.length + 1) + "-" + BRAZIER.name + ".png"); next(); }
                }
                case 6 -> { // nobody stays on a pillar: a Survival player set on the 24-block one slides off and lands unhurt
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                        p.changeGameMode(GameMode.SURVIVAL);
                        p.setHealth(p.getMaxHealth());
                        Vec3d pillar = PyreHall.nearestPillar(centre, new Vec3d(centre.getX() + .5 + 10, 0, centre.getZ() + .5 - 17.3));
                        p.networkHandler.requestTeleport(pillar.x, PyreHall.SURFACE_Y + 27, pillar.z, 0, 0);
                    });
                    if (scene == 140) onServer(server, () -> {
                        var p = player(server, uuid);
                        double up = p.getY() - (PyreHall.SURFACE_Y + 1);
                        require(p.getEntityWorld().getRegistryKey() == PyreHall.WORLD && up < .6 && p.isOnGround(), "A player set on a pillar should slide off to the floor; they are " + up + " above it");
                        require(p.getHealth() == p.getMaxHealth(), "Sliding off a pillar should not hurt; the player has " + p.getHealth() + " health");
                        p.changeGameMode(GameMode.CREATIVE);
                        pillarNote = "a Survival player set on the 24-block pillar slid off and landed unhurt; ";
                    });
                    if (scene == 150) { require(!pillarNote.isEmpty(), "The pillar check did not run"); next(); }
                }
                case 7 -> {
                    if (++scene == 1) onServer(server, () -> run(server, player(server, uuid), "ew hall leave"));
                    if (scene > 200) throw new AssertionError("/ew hall leave did not take the player out of the hall");
                    if (inHall || scene < 20) return;
                    if (scene == 20 || !home && scene % 10 == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        String dimension = p.getEntityWorld().getRegistryKey().getValue().toString();
                        require(dimension.equals(startDimension), "Left to " + dimension + ", entered from " + startDimension);
                        require(p.getEntityPos().distanceTo(start) < 1, "Left to " + p.getEntityPos() + ", entered from " + start);
                        home = true;
                    });
                    if (!home) return;
                    home = false;
                    next();
                }
                case 8 -> { // in again; the floor will not break; he dies, and ten seconds later the hall sends the player home
                    int t = ++scene;
                    if (t == 1) onServer(server, () -> run(server, player(server, uuid), "ew hall enter"));
                    if (t > 900) throw new AssertionError("The victory check never finished");
                    if (t < 20) return;
                    if (!won) {
                        if (!inHall) return;
                        if (!summoned) { summoned = true; onServer(server, () -> run(server, player(server, uuid), "ew hall summon")); return; }
                        won = true;
                        onServer(server, () -> {
                            var p = player(server, uuid);
                            ServerWorld hall = (ServerWorld) p.getEntityWorld();
                            BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
                            p.changeGameMode(GameMode.SURVIVAL);
                            BlockPos floor = PyreHall.cell(centre, 3, 0, 30);
                            var before = hall.getBlockState(floor);
                            require(!before.isAir(), "No floor block at layout 3,0,30 to try to break");
                            require(AttackBlockCallback.EVENT.invoker().interact(p, hall, net.minecraft.util.Hand.MAIN_HAND, floor, net.minecraft.util.math.Direction.UP)
                                    == net.minecraft.util.ActionResult.FAIL, "A Survival player could start mining the hall's floor");
                            p.interactionManager.tryBreakBlock(floor);
                            require(hall.getBlockState(floor).equals(before), "A Survival player broke the hall's floor: " + hall.getBlockState(floor));
                            var kings = hall.getEntitiesByClass(PyreKingEntity.class, PyreHall.footprint(centre), e -> e.isAlive());
                            require(kings.size() == 1, "Expected one Pyre King to defeat, found " + kings.size());
                            kings.get(0).kill(hall);
                            wonAt = server.getTicks();
                        });
                        return;
                    }
                    if (wonAt == 0) return;
                    if (inHall) {
                        if (server.getTicks() - wonAt > 260) throw new AssertionError("Still in the hall " + (server.getTicks() - wonAt) + " ticks after the Pyre King died");
                        return;
                    }
                    if (!home) onServer(server, () -> {
                        var p = player(server, uuid);
                        int after = server.getTicks() - wonAt;
                        require(after >= 195, "Sent home only " + after + " ticks after he died; the hall waits ten seconds");
                        String dimension = p.getEntityWorld().getRegistryKey().getValue().toString();
                        require(dimension.equals(startDimension) && p.getEntityPos().distanceTo(start) < 1,
                                "After the win, sent to " + dimension + " " + fmt(p.getEntityPos()) + ", entered from " + startDimension + " " + fmt(start));
                        victoryNote = "a Survival player could not start mining the floor; " + after / 20.0 + " s after the Pyre King died the hall sent the player back to where they entered";
                        home = true;
                    });
                    if (!home) return;
                    Files.writeString(Path.of("PYRE_HALL_CLIENT_PASSED.txt"), "Entered elementalwands:pyre_hall by /ew hall enter, slot laid out; "
                            + "solid floor under the arrival and the king, barrier ring and lid, lava sea at its contract height; "
                            + "/ew hall summon stood one Pyre King on his mark facing south; " + pillarNote + lightNote + (VIEWS.length + SEA.length + 1)
                            + " screenshots in screenshots/ (" + SEA.length + " of them the sea again once built: " + String.join("; ", seaNotes)
                            + "; the last a brazier from the gallery); /ew hall leave returned to " + startDimension + " " + fmt(start) + "; then in again, "
                            + victoryNote + ".\n");
                    done = true; c.scheduleStop();
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("PYRE_HALL_CLIENT_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true; c.scheduleStop();
        }
    }

    /** Server-side assertions on the laid-out slot and the summoned king. */
    private void check(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity p = player(server, uuid);
        require(p.getEntityWorld().getRegistryKey() == PyreHall.WORLD, "Not in elementalwands:pyre_hall");
        ServerWorld hall = (ServerWorld) p.getEntityWorld();
        BlockPos centre = PyreHall.nearestCentre(p.getEntityPos());
        Vec3d arrival = PyreHall.arrival(centre), kingAt = PyreHall.king(centre);
        solid(hall, BlockPos.ofFloored(arrival).down(), "under the arrival");
        solid(hall, BlockPos.ofFloored(kingAt).down(), "under the king");
        // The barrier ring just outside the floor, every 45 degrees, and the lid over it.
        for (int a = 0; a < 360; a += 45) {
            boolean wall = false;
            for (double r = PyreHall.FLOOR_RADIUS + .5; r <= PyreHall.FLOOR_RADIUS + 4 && !wall; r += .25) {
                int x = (int) Math.round(r * Math.cos(Math.toRadians(a))), z = (int) Math.round(r * Math.sin(Math.toRadians(a)));
                if (PyreHall.floorCell(x, z)) continue;
                // The ring may stand on a low kerb: blocked at foot height, barrier above it.
                BlockPos foot = PyreHall.cell(centre, x, 1, z);
                wall = !hall.getBlockState(foot).getCollisionShape(hall, foot).isEmpty()
                        && hall.getBlockState(PyreHall.cell(centre, x, 2, z)).isOf(Blocks.BARRIER);
            }
            require(wall, "No barrier just outside the floor at azimuth " + a);
        }
        for (int[] at : new int[][]{{0, 0}, {20, -10}, {-30, 15}})
            require(hall.getBlockState(PyreHall.cell(centre, at[0], PyreHall.PLAY_CEILING + 1, at[1])).isOf(Blocks.BARRIER),
                    "No barrier lid over the floor at " + at[0] + "," + at[1]);
        // The lava sea is the generator's, at the contract height, out in the open corners.
        for (int[] at : new int[][]{{-115, -115}, {115, -115}, {-115, 115}, {115, 115}}) {
            require(hall.getBlockState(PyreHall.cell(centre, at[0], PyreHall.LAVA_Y, at[1])).isOf(Blocks.LAVA),
                    "No lava at layout " + at[0] + "," + PyreHall.LAVA_Y + "," + at[1] + ": " + hall.getBlockState(PyreHall.cell(centre, at[0], PyreHall.LAVA_Y, at[1])));
            require(hall.getBlockState(PyreHall.cell(centre, at[0], PyreHall.LAVA_Y + 1, at[1])).isAir(),
                    "Lava sea top is not at layout y=" + PyreHall.LAVA_Y);
        }
        // No magma on the floor, the kerb or the gallery; the great braziers burn on their pillars.
        int fires = 0;
        for (int x = -49; x <= 49; x++)
            for (int z = -49; z <= 49; z++) {
                double r = Math.hypot(x, z);
                if (r > 48.5) continue;
                for (int y = 0; y <= 3; y++)
                    require(!hall.getBlockState(PyreHall.cell(centre, x, y, z)).isOf(Blocks.MAGMA_BLOCK), "Magma inside the wall at " + x + "," + y + "," + z);
                if (r > 41.5) for (int y = 15; y <= 20; y++) if (hall.getBlockState(PyreHall.cell(centre, x, y, z)).isOf(Blocks.FIRE)) fires++;
            }
        require(fires >= 10 * 25, "Only " + fires + " flames burn in the braziers' bowls");
        // How far their light reaches onto the floor: block light just above the rim, every 3 degrees.
        int rimMin = 15, rimMax = 0, rimSum = 0, n = 0;
        for (int a = 0; a < 360; a += 3) {
            int x = (int) Math.round(40 * Math.cos(Math.toRadians(a))), z = (int) Math.round(40 * Math.sin(Math.toRadians(a)));
            int l = hall.getLightLevel(net.minecraft.world.LightType.BLOCK, PyreHall.cell(centre, x, 1, z));
            rimMin = Math.min(rimMin, l); rimMax = Math.max(rimMax, l); rimSum += l; n++;
        }
        int r35 = 0, r30 = 0;
        for (int a = 0; a < 360; a += 3) {
            r35 = Math.max(r35, hall.getLightLevel(net.minecraft.world.LightType.BLOCK, PyreHall.cell(centre, (int) Math.round(35 * Math.cos(Math.toRadians(a))), 1, (int) Math.round(35 * Math.sin(Math.toRadians(a))))));
            r30 = Math.max(r30, hall.getLightLevel(net.minecraft.world.LightType.BLOCK, PyreHall.cell(centre, (int) Math.round(30 * Math.cos(Math.toRadians(a))), 1, (int) Math.round(30 * Math.sin(Math.toRadians(a))))));
        }
        int middle = hall.getLightLevel(net.minecraft.world.LightType.BLOCK, PyreHall.cell(centre, 0, 1, 0));
        require(rimMax >= 8, "The braziers' light reaches the floor's rim at only " + rimMax);
        lightNote = fires + " flames in the braziers; block light on the floor at r=40 " + rimMin + ".." + rimMax + " (mean "
                + String.format("%.1f", rimSum / (double) n) + "), best at r=35 " + r35 + ", r=30 " + r30 + ", centre " + middle + "; ";
        var kings = hall.getEntitiesByClass(PyreKingEntity.class, PyreHall.footprint(centre), e -> e.isAlive());
        require(kings.size() == 1, "Expected one Pyre King in the slot, found " + kings.size());
        PyreKingEntity king = kings.get(0);
        require(king.getEntityPos().distanceTo(kingAt) < .5, "The Pyre King stands at " + king.getEntityPos() + ", not " + kingAt);
        require(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(king.getYaw() - PyreHall.KING_YAW)) < 1, "The Pyre King faces " + king.getYaw() + ", not south");
    }

    private static void solid(World world, BlockPos pos, String where) {
        var state = world.getBlockState(pos);
        require(!state.isAir() && !state.isOf(Blocks.BARRIER) && !state.getCollisionShape(world, pos).isEmpty(),
                "The floor " + where + " (" + pos.toShortString() + ") is " + state);
    }

    private static float[] look(View v) {
        double dx = v.tx - v.ex, dy = v.ty - v.ey, dz = v.tz - v.ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = v.pitch != null ? v.pitch : (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        if (v.pitch != null) yaw = 180;
        return new float[]{yaw, pitch};
    }

    private void next() { stage++; scene = 0; }

    private static String seaName(int[] setting) { return "rd" + setting[0] + (setting[1] > 0 ? "-server" + setting[1] : ""); }

    /** How far the client has the sea loaded and built along the view, for the receipt. */
    private void seaReach(MinecraftClient c, View v, String name) {
        var cam = c.gameRenderer.getCamera().getPos();
        double dx = v.tx - v.ex, dz = v.tz - v.ez, n = Math.hypot(dx, dz);
        int built = 0;
        for (int d = 0; d <= 400; d += 2) {
            BlockPos pos = BlockPos.ofFloored(cam.x + dx / n * d, PyreHall.SURFACE_Y + PyreHall.LAVA_Y, cam.z + dz / n * d);
            if (!c.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) || !c.worldRenderer.isRenderingReady(pos)) break;
            built = d;
        }
        seaNotes.add(name + " drawn " + c.options.getClampedViewDistance() + " chunks, sea built " + built + " blocks out");
    }

    private void onServer(MinecraftServer server, Runnable action) {
        server.execute(() -> {
            try { action.run(); }
            catch (Throwable e) { serverFailure = e.toString(); }
        });
    }

    private static String fmt(Vec3d v) { return v == null ? "?" : String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z); }
    private static void run(MinecraftServer server, ServerPlayerEntity p, String command) {
        server.getCommandManager().parseAndExecute(p.getCommandSource(), command);
    }
    private static ServerPlayerEntity player(MinecraftServer server, UUID id) { return server.getPlayerManager().getPlayer(id); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
