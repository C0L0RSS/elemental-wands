package com.anton.elementalwands.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** One bit per world-block column. Rendering and collision use these same nine cuts. */
public final class SpringbloomFootprint {
    public static final int FULL=511, CENTER=16;
    private static final VoxelShape[] SHAPES=new VoxelShape[FULL+1];
    static {
        for(int mask=0;mask<=FULL;mask++) {
            VoxelShape shape=VoxelShapes.empty();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if((mask&bit(x,z))!=0)
                shape=VoxelShapes.union(shape,VoxelShapes.cuboid(cell(x,z)));
            SHAPES[mask]=shape.simplify();
        }
    }
    public static int bit(int x,int z){return 1<<((x+1)*3+z+1);}
    private static Box cell(int x,int z){return new Box(Math.max(-.25,x),0,Math.max(-.25,z),
            Math.min(1.25,x+1),SpringbloomRules.HEIGHT,Math.min(1.25,z+1));}
    public static VoxelShape shape(int mask){return SHAPES[mask&FULL];}
    public static boolean overlaps(int mask,BlockPos anchor,Box feet){
        for(var box:shape(mask).getBoundingBoxes()) {
            if(feet.maxX>anchor.getX()+box.minX+1E-5 && feet.minX<anchor.getX()+box.maxX-1E-5
                    && feet.maxZ>anchor.getZ()+box.minZ+1E-5 && feet.minZ<anchor.getZ()+box.maxZ-1E-5)return true;
        }
        return false;
    }
    private SpringbloomFootprint(){}
}
