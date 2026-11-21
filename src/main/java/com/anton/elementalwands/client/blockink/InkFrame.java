package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.client.blockfire.BlockFireMesh;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * One frame of the First Wizard's effects. {@code fire} is a block-fire mesh (the Fire kit's atlas and primitives,
 * {@code BlockFireKit}); {@code ink} the ink atlas ({@link InkKit}); {@code space} the Space kit's atlas, for Space's
 * colours before they cool; {@code wind} the Wind kit's atlas ({@code BlockWindKit}'s rows), for Wind's; {@code stone} the boss's stone atlas ({@link StoneFx}), for Stone's; {@code nature} the Nature kit's atlas ({@link NatureInk}), for Nature's. {@code now} is the world clock in seconds with the partial tick; {@code embers} is false
 * when the particle setting is reduced, and accents (drips, sparks, motes) are then skipped or thinned. {@code floor} takes
 * the marks laid flat on the floor ({@link FloorDecal}).
 */
public record InkFrame(BlockFireMesh fire, AtlasMesh ink, AtlasMesh space, AtlasMesh wind, AtlasMesh stone, AtlasMesh nature, double now, float delta, ClientWorld world, boolean embers, FloorDecal floor) {
    public Vec3d camera() { return ink.camera; }
    /** Whether a point is close enough to the camera to be worth drawing. */
    public boolean near(Vec3d point, double reach) { return ink.camera.squaredDistanceTo(point) < reach * reach; }
}
