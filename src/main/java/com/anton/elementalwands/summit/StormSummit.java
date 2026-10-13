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

    public static Vec3d arrival(BlockPos centre) {
        int[] a = StormSummitLayout.ARRIVAL;
        return new Vec3d(centre.getX() + a[0] + .5, SURFACE_Y + a[1], centre.getZ() + a[2] + .5);
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
