package com.anton.elementalwands.mixin;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A shield that blocks one of the Fractured Guardian's rocks or Airburst shards loses its guard,
 * as it does for the Guardian's blows. Vanilla only disables a shield for blows dealt directly
 * by a living attacker, so it skips thrown damage.
 */
@Mixin(LivingEntity.class)
public abstract class GuardianShieldBreakMixin {
    @Inject(method = "getDamageBlockedAmount", at = @At("RETURN"))
    private void elementalwands$guardianProjectileBreaksGuard(ServerWorld world, DamageSource source, float amount,
            CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() <= 0 || !source.isIn(DamageTypeTags.IS_PROJECTILE)) return;
        if (!((Object)this instanceof PlayerEntity player) || !(source.getAttacker() instanceof FracturedGuardianEntity guardian)) return;
        ItemStack stack = player.getBlockingItem();
        BlocksAttacksComponent shield = stack == null ? null : stack.get(DataComponentTypes.BLOCKS_ATTACKS);
        if (shield != null) shield.applyShieldCooldown(world, player, guardian.getWeaponDisableBlockingForSeconds(), stack);
    }
}
