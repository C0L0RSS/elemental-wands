package com.anton.elementalwands.mixin;

import com.anton.elementalwands.crypt.HollowCryptManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nothing drops in the Hollow Crypt; the crypt hands inventory and experience to the respawned player. */
@Mixin(PlayerEntity.class)
public abstract class HollowCryptDeathMixin {
    @Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
    private void keepCryptInventory(ServerWorld world, CallbackInfo ci) {
        if (HollowCryptManager.keepsBelongings((PlayerEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "getExperienceToDrop", at = @At("HEAD"), cancellable = true)
    private void keepCryptExperience(ServerWorld world, CallbackInfoReturnable<Integer> ci) {
        if (HollowCryptManager.keepsBelongings((PlayerEntity) (Object) this)) ci.setReturnValue(0);
    }
}
