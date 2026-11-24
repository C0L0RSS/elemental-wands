package com.anton.elementalwands.party;

import java.nio.file.*;
import java.util.*;

public final class PartyStoreTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ew-party-contract-");
        Path path = dir.resolve("parties.json");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        String legacy = """
                {"version":1,"parties":[{"id":"%s","leader":"%s","members":{"%s":".BedrockPlayer","%s":"Renamed Player With Spaces"}}]}
                """.formatted(partyId,first,first,second);
        Files.writeString(path,legacy);
        PartyStore store = new PartyStore(path);
        var party = store.party(first);
        require(store.available() && party.members().equals(List.of(first,second)), "Legacy UUID roster/order not recovered");
        require(Files.readString(path).equals(legacy), "Loading rewrote the legacy file");
        require(new PartyStore(path).allied(first,second),"Membership did not persist");
        require(!store.allied(first,third),"Unrelated player protected");
        String saved = Files.readString(path);
        try { store.replace(null,new PartyStore.Party(UUID.randomUUID(),first,List.of(first))); throw new AssertionError("Duplicate accepted"); }
        catch (IllegalArgumentException expected) {}
        require(Files.readString(path).equals(saved),"Invalid write changed data");
        Files.createDirectory(dir.resolve("parties.json.tmp"));
        try { store.replace(party,null);throw new AssertionError("Write failure accepted"); }
        catch (java.io.IOException expected) {}
        require(store.allied(first,second) && Files.readString(path).equals(saved),"Failed write changed live membership");
        Files.delete(dir.resolve("parties.json.tmp"));
        var next = new PartyStore.Party(party.id(),second,party.members()); store.replace(party,next);
        require(new PartyStore(path).party(first).leader().equals(second),"Leader transfer lost");
        require(!Files.readString(path).contains("BedrockPlayer"),"Display name was persisted as membership");
        store.replace(next,null);require(new PartyStore(path).party(first)==null,"Disband did not persist");
        Files.writeString(path,"{bad");
        var disabled = new PartyStore(path);
        require(!disabled.available() && !disabled.allied(first,second),"Invalid storage stayed active");
        try { disabled.replace(null,party);throw new AssertionError("Disabled store accepted a write"); }
        catch (java.io.IOException expected) {}
        require(Files.readString(path).equals("{bad"),"Corrupt data overwritten");
        Files.writeString(path,legacy);
        require(new PartyStore(path).allied(first,second),"Repaired save did not reopen");
        Files.delete(path);Files.delete(dir);
        System.out.println("Party store checks passed: UUID membership, restart, transfer/disband, duplicate validation, atomic failure rollback and corrupt data preservation.");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
