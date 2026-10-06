package com.anton.elementalwands.client;

import com.anton.elementalwands.crypt.HollowCryptManager;
import com.anton.elementalwands.crypt.HollowCryptRealm;
import com.anton.elementalwands.crypt.MausoleumVeilBlock;
import com.anton.elementalwands.registry.ModBlocks;
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

/** Native Hollow Crypt harness: build a slot, enter, summon, contain, reset, leave, then locate the graveyard and walk into its mausoleum veil. */
public final class CryptClientSmoke implements ClientModInitializer {
    private boolean started, done;
    private int ticks, scene, stage;
    private volatile String serverFailure;
    private volatile boolean serverStep;
    private Vec3d home;
    private volatile BlockPos door, graveyard;
    private volatile net.minecraft.util.math.Direction doorFacing;
    private volatile Vec3d doorEye;
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
                if (INTRO_VIDEO || TRANSFORM_VIDEO) GLFW.glfwHideWindow(c.getWindow().getHandle());
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
                    if (TRANSFORM_VIDEO) { transformScene(c, server, uuid, s); return; }
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
                        door = null;
                        // The yard sits within 32 blocks of its chunk centre, in any rotation, on the surface.
                        // The door is the veil's bottom-centre cell; the mausoleum rises well above it.
                        for (int x = graveyard.getX() - 64; x <= graveyard.getX() + 64 && door == null; x++)
                            for (int z = graveyard.getZ() - 64; z <= graveyard.getZ() + 64 && door == null; z++) {
                                int top = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
                                for (int y = top - 1; y >= top - 30; y--) {
                                    var state = world.getBlockState(new BlockPos(x, y, z));
                                    if (state.isOf(ModBlocks.MAUSOLEUM_VEIL) && state.get(MausoleumVeilBlock.PIECE) == 1) { door = new BlockPos(x, y, z); break; }
                                }
                            }
                        require(door != null, "Natural graveyard has no mausoleum veil");
                        doorFacing = world.getBlockState(door).get(MausoleumVeilBlock.FACING);
                        doorEye = MausoleumVeilBlock.eye(door, doorFacing);
                        require(MausoleumVeilBlock.anchor(world.getBlockState(door.up(3).offset(doorFacing.rotateYClockwise())), door.up(3).offset(doorFacing.rotateYClockwise())).equals(door),
                                "Veil cells do not agree on their anchor");
                        // The woods around the yard are dead: no leaves anywhere near the door.
                        java.util.List<String> leaves = new java.util.ArrayList<>();
                        var yard = world.getStructureAccessor().getStructureContaining(door, s -> s.value() instanceof com.anton.elementalwands.world.HollowGraveyardStructure);
                        var footprint = yard.hasChildren() ? yard.getChildren().get(0).getBoundingBox() : null;
                        for (BlockPos q : BlockPos.iterate(door.add(-14, -6, -14), door.add(14, 40, 14)))
                            if (q.isWithinDistance(door.withY(q.getY()), 14) && world.getBlockState(q).isIn(net.minecraft.registry.tag.BlockTags.LEAVES))
                                leaves.add(q.toShortString() + " " + world.getBlockState(q).getBlock().getTranslationKey().replace("block.minecraft.", "")
                                        + (footprint == null ? "" : " dx=" + Math.max(Math.max(footprint.getMinX() - q.getX(), q.getX() - footprint.getMaxX()), 0)
                                        + " dz=" + Math.max(Math.max(footprint.getMinZ() - q.getZ(), q.getZ() - footprint.getMaxZ()), 0)));
                        require(leaves.isEmpty(), leaves.size() + " leaves survive within 14 blocks of the mausoleum door " + door.toShortString()
                                + " (footprint " + footprint + ", start box " + yard.getBoundingBox() + "): " + leaves.subList(0, Math.min(8, leaves.size())));
                        // Hover for the camera: the graveyard may generate in any rotation.
                        p.getAbilities().allowFlying = true; p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                        Vec3d view = doorEye.add(Vec3d.of(doorFacing.getVector()).multiply(18)).add(0, 9, 0);
                        p.networkHandler.requestTeleport(view.x, view.y, view.z, 0, 0);
                    });
                    if (scene > 23 && doorEye != null) lookAt(c, doorEye);
                    if (scene == 60) shot(c, "crypt-graveyard.png");
                    if (scene == 62) onServer(server, () -> {
                        Vec3d court = Vec3d.ofBottomCenter(door.offset(doorFacing, 6).down());
                        player(server, uuid).networkHandler.requestTeleport(court.x, court.y, court.z, 0, 0);
                    });
                    if (scene == 75) {
                        shot(c, "crypt-mausoleum.png");
                        // Aimed at the doorway from the court, the hint says how to start the fight.
                        c.options.hudHidden = false;
                        require(MausoleumHint.message(c) != null, "No hint while looking at the mausoleum door from " + c.player.getEntityPos());
                        c.options.hudHidden = true;
                    }
                    if (scene == 76) onServer(server, () -> {
                        // The mausoleum can't be mined, even in survival.
                        var p = player(server, uuid); var world = server.getOverworld();
                        p.getAbilities().allowFlying = false; p.getAbilities().flying = false; p.sendAbilitiesUpdate();
                        // Floor, veil and a carved frame piece: mining never progresses and explosions can't touch them.
                        for (BlockPos q : new BlockPos[]{door.down(), door, door.up(4).offset(doorFacing).offset(doorFacing.rotateYClockwise(), 2)}) {
                            var state = world.getBlockState(q);
                            require(!state.isAir(), "Nothing to test at " + q);
                            require(state.calcBlockBreakingDelta(p, world, q) == 0 && state.getBlock().getBlastResistance() >= 1200,
                                    "A survival player could break the mausoleum's " + state.getBlock() + " at " + q);
                        }
                    });
                    if (scene == 80) { require(serverStep, "Mausoleum checks did not run"); next(); }
                }
                case 7 -> { // a wipe: the veil takes the party in, the boss rises, the party falls
                    if (scene == 0) onServer(server, () -> {
                        var p = player(server, uuid);
                        p.getAbilities().allowFlying = false; p.getAbilities().flying = false; p.sendAbilitiesUpdate();
                        p.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 7));
                        p.experienceLevel = 5;
                        enterVeil(p);
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
                        require(p.getBlockPos().isWithinDistance(door, 24), "Wipe returned the player away from the graveyard: " + p.getBlockPos());
                        // Taken from the doorway, they come back to the court, not into the veil again.
                        require(!p.getBlockPos().isWithinDistance(door, 4), "Wipe returned the player into the doorway: " + p.getBlockPos());
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
                        require(standing(HollowCryptManager.status()) == 0, "A fight is still standing before the second ritual: " + HollowCryptManager.status());
                        enterVeil(player(server, uuid));
                    });
                    // Standing in the veil while the slot is laid out touches it every tick: that must seal one fight only.
                    if (scene == 15) onServer(server, () -> {
                        String status = HollowCryptManager.status();
                        require(standing(status) == 1, "Walking into the veil sealed " + standing(status) + " fights: " + status);
                    });
                    scene++;
                    if (!inRealm) {
                        require(risen == 0, "The wiped fight's end pulled the player out of their new fight: " + HollowCryptManager.status());
                        require(scene < 400, "Second ritual did not take the player in");
                        return;
                    }
                    // The boss can't die during its intro in a real fight, so the forced victory waits for it to end.
                    if (++risen == VICTORY_AT) onServer(server, () -> {
                        var p = player(server, uuid);
                        var realm = server.getWorld(HollowCryptRealm.WORLD);
                        var bosses = realm.getEntitiesByClass(NecromancerEntity.class, p.getBoundingBox().expand(60), e -> e.isAlive());
                        require(bosses.size() == 1, "Second ritual raised " + bosses.size() + " bosses");
                        var boss = bosses.get(0);
                        // The death event commits the victory; the corpse is removed at once.
                        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(boss, realm.getDamageSources().generic());
                        boss.discard();
                    });
                    if (risen > VICTORY_AT) next();
                }
                case 10 -> { // home again, with reward chests at the grave and a spell book to claim
                    if (inRealm) { require(++waited < 400, "Victory did not send the player home"); return; }
                    if (++scene == 20) onServer(server, () -> {
                        var p = player(server, uuid);
                        var world = server.getOverworld();
                        java.util.List<BlockPos> chests = new java.util.ArrayList<>();
                        for (BlockPos q : BlockPos.iterate(door.add(-8, -6, -8), door.add(8, 0, 8)))
                            if (world.getBlockEntity(q) instanceof net.minecraft.block.entity.ChestBlockEntity chest && !chest.isEmpty()) chests.add(q.toImmutable());
                        require(chests.size() == 2, "Expected two filled reward chests beside the mausoleum steps, found " + chests.size());
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
                            + "spell teleport limits, survival block protection, reset restoration, return, /locate, graveyard placement, withered woods, "
                            + "unbreakable mausoleum, door hint, walk-in veil ritual, "
                            + "wipe (boss vanishes, return to the court with items, XP and game mode), second ritual before the wiped fight ends (it keeps the player; standing in the veil seals no second fight), victory return, "
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

    /** Ticks in the realm before the second ritual's forced victory: after the rise delay and the intro. */
    private static final int VICTORY_AT = com.anton.elementalwands.entity.necromancer.NecromancerIntro.LENGTH + 80;

    private void next() { stage++; scene = 0; risen = 0; waited = 0; serverStep = false; }

    /** Fights with anyone still standing; a wiped fight lingers, with nobody, until its end. */
    private static int standing(String status) { return status.split(" fight: [1-9]", -1).length - 1; }

    /** Step into the veil's bottom-centre cell; the server sees the collision as the client moves. */
    private void enterVeil(ServerPlayerEntity p) {
        p.networkHandler.requestTeleport(door.getX() + .5, door.getY(), door.getZ() + .5, doorFacing.getOpposite().getPositiveHorizontalDegrees(), 0);
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
    /** -PtransformVideo plays the transformation in the crypt after the intro and saves every tick of it, for a review video. */
    private static final boolean TRANSFORM_VIDEO = Boolean.getBoolean("crypt.transformVideo");
    private int lastTransformFrame = -1, transformFrames, transformDone = -1;

    /** Lit soul campfires around the circle, the braziers the storm puts out and the howl relights. */
    private static int litBraziers(MinecraftServer server, ServerPlayerEntity p) {
        var realm = server.getWorld(HollowCryptRealm.WORLD);
        BlockPos centre = HollowCryptRealm.nearestCentre(p.getEntityPos());
        int lit = 0;
        for (BlockPos q : BlockPos.iterate(centre.add(-44, 1, -44), centre.add(44, 4, 44)))
            if (realm.getBlockState(q).isOf(Blocks.SOUL_CAMPFIRE) && realm.getBlockState(q).get(net.minecraft.block.CampfireBlock.LIT)) lit++;
        return lit;
    }

    /**
     * The transformation cinematic in the real crypt: it takes the camera, hides his hood in his
     * point of view, darkens the crypt and puts the braziers out, flies the storm of ghosts, and
     * hands back to the finished colossus with the braziers relit. Every tick is saved.
     */
    private void transformScene(MinecraftClient c, MinecraftServer server, UUID uuid, int s) {
        if (s == 10) onServer(server, () -> {
            var p = player(server, uuid);
            p.getAbilities().invulnerable = true; p.sendAbilitiesUpdate();
            crypt(server, p).requestTransform();
        });
        require(s < 1100, "The transformation never finished");
        NecromancerEntity boss = null;
        for (var e : c.world.getEntities()) if (e instanceof NecromancerEntity n) boss = n;
        if (boss == null) return;
        if (boss.isTransforming()) {
            c.options.hudHidden = false;
            int t = (int)boss.getTransformTime(0);
            if (t == 20) require(com.anton.elementalwands.client.NecromancerTransformClient.cinematic(), "The transformation did not take the camera");
            if (t == 60) require(com.anton.elementalwands.client.NecromancerTransformClient.hidesHood(boss.getId()), "His hood shows in his own point of view");
            if (t == 100) onServer(server, () -> require(litBraziers(server, player(server, uuid)) == 0, "The storm left braziers burning"));
            if (t == 160) {
                int souls = 0;
                for (var e : c.world.getEntities()) if (e instanceof com.anton.elementalwands.entity.necromancer.TransformSoulEntity soul && soul.soul() != null) souls++;
                require(souls >= 20, "Only " + souls + " ghosts in the storm");
            }
            if (t == 200) require(com.anton.elementalwands.client.NecromancerTransformClient.arenaLight(0) == 0, "The crypt did not darken with the braziers out");
            // One frame per client tick, as the scene plays; a clock resync may skip or repeat a scene tick.
            require(lastTransformFrame < 0 || t <= lastTransformFrame + 3, "The scene's clock jumped from " + lastTransformFrame + " to " + t);
            shot(c, String.format("transform-video-%03d.png", transformFrames++));
            lastTransformFrame = Math.max(lastTransformFrame, t);
            return;
        }
        if (lastTransformFrame < 0) return; // not begun yet
        if (transformDone < 0) {
            transformDone = s;
            require(boss.isColossus(), "The transformation did not end in the colossus");
            onServer(server, () -> {
                var p = player(server, uuid);
                require(!com.anton.elementalwands.entity.necromancer.NecromancerTransformScene.watching(p), "The scene still holds the player");
                require(litBraziers(server, p) >= 8, "The howl relit only " + litBraziers(server, p) + " braziers");
            });
        }
        if (s == transformDone + 5) {
            require(!com.anton.elementalwands.client.NecromancerTransformClient.cinematic(), "The camera was not handed back");
            for (var e : c.world.getEntities()) require(!(e instanceof com.anton.elementalwands.entity.necromancer.TransformSoulEntity), "A ghost outlived the scene");
            shot(c, "crypt-transform-handback.png");
        }
        if (s == transformDone + 20) {
            try {
                Files.writeString(Path.of("CRYPT_PASSED.txt"), "Crypt transformation recording passed: " + transformFrames + " consecutive frames (to tick " + lastTransformFrame
                        + "); the cinematic took the camera, hid his hood in his point of view, put the braziers out and darkened the crypt, flew the storm of ghosts"
                        + " and handed back to the finished colossus with the braziers relit and no ghost left. Visual review and human Lunar playtest pending.\n");
            } catch (java.io.IOException e) { throw new AssertionError(e.toString()); }
            done = true; c.scheduleStop();
        }
    }

    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
