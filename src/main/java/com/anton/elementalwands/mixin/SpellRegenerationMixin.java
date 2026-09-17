package com.anton.elementalwands.mixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(targets="net.minecraft.entity.effect.RegenerationStatusEffect")
public abstract class SpellRegenerationMixin {
    @Redirect(method="applyUpdateEffect",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;heal(F)V"))
    private void ew$healing(LivingEntity target,float amount){com.anton.elementalwands.util.SpellCombat.regenerationHeal(target,amount);}
}
