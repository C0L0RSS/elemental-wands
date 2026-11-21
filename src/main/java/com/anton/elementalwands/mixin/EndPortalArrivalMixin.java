package com.anton.elementalwands.mixin;

import com.anton.elementalwands.end.EndGate;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every trip into the End goes by {@link EndGate}: its admission rules, the way back it records, and the
 * arena's arrival spot instead of vanilla's obsidian platform (which is therefore never made). Trips out
 * of the End, the way home and the credits, stay vanilla's.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalArrivalMixin {
    @Inject(method = "createTeleportTarget", at = @At("HEAD"), cancellable = true)
    private void elementalwands$intoTheArena(ServerWorld world, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTarget> ci) {
        if (world.getRegistryKey() != World.END) ci.setReturnValue(EndGate.target(world, entity, pos));
    }
}
