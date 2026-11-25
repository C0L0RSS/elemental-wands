package com.anton.elementalwands.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** UUID membership is independent of vanilla scoreboard teams. Commit to disk before publishing changes. */
public final class PartyStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_MEMBERS = 32;
    public record Party(UUID id, UUID leader, List<UUID> members) {
        public Party { members = List.copyOf(members); }
    }
    public record Snapshot(int version, List<Party> parties) {}
    private record LegacyParty(UUID id, UUID leader, Map<UUID, String> members) {}
    private record LegacySnapshot(int version, List<LegacyParty> parties) {}
    private final Path path;
    private List<Party> parties;
    private Map<UUID, Party> membership;

    private final boolean available;

    public PartyStore(Path path) {
        this.path = path;
        boolean loaded = false;
        try {
            var json = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var parties = switch (json.get("version").getAsInt()) {
                case 1 -> JSON.fromJson(json, LegacySnapshot.class).parties().stream()
                        .map(p -> new Party(p.id(), p.leader(), List.copyOf(p.members().keySet()))).toList();
                case 2 -> JSON.fromJson(json, Snapshot.class).parties();
                default -> throw new IllegalArgumentException("Unknown party data version");
            };
            install(validate(parties));
            loaded = true;
        } catch (NoSuchFileException missing) {
            install(List.of());
            loaded = true;
        } catch (IOException | RuntimeException invalid) {
            install(List.of());
            org.slf4j.LoggerFactory.getLogger("elementalwands").error(
                    "Parties are disabled for this session. Repair {} and restart the server.", path, invalid);
        }
        available = loaded;
    }

    public boolean available() { return available; }

    private static List<Party> validate(List<Party> input) {
        Set<UUID> ids = new HashSet<>(), members = new HashSet<>();
        for (Party party : input) {
            if (party.id == null || !ids.add(party.id) || !party.members.contains(party.leader)
                    || party.members.isEmpty() || party.members.size() > MAX_MEMBERS)
                throw new IllegalArgumentException("Invalid party or leader");
            for (UUID id : party.members) {
                if (!members.add(id))
                    throw new IllegalArgumentException("Invalid or duplicate party member");
            }
        }
        return List.copyOf(input);
    }

    private void install(List<Party> next) {
        parties = next;
        Map<UUID, Party> index = new HashMap<>();
        for (Party party : next) for (UUID member : party.members) index.put(member, party);
        membership = index;
    }
    public Party party(UUID player) { return membership.get(player); }
    public boolean allied(UUID first, UUID second) {
        Party party = party(first);
        return party != null && party.members.contains(second);
    }
    public void replace(Party old, Party replacement) throws IOException {
        if (!available) throw new IOException("Party storage is disabled until the save is repaired and the server restarted");
        List<Party> next = new ArrayList<>(parties);
        if (old != null && !next.remove(old)) throw new IllegalStateException("Party changed; try again");
        if (replacement != null) next.add(replacement);
        next = validate(next);
        Files.createDirectories(path.getParent());
        Path pending = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] bytes = JSON.toJson(new Snapshot(2, next)).getBytes(StandardCharsets.UTF_8);
        try (FileChannel out = FileChannel.open(pending, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) out.write(buffer);
            out.force(true);
        }
        Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        install(next);
    }
}
