package com.anton.elementalwands.party;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Fixture check: the ground at a boss's arrival or way home has room for a party to stand side by side ({@link PartyLine}). */
public final class PartyLineCheck {
    private PartyLineCheck() {}

    /**
     * A party of four set down here stands apart: every spot level with the point and at least the
     * line's minimum apart, none sent back onto the point for want of room. Returns a note of where they stand.
     */
    public static String require(World world, Vec3d at, float yaw, String where) {
        List<Vec3d> spots = PartyLine.spots(world, at, yaw, 4);
        int ideal = 0;
        StringBuilder note = new StringBuilder(where).append(": a party of four stands at");
        for (int i = 0; i < spots.size(); i++) {
            Vec3d s = spots.get(i);
            if (Math.abs(s.y - at.y) > 1e-6) throw new AssertionError(where + ": spot " + s + " is not level with " + at);
            for (int j = 0; j < i; j++) {
                Vec3d o = spots.get(j);
                double apart = Math.hypot(s.x - o.x, s.z - o.z);
                if (apart < 1.49) throw new AssertionError(where + ": two of a party of four would stand " + apart + " blocks apart: " + spots);
            }
            Vec3d wanted = at.add(new Vec3d(-Math.cos(Math.toRadians(yaw)), 0, -Math.sin(Math.toRadians(yaw))).multiply(PartyLine.place(i, 4) * PartyLine.SPACING));
            if (s.distanceTo(wanted) < 1e-6) ideal++;
            note.append(String.format(Locale.ROOT, " (%.1f, %.1f, %.1f)", s.x, s.y, s.z));
        }
        return note.append("; ").append(ideal).append(" of 4 in the straight line").toString();
    }
}
