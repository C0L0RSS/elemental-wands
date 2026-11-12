package com.anton.elementalwands.world;

import com.anton.elementalwands.hall.PyreGateLayout;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.EmptyPoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

/**
 * The Burned Gate in the Nether: one rigid template, a bridge from a lava shore out over the lake to a lone gate.
 *
 * <p>The Nether has no surface to seat a structure on, so this one looks for its own place. Round the chunk's centre
 * it tries spots on a grid, each of them turned all four ways, until one fits the template's own list of columns
 * ({@link PyreGateLayout#LAND} and {@link PyreGateLayout#SEA}): the shore end must be solid ground at about one
 * height, a few blocks above the lava, with a cavern's open air over it, and everything under the bridge and the gate
 * must be the open lava sea. The road's deck is then set at the shore's height. A region with no such shore gets no
 * gate, and {@code /place structure} fails there too.
 *
 * <p>The spots are first judged on columns snapped to a 4-block lattice, which candidates share; the one that passes is
 * judged again on its exact columns. The template carries its own piers down into the lava and its own open air, so
 * the land is not reshaped ({@code terrain_adaptation} none); it generates in the last feature step, over whatever the
 * biome grew there.
 */
public final class PyreGateStructure extends Structure {
    public static final MapCodec<PyreGateStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            configCodecBuilder(instance),
            StructurePool.REGISTRY_CODEC.fieldOf("start_pool").forGetter(s -> s.startPool)
    ).apply(instance, PyreGateStructure::new));

    /** Registered as {@code elementalwands:pyre_gate} by {@link ModWorldGen}. */
    public static final StructureType<PyreGateStructure> TYPE = () -> CODEC;

    /** Spots are tried this far apart, out to this many steps from the chunk's centre. */
    private static final int STEP = 12, REACH = 3;
    private static final int LATTICE = 4;
    /** The shore's columns may differ this much in height from the deck. */
    private static final int UNEVEN = 2;
    private static final int[][] CANDIDATES = candidates(REACH);
    private static final int NONE = Integer.MIN_VALUE, SEA = Integer.MIN_VALUE + 1;

    private final RegistryEntry<StructurePool> startPool;

    public PyreGateStructure(Config config, RegistryEntry<StructurePool> startPool) {
        super(config);
        this.startPool = startPool;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        StructurePoolElement element = startPool.value().getRandomElement(context.random());
        if (element == EmptyPoolElement.INSTANCE) return Optional.empty();
        StructureTemplateManager templates = context.structureTemplateManager();
        ChunkPos chunk = context.chunkPos();
        Survey survey = new Survey(context);
        BlockRotation first = BlockRotation.random(context.random());
        for (int[] candidate : CANDIDATES) {
            int x = chunk.getCenterX() + candidate[0] * STEP, z = chunk.getCenterZ() + candidate[1] * STEP;
            BlockRotation rotation = first;
            for (int turn = 0; turn < 4; turn++, rotation = rotation.rotate(BlockRotation.CLOCKWISE_90)) {
                if (survey.deck(x, z, rotation, LATTICE) == NONE) continue;
                int deck = survey.deck(x, z, rotation, 1);
                if (deck == NONE) continue;
                // The frame's origin, in the template, lands on (x, deck, z).
                BlockPos origin = new BlockPos(x, deck, z).subtract(
                        new BlockPos(PyreGateLayout.ORIGIN_X, PyreGateLayout.ORIGIN_Y, PyreGateLayout.ORIGIN_Z).rotate(rotation));
                BlockBox box = element.getBoundingBox(templates, origin, rotation);
                if (box.getMinY() < context.world().getBottomY() || box.getMaxY() > context.world().getTopYInclusive()) continue;
                PoolStructurePiece piece = new PoolStructurePiece(templates, element, origin, PyreGateLayout.ORIGIN_Y + 1, rotation, box,
                        StructureLiquidSettings.IGNORE_WATERLOGGING);
                return Optional.of(new StructurePosition(new BlockPos(x, deck, z), collector -> collector.addPiece(piece)));
            }
        }
        return Optional.empty();
    }

    /** The cell a party stands in before this gate, where /locate sends players. */
    public static BlockPos arrival(PoolStructurePiece piece) {
        return piece.getPos().add(new BlockPos(PyreGateLayout.ORIGIN_X + PyreGateLayout.STAND[0], PyreGateLayout.ORIGIN_Y + PyreGateLayout.STAND[1],
                PyreGateLayout.ORIGIN_Z + PyreGateLayout.STAND[2]).rotate(piece.getRotation()));
    }

    /** What the terrain's noise makes of single columns, kept across the spots of one attempt. */
    private static final class Survey {
        private final Context context;
        private final int lava;
        private final Long2IntOpenHashMap columns = new Long2IntOpenHashMap();

        Survey(Context context) {
            this.context = context;
            lava = context.chunkGenerator().getSeaLevel() - 1;     // the lava sea's top block
        }

        /**
         * The height of the road's deck for a gate whose arch stands at (x, z), turned this way, or NONE if it does
         * not fit there. Columns are snapped to `lattice`.
         */
        int deck(int x, int z, BlockRotation rotation, int lattice) {
            int[][] land = PyreGateLayout.LAND, sea = PyreGateLayout.SEA;
            int[] floors = new int[land.length];
            for (int i = 0; i < land.length; i++) {
                int column = at(x, z, land[i], rotation, lattice);
                if (column == NONE || column == SEA) return NONE;
                floors[i] = column;
                // The nearest of the sea's columns next: most spots fail on one of the first two.
                if (i == 0 && at(x, z, sea[0], rotation, lattice) != SEA) return NONE;
            }
            int[] sorted = floors.clone();
            Arrays.sort(sorted);
            int deck = sorted[sorted.length / 2];
            if (deck - lava < PyreGateLayout.DECK_MIN || deck - lava > PyreGateLayout.DECK_MAX) return NONE;
            for (int floor : floors) if (Math.abs(floor - deck) > UNEVEN) return NONE;
            for (int[] cell : sea) if (at(x, z, cell, rotation, lattice) != SEA) return NONE;
            return deck;
        }

        private int at(int x, int z, int[] cell, BlockRotation rotation, int lattice) {
            BlockPos turned = new BlockPos(cell[0], 0, cell[1]).rotate(rotation);
            return column(Math.floorDiv(x + turned.getX() + lattice / 2, lattice) * lattice, Math.floorDiv(z + turned.getZ() + lattice / 2, lattice) * lattice);
        }

        /**
         * One column: SEA where the lava sea lies open under a cavern, the height of its floor where solid ground
         * stands a little above the lava with open air over it, NONE otherwise.
         */
        private int column(int x, int z) {
            long key = ChunkPos.toLong(x, z);
            if (columns.containsKey(key)) return columns.get(key);
            VerticalBlockSample sample = context.chunkGenerator().getColumnSample(x, z, context.world(), context.noiseConfig());
            int result = NONE;
            if (!sample.getState(lava).getFluidState().isEmpty()) {
                if (open(sample, lava + 1, lava + PyreGateLayout.DECK_MAX + PyreGateLayout.HEADROOM)) result = SEA;
            } else {
                for (int y = lava; y <= lava + PyreGateLayout.DECK_MAX + UNEVEN; y++)
                    if (solid(sample.getState(y)) && open(sample, y + 1, y + PyreGateLayout.HEADROOM)) { result = y; break; }
            }
            columns.put(key, result);
            return result;
        }

        private static boolean open(VerticalBlockSample sample, int from, int to) {
            for (int y = from; y <= to; y++) if (!sample.getState(y).isAir()) return false;
            return true;
        }

        private static boolean solid(BlockState state) { return !state.isAir() && state.getFluidState().isEmpty(); }
    }

    private static int[][] candidates(int reach) {
        List<int[]> out = new ArrayList<>();
        for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) out.add(new int[]{x, z});
        out.sort(Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
        return out.toArray(int[][]::new);
    }

    @Override
    public StructureType<?> getType() {
        return TYPE;
    }
}
