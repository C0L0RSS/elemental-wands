package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.StoneStaggerAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class StoneStaggerMixin implements StoneStaggerAccess {
    @Unique private long elementalwands$staggerUntil = Long.MIN_VALUE;
    @Override public void elementalwands$staggerUntil(long tick) { elementalwands$staggerUntil = tick; }
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void elementalwands$preventSprint(boolean sprinting, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (sprinting && self.getEntityWorld().getTime() < elementalwands$staggerUntil) ci.cancel();
    }
}
