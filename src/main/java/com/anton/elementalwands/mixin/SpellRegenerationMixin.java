package com.anton.elementalwands.mixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.anton.elementalwands.util.SpellCombat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
@Mixin(targets="net.minecraft.entity.effect.RegenerationStatusEffect")
public abstract class SpellRegenerationMixin {
    @WrapOperation(method = "applyUpdateEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;heal(F)V"))
    private void ew$healing(LivingEntity target, float amount, Operation<Void> original) {
        SpellCombat.regenerationHeal(target, amount, value -> original.call(target, value));
    }
}
