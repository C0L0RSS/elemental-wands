package com.anton.elementalwands.mixin;

import com.anton.elementalwands.end.EndEncounter;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No vanilla mob spawns naturally into the End arena while a fight runs. The biome itself is unchanged. */
@Mixin(SpawnHelper.class)
public abstract class EndArenaSpawnMixin {
    @Inject(method = "isValidSpawn", at = @At("HEAD"), cancellable = true)
    private static void elementalwands$keepEndArenaClear(ServerWorld world, MobEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> ci) {
        if (EndEncounter.blocksSpawn(world, entity)) ci.setReturnValue(false);
    }
}
