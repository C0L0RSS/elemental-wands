package com.anton.elementalwands.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Only the mod's authored guide books are retired, never ordinary player books or loot. */
public final class WandGuide {
    public static boolean legacyBook(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) return false;
        var content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (content == null) return false;
        return (content.title().raw().equals("The Wizard's Path") && content.author().equals("The Ancients"))
                || (content.title().raw().equals("The Keeper's Promise") && content.author().equals("The Last Bellkeeper"));
    }
    public static void removeLegacyBooks(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) if (legacyBook(inventory.getStack(i))) inventory.setStack(i, ItemStack.EMPTY);
        inventory.markDirty();
    }
    private WandGuide() {}
}
