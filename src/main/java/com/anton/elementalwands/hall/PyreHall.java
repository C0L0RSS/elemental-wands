package com.anton.elementalwands.hall;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Geometry of the Burned Throne Hall, the Pyre King's realm: a round ruined throne hall on a crag in
 * a Nether lava sea. Each slot holds one copy of the hall authored by
 * {@code art/pyre_hall/build_layout.py}. The tile grid, floor radius, ceiling, arrival, the king's
 * mark and the lava height below are duplicated from that builder and must stay in step with it.
 * Layout coordinates are relative to the hall's centre block; layout y=0 is the fight floor's block.
 */
public final class PyreHall {
    private PyreHall() {}

    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ElementalWandsMod.MOD_ID, "pyre_hall"));

    // ---- in step with art/pyre_hall/build_layout.py
    static final int TILE = 48, TILE_ORIGIN = -120, TILE_COUNT = 5;
    /** Tile-local y = layout y - BOTTOM. */
    static final int BOTTOM = -16;
    /** The fight floor is the cells within FLOOR_RADIUS + .5 of the centre. */
    public static final int FLOOR_RADIUS = 40;
    /** The tiles' barrier lid sits at layout y = PLAY_CEILING + 1. */
    public static final int PLAY_CEILING = 32;
    /** Where players arrive (feet), facing north; where the king stands (feet), facing south. */
    static final int[] ARRIVAL = {0, 1, 33}, KING = {0, 1, 0};
    /** Top block of the lava sea, which the dimension's flat generator supplies (the tiles have none). */
    public static final int LAVA_Y = -14;
    // ----

    /** World y of the fight floor's block, the layout's y=0. The generator's lava top is SURFACE_Y + LAVA_Y. */
    public static final int SURFACE_Y = 64;
    public static final int SLOTS = 8;
    /** Far enough apart that no view distance reaches a neighbouring slot. */
    public static final int SLOT_SPACING = 1024;
    /** Everything a slot owns: the tiles reach 120 blocks out. */
    static final int FOOTPRINT = -TILE_ORIGIN + 8;
    /** Players arrive facing north (-Z); the king faces south (+Z). */
    public static final float ARRIVAL_YAW = 180, KING_YAW = 0;
    /** Slack past the floor's edge before a player counts as off it: the barrier ring stands just outside. */
    static final double EDGE_SLACK = 1.5;

    /** Spells and explosions never damage the hall; only its own layout work writes terrain. */
    public static boolean keepsTerrain(World world) { return world.getRegistryKey() == WORLD; }

    /** The hall's centre block on the floor (layout 0,0,0). Generations move to fresh ground when the layout changes. */
    public static BlockPos centre(int generation, int slot) {
        return new BlockPos((generation * SLOTS + slot) * SLOT_SPACING, SURFACE_Y, 0);
    }

    /** Slot centre nearest to a position, for whichever generation laid it out. */
    public static BlockPos nearestCentre(Vec3d pos) {
        return new BlockPos((int) Math.round(pos.x / SLOT_SPACING) * SLOT_SPACING, SURFACE_Y, 0);
    }

    /** A layout cell's world position, and the feet position standing in it. */
    public static BlockPos cell(BlockPos centre, int x, int y, int z) { return new BlockPos(centre.getX() + x, SURFACE_Y + y, centre.getZ() + z); }
    static Vec3d feet(BlockPos centre, int[] layout) { return Vec3d.ofBottomCenter(cell(centre, layout[0], layout[1], layout[2])); }

    public static Vec3d arrival(BlockPos centre) { return feet(centre, ARRIVAL); }
    public static Vec3d king(BlockPos centre) { return feet(centre, KING); }

    public static Identifier tile(int i, int j) { return Identifier.of(ElementalWandsMod.MOD_ID, "pyre_hall/hall_" + i + "_" + j); }
    public static BlockPos tileOrigin(BlockPos centre, int i, int j) {
        return new BlockPos(centre.getX() + TILE_ORIGIN + i * TILE, SURFACE_Y + BOTTOM, centre.getZ() + TILE_ORIGIN + j * TILE);
    }

    public static Box footprint(BlockPos centre) {
        return new Box(centre.getX() - FOOTPRINT, 0, centre.getZ() - FOOTPRINT,
                centre.getX() + FOOTPRINT, SURFACE_Y + 160, centre.getZ() + FOOTPRINT);
    }

    /** Horizontal distance from the centre block's middle. */
    static double radius(BlockPos centre, Vec3d pos) {
        return Math.hypot(pos.x - (centre.getX() + .5), pos.z - (centre.getZ() + .5));
    }

    /** A layout cell on the fight floor itself. */
    public static boolean floorCell(int x, int z) { return x * x + z * z <= (FLOOR_RADIUS + .5) * (FLOOR_RADIUS + .5); }

    /**
     * Inside the walled fight volume over the floor, with {@code margin} blocks to spare: within the
     * barrier ring, no lower than just under the floor and under the lid.
     */
    public static boolean inPlay(BlockPos centre, Vec3d pos, double margin) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && radius(centre, pos) <= FLOOR_RADIUS + .5 + EDGE_SLACK - margin
                && pos.y >= SURFACE_Y - 1 && pos.y <= SURFACE_Y + 1 + PLAY_CEILING - margin;
    }
}
