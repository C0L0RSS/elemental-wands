package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.util.TemporaryTerrain.Cell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public final class TemporaryBlockManager {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    // Recovery must not load neighboring chunks or break a tall plant between its two writes.
    private static final int QUIET_WRITE = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    public static final class TemporaryPlacement {
        private final UUID id = UUID.randomUUID();
        private final ServerWorld world;
        private final UUID caster;
        private final int expires;
        private final Set<BlockPos> positions = new LinkedHashSet<>();
        private final Set<BlockPos> plantHalves = new LinkedHashSet<>();

        private TemporaryPlacement(ServerWorld world, UUID caster, int durationTicks) {
            this.world = world;
            this.caster = caster;
            this.expires = world.getServer().getTicks() + durationTicks;
        }

        public int placedCount() {
            return positions.size();
        }

        public boolean isEmpty() {
            return positions.isEmpty();
        }
    }

    private static final class WorldPlacements {
        final Map<UUID, TemporaryPlacement> active = new HashMap<>();
        final Map<WorldChunk, Integer> recover = new HashMap<>();
    }

    private static final Map<ServerWorld, WorldPlacements> WORLDS = new HashMap<>();

    private TemporaryBlockManager() {}

    public static void init() {
        // Fabric must know this type before it starts decoding any saved chunk.
        var terrainAttachment = TemporaryTerrain.ATTACHMENT;
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (chunk.hasAttached(terrainAttachment)) scheduleRecovery(world, chunk, 0);
        });
        // A chunk may become accessible again before Minecraft completes its unload.
        ServerChunkEvents.CHUNK_LEVEL_TYPE_CHANGE.register((world, chunk, oldLevel, newLevel) -> {
            if (!oldLevel.isAfter(ChunkLevelType.FULL) && newLevel.isAfter(ChunkLevelType.FULL)
                    && chunk.hasAttached(terrainAttachment)) scheduleRecovery(world, chunk, 0);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            WorldPlacements placements = WORLDS.get(world);
            if (placements != null) placements.recover.remove(chunk);
        });
        ServerTickEvents.START_WORLD_TICK.register(TemporaryBlockManager::recoverLoadedChunks);
        ServerTickEvents.END_WORLD_TICK.register(TemporaryBlockManager::expirePlacements);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                WorldPlacements placements = WORLDS.get(world);
                if (placements == null) continue;
                for (TemporaryPlacement placement : List.copyOf(placements.active.values())) {
                    restoreTemporaryBlocks(world, placement);
                }
                recoverLoadedChunks(world);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.keySet().removeIf(world -> world.getServer() == server));
    }

    public static int placeTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions, BlockState placedState,
            int durationTicks, Predicate<BlockState> canReplace) {
        return placeTrackedTemporaryBlocks(world, positions, placedState, durationTicks, canReplace).placedCount();
    }

    public static TemporaryPlacement placeTrackedTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions,
            BlockState placedState, int durationTicks, Predicate<BlockState> canReplace) {
        return placeTrackedTemporaryBlocks(world, positions, placedState, durationTicks, canReplace, null);
    }

    public static TemporaryPlacement placeTrackedTemporaryBlocks(ServerWorld world, Iterable<BlockPos> positions,
            BlockState placedState, int durationTicks, Predicate<BlockState> canReplace, UUID caster) {
        TemporaryPlacement placement = startPlacement(world, durationTicks, caster);
        for (BlockPos pos : positions) placeTemporaryBlock(world, placement, pos, placedState, canReplace);
        return placement;
    }

    /** A cast may add to the same placement over several ticks, as Titan Dome forms. */
    public static TemporaryPlacement startPlacement(ServerWorld world, int durationTicks, UUID caster) {
        return new TemporaryPlacement(world, caster, durationTicks);
    }

    public static boolean placeTemporaryBlock(ServerWorld world, TemporaryPlacement placement, BlockPos pos,
            BlockState placedState, Predicate<BlockState> canReplace) {
        return placeTemporaryBlock(world, placement, pos, placedState, canReplace,
                () -> write(world, pos, placedState, Block.NOTIFY_ALL));
    }

    /** Preserve vanilla's wrapped write so other explosion hooks still see the placement. */
    public static boolean placeTrackedExplosionFire(ServerWorld world, BlockPos pos, BlockState state, int durationTicks,
            UUID caster, BooleanSupplier blockWrite) {
        return placeTemporaryBlock(world, startPlacement(world, durationTicks, caster), pos, state, BlockState::isAir,
                blockWrite);
    }

    private static boolean placeTemporaryBlock(ServerWorld world, TemporaryPlacement placement, BlockPos pos,
            BlockState placedState, Predicate<BlockState> canReplace, BooleanSupplier blockWrite) {
        if (placement.world != world || !world.isInBuildLimit(pos) || placedState.hasBlockEntity()) return false;
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        long key = pos.asLong();
        Cell previous = TemporaryTerrain.get(chunk, key);
        WorldPlacements placements = WORLDS.computeIfAbsent(world, ignored -> new WorldPlacements());
        if (previous != null && !placements.active.containsKey(previous.placement())) {
            if (!restore(world, chunk, previous, QUIET_WRITE)) return false;
            previous = TemporaryTerrain.get(chunk, key);
        }

        BlockState current = chunk.getBlockState(pos);
        if (previous != null && previous.placed() != current) {
            TemporaryTerrain.remove(chunk, previous);
            previous = null;
        }
        if (current == placedState || current.hasBlockEntity() || !canReplace.test(current)
                || (previous != null && isPlantReservation(previous))) return false;

        BlockState original = previous == null ? current : previous.original();
        Cell claim = new Cell(key, placement.id, original, placedState);
        Cell companion = null;
        if (original.getBlock() instanceof TallPlantBlock) {
            BlockPos otherPos = plantOtherHalf(pos, original);
            BlockState other = chunk.getBlockState(otherPos);
            if (previous == null) {
                if (TemporaryTerrain.get(chunk, otherPos.asLong()) != null || !isOtherHalf(original, other)) return false;
                companion = new Cell(otherPos.asLong(), placement.id, other, AIR);
                TemporaryTerrain.put(chunk, companion);
                if (!write(world, otherPos, AIR, QUIET_WRITE)) {
                    TemporaryTerrain.remove(chunk, companion);
                    return false;
                }
            } else {
                companion = TemporaryTerrain.get(chunk, otherPos.asLong());
                if (companion != null && (!companion.placement().equals(previous.placement())
                        || !isPlantReservation(companion) || companion.placed() != other)) companion = null;
            }
        }

        // Stage ownership before the write. The chunk can only save the pair together.
        TemporaryTerrain.put(chunk, claim);
        if (!blockWrite.getAsBoolean() || chunk.getBlockState(pos) != placedState) {
            TemporaryTerrain.remove(chunk, claim);
            if (previous != null && chunk.getBlockState(pos) == previous.placed()) TemporaryTerrain.put(chunk, previous);
            if (previous == null && companion != null) restore(world, chunk, companion, QUIET_WRITE);
            return false;
        }
        if (previous != null && companion != null && TemporaryTerrain.get(chunk, companion.pos()) == companion) {
            TemporaryTerrain.put(chunk, companion.withPlacement(placement.id));
        }
        placements.active.put(placement.id, placement);
        placement.positions.add(pos.toImmutable());
        if (companion != null) placement.plantHalves.add(BlockPos.fromLong(companion.pos()));
        return true;
    }

    /** Called after an actual chunk write, including changes made by players and vanilla. */
    public static void onBlockChanged(WorldChunk chunk, BlockPos pos) {
        Cell cell = TemporaryTerrain.get(chunk, pos.asLong());
        if (cell != null && chunk.getBlockState(pos) != cell.placed()) TemporaryTerrain.remove(chunk, cell);
    }

    /** Includes anonymous placements: a null caster is not proof that terrain is unowned. */
    public static boolean isTracked(ServerWorld world, BlockPos pos) {
        Cell cell = presentCell(world, pos);
        return cell != null && !isPlantReservation(cell);
    }

    public static boolean owns(ServerWorld world, TemporaryPlacement placement, BlockPos pos) {
        Cell cell = presentCell(world, pos);
        return cell != null && placement.world == world && cell.placement().equals(placement.id);
    }

    public static UUID casterAt(ServerWorld world, BlockPos pos) {
        Cell cell = presentCell(world, pos);
        WorldPlacements placements = WORLDS.get(world);
        if (cell == null || placements == null) return null;
        TemporaryPlacement active = placements.active.get(cell.placement());
        return active == null ? null : active.caster;
    }

    /** Keep legitimate animation changes in the same ownership record. */
    public static void updateTemporaryBlock(ServerWorld world, TemporaryPlacement placement, BlockPos pos,
            BlockState state, int flags) {
        WorldChunk chunk = loaded(world, pos);
        if (chunk == null || placement.world != world) return;
        Cell cell = TemporaryTerrain.get(chunk, pos.asLong());
        if (cell == null || !cell.placement().equals(placement.id) || chunk.getBlockState(pos) != cell.placed()
                || state == cell.placed() || !state.isOf(cell.placed().getBlock())) return;
        Cell updated = new Cell(cell.pos(), cell.placement(), cell.original(), state);
        TemporaryTerrain.put(chunk, updated);
        if (!write(world, pos, state, flags)) {
            TemporaryTerrain.remove(chunk, updated);
            if (chunk.getBlockState(pos) == cell.placed()) TemporaryTerrain.put(chunk, cell);
        }
    }

    public static List<BlockPos> placementPositions(ServerWorld world, TemporaryPlacement placement) {
        if (placement.world != world) return List.of();
        return placement.positions.stream().filter(pos -> owns(world, placement, pos)).toList();
    }

    public static void restoreTemporaryBlock(ServerWorld world, TemporaryPlacement placement, BlockPos pos) {
        if (placement == null || placement.world != world) return;
        WorldChunk chunk = loaded(world, pos);
        if (chunk == null) return; // The chunk's persisted receipt will be checked on its next load.
        Cell cell = TemporaryTerrain.get(chunk, pos.asLong());
        if (cell != null && cell.placement().equals(placement.id) && !restore(world, chunk, cell, Block.NOTIFY_ALL)) {
            scheduleRecovery(world, chunk, 20);
        }
    }

    public static void restoreTemporaryBlocks(ServerWorld world, TemporaryPlacement placement) {
        if (placement == null || placement.world != world) return;
        WorldPlacements placements = WORLDS.get(world);
        if (placements != null) placements.active.remove(placement.id);
        restorePlacementCells(world, placement);
    }

    private static Cell presentCell(ServerWorld world, BlockPos pos) {
        WorldChunk chunk = loaded(world, pos);
        Cell cell = chunk == null ? null : TemporaryTerrain.get(chunk, pos.asLong());
        return cell != null && chunk.getBlockState(pos) == cell.placed() ? cell : null;
    }

    private static WorldChunk loaded(ServerWorld world, BlockPos pos) {
        return world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void expirePlacements(ServerWorld world) {
        WorldPlacements placements = WORLDS.get(world);
        if (placements == null) return;
        int now = world.getServer().getTicks();
        var iterator = placements.active.values().iterator();
        while (iterator.hasNext()) {
            TemporaryPlacement placement = iterator.next();
            if (now < placement.expires) continue;
            iterator.remove();
            restorePlacementCells(world, placement);
        }
    }

    private static void restorePlacementCells(ServerWorld world, TemporaryPlacement placement) {
        for (BlockPos pos : placement.positions) restoreTemporaryBlock(world, placement, pos);
        for (BlockPos pos : placement.plantHalves) restoreTemporaryBlock(world, placement, pos);
    }

    private static void scheduleRecovery(ServerWorld world, WorldChunk chunk, int delay) {
        WORLDS.computeIfAbsent(world, ignored -> new WorldPlacements()).recover
                .merge(chunk, world.getServer().getTicks() + delay, Math::min);
    }

    private static void recoverLoadedChunks(ServerWorld world) {
        WorldPlacements placements = WORLDS.get(world);
        if (placements == null || placements.recover.isEmpty()) return;
        int now = world.getServer().getTicks();
        List<WorldChunk> ready = new ArrayList<>();
        placements.recover.entrySet().removeIf(entry -> {
            if (entry.getValue() > now) return false;
            ready.add(entry.getKey());
            return true;
        });
        for (WorldChunk chunk : ready) {
            TemporaryTerrain terrain = chunk.getAttached(TemporaryTerrain.ATTACHMENT);
            if (terrain == null) continue;
            if (world.getChunkManager().getWorldChunk(chunk.getPos().x, chunk.getPos().z) != chunk) {
                scheduleRecovery(world, chunk, 20);
                continue;
            }
            for (Cell cell : List.copyOf(terrain.cells.values())) {
                if (!placements.active.containsKey(cell.placement()) && !restore(world, chunk, cell, QUIET_WRITE)) {
                    scheduleRecovery(world, chunk, 20);
                }
            }
        }
    }

    private static boolean restore(ServerWorld world, WorldChunk chunk, Cell cell, int flags) {
        if (TemporaryTerrain.get(chunk, cell.pos()) != cell) return true;
        BlockPos pos = BlockPos.fromLong(cell.pos());
        if (pos.getX() >> 4 != chunk.getPos().x || pos.getZ() >> 4 != chunk.getPos().z || world.isOutOfHeightLimit(pos)
                || chunk.getBlockState(pos) != cell.placed()) {
            TemporaryTerrain.remove(chunk, cell);
            return true;
        }
        if (!(cell.original().getBlock() instanceof TallPlantBlock)) {
            return restoreState(world, chunk, cell, pos, cell.original(), flags);
        }

        BlockPos otherPos = plantOtherHalf(pos, cell.original());
        BlockState other = chunk.getBlockState(otherPos);
        Cell companion = TemporaryTerrain.get(chunk, otherPos.asLong());
        if (companion != null && cell.placement().equals(companion.placement()) && other == companion.placed()
                && isOtherHalf(cell.original(), companion.original())) {
            boolean lower = cell.original().get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER;
            Cell bottom = lower ? cell : companion;
            Cell top = lower ? companion : cell;
            BlockPos bottomPos = lower ? pos : otherPos;
            boolean supported = bottom.original().canPlaceAt(world, bottomPos);
            return restoreState(world, chunk, bottom, bottomPos, supported ? bottom.original() : AIR, QUIET_WRITE)
                    && restoreState(world, chunk, top, bottomPos.up(), supported ? top.original() : AIR, QUIET_WRITE);
        }
        // The other half may already have been restored, or may have been replaced by an edit.
        BlockState original = companion == null && isOtherHalf(cell.original(), other) ? cell.original() : AIR;
        return restoreState(world, chunk, cell, pos, original, QUIET_WRITE);
    }

    private static boolean restoreState(ServerWorld world, WorldChunk chunk, Cell cell, BlockPos pos,
            BlockState original, int flags) {
        if (original != cell.placed() && !write(world, pos, original, flags)) return false;
        TemporaryTerrain.remove(chunk, cell);
        return true;
    }

    private static boolean isPlantReservation(Cell cell) {
        return cell.placed().isAir() && cell.original().getBlock() instanceof TallPlantBlock;
    }

    private static BlockPos plantOtherHalf(BlockPos pos, BlockState state) {
        return state.get(TallPlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
    }

    private static boolean isOtherHalf(BlockState state, BlockState other) {
        return other.isOf(state.getBlock()) && other.get(TallPlantBlock.HALF) != state.get(TallPlantBlock.HALF);
    }

    private static boolean write(ServerWorld world, BlockPos pos, BlockState state, int flags) {
        return GuardianArenaManager.setTemporarySpellBlock(world, pos, state, flags);
    }
}
