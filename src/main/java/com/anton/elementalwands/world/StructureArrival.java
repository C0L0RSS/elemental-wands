package com.anton.elementalwands.world;

import com.anton.elementalwands.church.ChurchLayout;
import com.anton.elementalwands.church.GuardianChurchLocator;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;

/**
 * Where a /locate teleport link puts the player at a church or graveyard. Vanilla links keep the
 * player's own height ("~"), which buries them or drops them from the sky. Each structure names a
 * clear cell of its own template instead, so the link carries the floor's real height even before
 * the terrain there has generated.
 */
public final class StructureArrival {
    private StructureArrival() {}

    /** Arrival for the structure starting in the located chunk, or null for other structures. Loads that chunk's starts. */
    public static BlockPos find(ServerWorld world, RegistryEntry<Structure> structure, BlockPos located) {
        if (!(structure.value() instanceof HollowGraveyardStructure) && !structure.matchesId(GuardianChurchLocator.ID)) return null;
        var chunk = world.getChunk(located.getX() >> 4, located.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
        return of(world, structure, chunk.getStructureStart(structure.value()));
    }

    public static BlockPos of(ServerWorld world, RegistryEntry<Structure> structure, StructureStart start) {
        if (start == null || !start.hasChildren() || !(start.getChildren().getFirst() instanceof PoolStructurePiece piece)) return null;
        BlockPos spot;
        if (structure.value() instanceof HollowGraveyardStructure graveyard) spot = graveyard.arrival(piece);
        else if (structure.matchesId(GuardianChurchLocator.ID)) spot = ChurchLayout.arrival(piece);
        else return null;
        // Built ground shows what is really there: an older layout, or players' own changes.
        var ground = world.getChunk(spot.getX() >> 4, spot.getZ() >> 4, ChunkStatus.EMPTY, true);
        if (ground.getStatus().isAtLeast(ChunkStatus.FEATURES))
            spot = spot.withY(ground.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ()) + 1);
        return spot;
    }
}
