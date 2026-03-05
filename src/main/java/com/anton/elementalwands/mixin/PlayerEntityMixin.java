package com.anton.elementalwands.mixin;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void onDropInventory(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        List<ItemStack> soulboundItems = new ArrayList<>();

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty())
                continue;

            boolean isWand = stack.getItem() instanceof com.anton.elementalwands.item.AbstractWandItem;
            boolean isWizardBook = false;

            if (stack.isOf(Items.WRITTEN_BOOK)) {
                WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
                if (content != null && "The Wizard's Path".equals(content.title().raw())) {
                    isWizardBook = true;
                }
            }

            if (isWand || isWizardBook) {
                // Add to our list, and remove from the inventory so it doesn't get dropped.
                soulboundItems.add(stack.copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }

        // Store soulbound items temporarily in a way we can retrieve them in
        // ServerPlayerEvents.COPY_FROM
        // Since we removed them from the inventory, dropAll() will not drop them.
        // We can pass them to the new player in COPY_FROM. To do that, we need a place
        // to store them on the old player.
        // For simplicity, we can let COPY_FROM know by placing them in a custom field
        // or attachment, BUT we can actually
        // just put them in the player's EnderChest inventory temporarily (if there's
        // space, though that's hacky),
        // OR better: we can create a temporary list field in this mixin or in
        // ElementalWandsMod.

        // Let's use an attachment or we can just inject an interface.
        // Wait, COPY_FROM gives us the old player entity. We can just add a public map
        // in ElementalWandsMod connecting UUID to List<ItemStack>.
        if (!soulboundItems.isEmpty()) {
            ElementalWandsMod.stashSoulboundItems(player.getUuid(), soulboundItems);
        }
    }
}
