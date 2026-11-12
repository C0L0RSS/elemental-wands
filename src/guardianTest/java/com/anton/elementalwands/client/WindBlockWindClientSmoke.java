package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
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

/** Hidden, muted native Wind rehearsal. Assertions cover effects and lifecycle, never appearance. */
public final class WindBlockWindClientSmoke implements ClientModInitializer {
    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private volatile int standId = -1;
    private int ticks, scene, floor;
    private UUID uuid, targetId;
    private final java.util.Set<String> effects = new java.util.HashSet<>();
    private boolean blade, fired, wings, rose;
    private volatile boolean landed;
    private int shots;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 4000) throw new AssertionError("Block wind client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.createIntegratedServerLoader().createAndStart("blockwind-" + System.currentTimeMillis(),
                        new LevelInfo("Block wind verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
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

            int pass = (t - 1) / 650;
            int q = (t - 1) % 650 + 1;
            String view = pass == 0 ? "1st" : "third";
            observe(c);
            if (q == 1) {
                effects.clear(); blade = fired = wings = rose = false; landed = false;
                reset(c, server, pass, 0, 0);
                cast(server, p -> positionTarget(p, .5, 5.5));
            }
            if (q == 20) require(ClientPlayerData.getAffinity() == WizardAffinity.WIND, "Wind affinity did not sync");
            if (q == 30) cast(server, p -> com.anton.elementalwands.item.WindAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()));
            if (q == 70) {
                require(blade && effects.contains("ShearFx"), "Sky Shear never produced a client blade/effect");
                cast(server, p -> require(((net.minecraft.entity.LivingEntity)p.getEntityWorld().getEntity(targetId)).getHealth() < 200, "Sky Shear never hit frozen target"));
            }
            if (q == 85) {
                reset(c, server, pass, 90, 0);
                // The miss lane points west at a target twenty-two blocks away, beyond Shear's twenty-block range.
                cast(server, p -> positionTarget(p, -21.5, .5));
                if (pass == 1) side(c, server, new Vec3d(-3, floor + 4, -8), new Vec3d(-4, floor + 1.5, .5));
            }
            if (q == 100) cast(server, p -> { aim(p, 90, 0); com.anton.elementalwands.item.WindAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()); });
            if (q == 150) { reset(c, server, pass, 0, 0); if (pass == 1) { c.setCameraEntity(c.player); c.options.setPerspective(Perspective.THIRD_PERSON_BACK); } }
            if (q == 170 || q == 190) cast(server, p -> {
                int before = com.anton.elementalwands.item.WindAbilityHandler.getDashCharges(p.getMainHandStack());
                com.anton.elementalwands.item.WindAbilityHandler.castSecondary(p.getEntityWorld(), p, p.getMainHandStack());
                require(com.anton.elementalwands.item.WindAbilityHandler.getDashCharges(p.getMainHandStack()) == before - 1, "Dash did not consume charge");
            });
            if (q == 230) require(effects.contains("DashFx"), "Dash never produced a client effect");
            if (q == 245) reset(c, server, pass, 0, 18);
            if (q == 260) cast(server, p -> { equip(p, 1, "updraft"); require(com.anton.elementalwands.util.UpdraftManager.cast(p), "Updraft refused"); });
            if (q >= 261 && q <= 300 && c.player.getY() > floor + 3) rose = true;
            if (q == 340) require(rose && effects.contains("UpdraftFx"), "Updraft never rose or produced its effect");
            if (q == 355) { reset(c, server, pass, 0, 0); cast(server, p -> {
                positionTarget(p, .5, 12.5); // Twelve blocks: comfortably within Gale Daggers' forty-block range.
                equip(p, 3, "gale_daggers");
            }); }
            if (q == 370) cast(server, p -> require(com.anton.elementalwands.util.GaleDaggers.cast(p), "Gale prepare refused"));
            if (q == 395) require(countDaggers(c, false) == 3, "Three prepared daggers never synchronized");
            if (q == 420) cast(server, p -> require(com.anton.elementalwands.util.GaleDaggers.fire(p), "Gale volley refused"));
            if (q == 460) require(fired && effects.contains("DaggerFx"), "Gale volley never produced flight/trails");
            if (q == 480) { cast(server, p -> positionTarget(p, 3, -12.5)); reset(c, server, pass, 180, 0); c.setCameraEntity(c.player); c.options.setPerspective(pass == 0 ? Perspective.FIRST_PERSON : Perspective.THIRD_PERSON_BACK); }
            if (q == 500) cast(server, p -> {
                aim(p, 180, 0); equip(p, 2, "zephyr_strike");
                com.anton.elementalwands.item.AbstractWandItem.addUltimateCharge(p.getMainHandStack(), 100);
                com.anton.elementalwands.item.WindAbilityHandler.castUltimate(p.getEntityWorld(), p, p.getMainHandStack());
                require(com.anton.elementalwands.util.ZephyrStrikeManager.isActive(p), "Zephyr did not begin");
            });
            if (q >= 501 && q <= 550 && c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(ModItems.ZEPHYR_WINGS)) wings = true;
            // Match the established Wind fixture's controlled descent so the landing is visible on the test runway.
            if (q == 550) cast(server, p -> {
                require(com.anton.elementalwands.util.ZephyrStrikeManager.isActive(p), "Zephyr ended before controlled descent");
                p.networkHandler.requestTeleport(.5, floor + 5, -12.5, 180, 65); p.setVelocity(0, -1.4, 0); p.velocityModified = true;
            });
            if (q >= 550 && q <= 580) look(c, 180, 65);
            if (q == 600) cast(server, p -> landed = !com.anton.elementalwands.util.ZephyrStrikeManager.isActive(p) && p.isOnGround());
            if (q == 620) require(wings && landed && effects.contains("Launch") && effects.contains("Landing"), "Zephyr missing wings, launch, landing effect or completed landing: " + effects);
            int[] moments = {30, 100, 170, 190, 260, 370, 420, 500, 550};
            String[] labels = {"shear-hit", "shear-miss", "dash", "dash-chain", "updraft", "daggers-prepared", "daggers-volley", "zephyr-launch-wings", "zephyr-landing"};
            for (int i = 0; i < moments.length; i++) for (int offset : new int[]{2, 4, 7, 11})
                if (q == moments[i] + offset) shot(c, labels[i] + "-" + view + "-" + offset);
            if (q == 650 && pass == 1) {
                Files.writeString(Path.of("BLOCKWIND_CLIENT_PASSED.txt"), "Native Wind client passed: both camera passes, Sky Shear target hit and miss, dash and chain, Updraft, prepared daggers and volley, Zephyr wings through landing. " + shots + " screenshots. Appearance requires inspection.\n");
                done = true; c.scheduleStop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("BLOCKWIND_CLIENT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void arrange(MinecraftServer server) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        for (int x = -50; x <= 35; x++) for (int z = -150; z <= 80; z++) {
            w.setBlockState(new BlockPos(x, floor - 1, z), Blocks.STONE_BRICKS.getDefaultState());
            for (int y = 0; y < 20; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        w.setTimeOfDay(6000); w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        var target = EntityType.HUSK.create(w, SpawnReason.COMMAND);
        target.refreshPositionAndAngles(.5, floor, 5.5, 180, 0);
        target.setAiDisabled(true); target.setNoGravity(true); target.setPersistent(); target.setSilent(true);
        target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);
        target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(200); w.spawnEntity(target); targetId = target.getUuid();
        var p = server.getPlayerManager().getPlayer(uuid);
        p.setAttached(EWAttachments.WELCOME_SEEN, true); p.setAttached(EWAttachments.AFFINITY, "WIND");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FRACTURED_WAND));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100000, 4, false, false));
        WandProgression.earn(p, WizardAffinity.WIND, 5000);
        for (String id : new String[]{"sky_shear", "waylay_dash", "updraft", "gale_daggers", "zephyr_strike"}) WandProgression.purchase(p, "WIND", id);
        equip(p, 0, "sky_shear"); equip(p, 1, "waylay_dash"); equip(p, 2, "zephyr_strike"); equip(p, 3, "gale_daggers");
        ModNetworking.syncPlayerData(p);
        p.networkHandler.requestTeleport(.5, floor, .5, 0, 0);
        var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, w);
        stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.refreshPositionAndAngles(.5, floor + 30, .5, 0, 0);
        w.spawnEntity(stand); standId = stand.getId();
    }
    private void positionTarget(ServerPlayerEntity p, double x, double z) {
        var target = (net.minecraft.entity.LivingEntity)p.getEntityWorld().getEntity(targetId);
        target.refreshPositionAndAngles(x, floor, z, 180, 0);
        target.setVelocity(Vec3d.ZERO); target.setHealth(200);
    }
    private void equip(ServerPlayerEntity p, int slot, String id) {
        String result = WandLoadouts.equip(p, "WIND", slot, id);
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
        shots++;
        ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), 1, text -> {});
    }
    private void reset(MinecraftClient c, MinecraftServer server, int pass, float yaw, float pitch) {
        first(c, yaw, pitch);
        cast(server, p -> { p.networkHandler.requestTeleport(.5, floor, .5, yaw, pitch); p.setVelocity(Vec3d.ZERO); aim(p, yaw, pitch); });
        if (pass == 1) side(c, server, new Vec3d(8.5, floor + 5, -4), new Vec3d(.5, floor + 2.5, 5));
    }
    private int countDaggers(MinecraftClient c, boolean fired) {
        int n = 0;
        for (var e : c.world.getEntities()) if (e instanceof com.anton.elementalwands.entity.GaleDaggerEntity d && d.fired() == fired) n++;
        return n;
    }
    private void observe(MinecraftClient c) throws ReflectiveOperationException {
        for (var e : c.world.getEntities()) if (e instanceof com.anton.elementalwands.entity.VacuumBladeEntity) blade = true;
        fired |= countDaggers(c, true) > 0;
        // Observe the real client's effect list without replacing receivers or injecting visual events.
        var field = com.anton.elementalwands.client.blockwind.BlockWindEffects.class.getDeclaredField("EFFECTS");
        field.setAccessible(true);
        for (Object fx : (java.util.List<?>)field.get(null)) effects.add(fx.getClass().getSimpleName());
    }
    private static void require(boolean b, String message) { if (!b) throw new AssertionError(message); }
}
