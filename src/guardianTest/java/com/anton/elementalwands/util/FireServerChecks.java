package com.anton.elementalwands.util;

import com.anton.elementalwands.entity.PyreFrontEntity;
import com.anton.elementalwands.item.FireAbilityHandler;
import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Real scheduler and entity lifecycle checks on a separate disposable runway. */
final class FireServerChecks {
    private final int floor;
    private PyreFrontEntity wall;
    private double startX;
    FireServerChecks(ServerWorld world,ServerPlayerEntity player) {
        floor=player.getBlockPos().getY()-1;
        for(int x=96;x<146;x++)for(int z=-3;z<=3;z++) {
            world.getChunk(new BlockPos(x,floor,z));
            world.setBlockState(new BlockPos(x,floor,z),Blocks.STONE.getDefaultState());
            for(int y=1;y<=4;y++)world.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());
        }
        world.spawnEntity(new FireAbilityHandler.PyreSchedulerEntity(world,player,new Vec3d(100.5,floor+2.62,.5),new Vec3d(1,0,0)));
    }
    void check(ServerWorld world,int tick) {
        if(tick==32) {
            var walls=world.getEntitiesByClass(PyreFrontEntity.class,new Box(95,floor-1,-6,148,floor+10,6),e->true);
            require(walls.size()==1,"Pyre did not create exactly one synchronized wall");wall=walls.getFirst();startX=wall.getX();
            require(!wall.isInvisible()&&!wall.shouldSave()&&!wall.isAttackable()&&!wall.canBeHitByProjectile(),"Pyre visual has unsafe lifecycle or interaction");
            require(Math.abs(wall.getY()-(floor+1))<.001,"Pyre wall does not sit on the sampled floor");
        }
        if(tick==36)require(wall!=null&&wall.getX()>startX+2.5,"Pyre wall does not advance with its scheduler");
        if(tick==72)require(wall!=null&&wall.isRemoved(),"Pyre wall outlived its moving front");
        if(tick==146) {
            require(world.getBlockState(new BlockPos(102,floor,0)).isOf(Blocks.STONE),"Pyre floor did not restore original material");
            require(!world.getBlockState(new BlockPos(102,floor+1,0)).isOf(ModSpellBlocks.PYRE_FLAME),"Expired low Pyre fire remains");
            System.out.println("FIRE SERVER CHECKS PASSED: one moving wall, nonpersistent/noninteractive, exact floor height, timely cleanup and restored runway.");
        }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
