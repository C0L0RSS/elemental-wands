package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.entity.pyreking.PyreKingIntro;
import com.anton.elementalwands.hall.PyreGate;
import com.anton.elementalwands.hall.PyreGateLayout;
import com.anton.elementalwands.hall.PyreGateVeilBlock;
import com.anton.elementalwands.hall.PyreHall;
import com.anton.elementalwands.hall.PyreSealBlock;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.world.StructureArrival;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
 * The Burned Gate in a fresh normal world, through the game itself: finds the nearest one in the Nether as /locate
 * does, checks that its bridge can be walked from the shore to the seal, and takes pictures of it. Then a Survival
 * player touches the seal twice, is taken to the hall, wins, and is sent back before the gate; touches it again,
 * dies in the hall, and wakes before the gate with their belongings, the king gone and the gate cold. Hidden
 * window, muted before any world loads.
 */
public final class PyreGateSmoke implements ClientModInitializer {
    private static final Identifier GATE = Identifier.of("elementalwands", "pyre_gate");
    /** Viewpoints in the gate's own frame: name, eye, target. */
    private static final Object[][] VIEWS = {
            {"from-the-shore", new double[]{3.5, 5, 53}, new double[]{0, 4, 0}},
            {"on-the-bridge", new double[]{0, 2.62, 24}, new double[]{0, 4.5, 0}},
            {"before-the-seal", new double[]{0, 2.62, 6.5}, new double[]{0, 3.6, 0}},
            {"from-the-lake", new double[]{24, 7, 14}, new double[]{0, 3, 8}},
            {"from-behind", new double[]{-9, 6, -16}, new double[]{0, 4, 4}},
    };

    private boolean started, done;
    private int ticks, scene, stage;
    private volatile String serverFailure, found = "", walk = "", wonNote = "", fellNote = "";
    private volatile BlockPos seal, stand;
    private volatile Direction facing;
    private volatile boolean located, checked, lit, burning, inHall, skipped, judged;
    private volatile int killedAt;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 9000) throw new AssertionError("Pyre Gate timeout at stage " + stage + ", scene " + scene);
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
                c.createIntegratedServerLoader().createAndStart("pyre-gate-" + System.currentTimeMillis(),
                        new LevelInfo("Pyre Gate verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(5012L, true, false), WorldPresets::createDemoOptions, null);
                return;
            }
            if (serverFailure != null) throw new AssertionError(serverFailure);
            // Respawn at once after the fall the last stage arranges.
            if (c.player != null && c.player.isDead() && stage == 6) { c.player.requestRespawn(); c.setScreen(null); return; }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean nether = c.world.getRegistryKey() == World.NETHER, hall = c.world.getRegistryKey() == PyreHall.WORLD;
            switch (stage) {
                case 0 -> { // find the nearest gate, as /locate does, and go and stand before it
                    if (++scene == 20) onServer(server, () -> locate(server, uuid));
                    if (scene > 2400) throw new AssertionError("Never reached a Burned Gate");
                    if (located && nether && scene > 60) next();
                }
                case 1 -> { // the gate as worldgen left it
                    if (++scene == 100) onServer(server, () -> { check(server, uuid); checked = true; });
                    if (scene > 100 && checked) next();
                }
                case 2 -> { // pictures
                    int every = 70, view = scene / every, at = scene % every;
                    scene++;
                    if (view >= VIEWS.length) { next(); return; }
                    double[] eye = (double[]) VIEWS[view][1], target = (double[]) VIEWS[view][2];
                    Vec3d from = frame(eye[0], eye[1], eye[2]), to = frame(target[0], target[1], target[2]);
                    float[] look = look(from, to);
                    if (at == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(from.x, from.y - 1.62, from.z, look[0], look[1]);
                    });
                    c.player.setYaw(look[0]); c.player.setPitch(look[1]);
                    if (at == every - 5) shot(c, "pyre-gate-" + (view + 1) + "-" + VIEWS[view][0] + ".png");
                }
                case 3 -> { // a Survival player touches the seal twice and is taken to the hall
                    int t = ++scene;
                    if (t == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        run(server, p, "gamerule doMobSpawning false");
                        run(server, p, "kill @e[type=!minecraft:player,distance=..160]");
                        survival(p);
                    });
                    if (t == 20) onServer(server, () -> {
                        var p = player(server, uuid);
                        touch(p);
                        ServerWorld world = (ServerWorld) p.getEntityWorld();
                        require(world.getBlockState(seal).get(PyreSealBlock.LIT), "The first touch should light the seal");
                        require(world.getBlockState(seal.offset(facing.getOpposite(), PyreGateLayout.VEIL_BEHIND)).get(PyreGateVeilBlock.LIT), "The first touch should blaze the fire in the arch");
                        require(!PyreGate.active(world, seal), "One touch should not open the gate");
                        lit = true;
                    });
                    if (t == 32) shot(c, "pyre-gate-6-the-seal-stirs.png");
                    if (t == 40) onServer(server, () -> {
                        var p = player(server, uuid);
                        touch(p);
                        require(PyreGate.active(p.getEntityWorld(), seal), "The second touch should open the gate");
                        burning = true;
                    });
                    if (t == 70) { require(lit && burning, "The seal was not touched"); shot(c, "pyre-gate-7-the-chains-burn.png"); }
                    if (t > 500) throw new AssertionError("The gate never took the player to the hall");
                    if (t > 70 && hall) next();
                }
                case 4 -> { // the intro holds them; skipped, the fight begins; he dies; ten seconds on they stand before the gate again
                    int t = ++scene;
                    if (t == 30) onServer(server, () -> {
                        var p = player(server, uuid);
                        require(p.getEntityWorld().getRegistryKey() == PyreHall.WORLD && PyreKingIntro.watching(p), "The player should be in the hall, held by his intro");
                        inHall = true;
                    });
                    if (t == 30 + PyreKingIntro.SKIP_AFTER + 20) onServer(server, () -> { PyreKingIntro.skip(player(server, uuid)); skipped = true; });
                    if (t == 30 + PyreKingIntro.SKIP_AFTER + 50) onServer(server, () -> {
                        var p = player(server, uuid);
                        ServerWorld realm = (ServerWorld) p.getEntityWorld();
                        var kings = realm.getEntitiesByClass(PyreKingEntity.class, PyreHall.footprint(PyreHall.nearestCentre(p.getEntityPos())), e -> e.isAlive());
                        require(inHall && skipped && kings.size() == 1 && !kings.get(0).inIntro(), "After the skip one Pyre King should be fighting; found " + kings.size());
                        kings.get(0).kill(realm);
                        killedAt = server.getTicks();
                    });
                    if (t > 700) throw new AssertionError("The hall never sent the victor home");
                    if (killedAt == 0 || !nether) return;
                    if (!judged) { judged = true; onServer(server, () -> {
                        var p = player(server, uuid);
                        int after = server.getTicks() - killedAt;
                        require(after >= 195, "Sent home only " + after + " ticks after he died");
                        require(p.getEntityPos().distanceTo(Vec3d.ofBottomCenter(stand)) < 1.5, "The victor should stand before the gate at " + stand.toShortString() + ", not at " + fmt(p.getEntityPos()));
                        wonNote = "after a win the player stood before the gate " + after / 20.0 + " s later";
                    }); }
                    if (!wonNote.isEmpty()) { judged = false; next(); }
                }
                case 5 -> { // the gate goes cold again, and can be touched again
                    int t = ++scene;
                    if (t == 80) onServer(server, () -> {
                        var p = player(server, uuid);
                        ServerWorld world = (ServerWorld) p.getEntityWorld();
                        require(!PyreGate.active(world, seal) && !world.getBlockState(seal).get(PyreSealBlock.LIT), "The gate should be cold again after the fight");
                        p.getInventory().insertStack(new ItemStack(Items.DIAMOND, 3));
                        touch(p);
                    });
                    if (t == 100) onServer(server, () -> touch(player(server, uuid)));
                    if (t > 600) throw new AssertionError("The gate never took the player to the hall a second time");
                    if (t > 100 && hall) next();
                }
                case 6 -> { // they fall in the hall, and wake before the gate with what they carried; he is gone
                    int t = ++scene;
                    if (t == 30 + PyreKingIntro.SKIP_AFTER + 20) onServer(server, () -> PyreKingIntro.skip(player(server, uuid)));
                    if (t == 30 + PyreKingIntro.SKIP_AFTER + 50) onServer(server, () -> {
                        var p = player(server, uuid);
                        require(p.getEntityWorld().getRegistryKey() == PyreHall.WORLD, "The player should be in the hall for the second fight");
                        p.kill((ServerWorld) p.getEntityWorld());
                        killedAt = -server.getTicks();
                    });
                    if (t > 900) throw new AssertionError("The fallen player never woke before the gate");
                    if (killedAt >= 0 || !nether || c.player.isDead()) return;
                    if (!judged) { judged = true; onServer(server, () -> {
                        var p = player(server, uuid);
                        require(p.getEntityPos().distanceTo(Vec3d.ofBottomCenter(stand)) < 1.5, "The fallen player should wake before the gate, not at " + fmt(p.getEntityPos()));
                        require(p.getInventory().count(Items.DIAMOND) == 3, "The fallen player should keep what they carried");
                        ServerWorld realm = server.getWorld(PyreHall.WORLD);
                        int kings = 0;
                        for (int slot = 0; slot < PyreHall.SLOTS; slot++)
                            kings += realm.getEntitiesByClass(PyreKingEntity.class, PyreHall.footprint(new BlockPos(slot * PyreHall.SLOT_SPACING, PyreHall.SURFACE_Y, 0)), e -> e.isAlive()).size();
                        require(kings == 0 && !PyreGate.active(p.getEntityWorld(), seal), "After a wipe the Pyre King should be gone and the gate free; " + kings + " king(s) remain");
                        fellNote = "a player who fell in the hall woke before the gate with their belongings, and the king was gone";
                    }); }
                    if (fellNote.isEmpty()) return;
                    Files.writeString(Path.of("PYRE_GATE_PASSED.txt"), "In a fresh normal world the nearest Burned Gate was " + found + ". " + walk
                            + " A Survival player touched the seal twice (it lit, and the fire in the arch blazed) and was taken to the hall, held by his intro; "
                            + wonNote + "; touching it again, " + fellNote + ". " + (VIEWS.length + 2) + " screenshots in screenshots/.\n");
                    done = true; c.scheduleStop();
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("PYRE_GATE_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true; c.scheduleStop();
        }
    }

    /** Finds the nearest gate from the Nether's origin and stands the player before it. */
    private void locate(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity p = player(server, uuid);
        p.setAttached(EWAttachments.WELCOME_SEEN, true);
        ServerWorld nether = server.getWorld(World.NETHER);
        long began = System.nanoTime();
        BlockPos at = nether.locateStructure(TagKey.of(RegistryKeys.STRUCTURE, GATE), new BlockPos(0, 64, 0), 100, false);
        require(at != null, "No Burned Gate within 100 chunks of the Nether's origin");
        var structure = nether.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).getEntry(GATE).orElseThrow();
        stand = StructureArrival.find(nether, structure, at);
        require(stand != null, "The located gate has no stand point");
        found = Math.round(Math.hypot(stand.getX(), stand.getZ())) + " blocks from the Nether's origin at " + stand.toShortString()
                + " (found in " + (System.nanoTime() - began) / 1_000_000 + " ms)";
        p.teleport(nether, stand.getX() + .5, stand.getY(), stand.getZ() + .5, Set.of(), 0, 0, false);
        located = true;
    }

    /** The gate as it generated: the seal and the fire in the arch, and a road that can be walked from the shore to the seal. */
    private void check(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity p = player(server, uuid);
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        require(world.getRegistryKey() == World.NETHER, "Not in the Nether");
        for (BlockPos pos : BlockPos.iterateOutwards(stand, 8, 4, 8))
            if (world.getBlockState(pos).isOf(ModBlocks.PYRE_SEAL)) { seal = pos.toImmutable(); break; }
        require(seal != null, "No seal near the stand point " + stand.toShortString());
        facing = world.getBlockState(seal).get(PyreSealBlock.FACING);
        require(cell(0, PyreGateLayout.STAND[1], PyreGateLayout.STAND[2]).equals(stand), "The stand point is not where the seal's facing puts it");
        require(world.getBlockState(seal.offset(facing.getOpposite(), PyreGateLayout.VEIL_BEHIND)).isOf(ModBlocks.PYRE_GATE_VEIL), "No fire in the arch behind the seal");
        int veils = 0, chains = 0;
        for (int x = -4; x <= 4; x++) for (int y = 1; y <= 11; y++) {
            if (world.getBlockState(cell(x, y, -PyreGateLayout.VEIL_BEHIND)).isOf(ModBlocks.PYRE_GATE_VEIL)) veils++;
            if (world.getBlockState(cell(x, y, 0)).isOf(Blocks.IRON_CHAIN)) chains++;
        }
        require(veils >= 50 && chains >= 15, "The arch should be filled with fire and bound with chains; " + veils + " fire, " + chains + " chain");
        // The road: a deck to stand on and room to walk, all the way from before the seal to the end of the apron.
        int end = PyreGateLayout.SIZE_Z - PyreGateLayout.ORIGIN_Z - 1;
        for (int z = 1; z <= end; z++) {
            BlockState deck = world.getBlockState(cell(0, 0, z));
            require(!deck.getCollisionShape(world, cell(0, 0, z)).isEmpty(), "The road has a hole at " + z + ": " + deck);
            for (int y = 1; y <= 2; y++)
                require(world.getBlockState(cell(0, y, z)).getCollisionShape(world, cell(0, y, z)).isEmpty(), "The road is blocked at " + z + ", " + y + ": " + world.getBlockState(cell(0, y, z)));
        }
        // Past the apron there must be shore to come from: ground within a block or two of the deck, with room over it.
        int reach = 0;
        for (int z = end + 1; z <= end + 6; z++) for (int y = 2; y >= -2; y--) {
            BlockPos ground = cell(0, y, z);
            if (!world.getBlockState(ground).getCollisionShape(world, ground).isEmpty() && world.getBlockState(ground.up()).isAir() && world.getBlockState(ground.up(2)).isAir()) { reach++; break; }
        }
        require(reach >= 4, "The bridge's head should stand on a shore that can be walked; only " + reach + " of the 6 blocks past it are ground");
        // Under the bridge and the gate: the lake.
        int drop = 0;
        while (drop < 20 && !world.getBlockState(cell(4, -1 - drop, 22)).isOf(Blocks.LAVA)) drop++;
        require(drop < 20 && world.getBlockState(cell(0, -1 - drop, -8)).isOf(Blocks.LAVA), "The bridge should cross open lava, and the lake lie behind the gate");
        walk = "Its road can be walked from the shore to the seal (" + end + " blocks), its deck " + (drop + 1) + " blocks above the lava, the arch holding " + veils
                + " blocks of fire bound by " + chains + " links of chain.";
    }

    /** A real right-click on the seal. */
    private void touch(ServerPlayerEntity p) {
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        p.interactionManager.interactBlock(p, world, p.getMainHandStack(), Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(seal), facing, seal, false));
    }

    private void survival(ServerPlayerEntity p) {
        p.changeGameMode(GameMode.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        float yaw = (float) Math.toDegrees(Math.atan2(facing.getVector().getX(), -facing.getVector().getZ()));
        p.networkHandler.requestTeleport(stand.getX() + .5, stand.getY(), stand.getZ() + .5, yaw, 0);
    }

    /** A cell of the gate's frame in the world. */
    private BlockPos cell(int x, int y, int z) {
        return seal.offset(facing.rotateYCounterclockwise(), x - PyreGateLayout.SEAL[0]).up(y - PyreGateLayout.SEAL[1]).offset(facing, z - PyreGateLayout.SEAL[2]);
    }

    private Vec3d frame(double x, double y, double z) {
        var east = facing.rotateYCounterclockwise().getVector(); var out = facing.getVector();
        double dx = x - PyreGateLayout.SEAL[0], dz = z - PyreGateLayout.SEAL[2];
        return new Vec3d(seal.getX() + .5 + east.getX() * dx + out.getX() * dz, seal.getY() + y - PyreGateLayout.SEAL[1], seal.getZ() + .5 + east.getZ() * dx + out.getZ() * dz);
    }

    private static float[] look(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }

    private void next() { stage++; scene = 0; }

    private void onServer(MinecraftServer server, Runnable action) {
        server.execute(() -> {
            try { action.run(); }
            catch (Throwable e) { e.printStackTrace(); serverFailure = e.toString(); }
        });
    }

    private static String fmt(Vec3d v) { return v == null ? "?" : String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z); }
    private static void run(MinecraftServer server, ServerPlayerEntity p, String command) {
        server.getCommandManager().parseAndExecute(p.getCommandSource().withLevel(4).withSilent(), command);
    }
    private static ServerPlayerEntity player(MinecraftServer server, UUID id) { return server.getPlayerManager().getPlayer(id); }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
