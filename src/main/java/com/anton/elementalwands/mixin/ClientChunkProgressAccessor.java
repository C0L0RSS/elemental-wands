package com.anton.elementalwands.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientChunkLoadProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's own "the new world has loaded" signal: the handler drops its progress once the player's chunk is ready. */
@Mixin(ClientPlayNetworkHandler.class)
public interface ClientChunkProgressAccessor {
    @Accessor("chunkLoadProgress")
    ClientChunkLoadProgress elementalwands$chunkLoadProgress();
}
