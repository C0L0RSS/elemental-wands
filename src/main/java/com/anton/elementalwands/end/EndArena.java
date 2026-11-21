package com.anton.elementalwands.end;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Geometry of the First Wizard's arena on the End's main island, authored by
 * {@code art/end_island/build_layout.py}. The floor's centre, radius and arrival, the tile grid and the
 * way home's corner are duplicated from that builder and must stay in step with it. Layout
 * coordinates are relative to the centre block; layout y=0 is the floor block (world y=63).
 */
public final class EndArena {
    private EndArena() {}

    public static final RegistryKey<World> WORLD = World.END;

    // ---- in step with art/end_island/build_layout.py
    /** The floor's centre block: the End's origin. Players stand at y=64. */
    public static final BlockPos CENTRE = new BlockPos(0, 63, 0);
    /** The fight floor is every cell within this distance of the centre, flat at y=63. The boss relies on it. */
    public static final double FLOOR_RADIUS = 44.5;
    static final int TILE = 48, TILE_ORIGIN = -72, TILE_COUNT = 3;
    /** Tile-local y = layout y - BOTTOM. */
    static final int BOTTOM = -24;
    /** Where players arrive (feet), on the south rim facing north. */
    static final int[] ARRIVAL = {0, 1, 42};
    /** The way home's template corner; it is 9 x 3 x 9 round the centre. */
    static final int[] WAY_HOME_ORIGIN = {-4, -2, -4};
    /** Before the tiles go down the game clears every block above the floor within this radius. */
    static final int CLEAR_RADIUS = 64;
    // ----

    /** Vanilla's spikes reach y=113 with their cages; the clear goes higher than that. */
    static final int CLEAR_TOP = 128;
    public static final float ARRIVAL_YAW = 180;
    /** What the arena owns: protection, keeping belongings, keeping vanilla mobs out during a fight. */
    public static final int FOOTPRINT_RADIUS = 72;
    /**
     * Where dragon eggs lifted by a layout are set down, side by side eastward from here: on the kerb
     * just off the floor's south edge, beside the arrival, in the wall's gap (cells 2..8 at z=45 are kerb).
     */
    static final int[] EGG_SPOT = {2, 45};
    /** Slack past the floor's edge before a fighter counts as off it. */
    static final double EDGE_SLACK = 2.5;
    /** A fighter's teleport may not end higher than this above the floor. */
    static final int FIGHT_CEILING = 64;
    /** Vanilla's gateway ring: twenty spots 96 out at y=75. The four the win opens are vanilla's at the compass points. */
    static final int GATEWAY_COUNT = 20, GATEWAY_Y = 75;
    static final int[] WIN_GATEWAYS = {0, 5, 10, 15};

    public static Identifier tile(int i, int j) { return Identifier.of(ElementalWandsMod.MOD_ID, "end_island/arena_" + i + "_" + j); }
    public static final Identifier WAY_HOME = Identifier.of(ElementalWandsMod.MOD_ID, "end_island/way_home");

    public static BlockPos tileOrigin(int i, int j) {
        return new BlockPos(CENTRE.getX() + TILE_ORIGIN + i * TILE, CENTRE.getY() + BOTTOM, CENTRE.getZ() + TILE_ORIGIN + j * TILE);
    }

    public static BlockPos wayHomeOrigin() { return CENTRE.add(WAY_HOME_ORIGIN[0], WAY_HOME_ORIGIN[1], WAY_HOME_ORIGIN[2]); }

    /** The tile that holds the floor's centre, laid again when the way home is closed. */
    static int[] centreTile() { return new int[]{-TILE_ORIGIN / TILE, -TILE_ORIGIN / TILE}; }

    public static Vec3d arrival() { return Vec3d.ofBottomCenter(CENTRE.add(ARRIVAL[0], ARRIVAL[1], ARRIVAL[2])); }

    /** Where the boss is placed: on the centre block, facing the arrival (south). */
    public static Vec3d bossSpot() { return Vec3d.ofBottomCenter(CENTRE.up()); }

    /** Vanilla's gateway spot {@code i} of twenty, by the same formula as {@code EnderDragonFight}. */
    static BlockPos gateway(int i) {
        double angle = 2.0 * (-Math.PI + 0.15707963267948966 * i);
        return new BlockPos(MathHelper.floor(96.0 * Math.cos(angle)), GATEWAY_Y, MathHelper.floor(96.0 * Math.sin(angle)));
    }

    /** Horizontal distance from the centre (the centre block is at 0,0 so cell (x, z) lies hypot(x, z) out). */
    public static double radius(Vec3d pos) {
        return Math.hypot(pos.x - (CENTRE.getX() + .5), pos.z - (CENTRE.getZ() + .5));
    }

    public static boolean inFootprint(World world, Vec3d pos) {
        return world.getRegistryKey() == WORLD && radius(pos) <= FOOTPRINT_RADIUS;
    }

    public static boolean inFootprint(World world, BlockPos pos) { return inFootprint(world, Vec3d.ofCenter(pos)); }

    public static Box footprintBox() {
        return new Box(CENTRE.getX() - FOOTPRINT_RADIUS, -64, CENTRE.getZ() - FOOTPRINT_RADIUS,
                CENTRE.getX() + FOOTPRINT_RADIUS + 1, 320, CENTRE.getZ() + FOOTPRINT_RADIUS + 1);
    }

    /** On the fight floor, or just off its edge, and no lower than its surface. */
    static boolean onFloor(Vec3d pos, double slack) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && radius(pos) <= FLOOR_RADIUS + .5 + slack && pos.y >= CENTRE.getY() + .5;
    }

    /** Where a teleport by a fighter may end: over the floor, a little inside its edge, not far above it. */
    static boolean teleportOnFloor(Vec3d pos) {
        return onFloor(pos, -.4) && pos.y <= CENTRE.getY() + 1 + FIGHT_CEILING;
    }

    /** The floor's edge nearest a point, a block inside, and the yaw that faces the centre from there. */
    static Vec3d edgeSpot(Vec3d pos) {
        double dx = pos.x - (CENTRE.getX() + .5), dz = pos.z - (CENTRE.getZ() + .5);
        double length = Math.hypot(dx, dz);
        if (length < 1e-3) { dx = 0; dz = 1; length = 1; }
        double r = FLOOR_RADIUS - 1.5;
        return new Vec3d(CENTRE.getX() + .5 + dx / length * r, CENTRE.getY() + 1, CENTRE.getZ() + .5 + dz / length * r);
    }

    static float yawToCentre(Vec3d from) {
        double dx = CENTRE.getX() + .5 - from.x, dz = CENTRE.getZ() + .5 - from.z;
        // Minecraft yaw: 0 faces south (+z), 90 faces west (-x).
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
