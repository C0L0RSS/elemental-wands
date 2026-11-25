package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.renderer.ThornbiteVisual;
import com.anton.elementalwands.entity.ThornLashEntity;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class ThornbiteTrackingMixin {
    @Inject(method = "onDataTrackerUpdate", at = @At("TAIL"))
    private void elementalwands$trackLash(List<DataTracker.SerializedEntry<?>> entries, CallbackInfo ci) {
        if ((Object) this instanceof ThornLashEntity lash) ThornbiteVisual.track(lash);
    }
}
