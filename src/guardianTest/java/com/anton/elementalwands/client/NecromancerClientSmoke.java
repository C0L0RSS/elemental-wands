package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.registry.ModEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Native V2 anatomy, eight-second emergence, attack poses and optional framebuffer recording. */
public final class NecromancerClientSmoke implements ClientModInitializer {
    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks, scene, floor;
    private UUID bossId;
    private final boolean rushRecord = Boolean.getBoolean("necro.rushRecord");
    private UUID rushVictim;
    private net.minecraft.util.math.Vec3d rushFocus;
    private boolean sawRushGrip, sawHandGrip, sawHandModels;
    private final boolean soulRecord = Boolean.getBoolean("necro.soulRecord");
    private final boolean drainRecord = Boolean.getBoolean("necro.drainRecord");
    private final boolean mechanics = Boolean.getBoolean("necro.mechanics");
    private volatile UUID target;
    private volatile int warnAt = -1, eruptAt = -1;
    private volatile boolean sawFireball, sawSoul, sawHarvest, sawGlow;
    private volatile String serverFireballs = "not cast";
    private int mechanicsTick;
    private boolean sawDrain, sawDrainClear;
    private final boolean record = Boolean.getBoolean("necro.record") || soulRecord || rushRecord || drainRecord;
    private boolean sawSoulFlight, sawSoulBite;
    private int soulId = -1;
    private final java.util.concurrent.atomic.AtomicInteger recorded = new java.util.concurrent.atomic.AtomicInteger();
    private String recordingDirectory;
    private net.minecraft.util.math.Vec3d walkDestination, walkStart;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private boolean rushSkip;

    /** Every watcher skips the transformation cinematic; it ends once its skip window opens. */
    private static void skipTransform(net.minecraft.server.MinecraftServer server) {
        server.execute(() -> server.getPlayerManager().getPlayerList().forEach(com.anton.elementalwands.entity.necromancer.NecromancerTransformScene::skip));
    }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 1500) throw new AssertionError("Necromancer client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                c.options.getFov().setValue(60);
                if (record) {
                    GLFW.glfwHideWindow(c.getWindow().getHandle());
                    recordingDirectory = "necromancer-recording-" + System.currentTimeMillis();
                    Files.createDirectories(c.runDirectory.toPath().resolve("screenshots").resolve(recordingDirectory));
                    Files.writeString(Path.of("RECORDING_DIR.txt"), Path.of("screenshots", recordingDirectory).toAbsolutePath().toString());
                }
                c.createIntegratedServerLoader().createAndStart("necromancer-" + System.currentTimeMillis(),
                        new LevelInfo("Necromancer verification", GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            var server = c.getServer(); var uuid = c.player.getUuid();
            if (!arranged) {
                arranged = true;
                server.execute(() -> {
                    try {
                        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
                        for (int x = -12; x <= 12; x++) for (int z = -8; z <= 20; z++) {
                            w.setBlockState(new BlockPos(x, floor - 1, z), ((x + z) % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).getDefaultState());
                            for (int y = 0; y < 9; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
                        }
                        w.setTimeOfDay(6000); w.setWeather(6000, 0, false, false);
                        var p = server.getPlayerManager().getPlayer(uuid); p.setAttached(EWAttachments.WELCOME_SEEN, true);
                        var boss = new NecromancerEntity(ModEntities.HOLLOW_NECROMANCER, w);
                        boss.refreshPositionAndAngles(.5, floor, 4.5, 180, 0);
                        boss.setBodyYaw(180); boss.setHeadYaw(180);
                        w.spawnEntity(boss); boss.stopFight(); bossId = boss.getUuid();
                        p.networkHandler.requestTeleport(.5, floor, .5, 0, 0); ready = true;
                    } catch (Throwable e) { serverFailure = e.toString(); }
                });
                return;
            }
            if (serverFailure != null) throw new AssertionError(serverFailure);
            if (!ready) return;
            int t = ++scene;
            c.setScreen(null); WandWelcome.reset();
            c.options.hudHidden = true;
            if (drainRecord) { drainScene(c, t); return; }
            if (mechanics) { mechanicsScene(c, t); return; }
            if (soulRecord) { soulScene(c, t); return; }
            if (rushRecord) { rushScene(c, t); return; }
            if (t < 40) look(c, 0, -5);
            if (t == 35) {
                var boss = boss(c);
                require(boss != null, "Necromancer missing on client");
                require(c.getEntityRenderDispatcher().getRenderer(boss) instanceof com.anton.elementalwands.client.renderer.NecromancerRenderer, "Wrong necromancer renderer");
                shot(c, "necromancer-front.png");
            }
            if (t == 40) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(3.8, floor, 4.5, 90, 10));
            if (t > 42 && t < 60) look(c, 90, -5);
            if (t == 55) shot(c, "necromancer-side.png");
            if (t == 60) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5, floor, .5, 0, 10));
            if (t > 62) look(c, 0, -5);
            if (t == 65) cast(server, uuid, Action.BOLT);
            if (t == 72) shot(c, "necromancer-bolt-windup.png");
            if (t == 84) shot(c, "necromancer-bolt.png");
            if (t == 110) cast(server, uuid, Action.HANDS);
            if (t == 128) shot(c, "necromancer-hands-telegraph.png");
            if (t == 143) shot(c, "necromancer-hands.png");
            if (t == 160) cast(server, uuid, Action.DRAIN);
            if (t == 185) shot(c, "necromancer-drain.png");
            if (t == 250) server.execute(() -> {
                if (server.getOverworld().getEntity(bossId) instanceof NecromancerEntity boss) boss.testWave(server.getPlayerManager().getPlayer(uuid), 1);
                else serverFailure = "Boss missing for wave";
            });
            if (t == 262) shot(c, "necromancer-wave-streams.png");
            if (t == 284) shot(c, "necromancer-wave-rising.png");
            if (t == 305) {
                boolean undead = false;
                for (var e : c.world.getEntities())
                    undead |= c.getEntityRenderDispatcher().getRenderer(e) instanceof com.anton.elementalwands.client.renderer.HollowUndeadRenderer<?>;
                require(undead, "No Hollow undead minion rendered");
                shot(c, "necromancer-minions.png");
            }
            if (t == 320) cast(server, uuid, Action.BLINK);
            if (t == 325) shot(c, "necromancer-blink.png");
            if (t == 345) shot(c, "necromancer-curse.png");
            if (t == 350) c.options.getFov().setValue(45);
            // Phase two: the transformation from a wide front view, then the colossus and its attacks.
            if (t == 355) server.execute(() -> {
                var p = server.getPlayerManager().getPlayer(uuid);
                if (server.getOverworld().getEntity(bossId) instanceof NecromancerEntity boss) {
                    boss.stopFight();
                    boss.refreshPositionAndAngles(.5, floor, 6.5, 180, 0); boss.setBodyYaw(180); boss.setHeadYaw(180);
                    boss.requestTransform();
                }
                p.networkHandler.requestTeleport(-7.5, floor + 1, -3.5, -39, 0);
            });
            // The cinematic has its own recording (crypt_client_smoke -PtransformVideo): skip it here.
            if (t == 358 || t == 365 || t == 375) skipTransform(server);
            if (t == 362) require(com.anton.elementalwands.client.NecromancerTransformClient.cinematic(), "The transformation did not take the camera");
            if (t > 357 && t < 530) lookAtBoss(c);
            for (int frame = 0; frame < 12; frame++) if (t == 362 + frame * 13) shot(c, "necromancer-transform-" + frame + ".png");
            if (t == 520) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5, floor + 1, -5.5, 0, 0));
            if (t == 530) {
                var boss = boss(c);
                require(boss != null && boss.isColossus() && !boss.isTransforming(), "Client did not see the finished colossus");
                require(Math.abs(boss.getWidth() - com.anton.elementalwands.entity.necromancer.NecromancerRules.COLOSSUS_WIDTH) < .01, "Client hitbox did not grow");
                shot(c, "necromancer-colossus-front.png");
            }
            if (t == 532) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(10.5, floor + 1, 6.5, 90, 10));
            if (t > 534 && t < 550) look(c, 90, -5);
            if (t == 548) shot(c, "necromancer-colossus-side.png");
            if (t == 550) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5, floor + 1, -5.5, 0, 8));
            if (t > 552 && t < 652) look(c, 0, -3);
            if (t >= 652) lookAtBoss(c);
            if (t == 555) cast(server, uuid, Action.SWIPE);
            if (t == 569) shot(c, "necromancer-colossus-swipe-windup.png");
            if (t == 574) shot(c, "necromancer-colossus-swipe.png");
            if (t == 600) cast(server, uuid, Action.BOLT);
            if (t == 612) shot(c, "necromancer-colossus-bolt.png");
            if (t == 650) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(6.5, floor + 1, -7.5, -25, 12));
            if (t == 655) cast(server, uuid, Action.RUSH);
            // Move the camera aside only after the lunge locks its landing, then follow the body.
            if (t == 670) server.execute(() -> server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(-7.5, floor + 1, -1.5, -90, 0));
            if (t >= 672) lookAtBoss(c);
            if (t == 668) shot(c, "necromancer-colossus-lunge-mark.png");
            if (t == 684) shot(c, "necromancer-colossus-lunge-air.png");
            if (t == 710) server.execute(() -> {
                if (server.getOverworld().getEntity(bossId) instanceof NecromancerEntity boss) {
                    boss.stopFight();
                    walkDestination = boss.getEntityPos().add(4, 0, 0);
                }
            });
            if (t >= 714 && t < 770 && t % 4 == 2) server.execute(() -> {
                if (walkDestination != null && server.getOverworld().getEntity(bossId) instanceof NecromancerEntity boss)
                    boss.getMoveControl().moveTo(walkDestination.x, walkDestination.y, walkDestination.z, 1);
            });
            if (t == 714 && boss(c) != null) walkStart = boss(c).getEntityPos();
            if (t == 746) shot(c, "necromancer-colossus-crawl.png");
            if (t == 770) {
                require(walkStart != null && boss(c) != null && boss(c).getEntityPos().squaredDistanceTo(walkStart) > 1, "Colossus did not crawl in the native preview");
            }
            if (record && t >= 340 && t < 790)
                ScreenshotRecorder.saveScreenshot(c.runDirectory, recordingDirectory + "/frame-" + String.format(java.util.Locale.ROOT, "%04d", t - 340) + ".png", c.getFramebuffer(), 1, message -> recorded.incrementAndGet());
            if (t >= 790) {
                if (record && recorded.get() < 450) return;
                Files.writeString(Path.of("NECRO_PASSED.txt"), "Hollow Necromancer native client passed: V2 GeckoLib model and renderer, front/side views, bolt, hands, drain, siege wave, Hollow undead renderers, blink and curse screenshots, the transformation cinematic taking the camera (then skipped), synchronized colossus form and hitbox, swipe, bolt spit, outstretched lunge and moving crawl screenshots. Visual review and human Lunar playtest pending.\n");
                done = true; c.scheduleStop();
            }
        } catch (Throwable e) {
            done = true; e.printStackTrace();
            try { Files.writeString(Path.of("NECRO_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            c.scheduleStop();
        }
    }

    /** Side camera with an admitted scripted player; real combat owns every grab and hit. */
    private void rushScene(MinecraftClient c, int t) throws Exception {
        var server = c.getServer();
        if (t == 1) {
            c.options.getFov().setValue(70);
            server.execute(() -> {
                try {
                    var w = server.getOverworld();
                    var boss = (NecromancerEntity)w.getEntity(bossId);
                    boss.refreshPositionAndAngles(.5, floor, 2.5, 0, 0);
                    boss.setBodyYaw(0); boss.requestTransform();
                    rushSkip = true;
                    var f = com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player", net.minecraft.server.MinecraftServer.class,
                            UUID.class, String.class, double.class, double.class, double.class);
                    f.setAccessible(true);
                    var victim = (net.minecraft.server.network.ServerPlayerEntity)f.invoke(null, server, UUID.randomUUID(), "RushTarget", .5, (double)floor, 16.5);
                    victim.changeGameMode(GameMode.SURVIVAL);
                    victim.setNoGravity(true);
                    // Full iron, as in the playtest: the Hands + bite combo hurts badly but is survivable.
                    victim.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ARMOR).setBaseValue(15);
                    var observer = server.getPlayerManager().getPlayer(c.player.getUuid());
                    observer.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(victim)));
                    observer.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(victim.getId(), victim.getUuid(), victim.getX(), victim.getY(), victim.getZ(), victim.getPitch(), victim.getYaw(), victim.getType(), 0, victim.getVelocity(), victim.getHeadYaw()));
                    observer.changeGameMode(GameMode.SPECTATOR);
                    rushVictim = victim.getUuid(); victim.setLoaded(true); victim.onTeleportationDone(); victim.getHungerManager().setFoodLevel(10);
                    server.getPlayerManager().getPlayer(c.player.getUuid()).networkHandler.requestTeleport(-9, floor + 2, 15, -90, 5);
                } catch (Throwable e) { serverFailure = e.toString(); }
            });
        }
        if (rushSkip && (t == 4 || t == 10 || t == 20)) skipTransform(server);
        // Fake players have no input connection: integrate their released throw velocity in this fixture only.
        if (t > 165) server.execute(() -> {
            var victim = server.getPlayerManager().getPlayer(rushVictim);
            var boss = (NecromancerEntity)server.getOverworld().getEntity(bossId);
            if (victim != null && boss != null && boss.getGrabbed() != victim.getId() && victim.getVelocity().lengthSquared() > .01) {
                victim.move(net.minecraft.entity.MovementType.SELF, victim.getVelocity());
                victim.setVelocity(victim.getVelocity().multiply(.91, .98, .91).add(0, -.08, 0));
                if (victim.isOnGround()) victim.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
            }
        });
        if (t == 180 || t == 300) server.execute(() -> {
            var w = server.getOverworld(); var boss = (NecromancerEntity)w.getEntity(bossId);
            var victim = server.getPlayerManager().getPlayer(rushVictim);
            boss.stopFight(); boss.refreshPositionAndAngles(.5, floor, 2.5, 0, 0); boss.setBodyYaw(0);
            victim.setPosition(.5, floor, 16.5); victim.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
            victim.setHealth(20); victim.timeUntilRegen = 0; victim.clearStatusEffects();
            boss.testAction(victim, t == 180 ? Action.RUSH : Action.HANDS);
        });
        // Ease the fixed side camera toward the midpoint so the throw stays in frame.
        var focusBoss = boss(c);
        var focusVictim = rushVictim == null ? null : c.world.getPlayerByUuid(rushVictim);
        var wantedFocus = focusBoss != null && focusVictim != null
                ? focusBoss.getEntityPos().lerp(focusVictim.getEntityPos(), .5).add(0, 1.6, 0)
                : new net.minecraft.util.math.Vec3d(.5, floor + 1.8, 10);
        rushFocus = rushFocus == null ? wantedFocus : rushFocus.lerp(wantedFocus, .12);
        var delta = rushFocus.subtract(c.player.getEyePos());
        look(c, (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)), (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));
        var boss = boss(c);
        if (boss != null && boss.getGrabbed() >= 0) {
            if (t < 300) sawRushGrip = true; else sawHandGrip = true;
        }
        for (var e : c.world.getEntities()) if (e instanceof com.anton.elementalwands.entity.necromancer.GraspingHandEntity hand) {
            require(c.getEntityRenderDispatcher().getRenderer(hand) instanceof com.anton.elementalwands.client.renderer.GraspingHandRenderer, "Missing hands renderer");
            sawHandModels = true;
            if (t == 348) {
                var renderer = (com.anton.elementalwands.client.renderer.GraspingHandRenderer)c.getEntityRenderDispatcher().getRenderer(hand);
                var field = renderer.getClass().getDeclaredField("models"); field.setAccessible(true);
                var models = (com.anton.elementalwands.client.model.GraspingHandModel[])field.get(renderer);
                for (int variant = 0; variant < models.length; variant++) {
                    require(Math.abs(models[variant].getBone("root").orElseThrow().getRotX()) > .2,
                            "Hand variant " + variant + " did not lean into grip");
                    require(Math.abs(models[variant].getBone("finger_1_1").orElseThrow().getRotX()) > .5,
                            "Hand variant " + variant + " fingers not animated");
                }
            }
        }
        if (t >= 170 && t < 470) ScreenshotRecorder.saveScreenshot(c.runDirectory,
                recordingDirectory + "/frame-" + String.format(java.util.Locale.ROOT, "%04d", t - 170) + ".png", c.getFramebuffer(), 1, message -> recorded.incrementAndGet());
        if (t == 178) require(c.world.getPlayerByUuid(rushVictim) != null, "Scripted victim missing on client");
        if (t >= 470 && recorded.get() >= 300) {
            require(sawRushGrip && sawHandGrip && sawHandModels, "Missing rush/hands native state: " + sawRushGrip + "/" + sawHandGrip + "/" + sawHandModels);
            Files.writeString(Path.of("NECRO_PASSED.txt"), "Native rush/hands: colossus model, rush contact grip, hands models/renderer and catch-triggered grip; 300 frames at 20 fps. Scripted player throw integration; human Lunar playtest pending.\n");
            done = true; c.scheduleStop();
        }
    }

    /** Isolated native view of the real projectile, its looping jaw and wall impact. */
    /** Real tracked casts: observer braid, local mist, cover break, then boss removal. */
    private void drainScene(MinecraftClient c,int t) throws Exception {
        var server=c.getServer();var uuid=c.player.getUuid();
        c.options.hudHidden=false;
        if(t==1) {
            c.options.getFov().setValue(65);
            server.execute(() -> {
                try {
                    var w=server.getOverworld();
                    w.setTimeOfDay(18000);
                    var f=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",net.minecraft.server.MinecraftServer.class,
                            UUID.class,String.class,double.class,double.class,double.class);
                    f.setAccessible(true);
                    var victim=(net.minecraft.server.network.ServerPlayerEntity)f.invoke(null,server,UUID.randomUUID(),"DrainTarget",.5,(double)floor,-2.5);
                    rushVictim=victim.getUuid();victim.changeGameMode(GameMode.SURVIVAL);victim.setNoGravity(true);
                    var observer=server.getPlayerManager().getPlayer(uuid);
                    observer.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(victim)));
                    observer.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(victim.getId(),victim.getUuid(),victim.getX(),victim.getY(),victim.getZ(),0,0,victim.getType(),0,victim.getVelocity(),0));
                    victim.setLoaded(true);victim.onTeleportationDone();
                    observer.networkHandler.requestTeleport(-6,floor+1,1,-90,10);
                }catch(Throwable e){serverFailure=e.toString();}
            });
        }
        if(t<105)look(c,-90,10);else look(c,0,0);
        if(t==20)cast(server,rushVictim,Action.DRAIN);
        if(t==60) {
            require(boss(c).getDrainTarget()!=c.player.getId(),"Observer became drain target");
            require(NecromancerDrainEffects.localVeil(0)==null,"Observer received mist");
            shot(c,"life-drain-braid.png");
        }
        if(t==105)server.execute(() -> {
            var p=server.getPlayerManager().getPlayer(uuid);
            var standIn=server.getPlayerManager().getPlayer(rushVictim);
            // This actor was explicitly spawned for the observer shot. Remove that
            // client copy before putting the real first-person camera in its place.
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket(standIn.getId()));
            standIn.discard();
            p.networkHandler.requestTeleport(.5,floor,-2.5,0,0);
        });
        if(t==125)cast(server,uuid,Action.DRAIN);
        if(t==155) {
            var veil=NecromancerDrainEffects.localVeil(0);
            require(veil!=null && veil.time()>.7 && veil.fade()==0,"Local first-person drain mist missing");
            require(Math.abs(veil.time()*20-boss(c).getDrainTime(0))<2,"Mist clock differs from tracked cast");
            c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            require(NecromancerDrainEffects.localVeil(0)==null,"Mist visible in third person");
            c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            sawDrain=true;shot(c,"life-drain-player-eye.png");
        }
        if(t==175)server.execute(() -> {
            var w=server.getOverworld();
            for(int x=-2;x<=2;x++)for(int y=0;y<5;y++)w.setBlockState(new BlockPos(x,floor+y,1),Blocks.STONE_BRICKS.getDefaultState());
        });
        if(t==205) {
            require(boss(c).getDrainTarget()<0 && boss(c).getDrainStart()<0,"Cover did not clear server drain state");
            require(NecromancerDrainEffects.localVeil(0)==null,"Mist did not fade after cover break");
            sawDrainClear=true;shot(c,"life-drain-cover-clear.png");
        }
        if(t==215)server.execute(() -> {
            var w=server.getOverworld();
            for(int x=-2;x<=2;x++)for(int y=0;y<5;y++)w.setBlockState(new BlockPos(x,floor+y,1),Blocks.AIR.getDefaultState());
        });
        if(t==225)cast(server,uuid,Action.DRAIN);
        if(t==260)server.execute(() -> server.getOverworld().getEntity(bossId).discard());
        if(t==280)require(NecromancerDrainEffects.localVeil(0)==null,"Mist survived boss removal");
        if(t>=10 && t<310)ScreenshotRecorder.saveScreenshot(c.runDirectory,recordingDirectory+"/frame-"+String.format(java.util.Locale.ROOT,"%04d",t-10)+".png",
                c.getFramebuffer(),1,message -> recorded.incrementAndGet());
        if(t>=310 && recorded.get()>=300) {
            require(sawDrain && sawDrainClear,"Drain lifecycle checks incomplete");
            Files.writeString(Path.of("NECRO_PASSED.txt"),"Life Drain native client passed: tracked cast clock, observer exclusion, first-person mist, third-person exclusion, cover break/fade, boss removal; 300 frames at 20 fps. Human Lunar review pending.\n");
            done=true;c.scheduleStop();
        }
    }

    private void soulScene(MinecraftClient c, int t) throws Exception {
        look(c, 0, 0);
        if (t == 1) {
            c.options.getFov().setValue(70);
            c.getServer().execute(() -> {
                var w = c.getServer().getOverworld();
                if (w.getEntity(bossId) instanceof NecromancerEntity boss) {
                    boss.refreshPositionAndAngles(7, floor, 7, 180, 0);
                    for (int y = 0; y < 4; y++) for (int z = 2; z <= 5; z++)
                        w.setBlockState(new BlockPos(3, floor + y, z), Blocks.STONE_BRICKS.getDefaultState());
                    var bolt = new com.anton.elementalwands.entity.necromancer.SoulBoltEntity(ModEntities.SOUL_BOLT, w);
                    bolt.setOwner(boss);
                    bolt.setPosition(-1.7, floor + 1.6, 3.5);
                    bolt.setVelocity(.06, 0, 0);
                    w.spawnEntity(bolt);
                }
            });
        }
        for (var entity : c.world.getEntities()) {
            if (!(entity instanceof com.anton.elementalwands.entity.necromancer.SoulBoltEntity bolt)) continue;
            soulId = bolt.getId();
            require(c.getEntityRenderDispatcher().getRenderer(bolt) instanceof com.anton.elementalwands.client.renderer.SoulBoltRenderer,
                    "Soul Bolt still uses the item renderer");
            if (bolt.isBiting()) {
                sawSoulBite = true;
                require(bolt.getVelocity().lengthSquared() < 1e-8, "Impact skull kept moving");
            } else if (bolt.age > 15) sawSoulFlight = true;
        }
        if (t == 35) shot(c, "soul-bolt-flight.png");
        if (t >= 10 && t < 170)
            ScreenshotRecorder.saveScreenshot(c.runDirectory, recordingDirectory + "/frame-" + String.format(java.util.Locale.ROOT, "%04d", t - 10) + ".png",
                    c.getFramebuffer(), 1, message -> recorded.incrementAndGet());
        if (t >= 170) {
            if (recorded.get() < 160) return;
            require(sawSoulFlight && sawSoulBite, "Missing flight or synchronized bite state");
            require(c.world.getEntityById(soulId) == null, "Impact skull did not expire");
            Files.writeString(Path.of("NECRO_PASSED.txt"), "Soul Bolt native client passed: custom GeckoLib renderer, moving flight, synchronized stationary wall bite, expiry; 160 frames at 20 fps. Human Lunar review pending.\n");
            done = true; c.scheduleStop();
        }
    }

    /**
     * Second-playtest mechanics from a side camera: a Soul Fire Rain volley, the colossus charge
     * windup, a dodged Grave Dive, the soul split and a Soul Harvest, against a scripted player.
     */
    private void mechanicsScene(MinecraftClient c, int tick) throws Exception {
        var server = c.getServer();
        var observer = c.player.getUuid();
        // The scene's clock starts once the scripted player exists on the server.
        int t = target == null ? 0 : ++mechanicsTick;
        if (tick == 1) server.execute(() -> {
            try {
                var boss = (NecromancerEntity)server.getOverworld().getEntity(bossId);
                boss.refreshPositionAndAngles(.5, floor, 4.5, 0, 0); boss.setBodyYaw(0);
                var f = com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player", net.minecraft.server.MinecraftServer.class,
                        UUID.class, String.class, double.class, double.class, double.class);
                f.setAccessible(true);
                var victim = (net.minecraft.server.network.ServerPlayerEntity)f.invoke(null, server, UUID.randomUUID(), "Target", .5, (double)floor, 14.5);
                victim.changeGameMode(GameMode.SURVIVAL); victim.setNoGravity(true);
                var watcher = server.getPlayerManager().getPlayer(observer);
                watcher.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(java.util.List.of(victim)));
                watcher.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(victim.getId(), victim.getUuid(), victim.getX(), victim.getY(), victim.getZ(), victim.getPitch(), victim.getYaw(), victim.getType(), 0, victim.getVelocity(), victim.getHeadYaw()));
                watcher.changeGameMode(GameMode.SPECTATOR);
                victim.setLoaded(true); victim.onTeleportationDone(); victim.getHungerManager().setFoodLevel(10);
                target = victim.getUuid();
                watcher.networkHandler.requestTeleport(-11.5, floor + 3, 9.5, -90, 12);
            } catch (Throwable e) { serverFailure = e.toString(); }
        });
        if (t == 0) return;
        // The scripted player only has to stand in for a party: keep it alive and in place.
        server.execute(() -> {
            var victim = server.getPlayerManager().getPlayer(target);
            if (victim != null && victim.getHealth() < 12) { victim.setHealth(20); victim.extinguish(); }
        });
        NecromancerEntity boss = boss(c);
        // Soul Fire Rain: the robed caster lobs the model fireball, trailing flames, onto its marker.
        if (t == 5) server.execute(() -> {
            try {
                var w = server.getOverworld();
                int markers = ((NecromancerEntity)w.getEntity(bossId)).testRain(server.getPlayerManager().getPlayer(target));
                serverFireballs = markers + " markers, " + w.getEntitiesByClass(com.anton.elementalwands.entity.necromancer.SoulFireballEntity.class,
                        new net.minecraft.util.math.Box(-40, floor - 10, -40, 40, floor + 30, 40), e -> true).size() + " fireballs spawned";
            } catch (Throwable e) { serverFailure = e.toString(); }
        });
        if (t == 12) server.execute(() -> serverFireballs += ", " + server.getOverworld().getEntitiesByClass(com.anton.elementalwands.entity.necromancer.SoulFireballEntity.class,
                new net.minecraft.util.math.Box(-40, floor - 10, -40, 40, floor + 30, 40), e -> true).size() + " alive at tick 12");
        if (t > 5 && t < 50) lookAt(c, new net.minecraft.util.math.Vec3d(.5, floor + 1.5, 9.5));
        if (t >= 8 && t < 30) for (var e : c.world.getEntities())
            if (e instanceof com.anton.elementalwands.entity.necromancer.SoulFireballEntity) {
                require((Object)c.getEntityRenderDispatcher().getRenderer(e) instanceof com.anton.elementalwands.client.renderer.SoulFireballRenderer, "Fireball uses the wrong renderer");
                sawFireball = true;
            }
        if (t == 14) shot(c, "mechanics-rain-launch.png");
        if (t == 24) shot(c, "mechanics-rain-flight.png");
        if (t == 36) shot(c, "mechanics-rain-landing.png");
        if (t == 50) { require(sawFireball, "No Soul Fire Rain fireball reached the client (server: " + serverFireballs + ")"); server.execute(() -> ((NecromancerEntity)server.getOverworld().getEntity(bossId)).requestTransform()); }
        if (t > 50 && t < 230) lookAtBoss(c);
        // The charge windup: crouch, then the rocking coil.
        if (t == 230) cast(server, target, Action.RUSH);
        if (t > 230 && t < 290) lookAtBoss(c);
        if (t == 238) shot(c, "mechanics-rush-crouch.png");
        if (t == 248) shot(c, "mechanics-rush-coil-a.png");
        if (t == 256) shot(c, "mechanics-rush-coil-b.png");
        // Grave Dive: the target steps out as the ground cracks, so it ends stuck.
        if (t == 300) server.execute(() -> {
            var b = (NecromancerEntity)server.getOverworld().getEntity(bossId);
            b.stopFight(); b.refreshPositionAndAngles(.5, floor, 1.5, 0, 0); b.setBodyYaw(0);
            server.getPlayerManager().getPlayer(target).networkHandler.requestTeleport(.5, floor, 15.5, 180, 0);
        });
        if (t == 304) cast(server, target, Action.DIVE);
        if (t > 300 && t < 470) lookAt(c, boss == null ? new net.minecraft.util.math.Vec3d(.5, floor, 8) : boss.getEntityPos().add(0, 1.2, 0));
        if (t == 314) shot(c, "mechanics-dive-rear.png");
        if (t == 322) shot(c, "mechanics-dive-plunge.png");
        if (t == 345) shot(c, "mechanics-dive-tunnel.png");
        if (t > 304 && warnAt < 0) server.execute(() -> {
            var b = (NecromancerEntity)server.getOverworld().getEntity(bossId);
            if (warnAt < 0 && b.status().contains("dive warn")) {
                warnAt = scene;
                var victim = server.getPlayerManager().getPlayer(target);
                victim.networkHandler.requestTeleport(victim.getX() + 8, floor, victim.getZ(), 180, 0);
            }
        });
        if (warnAt > 0 && eruptAt < 0 && t > warnAt) server.execute(() -> {
            if (eruptAt < 0 && ((NecromancerEntity)server.getOverworld().getEntity(bossId)).status().contains("dive erupt")) eruptAt = scene;
        });
        if (warnAt > 0 && t == warnAt + 12) shot(c, "mechanics-dive-cracks.png");
        if (eruptAt > 0 && t == eruptAt + 3) shot(c, "mechanics-dive-erupt.png");
        if (eruptAt > 0 && t == eruptAt + 10) shot(c, "mechanics-dive-burst.png");
        if (eruptAt > 0 && t == eruptAt + 40) {
            require(boss != null && !boss.isBuried(), "Client still hides the colossus after the eruption");
            shot(c, "mechanics-dive-stuck.png");
        }
        if (t == 470) require(warnAt > 0 && eruptAt > 0, "The dive never cracked the ground or erupted: warn " + warnAt + ", erupt " + eruptAt);
        // The soul split: in fight mode, the soul tears out of the ribcage and hovers.
        if (t == 475) server.execute(() -> {
            var b = (NecromancerEntity)server.getOverworld().getEntity(bossId);
            b.stopFight(); b.refreshPositionAndAngles(.5, floor, 4.5, 0, 0); b.setBodyYaw(0);
            server.getPlayerManager().getPlayer(target).networkHandler.requestTeleport(.5, floor, 16.5, 180, 0);
            b.startFight(); b.requestSplit();
        });
        if (t > 475 && t < 560) lookAt(c, boss == null ? new net.minecraft.util.math.Vec3d(.5, floor + 2, 6) : boss.getEntityPos().add(0, 3, 2));
        if (t == 490) shot(c, "mechanics-split-rear.png");
        if (t == 500) shot(c, "mechanics-split-release.png");
        for (var e : c.world.getEntities())
            if (e instanceof com.anton.elementalwands.entity.necromancer.NecromancerSoulEntity) {
                require((Object)c.getEntityRenderDispatcher().getRenderer(e) instanceof com.anton.elementalwands.client.renderer.NecromancerSoulRenderer, "Soul uses the wrong renderer");
                sawSoul = true;
                if (t == 520 || t == 545) {
                    lookAt(c, e.getEntityPos());
                    shot(c, "mechanics-soul-" + t + ".png");
                }
            }
        if (t == 555) require(sawSoul && boss != null && boss.isSplit(), "The freed soul never reached the client");
        // Soul Harvest: souls claw out of the ground and drift toward the ribcage.
        if (t == 560) server.execute(() -> {
            var b = (NecromancerEntity)server.getOverworld().getEntity(bossId);
            b.stopFight(); b.refreshPositionAndAngles(.5, floor, 4.5, 0, 0);
        });
        // At night, like the dark crypt, so the glowing spots show as they will in the fight.
        if (t == 561) server.execute(() -> {
            server.getOverworld().setTimeOfDay(18000);
            server.getPlayerManager().getPlayer(observer).networkHandler.requestTeleport(-14.5, floor + 16, -10.5, -45, 40);
        });
        if (t == 563) cast(server, target, Action.HARVEST);
        if (t > 563 && t < 660) lookAt(c, new net.minecraft.util.math.Vec3d(.5, floor, 6));
        if (t >= 590) for (var e : c.world.getEntities())
            if (e instanceof com.anton.elementalwands.entity.necromancer.HarvestSoulEntity) {
                require((Object)c.getEntityRenderDispatcher().getRenderer(e) instanceof com.anton.elementalwands.client.renderer.HarvestSoulRenderer, "Soul uses the wrong renderer");
                sawHarvest = true;
            }
        if (t == 581) shot(c, "mechanics-harvest-scream.png");
        if (t == 592) shot(c, "mechanics-harvest-glow.png");
        if (t == 606) shot(c, "mechanics-harvest-rise.png");
        if (t == 645) shot(c, "mechanics-harvest-drift.png");
        if (t == 660) require(sawHarvest, "No harvested soul reached the client");
        // Soul light at night: a rain volley and a skull volley light the floor and pool blue on it.
        if (t == 662) server.execute(() -> {
            var b = (NecromancerEntity)server.getOverworld().getEntity(bossId);
            b.stopFight(); b.refreshPositionAndAngles(.5, floor, 3.5, 0, 0); b.setBodyYaw(0);
            server.getPlayerManager().getPlayer(target).networkHandler.requestTeleport(.5, floor, 13.5, 180, 0);
            server.getPlayerManager().getPlayer(observer).networkHandler.requestTeleport(-7.5, floor + 4.5, 6.5, -60, 30);
        });
        if (t == 666) server.execute(() -> ((NecromancerEntity)server.getOverworld().getEntity(bossId)).testRain(server.getPlayerManager().getPlayer(target)));
        if (t > 662 && t < 740) lookAt(c, new net.minecraft.util.math.Vec3d(.5, floor + .5, 11.5));
        if (t >= 668 && t < 696) for (var e : c.world.getEntities())
            if (e instanceof com.anton.elementalwands.entity.necromancer.SoulFireballEntity ball)
                for (BlockPos pos : BlockPos.iterate(ball.getBlockPos().add(-1, -1, -1), ball.getBlockPos().add(1, 1, 1)))
                    if (c.world.getBlockState(pos).isOf(com.anton.elementalwands.registry.ModSpellBlocks.SOUL_GLOW)) sawGlow = true;
        if (t == 676) shot(c, "mechanics-night-rain-flight.png");
        if (t == 686) shot(c, "mechanics-night-rain-closing.png");
        if (t == 694) shot(c, "mechanics-night-rain-marker.png");
        if (t == 698) shot(c, "mechanics-night-rain-impact.png");
        if (t == 706) cast(server, target, Action.BOLT);
        if (t == 726) shot(c, "mechanics-night-bolts.png");
        if (t == 732) shot(c, "mechanics-night-bolts-late.png");
        if (t == 745) {
            require(sawGlow, "No soul light reached the client around a fireball");
            Files.writeString(Path.of("NECRO_PASSED.txt"), "Hollow Necromancer second-playtest mechanics passed in the native client: model fireball rain, colossus charge crouch and coil, grave dive rear/plunge/tunnel/cracks/eruption/stuck, the soul split with the freed soul rendered, a soul harvest with rendered souls, and soul light (glow blocks and blue ground pools) under a night rain and skull volley; screenshots for visual review.\n");
            done = true; c.scheduleStop();
        }
    }

    private static void lookAt(MinecraftClient c, net.minecraft.util.math.Vec3d point) {
        var delta = point.subtract(c.player.getEyePos());
        look(c, (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)), (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));
    }

    private void cast(net.minecraft.server.MinecraftServer server, UUID player, Action action) {
        server.execute(() -> {
            var w = server.getOverworld();
            if (w.getEntity(bossId) instanceof NecromancerEntity boss) boss.testAction(server.getPlayerManager().getPlayer(player), action);
            else serverFailure = "Boss missing for " + action;
        });
    }
    private NecromancerEntity boss(MinecraftClient c) {
        for (var e : c.world.getEntities()) if (e instanceof NecromancerEntity boss) return boss;
        return null;
    }
    private static void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setPitch(pitch); }
    private void lookAtBoss(MinecraftClient c) {
        var boss = boss(c);
        if (boss == null) return;
        var delta = boss.getEntityPos().add(0, 1.7, 0).subtract(c.player.getEyePos());
        look(c, (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));
    }
    private static void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, name, c.getFramebuffer(), 1, t -> {}); }
    private static void require(boolean b, String why) { if (!b) throw new AssertionError(why); }
}
