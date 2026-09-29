package com.anton.elementalwands.client;

import com.anton.elementalwands.crypt.HollowCryptManager;
import com.anton.elementalwands.crypt.HollowCryptRealm;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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

/** Native Hollow Crypt harness: build a slot, enter, summon, contain, reset, leave, then locate the graveyard and use its headstone. */
public final class CryptClientSmoke implements ClientModInitializer {
    private boolean started, done;
    private int ticks, scene, stage;
    private volatile String serverFailure;
    private volatile boolean serverStep;
    private Vec3d home;
    private volatile BlockPos headstone, graveyard;
    private volatile Vec3d rewardView;
    private int risen, waited;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 7000) throw new AssertionError("Crypt client timeout at stage " + stage);
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                if (INTRO_VIDEO) GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                c.getNarratorManager().clear();
                c.createIntegratedServerLoader().createAndStart("crypt-" + System.currentTimeMillis(),
                        new LevelInfo("Crypt verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(4011L, true, false), WorldPresets::createDemoOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            if (serverFailure != null) throw new AssertionError(serverFailure);
            var server = c.getServer(); var uuid = c.player.getUuid();
            c.setScreen(null); WandWelcome.reset(); c.options.hudHidden = true;
            boolean inRealm = c.world.getRegistryKey() == HollowCryptRealm.WORLD;
            switch (stage) {
                case 0 -> { // enter: the slot is built section by section, then the player arrives
                    if (++scene < 20) return;
                    home = c.player.getEntityPos();
                    onServer(server, () -> {
                        var p = player(server, uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        run(server, p, "ew crypt enter");
                    });
                    next();
                }
                case 1 -> {
                    if (!inRealm) return;
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        require(HollowCryptRealm.inPlay(centre, p.getEntityPos(), 0), "Arrival is outside the clearing");
                        require(realm.getBlockState(centre).isOf(Blocks.SOUL_SOIL), "Summoning circle missing at the centre");
                        require(realm.getBlockState(centre.add(45, 5, 0)).isOf(Blocks.BARRIER), "Invisible wall missing at the rim");
                        require(realm.getBlockState(centre.add(0, 31, 0)).isOf(Blocks.BARRIER), "Invisible lid missing");
                        int wood = 0;
                        for (int x = 50; x < 120; x++) for (int y = 1; y < 40; y++)
                            if (realm.getBlockState(centre.add(x, y, 3)).isOf(Blocks.DARK_OAK_WOOD)) wood++;
                        require(wood > 20, "Forest missing beyond the rim (" + wood + " wood blocks on the sample line)");
                    });
                    if (scene < 45) look(c, 180, 0); else if (scene < 60) look(c, 200, -40); else look(c, 110, -8);
                    if (scene == 40) shot(c, "crypt-arrival.png");
                    if (scene == 55) shot(c, "crypt-look-up.png");
                    if (scene == 70) shot(c, "crypt-rim.png");
                    if (scene == 75) next();
                }
                case 2 -> { // summon: the intro cinematic plays, then the boss is kept passive for the camera
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        run(server, p, "ew crypt summon");
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        var bosses = realm.getEntitiesByClass(NecromancerEntity.class, p.getBoundingBox().expand(60), e -> e.isAlive());
                        require(bosses.size() == 1, "Expected one summoned necromancer, found " + bosses.size());
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        require(bosses.get(0).getBlockPos().isWithinDistance(centre.up(), 2), "Boss did not rise at the circle");
                        require(bosses.get(0).inIntro(), "Summon did not open with the intro");
                    });
                    if (scene < INTRO_END) { introShots(c, scene - 2); return; }
                    if (scene == INTRO_END) onServer(server, () -> {
                        var p = player(server, uuid);
                        var boss = crypt(server, p);
                        require(!boss.inIntro() && boss.isBossAggressive(), "The fight did not start after the intro: " + boss.status());
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        int lit = 0;
                        for (BlockPos q : BlockPos.iterate(centre.add(-44, 1, -44), centre.add(44, 4, 44)))
                            if (realm.getBlockState(q).isOf(Blocks.SOUL_CAMPFIRE) && realm.getBlockState(q).get(net.minecraft.block.CampfireBlock.LIT)) lit++;
                        require(lit >= 8, "The intro's ring lit only " + lit + " braziers");
                        boss.stopFight();
                        p.networkHandler.requestTeleport(centre.getX() + 6.5, HollowCryptRealm.SURFACE_Y + 1, centre.getZ() + 12.5, 150, 0);
                    });
                    int s = scene - INTRO_END + 1;
                    if (s > 5) lookAtBoss(c);
                    if (s == 40) shot(c, "crypt-boss.png");
                    // A siege from a shielded player: the caster takes a bough above the lid and raises wave 1.
                    if (s == 45) onServer(server, () -> {
                        var p = player(server, uuid);
                        p.getAbilities().invulnerable = true; p.sendAbilitiesUpdate();
                        var boss = crypt(server, p);
                        boss.startFight();
                        require(boss.requestSiege(), "Siege could not start: " + boss.status());
                    });
                    if (s == 90) onServer(server, () -> {
                        var p = player(server, uuid);
                        var boss = crypt(server, p);
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        require(boss.stage() == NecromancerRules.Stage.SIEGE_1 && boss.hasNoGravity()
                                && boss.getY() > HollowCryptRealm.SURFACE_Y + HollowCryptRealm.PLAY_CEILING + 1, "Caster is not on a bough perch: " + boss.status());
                        require(HollowCryptRealm.perches(centre).stream().anyMatch(spot -> spot.distanceTo(boss.getEntityPos()) < .1), "Caster is off the exported perches");
                        // Across the clearing from the perch, on the far rim.
                        Vec3d away = centre.toCenterPos().subtract(boss.getEntityPos()).multiply(1, 0, 1).normalize().multiply(HollowCryptRealm.PLAY_RADIUS - 3);
                        p.networkHandler.requestTeleport(centre.getX() + .5 + away.x, HollowCryptRealm.SURFACE_Y + 1, centre.getZ() + .5 + away.z, 0, 0);
                    });
                    if (s == 85) shot(c, "crypt-siege-perch-near.png");
                    if (s == 115) shot(c, "crypt-siege-perch-far-rim.png");
                    if (s == 120) onServer(server, () -> {
                        var p = player(server, uuid);
                        var boss = crypt(server, p);
                        boss.stopFight();
                        require(!boss.hasNoGravity() && boss.getY() < HollowCryptRealm.SURFACE_Y + 3, "Stopped caster stayed on its perch");
                        p.getAbilities().invulnerable = false; p.sendAbilitiesUpdate();
                    });
                    if (s == 125) next();
                }
                case 3 -> { // containment, teleport limits and block protection
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        var realm = (net.minecraft.server.world.ServerWorld) p.getEntityWorld();
                        require(!HollowCryptManager.canTeleport(p, realm, Vec3d.ofCenter(centre.add(70, 1, 0))), "Spell teleport allowed past the wall");
                        require(HollowCryptManager.canTeleport(p, realm, Vec3d.ofCenter(centre.add(10, 1, 0))), "Spell teleport refused inside the clearing");
                        require(!HollowCryptManager.canTeleport(p, server.getOverworld(), Vec3d.ZERO), "Spell teleport allowed out of the realm");
                        BlockPos ground = centre.add(3, 0, 20);
                        require(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(realm, p, ground, realm.getBlockState(ground), null),
                                "Survival player may break realm blocks");
                        p.networkHandler.requestTeleport(centre.getX() + 70.5, HollowCryptRealm.SURFACE_Y + 1, centre.getZ() + .5, 0, 0);
                    });
                    if (scene == 10) onServer(server, () -> {
                        var p = player(server, uuid);
                        require(HollowCryptRealm.inPlay(HollowCryptRealm.nearestCentre(p.getEntityPos()), p.getEntityPos(), 0),
                                "Player outside the wall was not pulled back: " + p.getEntityPos());
                    });
                    if (scene == 15) next();
                }
                case 4 -> { // reset clears the boss and restores the layout
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        var realm = (net.minecraft.server.world.ServerWorld) p.getEntityWorld();
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        realm.setBlockState(centre.add(2, 0, 2), Blocks.AIR.getDefaultState());
                        run(server, p, "ew crypt reset");
                        require(realm.getEntitiesByClass(NecromancerEntity.class, HollowCryptRealm.footprint(centre), e -> true).isEmpty(), "Reset left the boss behind");
                    });
                    if (scene == 60) onServer(server, () -> {
                        var p = player(server, uuid);
                        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
                        require(!p.getEntityWorld().getBlockState(centre.add(2, 0, 2)).isAir(), "Reset did not restore the damaged ground");
                        run(server, p, "ew crypt leave");
                    });
                    if (scene == 61) next();
                }
                case 5 -> {
                    if (inRealm || c.world.getRegistryKey() != World.OVERWORLD) return;
                    if (++scene < 10) return;
                    require(c.player.getEntityPos().distanceTo(home) < 2, "Leave did not return to the entry point: " + c.player.getEntityPos() + " vs " + home);
                    next();
                }
                case 6 -> { // the graveyard generates, /locate finds it, and the nearest natural one is used for the camera
                    if (++scene == 1) onServer(server, () -> {
                        var p = player(server, uuid);
                        try {
                            int found = server.getCommandManager().getDispatcher().execute("locate structure elementalwands:hollow_graveyard", p.getCommandSource());
                            require(found > 0, "/locate found no graveyard");
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError("/locate failed: " + e.getMessage()); }
                        server.getOverworld().setTimeOfDay(6000);
                        // Placement refuses uneven ground, so a fixed /place spot is not reliable; use the natural one.
                        graveyard = server.getOverworld().locateStructure(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,
                                net.minecraft.util.Identifier.of("elementalwands", "hollow_graveyard")), p.getBlockPos(), 100, false);
                        require(graveyard != null, "No natural graveyard near the player");
                        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++)
                            server.getOverworld().setChunkForced((graveyard.getX() >> 4) + cx, (graveyard.getZ() >> 4) + cz, true);
                    });
                    if (scene == 20) onServer(server, () -> {
                        var p = player(server, uuid); var world = server.getOverworld();
                        headstone = null;
                        // The yard sits within 32 blocks of its chunk centre, in any rotation, on the surface.
                        for (int x = graveyard.getX() - 64; x <= graveyard.getX() + 64 && headstone == null; x++)
                            for (int z = graveyard.getZ() - 64; z <= graveyard.getZ() + 64 && headstone == null; z++) {
                                int top = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                                for (int y = top - 1; y >= top - 6; y--)
                                    if (world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.SKELETON_SKULL)) { headstone = new BlockPos(x, y, z); break; }
                            }
                        require(headstone != null, "Natural graveyard has no headstone skull");
                        // Hover for the camera: the graveyard may generate in any rotation.
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(headstone.getX() + 18.5, headstone.getY() + 10, headstone.getZ() + 18.5, 0, 0);
                    });
                    if (scene > 23 && headstone != null) lookAt(c, Vec3d.ofCenter(headstone).add(0, -4, 0));
                    if (scene == 60) shot(c, "crypt-graveyard.png");
                    if (scene == 62) onServer(server, () -> player(server, uuid).networkHandler.requestTeleport(headstone.getX() - 7.5, headstone.getY() + 1, headstone.getZ() - 7.5, 0, 0));
                    if (scene == 75) shot(c, "crypt-headstone.png");
                    if (scene == 80) next();
                }
                case 7 -> { // a wipe: the headstone takes the party in, the boss rises, the party falls
                    if (scene == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        p.getAbilities().allowFlying = false; p.getAbilities().flying = false; p.sendAbilitiesUpdate();
                        p.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 7));
                        p.experienceLevel = 5;
                        useHeadstone(server, p);
                    });
                    scene++;
                    if (!inRealm && risen <= 100) return; // after the death, keep counting wherever the player is
                    if (++risen == 100) onServer(server, () -> {
                        var p = player(server, uuid);
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        require(realm.getEntitiesByClass(NecromancerEntity.class, p.getBoundingBox().expand(60), e -> e.isAlive()).size() == 1, "Ritual raised no boss");
                        p.kill(realm);
                    });
                    if (risen > 100 && c.player.isDead()) c.player.requestRespawn();
                    if (risen > 110 && !c.player.isDead()) next();
                }
                case 8 -> { // ...and comes back to the graveyard, as themselves, with everything they carried
                    if (inRealm || c.player.isDead()) { if (c.player.isDead()) c.player.requestRespawn(); require(++waited < 400, "Wipe did not send the player home"); return; }
                    // Checked soon after the return, so the next ritual lands before the wiped fight has finished.
                    if (++scene == 5) onServer(server, () -> {
                        var p = player(server, uuid);
                        require(p.getBlockPos().isWithinDistance(headstone, 24), "Wipe returned the player away from the graveyard: " + p.getBlockPos());
                        require(p.interactionManager.getGameMode() == GameMode.SURVIVAL, "Game mode not restored: " + p.interactionManager.getGameMode());
                        require(p.getInventory().count(net.minecraft.item.Items.DIAMOND) == 7, "Items were lost in the crypt");
                        require(p.experienceLevel == 5, "Experience was lost in the crypt: " + p.experienceLevel);
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        require(realm.getEntitiesByClass(NecromancerEntity.class, HollowCryptRealm.footprint(HollowCryptRealm.nearestCentre(Vec3d.ZERO)), e -> true).isEmpty(),
                                "The boss did not vanish after the wipe");
                    });
                    if (scene == 8) { require(serverStep, "Wipe return checks did not run"); next(); }
                }
                case 9 -> { // a victory: try again straight away, while the wiped fight is still ending, and win
                    if (scene == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        int before = fights(HollowCryptManager.status());
                        useHeadstone(server, p);
                        int after = fights(HollowCryptManager.status());
                        // A held right-click repeats the use while the slot builds: it must not seal the party into a second fight.
                        useHeadstone(server, p);
                        String status = HollowCryptManager.status();
                        require(after == before + 1 && fights(status) == after, "A repeated headstone use started a second fight: " + status);
                    });
                    scene++;
                    if (!inRealm) {
                        require(risen == 0, "The wiped fight's end pulled the player out of their new fight: " + HollowCryptManager.status());
                        require(scene < 400, "Second ritual did not take the player in");
                        return;
                    }
                    if (++risen == 220) onServer(server, () -> {
                        var p = player(server, uuid);
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        var bosses = realm.getEntitiesByClass(NecromancerEntity.class, p.getBoundingBox().expand(60), e -> e.isAlive());
                        require(bosses.size() == 1, "Second ritual raised " + bosses.size() + " bosses");
                        var boss = bosses.get(0);
                        // The death event commits the victory; the corpse is removed at once.
                        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(boss, realm.getDamageSources().generic());
                        boss.discard();
                    });
                    if (risen > 220) next();
                }
                case 10 -> { // home again, with reward chests at the grave and a spell book to claim
                    if (inRealm) { require(++waited < 400, "Victory did not send the player home"); return; }
                    if (++scene == 20) onServer(server, () -> {
                        var p = player(server, uuid);
                        var world = server.getOverworld();
                        java.util.List<BlockPos> chests = new java.util.ArrayList<>();
                        for (BlockPos q : BlockPos.iterate(headstone.add(-8, -6, -8), headstone.add(8, 0, 8)))
                            if (world.getBlockEntity(q) instanceof net.minecraft.block.entity.ChestBlockEntity chest && !chest.isEmpty()) chests.add(q.toImmutable());
                        require(chests.size() == 2, "Expected two filled reward chests at the grave, found " + chests.size());
                        int bones = 0;
                        for (BlockPos q : chests) if (world.getBlockEntity(q) instanceof net.minecraft.block.entity.ChestBlockEntity chest)
                            for (int i = 0; i < chest.size(); i++) if (chest.getStack(i).isOf(net.minecraft.item.Items.BONE) || chest.getStack(i).isOf(net.minecraft.item.Items.BONE_BLOCK)) bones++;
                        require(bones > 0, "Reward chests hold no bones");
                        var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(chests.get(0)), net.minecraft.util.math.Direction.UP, chests.get(0), false);
                        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, world, net.minecraft.util.Hand.MAIN_HAND, hit);
                        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, world, net.minecraft.util.Hand.MAIN_HAND, hit);
                        require(p.getInventory().count(com.anton.elementalwands.registry.ModItems.SECONDARY_SPELL_BOOK) == 1, "Victor did not receive exactly one spell book");
                        p.networkHandler.requestTeleport(chests.get(0).getX() + .5 + 4, chests.get(0).getY() + 3, chests.get(0).getZ() + .5 + 4, 0, 0);
                        rewardView = Vec3d.ofCenter(chests.get(0));
                    });
                    if (scene > 22 && rewardView != null) lookAt(c, rewardView);
                    if (scene == 40) shot(c, "crypt-rewards.png");
                    if (scene < 80) return; // let the screenshot finish writing
                    require(serverStep && rewardView != null, "Reward checks did not run");
                    Files.writeString(Path.of("CRYPT_PASSED.txt"), "Hollow Crypt native client passed: slot build, layout spot checks, arrival, summon at circle, siege on an exported bough perch and back, wall pull-back, "
                            + "spell teleport limits, survival block protection, reset restoration, return, /locate, graveyard placement, headstone ritual, "
                            + "wipe (boss vanishes, return to the graveyard with items, XP and game mode), second ritual before the wiped fight ends (it keeps the player; a repeated headstone use seals no second fight), victory return, "
                            + "reward chests with bones and loot, one spell book per victor. Screenshots: crypt-*.png. Human Lunar review pending.\n");
                    done = true; c.scheduleStop();
                }
                default -> {}
            }
        } catch (Throwable e) {
            try { Files.writeString(Path.of("CRYPT_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            e.printStackTrace();
            done = true; c.scheduleStop();
        }
    }

    private static int fights(String status) { return status.split(" fight: ", -1).length - 1; }

    private void next() { stage++; scene = 0; risen = 0; waited = 0; serverStep = false; }

    private void useHeadstone(MinecraftServer server, ServerPlayerEntity p) {
        BlockPos stone = headstone.down(); // chiseled deepslate under the skull
        var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(stone), net.minecraft.util.math.Direction.SOUTH, stone, false);
        var result = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, server.getOverworld(), net.minecraft.util.Hand.MAIN_HAND, hit);
        require(result == net.minecraft.util.ActionResult.SUCCESS, "Headstone did not respond: " + result);
    }

    private void onServer(MinecraftServer server, Runnable action) {
        server.execute(() -> {
            try { action.run(); serverStep = true; }
            catch (Throwable e) { serverFailure = e.toString(); }
        });
    }

    private static void run(MinecraftServer server, ServerPlayerEntity p, String command) {
        server.getCommandManager().parseAndExecute(p.getCommandSource(), command);
    }

    private static ServerPlayerEntity player(MinecraftServer server, UUID id) { return server.getPlayerManager().getPlayer(id); }
    private static NecromancerEntity crypt(MinecraftServer server, ServerPlayerEntity p) {
        var realm = server.getWorld(HollowCryptRealm.WORLD);
        var bosses = realm.getEntitiesByClass(NecromancerEntity.class, HollowCryptRealm.footprint(HollowCryptRealm.nearestCentre(p.getEntityPos())), e -> e.isAlive());
        require(bosses.size() == 1, "Expected one necromancer in the slot, found " + bosses.size());
        return bosses.get(0);
    }
    private static void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setPitch(pitch); }
    private static void lookAt(MinecraftClient c, Vec3d target) {
        var d = target.subtract(c.player.getEyePos());
        look(c, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalLength())));
    }
    private static void lookAtBoss(MinecraftClient c) {
        for (var e : c.world.getEntities()) if (e instanceof NecromancerEntity boss) {
            var d = boss.getEntityPos().add(0, 1.2, 0).subtract(c.player.getEyePos());
            look(c, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalLength())));
        }
    }
    /** Scene ticks since the summon: the intro's shots, with its letterbox, for review. */
    private static final int INTRO_END = com.anton.elementalwands.entity.necromancer.NecromancerIntro.LENGTH + 20;
    /** -PintroVideo also saves every tick of the intro, for a review video (ffmpeg -framerate 20). */
    private static final boolean INTRO_VIDEO = Boolean.getBoolean("crypt.introVideo");
    private static void introShots(MinecraftClient c, int t) {
        c.options.hudHidden = false;
        if (t == 20) require(com.anton.elementalwands.client.NecromancerIntroClient.cinematic(), "The intro did not take the camera");
        if (t == 30) require(com.anton.elementalwands.client.NecromancerIntroClient.arenaLight(0) == 0,
                "The crypt was not dark before ignition");
        if (t == com.anton.elementalwands.entity.necromancer.NecromancerIntro.RING_END + 4) require(com.anton.elementalwands.client.NecromancerIntroClient.arenaLight(0) == 1,
                "The fire wave did not restore the arena lighting");
        if (t == com.anton.elementalwands.entity.necromancer.NecromancerIntro.SLAM || t == com.anton.elementalwands.entity.necromancer.NecromancerIntro.RING_END - 4) {
            NecromancerEntity boss = null;
            for (var e : c.world.getEntities()) if (e instanceof NecromancerEntity n && n.inIntro()) { boss = n; break; }
            require(boss != null, "Intro boss missing at the staff slam");
            var renderer = (com.anton.elementalwands.client.renderer.NecromancerRenderer)c.getEntityRenderDispatcher().getRenderer(boss);
            var state = renderer.getAndUpdateRenderState(boss, 0);
            float yaw = state.getGeckolibData(software.bernie.geckolib.constant.DataTickets.ENTITY_BODY_YAW);
            require(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(yaw)) < 5,
                    "Rendered caster is not facing the players during the staff slam: " + yaw);
        }
        if (INTRO_VIDEO && t >= 0 && t <= com.anton.elementalwands.entity.necromancer.NecromancerIntro.LENGTH + 4)
            shot(c, String.format("intro-video-%03d.png", t));
        for (int[] at : new int[][]{{30, 1}, {84, 2}, {110, 3}, {130, 4}, {146, 5},
                {com.anton.elementalwands.entity.necromancer.NecromancerIntro.EYES + 4, 6},
                {com.anton.elementalwands.entity.necromancer.NecromancerIntro.TURN + 4, 7},
                {com.anton.elementalwands.entity.necromancer.NecromancerIntro.TITLE + 16, 8},
                {com.anton.elementalwands.entity.necromancer.NecromancerIntro.RETURN + 6, 9}})
            if (t == at[0]) shot(c, "crypt-intro-" + at[1] + ".png");
    }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
