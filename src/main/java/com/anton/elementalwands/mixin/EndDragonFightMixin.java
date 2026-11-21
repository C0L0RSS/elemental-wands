package com.anton.elementalwands.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The End has no vanilla dragon fight: the world drops the fight it made, so there is no dragon, boss
 * bar, crystal logic, exit portal or gateway from it. The world only ticks and saves a fight that
 * exists, so the dragon-fight data saved in level.dat stays as it was.
 */
@Mixin(ServerWorld.class)
public abstract class EndDragonFightMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void elementalwands$noDragonFight(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world.getRegistryKey() == World.END) world.setEnderDragonFight(null);
    }
}
