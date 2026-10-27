package com.anton.elementalwands.summit;

import com.anton.elementalwands.registry.ModBlocks;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;

/**
 * Test-only entrypoint for tools/aerie_worldgen_smoke.init.gradle: in a fresh normal world, runs the
 * real {@code /locate structure elementalwands:storm_aerie} from spawn (and from far points around
 * it), generates each found aerie and measures it: the gong, the floor height, how flat worldgen
 * levelled the box, the biome's surface on it (and no snow on a stony peak), the remains, the
 * support under the floor, the earthwork walls and overhangs around it, and the land beyond.
 */
public final class StormAerieWorldgenSmoke implements ModInitializer {
    private static final Identifier ID = Identifier.of("elementalwands", "storm_aerie");
    private static final Pattern FOUND = Pattern.compile("\\[(-?\\d+), ~, (-?\\d+)\\] \\((\\d+) blocks away\\)");
    private static final int[][] FROM = {{0, 0}, {1600, 0}, {0, 1600}, {-1600, 0}, {0, -1600}, {3200, 0}, {0, 3200}, {-3200, -3200}};
    /** Blocks a stony peak's surface must never show, whoever placed them. */
    private static final Set<String> SNOW = Set.of("snow", "snow_block", "powder_snow", "ice", "packed_ice", "blue_ice");
    private final List<String> feedback = new CopyOnWriteArrayList<>();
    private int tick;
    private boolean finished;

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || ++tick != 25) return;
            finished = true;
            try { run(server); }
            catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("AERIE_WORLDGEN_FAILED.txt"), e + "\n"); } catch (Exception ignored) {}
            }
            server.stop(false);
        });
    }

    private void run(MinecraftServer server) throws Exception {
        ServerWorld world = server.getOverworld();
        BlockPos spawn = server.getSpawnPoint().getPos();
        StringBuilder report = new StringBuilder("seed=" + world.getSeed() + " spawn=" + spawn.toShortString() + "\n");
        List<String> problems = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (int[] offset : FROM) {
            Vec3d from = new Vec3d(spawn.getX() + offset[0] + .5, 100, spawn.getZ() + offset[1] + .5);
            long started = System.nanoTime();
            String said = locate(server, world, from);
            long ms = (System.nanoTime() - started) / 1_000_000;
            Matcher m = FOUND.matcher(said);
            report.append("\n/locate structure ").append(ID).append(" from ").append(BlockPos.ofFloored(from).toShortString())
                    .append(" (").append(ms).append(" ms): ").append(said).append('\n');
            if (!m.find()) {
                if (offset[0] == 0 && offset[1] == 0) throw new AssertionError("/locate from spawn found no aerie: " + said + "\n" + report);
                continue;
            }
            int x = Integer.parseInt(m.group(1)), z = Integer.parseInt(m.group(2));
            String measured = measure(world, x, z, from, seen, problems);
            report.append(measured);
        }
        report.append("\nproblems: ").append(problems.isEmpty() ? "none" : String.join("; ", problems)).append('\n');
        System.out.println("AERIE WORLDGEN REPORT\n" + report);
        Files.writeString(Path.of(problems.isEmpty() ? "AERIE_WORLDGEN_PASSED.txt" : "AERIE_WORLDGEN_FAILED.txt"), report.toString());
    }

    private String locate(MinecraftServer server, ServerWorld world, Vec3d from) {
        feedback.clear();
        ServerCommandSource source = server.getCommandSource().withWorld(world).withPosition(from).withOutput(new CommandOutput() {
            public void sendMessage(Text text) { feedback.add(text.getString()); }
            public boolean shouldReceiveFeedback() { return true; }
            public boolean shouldTrackOutput() { return true; }
            public boolean shouldBroadcastConsoleToOps() { return false; }
        });
        server.getCommandManager().parseAndExecute(source, "locate structure " + ID);
        return String.join(" | ", feedback);
    }

    private String measure(ServerWorld world, int lx, int lz, Vec3d from, Set<BlockPos> seen, List<String> problems) throws Exception {
        Structure structure = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).get(ID);
        StructureStart start = world.getChunk(lx >> 4, lz >> 4, ChunkStatus.STRUCTURE_STARTS).getStructureStart(structure);
        if (start == null || !start.hasChildren()) { problems.add("no structure start at located chunk " + lx + "," + lz); return "  no start at located chunk\n"; }
        PoolStructurePiece piece = (PoolStructurePiece) start.getChildren().getFirst();
        BlockBox box = piece.getBoundingBox();
        for (int cx = (box.getMinX() - 24) >> 4; cx <= (box.getMaxX() + 24) >> 4; cx++)
            for (int cz = (box.getMinZ() - 24) >> 4; cz <= (box.getMaxZ() + 24) >> 4; cz++) world.getChunk(cx, cz);

        StructureTemplate template = world.getStructureTemplateManager().getTemplate(ID).orElseThrow();
        StructurePlacementData data = new StructurePlacementData().setRotation(piece.getRotation());
        BlockPos origin = piece.getPos();
        // The template's own cells (frame, gong blocks, remains) in the world, with their placed states.
        Field lists = StructureTemplate.class.getDeclaredField("blockInfoLists");
        lists.setAccessible(true);
        List<StructureTemplate.StructureBlockInfo> infos = ((StructureTemplate.PalettedBlockInfoList) ((List<?>) lists.get(template)).getFirst()).getAll();
        Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        for (var info : infos) placed.put(origin.add(StructureTemplate.transform(data, info.pos())).toImmutable(), info.state().rotate(piece.getRotation()));
        int[] g = StormAerieLayout.GONG;
        BlockPos expected = origin.add(StructureTemplate.transform(data, new BlockPos(g[0], g[1], g[2])));
        List<BlockPos> anchors = new ArrayList<>(), parts = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            if (world.getBlockState(pos).isOf(ModBlocks.STORM_GONG)) anchors.add(pos.toImmutable());
            if (world.getBlockState(pos).isOf(ModBlocks.STORM_GONG_PART)) parts.add(pos.toImmutable());
        }
        StormAerie.Gong gong = anchors.size() == 1 ? StormAerie.gong(world, anchors.getFirst()) : null;
        StringBuilder out = new StringBuilder();
        if (gong == null) { problems.add("not one gong anchor in the aerie at " + box + ": " + anchors); return "  NO GONG in " + box + " (anchors " + anchors + ")\n"; }
        if (!seen.add(gong.anchor())) return "  (same aerie as before: anchor " + gong.anchor().toShortString() + ")\n";
        if (!gong.anchor().equals(expected) || gong.facing() != piece.getRotation().rotate(Direction.SOUTH))
            problems.add("gong " + gong + " differs from the piece's " + expected + " facing " + piece.getRotation().rotate(Direction.SOUTH));
        long strays = parts.stream().filter(p -> !gong.equals(StormAerie.gong(world, p))).count();
        if (parts.size() != 11 || strays > 0) problems.add(parts.size() + " parts, " + strays + " not resolving to the anchor");
        if (!(world.getBlockEntity(gong.anchor()) instanceof StormGongBlockEntity)) problems.add("anchor " + gong.anchor().toShortString() + " has no gong block entity");
        int floor = origin.getY() + StormAerieLayout.FLOOR;
        BlockPos stand = StormAerie.cell(gong, StormAerieLayout.STAND);
        double distance = Math.hypot(gong.anchor().getX() + .5 - from.x, gong.anchor().getZ() + .5 - from.z);
        String biome = biome(world, gong.foot());
        out.append("  AERIE anchor=").append(gong.anchor().toShortString()).append(" facing=").append(gong.facing())
                .append(" rotation=").append(piece.getRotation()).append(" parts=").append(parts.size())
                .append(" floorY=").append(floor).append(" stand=").append(stand.toShortString())
                .append(" box=").append(box.getMinX()).append("..").append(box.getMaxX()).append(",").append(box.getMinY()).append("..").append(box.getMaxY())
                .append(",").append(box.getMinZ()).append("..").append(box.getMaxZ())
                .append(" locatedChunkCorner=").append(lx).append(",").append(lz)
                .append(" anchorDistanceFromLocateOrigin=").append(Math.round(distance)).append('\n');
        out.append("  biome at gong=").append(biome).append("; box corners=")
                .append(biome(world, new BlockPos(box.getMinX(), floor, box.getMinZ()))).append(',')
                .append(biome(world, new BlockPos(box.getMaxX(), floor, box.getMinZ()))).append(',')
                .append(biome(world, new BlockPos(box.getMinX(), floor, box.getMaxZ()))).append(',')
                .append(biome(world, new BlockPos(box.getMaxX(), floor, box.getMaxZ()))).append('\n');
        if (floor < 140) problems.add("floor y " + floor + " is below 140");
        if (gong.anchor().getY() != floor + 1 + StormAerieLayout.GONG_UP) problems.add("anchor y " + gong.anchor().getY() + " is not floor+1+GONG_UP");
        List<BlockPos> cells = new ArrayList<>(List.of(stand));
        for (int[] c : StormAerieLayout.CHESTS) cells.add(StormAerie.cell(gong, c));
        for (BlockPos c : cells) {
            boolean open = !holds(world.getBlockState(c)) && world.getBlockState(c).getFluidState().isEmpty() && !holds(world.getBlockState(c.up()));
            if (c.getY() != floor + 1 || !open || !holds(world.getBlockState(c.down())))
                problems.add((c == stand ? "STAND " : "chest cell ") + c.toShortString() + " is not standable: " + world.getBlockState(c.down()) + " / "
                        + world.getBlockState(c) + " / " + world.getBlockState(c.up()));
        }

        // Levelling: in every column of the box, the top of the land (the template's own cells aside)
        // against the floor, and what covers it.
        int above = 0, below = 0, highest = 0, lowest = 0, snowLayers = 0, stonySnow = 0, stonyColumns = 0;
        Map<String, Integer> mix = new TreeMap<>();
        List<String> uneven = new ArrayList<>();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
            int top = Integer.MIN_VALUE;
            for (int y = box.getMaxY() + 6; y >= floor - 24 && top == Integer.MIN_VALUE; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (placed.containsKey(pos)) { if (y == floor) top = floor; continue; }
                if (holds(world.getBlockState(pos))) top = y;
            }
            if (top == Integer.MIN_VALUE) top = floor - 25;
            BlockPos ground = new BlockPos(x, top, z);
            String cover = placed.containsKey(ground) ? "(frame)" : id(world.getBlockState(ground));
            mix.merge(cover, 1, Integer::sum);
            String on = id(world.getBlockState(ground.up()));
            if (on.equals("snow")) snowLayers++;
            if (top > floor) { above++; highest = Math.max(highest, top - floor); }
            if (top < floor) { below++; lowest = Math.min(lowest, top - floor); }
            if (top != floor && uneven.size() < 8) uneven.add(x + "," + z + " at " + (top - floor) + " (" + cover + ")");
            if (biome(world, ground).equals("stony_peaks")) {
                stonyColumns++;
                for (int y = top - 1; y <= top + 2; y++) if (SNOW.contains(id(world.getBlockState(new BlockPos(x, y, z))))) stonySnow++;
            }
        }
        int columns = box.getBlockCountX() * box.getBlockCountZ();
        out.append("  levelled box: ").append(columns).append(" columns, ").append(columns - above - below).append(" at the floor, ")
                .append(above).append(" above (up to +").append(highest).append("), ").append(below).append(" below (down to ").append(lowest).append(")")
                .append(uneven.isEmpty() ? "" : "; e.g. " + String.join("; ", uneven)).append('\n');
        out.append("  surface mix in the box: ").append(mix).append("; snow layers on top: ").append(snowLayers).append('\n');
        long templateSnow = placed.values().stream().filter(state -> SNOW.contains(id(state))).count();
        out.append("  template-made snow cells: ").append(templateSnow).append("; snow or ice in stony_peaks columns of the box (")
                .append(stonyColumns).append(" columns): ").append(stonySnow).append('\n');
        if (above + below > 0) problems.add("box not level: " + above + " columns above the floor (to +" + highest + "), " + below + " below (to " + lowest + ")");
        if (templateSnow > 0) problems.add("the template places " + templateSnow + " snow cells");
        if (stonySnow > 0) problems.add(stonySnow + " snow or ice blocks on stony_peaks columns of the box");

        // The remains: each still there, on solid ground, with nothing over it.
        List<String> remains = new ArrayList<>();
        for (var entry : placed.entrySet()) {
            String what = id(entry.getValue());
            if (!what.equals("skeleton_skull") && !what.equals("old_bones") && !what.equals("roc_feather")) continue;
            BlockPos pos = entry.getKey();
            boolean there = world.getBlockState(pos).isOf(entry.getValue().getBlock()), resting = holds(world.getBlockState(pos.down())),
                    open = !holds(world.getBlockState(pos.up())) && !placed.containsKey(pos.up());
            remains.add(what + "@" + pos.toShortString() + (there && resting && open ? " ok" : " BAD on " + id(world.getBlockState(pos.down())) + " under " + id(world.getBlockState(pos.up()))));
            if (!there || !resting || !open) problems.add(what + " at " + pos.toShortString() + (!there ? " was overwritten" : !resting ? " floats over " + world.getBlockState(pos.down()) : " is buried under " + world.getBlockState(pos.up())));
        }
        out.append("  remains: ").append(String.join(", ", remains)).append('\n');

        // Support under every column of the floor: contiguous solid from the floor down.
        int minDepth = Integer.MAX_VALUE, shallow = 0, gaps = 0, missing = 0;
        TreeMap<Integer, Integer> histogram = new TreeMap<>();
        List<String> worst = new ArrayList<>();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
            BlockPos top = new BlockPos(x, floor, z);
            if (!holds(world.getBlockState(top))) missing++;
            int depth = 0;
            while (depth < 64 && holds(world.getBlockState(top.down(depth + 1)))) depth++;
            minDepth = Math.min(minDepth, depth);
            histogram.merge(Math.min(depth / 8 * 8, 64), 1, Integer::sum);
            if (depth < 64) {
                int air = 0;
                while (air < 96 && !holds(world.getBlockState(top.down(depth + 1 + air)))) air++;
                if (air < 96) gaps++;
                if (depth < 16) { shallow++; if (worst.size() < 6) worst.add(top.toShortString() + " solid " + depth + " then " + air + " open" + (air < 96 ? " then rock" : "") + " " + profile(world, top, 24)); }
            }
        }
        out.append("  floor columns=").append(columns).append(" floorBlockMissing=").append(missing)
                .append(" minContiguousSolidBelowFloor=").append(minDepth).append(" columnsWithLessThan16=").append(shallow)
                .append(" columnsWithAnOpenGapAboveRock(within 64)=").append(gaps).append(" depthHistogram(bucket of 8, 64=64+)=").append(histogram).append('\n');
        if (!worst.isEmpty()) out.append("  shallow columns: ").append(String.join("; ", worst)).append('\n');
        if (missing > 0 || minDepth < 4) problems.add("floor floats: " + missing + " missing floor blocks, min support " + minDepth);

        // Earthwork: surface around the square footprint, the tallest vertical step, and what it is made of.
        int margin = 14;
        int x0 = box.getMinX() - margin, x1 = box.getMaxX() + margin, z0 = box.getMinZ() - margin, z1 = box.getMaxZ() + margin;
        Map<Long, Integer> surface = new LinkedHashMap<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) surface.put(BlockPos.asLong(x, 0, z), ground(world, placed, floor, x, z));
        int bestStep = 0; BlockPos high = null, low = null;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean nearBox = x >= box.getMinX() - 3 && x <= box.getMaxX() + 3 && z >= box.getMinZ() - 3 && z <= box.getMaxZ() + 3;
            if (!nearBox) continue;
            int here = surface.get(BlockPos.asLong(x, 0, z));
            for (Direction d : Direction.Type.HORIZONTAL) {
                int nx = x + d.getOffsetX(), nz = z + d.getOffsetZ();
                Integer there = surface.get(BlockPos.asLong(nx, 0, nz));
                if (there == null || here - there <= bestStep) continue;
                bestStep = here - there; high = new BlockPos(x, here, z); low = new BlockPos(nx, there, nz);
            }
        }
        Map<String, Integer> face = new TreeMap<>();
        if (high != null) for (int y = low.getY() + 1; y <= high.getY(); y++)
            face.merge(Registries.BLOCK.getId(world.getBlockState(new BlockPos(high.getX(), y, high.getZ())).getBlock()).getPath(), 1, Integer::sum);
        out.append("  tallest vertical step within 3 of the footprint: ").append(bestStep).append(" blocks, from ")
                .append(high == null ? "-" : high.toShortString()).append(" down to ").append(low == null ? "-" : low.toShortString())
                .append(" (top ").append(high == null ? 0 : high.getY() - floor).append(" vs floor; face blocks ").append(face).append(")\n");
        // The plinth: how far each footprint edge stands over the ground 1, 4 and 8 blocks outside it.
        int[] drops = new int[3]; String[] where = new String[3]; int[] outs = {1, 4, 8};
        int raiseMax = Integer.MIN_VALUE; String raiseAt = "";
        var generator = world.getChunkManager().getChunkGenerator();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
            int here = surface.get(BlockPos.asLong(x, 0, z));
            int natural = generator.getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, world.getChunkManager().getNoiseConfig()) - 1;
            if (here - natural > raiseMax) { raiseMax = here - natural; raiseAt = x + "," + z + " (natural " + natural + ", now " + here + ")"; }
            boolean edge = x == box.getMinX() || x == box.getMaxX() || z == box.getMinZ() || z == box.getMaxZ();
            if (!edge) continue;
            int dx = x == box.getMinX() ? -1 : x == box.getMaxX() ? 1 : 0, dz = z == box.getMinZ() ? -1 : z == box.getMaxZ() ? 1 : 0;
            for (int i = 0; i < outs.length; i++) {
                for (int[] dir : new int[][]{{dx, 0}, {0, dz}}) {
                    if (dir[0] == 0 && dir[1] == 0) continue;
                    Integer there = surface.get(BlockPos.asLong(x + dir[0] * outs[i], 0, z + dir[1] * outs[i]));
                    if (there != null && here - there > drops[i]) { drops[i] = here - there; where[i] = x + "," + z + " (" + here + " over " + there + ")"; }
                }
            }
        }
        out.append("  footprint edge over ground 1/4/8 blocks out: ").append(drops[0]).append(" at ").append(where[0]).append("; ")
                .append(drops[1]).append(" at ").append(where[1]).append("; ").append(drops[2]).append(" at ").append(where[2]).append('\n');
        out.append("  most raised column in the footprint above its natural noise height: +").append(raiseMax).append(" at ").append(raiseAt).append('\n');
        // The plinth's exposed side: along each footprint edge, solid edge-column blocks with open air
        // (or fluid) right outside them, from the floor down. Tallest unbroken run, and what it is made of.
        int faceBest = 0; String faceAt = "-"; Map<String, Integer> faceBlocks = new TreeMap<>(); int faceTotal = 0;
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
            int dx = x == box.getMinX() ? -1 : x == box.getMaxX() ? 1 : 0, dz = z == box.getMinZ() ? -1 : z == box.getMaxZ() ? 1 : 0;
            for (int[] dir : new int[][]{{dx, 0}, {0, dz}}) {
                if (dir[0] == 0 && dir[1] == 0) continue;
                int run = 0;
                for (int y = floor + 6; y >= floor - 64; y--) {
                    BlockState inside = world.getBlockState(new BlockPos(x, y, z)), outside = world.getBlockState(new BlockPos(x + dir[0], y, z + dir[1]));
                    if (holds(inside) && !holds(outside)) {
                        run++; faceTotal++;
                        faceBlocks.merge(Registries.BLOCK.getId(inside.getBlock()).getPath(), 1, Integer::sum);
                        if (run > faceBest) { faceBest = run; faceAt = x + "," + z + " facing " + Direction.fromVector(dir[0], 0, dir[1], null) + " down to y " + y; }
                    } else run = 0;
                }
            }
        }
        out.append("  plinth side: tallest unbroken exposed face on the footprint edge = ").append(faceBest).append(" blocks at ").append(faceAt)
                .append("; all exposed edge-face blocks ").append(faceTotal).append(" by type ").append(faceBlocks).append('\n');
        // Overhangs just outside the footprint: a solid top with open space under it before rock.
        int overhangs = 0; String worstOverhang = "-"; int worstGap = 0;
        for (int x = box.getMinX() - 6; x <= box.getMaxX() + 6; x++) for (int z = box.getMinZ() - 6; z <= box.getMaxZ() + 6; z++) {
            if (x >= box.getMinX() && x <= box.getMaxX() && z >= box.getMinZ() && z <= box.getMaxZ()) continue;
            int top = surface.get(BlockPos.asLong(x, 0, z)), y = top;
            while (y > top - 12 && holds(world.getBlockState(new BlockPos(x, y, z)))) y--;
            int gap = 0;
            while (gap < 64 && !holds(world.getBlockState(new BlockPos(x, y - gap, z)))) gap++;
            if (y > top - 12 && gap >= 3 && gap < 64 && !world.getBlockState(new BlockPos(x, top, z)).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                overhangs++;
                if (gap > worstGap) { worstGap = gap; worstOverhang = x + "," + z + " top " + top + ": " + profile(world, new BlockPos(x, top, z), Math.min(top - y + gap + 2, 40)); }
            }
        }
        out.append("  overhangs within 6 blocks outside the footprint (solid top <12 thick, then >=3 open, then rock; trees excluded): ").append(overhangs)
                .append("; worst gap ").append(worstGap).append(" at ").append(worstOverhang).append('\n');
        // Overhangs anywhere the earthwork or its rock cone reaches: a solid top of any thickness up to
        // 48 blocks over 3 or more open blocks before rock (a lip at the cone's base would show here).
        // The rock cone's deepest fill is floor-36 (max_drop 24 plus 12), so a lip at its base opens at floor-37.
        int lips = 0, worstLip = 0, baseLips = 0; String worstLipAt = "-";
        for (int x = box.getMinX() - 16; x <= box.getMaxX() + 16; x++) for (int z = box.getMinZ() - 16; z <= box.getMaxZ() + 16; z++) {
            if (x >= box.getMinX() && x <= box.getMaxX() && z >= box.getMinZ() && z <= box.getMaxZ()) continue;
            int top = surface(world, x, z), y = top;
            if (top > floor + 2 || world.getBlockState(new BlockPos(x, top, z)).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) continue;
            while (y > top - 48 && holds(world.getBlockState(new BlockPos(x, y, z)))) y--;
            int gap = 0;
            while (gap < 64 && !holds(world.getBlockState(new BlockPos(x, y - gap, z)))) gap++;
            if (y > top - 48 && gap >= 3 && gap < 64) {
                lips++;
                if (y == floor - 37) baseLips++;
                if (gap > worstLip) { worstLip = gap; worstLipAt = x + "," + z + " top " + (top - floor) + " vs floor: " + profile(world, new BlockPos(x, top, z), Math.min(top - y + gap + 2, 60)); }
            }
        }
        out.append("  overhangs within 16 blocks outside the box (solid top up to 48 thick, then >=3 open, then rock; trees excluded): ").append(lips)
                .append(" (opening just under the cone's deepest fill: ").append(baseLips).append("); worst gap ").append(worstLip).append(" at ").append(worstLipAt).append('\n');
        if (high != null) out.append("  tallest-step column profile ").append(profile(world, high, Math.min(bestStep + 2, 40))).append('\n');
        // The tallest bare wall anywhere in reach of the earthwork: a column whose solid face toward a
        // neighbour stands over open air for this many blocks without a break.
        int wall = 0; String wallAt = "-";
        for (int x = box.getMinX() - 16; x <= box.getMaxX() + 16; x++) for (int z = box.getMinZ() - 16; z <= box.getMaxZ() + 16; z++) {
            for (Direction d : Direction.Type.HORIZONTAL) {
                int run = 0;
                for (int y = floor + 2; y >= floor - 48; y--) {
                    BlockPos inside = new BlockPos(x, y, z), outside = new BlockPos(x + d.getOffsetX(), y, z + d.getOffsetZ());
                    if (!placed.containsKey(inside) && holds(world.getBlockState(inside)) && !holds(world.getBlockState(outside))) {
                        if (++run > wall) { wall = run; wallAt = x + "," + z + " facing " + d + " from y " + (y + run - 1) + " to " + y + " (" + (y + run - 1 - floor) + " vs floor)"; }
                    } else run = 0;
                }
            }
        }
        out.append("  tallest bare wall within 16 blocks of the box, floor+2 down to floor-48: ").append(wall).append(" blocks at ").append(wallAt).append('\n');
        // Surface relative to the floor, every 2 blocks, around the footprint (north up, x right).
        out.append("  height map (surface - floor; every 2 blocks; [] = footprint box; x ").append(x0).append("..").append(x1)
                .append(", z ").append(z0).append("..").append(z1).append("):\n");
        for (int z = z0; z <= z1; z += 2) {
            StringBuilder row = new StringBuilder("   ");
            for (int x = x0; x <= x1; x += 2) {
                int v = surface.get(BlockPos.asLong(x, 0, z)) - floor;
                boolean in = x >= box.getMinX() && x <= box.getMaxX() && z >= box.getMinZ() && z <= box.getMaxZ();
                row.append(String.format(in ? "[%3d]" : " %3d ", Math.max(-99, Math.min(999, v))));
            }
            out.append(row).append('\n');
        }
        // The land beyond, from noise heights: compass directions at 16 to 96 blocks from the gong.
        out.append("  natural terrain around the gong (noise height - floor; biome at that surface):\n");
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[][] dirs = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int i = 0; i < 8; i++) {
            StringBuilder line = new StringBuilder("   " + names[i] + ":");
            for (int r : new int[]{16, 32, 48, 64, 96}) {
                double len = Math.hypot(dirs[i][0], dirs[i][1]);
                int x = gong.anchor().getX() + (int) Math.round(dirs[i][0] / len * r), z = gong.anchor().getZ() + (int) Math.round(dirs[i][1] / len * r);
                int h = generator.getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, world.getChunkManager().getNoiseConfig()) - 1;
                line.append(' ').append(r).append("m=").append(h - floor).append('(').append(noiseBiome(world, x, h, z)).append(')');
            }
            out.append(line).append('\n');
        }
        System.out.println("AERIE MEASURED\n" + out);
        return out.toString();
    }

    /** A column top-down as runs of block ids, e.g. "[snow_block x1, stone x7, air x3, stone x2]". */
    private static String profile(ServerWorld world, BlockPos top, int length) {
        List<String> runs = new ArrayList<>();
        String last = null; int n = 0;
        for (int i = 0; i < length; i++) {
            String id = Registries.BLOCK.getId(world.getBlockState(top.down(i)).getBlock()).getPath();
            if (id.equals(last)) { n++; continue; }
            if (last != null) runs.add(last + " x" + n);
            last = id; n = 1;
        }
        if (last != null) runs.add(last + " x" + n);
        return runs.toString();
    }

    private static String id(BlockState state) { return Registries.BLOCK.getId(state.getBlock()).getPath(); }

    private static String biome(ServerWorld world, BlockPos pos) {
        return world.getBiome(pos).getKey().map(k -> k.getValue().getPath()).orElse("?");
    }

    private static String noiseBiome(ServerWorld world, int x, int y, int z) {
        var source = world.getChunkManager().getChunkGenerator().getBiomeSource();
        return source.getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
                world.getChunkManager().getNoiseConfig().getMultiNoiseSampler()).getKey().map(k -> k.getValue().getPath()).orElse("?");
    }

    /** Top block that carries weight: not air, fluid, snow layer or plant. */
    /** As {@link #surface}, but the land under the template's frame, gong and remains (its post feet in the floor count). */
    private static int ground(ServerWorld world, Map<BlockPos, BlockState> placed, int floor, int x, int z) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockPos.Mutable pos = new BlockPos.Mutable(x, y, z);
        while (y > world.getBottomY() && (y > floor && placed.containsKey(pos.setY(y)) || !holds(world.getBlockState(pos.setY(y))))) y--;
        return y;
    }

    private static int surface(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockPos.Mutable pos = new BlockPos.Mutable(x, y, z);
        while (y > world.getBottomY() && !holds(world.getBlockState(pos.setY(y)))) y--;
        return y;
    }

    private static boolean holds(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.isReplaceable();
    }
}
