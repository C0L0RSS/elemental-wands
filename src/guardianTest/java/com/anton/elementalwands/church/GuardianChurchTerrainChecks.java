package com.anton.elementalwands.church;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

final class GuardianChurchTerrainChecks {
    static void run(ServerWorld world) {
        var site=new GuardianChurchManager.Site();site.x=60;site.z=30;
        int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,60,30)-1;
        site.y=ground+6; // Reproduce a four-block exposed edge.
        var chest=new BlockPos(77,ground+1,30);world.setBlockState(chest,Blocks.CHEST.getDefaultState());
        var road=new BlockPos(77,ground+1,31);world.setBlockState(road,Blocks.STONE_BRICKS.getDefaultState());
        var tree=new BlockPos(77,ground+1,32);world.setBlockState(tree,Blocks.OAK_LOG.getDefaultState());
        var plan=GuardianChurchTerrain.plan(world,site);
        check(!plan.isEmpty(),"Elevated site did not receive a slope plan");
        for(var c:plan)check(c.x()!=77 || c.z()<30 || c.z()>32,"Blend would bury a chest, road or tree");
        for(var c:plan)GuardianChurchTerrain.apply(world,c);
        int last=ground+4;
        for(int x=77;x<=90;x++) {
            int surface=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,29)-1;
            check(Math.abs(surface-last)<=1,"Straight elevated edge remains at "+x+" surface="+surface+" previous="+last+" planned="+plan.stream().filter(c -> c.x()==77 && c.z()==29).toList()+" protected="+GuardianChurchManager.protectedBlock(world,new BlockPos(x,surface,29)));
            last=surface;
        }
        check(last==ground,"Slope did not meet the original surrounding terrain");
        int before=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,80,29);
        for(var c:plan)GuardianChurchTerrain.apply(world,c);
        check(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,80,29)==before,"Replaying a saved plan raised the ground twice");
        check(world.getBlockState(chest).isOf(Blocks.CHEST) && world.getBlockState(road).isOf(Blocks.STONE_BRICKS) && world.getBlockState(tree).isOf(Blocks.OAK_LOG),"Terrain blending overwrote non-soil structures");
        var modern=new GuardianChurchManager.Site();modern.x=100;modern.z=30;modern.y=ground+4;modern.layoutVersion=2;
        for(int x=93;x<=116;x++)for(int z=10;z<=45;z++)world.getChunk(x>>4,z>>4);
        // A two-block uphill bank beside the central route, over natural soil.
        for(int x=104;x<=107;x++)for(int z=32;z<=38;z++)for(int y=ground;y<=ground+4;y++)
            world.setBlockState(new BlockPos(x,y,z),(y==ground+4?Blocks.GRASS_BLOCK:Blocks.DIRT).getDefaultState());
        var terraces=GuardianChurchTerrain.plan(world,modern);
        check(terraces.stream().anyMatch(c -> c.target()<c.ground()),"Gentle uphill courtyard has no trim plan");
        check(terraces.stream().anyMatch(c -> c.stairs()!=null),"Lower entrance has no stair plan");
        for(var c:terraces)GuardianChurchTerrain.apply(world,c);
        int trimmed=world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,104,34);
        check(trimmed<ground+5,"Uphill route bank was left blocking the walkway");
        for(var c:terraces)GuardianChurchTerrain.apply(world,c);
        check(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,104,34)==trimmed,"Trim plan cut deeper on replay");
        check(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,114,30)==ground+1,"Natural courtyard away from the route was flattened");
        System.out.println("MODERN CHURCH TERRAIN CHECKS PASSED: uphill soil trimmed, downhill entrance stairs, natural courtyard preserved, replay stable.");
        System.out.println("CHURCH TERRAIN CHECKS PASSED: four-block ledge became a walkable slope; objects preserved; replay stable.");
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
