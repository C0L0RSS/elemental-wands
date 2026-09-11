package com.anton.elementalwands.mixin;

import com.anton.elementalwands.arena.GuardianArenaManager;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class GuardianArenaWorldMixin {
    @Inject(method="breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",at=@At("HEAD"),cancellable=true)
    private void protectChurchDrops(BlockPos pos,boolean drop,net.minecraft.entity.Entity breaker,int depth,CallbackInfoReturnable<Boolean> ci) {
        if ((Object)this instanceof ServerWorld world && com.anton.elementalwands.church.GuardianChurchManager.protectedBlock(world,pos)) ci.setReturnValue(false);
    }

    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void protectFoundation(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> ci) {
        if (!com.anton.elementalwands.church.GuardianChurchManager.isMutating() && (Object)this instanceof ServerWorld world &&
                (com.anton.elementalwands.church.GuardianChurchManager.protectedBlock(world,pos) || (GuardianArenaManager.protectedBlock(world,pos) && !GuardianArenaManager.authorizedSpellWrite(world,pos,state)) || GuardianArenaManager.rejectPlacement(world,pos,state))) ci.setReturnValue(false);
    }
}
