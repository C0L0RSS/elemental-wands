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
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TemporarySnowManager;
import com.anton.elementalwands.util.TitanDomeManager;
import com.anton.elementalwands.util.BlazeTrailManager;
import com.anton.elementalwands.world.ModWorldGen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ElementalWandsMod implements ModInitializer {
    public static final String MOD_ID = "elementalwands";

    private static final String NBT_STARTER_RECEIVED = "ew_starter_received";
    public static final Map<UUID, List<ItemStack>> soulboundStash = new ConcurrentHashMap<>();

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
        ServerPlayerEvents.COPY_FROM.register((newPlayer, original, alive) -> {
            if (alive)
                return; // Alive == end-of-portal, not death

            // Copy items stashed by PlayerEntityMixin which intercepted dropInventory
            List<ItemStack> stashed = soulboundStash.remove(original.getUuid());
            if (stashed != null) {
                for (ItemStack stack : stashed) {
                    newPlayer.getInventory().insertStack(stack);
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
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    public static void stashSoulboundItems(UUID uuid, List<ItemStack> items) {
        soulboundStash.put(uuid, items);
    }

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
        player.addExperienceLevels(-xpCost);
        player.setAttached(EWAttachments.UNLOCKED_SKILLS, currentSkills | skillBit);

        // Sound + message
        ((net.minecraft.server.world.ServerWorld) player.getEntityWorld()).playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.elementalwands.skill_unlocked").formatted(Formatting.GOLD),
                false);

        // Replace wizard book in inventory with updated version
        replaceWizardBook(player);

        // Sync HUD data to client
        ModNetworking.syncPlayerData(player);
        return 1;
    }

    /**
     * Finds the wizard's path book in the player's inventory and refreshes its
     * content.
     */
    private static void replaceWizardBook(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isWizardPathBook(stack)) {
                player.getInventory().setStack(i, new ElementalWandsMod().createWizardBook(player));
                return;
            }
        }
    }

    // ── Dynamic Wizard's Path Book ──────────────────────────────────────────

    ItemStack createWizardBook(ServerPlayerEntity player) {
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
