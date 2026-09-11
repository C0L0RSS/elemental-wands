package com.anton.elementalwands.mixin;

import com.anton.elementalwands.church.GuardianChurchManager;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonHandler.class)
public abstract class GuardianChurchPistonMixin {
    @Shadow @Final private World world;
    @Inject(method="calculatePush",at=@At("RETURN"),cancellable=true)
    private void keepWard(CallbackInfoReturnable<Boolean> ci) {
        var handler=(PistonHandler)(Object)this;
        if (ci.getReturnValue() && java.util.stream.Stream.concat(handler.getMovedBlocks().stream(),handler.getBrokenBlocks().stream()).anyMatch(p -> GuardianChurchManager.protectedBlock(world,p))) ci.setReturnValue(false);
    }
}
