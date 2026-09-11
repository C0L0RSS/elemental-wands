package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.StoneClusterManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class StoneDamageMixin {
    @Unique private float elementalwands$stoneHealthBefore;
    @Inject(method = "damage", at = @At("HEAD"))
    private void elementalwands$beforeDamage(ServerWorld world, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        elementalwands$stoneHealthBefore = self.getHealth() + self.getAbsorptionAmount();
    }
    @Inject(method = "damage", at = @At("RETURN"))
    private void elementalwands$afterDamage(ServerWorld world, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof ServerPlayerEntity player && cir.getReturnValueZ()) {
            float lost = elementalwands$stoneHealthBefore - player.getHealth() - player.getAbsorptionAmount();
            if (lost > 0 && player.isAlive()) StoneClusterManager.onDamage(player, lost);
        }
    }
}
