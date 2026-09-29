package com.anton.elementalwands.mixin;

import com.anton.elementalwands.entity.GuardianLiftEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class GuardianLiftPassengerMixin {
    @Inject(method="stopRiding",at=@At("HEAD"),cancellable=true)
    private void remainOnMovingFloor(CallbackInfo ci) {
        Entity entity=(Entity)(Object)this;
        if (entity.isAlive() && entity.getVehicle() instanceof GuardianLiftEntity lift && lift.locksPassenger()) ci.cancel();
    }
}
