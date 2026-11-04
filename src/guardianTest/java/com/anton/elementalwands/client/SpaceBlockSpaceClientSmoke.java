package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.entity.GravityBombEntity;
import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.item.SpaceAbilityHandler;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.AstralDoubleManager;
import com.anton.elementalwands.util.GravityWellManager;
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

/** Hidden, muted native Space rehearsal of the void-glass visuals. Assertions cover effects and lifecycle, never appearance. */
public final class SpaceBlockSpaceClientSmoke implements ClientModInitializer {
    private static final int PASS = 900;
    private boolean started, arranged, done;
    private volatile boolean ready;
    private volatile String serverFailure;
    private volatile int standId = -1;
    private int ticks, scene, floor;
    private UUID uuid, targetId;
    private final java.util.Set<String> effects = new java.util.HashSet<>();
    private boolean bolt, copy, bomb, well, burst, orb;
    private int shots;

    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient c) {
        if (done || c.getOverlay() != null) return;
        try {
            if (++ticks > 4500) throw new AssertionError("Block space client timeout");
            if (!started) {
                started = true; c.options.pauseOnLostFocus = false; c.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(), 1280, 720);
                GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.options.getFov().setValue(70);
                c.createIntegratedServerLoader().createAndStart("blockspace-" + System.currentTimeMillis(),
                        new LevelInfo("Block space verification", GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(815L, false, false), WorldPresets::createTestOptions, null);
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

            int pass = (t - 1) / PASS;
            int q = (t - 1) % PASS + 1;
            String view = pass == 0 ? "1st" : "third";
            observe(c);
            if (q == 1) {
                effects.clear(); bolt = copy = bomb = well = burst = orb = false;
                reset(c, server, pass, 0, 0);
                cast(server, p -> positionTarget(p, .5, 7.5));
            }
            if (q == 20) require(ClientPlayerData.getAffinity() == WizardAffinity.SPACE, "Space affinity did not sync");
            // Singularity Bolt: a hit on the frozen husk, then a miss that flies its full range.
            if (q == 30) cast(server, p -> SpaceAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()));
            if (q == 70) {
                require(bolt && effects.contains("BoltFx"), "Singularity Bolt never produced a client bolt/effect");
                cast(server, p -> require(((net.minecraft.entity.LivingEntity)p.getEntityWorld().getEntity(targetId)).getHealth() < 200, "Bolt never hit the target"));
            }
            if (q == 85) { reset(c, server, pass, 90, 0); if (pass == 1) side(c, server, new Vec3d(-8, floor + 5, -10), new Vec3d(-10, floor + 1.5, .5)); }
            if (q == 100) cast(server, p -> { aim(p, 90, 0); SpaceAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()); });
            // Blink Rift: ten blocks along the aim, with the husk moved out of the lane.
            if (q == 160) { cast(server, p -> positionTarget(p, 5.5, 4.5)); reset(c, server, pass, 0, 0); }
            if (q == 180) cast(server, p -> SpaceAbilityHandler.castSecondary(p.getEntityWorld(), p, p.getMainHandStack()));
            if (q == 200) require(effects.contains("BlinkFx"), "Blink never produced a client effect");
            // Astral Double: toss, the double forms, a bolt and its echo, then the swap.
            if (q == 240) { cast(server, p -> positionTarget(p, .5, 12.5)); reset(c, server, pass, 0, 0); }
            if (q == 260) cast(server, p -> require(AstralDoubleManager.cast(p), "Astral toss refused"));
            if (q == 320) require(copy && effects.contains("AstralFx$Copy"), "Astral Double never formed on the client");
            if (q == 330) cast(server, p -> { aim(p, 0, 0); SpaceAbilityHandler.castPrimary(p.getEntityWorld(), p, p.getMainHandStack()); });
            if (q == 380) cast(server, p -> require(AstralDoubleManager.cast(p), "Astral swap refused"));
            if (q == 420) require(!copy && effects.contains("AstralFx$Poof"), "Astral swap never consumed the double in a poof");
            // Gravity Well: throw, pull, recast to collapse early, burst.
            if (q == 440) { cast(server, p -> positionTarget(p, 1.5, 11.5)); reset(c, server, pass, 0, 0); }
            if (q == 460) cast(server, p -> require(GravityWellManager.cast(p), "Gravity Well throw refused"));
            if (q == 520) require(bomb && well && effects.contains("WellFx"), "Gravity Well never formed on the client");
            if (q == 540) cast(server, p -> require(GravityWellManager.cast(p), "Gravity Well collapse refused"));
            if (q == 580) require(burst, "Gravity Well never burst on the client");
            // Hollow Purple: the charge rises for 3.5 s, the orb flies 3.25 s and collapses.
            if (q == 600) { reset(c, server, pass, 180, 8); }
            if (q == 620) cast(server, p -> {
                aim(p, 180, 8);
                com.anton.elementalwands.item.AbstractWandItem.addUltimateCharge(p.getMainHandStack(), 100);
                SpaceAbilityHandler.castUltimate(p.getEntityWorld(), p, p.getMainHandStack());
            });
            if (q == 640) require(effects.contains("PurpleFx$Charge"), "Hollow Purple charge never drew");
            if (q >= 621 && q <= 700 && pass == 0) look(c, 180, 8);
            if (q == 760) require(orb && effects.contains("PurpleFx$Orb"), "Hollow Purple orb never drew");
            int[] moments = {30, 100, 180, 260, 285, 330, 380, 460, 500, 540, 625, 680, 700, 740, 770};
            String[] labels = {"bolt-hit", "bolt-miss", "blink", "astral-toss", "astral-formed", "astral-echo", "astral-swap",
                    "well-throw", "well-pull", "well-collapse", "purple-charge", "purple-hold", "purple-launch", "purple-orb", "purple-collapse"};
            for (int i = 0; i < moments.length; i++) for (int offset : new int[]{2, 5, 9})
                if (q == moments[i] + offset) shot(c, labels[i] + "-" + view + "-" + offset);
            if (q == PASS && pass == 1) {
                Files.writeString(Path.of("BLOCKSPACE_CLIENT_PASSED.txt"), "Native Space client passed: both camera passes, Singularity Bolt hit and miss, Blink Rift, Astral Double toss, form, echo and swap, Gravity Well pull and burst, Hollow Purple charge, orb and collapse. " + shots + " screenshots. Appearance requires inspection.\n");
                done = true; c.scheduleStop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.writeString(Path.of("BLOCKSPACE_CLIENT_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
            done = true; c.scheduleStop();
        }
    }

    private void arrange(MinecraftServer server) {
        var w = server.getOverworld(); floor = w.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
        for (int x = -40; x <= 35; x++) for (int z = -120; z <= 40; z++) {
            w.setBlockState(new BlockPos(x, floor - 1, z), Blocks.STONE_BRICKS.getDefaultState());
            for (int y = 0; y < 24; y++) w.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
        }
        w.setTimeOfDay(6000); w.setWeather(6000, 0, false, false);
        w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        var target = EntityType.HUSK.create(w, SpawnReason.COMMAND);
        target.refreshPositionAndAngles(.5, floor, 7.5, 180, 0);
        target.setAiDisabled(true); target.setNoGravity(true); target.setPersistent(); target.setSilent(true);
        target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);
        target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(200); w.spawnEntity(target); targetId = target.getUuid();
        var p = server.getPlayerManager().getPlayer(uuid);
        p.setAttached(EWAttachments.WELCOME_SEEN, true); p.setAttached(EWAttachments.AFFINITY, "SPACE");
        p.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FRACTURED_WAND));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100000, 4, false, false));
        WandProgression.earn(p, WizardAffinity.SPACE, 5000);
        for (String id : new String[]{"singularity_bolt", "blink_rift", "hollow_purple", "astral_double", "gravity_well"}) WandProgression.purchase(p, "SPACE", id);
        equip(p, 0, "singularity_bolt"); equip(p, 1, "blink_rift"); equip(p, 2, "hollow_purple"); equip(p, 3, "astral_double"); equip(p, 4, "gravity_well");
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
        String result = WandLoadouts.equip(p, "SPACE", slot, id);
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
    /** First person from the caster, or in the second pass a fixed side view that keeps both spell ends in frame. */
    private void reset(MinecraftClient c, MinecraftServer server, int pass, float yaw, float pitch) {
        first(c, yaw, pitch);
        cast(server, p -> { p.networkHandler.requestTeleport(.5, floor, .5, yaw, pitch); p.setVelocity(Vec3d.ZERO); aim(p, yaw, pitch); });
        if (pass == 1) {
            if (yaw == 180) side(c, server, new Vec3d(22, floor + 14, -18), new Vec3d(.5, floor + 9, -22));
            else side(c, server, new Vec3d(10.5, floor + 6, -5), new Vec3d(.5, floor + 2, 6));
        }
    }
    private void observe(MinecraftClient c) throws ReflectiveOperationException {
        boolean sawCopy = false;
        for (var e : c.world.getEntities()) {
            if (e instanceof SingularityBoltEntity) bolt = true;
            if (e instanceof AstralDoubleEntity) sawCopy = true;
            if (e instanceof GravityBombEntity b) { bomb = true; well |= b.isWell(); burst |= b.isBurst(); }
            if (e instanceof HollowPurpleOrbEntity) orb = true;
        }
        copy = sawCopy;
        // Observe the real client's effect list without replacing receivers or injecting visual events.
        var field = com.anton.elementalwands.client.blockspace.BlockSpaceEffects.class.getDeclaredField("EFFECTS");
        field.setAccessible(true);
        for (Object fx : (java.util.List<?>)field.get(null)) {
            String name = fx.getClass().getName();
            effects.add(name.substring(name.lastIndexOf('.') + 1));
        }
    }
    private static void require(boolean b, String message) { if (!b) throw new AssertionError(message); }
}
