package com.anton.elementalwands.mixin;

import com.anton.elementalwands.church.GuardianChurchManager;
import com.anton.elementalwands.crypt.HollowCryptRealm;
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
        // Explosion fire also lands only on this list, so an empty one keeps the crypt and the nave unburnt.
        if (HollowCryptRealm.keepsTerrain(world) || com.anton.elementalwands.arena.ShatteredNave.keepsTerrain(world)) {
            ci.setReturnValue(new java.util.ArrayList<>());
            return;
        }
        ci.setReturnValue(ci.getReturnValue().stream().filter(p -> !GuardianChurchManager.protectedBlock(world,p)).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
    }
}
