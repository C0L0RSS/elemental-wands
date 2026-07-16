package com.anton.elementalwands.item;

import com.anton.elementalwands.util.ZephyrStrikeManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Transient glider equipped only while Zephyr Strike is active.
 *
 * <p>The item is intentionally absent from creative tabs, recipes, and loot.
 * The server-side guard also deletes orphaned copies instead of allowing the
 * temporary spell equipment to become an obtainable item.</p>
 */
public final class ZephyrWingsItem extends Item {

    public ZephyrWingsItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (entity instanceof ServerPlayerEntity player && !ZephyrStrikeManager.isActive(player)) {
            stack.setCount(0);
        }
    }
}
