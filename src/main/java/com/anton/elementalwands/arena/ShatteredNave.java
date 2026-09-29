package com.anton.elementalwands.arena;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Geometry of the Shattered Nave, the Fractured Guardian's realm. Each fight slot holds one copy
 * of the endless hall authored by {@code art/guardian_nave/build_layout.py}: a 128-block square
 * fight floor under a vaulted crossing, walled in by an invisible barrier. The half-width,
 * ceiling and tile grid below must match that builder.
 */
public final class ShatteredNave {
    private ShatteredNave() {}

    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ElementalWandsMod.MOD_ID, "shattered_nave"));
    /** Top block of the floor; the layout's y=0. */
    public static final int SURFACE_Y = 63;
    /** The fight floor covers blocks [-HALF, HALF-1] from the centre on both axes. */
    public static final int HALF = 64;
    /** The invisible lid's height above the floor. */
    public static final int CEILING = 40;
    public static final int SLOTS = 8;
    /** Far enough apart that no view distance reaches a neighbouring slot. */
    public static final int SLOT_SPACING = 1024;
    static final int TILE = 48, TILE_ORIGIN = -264, TILE_COUNT = 11;
    /** Everything a slot owns: the pillars reach about 244 blocks out and 131 up. */
    static final int FOOTPRINT = 270;
    public static final float ARRIVAL_YAW = 180;

    /**
     * Spells and explosions never damage the nave's blocks. Temporary spell blocks still place and
     * restore themselves; only the nave's own layout work rewrites the terrain.
     */
    public static boolean keepsTerrain(World world) { return world.getRegistryKey() == WORLD; }

    /** The crossing's centre. Generations move to fresh ground when the layout changes. */
    public static BlockPos centre(int generation, int slot) {
        return new BlockPos((generation * SLOTS + slot) * SLOT_SPACING, SURFACE_Y, 0);
    }

    /** The effigy seat in the middle of the floor, where the Guardian stands. */
    public static Vec3d seat(BlockPos centre) { return new Vec3d(centre.getX() + .5, SURFACE_Y + 1, centre.getZ() + .5); }
    /** South of the seat, facing it. */
    public static Vec3d arrival(BlockPos centre) { return new Vec3d(centre.getX() + .5, SURFACE_Y + 1, centre.getZ() + 28.5); }
    /** Where fallen fighters watch from: above the south half of the floor, looking at the seat. */
    public static Vec3d gallery(BlockPos centre) { return new Vec3d(centre.getX() + .5, SURFACE_Y + 16, centre.getZ() + 40.5); }

    public static Identifier tile(int i, int j) { return Identifier.of(ElementalWandsMod.MOD_ID, "shattered_nave/nave_" + i + "_" + j); }
    public static BlockPos tileOrigin(BlockPos centre, int i, int j) {
        return new BlockPos(centre.getX() + TILE_ORIGIN + i * TILE, SURFACE_Y, centre.getZ() + TILE_ORIGIN + j * TILE);
    }

    /** Slot centre nearest to a position, for whichever generation laid it out. */
    public static BlockPos nearestCentre(Vec3d pos) {
        return new BlockPos((int) Math.round(pos.x / SLOT_SPACING) * SLOT_SPACING, SURFACE_Y, 0);
    }

    public static Box footprint(BlockPos centre) {
        return new Box(centre.getX() - FOOTPRINT, SURFACE_Y - 4, centre.getZ() - FOOTPRINT,
                centre.getX() + FOOTPRINT, SURFACE_Y + 160, centre.getZ() + FOOTPRINT);
    }

    /** Inside the walled fight volume, with {@code margin} blocks to spare. */
    public static boolean inPlay(BlockPos centre, Vec3d pos, double margin) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && Math.abs(pos.x - centre.getX()) <= HALF - margin && Math.abs(pos.z - centre.getZ()) <= HALF - margin
                && pos.y >= SURFACE_Y - 1 && pos.y <= SURFACE_Y + 1 + CEILING - margin;
    }

    /** Nearest point inside the fight volume, keeping the height where possible. */
    public static Vec3d clampToPlay(BlockPos centre, Vec3d pos) {
        double limit = HALF - 1.5;
        return new Vec3d(Math.clamp(pos.x, centre.getX() - limit, centre.getX() + limit),
                Math.clamp(pos.y, SURFACE_Y + 1, SURFACE_Y + CEILING - 2),
                Math.clamp(pos.z, centre.getZ() - limit, centre.getZ() + limit));
    }
}
