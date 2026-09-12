package com.anton.elementalwands.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityInvoker {
    /** Create a spell projectile without vanilla spawnFromBlock's terrain removal. */
    @Invoker("<init>")
    static FallingBlockEntity elementalwands$create(World world, double x, double y, double z, BlockState state) {
        throw new AssertionError("Mixin constructor invoker was not applied");
    }
}
