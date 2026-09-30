package com.anton.elementalwands.arena;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianIntro;
import com.anton.elementalwands.entity.GuardianIntroZombieEntity;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Test-only Fabric entrypoint, packaged solely by the optional nave smoke task. Drives the Guardian
 * ritual through the Shattered Nave with simulated players: layout, arrival, the Guardian's intro
 * (played in full once, then skipped), protection and containment, spectating with kept belongings,
 * victory, a wipe, and a restart.
 */
public final class GuardianNaveSmokeMod implements ModInitializer {
    private static final String SITE = "smoke|0|0|0";
    private static final Path SPECTATOR = Path.of("NAVE_SPECTATOR.txt");
    private int ticks, stage, mark, ground;
    private ServerPlayerEntity first, second;
    private FracturedGuardianEntity guardian;
    private BlockPos centre;
    private boolean accepted;
    private String previous = "";
    private Vec3d held;

    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { tick(server); }
            catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("SMOKE_FAILED.txt"), failure.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void tick(MinecraftServer server) throws Exception {
        ticks++;
        String status = GuardianArenaManager.status();
        if (!status.equals(previous)) { System.out.println("NAVE SMOKE: " + status); previous = status; }
        require(ticks < 3600, "Nave smoke exceeded three minutes at stage " + stage + ": " + status);
        ServerWorld world = server.getOverworld(), nave = server.getWorld(ShatteredNave.WORLD);
        require(nave != null, "The Shattered Nave dimension is not registered");
        if (Boolean.getBoolean("ew.nave.recovery")) { recover(server, world, nave, status); return; }
        if (ticks == 30) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) { world.setChunkForced(x, z, true); world.getChunk(x, z); }
            ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, 0, 0);
            first = player(server, world, UUID.randomUUID(), "NaveOne", .5, ground, .5);
            second = player(server, world, UUID.randomUUID(), "NaveTwo", 3.5, ground, .5);
            second.changeGameMode(GameMode.ADVENTURE);
            second.getInventory().insertStack(new ItemStack(Items.DIAMOND, 3));
            ritual(first);
            require(!GuardianArenaManager.canCast(first), "A sealed player can cast before the Guardian wakes");
            require(GuardianArenaManager.ritual(first, SITE, BlockPos.ORIGIN, () -> {}).startsWith("The heart is already"),
                    "A repeated ritual opened a second fight");
            require(!GuardianArenaManager.canTeleport(first, nave, new Vec3d(.5, 64, 28.5)), "A player can teleport into the nave by themselves");
            stage = 1;
        }
        if (stage == 1 && GuardianArenaManager.inRealm(first)) {
            centre = ShatteredNave.nearestCentre(first.getEntityPos());
            // A real client confirms each move into the nave; until then a player cannot be hurt at all.
            first.onTeleportationDone();
            second.onTeleportationDone();
            require(GuardianArenaManager.inRealm(second), "The ritual left the second player behind");
            require(Math.abs(first.getZ() - centre.getZ() - 28.5) < .01 && Math.abs(Math.abs(first.getX() - second.getX()) - 2) < .01,
                    "Players did not arrive side by side facing the seat");
            require(nave.getBlockState(centre).isOf(Blocks.CHISELED_STONE_BRICKS), "The effigy seat is missing");
            require(nave.getBlockState(centre.add(ShatteredNave.HALF, 5, 0)).isOf(Blocks.BARRIER), "The invisible wall is missing");
            require(nave.getBlockState(centre.add(0, ShatteredNave.CEILING + 1, 0)).isOf(Blocks.BARRIER), "The invisible lid is missing");
            // Pillars are hollow shells; the ring pillar east of the floor carries a lit medallion facing it.
            require(nave.getBlockState(centre.add(77, 30, 0)).isOf(Blocks.SEA_LANTERN), "The ring pillar's medallion is missing");
            require(nave.getBlockState(centre.add(-30, 1, 14)).isOf(Blocks.LIGHT), "A light shaft's floor pool has no light");
            require(!GuardianArenaManager.canCast(first), "A player can cast before the Guardian wakes");
            float health = first.getHealth();
            first.damage(nave, nave.getDamageSources().generic(), 5);
            require(first.getHealth() == health, "A player took damage before the Guardian woke");
            stage = 2;
        }
        if (stage == 2) {
            guardian = nave.getEntitiesByClass(FracturedGuardianEntity.class, new Box(centre).expand(80), Entity::isAlive).stream().findFirst().orElse(null);
            if (guardian != null) {
                require(guardian.inIntro() && guardian.isInvulnerable() && GuardianArenaManager.owns(guardian), "The Guardian did not start its intro");
                require(guardian.getEntityPos().distanceTo(ShatteredNave.seat(centre)) < .6, "The Guardian's intro is off its seat");
                require(!GuardianArenaManager.isFighting(guardian), "The fight began before the intro");
                require(GuardianIntro.watching(first) && GuardianIntro.watching(second), "The intro did not hold both players");
                mark = ticks;
                stage = 3;
            }
        }
        if (stage == 3 && !GuardianArenaManager.isFighting(guardian)) {
            // The whole scene plays once: watchers held still and unhurt, the Guardian untouchable,
            // and the zombie it tears apart on stage until the scene ends.
            int t = ticks - mark;
            if (t == 6) { held = first.getEntityPos(); first.setPosition(held.add(2, 0, 0)); }
            if (t == 8) require(first.getEntityPos().distanceTo(held) < .05, "The intro did not hold its watcher still");
            if (t == 12) {
                require(zombies(nave) == 1, "The intro's zombie is not on stage");
                first.onTeleportationDone();
                float health = first.getHealth(), stone = guardian.getHealth();
                first.damage(nave, nave.getDamageSources().generic(), 5);
                guardian.damage(nave, nave.getDamageSources().playerAttack(first), 5);
                require(first.getHealth() == health && guardian.getHealth() == stone, "Someone was hurt during the intro");
            }
            if (t == GuardianIntro.POINT) require(zombies(nave) == 1, "The intro's zombie left before the scene ended");
        }
        if (stage == 3 && GuardianArenaManager.isFighting(guardian)) {
            require(ticks - mark >= GuardianIntro.LENGTH - 2, "The fight began before the intro ended");
            require(!GuardianIntro.watching(first) && !GuardianIntro.watching(second) && !guardian.inIntro(), "The intro still holds its watchers");
            require(zombies(nave) == 0, "The intro's zombie outlived the scene");
            first.onTeleportationDone();
            second.onTeleportationDone();
            Vec3d seat = ShatteredNave.seat(centre);
            require(guardian.getEntityPos().distanceTo(seat) < .6 && !guardian.isInvulnerable(), "The Guardian did not wake on the seat");
            require(GuardianArenaManager.canCast(first) && GuardianArenaManager.eligible(guardian, first), "A standing fighter cannot fight");
            checkProtection(nave, world);
            second.kill(nave);
            respawn(server, second);
            mark = ticks;
            stage = 4;
        }
        if (stage == 4 && ticks - mark == 3) {
            second = server.getPlayerManager().getPlayer(second.getUuid());
            require(second.isAlive() && second.isSpectator() && GuardianArenaManager.inRealm(second), "The fallen player is not watching the fight");
            require(second.getInventory().count(Items.DIAMOND) == 3, "A death in the nave dropped the player's belongings");
            require(!GuardianArenaManager.eligible(guardian, second) && !GuardianArenaManager.canCast(second), "A spectator can fight or cast");
            require(!GuardianArenaManager.canTeleport(second, nave, Vec3d.of(centre).add(400, 20, 0)), "A spectator can leave the slot");
            require(GuardianArenaManager.canTeleport(second, nave, Vec3d.of(centre).add(20, 20, 20)), "A spectator cannot move within the slot");
            require(GuardianArenaManager.spectatorViewer(guardian, second), "A spectator cannot see the Guardian's health");
            float health = guardian.getHealth();
            guardian.damage(nave, nave.getDamageSources().playerAttack(second), 5);
            require(guardian.getHealth() == health, "A spectator damaged the Guardian");
            guardian.kill(nave);
            require(status().contains(", won"), "The Guardian's death was not recorded as a victory");
            stage = 5;
        }
        if (stage == 5 && !status.contains("fight:")) {
            first = server.getPlayerManager().getPlayer(first.getUuid());
            second = server.getPlayerManager().getPlayer(second.getUuid());
            require(first.getEntityWorld() == world && first.getEntityPos().distanceTo(new Vec3d(.5, ground, .5)) < 1, "The victor was not sent back to the church");
            require(second.getEntityWorld() == world && second.interactionManager.getGameMode() == GameMode.ADVENTURE, "The spectator did not get Adventure mode back");
            ritual(first);
            stage = 6;
        }
        // Later fights skip the intro: it ends early only once everyone has asked to.
        if ((stage == 6 || stage == 8) && guardian(nave) != null && guardian.inIntro()) {
            if (GuardianIntro.watching(first)) GuardianIntro.skip(first);
            if (GuardianIntro.watching(second)) GuardianIntro.skip(second);
        }
        if (stage == 6 && guardian(nave) != null && GuardianArenaManager.isFighting(guardian)) {
            require(zombies(nave) == 0, "A skipped intro left its zombie behind");
            first.onTeleportationDone();
            second.onTeleportationDone();
            first.kill(nave);
            respawn(server, first);
            second.kill(nave);
            respawn(server, second);
            stage = 7;
        }
        if (stage == 7 && !status.contains("fight:")) {
            first = server.getPlayerManager().getPlayer(first.getUuid());
            second = server.getPlayerManager().getPlayer(second.getUuid());
            require(first.isAlive() && first.getEntityWorld() == world && first.interactionManager.getGameMode() == GameMode.SURVIVAL,
                    "A wiped player did not return to Survival at the church");
            require(second.getEntityWorld() == world && second.interactionManager.getGameMode() == GameMode.ADVENTURE, "A wiped observer lost Adventure mode");
            require(nave.getEntitiesByClass(FracturedGuardianEntity.class, new Box(centre).expand(80), Entity::isAlive).isEmpty(), "The wipe left the Guardian standing");
            ritual(first);
            stage = 8;
        }
        if (stage == 8 && guardian(nave) != null && GuardianArenaManager.isFighting(guardian)) {
            second.kill(nave);
            respawn(server, second);
            mark = ticks;
            stage = 9;
        }
        if (stage == 9 && ticks - mark == 3) {
            second = server.getPlayerManager().getPlayer(second.getUuid());
            require(second.isSpectator(), "The restart check needs a saved spectator");
            Files.writeString(SPECTATOR, second.getUuidAsString());
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(second.networkHandler, server);
            server.getPlayerManager().remove(second);
            Files.writeString(Path.of("SMOKE_PASSED.txt"), "Ritual admission, nave layout, arrival, the Guardian's full intro and a unanimous skip, casting and damage gates, "
                    + "floor and hall protection, spell writes, explosions, containment, spectating with kept belongings, victory return, "
                    + "a party wipe and a retry passed. Stopping mid-fight to exercise restart recovery.\n");
            System.out.println("NAVE SMOKE PASSED — stopping mid-fight to exercise recovery");
            server.stop(false);
        }
    }

    private void checkProtection(ServerWorld nave, ServerWorld world) {
        BlockPos floor = centre.add(5, 0, 5), pillar = centre.add(77, 30, 0), space = centre.add(3, 1, 3);
        var original = nave.getBlockState(floor);
        require(!nave.setBlockState(floor, Blocks.AIR.getDefaultState()), "The fight floor can be broken");
        require(!nave.setBlockState(pillar, Blocks.AIR.getDefaultState()), "A pillar can be broken");
        require(nave.setBlockState(space, Blocks.STONE.getDefaultState()) && nave.setBlockState(space, Blocks.AIR.getDefaultState()),
                "Spells cannot place and clear their own blocks above the floor");
        require(GuardianArenaManager.setTemporarySpellBlock(nave, floor, Blocks.MAGMA_BLOCK.getDefaultState()), "A temporary spell block cannot cover the floor");
        require(!GuardianArenaManager.setTemporarySpellBlock(nave, floor, Blocks.AIR.getDefaultState()), "A temporary spell write opened a hole in the floor");
        require(GuardianArenaManager.setTemporarySpellBlock(nave, floor, original) && nave.getBlockState(floor).equals(original), "The covered floor was not restored");
        require(!GuardianArenaManager.setTemporarySpellBlock(nave, pillar.add(0, 0, 12), Blocks.STONE.getDefaultState()), "A temporary spell block reached into the hall");
        BlockPos blast = centre.add(10, 0, 10);
        var beforeBlast = nave.getBlockState(blast);
        nave.createExplosion(null, blast.getX() + .5, blast.getY() + 1, blast.getZ() + .5, 4, World.ExplosionSourceType.TNT);
        require(nave.getBlockState(blast).equals(beforeBlast), "An explosion damaged the floor");
        double before = first.getX();
        first.requestTeleport(centre.getX() + 200, first.getY(), centre.getZ());
        require(first.getX() == before, "A fighter teleported out of the walled floor");
        require(!first.teleport(world, 0, 80, 0, Set.of(), 0, 0, false), "A fighter teleported out of the nave");
        first.requestTeleport(before + 2, first.getY(), first.getZ());
        require(first.getX() == before + 2, "A teleport within the floor was blocked");
    }

    private void recover(MinecraftServer server, ServerWorld world, ServerWorld nave, String status) throws Exception {
        if (ticks == 100) {
            require(!status.contains("fight:"), "A restart resumed a fight: " + status);
            UUID id = UUID.fromString(Files.readString(SPECTATOR).trim());
            // The spectator was saved above the floor; rejoining there sends them home with their game mode.
            second = player(server, nave, id, "NaveTwo", .5, 80, 40.5);
            second.changeGameMode(GameMode.SPECTATOR);
            ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(second.networkHandler, null, server);
        }
        if (ticks == 103) {
            require(second.getEntityWorld() == world, "The offline spectator was not sent home after the restart");
            require(second.interactionManager.getGameMode() == GameMode.ADVENTURE, "The offline spectator did not get Adventure mode back");
            Files.writeString(Path.of("SMOKE_RECOVERY_PASSED.txt"), "The interrupted fight was cancelled; the offline spectator rejoined at the church in Adventure mode.\n");
            System.out.println("NAVE SMOKE RECOVERY PASSED");
            server.stop(false);
        }
    }

    private int zombies(ServerWorld nave) {
        return nave.getEntitiesByClass(GuardianIntroZombieEntity.class, new Box(centre).expand(80), Entity::isAlive).size();
    }

    private void ritual(ServerPlayerEntity caller) {
        accepted = false;
        String result = GuardianArenaManager.ritual(caller, SITE, BlockPos.ORIGIN.up(ground), () -> accepted = true);
        System.out.println("NAVE SMOKE RITUAL: " + result);
        require(result.startsWith("The heart pulls") && accepted && GuardianArenaManager.hosts(SITE), "The ritual was refused: " + result);
    }

    private FracturedGuardianEntity guardian(ServerWorld nave) {
        guardian = nave.getEntitiesByClass(FracturedGuardianEntity.class, new Box(centre).expand(80), Entity::isAlive).stream().findFirst().orElse(null);
        return guardian;
    }

    private static String status() { return GuardianArenaManager.status(); }

    /** A simulated player has no client to press Respawn; do what the respawn packet does. */
    private static void respawn(MinecraftServer server, ServerPlayerEntity dead) {
        var handler = dead.networkHandler;
        handler.player = server.getPlayerManager().respawnPlayer(dead, false, Entity.RemovalReason.KILLED);
    }

    /** A simulated player standing in the Overworld, for fixtures that borrow this helper. */
    static ServerPlayerEntity player(MinecraftServer server, UUID uuid, String name, double x, double y, double z) throws Exception {
        return player(server, server.getOverworld(), uuid, name, x, y, z);
    }

    @SuppressWarnings("unchecked")
    static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, UUID uuid, String name, double x, double y, double z) throws Exception {
        GameProfile profile = new GameProfile(uuid, name);
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, SyncedClientOptions.createDefault());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {}
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) {}
            @Override public void flush() {}
            @Override public boolean isOpen() { return true; }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server, connection, player, ConnectedClientData.createDefault(profile, false));
        player.setPosition(x, y, z);
        var field = PlayerManager.class.getDeclaredField("playerMap");
        field.setAccessible(true);
        ((Map<UUID, ServerPlayerEntity>) field.get(server.getPlayerManager())).put(uuid, player);
        server.getPlayerManager().getPlayerList().add(player);
        world.onPlayerConnected(player);
        return player;
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
