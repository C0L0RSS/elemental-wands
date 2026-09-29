package com.anton.elementalwands.arena;

/** Journal records shared by the realms that move players in and out. Never live Minecraft objects. */
public final class GuardianArenaJournal {
    private GuardianArenaJournal() {}

    /** Where a player stood before a realm took them; they are sent back here. */
    public record Point(String dimension,double x,double y,double z,float yaw,float pitch) {}
}
