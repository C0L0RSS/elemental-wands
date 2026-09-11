package com.anton.elementalwands.church;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Blends the rigid sanctuary floor into existing soil without grading buildings or water. */
public final class GuardianChurchTerrain {
    public static final int MARGIN=14;
    public record Column(int x,int z,int ground,int target,String stairs) {
        public Column(int x,int z,int ground,int target) { this(x,z,ground,target,null); }
    }
    private GuardianChurchTerrain() {}

    public static boolean loaded(ServerWorld world,GuardianChurchManager.Site site) {
        var a=site.at(ChurchLayout.MIN_X-MARGIN,0,ChurchLayout.MIN_Z-MARGIN);
        var b=site.at(ChurchLayout.MAX_X+MARGIN,0,ChurchLayout.MAX_Z+MARGIN);
        for (int x=Math.min(a.getX(),b.getX())>>4;x<=Math.max(a.getX(),b.getX())>>4;x++)
            for (int z=Math.min(a.getZ(),b.getZ())>>4;z<=Math.max(a.getZ(),b.getZ())>>4;z++)
                if (world.getChunkManager().getWorldChunk(x,z)==null) return false;
        return true;
    }

    public static List<Column> plan(ServerWorld world,GuardianChurchManager.Site site) {
        List<Column> result=new ArrayList<>();
        int floor=site.y-2;
        for (int x=ChurchLayout.MIN_X-MARGIN;x<=ChurchLayout.MAX_X+MARGIN;x++)
            for (int z=ChurchLayout.MIN_Z-MARGIN;z<=ChurchLayout.MAX_Z+MARGIN;z++) {
                int dx=Math.max(ChurchLayout.MIN_X-x,Math.max(0,x-ChurchLayout.MAX_X));
                int dz=Math.max(ChurchLayout.MIN_Z-z,Math.max(0,z-ChurchLayout.MAX_Z));
                boolean modern=site.layoutVersion>=2;
                if (dx==0 && dz==0 && (!modern || !ChurchLayout.naturalCourtyard(x,z))) continue;
                double distance=modern?Math.min(distance(x,z,-16,16,9,42),Math.min(
                        distance(x,z,-3,3,-10,9),distance(x,z,-5,4,-6,0))):Math.hypot(dx,dz);
                var p=site.at(x,0,z);
                // Gentle, broad variations avoid replacing a straight cliff with concentric square steps.
                double reach=(modern?6.5:10.5)+1.7*Math.sin(p.getX()*.19)+1.3*Math.cos(p.getZ()*.17);
                if (distance>=reach) continue;
                int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())-1;
                if (ground<world.getBottomY() || (modern?Math.abs(floor-ground)>3:(ground>=floor || floor-ground>12))) continue;
                var support=new BlockPos(p.getX(),ground,p.getZ());
                // Do not bury tree trunks, roofs, roads, containers, fluids, or other artificial surfaces.
                if (!soil(world.getBlockState(support)) || world.getBlockEntity(support)!=null) continue;
                int target=targetHeight(ground,floor,distance,reach);
                if (target==ground) continue;
                boolean clear=true;
                for (int y=ground+1;y<=target+1;y++) {
                    var q=new BlockPos(p.getX(),y,p.getZ());var state=world.getBlockState(q);
                    if (!open(state) || world.getBlockEntity(q)!=null) { clear=false;break; }
                }
                if(target<ground) {
                    // Only shave a thin natural soil bank; never carve exposed stone or a hillside.
                    for(int y=target;y<=ground;y++) {
                        var q=new BlockPos(p.getX(),y,p.getZ());
                        if(!soil(world.getBlockState(q)) || world.getBlockEntity(q)!=null) {clear=false;break;}
                    }
                }
                String stairs=null;
                if(modern && Math.abs(x)<=2 && z<ChurchLayout.MIN_Z && target>ground) {
                    stairs=site.rotation.rotate(net.minecraft.util.math.Direction.SOUTH).asString();
                }
                if (clear) result.add(new Column(p.getX(),p.getZ(),ground,target,stairs));
            }
        return result;
    }
    private static double distance(int x,int z,int left,int right,int front,int back) {
        return Math.hypot(Math.max(left-x,Math.max(0,x-right)),Math.max(front-z,Math.max(0,z-back)));
    }
    static int targetHeight(int ground,int floor,double distance,double reach) {
        double t=Math.clamp(distance/reach,0,1),weight=1-t*t*(3-2*t);
        return ground+(int)Math.round((floor-ground)*weight);
    }
    private static boolean soil(BlockState state) {
        return state.isIn(BlockTags.DIRT) || state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.GRAVEL);
    }
    private static boolean open(BlockState state) {
        return state.getFluidState().isEmpty() && (state.isAir() || state.isOf(Blocks.SHORT_GRASS)
                || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN)
                || state.isIn(BlockTags.FLOWERS) || state.isOf(Blocks.SNOW));
    }
    /** Rechecks the column in case something was placed after the durable plan was recorded. */
    public static void apply(ServerWorld world,Column column) {
        var bottom=new BlockPos(column.x(),column.ground(),column.z());
        if(column.target()<column.ground()) {
            var target=new BlockPos(column.x(),column.target(),column.z());
            if(!soil(world.getBlockState(target)) || world.getBlockEntity(target)!=null) return;
            for(int y=column.target()+1;y<=column.ground();y++) {
                var p=new BlockPos(column.x(),y,column.z());var state=world.getBlockState(p);
                if((!soil(state) && !open(state)) || world.getBlockEntity(p)!=null)return;
            }
            for(int y=column.target()+1;y<=column.ground()+1;y++) {
                var p=new BlockPos(column.x(),y,column.z());
                if(y<=column.ground() || open(world.getBlockState(p)))world.setBlockState(p,Blocks.AIR.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
            world.setBlockState(target,Blocks.GRASS_BLOCK.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS);
            return;
        }
        if (!soil(world.getBlockState(bottom)) || world.getBlockEntity(bottom)!=null) return;
        for (int y=column.ground()+1;y<=column.target()+1;y++) {
            var p=new BlockPos(column.x(),y,column.z());var state=world.getBlockState(p);
            // Soil may already be present after replaying a partially completed plan.
            if ((!open(state) && !soil(state) && !(column.stairs()!=null && state.isOf(Blocks.STONE_BRICK_STAIRS))) || world.getBlockEntity(p)!=null) return;
        }
        for (int y=column.ground();y<=column.target();y++) {
            var p=new BlockPos(column.x(),y,column.z());
            var state=(y==column.target()?Blocks.GRASS_BLOCK:Blocks.DIRT).getDefaultState();
            if(y==column.target() && column.stairs()!=null)state=Blocks.STONE_BRICK_STAIRS.getDefaultState().with(
                    net.minecraft.state.property.Properties.HORIZONTAL_FACING,net.minecraft.util.math.Direction.valueOf(column.stairs().toUpperCase(java.util.Locale.ROOT)));
            world.setBlockState(p,state,net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
        var above=new BlockPos(column.x(),column.target()+1,column.z());
        if (world.getBlockState(above).isOf(Blocks.TALL_GRASS) || world.getBlockState(above).isOf(Blocks.LARGE_FERN))
            world.setBlockState(above,Blocks.SHORT_GRASS.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS);
    }
}
