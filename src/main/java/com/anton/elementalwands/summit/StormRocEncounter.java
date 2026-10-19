package com.anton.elementalwands.summit;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.registry.ModEntities;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Server-owned, sealed fight rosters. Original modes are journalled before admission. */
public final class StormRocEncounter {
    private StormRocEncounter() {}
    private static final Map<BlockPos, Fight> fights = new HashMap<>();
    // Perches are six chunks beyond the players. An active fight must keep ticking even
    // on a server with a low simulation distance. No SERIALIZE flag: crashes leave no tickets.
    private static final net.minecraft.server.world.ChunkTicketType FIGHT_TICKET = net.minecraft.registry.Registry.register(
            net.minecraft.registry.Registries.TICKET_TYPE, net.minecraft.util.Identifier.of("elementalwands", "roc_fight"),
            new net.minecraft.server.world.ChunkTicketType(0,
                    net.minecraft.server.world.ChunkTicketType.FOR_LOADING | net.minecraft.server.world.ChunkTicketType.FOR_SIMULATION));
    private static final int TICKET_RADIUS = 7 + net.minecraft.server.world.ChunkLevels.getLevelFromType(net.minecraft.server.world.ChunkLevelType.FULL)
            - net.minecraft.server.world.ChunkLevels.getLevelFromType(net.minecraft.server.world.ChunkLevelType.ENTITY_TICKING);
    private static final class Fight {
        final BlockPos centre;
        final StormRocEntity boss;
        final Set<UUID> roster = new HashSet<>(), standing = new HashSet<>();
        Fight(BlockPos centre, StormRocEntity boss) { this.centre = centre; this.boss = boss; }
    }
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StormRocEncounter::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) eliminate(player);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return true;
            Fight fight = at(victim);
            if (source.getAttacker() instanceof StormRocEntity roc)
                return roc.eligible(victim) && (fight == null || fight.boss == roc && fight.standing.contains(victim.getUuid()));
            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                Fight attackers = of(attacker);
                if (fight != null || attackers != null)
                    return fight != null && attackers == fight && fight.standing.contains(victim.getUuid()) && fight.standing.contains(attacker.getUuid());
            }
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> eliminate(handler.getPlayer()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> stopAll(server, "Storm Roc encounter reset after reload."));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopAll(server, "Storm Roc encounter stopped."));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> fights.clear());
    }
    public static boolean activeAt(BlockPos centre) { return fights.containsKey(centre); }
    public static boolean enrolled(PlayerEntity player) { return of(player) != null; }
    private static Fight of(PlayerEntity player) {
        for (Fight fight : fights.values()) if (fight.roster.contains(player.getUuid())) return fight;
        return null;
    }
    private static Fight at(PlayerEntity player) {
        return StormSummitManager.inRealm(player) ? fights.get(StormSummit.nearestCentre(player.getEntityPos())) : null;
    }
    public static String start(MinecraftServer server, BlockPos centre) {
        if (activeAt(centre)) return "This summit already has a fight in progress.";
        ServerWorld realm = server.getWorld(StormSummit.WORLD);
        if (realm == null) return "Storm Summit is unavailable.";
        var modes = StormSummitManager.savedModes();
        if (modes == null) return "Storm Summit storage is unavailable.";
        List<ServerPlayerEntity> players = realm.getPlayers().stream().filter(p -> p.isAlive()
                && !p.isCreative() && !p.isSpectator() && StormSummit.nearestCentre(p.getEntityPos()).equals(centre)
                && !StormSummit.fallen(centre, p.getEntityPos()) && !enrolled(p)).toList();
        if (players.isEmpty()) return "No living Survival or Adventure challengers are on this summit.";
        for (var player : players) modes.putIfAbsent(player.getUuidAsString(), player.interactionManager.getGameMode().getId());
        if (!StormSummitManager.persist()) return "Could not record player recovery; fight was not started.";
        StormRocEntity boss = new StormRocEntity(ModEntities.STORM_ROC, realm);
        Fight fight = new Fight(centre.toImmutable(), boss);
        for (var player : players) { fight.roster.add(player.getUuid()); fight.standing.add(player.getUuid()); }
        fights.put(fight.centre, fight);
        realm.getChunkManager().addTicket(FIGHT_TICKET, new net.minecraft.util.math.ChunkPos(fight.centre), TICKET_RADIUS);
        try {
            boss.startFight(centre, players);
            if (!realm.spawnEntity(boss)) {
                end(server, fight, "Storm Roc could not spawn; encounter reset.");
                return "Storm Roc could not spawn.";
            }
        } catch (RuntimeException failure) {
            end(server, fight, "Storm Roc could not begin; encounter reset.");
            return "Storm Roc could not begin: " + failure.getClass().getSimpleName() + ".";
        }
        announce(server, fight, "The Storm Roc attacks. The challenger roster is sealed.");
        return "Started the fight with " + players.size() + " challengers.";
    }
    public static String skip(MinecraftServer server, BlockPos centre) {
        Fight fight = fights.get(centre);
        if (fight == null) return "No active fight on this summit.";
        if (!fight.boss.skipToPhaseTwo()) return "The Roc is already in or entering phase two.";
        announce(server, fight, "An operator skipped the fight to the Storm Roc's phase two.");
        return "Skipping to phase two.";
    }
    public static String reset(MinecraftServer server, BlockPos centre) {
        Fight fight = fights.get(centre);
        if (fight == null) return "No active fight on this summit.";
        end(server, fight, "Storm Roc encounter reset. You may retry.");
        return "Storm Roc encounter reset.";
    }
    public static String status() {
        if (fights.isEmpty()) return " No active Roc fights.";
        StringBuilder result = new StringBuilder();
        for (Fight fight : fights.values()) result.append(" Roc at ").append(fight.centre.getX()).append(": ")
                .append(fight.boss.status()).append("; standing ").append(fight.standing.size()).append('/').append(fight.roster.size()).append('.');
        return result.toString();
    }
    public static void leave(ServerPlayerEntity player) {
        Fight fight = of(player);
        eliminate(player);
        if (fight != null) fight.roster.remove(player.getUuid());
        restore(player);
    }
    private static void eliminate(ServerPlayerEntity player) {
        Fight fight = of(player);
        if (fight == null || !fight.standing.remove(player.getUuid())) return;
        fight.boss.removeParticipant(player.getUuid());
        player.sendMessage(Text.literal("You are defeated. You may spectate until the encounter ends, or /ew summit leave."), false);
        if (player.isAlive() && StormSummitManager.inRealm(player)) spectate(player, fight);
    }
    private static void spectate(ServerPlayerEntity player, Fight fight) {
        player.changeGameMode(GameMode.SPECTATOR);
        StormSummitManager.transfer(player, player.getEntityWorld().getServer().getWorld(StormSummit.WORLD), StormSummit.arrival(fight.centre), StormSummit.ARRIVAL_YAW, 0);
    }
    /** Encounter falls eliminate immediately without a death screen or inventory loss. */
    public static boolean handlesFall(ServerPlayerEntity player) {
        Fight fight = of(player);
        if (fight == null) return false;
        if (fight.standing.contains(player.getUuid()) && StormSummit.fallen(fight.centre, player.getEntityPos())) eliminate(player);
        return true;
    }
    public static boolean canCast(PlayerEntity player) {
        Fight fight = of(player), local = at(player);
        return local == null && fight == null || fight != null && fight == local && fight.standing.contains(player.getUuid()) && player.isAlive() && !player.isSpectator() && !player.isCreative();
    }
    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (StormSummitManager.internalTeleport()) return true;
        Fight fight = of(player);
        Fight targetFight = destination.getRegistryKey() == StormSummit.WORLD ? fights.get(StormSummit.nearestCentre(target)) : null;
        if (fight != null) return destination.getRegistryKey() == StormSummit.WORLD && StormSummit.footprint(fight.centre).contains(target);
        return targetFight == null;
    }
    private static void tick(MinecraftServer server) {
        for (Fight fight : List.copyOf(fights.values())) {
            for (UUID id : List.copyOf(fight.standing)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) { fight.standing.remove(id); fight.boss.removeParticipant(id); }
                else if (!player.isAlive() || !StormSummitManager.inRealm(player) || !StormSummit.nearestCentre(player.getEntityPos()).equals(fight.centre) || player.isCreative() || player.isSpectator()) eliminate(player);
            }
            // A defeat earned in the same tick the last challenger fell is still a victory.
            if (fight.boss.fightComplete()) {
                end(server, fight, "The Storm Roc falls. Its death and the shard reward are still to come; you may fight it again.");
                continue;
            }
            if (fight.standing.isEmpty()) { end(server, fight, "The challengers have fallen. You may retry."); continue; }
            if (fight.boss.isRemoved() || !fight.boss.isAlive()) { end(server, fight, "Storm Roc encounter interrupted. You may retry."); continue; }
            for (UUID id : fight.roster) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null && player.isAlive() && !fight.standing.contains(id) && (!StormSummitManager.inRealm(player) || !player.isSpectator())) spectate(player, fight);
            }
        }
        // Offline recovery records survive stops/crashes and are applied on the next login.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) if (!enrolled(player)) restore(player);
    }
    private static void restore(ServerPlayerEntity player) {
        var modes = StormSummitManager.savedModes();
        if (modes == null) return;
        String mode = modes.get(player.getUuidAsString());
        if (mode == null || !player.isAlive()) return;
        player.changeGameMode(GameMode.byId(mode, GameMode.SURVIVAL));
        // Save the restored mode before retiring its journal entry: a crash between these
        // writes must replay recovery, never leave a saved spectator without a record.
        player.getEntityWorld().getServer().getPlayerManager().saveAllPlayerData();
        modes.remove(player.getUuidAsString());
        StormSummitManager.persist();
    }
    private static void end(MinecraftServer server, Fight fight, String message) {
        fights.remove(fight.centre);
        ServerWorld realm=server.getWorld(StormSummit.WORLD);
        if(realm!=null)realm.getChunkManager().removeTicket(FIGHT_TICKET,new net.minecraft.util.math.ChunkPos(fight.centre),TICKET_RADIUS);
        fight.boss.stopFight();
        fight.boss.discard();
        announce(server, fight, message);
        for (UUID id : fight.roster) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
                if (player.isAlive() && StormSummitManager.inRealm(player)) StormSummitManager.transfer(player, server.getWorld(StormSummit.WORLD), StormSummit.arrival(fight.centre), StormSummit.ARRIVAL_YAW, 0);
                restore(player);
            }
        }
    }
    private static void announce(MinecraftServer server, Fight fight, String message) {
        for (UUID id : fight.roster) { var player = server.getPlayerManager().getPlayer(id); if (player != null) player.sendMessage(Text.literal(message), false); }
    }
    private static void stopAll(MinecraftServer server, String message) { for (Fight fight : List.copyOf(fights.values())) end(server, fight, message); }
}
