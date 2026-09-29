package com.anton.elementalwands.mixin;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.crypt.HollowCryptManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nothing drops in the Hollow Crypt or the Shattered Nave; the realm hands inventory and experience
 * to the respawned player.
 */
@Mixin(PlayerEntity.class)
public abstract class BossRealmDeathMixin {
    @Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
    private void keepRealmInventory(ServerWorld world, CallbackInfo ci) {
        if (elementalwands$keepsBelongings()) ci.cancel();
    }

    @Inject(method = "getExperienceToDrop", at = @At("HEAD"), cancellable = true)
    private void keepRealmExperience(ServerWorld world, CallbackInfoReturnable<Integer> ci) {
        if (elementalwands$keepsBelongings()) ci.setReturnValue(0);
    }

    private boolean elementalwands$keepsBelongings() {
        PlayerEntity player = (PlayerEntity) (Object) this;
        return HollowCryptManager.keepsBelongings(player) || GuardianArenaManager.keepsBelongings(player);
    }
}
