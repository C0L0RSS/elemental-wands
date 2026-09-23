package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.VacuumBladeEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.WandLoadouts;
import com.anton.elementalwands.util.WandProgression;
import com.anton.elementalwands.util.ZephyrStrikeManager;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** A guided native wind-art inspection; the completed integrated world stays open. */
public final class WindVisualClientSmoke implements ClientModInitializer {
    private int ticks, scene, floor;
    private boolean started, arranged, done, sawBlade;
    private volatile boolean setupReady;
    private volatile boolean sawWings, sawLanding;
    private volatile float targetHealth = 200;
    private UUID targetId;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (done || client.getOverlay() != null || client.isPaused()) return;
        try {
            if (++ticks > 1500) throw new AssertionError("Wind visual scene timed out");
            if (!started) {
                started = true;
                client.options.pauseOnLostFocus = false;
                client.createIntegratedServerLoader().createAndStart("wind-visual-" + System.currentTimeMillis(),
                        new LevelInfo("Wind visual inspection", GameMode.SURVIVAL, false, Difficulty.NORMAL,
                                true, new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L, false, false), WorldPresets::createTestOptions, null);
                return;
            }
            if (client.world == null || client.player == null || client.getServer() == null) return;
            if (!arranged) {
                arranged = true;
                UUID playerId = client.player.getUuid();
                client.getServer().execute(() -> {
                    var server = client.getServer();
                    var world = server.getOverworld();
                    floor = world.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0) + 2;
                    for (int x = -18; x <= 18; x++) for (int z = -18; z <= 70; z++) {
                        world.setBlockState(new BlockPos(x, floor - 1, z), Blocks.STONE_BRICKS.getDefaultState());
                        for (int y = 0; y < 18; y++) world.setBlockState(new BlockPos(x, floor + y, z), Blocks.AIR.getDefaultState());
                    }
                    world.setTimeOfDay(6000);
                    world.setWeather(0, 6000, false, false);
                    var player = server.getPlayerManager().getPlayer(playerId);
                    player.setInvulnerable(true); // Keep the visual-review player safe from ambient mobs.
                    player.setAttached(EWAttachments.WELCOME_SEEN, true);
                    player.setAttached(EWAttachments.AFFINITY, "WIND");
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FRACTURED_WAND));
                    WandProgression.earn(player, WizardAffinity.WIND,
                            com.anton.elementalwands.data.WandSpells.forAffinity(WizardAffinity.WIND)
                                    .stream().mapToLong(spell -> spell.price()).sum());
                    WandProgression.purchase(player, "WIND", "sky_shear");
                    WandProgression.purchase(player, "WIND", "waylay_dash");
                    WandProgression.purchase(player, "WIND", "zephyr_strike");
                    WandLoadouts.equip(player, "WIND", 0, "sky_shear");
                    WandLoadouts.equip(player, "WIND", 1, "waylay_dash");
                    WandLoadouts.equip(player, "WIND", 2, "zephyr_strike");
                    AbstractWandItem.addUltimateCharge(player.getMainHandStack(), 100);
                    ModNetworking.syncPlayerData(player);
                    player.networkHandler.requestTeleport(.5, floor, .5, 0, 0);
                    var zombie = new ZombieEntity(EntityType.ZOMBIE, world);
                    zombie.setAiDisabled(true);
                    zombie.setSilent(true);
                    zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                    zombie.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);
                    zombie.setHealth(200);
                    zombie.refreshPositionAndAngles(.5, floor, 8.5, 180, 0);
                    world.spawnEntity(zombie);
                    targetId = zombie.getUuid();
                    setupReady = true;
                });
                return;
            }
            if (!setupReady) return;
            ++scene;
            if (scene <= 160) {
                if (scene == 1) stage(client, "Wind scene starts in 8 seconds. Click the game to watch.");
                return;
            }
            int t = scene - 160;
            if (t < 35) { WandWelcome.reset(); client.setScreen(null); }
            if (t <= 100) { client.player.setYaw(0); client.player.setPitch(0); }
            if (t == 35) {
                require(ClientPlayerData.getAffinity() == WizardAffinity.WIND, "Wind affinity did not sync");
                require(ClientPlayerData.loadout().get(0).equals("sky_shear"), "Sky Shear did not equip");
                require(ClientPlayerData.loadout().get(1).equals("waylay_dash"), "Waylay Dash did not equip");
                require(ClientPlayerData.loadout().get(2).equals("zephyr_strike"), "Zephyr Strike did not unlock and equip");
                stage(client, "Wind visual test: Sky Shear blades and HUD");
                shot(client, "wind-hud-before-cast.png");
            }
            if (t == 60) cast(client, 0);
            if (t >= 61 && t <= 72) for (var entity : client.world.getEntities())
                if (entity instanceof VacuumBladeEntity) {
                    sawBlade = true;
                    if (t == 62 || t == 65) shot(client, "wind-sky-shear-" + t + ".png");
                }
            if (t == 90) {
                require(sawBlade, "Sky Shear blades never synchronized to the client");
                client.getServer().execute(() -> {
                    var entity = client.getServer().getOverworld().getEntity(targetId);
                    if (entity instanceof ZombieEntity zombie) targetHealth = zombie.getHealth();
                });
            }
            if (t == 110) {
                require(targetHealth < 200, "Sky Shear did not damage its target");
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                stage(client, "Waylay Dash: watch the slipstream and burst rings");
            }
            if (t == 145) cast(client, 1);
            if (t == 151) shot(client, "wind-waylay-dash.png");
            if (t == 165) cast(client, 1);
            if (t == 170) shot(client, "wind-waylay-chain.png");
            if (t == 215) {
                stage(client, "Zephyr Strike: third-person wings and landing impact");
                UUID playerId = client.player.getUuid();
                client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(playerId);
                    player.networkHandler.requestTeleport(.5, floor, .5, 0, 0);
                    player.setVelocity(Vec3d.ZERO);
                });
            }
            if (t == 245) cast(client, 2);
            if (t >= 250 && t <= 400) {
                UUID playerId = client.player.getUuid();
                if (t == 255) client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(playerId);
                    sawWings = ZephyrStrikeManager.isActive(player)
                            && player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.ZEPHYR_WINGS);
                });
                if (t == 265) shot(client, "wind-zephyr-wings.png");
                if (t == 320) client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(playerId);
                    if (ZephyrStrikeManager.isActive(player)) {
                        player.networkHandler.requestTeleport(player.getX(), floor + 5, player.getZ(), player.getYaw(), 0);
                        player.setVelocity(0, -1.4, 0);
                    }
                });
                if (t == 330 || t == 340) shot(client, "wind-zephyr-landing-" + t + ".png");
                if (t == 370) client.getServer().execute(() -> {
                    var player = client.getServer().getPlayerManager().getPlayer(playerId);
                    sawLanding = !ZephyrStrikeManager.isActive(player);
                });
            }
            if (t == 420) {
                require(sawWings, "Zephyr wings were not equipped during the real ultimate");
                require(sawLanding, "Zephyr Strike did not complete after landing");
                shot(client, "wind-final-third-person.png");
                client.options.setPerspective(Perspective.FIRST_PERSON);
                UUID playerId = client.player.getUuid();
                client.getServer().execute(() -> AbstractWandItem.addUltimateCharge(
                        client.getServer().getPlayerManager().getPlayer(playerId).getMainHandStack(), 100));
                Files.writeString(Path.of("WIND_VISUAL_PASSED.txt"),
                        "Native wind visual scene passed: synchronized Sky Shear blades damaged a target, both Waylay Dash casts ran, Zephyr wings equipped and the landing resolved. The world remains open for manual viewing. Screenshots are in screenshots/.\n");
                stage(client, "Wind scene complete. World left open: use your wand to inspect freely.");
                done = true;
            }
        } catch (Throwable error) {
            done = true;
            error.printStackTrace();
            try { Files.writeString(Path.of("WIND_VISUAL_FAILED.txt"), error.toString()); }
            catch (Exception ignored) { }
            client.scheduleStop();
        }
    }

    private static void cast(MinecraftClient client, int slot) {
        UUID playerId = client.player.getUuid();
        client.getServer().execute(() -> WandLoadouts.cast(client.getServer().getPlayerManager().getPlayer(playerId), slot));
    }
    private static void stage(MinecraftClient client, String message) {
        client.player.sendMessage(Text.literal(message), false);
        client.player.sendMessage(Text.literal(message), true);
    }
    private static void shot(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1, text -> { });
    }
    private static void require(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
}
