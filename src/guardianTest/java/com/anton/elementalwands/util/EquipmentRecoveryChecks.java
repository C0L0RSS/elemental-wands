package com.anton.elementalwands.util;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.registry.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;


/** Run only with an isolated smoke-test player/world, before any manager ticks. */
public final class EquipmentRecoveryChecks {
    public static void run(MinecraftServer server, ServerPlayerEntity player) throws Exception {
        player.getInventory().clear();
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.setDamage(37);
        chest.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal("Recovery armor"));
        chest.addEnchantment(player.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                .getOrThrow(net.minecraft.enchantment.Enchantments.PROTECTION),3);
        player.equipStack(EquipmentSlot.CHEST, chest);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FRACTURED_WAND));
        ZephyrStrikeManager.begin(player, player.getMainHandStack(), server.getTicks());
        var saved = save(player);
        ZephyrStrikeManager.cancel(player);
        require(player.getAttached(EWAttachments.ZEPHYR_GEAR) == null, "Zephyr normal completion retained receipt");
        load(player, saved);
        ZephyrStrikeManager.cancel(player);
        require(ItemStack.areEqual(chest, player.getEquippedStack(EquipmentSlot.CHEST)), "Zephyr reload lost original chest data");
        ZephyrStrikeManager.cancel(player);
        require(count(player, Items.DIAMOND_CHESTPLATE) == 1, "Zephyr replay duplicated chestplate");

        player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        ItemStack wand = player.getMainHandStack().copy();
        TitanDomeManager.startDome(player.getEntityWorld(), player);
        saved = save(player);
        TitanDomeManager.cancelForEncounter(player);
        require(player.getAttached(EWAttachments.TITAN_GEAR) == null, "Titan normal completion retained receipt");
        load(player, saved);
        TitanDomeManager.cancelForEncounter(player);
        require(ItemStack.areEqual(wand, player.getMainHandStack()), "Titan reload lost original wand");
        require(ItemStack.areEqual(chest, player.getEquippedStack(EquipmentSlot.CHEST)), "Titan reload lost chest data");
        require(player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET), "Titan reload lost helmet");
        TitanDomeManager.cancelForEncounter(player);
        require(count(player, Items.DIAMOND_CHESTPLATE) == 1 && count(player, ModItems.FRACTURED_WAND) == 1,
                "Titan replay duplicated originals");
        require(player.getAttached(EWAttachments.TITAN_GEAR) == null, "Titan recovery retained receipt");
    }

    private static net.minecraft.nbt.NbtCompound save(ServerPlayerEntity player) {
        var output = NbtWriteView.create(ErrorReporter.EMPTY, player.getRegistryManager());
        player.writeData(output);
        return output.getNbt().copy();
    }
    private static void load(ServerPlayerEntity player, net.minecraft.nbt.NbtCompound saved) {
        player.readData(NbtReadView.create(ErrorReporter.EMPTY, player.getRegistryManager(), saved));
    }
    private static int count(ServerPlayerEntity player, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
