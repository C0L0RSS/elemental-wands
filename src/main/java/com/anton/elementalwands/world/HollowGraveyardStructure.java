package com.anton.elementalwands.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.EmptyPoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * The overworld Hollow graveyard: one rigid template seated flush on gentle, dry ground.
 *
 * <p>Vanilla jigsaw placement takes the height of a single column at the piece's centre, so on a
 * slope or cliff edge the whole yard followed that one sample. This structure surveys noise-only
 * ground heights on an 8-block grid over the rotated footprint plus an 8-block rim, at the chunk
 * centre and at spots up to two {@code search_step}s away. A spot is refused when any surveyed
 * column lies more than {@code max_floor_offset} blocks above or below the floor, when water or
 * lava covers the footprint, or when its centre is outside the structure's biomes. The floor is
 * the median ground height under the footprint. The nearest spot within two blocks everywhere is
 * taken; otherwise the one needing the least earthwork. A region with no suitable spot gets no
 * graveyard, and {@code /place structure} fails there too.
 *
 * <p>The piece reports its floor as the ground level, so the structure's terrain adaptation
 * ({@code beard_thin}) levels the land under and around the footprint to the floor itself rather
 * than to the template's bottom layer. After placement, cave carvers' air or fluid directly under
 * the floor is filled with dirt.
 *
 * <p>The graveyard generates in the last feature step, after trees, so its template clears any
 * that grew in the yard and {@link GraveyardBlight} can kill the woods around it. For that the
 * structure's box reaches {@link GraveyardBlight#REACH} blocks past the footprint, which makes
 * every chunk in the blight place it; the terrain beard still follows the piece alone.
 */
public final class HollowGraveyardStructure extends Structure {
    public static final MapCodec<HollowGraveyardStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            StructurePool.REGISTRY_CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.intRange(0, 64).fieldOf("floor_layer").forGetter(s -> s.floorLayer),
            Codec.intRange(0, 16).optionalFieldOf("max_floor_offset", 3).forGetter(s -> s.maxFloorOffset),
            Codec.intRange(0, 32).optionalFieldOf("search_step", 16).forGetter(s -> s.searchStep)
    ).apply(instance, HollowGraveyardStructure::new));

    /** Registered as {@code elementalwands:hollow_graveyard} by {@link ModWorldGen}. */
    public static final StructureType<HollowGraveyardStructure> TYPE = () -> CODEC;

    /** Survey spacing. Samples on a fixed world grid are shared between overlapping candidate spots. */
    private static final int GRID = 8;
    /** A spot whose ground is everywhere this close to the floor is taken without searching further. */
    private static final int GOOD_ENOUGH_OFFSET = 2;
    /** Deepest carved gap under the floor that post-placement fills. */
    private static final int FILL_DEPTH = 8;
    /** Candidate offsets in search steps, nearest to the chunk centre first. */
    private static final int[][] CANDIDATES = candidates(2);

    private final RegistryEntry<StructurePool> startPool;
    private final int floorLayer;
    private final int maxFloorOffset;
    private final int searchStep;

    public HollowGraveyardStructure(Config config, RegistryEntry<StructurePool> startPool, int floorLayer,
                                    int maxFloorOffset, int searchStep) {
        super(config);
        this.startPool = startPool;
        this.floorLayer = floorLayer;
        this.maxFloorOffset = maxFloorOffset;
        this.searchStep = searchStep;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        BlockRotation rotation = BlockRotation.random(context.random());
        StructurePoolElement element = startPool.value().getRandomElement(context.random());
        if (element == EmptyPoolElement.INSTANCE) return Optional.empty();
        StructureTemplateManager templates = context.structureTemplateManager();
        BlockBox local = element.getBoundingBox(templates, BlockPos.ORIGIN, rotation);
        ChunkPos chunk = context.chunkPos();
        HeightLimitView world = context.world();
        int seaLevel = context.chunkGenerator().getSeaLevel();
        Survey survey = new Survey(context);
        BlockBox bestFootprint = null;
        int bestFloor = 0;
        long bestScore = Long.MAX_VALUE;
        for (int[] candidate : CANDIDATES) {
            int originX = chunk.getCenterX() + candidate[0] * searchStep - (local.getMinX() + local.getMaxX()) / 2;
            int originZ = chunk.getCenterZ() + candidate[1] * searchStep - (local.getMinZ() + local.getMaxZ()) / 2;
            BlockBox footprint = local.offset(originX, 0, originZ);
            BlockPos centre = footprint.getCenter();
            // Terrain columns are costly: skip spots whose biome or coarse relief rules them out first.
            if (!survey.biomeAllows(centre.getX(), seaLevel, centre.getZ()) || !survey.coarselyLevel(footprint)) continue;
            int floor = survey.floor(footprint);
            long score = survey.score(footprint, floor, bestScore);
            if (score < 0) continue;
            int minY = floor - floorLayer - local.getMinY();
            if (minY < world.getBottomY() || minY + local.getBlockCountY() - 1 > world.getTopYInclusive()) continue;
            bestFootprint = footprint;
            bestFloor = floor;
            bestScore = score;
            if (survey.maxOffset <= GOOD_ENOUGH_OFFSET) break;
        }
        if (bestFootprint == null) return Optional.empty();
        BlockPos origin = new BlockPos(bestFootprint.getMinX() - local.getMinX(), bestFloor - floorLayer - local.getMinY(),
                bestFootprint.getMinZ() - local.getMinZ());
        BlockBox box = element.getBoundingBox(templates, origin, rotation);
        // Ground level is the layer above the floor: the terrain beard fills up to the floor itself.
        PoolStructurePiece piece = new PoolStructurePiece(templates, element, origin, floorLayer + 1, rotation, box,
                StructureLiquidSettings.APPLY_WATERLOGGING);
        BlockPos centre = new BlockPos(bestFootprint.getCenter().getX(), bestFloor, bestFootprint.getCenter().getZ());
        return Optional.of(new StructurePosition(centre, collector -> collector.addPiece(piece)));
    }

    /** The whole blight, not just the piece; it also covers the terrain beard's usual 12 blocks. */
    @Override
    public BlockBox expandBoxIfShouldAdaptNoise(BlockBox box) {
        return box.expand(Math.max(12, GraveyardBlight.REACH));
    }

    /**
     * Carvers run after the terrain beard and can hollow out the ground under the one-block floor.
     * Fill that air or fluid with dirt, column by column, until natural ground is reached. Then
     * blight this chunk's share of the woods around the yard.
     */
    @Override
    public void postPlace(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator,
                          Random random, BlockBox box, ChunkPos chunkPos, StructurePiecesList pieces) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolStructurePiece pool)) continue;
            BlockBox footprint = pool.getBoundingBox();
            int floor = footprint.getMinY() + pool.getGroundLevelDelta() - 1;
            for (int x = Math.max(box.getMinX(), footprint.getMinX()); x <= Math.min(box.getMaxX(), footprint.getMaxX()); x++) {
                for (int z = Math.max(box.getMinZ(), footprint.getMinZ()); z <= Math.min(box.getMaxZ(), footprint.getMaxZ()); z++) {
                    if (world.getBlockState(pos.set(x, floor, z)).isAir()) continue;
                    for (int y = floor - 1; y >= floor - FILL_DEPTH; y--) {
                        BlockState state = world.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && state.getFluidState().isEmpty() && !state.isReplaceable()) break;
                        world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 2);
                    }
                }
            }
            GraveyardBlight.apply(world, box, footprint, floor);
        }
    }

    /** Noise-only ground heights, cached per column across the candidate spots of one attempt. */
    private final class Survey {
        private final Context context;
        private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap solids = new Long2IntOpenHashMap();
        /** Largest ground offset from the floor seen by the last successful {@link #score}. */
        int maxOffset;

        Survey(Context context) {
            this.context = context;
        }

        /** Top of whatever covers the column, water and lava included. */
        int top(int x, int z) {
            return tops.computeIfAbsent(ChunkPos.toLong(x, z), key -> context.chunkGenerator().getHeightOnGround(x, z,
                    Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig()) - 1);
        }

        /** True when water or lava lies on top of the column. */
        boolean wet(int x, int z) {
            int solid = solids.computeIfAbsent(ChunkPos.toLong(x, z), key -> context.chunkGenerator().getHeightOnGround(x, z,
                    Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig()) - 1);
            return solid != top(x, z);
        }

        /** Relief on a double-spaced grid: more than twice the allowed offset can never pass. */
        boolean coarselyLevel(BlockBox footprint) {
            BlockBox area = footprint.expand(GRID);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int x = first(area.getMinX(), 2 * GRID); x <= area.getMaxX(); x += 2 * GRID) {
                for (int z = first(area.getMinZ(), 2 * GRID); z <= area.getMaxZ(); z += 2 * GRID) {
                    int top = top(x, z);
                    min = Math.min(min, top);
                    max = Math.max(max, top);
                    if (max - min > 2 * maxFloorOffset) return false;
                }
            }
            return true;
        }

        /** Median ground height under the footprint. */
        int floor(BlockBox footprint) {
            int[] heights = new int[64];
            int n = 0;
            for (int x = first(footprint.getMinX(), GRID); x <= footprint.getMaxX(); x += GRID) {
                for (int z = first(footprint.getMinZ(), GRID); z <= footprint.getMaxZ(); z += GRID) {
                    if (n == heights.length) heights = Arrays.copyOf(heights, n * 2);
                    heights[n++] = top(x, z);
                }
            }
            if (n == 0) return top(footprint.getCenter().getX(), footprint.getCenter().getZ());
            Arrays.sort(heights, 0, n);
            return heights[n / 2];
        }

        /** Earthwork cost (sum of squared offsets), or -1 when the spot is refused or cannot beat {@code bound}. */
        long score(BlockBox footprint, int floor, long bound) {
            BlockPos centre = footprint.getCenter();
            if (!biomeAllows(centre.getX(), floor, centre.getZ())) return -1;
            BlockBox area = footprint.expand(GRID);
            long score = 0;
            int worst = 0;
            for (int x = first(area.getMinX(), GRID); x <= area.getMaxX(); x += GRID) {
                for (int z = first(area.getMinZ(), GRID); z <= area.getMaxZ(); z += GRID) {
                    int offset = Math.abs(top(x, z) - floor);
                    if (offset > maxFloorOffset) return -1;
                    worst = Math.max(worst, offset);
                    score += (long) offset * offset;
                    if (score >= bound) return -1;
                }
            }
            for (int x = first(footprint.getMinX(), GRID); x <= footprint.getMaxX(); x += GRID)
                for (int z = first(footprint.getMinZ(), GRID); z <= footprint.getMaxZ(); z += GRID)
                    if (wet(x, z)) return -1;
            maxOffset = worst;
            return score;
        }

        /** Same test vanilla applies to the returned position; checked here so a bad spot is skipped, not the region. */
        boolean biomeAllows(int x, int y, int z) {
            return context.biomePredicate().test(context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y),
                    BiomeCoords.fromBlock(z), context.noiseConfig().getMultiNoiseSampler()));
        }
    }

    /** The first multiple of {@code step} at or above {@code min}. */
    private static int first(int min, int step) {
        return Math.floorDiv(min + step - 1, step) * step;
    }

    private static int[][] candidates(int reach) {
        int side = 2 * reach + 1;
        int[][] out = new int[side * side][];
        int n = 0;
        for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) out[n++] = new int[]{x, z};
        Arrays.sort(out, Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        return out;
    }

    @Override
    public StructureType<?> getType() {
        return TYPE;
    }
}
