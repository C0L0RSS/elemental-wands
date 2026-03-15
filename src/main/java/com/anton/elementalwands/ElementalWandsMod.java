package com.anton.elementalwands;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.BlizzardManager;
import com.anton.elementalwands.util.BlinkRiftManager;
import com.anton.elementalwands.util.ChillTracker;
import com.anton.elementalwands.util.CycloneManager;
import com.anton.elementalwands.util.EventHorizonManager;
import com.anton.elementalwands.util.HollowPurpleChargeManager;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.MovementDisruptManager;
import com.anton.elementalwands.util.SoulboundInventoryCarrier;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TemporarySnowManager;
import com.anton.elementalwands.util.TitanDomeManager;
import com.anton.elementalwands.util.BlazeTrailManager;
import com.anton.elementalwands.world.ModWorldGen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import com.anton.elementalwands.entity.FireSpiritEntity;
import com.anton.elementalwands.entity.StoneZombieEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldAccess;

public class ElementalWandsMod implements ModInitializer {
    public static final String MOD_ID = "elementalwands";

    private static final String NBT_STARTER_RECEIVED = "ew_starter_received";
    private static final int BOOK_REFRESH_INTERVAL = 40; // Refresh every 2 seconds (40 ticks)

    @Override
    public void onInitialize() {
        EWAttachments.init();

        TemporarySnowManager.init();
        TemporaryBlockManager.init();
        ChillTracker.init();
        BlizzardManager.init();
        CycloneManager.init();
        MeteorManager.init();
        TitanDomeManager.init();
        BlazeTrailManager.init();
        MovementDisruptManager.init();
        BlinkRiftManager.init();
        EventHorizonManager.init();
        HollowPurpleChargeManager.init();
        ModBlocks.registerAll();
        ModEntities.registerAll();
        FabricDefaultAttributeRegistry.register(ModEntities.STONE_ZOMBIE, StoneZombieEntity.createAttributes().build());
        SpawnRestriction.register(
                ModEntities.STONE_ZOMBIE,
                SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                ZombieEntity::canSpawnInDark);
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                SpawnGroup.MONSTER,
                ModEntities.STONE_ZOMBIE,
                15, 1, 3);
        FabricDefaultAttributeRegistry.register(ModEntities.FIRE_SPIRIT, FireSpiritEntity.createAttributes().build());
        SpawnRestriction.register(
                ModEntities.FIRE_SPIRIT,
                SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                ZombieEntity::canSpawnInDark);
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                SpawnGroup.MONSTER,
                ModEntities.FIRE_SPIRIT,
                10, 1, 3);
        ModItems.registerAll();
        ModNetworking.registerPayloads();
        ModNetworking.registerC2SReceivers();
        ModWorldGen.registerAll();

        // ── First-join starter kit ──────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> {
                giveStarterKit(player);
                ModNetworking.syncPlayerData(player);
            });
        });

        // ── Soulbound: copy Fractured Wand + Wizard's Path on respawn ───
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive)
                return; // Alive == end-of-portal, not death

            List<ItemStack> stashed = ((SoulboundInventoryCarrier) oldPlayer).elementalWands$consumeSoulboundItems();
            if (stashed != null) {
                for (ItemStack stack : stashed) {
                    if (!newPlayer.getInventory().insertStack(stack.copy())) {
                        newPlayer.dropItem(stack.copy(), false);
                    }
                }
            }
        });

        // ── /ew unlock <secondary|ultimate> command ─────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("ew")
                        .then(CommandManager.literal("unlock")
                                .then(CommandManager.literal("secondary")
                                        .executes(ctx -> handleSkillUnlock(ctx.getSource(), "secondary")))
                                .then(CommandManager.literal("ultimate")
                                        .executes(ctx -> handleSkillUnlock(ctx.getSource(), "ultimate"))))));

        // ── Periodic book refresh so flux display stays current ─────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % BOOK_REFRESH_INTERVAL != 0)
                return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                refreshWizardBook(player);
            }
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isWizardPathBook(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK))
            return false;
        WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (content == null)
            return false;
        String title = content.title().raw();
        return "The Wizard's Path".equals(title);
    }

    private void giveStarterKit(ServerPlayerEntity player) {
        if (player.getCommandTags().contains(NBT_STARTER_RECEIVED))
            return;

        player.getInventory().insertStack(new ItemStack(ModItems.FRACTURED_WAND));
        player.getInventory().insertStack(createWizardBook(player));

        player.sendMessage(
                Text.literal("Welcome Wizard! Your journey begins now.")
                        .formatted(Formatting.GOLD),
                false);

        player.addCommandTag(NBT_STARTER_RECEIVED);
    }

    // ── /ew unlock handler ──────────────────────────────────────────────────

    private static int handleSkillUnlock(ServerCommandSource source, String skillName) {
        if (!source.isExecutedByPlayer())
            return 0;
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        int skillBit;
        long fluxCost;
        int xpCost;
        if (skillName.equals("secondary")) {
            skillBit = EWAttachments.SKILL_SECONDARY;
            fluxCost = EWAttachments.SECONDARY_FLUX_COST;
            xpCost = EWAttachments.SECONDARY_XP_COST;
        } else {
            skillBit = EWAttachments.SKILL_ULTIMATE;
            fluxCost = EWAttachments.ULTIMATE_FLUX_COST;
            xpCost = EWAttachments.ULTIMATE_XP_COST;
        }

        int currentSkills = player.getAttachedOrElse(EWAttachments.UNLOCKED_SKILLS, 0);
        if ((currentSkills & skillBit) != 0) {
            player.sendMessage(Text.literal("You have already unlocked this ability!").formatted(Formatting.YELLOW),
                    false);
            return 0;
        }

        long currentFlux = player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L);
        if (currentFlux < fluxCost) {
            player.sendMessage(Text.translatable("message.elementalwands.not_enough_flux").formatted(Formatting.RED),
                    false);
            return 0;
        }

        if (player.experienceLevel < xpCost) {
            player.sendMessage(
                    Text.literal("You need " + xpCost + " XP levels to unlock this.").formatted(Formatting.RED), false);
            return 0;
        }

        // Consume resources
        player.setAttached(EWAttachments.ARCANE_FLUX, currentFlux - fluxCost);
        player.addExperienceLevels(-xpCost);
        player.setAttached(EWAttachments.UNLOCKED_SKILLS, currentSkills | skillBit);

        // Sound + message
        ((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.elementalwands.skill_unlocked").formatted(Formatting.GOLD),
                false);

        // Replace wizard book in inventory with updated version
        refreshWizardBook(player);

        // Sync HUD data to client
        ModNetworking.syncPlayerData(player);
        return 1;
    }

    /**
     * Finds the wizard's path book in the player's inventory and refreshes its
     * content.
     */
    public static void refreshWizardBook(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isWizardPathBook(stack)) {
                // Only rebuild if flux or skills have changed since the book was last created
                ItemStack updated = createWizardBook(player);
                WrittenBookContentComponent oldContent = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                WrittenBookContentComponent newContent = updated.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                if (oldContent != null && newContent != null && oldContent.equals(newContent)) {
                    return; // No change needed
                }
                player.getInventory().setStack(i, updated);
                return;
            }
        }
    }

    // ── Dynamic Wizard's Path Book ──────────────────────────────────────────

    static ItemStack createWizardBook(ServerPlayerEntity player) {
        long arcaneFlux = player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L);
        int skills = player.getAttachedOrElse(EWAttachments.UNLOCKED_SKILLS, 0);
        boolean secUnlocked = (skills & EWAttachments.SKILL_SECONDARY) != 0;
        boolean ultUnlocked = (skills & EWAttachments.SKILL_ULTIMATE) != 0;

        long nextCost = secUnlocked ? EWAttachments.ULTIMATE_FLUX_COST : EWAttachments.SECONDARY_FLUX_COST;
        String fluxLine = (secUnlocked && ultUnlocked)
                ? "All paths opened!"
                : arcaneFlux + " / " + nextCost;

        // Page 1 — progress + unlock buttons
        Text page1 = Text.literal("The Wizard's Path\n\n")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD)
                .append(Text.literal("Arcane Flux: ").formatted(Formatting.DARK_AQUA))
                .append(Text.literal(fluxLine + "\n\n").formatted(Formatting.AQUA))
                .append(buildUnlockButton(secUnlocked, "secondary",
                        EWAttachments.SECONDARY_FLUX_COST, EWAttachments.SECONDARY_XP_COST, arcaneFlux))
                .append(Text.literal("\n\n"))
                .append(buildUnlockButton(ultUnlocked, "ultimate",
                        EWAttachments.ULTIMATE_FLUX_COST, EWAttachments.ULTIMATE_XP_COST, arcaneFlux));

        // Page 2 — tips
        Text page2 = Text.literal("Mining & Smelting\n\n")
                .formatted(Formatting.DARK_GREEN, Formatting.BOLD)
                .append(Text.literal(
                        "Mine Crystal Ores to gain Arcane Flux.\n\nEvery hit on an enemy also charges your wand's Ultimate Reservoir.\n\nSmelt Raw Crystals, then craft with a Fractured Wand to awaken it.")
                        .formatted(Formatting.BLACK));

        // Page 3 — elements
        Text page3 = Text.literal("Elemental Paths\n\n")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(
                        "Fire  Wind  Stone\nIce   Space\n\nEach element has three spells:\nPrimary  Secondary  Ultimate\n\nUnlock Secondary and Ultimate via Arcane Flux.")
                        .formatted(Formatting.DARK_PURPLE));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                RawFilteredPair.of("The Wizard's Path"),
                "The Ancients",
                0,
                List.of(RawFilteredPair.of(page1), RawFilteredPair.of(page2), RawFilteredPair.of(page3)),
                true));
        return book;
    }

    private static Text buildUnlockButton(boolean alreadyUnlocked, String skill,
            long fluxCost, int xpCost, long currentFlux) {
        if (alreadyUnlocked) {
            return Text.literal("[" + skill.toUpperCase() + ": UNLOCKED]").formatted(Formatting.GREEN);
        }
        boolean ready = currentFlux >= fluxCost;
        String label = "[ UNLOCK " + skill.toUpperCase()
                + " (Cost: " + xpCost + " Levels) ]";
        net.minecraft.text.MutableText btn = Text.literal(label)
                .formatted(ready ? Formatting.GOLD : Formatting.GRAY);
        return btn.styled(style -> style.withClickEvent(
                new ClickEvent.RunCommand("/ew unlock " + skill)));
    }
}
