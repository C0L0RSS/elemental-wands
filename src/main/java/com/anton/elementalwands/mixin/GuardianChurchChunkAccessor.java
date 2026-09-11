package com.anton.elementalwands.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkManager.class)
public interface GuardianChurchChunkAccessor {
    // The public getChunkFutureSyncOnMainThread pumps tasks until generation finishes.
    // Invoke the scheduling-only method, then poll its future on later server ticks.
    @Invoker("getChunkFuture")
    CompletableFuture<OptionalChunk<Chunk>> elementalwands$requestChunk(int x,int z,ChunkStatus status,boolean create);
}
