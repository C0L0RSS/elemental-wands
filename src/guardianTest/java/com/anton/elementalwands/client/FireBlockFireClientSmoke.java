package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.FireAbilityHandler;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.FireBuildManager;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.WandLoadouts;
import com.anton.elementalwands.util.WandProgression;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
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
 * Native screenshots of the six Fire spells drawn as block fire, cast at frozen husks and a stone wall in a
 * disposable daylight world: first person, behind the caster and from a side camera. The window stays hidden
 * and the sound is muted. Proves the effects render in Minecraft; appearance still needs the user's review.
 */
public final class FireBlockFireClientSmoke implements ClientModInitializer {
    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private volatile int standId = -1;
    private int ticks, scene, floor, flew = -1, landed = -1;
    private UUID uuid;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 3000) throw new AssertionError("Block fire client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.createIntegratedServerLoader().createAndStart("blockfire-" + System.currentTimeMillis(),
                        new LevelInfo("Block fire verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(814L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (c.world == null || c.player == null || c.getServer() == null) return;
            var server = c.getServer(); uuid = c.player.getUuid();
            if (!arranged) {
                arranged = true;
                server.execute(() -> { try { arrange(server); ready = true; } catch (Throwable e) { serverFailure = e.toString(); } });
                return;
            }
            if (serverFailure != null) throw new AssertionError(serverFailure);
            if (!ready) return;
            int t = ++scene;
            c.setScreen(null); WandWelcome.reset();
            c.options.hudHidden = true;

            // ---- Inferno Wave: first person, then a side camera ------------------------------------------
            if (t == 20) first(c, 0, 0);
            if (t == 30) cast(server, p -> FireAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()));
            if (t == 32) shot(c, "inferno-1st-a");
            if (t == 34) shot(c, "inferno-1st-b");
            if (t == 37) shot(c, "inferno-1st-c");
            if (t == 41) shot(c, "inferno-1st-d");
            if (t == 60) side(c, server, new Vec3d(8.5, floor + 2.6, 4), new Vec3d(.5, floor + 1.4, 7));
            if (t == 70) cast(server, p -> FireAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()));
            if (t == 72) shot(c, "inferno-side-a");
            if (t == 75) shot(c, "inferno-side-b");
            if (t == 78) shot(c, "inferno-side-c");
            if (t == 83) shot(c, "inferno-side-d");

            // ---- Flamethrower: hold until it overheats, first person then side --------------------------
            if (t == 100) { first(c, 0, -2); cast(server, p -> equip(p, 0, "flamethrower")); }
            if (t >= 110 && t <= 200) cast(server, FireBuildManager::hold);
            if (t == 120) shot(c, "flamethrower-1st-a");
            if (t == 150) shot(c, "flamethrower-1st-b");
            if (t == 184) shot(c, "flamethrower-1st-sputter");
            if (t == 194) shot(c, "flamethrower-1st-overheat");
            if (t == 250) side(c, server, new Vec3d(6.5, floor + 2.4, 1), new Vec3d(.5, floor + 1.2, 4));
            if (t >= 262 && t <= 330) cast(server, FireBuildManager::hold);
            if (t == 280) shot(c, "flamethrower-side-a");
            if (t == 320) shot(c, "flamethrower-side-b");

            // ---- Dragon's Pyre along -x: first person, then the side camera while the carpet burns ---------
            if (t == 360) { first(c, 90, 2); }
            if (t == 370) cast(server, p -> FireAbilityHandler.castSecondary(p.getEntityWorld(), p, p.getMainHandStack()));
            if (t == 374) shot(c, "pyre-1st-a");
            if (t == 379) shot(c, "pyre-1st-b");
            if (t == 381) side(c, server, new Vec3d(-12, floor + 5, 13), new Vec3d(-18, floor + 1, 0));
            if (t == 386) shot(c, "pyre-side-a");
            if (t == 392) shot(c, "pyre-side-b");
            if (t == 410) shot(c, "pyre-side-carpet");
            if (t == 465) shot(c, "pyre-side-carpet-late");

            // ---- Fire Leap north, from behind the caster ---------------------------------------------------
            if (t == 480) { first(c, 180, 9); c.options.setPerspective(Perspective.THIRD_PERSON_BACK); cast(server, p -> equip(p, 1, "fire_hop")); }
            if (t >= 480 && t <= 520) look(c, 180, 9);
            if (t == 500) cast(server, p -> {
                aim(p, 180, 9);
                var to = com.anton.elementalwands.util.FireLeapRules.target(p);
                if (to == null || !com.anton.elementalwands.util.FireLeapManager.commit(p, to)) serverFailure = "Leap did not commit toward " + to;
            });
            if (t >= 506 && flew < 0 && FireLeapClient.active()) flew = t;
            if (flew > 0 && t == flew + 1) shot(c, "leap-back-takeoff");
            if (flew > 0 && t == flew + 8) shot(c, "leap-back-flight");
            if (flew > 0 && landed < 0 && !FireLeapClient.active()) landed = t;
            if (landed > 0 && t == landed + 2) shot(c, "leap-back-land-a");
            if (landed > 0 && t == landed + 6) shot(c, "leap-back-land-b");
            if (landed > 0 && t == landed + 12) { c.options.setPerspective(Perspective.FIRST_PERSON); shot(c, "leap-back-land-c"); }
            if (t == 600) require(flew > 0 && landed > 0, "Leap never flew or landed");

            // ---- Flashover: three coals, then detonate -----------------------------------------------------
            if (t == 620) { cast(server, p -> { p.networkHandler.requestTeleport(.5, floor, .5, 0, 0); equip(p, 1, "flashover"); }); first(c, 0, 12); }
            int[] throwsAt = {640, 662, 684};
            float[] yaws = {-14, 0, 14};
            for (int i = 0; i < 3; i++) {
                if (t >= throwsAt[i] - 4 && t <= throwsAt[i] + 2) look(c, yaws[i], 12);
                float yaw = yaws[i];
                if (t == throwsAt[i]) cast(server, p -> { aim(p, yaw, 12); if (!com.anton.elementalwands.util.FlashoverManager.toss(p)) serverFailure = "Flashover toss refused"; });
            }
            if (t == 690) look(c, 0, 12);
            if (t == 692) shot(c, "flashover-1st-flight");
            if (t == 715) shot(c, "flashover-1st-armed");
            if (t == 718) side(c, server, new Vec3d(9.5, floor + 3, 2), new Vec3d(0, floor + .5, 8));
            if (t == 726) shot(c, "flashover-side-armed");
            if (t == 728) cast(server, p -> { if (!com.anton.elementalwands.util.FlashoverManager.detonate(p)) serverFailure = "Flashover did not detonate"; });
            if (t == 732) shot(c, "flashover-side-pop-a");
            if (t == 736) shot(c, "flashover-side-pop-b");
            if (t == 745) shot(c, "flashover-side-pop-c");
            if (t == 760) shot(c, "flashover-side-smoke");

            // ---- Meteor east at 17 blocks: from the caster looking up, then far to the side -------------------
            if (t == 790) { first(c, -90, -40); cast(server, p -> p.networkHandler.requestTeleport(.5, floor, .5, -90, -40)); }
            if (t == 800) cast(server, p -> MeteorManager.spawnMeteor(p.getEntityWorld(), p, new Vec3d(17.5, floor, .5), 35, 5f));
            if (t == 815) shot(c, "meteor-1st-falling");
            if (t == 818) side(c, server, new Vec3d(2, floor + 6, -24), new Vec3d(17.5, floor + 9, .5));
            if (t == 826) shot(c, "meteor-side-falling");
            if (t == 838) shot(c, "meteor-side-near");
            if (t == 846) shot(c, "meteor-side-impact-a");
            if (t == 850) shot(c, "meteor-side-impact-b");
            if (t == 858) shot(c, "meteor-side-impact-c");
            if (t == 880) shot(c, "meteor-side-after");
            if (t == 900) {
                Files.writeString(Path.of("BLOCKFIRE_CLIENT_PASSED.txt"), "Block fire native client: all six Fire spells cast in an integrated world and captured "
                        + "from first person, behind and side cameras (screenshots in this run directory). Appearance needs human review.\n");
                done = true; c.scheduleStop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("BLOCKFIRE_CLIENT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void arrange(MinecraftServer server) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        for (int x = -46; x <= 30; x++) for (int z = -28; z <= 16; z++) {
            w.setBlockState(new BlockPos(x, floor - 2, z), Blocks.DIRT.getDefaultState());
            w.setBlockState(new BlockPos(x, floor - 1, z), Blocks.GRASS_BLOCK.getDefaultState());
            for (int y = 0; y < 12; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        // A stone wall for Inferno Wave to break on, 12 blocks south of the caster.
        for (int x = -3; x <= 3; x++) for (int y = 0; y < 3; y++) w.setBlockState(new BlockPos(x, floor + y, 12), Blocks.STONE_BRICKS.getDefaultState());
        // A one-block step and a dip on the Pyre runway (west).
        for (int x = -16; x <= -11; x++) for (int z = -3; z <= 3; z++) w.setBlockState(new BlockPos(x, floor, z), Blocks.GRASS_BLOCK.getDefaultState());
        for (int x = -26; x <= -23; x++) for (int z = -3; z <= 3; z++) w.setBlockState(new BlockPos(x, floor - 1, z), Blocks.AIR.getDefaultState());
        w.setTimeOfDay(6000); w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        w.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, server);
        husk(w, .5, 4.5); husk(w, .5, 8.5); husk(w, -30.5, .5); husk(w, 1.8, -9.4); husk(w, .5, -13.6); husk(w, 19.5, 2.5);
        var p = server.getPlayerManager().getPlayer(uuid);
        p.setAttached(EWAttachments.WELCOME_SEEN, true); p.setAttached(EWAttachments.AFFINITY, "FIRE");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FRACTURED_WAND));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100000, 4, false, false));
        WandProgression.earn(p, WizardAffinity.FIRE, 5000);
        for (String id : new String[]{"flamethrower", "fire_hop", "flashover"}) WandProgression.purchase(p, "FIRE", id);
        ModNetworking.syncPlayerData(p);
        p.networkHandler.requestTeleport(.5, floor, .5, 0, 0);
        var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, w);
        stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.refreshPositionAndAngles(.5, floor + 30, .5, 0, 0);
        w.spawnEntity(stand); standId = stand.getId();
    }
    private void husk(net.minecraft.server.world.ServerWorld w, double x, double z) {
        var husk = EntityType.HUSK.create(w, SpawnReason.COMMAND);
        husk.refreshPositionAndAngles(x, floor, z, 180, 0);
        husk.setAiDisabled(true); husk.setPersistent();
        husk.setHealth(husk.getMaxHealth());
        w.spawnEntity(husk);
    }
    private void equip(ServerPlayerEntity p, int slot, String id) {
        String result = WandLoadouts.equip(p, "FIRE", slot, id);
        ModNetworking.syncPlayerData(p);
        if (!WandLoadouts.get(p).contains(id)) serverFailure = "Could not equip " + id + ": " + result;
    }
    private void cast(MinecraftServer server, java.util.function.Consumer<ServerPlayerEntity> action) {
        server.execute(() -> { try { action.accept(server.getPlayerManager().getPlayer(uuid)); } catch (Throwable e) { serverFailure = e.toString(); } });
    }

    private void first(MinecraftClient c, float yaw, float pitch) {
        c.setCameraEntity(c.player); c.options.setPerspective(Perspective.FIRST_PERSON); look(c, yaw, pitch);
    }
    private static void aim(ServerPlayerEntity p, float yaw, float pitch) { p.setYaw(yaw); p.setHeadYaw(yaw); p.setPitch(pitch); }
    private void look(MinecraftClient c, float yaw, float pitch) { c.player.setYaw(yaw); c.player.setHeadYaw(yaw); c.player.setPitch(pitch); }
    /** Views from an invisible armor stand at eye, looking at target; the caster stays where they are. */
    private void side(MinecraftClient c, MinecraftServer server, Vec3d eye, Vec3d target) {
        Vec3d d = target.subtract(eye);
        float yaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90, pitch = (float) -(MathHelper.atan2(d.y, d.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
        server.execute(() -> {
            var stand = server.getOverworld().getEntityById(standId);
            if (stand != null) { stand.refreshPositionAndAngles(eye.x, eye.y - stand.getStandingEyeHeight(), eye.z, yaw, pitch); stand.setHeadYaw(yaw); }
        });
        c.options.setPerspective(Perspective.FIRST_PERSON);
        var stand = c.world.getEntityById(standId);
        if (stand != null) c.setCameraEntity(stand);
    }
    private void shot(MinecraftClient c, String name) {
        require(c.getCameraEntity() != null, "No camera for " + name);
        ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), 1, text -> {});
    }
    private static void require(boolean b, String message) { if (!b) throw new AssertionError(message); }
}
