package com.anton.elementalwands.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class SpellBuffs {
    private SpellBuffs() {}

    /** Let Regeneration I cross its 50-tick healing boundary; refreshing 20 ticks every tick never heals. */
    public static void regeneration(LivingEntity target) {
        regeneration(target, target, com.anton.elementalwands.data.WizardAffinity.FIRE, 0);
    }

    public static void regeneration(LivingEntity target, int amplifier) {
        regeneration(target,target,com.anton.elementalwands.data.WizardAffinity.NATURE,amplifier);
    }
    public static void regeneration(LivingEntity target, net.minecraft.entity.Entity owner, com.anton.elementalwands.data.WizardAffinity affinity,int amplifier) {
        StatusEffectInstance current = target.getStatusEffect(StatusEffects.REGENERATION);
        if (current == null || current.getAmplifier() < amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() <= 10)) {
            if(target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,60,amplifier,false,false,true)))
                SpellCombat.regeneration(target,owner,affinity,amplifier);
        }
    }
}
