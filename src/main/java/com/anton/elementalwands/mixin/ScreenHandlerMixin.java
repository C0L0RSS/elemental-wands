package com.anton.elementalwands.mixin;

import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.ZephyrStrikeManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents transient Zephyr equipment from escaping into external storage. */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void elementalWands$guardZephyrWings(int slotIndex, int button,
            SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !ZephyrStrikeManager.isActive(serverPlayer)) {
            return;
        }

        ScreenHandler handler = (ScreenHandler) (Object) this;
        boolean wingsOnCursor = handler.getCursorStack().isOf(ModItems.ZEPHYR_WINGS);
        boolean clickedWings = slotIndex >= 0
                && slotIndex < handler.slots.size()
                && handler.getSlot(slotIndex).getStack().isOf(ModItems.ZEPHYR_WINGS);

        if (wingsOnCursor || clickedWings) {
            // The client predicts inventory clicks locally; force the unchanged
            // authoritative contents back so a blocked move cannot leave a
            // ghost Wings stack on its cursor.
            handler.syncState();
            ci.cancel();
        }
    }
}
