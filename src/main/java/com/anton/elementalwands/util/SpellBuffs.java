package com.anton.elementalwands.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class SpellBuffs {
    private SpellBuffs() {}

    /** Let Regeneration I cross its 50-tick healing boundary; refreshing 20 ticks every tick never heals. */
    public static void regeneration(LivingEntity target) {
        StatusEffectInstance current = target.getStatusEffect(StatusEffects.REGENERATION);
        if (current == null || (current.getAmplifier() == 0 && current.getDuration() <= 10)) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,60,0,false,false,true));
        }
    }
}
