package com.anton.elementalwands.crypt;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Geometry of the Hollow Crypt dimension. Each fight slot holds one copy of the clearing
 * authored by {@code art/hollow_crypt/build_layout.py}; the radius, ceiling and tile grid
 * below must match that builder.
 */
public final class HollowCryptRealm {
    private HollowCryptRealm() {}

    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ElementalWandsMod.MOD_ID, "hollow_crypt"));
    /** Top block of the flat ground; the layout's y=0. */
    public static final int SURFACE_Y = 63;
    public static final int PLAY_RADIUS = 44;
    public static final int PLAY_CEILING = 30;
    public static final int SLOTS = 8;
    /** Far enough apart that no view distance reaches a neighbouring slot. */
    public static final int SLOT_SPACING = 1024;
    static final int TILE = 48, TILE_ORIGIN = -192, TILE_COUNT = 8;
    /** Everything a slot owns: the forest reaches about 146 blocks out and 90 up. */
    static final int FOOTPRINT = 200;

    /** The clearing's centre. Generations move to fresh ground when the layout changes. */
    public static BlockPos centre(int generation, int slot) {
        return new BlockPos((generation * SLOTS + slot) * SLOT_SPACING, SURFACE_Y, 0);
    }

    public static Vec3d summonPoint(BlockPos centre) { return new Vec3d(centre.getX() + .5, SURFACE_Y + 1, centre.getZ() + .5); }
    /** South rim, facing the summoning circle. */
    public static Vec3d arrival(BlockPos centre) { return new Vec3d(centre.getX() + .5, SURFACE_Y + 1, centre.getZ() + 34.5); }
    public static final float ARRIVAL_YAW = 180;

    public static Identifier tile(int i, int j) { return Identifier.of(ElementalWandsMod.MOD_ID, "hollow_crypt/realm_" + i + "_" + j); }
    public static BlockPos tileOrigin(BlockPos centre, int i, int j) {
        return new BlockPos(centre.getX() + TILE_ORIGIN + i * TILE, SURFACE_Y, centre.getZ() + TILE_ORIGIN + j * TILE);
    }

    /** Slot centre nearest to a position, for whichever generation laid it out. */
    public static BlockPos nearestCentre(Vec3d pos) {
        return new BlockPos((int) Math.round(pos.x / SLOT_SPACING) * SLOT_SPACING, SURFACE_Y, 0);
    }

    public static Box footprint(BlockPos centre) {
        return new Box(centre.getX() - FOOTPRINT, SURFACE_Y - 4, centre.getZ() - FOOTPRINT,
                centre.getX() + FOOTPRINT, SURFACE_Y + 128, centre.getZ() + FOOTPRINT);
    }

    /** Inside the playable cylinder, with {@code margin} blocks to spare. */
    public static boolean inPlay(BlockPos centre, Vec3d pos, double margin) {
        double dx = pos.x - centre.getX(), dz = pos.z - centre.getZ();
        return dx * dx + dz * dz <= (PLAY_RADIUS - margin) * (PLAY_RADIUS - margin)
                && pos.y >= SURFACE_Y - 1 && pos.y <= SURFACE_Y + 1 + PLAY_CEILING - margin;
    }

    /** Nearest point inside the play cylinder, keeping the height where possible. */
    public static Vec3d clampToPlay(BlockPos centre, Vec3d pos) {
        double dx = pos.x - centre.getX(), dz = pos.z - centre.getZ(), r = Math.sqrt(dx * dx + dz * dz), limit = PLAY_RADIUS - 1.5;
        double x = r > limit ? centre.getX() + dx / r * limit : pos.x, z = r > limit ? centre.getZ() + dz / r * limit : pos.z;
        double y = Math.max(SURFACE_Y + 1, Math.min(SURFACE_Y + PLAY_CEILING - 2, pos.y));
        return new Vec3d(x, y, z);
    }
}
