package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.SoulboundInventoryCarrier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements SoulboundInventoryCarrier {

    @Unique
    private List<ItemStack> elementalWands$soulboundItems = Collections.emptyList();

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void onDropInventory(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!(player instanceof ServerPlayerEntity)) {
            return;
        }

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
                // Remove protected gear before vanilla drops the rest of the inventory.
                soulboundItems.add(stack.copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }

        elementalWands$soulboundItems = soulboundItems.isEmpty() ? Collections.emptyList() : List.copyOf(soulboundItems);
    }

    @Override
    public List<ItemStack> elementalWands$consumeSoulboundItems() {
        List<ItemStack> items = elementalWands$soulboundItems;
        elementalWands$soulboundItems = Collections.emptyList();
        return items;
    }
}
