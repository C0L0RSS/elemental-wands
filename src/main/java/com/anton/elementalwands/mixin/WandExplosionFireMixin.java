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
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;

/** Only wand explosions use owned, temporary fire; ordinary explosions retain vanilla fire. */
@Mixin(ExplosionImpl.class)
public abstract class WandExplosionFireMixin {
    @Shadow @Final private ExplosionBehavior behavior;
    @Redirect(method = "createFire", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
    private boolean elementalwands$ownedFire(ServerWorld world, BlockPos pos, BlockState state) {
        if (behavior instanceof WandExplosionBehavior wand) {
            return !TemporaryBlockManager.placeTrackedTemporaryBlocks(world, List.of(pos),
                    ModSpellBlocks.INFERNO_FLAME.getDefaultState(), 1200, BlockState::isAir, wand.caster()).isEmpty();
        }
        return world.setBlockState(pos, state);
    }
}
