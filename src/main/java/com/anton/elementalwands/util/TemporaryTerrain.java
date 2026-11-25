package com.anton.elementalwands.util;

import com.anton.elementalwands.ElementalWandsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.world.chunk.WorldChunk;

/** Saved in the same chunk snapshot as the terrain it describes. */
final class TemporaryTerrain {
    record Cell(long pos, UUID placement, BlockState original, BlockState placed) {
        static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(Cell::pos),
                Uuids.CODEC.fieldOf("placement").forGetter(Cell::placement),
                BlockState.CODEC.fieldOf("original").forGetter(Cell::original),
                BlockState.CODEC.fieldOf("placed").forGetter(Cell::placed)
        ).apply(instance, Cell::new));

        Cell withPlacement(UUID owner) {
            return new Cell(pos, owner, original, placed);
        }
    }

    static final AttachmentType<TemporaryTerrain> ATTACHMENT = AttachmentRegistry.create(
            Identifier.of(ElementalWandsMod.MOD_ID, "temporary_terrain"), builder -> builder.persistent(
                    Cell.CODEC.listOf().xmap(TemporaryTerrain::new, terrain -> List.copyOf(terrain.cells.values()))));

    final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();

    private TemporaryTerrain(List<Cell> saved) {
        for (Cell cell : saved) cells.put(cell.pos(), cell);
    }

    static Cell get(WorldChunk chunk, long pos) {
        TemporaryTerrain terrain = chunk.getAttached(ATTACHMENT);
        return terrain == null ? null : terrain.cells.get(pos);
    }

    static void put(WorldChunk chunk, Cell cell) {
        chunk.getAttachedOrCreate(ATTACHMENT, () -> new TemporaryTerrain(List.of())).cells.put(cell.pos(), cell);
        chunk.markNeedsSaving();
    }

    static void remove(WorldChunk chunk, Cell cell) {
        TemporaryTerrain terrain = chunk.getAttached(ATTACHMENT);
        if (terrain == null || !terrain.cells.remove(cell.pos(), cell)) return;
        if (terrain.cells.isEmpty()) chunk.removeAttached(ATTACHMENT);
        else chunk.markNeedsSaving();
    }
}
