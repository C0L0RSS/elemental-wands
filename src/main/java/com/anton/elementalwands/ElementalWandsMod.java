package com.anton.elementalwands;

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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;

public class ElementalWandsMod implements ModInitializer {
    public static final String MOD_ID = "elementalwands";

    private static final String NBT_STARTER_RECEIVED = "ew_starter_received";

    @Override
    public void onInitialize() {
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

        // First-join starter kit
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> giveStarterKit(player));
        });
    }

    private void giveStarterKit(ServerPlayerEntity player) {
        NbtCompound persistent = player.getCommandTags().stream()
                .filter(t -> t.equals(NBT_STARTER_RECEIVED))
                .findAny()
                .map(t -> (NbtCompound) null)
                .orElse(null);

        // Use command tags to track whether the player has received the starter
        if (player.getCommandTags().contains(NBT_STARTER_RECEIVED)) {
            return;
        }

        // Give Fractured Wand
        ItemStack wand = new ItemStack(ModItems.FRACTURED_WAND);
        player.getInventory().insertStack(wand);

        // Give "The Wizard's Path" written book
        ItemStack book = createWizardBook();
        player.getInventory().insertStack(book);

        // Send welcome message
        player.sendMessage(
                Text.literal("Welcome Wizard! Your journey begins now. ✨")
                        .formatted(Formatting.GOLD),
                false);

        // Mark as received
        player.addCommandTag(NBT_STARTER_RECEIVED);
    }

    private ItemStack createWizardBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        Text page1 = Text.literal("The Wizard's Path\n\n")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD)
                .append(Text.literal("You hold a Fractured Wand — an empty vessel yearning for power.\n\n")
                        .formatted(Formatting.BLACK))
                .append(Text.literal("To awaken it, you must find ")
                        .formatted(Formatting.BLACK))
                .append(Text.literal("Elemental Crystal Ores ")
                        .formatted(Formatting.DARK_AQUA, Formatting.BOLD))
                .append(Text.literal("deep underground.")
                        .formatted(Formatting.BLACK));

        Text page2 = Text.literal("Mining & Smelting\n\n")
                .formatted(Formatting.DARK_GREEN, Formatting.BOLD)
                .append(Text.literal("1. Mine Crystal Ores with a pickaxe to obtain Raw Crystals.\n\n")
                        .formatted(Formatting.BLACK))
                .append(Text.literal("2. Smelt the Raw Crystal in a furnace to refine it.\n\n")
                        .formatted(Formatting.BLACK))
                .append(Text.literal("3. Combine your Fractured Wand with a Refined Crystal in a crafting table.")
                        .formatted(Formatting.BLACK));

        Text page3 = Text.literal("Awakening!\n\n")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("The element of your crystal determines your wand's power:\n\n")
                        .formatted(Formatting.BLACK))
                .append(Text.literal("🔥 Fire  💨 Wind\n🪨 Stone  ❄ Ice\n🌌 Space\n\n")
                        .formatted(Formatting.DARK_PURPLE))
                .append(Text.literal("Choose wisely — or craft a Reset Rune to try another path!")
                        .formatted(Formatting.BLACK));

        List<RawFilteredPair<Text>> pages = List.of(
                RawFilteredPair.of(page1),
                RawFilteredPair.of(page2),
                RawFilteredPair.of(page3));

        WrittenBookContentComponent content = new WrittenBookContentComponent(
                RawFilteredPair.of("The Wizard's Path"),
                "The Ancients",
                0,
                pages,
                true);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
        return book;
    }
}
