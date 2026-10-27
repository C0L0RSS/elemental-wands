package com.anton.elementalwands.world;

import com.anton.elementalwands.summit.StormAerieLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.util.math.MathHelper;
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
 * The Storm Roc's aerie: an abandoned Windcaller gong in its frame, seated on the summit of an
 * Overworld peak. The template carries no ground: worldgen levels the real summit to its box, so the
 * biome's own surface covers it (snow and ice on frozen and jagged peaks, bare stone on stony ones).
 *
 * <p>Vanilla jigsaw placement would stand it wherever the region's chunk falls, usually on a
 * mountainside. This structure surveys noise-only ground heights on an 8-block grid out to
 * {@code search_radius} blocks around the chunk, in columns whose surface biome is one of the
 * structure's. Columns no lower than their eight neighbours are summits, tried highest first: each
 * is refined to the highest column on a 4-block grid within 8 blocks, the template is centred there,
 * and every rotation's footprint is surveyed on that grid. The floor is the median ground under the
 * footprint, raised to at most {@code summit_cut} below its highest column and lowered to at most
 * {@code max_drop} above its lowest, so a peak loses only its tip and the skirt never stands far
 * over a fall. A rotation is refused when both limits can't hold, the floor is below
 * {@code min_floor}, water or lava covers the footprint, the centre's biome is wrong, or ground
 * within 8 blocks of the footprint rises above its highest column (a mountainside, not a summit).
 * The rotation needing the least earthwork wins. A region with no passing summit gets no aerie,
 * and {@code /place structure} fails there too.
 *
 * <p>The piece reports the layer above its floor as the ground level, so {@code beard_thin} clears
 * the box down to the floor and fills it up to it before the surface is painted, and eases the land
 * around it toward the floor with a soft falloff (a box beard left a square plinth). After
 * placement a widening cone of rock fills any air left under the summit down to the peak.
 *
 * <p>The aerie generates in the last feature step, after lakes, springs, ores and plants, so none
 * of them lands on the summit. Only {@code freeze_top_layer} follows it, so snowy peaks lay snow
 * layers on its open floor.
 */
public final class StormAerieStructure extends Structure {
    public static final MapCodec<StormAerieStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            StructurePool.REGISTRY_CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.intRange(0, 64).optionalFieldOf("floor_layer", StormAerieLayout.FLOOR).forGetter(s -> s.floorLayer),
            Codec.intRange(0, 96).optionalFieldOf("search_radius", 48).forGetter(s -> s.searchRadius),
            Codec.intRange(0, 32).optionalFieldOf("summit_cut", 8).forGetter(s -> s.summitCut),
            Codec.intRange(0, 64).optionalFieldOf("max_drop", 24).forGetter(s -> s.maxDrop),
            Codec.INT.optionalFieldOf("min_floor", 140).forGetter(s -> s.minFloor)
    ).apply(instance, StormAerieStructure::new));

    /** Registered as {@code elementalwands:storm_aerie} by {@link ModWorldGen}. */
    public static final StructureType<StormAerieStructure> TYPE = () -> CODEC;

    /** Summit search spacing. Both grids are fixed world grids, so their samples are shared. */
    private static final int GRID = 8;
    /** Refinement and footprint spacing: jagged peaks change too quickly for the coarse grid. */
    private static final int FINE = 4;
    /** Summits tried per region, highest first. */
    private static final int MAX_SUMMITS = 3;
    /** The fill reaches this far past {@code max_drop}, for dips between footprint samples. */
    private static final int FILL_MARGIN = 12;
    /**
     * The rock cone under the summit: about the box's half width at the floor, then widening this much
     * per block down until it is this many blocks wider, where its flank starts to bend steeper.
     */
    private static final double CONE_TOP = 7.5, CONE_SPREAD = .7, CONE_KNEE = 6;
    /** How far past the footprint the cone, and so the structure's box, reaches. */
    private static final int CONE_REACH = 16;

    private final RegistryEntry<StructurePool> startPool;
    private final int floorLayer;
    private final int searchRadius;
    private final int summitCut;
    private final int maxDrop;
    private final int minFloor;

    public StormAerieStructure(Config config, RegistryEntry<StructurePool> startPool, int floorLayer, int searchRadius,
                               int summitCut, int maxDrop, int minFloor) {
        super(config);
        this.startPool = startPool;
        this.floorLayer = floorLayer;
        this.searchRadius = searchRadius;
        this.summitCut = summitCut;
        this.maxDrop = maxDrop;
        this.minFloor = minFloor;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        BlockRotation first = BlockRotation.random(context.random());
        StructurePoolElement element = startPool.value().getRandomElement(context.random());
        if (element == EmptyPoolElement.INSTANCE) return Optional.empty();
        StructureTemplateManager templates = context.structureTemplateManager();
        Survey survey = new Survey(context);
        List<int[]> tried = new ArrayList<>();
        Spot best = null;
        for (int[] seed : survey.seeds()) {
            if (tried.size() == MAX_SUMMITS) break;
            int[] summit = survey.refine(seed[0], seed[1]);
            // Neighbouring seeds on one broad top refine to the same summit; survey it once.
            if (tried.stream().anyMatch(t -> Math.abs(t[0] - summit[0]) <= GRID && Math.abs(t[1] - summit[1]) <= GRID)) continue;
            tried.add(summit);
            BlockRotation rotation = first;
            for (int turn = 0; turn < 4; turn++, rotation = rotation.rotate(BlockRotation.CLOCKWISE_90)) {
                Spot spot = survey.spot(element.getBoundingBox(templates, BlockPos.ORIGIN, rotation), rotation, summit[0], summit[1]);
                if (spot != null && (best == null || spot.score() < best.score())) best = spot;
            }
            if (best != null) break;
        }
        if (best == null) return Optional.empty();
        BlockBox local = best.local(), footprint = best.footprint();
        BlockPos origin = new BlockPos(footprint.getMinX() - local.getMinX(), best.floor() - floorLayer - local.getMinY(),
                footprint.getMinZ() - local.getMinZ());
        BlockBox box = element.getBoundingBox(templates, origin, best.rotation());
        // Ground level is the layer above the floor: the beard clears from there up and fills the floor itself.
        PoolStructurePiece piece = new PoolStructurePiece(templates, element, origin, floorLayer + 1, best.rotation(), box,
                StructureLiquidSettings.APPLY_WATERLOGGING);
        BlockPos centre = new BlockPos(footprint.getCenter().getX(), best.floor(), footprint.getCenter().getZ());
        return Optional.of(new StructurePosition(centre, collector -> collector.addPiece(piece)));
    }

    /** The cone and the beard's usual 12 blocks, so every chunk the cone crosses builds its share. */
    @Override
    public BlockBox expandBoxIfShouldAdaptNoise(BlockBox box) {
        return box.expand(Math.max(12, CONE_REACH));
    }

    /**
     * The beard fills just the first few blocks under the floor. Where the peak falls away further,
     * the summit stands on a cone of rock: under the box's middle it starts at the floor, and it
     * widens as it goes down, so it meets the slope as a steep flank rather than a square wall. Only
     * air and fluid inside the cone are filled, so the mountain stays wherever it is already wider,
     * and caves, overhangs and crusts under it close.
     *
     * <p>The flank keeps {@code CONE_SPREAD} under the rim, where it closes the beard's crusts, and past
     * the knee bends steeper with depth so that its widest ring reaches the deepest fill just inside
     * the area it may build in. A straight flank ran out of room on steep peaks and was cut off there,
     * leaving a sheer wall of rock 16 blocks out.
     */
    @Override
    public void postPlace(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator,
                          Random random, BlockBox box, ChunkPos chunkPos, StructurePiecesList pieces) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolStructurePiece pool)) continue;
            BlockBox footprint = pool.getBoundingBox(), area = footprint.expand(CONE_REACH);
            int floor = footprint.getMinY() + pool.getGroundLevelDelta() - 1;
            // Depth below the floor {@code out} blocks past the cone's top: out / SPREAD, plus
            // bend * (out - KNEE)^2 past the knee, reaching the deepest fill at the widest ring that fits.
            double widest = Math.min(footprint.getBlockCountX(), footprint.getBlockCountZ()) / 2.0 + CONE_REACH - CONE_TOP;
            int fill = maxDrop + FILL_MARGIN;
            double bend = widest <= CONE_KNEE ? 0 : Math.max(0, (fill - widest / CONE_SPREAD) / ((widest - CONE_KNEE) * (widest - CONE_KNEE)));
            int deepest = Math.max(world.getBottomY(), floor - (int) Math.min(fill, coneDepth(widest, bend)));
            double cx = (footprint.getMinX() + footprint.getMaxX() + 1) / 2.0, cz = (footprint.getMinZ() + footprint.getMaxZ() + 1) / 2.0;
            for (int x = Math.max(box.getMinX(), area.getMinX()); x <= Math.min(box.getMaxX(), area.getMaxX()); x++) {
                for (int z = Math.max(box.getMinZ(), area.getMinZ()); z <= Math.min(box.getMaxZ(), area.getMaxZ()); z++) {
                    double out = Math.hypot(x + .5 - cx, z + .5 - cz) - CONE_TOP;
                    if (out > widest) continue;
                    for (int y = floor - (out <= 0 ? 0 : (int) Math.ceil(coneDepth(out, bend))); y >= deepest; y--) {
                        BlockState state = world.getBlockState(pos.set(x, y, z));
                        if (state.isAir() || !state.getFluidState().isEmpty()) world.setBlockState(pos, rock(x, y, z), 2);
                    }
                }
            }
        }
    }

    /** How far below the floor the cone's flank is, this many blocks past its top. */
    private static double coneDepth(double out, double bend) {
        double past = Math.max(0, out - CONE_KNEE);
        return out / CONE_SPREAD + bend * past * past;
    }

    /** Stone streaked with andesite and tuff in three-block bands, fixed by position so chunk seams match. */
    private static BlockState rock(int x, int y, int z) {
        int n = (int) Math.floorMod(MathHelper.hashCode(x, Math.floorDiv(y, 3), z), 10L);
        return (n < 2 ? Blocks.ANDESITE : n == 2 ? Blocks.TUFF : Blocks.STONE).getDefaultState();
    }

    /** One rotation of the template centred on a summit, with its floor and earthwork. */
    private record Spot(BlockRotation rotation, BlockBox local, BlockBox footprint, int floor, long score) {}

    /** Noise-only ground heights, cached per column across the summits and rotations of one attempt. */
    private final class Survey {
        private final Context context;
        private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap solids = new Long2IntOpenHashMap();

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

        /** Coarse columns in the search radius whose surface biome fits and no neighbour is higher, highest first. */
        List<int[]> seeds() {
            ChunkPos chunk = context.chunkPos();
            int cx = chunk.getCenterX(), cz = chunk.getCenterZ();
            List<int[]> seeds = new ArrayList<>();
            // Biomes are cheap next to terrain columns: only fitting columns are measured.
            for (int x = first(cx - searchRadius, GRID); x <= cx + searchRadius; x += GRID)
                for (int z = first(cz - searchRadius, GRID); z <= cz + searchRadius; z += GRID)
                    if (surfaceAllows(x, z)) seeds.add(new int[]{x, z, top(x, z)});
            seeds.removeIf(s -> !highest(s[0], s[1], s[2]));
            seeds.sort(Comparator.<int[]>comparingInt(s -> -s[2])
                    .thenComparingLong(s -> (long) (s[0] - cx) * (s[0] - cx) + (long) (s[1] - cz) * (s[1] - cz))
                    .thenComparingInt(s -> s[0]).thenComparingInt(s -> s[1]));
            return seeds;
        }

        /** True when none of the column's eight coarse neighbours, in any biome, is higher. */
        private boolean highest(int x, int z, int top) {
            for (int dx = -GRID; dx <= GRID; dx += GRID)
                for (int dz = -GRID; dz <= GRID; dz += GRID)
                    if ((dx != 0 || dz != 0) && top(x + dx, z + dz) > top) return false;
            return true;
        }

        /** The highest fitting column on the fine grid within one coarse step of a seed. */
        int[] refine(int x, int z) {
            int[] best = {x, z};
            int bestTop = top(x, z);
            for (int dx = -GRID; dx <= GRID; dx += FINE) {
                for (int dz = -GRID; dz <= GRID; dz += FINE) {
                    int top = top(x + dx, z + dz);
                    if (top <= bestTop || !surfaceAllows(x + dx, z + dz)) continue;
                    best = new int[]{x + dx, z + dz};
                    bestTop = top;
                }
            }
            return best;
        }

        /** The template centred on a summit in one rotation, or null when that spot is refused. */
        Spot spot(BlockBox local, BlockRotation rotation, int x, int z) {
            BlockBox footprint = local.offset(x - (local.getMinX() + local.getMaxX()) / 2, 0,
                    z - (local.getMinZ() + local.getMaxZ()) / 2);
            BlockPos centre = footprint.getCenter();
            int[] heights = new int[64];
            int n = 0, top = Integer.MIN_VALUE, low = Integer.MAX_VALUE;
            for (int sx = first(footprint.getMinX(), FINE); sx <= footprint.getMaxX(); sx += FINE) {
                for (int sz = first(footprint.getMinZ(), FINE); sz <= footprint.getMaxZ(); sz += FINE) {
                    if (n == heights.length) heights = Arrays.copyOf(heights, n * 2);
                    heights[n++] = top(sx, sz);
                }
            }
            if (n == 0) heights[n++] = top(centre.getX(), centre.getZ());
            for (int i = 0; i < n; i++) {
                top = Math.max(top, heights[i]);
                low = Math.min(low, heights[i]);
            }
            if (top - summitCut > low + maxDrop) return null;
            int[] sorted = Arrays.copyOf(heights, n);
            Arrays.sort(sorted);
            int floor = MathHelper.clamp(sorted[n / 2], top - summitCut, low + maxDrop);
            int bottom = floor - floorLayer;
            HeightLimitView world = context.world();
            if (floor < minFloor || bottom < world.getBottomY() || bottom + local.getBlockCountY() - 1 > world.getTopYInclusive()) return null;
            if (!biomeAllows(centre.getX(), floor, centre.getZ())) return null;
            BlockBox rim = footprint.expand(GRID);
            for (int sx = first(rim.getMinX(), GRID); sx <= rim.getMaxX(); sx += GRID) {
                for (int sz = first(rim.getMinZ(), GRID); sz <= rim.getMaxZ(); sz += GRID) {
                    boolean under = sx >= footprint.getMinX() && sx <= footprint.getMaxX() && sz >= footprint.getMinZ() && sz <= footprint.getMaxZ();
                    if (!under && top(sx, sz) > top) return null;
                }
            }
            for (int sx = first(footprint.getMinX(), FINE); sx <= footprint.getMaxX(); sx += FINE)
                for (int sz = first(footprint.getMinZ(), FINE); sz <= footprint.getMaxZ(); sz += FINE)
                    if (wet(sx, sz)) return null;
            long score = 0;
            for (int i = 0; i < n; i++) score += (long) (heights[i] - floor) * (heights[i] - floor);
            return new Spot(rotation, local, footprint, floor, score);
        }

        /** The biome at the surface: above the ground the depth noise is negative, so no cave biome is chosen. */
        boolean surfaceAllows(int x, int z) {
            return biomeAllows(x, context.world().getTopYInclusive(), z);
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

    @Override
    public StructureType<?> getType() {
        return TYPE;
    }
}
