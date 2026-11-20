package com.anton.elementalwands.summit;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Geometry of the Storm Summit, the Storm Roc's realm. Each slot holds one copy of the mountaintop
 * authored by {@code art/storm_summit/build_layout.py}: a round summit above a sea of clouds, ringed
 * by the spires the Roc perches on. The layout's own numbers live in {@link StormSummitLayout},
 * which the builder generates.
 */
public final class StormSummit {
    private StormSummit() {}

    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ElementalWandsMod.MOD_ID, "storm_summit"));
    /** Top block of the summit's rock; the layout's y=0. */
    public static final int SURFACE_Y = 100;
    /** Top of the sea of clouds, drawn by the client; nothing below it is meant to be seen. */
    public static final int CLOUD_Y = SURFACE_Y + StormSummitLayout.CLOUD_TOP;
    /**
     * Underside of phase two's storm clouds. Its scud races 11 blocks lower, still clear of the
     * spires and of every phase-two flight path, the charge's hover included.
     */
    public static final int STORM_CEILING = SURFACE_Y + 76;
    /**
     * Underside of phase one's cap cloud, the thin deck over the summit its lightning falls from.
     * Above every spire and flight; only the dive climbs into it.
     */
    public static final int CAP_Y = SURFACE_Y + 58;
    public static final int SLOTS = 8;
    /** Far enough apart that no view distance reaches a neighbouring slot. */
    public static final int SLOT_SPACING = 1024;
    /** Everything a slot owns: the distant peaks reach about 250 blocks out. */
    static final int FOOTPRINT = StormSummitLayout.REACH + 8;
    /** The party lands facing north, toward the home spire. */
    public static final float ARRIVAL_YAW = 180;
    /** Below the summit by this much, outside the rim, a player has fallen off. */
    public static final double FALL_DEPTH = 4;

    /** Spells and explosions never damage the summit; only its own layout work writes terrain. */
    public static boolean keepsTerrain(World world) { return world.getRegistryKey() == WORLD; }

    /** The summit's centre. Generations move to fresh ground when the layout changes. */
    public static BlockPos centre(int generation, int slot) {
        return new BlockPos((generation * SLOTS + slot) * SLOT_SPACING, SURFACE_Y, 0);
    }

    public static Vec3d arrival(BlockPos centre) { return arrival(centre, 0); }

    /**
     * A called party lands side by side, two blocks apart like the Guardian's, up to eight places
     * either side of the arrival (a bigger party shares the ends). The line bends about the Roc's
     * home perch: each place is the arrival turned about the home spire by {@link #lineTurn}, so
     * everyone in it faces the Roc, and a player's intro is the whole summit scene turned with
     * them. The layout keeps the ground across the line clear (art/storm_summit/build_layout.py).
     */
    public static final double LINE_SPACING = com.anton.elementalwands.party.PartyLine.SPACING;
    public static final int LINE_REACH = com.anton.elementalwands.party.PartyLine.REACH;
    private static final double PIVOT_X = StormSummitLayout.PERCHES[0][0] + .5, PIVOT_Z = StormSummitLayout.PERCHES[0][2] + .5;
    private static final double LINE_RADIUS = Math.hypot(StormSummitLayout.ARRIVAL[0] + .5 - PIVOT_X, StormSummitLayout.ARRIVAL[2] + .5 - PIVOT_Z);

    /** Where the i-th of n players stands in the line, in places from its middle (0 for one alone). */
    public static double linePlace(int i, int n) { return com.anton.elementalwands.party.PartyLine.place(i, n); }

    /** The yaw in degrees that turns the summit about the home perch to bring the arrival to a place in the line (positive places to the right). */
    public static float lineTurn(double place) { return (float) Math.toDegrees(-place * LINE_SPACING / LINE_RADIUS); }

    /**
     * A point given in a slot's own frame (blocks from its centre, y from its surface), turned
     * about the home perch by that yaw, in the world. A Minecraft yaw: a view's yaw in the slot's
     * frame plus the turn is its yaw in the world.
     */
    public static Vec3d turned(BlockPos centre, Vec3d local, float turn) {
        double a = Math.toRadians(turn), c = Math.cos(a), s = Math.sin(a), x = local.x - PIVOT_X, z = local.z - PIVOT_Z;
        return new Vec3d(centre.getX() + PIVOT_X + x * c - z * s, centre.getY() + local.y, centre.getZ() + PIVOT_Z + x * s + z * c);
    }

    /** A place in the line at the arrival: the feet's position. Its facing is {@link #ARRIVAL_YAW} plus its {@link #lineTurn}. */
    public static Vec3d arrival(BlockPos centre, double place) {
        int[] a = StormSummitLayout.ARRIVAL;
        return turned(centre, new Vec3d(a[0] + .5, a[1], a[2] + .5), lineTurn(place));
    }

    public static Identifier tile(int i, int j) { return Identifier.of(ElementalWandsMod.MOD_ID, "storm_summit/summit_" + i + "_" + j); }
    public static BlockPos tileOrigin(BlockPos centre, int i, int j) {
        int t = StormSummitLayout.TILE, o = StormSummitLayout.TILE_ORIGIN;
        return new BlockPos(centre.getX() + o + i * t, SURFACE_Y + StormSummitLayout.BOTTOM, centre.getZ() + o + j * t);
    }

    /** Slot centre nearest to a position, for whichever generation laid it out. */
    public static BlockPos nearestCentre(Vec3d pos) {
        return new BlockPos((int) Math.round(pos.x / SLOT_SPACING) * SLOT_SPACING, SURFACE_Y, 0);
    }

    public static Box footprint(BlockPos centre) {
        return new Box(centre.getX() - FOOTPRINT, 0, centre.getZ() - FOOTPRINT,
                centre.getX() + FOOTPRINT, SURFACE_Y + 155, centre.getZ() + FOOTPRINT);
    }

    /** The rim's radius in the direction of a horizontal offset from the centre. */
    public static double rim(double dx, double dz) {
        double[] rim = StormSummitLayout.RIM;
        double step = 360.0 / rim.length;
        double angle = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
        int i = (int) (angle / step);
        double f = angle / step - i;
        return rim[i % rim.length] * (1 - f) + rim[(i + 1) % rim.length] * f;
    }

    /** How far a position is beyond the rim, horizontally (negative inside it). */
    public static double beyondRim(BlockPos centre, Vec3d pos) {
        double dx = pos.x - (centre.getX() + .5), dz = pos.z - (centre.getZ() + .5);
        return Math.hypot(dx, dz) - rim(dx, dz);
    }

    /** Off the summit: outside the rim and well below it, or down in the clouds anywhere. */
    public static boolean fallen(BlockPos centre, Vec3d pos) {
        return pos.y < CLOUD_Y || pos.y < SURFACE_Y + 1 - FALL_DEPTH && beyondRim(centre, pos) > -1;
    }
}
