package com.anton.elementalwands.party;

import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

public final class PartyManager {
    private record Invite(UUID party, UUID leader, int expires) {}
    private static final Map<MinecraftServer, PartyManager> SERVERS = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final PartyStore store;
    private final Map<UUID, Map<UUID, Invite>> invites = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Integer> inviteCooldowns = new HashMap<>();

    private PartyManager(MinecraftServer server) throws IOException {
        this.server = server;
        store = new PartyStore(server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/parties.json"));
    }
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { SERVERS.put(server, new PartyManager(server)); }
            catch (IOException e) { throw new IllegalStateException("Cannot safely load Elemental Wands parties", e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PartyManager manager = SERVERS.get(server);
            if (manager != null && server.getTicks() % 20 == 0) manager.expireInvites();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> get(server).remember(handler.getPlayer()));
    }
    public static PartyManager get(MinecraftServer server) {
        return Objects.requireNonNull(SERVERS.get(server), "Parties are not loaded");
    }
    public PartyStore.Party party(UUID player) { return store.party(player); }
    public boolean allied(UUID first, UUID second) { return store.allied(first, second); }
    public void remember(ServerPlayerEntity player) { names.put(player.getUuid(), player.getName().getString()); }
    public String name(UUID uuid) {
        var online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) { remember(online); return names.get(uuid); }
        if (names.containsKey(uuid)) return names.get(uuid);
        var party = party(uuid);
        return party == null ? null : party.members().get(uuid);
    }
    private void expireInvites() {
        invites.values().forEach(map -> map.values().removeIf(invite -> invite.expires <= server.getTicks()
                || party(invite.leader) == null || !party(invite.leader).id().equals(invite.party)
                || !party(invite.leader).leader().equals(invite.leader)));
        invites.values().removeIf(Map::isEmpty);
        inviteCooldowns.values().removeIf(tick -> tick <= server.getTicks());
    }
    public void create(ServerPlayerEntity player) throws IOException {
        require(party(player.getUuid()) == null, "You are already in a party. Use /party leave first.");
        remember(player);
        store.replace(null, new PartyStore.Party(UUID.randomUUID(), player.getUuid(), Map.of(player.getUuid(), name(player.getUuid()))));
        tell(player, "Party created. Invite someone with /party invite <player>.");
    }
    public void invite(ServerPlayerEntity player, ServerPlayerEntity target) {
        var party = leader(player);
        require(party(target.getUuid()) == null, "That player is already in a party.");
        require(party.members().size() < PartyStore.MAX_MEMBERS, "Your party is full (32 players).");
        require(inviteCooldowns.getOrDefault(player.getUuid(), 0) <= server.getTicks(), "Wait a few seconds before another invitation.");
        expireInvites();
        var pending = invites.computeIfAbsent(target.getUuid(), key -> new LinkedHashMap<>());
        require(pending.size() < 16 || pending.containsKey(player.getUuid()), "That player has too many pending invitations.");
        pending.put(player.getUuid(), new Invite(party.id(), player.getUuid(), server.getTicks() + 1200));
        inviteCooldowns.put(player.getUuid(), server.getTicks() + 60);
        tell(player, "Invited " + target.getName().getString() + ". The invitation expires in 60 seconds.");
        tell(target, player.getName().getString() + " invited you to a party. /party accept " + player.getName().getString()
                + " or /party decline " + player.getName().getString() + " (60 seconds).");
    }
    private Invite invitation(ServerPlayerEntity player, String inviter) {
        expireInvites();
        var pending = invites.getOrDefault(player.getUuid(), Map.of());
        if (inviter == null) {
            require(pending.size() == 1, pending.isEmpty() ? "You have no pending invitations." : "Choose an invitation: /party accept <leader>.");
            return pending.values().iterator().next();
        }
        return pending.values().stream().filter(invite -> inviter.equalsIgnoreCase(name(invite.leader)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No pending invitation from that player."));
    }
    public void accept(ServerPlayerEntity player, String inviter) throws IOException {
        require(party(player.getUuid()) == null, "Leave your current party before accepting an invitation.");
        Invite invite = invitation(player, inviter);
        var old = party(invite.leader);
        require(old.members().size() < PartyStore.MAX_MEMBERS, "That party is full (32 players).");
        remember(player);
        var members = new LinkedHashMap<>(old.members());
        members.put(player.getUuid(), name(player.getUuid()));
        var next = new PartyStore.Party(old.id(), old.leader(), members);
        store.replace(old, next);
        invites.remove(player.getUuid());
        announce(next, name(player.getUuid()) + " joined the party. Allied wand protection is active.");
    }
    public void decline(ServerPlayerEntity player, String inviter) {
        Invite invite = invitation(player, inviter);
        invites.get(player.getUuid()).remove(invite.leader);
        tell(player, "Invitation declined.");
    }
    public void leave(ServerPlayerEntity player) throws IOException {
        var old = member(player);
        var members = new LinkedHashMap<>(old.members()); members.remove(player.getUuid());
        UUID nextLeader = old.leader().equals(player.getUuid()) && !members.isEmpty()
                ? members.keySet().iterator().next() : old.leader();
        var next = members.isEmpty() ? null : new PartyStore.Party(old.id(), nextLeader, members);
        store.replace(old, next);
        expireInvites();
        tell(player, "You left the party.");
        if (next != null) announce(next, player.getName().getString() + " left. Leader: " + name(nextLeader) + ".");
    }
    public void kick(ServerPlayerEntity player, String targetName) throws IOException {
        var old = leader(player); UUID target = findMember(old, targetName);
        require(!target.equals(player.getUuid()), "Use /party leave to leave your own party.");
        var members = new LinkedHashMap<>(old.members()); members.remove(target);
        var next = new PartyStore.Party(old.id(), old.leader(), members);
        store.replace(old, next);
        var online = server.getPlayerManager().getPlayer(target);
        if (online != null) tell(online, "You were removed from the party.");
        announce(next, targetName + " was removed from the party.");
    }
    public void transfer(ServerPlayerEntity player, String targetName) throws IOException {
        var old = leader(player); UUID target = findMember(old, targetName);
        require(!target.equals(old.leader()), "You are already the leader.");
        var next = new PartyStore.Party(old.id(), target, old.members());
        store.replace(old, next); expireInvites();
        announce(next, name(target) + " is now the party leader.");
    }
    public void disband(ServerPlayerEntity player) throws IOException {
        var old = leader(player); store.replace(old, null); expireInvites();
        announce(old, "The party was disbanded.");
    }
    public void list(ServerPlayerEntity player) {
        var party = party(player.getUuid());
        if (party == null) {
            tell(player, "You are not in a party. /party create | invite <player> | accept [leader] | decline [leader]");
        } else {
            tell(player, "Party (" + party.members().size() + "/32), leader: " + name(party.leader()));
            for (UUID member : party.members().keySet()) tell(player, name(member)
                    + (member.equals(party.leader()) ? " [leader]" : "")
                    + (server.getPlayerManager().getPlayer(member) == null ? " (offline)" : " (online)"));
            tell(player, "/party leave | invite <player> | kick <member> | transfer <member> | disband");
        }
        expireInvites();
        for (UUID leader : invites.getOrDefault(player.getUuid(), Map.of()).keySet())
            tell(player, "Pending invitation: /party accept " + name(leader));
    }
    public List<String> memberNames(ServerPlayerEntity player) {
        var party = party(player.getUuid());
        return party == null ? List.of() : party.members().keySet().stream().map(this::name).toList();
    }
    private UUID findMember(PartyStore.Party party, String target) {
        return party.members().keySet().stream().filter(id -> target.equalsIgnoreCase(name(id))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That player is not in your party."));
    }
    private PartyStore.Party member(ServerPlayerEntity player) {
        var party = party(player.getUuid()); require(party != null, "You are not in a party."); return party;
    }
    private PartyStore.Party leader(ServerPlayerEntity player) {
        var party = member(player); require(party.leader().equals(player.getUuid()), "Only the party leader can do that."); return party;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static void tell(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message), false); }
    private void announce(PartyStore.Party party, String message) {
        for (UUID id : party.members().keySet()) {
            var player = server.getPlayerManager().getPlayer(id); if (player != null) tell(player, message);
        }
    }
}
