package com.anton.elementalwands.arena;

import com.anton.elementalwands.item.SpaceAbilityHandler;
import com.anton.elementalwands.party.PartyManager;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Exercise Blink's real cast and the public party commands in a disposable world. */
public final class PlaytestSystemsSmoke implements ModInitializer {
    private int ticks;

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks != 30) return;
            try {
                Path partyFile = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/parties.json");
                String phase = System.getProperty("ew.fixture.phase", "");
                if (phase.equals("damaged")) {
                    var member = player(server, ".BedrockCaster", 0.5, 0.5);
                    require(!PartyManager.get(server).available(), "Broken storage stayed active");
                    require(server.getCommandManager().getDispatcher().execute("party create", member.getCommandSource()) == 0,
                            "A disabled party command succeeded");
                    require(Files.readString(partyFile).equals("{broken"), "Startup or a command overwrote the damaged save");
                    Files.writeString(Path.of("PLAYTEST_SYSTEMS_PASSED.txt"), "Server started with damaged party data and preserved it.");
                } else {
                    run(server);
                    if (phase.equals("prepareDamaged")) Files.writeString(partyFile, "{broken");
                    Files.writeString(Path.of("PLAYTEST_SYSTEMS_PASSED.txt"), "Blink body sweep and Bedrock party commands passed.");
                }
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("PLAYTEST_SYSTEMS_FAILED.txt"), failure.toString()); }
                catch (java.io.IOException io) { failure.addSuppressed(io); }
            } finally {
                server.stop(false);
            }
        });
    }

    private void run(MinecraftServer server) throws Exception {
        ServerWorld world = server.getOverworld();
        var caster = player(server, ".BedrockCaster", 0.5, 0.5);
        var friend = player(server, ".BedrockAlly", 20.5, 0.5);
        var side = player(server, "BlinkSide", 10.85, 0.5);
        var diagonal = player(server, "BlinkDiagonal", 30.5, 0.5);
        var commands = server.getCommandManager().getDispatcher();
        require(commands.execute("party create", caster.getCommandSource()) == 1, "Create");
        require(commands.execute("party invite .BedrockAlly", caster.getCommandSource()) == 1, "Invite");
        require(commands.execute("party accept .BedrockCaster", friend.getCommandSource()) == 1, "Accept");
        require(PartyManager.get(server).allied(caster.getUuid(), friend.getUuid()), "UUID membership");
        require(commands.execute("party transfer .BedrockAlly", caster.getCommandSource()) == 1, "Transfer");
        require(commands.execute("party kick " + caster.getUuid(), friend.getCommandSource()) == 1, "Kick by UUID");

        // Both the normal and raised paths hit the low block immediately in front of the caster.
        BlockPos curb = new BlockPos(0, 200, 1);
        world.setBlockState(curb, Blocks.STONE.getDefaultState());
        Vec3d start = caster.getEntityPos();
        cast(world, caster);
        require(start.squaredDistanceTo(caster.getEntityPos()) < 0.00001, "Blinked through a feet-only obstruction");
        world.setBlockState(curb, Blocks.AIR.getDefaultState());
        cast(world, caster);
        require(caster.getZ() - start.z > 9.9, "Failed blink spent its cooldown or the clear lane was blocked");

        // The central eye ray misses this wall. The right edge of the body overlaps it.
        for (int y = 200; y < 204; y++) world.setBlockState(new BlockPos(11, y, 3), Blocks.STONE.getDefaultState());
        cast(world, side);
        require(side.getZ() > 1.4 && side.getZ() < 2.71, "Blink failed to shorten for a shoulder obstruction");

        // An obstacle inside the diagonal's broad search box but well off the line must be ignored.
        world.setBlockState(new BlockPos(31, 200, 6), Blocks.STONE.getDefaultState());
        diagonal.setYaw(-45);
        start = diagonal.getEntityPos();
        cast(world, diagonal);
        require(start.distanceTo(diagonal.getEntityPos()) > 9.9, "Clear diagonal was shortened");
    }

    private static ServerPlayerEntity player(MinecraftServer server, String name, double x, double z) throws Exception {
        var player = GuardianNaveSmokeMod.player(server, UUID.randomUUID(), name, x, 200, z);
        player.setYaw(0);
        player.setPitch(0);
        player.onTeleportationDone();
        return player;
    }

    private static void cast(ServerWorld world, ServerPlayerEntity player) {
        SpaceAbilityHandler.castSecondary(world, player, ModItems.FRACTURED_WAND.getDefaultStack());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
