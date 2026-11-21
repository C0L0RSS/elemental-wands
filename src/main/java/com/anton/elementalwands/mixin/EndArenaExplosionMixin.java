package com.anton.elementalwands.mixin;

import com.anton.elementalwands.end.EndArenaManager;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Explosions destroy nothing, and start no fire, in the End arena's footprint while its boss is
 * undefeated. The list is filtered where it is used: {@code GuardianChurchExplosionMixin} already
 * replaces {@code getBlocksToDestroy}'s result at its return, which would end the method before a
 * second handler there ran.
 */
@Mixin(ExplosionImpl.class)
public abstract class EndArenaExplosionMixin {
    @ModifyVariable(method = "destroyBlocks", at = @At("HEAD"), argsOnly = true)
    private List<BlockPos> elementalwands$keepEndArena(List<BlockPos> blocks) { return elementalwands$outside(blocks); }

    @ModifyVariable(method = "createFire", at = @At("HEAD"), argsOnly = true)
    private List<BlockPos> elementalwands$noFireInEndArena(List<BlockPos> blocks) { return elementalwands$outside(blocks); }

    private List<BlockPos> elementalwands$outside(List<BlockPos> blocks) {
        var world = ((ExplosionImpl) (Object) this).getWorld();
        if (blocks.isEmpty() || blocks.stream().noneMatch(p -> EndArenaManager.keepsTerrain(world, p))) return blocks;
        return blocks.stream().filter(p -> !EndArenaManager.keepsTerrain(world, p))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
