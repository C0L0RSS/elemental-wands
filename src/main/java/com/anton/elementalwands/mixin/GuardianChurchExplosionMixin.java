package com.anton.elementalwands.mixin;

import com.anton.elementalwands.church.GuardianChurchManager;
import net.minecraft.world.explosion.ExplosionImpl;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(ExplosionImpl.class)
public abstract class GuardianChurchExplosionMixin {
    @Inject(method="getBlocksToDestroy",at=@At("RETURN"),cancellable=true)
    private void keepWard(CallbackInfoReturnable<List<BlockPos>> ci) {
        var world=((ExplosionImpl)(Object)this).getWorld();
        ci.setReturnValue(ci.getReturnValue().stream().filter(p -> !GuardianChurchManager.protectedBlock(world,p)).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
    }
}
