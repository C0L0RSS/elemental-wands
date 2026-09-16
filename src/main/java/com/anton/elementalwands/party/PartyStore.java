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
    public record Party(UUID id, UUID leader, Map<UUID, String> members) {
        public Party { members = Collections.unmodifiableMap(new LinkedHashMap<>(members)); }
    }
    public record Snapshot(int version, List<Party> parties) {}
    private final Path path;
    private List<Party> parties;
    private Map<UUID, Party> membership;

    public PartyStore(Path path) throws IOException {
        this.path = path;
        try {
            Snapshot snapshot = Files.exists(path) ? JSON.fromJson(Files.readString(path), Snapshot.class)
                    : new Snapshot(1, List.of());
            if (snapshot == null || snapshot.version != 1) throw new IllegalArgumentException("Unknown party data version");
            install(validate(snapshot.parties));
        } catch (RuntimeException e) { throw new IOException("Invalid party data: " + path, e); }
    }

    private static List<Party> validate(List<Party> input) {
        Set<UUID> ids = new HashSet<>(), members = new HashSet<>();
        for (Party party : input) {
            if (party.id == null || !ids.add(party.id) || !party.members.containsKey(party.leader)
                    || party.members.isEmpty() || party.members.size() > MAX_MEMBERS)
                throw new IllegalArgumentException("Invalid party or leader");
            for (var entry : party.members.entrySet()) {
                if (entry.getKey() == null || !members.add(entry.getKey()) || entry.getValue() == null
                        || !entry.getValue().matches("[A-Za-z0-9_]{1,16}"))
                    throw new IllegalArgumentException("Invalid or duplicate party member");
            }
        }
        return List.copyOf(input);
    }

    private void install(List<Party> next) {
        parties = next;
        Map<UUID, Party> index = new HashMap<>();
        for (Party party : next) for (UUID member : party.members.keySet()) index.put(member, party);
        membership = index;
    }
    public Party party(UUID player) { return membership.get(player); }
    public boolean allied(UUID first, UUID second) {
        Party party = party(first);
        return party != null && party.members.containsKey(second);
    }
    public void replace(Party old, Party replacement) throws IOException {
        List<Party> next = new ArrayList<>(parties);
        if (old != null && !next.remove(old)) throw new IllegalStateException("Party changed; try again");
        if (replacement != null) next.add(replacement);
        next = validate(next);
        Files.createDirectories(path.getParent());
        Path pending = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] bytes = JSON.toJson(new Snapshot(1, next)).getBytes(StandardCharsets.UTF_8);
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
