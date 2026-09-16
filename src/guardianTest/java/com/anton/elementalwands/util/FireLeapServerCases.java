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
    static CowEntity cow(ServerPlayerEntity p,double x,double y,double z) {
        var cow=EntityType.COW.create(p.getEntityWorld(),SpawnReason.COMMAND);cow.setPosition(x,y,z);cow.setNoGravity(true);cow.setAiDisabled(true);
        cow.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);p.getEntityWorld().spawnEntity(cow);return cow;
    }
    static void require(boolean value,String reason) { if(!value)throw new AssertionError(reason); }
}
