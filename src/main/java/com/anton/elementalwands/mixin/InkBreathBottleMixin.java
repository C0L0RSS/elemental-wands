package com.anton.elementalwands.mixin;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.item.GlassBottleItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dragon's breath has a new source: no dragon can be fought, so the cloud the First Wizard's flame leaves is bottled
 * instead. Vanilla's glass bottle collects a living area effect cloud only when its owner is an Ender Dragon (the
 * predicate in {@code GlassBottleItem.use}); this lets a cloud owned by the First Wizard pass it as well. Everything
 * else (the radius it takes, the sound, the item) is vanilla's.
 */
@Mixin(GlassBottleItem.class)
abstract class InkBreathBottleMixin {
    @Inject(method = "method_7726", at = @At("HEAD"), cancellable = true)
    private static void elementalwands$inkBreath(AreaEffectCloudEntity cloud, CallbackInfoReturnable<Boolean> result) {
        if (cloud != null && cloud.isAlive() && cloud.getOwner() instanceof FirstWizardEntity) result.setReturnValue(true);
    }
}
