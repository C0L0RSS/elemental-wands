package com.anton.elementalwands.arena;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Membership is sealed once; death, disconnect, or leaving never enrolls a replacement body. */
public final class GuardianArenaRoster {
    private final Set<UUID> enrolled;
    private final Set<UUID> remaining;
    public GuardianArenaRoster(Collection<UUID> players) {
        enrolled=Set.copyOf(players); remaining=new LinkedHashSet<>(players);
    }
    public boolean enrolled(UUID id) { return enrolled.contains(id); }
    public boolean alive(UUID id) { return remaining.contains(id); }
    public boolean eliminate(UUID id) { return remaining.remove(id); }
    public Set<UUID> survivors() { return Set.copyOf(remaining); }
    public boolean wiped() { return remaining.isEmpty(); }
}
