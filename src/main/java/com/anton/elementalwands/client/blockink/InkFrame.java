package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.client.blockfire.BlockFireMesh;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/**
 * One frame of the First Wizard's effects. Each mesh uses its own boss texture. The painted
 * rows keep the element kits' material order so their primitives share the same UVs.
 * The world clock includes the partial tick. Reduced particles thin the optional accents.
 */
public record InkFrame(BlockFireMesh fire, AtlasMesh ink, AtlasMesh space, AtlasMesh wind, AtlasMesh stone, AtlasMesh nature, double now, float delta, ClientWorld world, boolean embers) {
    public Vec3d camera() { return ink.camera; }
    /** Whether a point is close enough to the camera to be worth drawing. */
    public boolean near(Vec3d point, double reach) { return ink.camera.squaredDistanceTo(point) < reach * reach; }
}
