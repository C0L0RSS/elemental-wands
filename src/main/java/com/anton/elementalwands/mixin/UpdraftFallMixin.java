package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.UpdraftManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class UpdraftFallMixin {
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void protect(double distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity p && UpdraftManager.protectedFall(p)) {
            UpdraftManager.finish(p);
            p.fallDistance = 0;
            cir.setReturnValue(false);
        }
    }
}
