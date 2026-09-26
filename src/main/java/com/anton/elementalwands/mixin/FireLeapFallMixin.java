package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.FireLeapManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The landing packet can outrun the leap's server tick; a committed arc never deals fall damage. */
@Mixin(PlayerEntity.class)
public abstract class FireLeapFallMixin {
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void protect(double distance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity p && FireLeapManager.protectedFall(p)) {
            p.fallDistance = 0;
            cir.setReturnValue(false);
        }
    }
}
