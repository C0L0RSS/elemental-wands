package com.anton.elementalwands.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;

/** Item components such as enchantments must be encoded with the world's registries. */
final class EquipmentReceipt {
    private EquipmentReceipt() {}

    static void put(PlayerEntity player, NbtCompound receipt, String key, ItemStack stack) {
        receipt.put(key, ItemStack.OPTIONAL_CODEC.encodeStart(
                player.getRegistryManager().getOps(NbtOps.INSTANCE), stack).getOrThrow());
    }

    static ItemStack get(PlayerEntity player, NbtCompound receipt, String key) {
        if (!receipt.contains(key)) throw new IllegalStateException("Missing saved equipment: " + key);
        return ItemStack.OPTIONAL_CODEC.parse(player.getRegistryManager().getOps(NbtOps.INSTANCE),
                receipt.get(key)).getOrThrow().copy();
    }
}
