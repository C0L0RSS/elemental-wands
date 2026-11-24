package com.anton.elementalwands.mixin;

import com.anton.elementalwands.party.WandExplosionBehavior;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.TemporaryBlockManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.anton.elementalwands.util.SpellCombat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;

/** Only wand explosions use owned, temporary fire; ordinary explosions retain vanilla fire. */
@Mixin(ExplosionImpl.class)
public abstract class WandExplosionFireMixin {
    @Shadow @Final private ExplosionBehavior behavior;
    @WrapOperation(method = "damageEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean ew$damage(Entity target, ServerWorld world, DamageSource source, float amount, Operation<Boolean> original) {
        if (behavior instanceof WandExplosionBehavior wand) {
            return SpellCombat.damage(target, world, source, amount, world.getPlayerByUuid(wand.caster()), wand.affinity(),
                    (victim, spellSource, scaled) -> original.call(victim, world, spellSource, scaled));
        }
        return original.call(target, world, source, amount);
    }
    @WrapOperation(method = "createFire", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
    private boolean elementalwands$ownedFire(ServerWorld world, BlockPos pos, BlockState state, Operation<Boolean> original) {
        if (behavior instanceof WandExplosionBehavior wand) {
            BlockState flame = ModSpellBlocks.INFERNO_FLAME.getDefaultState();
            return TemporaryBlockManager.placeTrackedExplosionFire(world, pos, flame, 1200, wand.caster(),
                    () -> original.call(world, pos, flame));
        }
        return original.call(world, pos, state);
    }
}
