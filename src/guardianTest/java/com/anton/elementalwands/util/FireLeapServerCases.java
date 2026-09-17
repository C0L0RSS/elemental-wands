package com.anton.elementalwands.util;

import com.anton.elementalwands.entity.FireLeapEntity;
import com.anton.elementalwands.registry.ModEntities;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;

/** Additional real-world cover, height, validation and reload cases. */
final class FireLeapServerCases {
    static void run(ServerPlayerEntity p) {
        targeting(p);
        var w=p.getEntityWorld();
        for(int x=40;x<=62;x++)for(int z=38;z<=62;z++) {
            w.getChunk(new BlockPos(x,99,z));w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
        }
        p.setPosition(50,100,40);Vec3d to=new Vec3d(50,100,50);
        require(FireLeapRules.validTarget(p,to),"Baseline route rejected");
        w.setBlockState(new BlockPos(50,102,40),Blocks.STONE.getDefaultState());
        require(!FireLeapRules.validTarget(p,to),"Low takeoff ceiling accepted");
        w.setBlockState(new BlockPos(50,102,40),Blocks.AIR.getDefaultState());
        w.setBlockState(new BlockPos(50,100,50),Blocks.WATER.getDefaultState());
        require(!FireLeapRules.validTarget(p,to),"Water landing accepted");
        w.setBlockState(new BlockPos(50,100,50),Blocks.AIR.getDefaultState());
        w.setBlockState(new BlockPos(50,101,50),Blocks.STONE.getDefaultState());
        require(!FireLeapRules.validTarget(p,to),"Landing head collision accepted");
        w.setBlockState(new BlockPos(50,101,50),Blocks.AIR.getDefaultState());
        p.setPosition(50,101.2,40);require(FireLeapRules.supported(w,p,p.getEntityPos(),1.5) && FireLeapRules.validTarget(p,to),"Sprint jump rejected");
        p.setPosition(50,105,40);require(!FireLeapManager.commit(p,to),"Unsupported midair recast accepted");
        p.setPosition(50,100,40);
        var center=cow(p,50,100,50);var outer=cow(p,54,100,50);var jumping=cow(p,50,101.1,47);
        var ally=cow(p,48,100,50);var covered=cow(p,50,100,54);
        var team=w.getScoreboard().addTeam("fire_leap_allies");
        w.getScoreboard().addScoreHolderToTeam(p.getName().getString(),team);w.getScoreboard().addScoreHolderToTeam(ally.getUuidAsString(),team);
        for(int x=49;x<=51;x++)for(int y=100;y<=102;y++)w.setBlockState(new BlockPos(x,y,52),Blocks.STONE.getDefaultState());
        var hits=new java.util.HashSet<java.util.UUID>();
        for(int pass=0;pass<2;pass++)for(int age=0;age<9;age++)FireLeapManager.wave(p,to,age,hits);
        require(center.getHealth()==192 && outer.getHealth()==196,"Damage bands or once-per-wave wrong");
        require(jumping.getHealth()==200 && covered.getHealth()==200 && ally.getHealth()==200,"Wave hit jumping/covered/allied target");
        var leap=new FireLeapEntity(ModEntities.FIRE_LEAP,w);leap.begin(p,to);
        var save=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,p.getRegistryManager());leap.writeData(save);
        var loaded=new FireLeapEntity(ModEntities.FIRE_LEAP,w);
        loaded.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,p.getRegistryManager(),save.getNbt()));
        loaded.tick();require(loaded.isRemoved(),"Reload resumed an orphaned leap");
    }
    private static void targeting(ServerPlayerEntity p) {
        var w=p.getEntityWorld();
        // Isolated lane: clear terrain, then exercise the actual world raycasts.
        for(int x=100;x<=104;x++)for(int z=100;z<=166;z++) {
            w.getChunk(new BlockPos(x,99,z));
            w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(int y=100;y<=110;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        }
        p.setPosition(102.5,100,100.5);p.setYaw(0);p.setHeadYaw(0);
        for(float pitch:new float[]{0, .5f, -15}) {
            p.setPitch(pitch);Vec3d target=FireLeapRules.target(p);
            require(target!=null && Math.abs(target.z-160.5)<.01 && target.y==100
                    && FireLeapRules.validTarget(p,target),"Forward aim failed at pitch "+pitch+": "+target);
        }
        p.setPitch(20);Vec3d near=FireLeapRules.target(p);
        require(near!=null && near.z>103 && near.z<106 && FireLeapRules.validTarget(p,near),"Precise nearby ground aim changed: "+near);
        // A three-block ledge hides its top from the eye ray.
        for(int x=101;x<=103;x++)for(int z=110;z<=113;z++)for(int y=100;y<=102;y++)
            w.setBlockState(new BlockPos(x,y,z),Blocks.STONE.getDefaultState());
        p.setPitch(0);Vec3d ledge=FireLeapRules.target(p);
        require(ledge!=null && ledge.y==103 && ledge.z>110 && ledge.z<111
                && FireLeapRules.validTarget(p,ledge),"Hidden ledge top rejected: "+ledge);
        // Fractional collision tops must be respected, not rounded to block Y.
        for(int x=101;x<=103;x++)for(int z=110;z<=113;z++)
            w.setBlockState(new BlockPos(x,103,z),Blocks.STONE_SLAB.getDefaultState());
        Vec3d slab=FireLeapRules.target(p);
        require(slab!=null && slab.y==103.5 && FireLeapRules.validTarget(p,slab),"Slab ledge rejected: "+slab);
        require(!FireLeapRules.validTarget(p,new Vec3d(102.5,104.5,110.5)),"Excessive rise accepted");
        require(!FireLeapRules.validTarget(p,new Vec3d(102.5,93.5,110.5)),"Excessive drop accepted");
        require(!FireLeapRules.validTarget(p,new Vec3d(Double.NaN,100,110)),"Nonfinite destination accepted");
        w.setBlockState(new BlockPos(102,102,100),Blocks.STONE.getDefaultState());
        require(!FireLeapRules.validTarget(p,slab),"Ledge snapping bypassed takeoff ceiling");
        w.setBlockState(new BlockPos(102,102,100),Blocks.AIR.getDefaultState());
        for(int x=100;x<=104;x++)for(int y=100;y<=110;y++)
            w.setBlockState(new BlockPos(x,y,106),Blocks.STONE.getDefaultState());
        require(!FireLeapRules.validTarget(p,slab),"Ledge snapping bypassed solid wall");
        Vec3d wall=FireLeapRules.target(p);
        require(wall!=null && wall.z<106 && FireLeapRules.validTarget(p,wall),"Tall wall did not stop target in front: "+wall);
    }
    static CowEntity cow(ServerPlayerEntity p,double x,double y,double z) {
        var cow=EntityType.COW.create(p.getEntityWorld(),SpawnReason.COMMAND);cow.setPosition(x,y,z);cow.setNoGravity(true);cow.setAiDisabled(true);
        cow.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);p.getEntityWorld().spawnEntity(cow);return cow;
    }
    static void require(boolean value,String reason) { if(!value)throw new AssertionError(reason); }
}
