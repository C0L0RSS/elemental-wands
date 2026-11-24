package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.SoulboundInventoryCarrier;
import com.anton.elementalwands.util.TitanDomeManager;
import com.anton.elementalwands.util.ZephyrStrikeManager;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.registry.ModItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements SoulboundInventoryCarrier {
    @Inject(method = "shouldCancelInteraction", at = @At("HEAD"), cancellable = true)
    private void wandBlockInteraction(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (player.getMainHandStack().getItem() instanceof com.anton.elementalwands.item.AbstractWandItem)
            cir.setReturnValue(false);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean ew$titanDamage(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (player.getEntityWorld() instanceof ServerWorld world && player.getMainHandStack().isOf(ModItems.TITAN_SWORD)
                && TitanDomeManager.hasActiveDome(player)) {
            return SpellCombat.damage(target, world, source, amount, player, WizardAffinity.STONE,
                    (victim, spellSource, scaled) -> original.call(victim, spellSource, scaled));
        }
        return original.call(target, source, amount);
    }
    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean ew$titanSweep(LivingEntity target, ServerWorld world, DamageSource source, float amount, Operation<Boolean> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (player.getMainHandStack().isOf(ModItems.TITAN_SWORD) && TitanDomeManager.hasActiveDome(player)) {
            return SpellCombat.damage(target, world, source, amount, player, WizardAffinity.STONE,
                    (victim, spellSource, scaled) -> original.call(victim, world, spellSource, scaled));
        }
        return original.call(target, world, source, amount);
    }

    @Unique
    private List<ItemStack> elementalWands$soulboundItems = Collections.emptyList();

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void onDropInventory(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        // Restore transient Zephyr equipment before vanilla or the soulbound
        // pass sees the inventory. This runs only for a finalized death, unlike
        // ALLOW_DEATH, which fires before a Totem of Undying can save a player.
        ZephyrStrikeManager.onPlayerDeath(serverPlayer);
        TitanDomeManager.onPlayerDeath(serverPlayer);

        List<ItemStack> soulboundItems = new ArrayList<>();

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty())
                continue;

            boolean isWand = stack.getItem() instanceof com.anton.elementalwands.item.AbstractWandItem;
            if (isWand) {
                // Remove protected gear before vanilla drops the rest of the inventory.
                soulboundItems.add(stack.copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }

        elementalWands$soulboundItems = soulboundItems.isEmpty() ? Collections.emptyList() : List.copyOf(soulboundItems);
    }

    @Override
    public List<ItemStack> elementalWands$consumeSoulboundItems() {
        List<ItemStack> items = elementalWands$soulboundItems;
        elementalWands$soulboundItems = Collections.emptyList();
        return items;
    }
}
