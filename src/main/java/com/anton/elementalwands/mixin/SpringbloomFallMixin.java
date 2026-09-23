package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.SpringbloomManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class SpringbloomFallMixin {
    @Inject(method="handleFallDamage",at=@At("HEAD"),cancellable=true)
    private void protect(double distance,float multiplier,DamageSource source,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof ServerPlayerEntity p && (SpringbloomManager.protectedFall(p) || SpringbloomManager.catching(p))) {
            SpringbloomManager.finishLanding(p);cir.setReturnValue(false);
        }
    }
}
