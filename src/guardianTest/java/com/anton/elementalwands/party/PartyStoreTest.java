package com.anton.elementalwands.party;

import java.nio.file.*;
import java.util.*;

public final class PartyStoreTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ew-party-contract-");
        Path path = dir.resolve("parties.json");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        PartyStore store = new PartyStore(path);
        var party = new PartyStore.Party(UUID.randomUUID(), first, Map.of(first,"First",second,"Second"));
        store.replace(null,party);
        require(new PartyStore(path).allied(first,second),"Membership did not persist");
        require(!store.allied(first,third),"Unrelated player protected");
        String saved = Files.readString(path);
        try { store.replace(null,new PartyStore.Party(UUID.randomUUID(),first,Map.of(first,"First"))); throw new AssertionError("Duplicate accepted"); }
        catch (IllegalArgumentException expected) {}
        require(Files.readString(path).equals(saved),"Invalid write changed data");
        Files.createDirectory(dir.resolve("parties.json.tmp"));
        try { store.replace(party,null);throw new AssertionError("Write failure accepted"); }
        catch (java.io.IOException expected) {}
        require(store.allied(first,second) && Files.readString(path).equals(saved),"Failed write changed live membership");
        Files.delete(dir.resolve("parties.json.tmp"));
        var next = new PartyStore.Party(party.id(),second,party.members()); store.replace(party,next);
        require(new PartyStore(path).party(first).leader().equals(second),"Leader transfer lost");
        store.replace(next,null);require(new PartyStore(path).party(first)==null,"Disband did not persist");
        Files.writeString(path,"{bad");
        try { new PartyStore(path);throw new AssertionError("Corrupt data silently reset"); }
        catch (java.io.IOException expected) {}
        require(Files.readString(path).equals("{bad"),"Corrupt data overwritten");
        Files.delete(path);Files.delete(dir);
        System.out.println("Party store checks passed: UUID membership, restart, transfer/disband, duplicate validation, atomic failure rollback and corrupt data preservation.");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
