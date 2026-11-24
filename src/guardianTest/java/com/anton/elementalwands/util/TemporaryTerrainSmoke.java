package com.anton.elementalwands.util;

import com.anton.elementalwands.block.NatureSeedlingBlock;
import com.anton.elementalwands.block.StoneWallBlock;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.registry.ModSpellBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.WorldChunk;

/** Runs against the same disposable singleplayer save across four independent Java processes. */
public final class TemporaryTerrainSmoke implements ModInitializer {
    private static final String PHASE = System.getProperty("ew.fixture.phase", "");
    private static final Path PASSED = Path.of("TEMP_TERRAIN_PASSED.txt");
    private static final Path FAILED = Path.of("TEMP_TERRAIN_FAILED.txt");
    private static final BlockPos ANCHOR = new BlockPos(2050, 110, 2050);
    private static final BlockPos WALL = ANCHOR;
    private static final BlockPos SPRING = ANCHOR.east(1);
    private static final BlockPos SENTRY = ANCHOR.east(2);
    private static final BlockPos EDITED = ANCHOR.east(3);
    private static final BlockPos REJECTED = ANCHOR.east(4);
    private static final BlockPos UNSAVED = ANCHOR.east(5);
    private static final BlockPos INTERRUPTED_PLANT = ANCHOR.east(6);
    private static final BlockPos STALE = ANCHOR.east(7);
    private static final BlockPos ALTERED_PLANT = ANCHOR.east(8);
    private static final BlockPos TITAN_CENTER = ANCHOR.east(30);
    private static final BlockPos TITAN = TITAN_CENTER.east(16);

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState EDIT = Blocks.DEEPSLATE.getDefaultState();
    private static final BlockState GRASS = Blocks.SHORT_GRASS.getDefaultState();
    private static final BlockState LOWER = Blocks.TALL_GRASS.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER);
    private static final BlockState UPPER = LOWER.with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER);
    private static final BlockState SETTLED_WALL = ModSpellBlocks.STONE_WALL.getDefaultState().with(StoneWallBlock.SHATTER_READY, true);

    private int tick;
    private boolean ready;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (server.getPlayerManager().getPlayerList().isEmpty()) return;
                run(server, server.getOverworld(), server.getPlayerManager().getPlayerList().getFirst());
            } catch (Throwable failure) {
                failure.printStackTrace();
                try {
                    Files.writeString(FAILED, PHASE + ": " + failure + "\n");
                } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void run(MinecraftServer server, ServerWorld world, ServerPlayerEntity caster) throws Exception {
        if (++tick == 1) server.setAutosave(false);
        if (tick == 10 && PHASE.equals("prepare")) {
            prepare(world, caster);
        } else if (tick == 48 && PHASE.equals("prepare")) {
            require(world.getBlockState(WALL) == SETTLED_WALL, "Settled Stone Wall is missing");
            require(world.getBlockState(SPRING).isOf(ModSpellBlocks.SPRINGBLOOM), "Springbloom pad expired before the save");
            require(world.getBlockState(SENTRY).get(NatureSeedlingBlock.STAGE) == 3, "Sentry Bloom did not animate");
            require(world.getBlockState(TITAN).isOf(ModSpellBlocks.TITAN_DOME), "Titan shell did not cover the existing fire");
            require(TemporaryBlockManager.casterAt(world, TITAN).equals(caster.getUuid()), "Titan did not take terrain ownership");
            require(world.getBlockState(EDITED) == SETTLED_WALL && !TemporaryBlockManager.isTracked(world, EDITED),
                    "An external edit re-acquired a stale record when the previous state returned");
            // Login loads chunks around the saved player before the fixture's JOIN handler runs.
            // Save the observer away from the terrain so only our explicit request loads it on restart.
            require(caster.teleport(world, 4000.5, 200, 4000.5, Set.of(), 0, 0, false), "Could not park the fixture player");
            server.saveAll(true, true, true);
            place(world, UNSAVED, ModSpellBlocks.INFERNO_FLAME.getDefaultState(), caster.getUuid());
            finish(server, "Saved active spell blocks, then halted with one additional unsaved placement.", true);
        } else if (tick == 10) {
            require(List.of("interrupt", "recover", "verify").contains(PHASE), "Invalid recovery phase: " + PHASE);
            var chunk = new ChunkPos(ANCHOR);
            require(world.getChunkManager().getWorldChunk(chunk.x, chunk.z) == null,
                    "The recovery target loaded without a player or chunk request");
            WorldChunk land = getChunk(world, ANCHOR);
            WorldChunk shell = getChunk(world, TITAN);
            if (!PHASE.equals("verify")) {
                require(land.getBlockState(WALL) == SETTLED_WALL && land.hasAttached(TemporaryTerrain.ATTACHMENT),
                        "The previous process did not leave its saved wall and attached recovery record");
                require(shell.getBlockState(TITAN).isOf(ModSpellBlocks.TITAN_DOME)
                                && shell.hasAttached(TemporaryTerrain.ATTACHMENT),
                        "The previous process did not leave its saved Titan shell and attached recovery record");
            }
            ready = true;
        } else if (ready && tick == 12) {
            verifyRecovery(world, PHASE.equals("verify"));
            switch (PHASE) {
                case "interrupt" -> finish(server, "Restored active terrain, then halted before saving the restored chunks.", true);
                case "recover" -> {
                    world.setBlockState(WALL, EDIT);
                    server.saveAll(true, true, true);
                    finish(server, "Retried recovery after the second halt, saved terrain and a subsequent edit.", true);
                }
                case "verify" -> finish(server, "Recovery, original block properties and subsequent player edits survived the next restart.", false);
            }
        }
    }

    private static void prepare(ServerWorld world, ServerPlayerEntity caster) {
        for (BlockPos pos : List.of(WALL, SPRING, SENTRY, EDITED, REJECTED, UNSAVED, INTERRUPTED_PLANT, STALE, ALTERED_PLANT, TITAN)) {
            world.setBlockState(pos.down(), Blocks.DIRT.getDefaultState());
            world.setBlockState(pos, GRASS);
        }
        world.setBlockState(TITAN_CENTER.down(), Blocks.STONE.getDefaultState());
        caster.changeGameMode(GameMode.CREATIVE);
        caster.teleport(world, TITAN_CENTER.getX() + 0.5, TITAN_CENTER.getY(), TITAN_CENTER.getZ() + 0.5, Set.of(), 0, 0, false);
        caster.setAttached(EWAttachments.AFFINITY, "NATURE");

        UUID owner = caster.getUuid();
        var roots = place(world, WALL, ModSpellBlocks.NATURE_ROOTS.getDefaultState(), owner);
        var wall = place(world, WALL, ModSpellBlocks.STONE_WALL.getDefaultState(), owner);
        TemporaryBlockManager.updateTemporaryBlock(world, wall, WALL, SETTLED_WALL, Block.NOTIFY_ALL);
        TemporaryBlockManager.restoreTemporaryBlocks(world, roots);
        require(world.getBlockState(WALL) == SETTLED_WALL, "Old roots erased their replacement Stone Wall");

        plantTallGrass(world, SPRING);
        require(SpringbloomManager.plant(caster, SPRING) && world.isAir(SPRING.up()), "Springbloom did not cover both plant halves");
        require(FlowerTurretManager.plant(world, caster, SENTRY), "Sentry Bloom could not be placed");
        var edited = place(world, EDITED, SETTLED_WALL, owner);
        world.setBlockState(EDITED, EDIT);
        world.setBlockState(EDITED, SETTLED_WALL);
        TemporaryBlockManager.restoreTemporaryBlocks(world, edited);
        require(world.getBlockState(EDITED) == SETTLED_WALL, "A later edit to the same state was erased");

        world.setBlockState(REJECTED, AIR);
        var flame = ModSpellBlocks.INFERNO_FLAME.getDefaultState();
        require(!TemporaryBlockManager.placeTrackedExplosionFire(world, REJECTED, flame, 2000, owner, () -> false)
                && world.isAir(REJECTED) && !TemporaryBlockManager.isTracked(world, REJECTED), "Rejected write created a receipt");
        require(!TemporaryBlockManager.placeTrackedExplosionFire(world, REJECTED, flame, 2000, owner,
                () -> world.setBlockState(REJECTED, EDIT)) && !TemporaryBlockManager.isTracked(world, REJECTED),
                "A substituted block write claimed unrelated terrain");

        // Exercise a saved receipt where only the quiet removal of the upper half took effect.
        plantTallGrass(world, INTERRUPTED_PLANT);
        world.setBlockState(INTERRUPTED_PLANT.up(), AIR, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        TemporaryTerrain.put(getChunk(world, INTERRUPTED_PLANT), new TemporaryTerrain.Cell(
                INTERRUPTED_PLANT.up().asLong(), UUID.randomUUID(), UPPER, AIR));
        require(world.getBlockState(STALE) == GRASS, "Missing terrain for stale-record check");
        TemporaryTerrain.put(getChunk(world, STALE), new TemporaryTerrain.Cell(
                STALE.asLong(), UUID.randomUUID(), AIR, SETTLED_WALL));

        plantTallGrass(world, ALTERED_PLANT);
        place(world, ALTERED_PLANT, ModSpellBlocks.STONE_WALL.getDefaultState(), owner);
        world.setBlockState(ALTERED_PLANT.up(), EDIT);
        world.setBlockState(ALTERED_PLANT.up(), AIR);

        place(world, TITAN, flame, owner);
        TitanDomeManager.startDome(world, caster);
    }

    private static void verifyRecovery(ServerWorld world, boolean savedEdit) {
        require(world.getBlockState(WALL) == (savedEdit ? EDIT : GRASS), "Stone Wall overlap restored the wrong state");
        require(world.getBlockState(SPRING) == LOWER && world.getBlockState(SPRING.up()) == UPPER,
                "Springbloom lost the original tall plant");
        require(world.getBlockState(SENTRY) == GRASS, "Sentry Bloom's animated block was stranded");
        require(world.getBlockState(TITAN) == GRASS, "Titan resurrected fire or lost the original ground cover");
        require(world.getBlockState(EDITED) == SETTLED_WALL, "Recovery overwrote an unowned replacement");
        require(world.getBlockState(REJECTED) == EDIT, "Recovery overwrote a rejected writer's replacement");
        require(world.getBlockState(UNSAVED) == GRASS, "The last unsaved placement resurrected after restart");
        require(world.getBlockState(INTERRUPTED_PLANT) == LOWER && world.getBlockState(INTERRUPTED_PLANT.up()) == UPPER,
                "Recovery could not finish a partially written tall plant");
        require(world.getBlockState(STALE) == GRASS, "A stale unmatched record overwrote current terrain");
        require(world.isAir(ALTERED_PLANT) && world.isAir(ALTERED_PLANT.up()), "Recovery overwrote an edited plant half");
        require(getChunk(world, ANCHOR).getAttached(TemporaryTerrain.ATTACHMENT) == null, "Settled recovery retained stale receipts");
    }

    private static void plantTallGrass(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos, LOWER, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        world.setBlockState(pos.up(), UPPER, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private static TemporaryBlockManager.TemporaryPlacement place(ServerWorld world, BlockPos pos, BlockState state, UUID owner) {
        var result = TemporaryBlockManager.placeTrackedTemporaryBlocks(world, List.of(pos), state, 2000, ignored -> true, owner);
        require(result.placedCount() == 1, "Could not place " + state + " at " + pos);
        return result;
    }

    private static WorldChunk getChunk(ServerWorld world, BlockPos pos) {
        return world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void finish(MinecraftServer server, String message, boolean abrupt) throws Exception {
        Files.writeString(PASSED, message + "\n");
        if (abrupt) Runtime.getRuntime().halt(0);
        server.stop(false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
