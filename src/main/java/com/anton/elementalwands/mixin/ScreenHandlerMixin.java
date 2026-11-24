package com.anton.elementalwands.mixin;

import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.TitanDomeManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents borrowed spell equipment from escaping into external storage. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void elementalWands$guardBorrowedGear(int slotIndex, int button,
            SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity)) return;

        ScreenHandler handler = (ScreenHandler) (Object) this;
        Slot slot = slotIndex >= 0 && slotIndex < handler.slots.size() ? handler.getSlot(slotIndex) : null;
        boolean clickedGear = slot != null && elementalWands$isBorrowed(slot.getStack());
        boolean swappedGear = actionType == SlotActionType.SWAP
                && (PlayerInventory.isValidHotbarIndex(button) || button == PlayerInventory.OFF_HAND_SLOT)
                && elementalWands$isBorrowed(player.getInventory().getStack(button));
        if (clickedGear || swappedGear || elementalWands$isBorrowed(handler.getCursorStack())) {
            // An older save may already contain loaned equipment in a chest.
            if (clickedGear && slot.inventory != player.getInventory()) slot.setStack(ItemStack.EMPTY);
            handler.syncState();
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean elementalWands$isBorrowed(ItemStack stack) {
        return stack.isOf(ModItems.ZEPHYR_WINGS) || TitanDomeManager.isMarkedTitanGear(stack);
    }
}
