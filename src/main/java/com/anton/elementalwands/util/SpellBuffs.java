package com.anton.elementalwands.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class SpellBuffs {
    private SpellBuffs() {}

    /** Let Regeneration I cross its 50-tick healing boundary; refreshing 20 ticks every tick never heals. */
    public static void regeneration(LivingEntity target) {
        regeneration(target, 0);
    }

    public static void regeneration(LivingEntity target, int amplifier) {
        StatusEffectInstance current = target.getStatusEffect(StatusEffects.REGENERATION);
        if (current == null || current.getAmplifier() < amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() <= 10)) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,60,amplifier,false,false,true));
        }
    }
}
