package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.SpringbloomEntity;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

public final class SpringbloomServerSmoke implements ModInitializer {
    private int tick;
    private ServerPlayerEntity owner,enemy;
    private final BlockPos forest=new BlockPos(12,100,0);
    private final BlockPos pad=new BlockPos(0,100,0);
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});}
    private void run(MinecraftServer server)throws Exception {
        int t=++tick;
        if(t==30){
            owner=player(server,"SpringCaster",.5,100,-3);enemy=player(server,"SpringEnemy",.5,100,3);
            var w=owner.getEntityWorld();for(int x=-25;x<=30;x++)for(int z=-20;z<=20;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(int x=-1;x<=1;x++)for(int z=-5;z<=-1;z++){
                w.setBlockState(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.getDefaultState());
                w.setBlockState(new BlockPos(x,100,z),((x+z)%2==0?Blocks.SHORT_GRASS:Blocks.LEAF_LITTER).getDefaultState());
            }
            for(var p:List.of(owner,enemy)){p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.setHealth(20);p.getHungerManager().setFoodLevel(10);}
            owner.setAttached(EWAttachments.AFFINITY,"NATURE");owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!SpringbloomManager.cast(owner),"Unowned spell cast");
            WandProgression.earn(owner,WizardAffinity.NATURE,500);WandProgression.purchase(owner,"NATURE","springbloom");
            require(WandProgression.owns(owner,WandSpells.find("springbloom")),"Purchase failed");
            require(WandSpells.forAffinity(WizardAffinity.NATURE).size()==5,"Nature does not have five spells");
            WandLoadouts.equip(owner,"NATURE",0,"springbloom");owner.setYaw(0);owner.setPitch(65);
            WandLoadouts.cast(owner,0);require(owner.getAttachedOrElse(EWAttachments.SPRINGBLOOM_READY,0L)>0,"Equipped cast did not start cooldown");
            owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!SpringbloomManager.cast(owner),"Fresh wand bypassed cooldown");
        }
        if(t==45){require(owner.getEntityWorld().getEntitiesByClass(SpringbloomEntity.class,new Box(-30,95,-25,30,140,25),SpringbloomEntity::open).size()==1,"Thrown pod did not plant");}
        if(t==50){
            require(SpringbloomManager.plant(owner,pad),"Could not plant collision fixture");
            // A hostile player falling from 25 blocks must be caught before damage, then launched.
            enemy.setPosition(.22,125,.5);enemy.setOnGround(false);enemy.fallDistance=25;
            enemy.setVelocity(.28,-5,0);enemy.move(MovementType.SELF,new Vec3d(.28,-30,0));
            require(enemy.getHealth()==20,"Incoming fall damaged player");
            require(enemy.getVelocity().y>1.9,"Pad did not launch descending enemy: "+enemy.getVelocity()+" at "+enemy.getEntityPos());
            require(SpringbloomManager.protectedFall(enemy),"Launch did not grant fall protection");
            double startY=enemy.getY(),startX=enemy.getX(),top=startY;Vec3d v=enemy.getVelocity();
            for(int i=0;i<65;i++){
                enemy.setVelocity(v);enemy.move(MovementType.SELF,v);top=Math.max(top,enemy.getY());
                if(enemy.isOnGround())break;
                v=new Vec3d(v.x*.91,(v.y-.08)*.98,v.z*.91);
            }
            require(top-startY>19 && top-startY<22,"Wrong apex "+(top-startY));
            require(enemy.getX()-startX>14 && enemy.getX()-startX<16,"Wrong horizontal distance "+(enemy.getX()-startX));
            enemy.handleFallDamage(25,1,enemy.getDamageSources().fall());
            require(enemy.getHealth()==20,"Launch landing damaged player");
            require(!SpringbloomManager.protectedFall(enemy),"Protection survived landing");
            enemy.timeUntilRegen=0;enemy.handleFallDamage(6,1,enemy.getDamageSources().fall());require(enemy.getHealth()<20,"Later ordinary fall remained protected");
        }
        if(t==55){
            enemy.setPosition(-2,100,.5);enemy.setOnGround(true);enemy.setVelocity(.28,0,0);
            enemy.move(MovementType.SELF,new Vec3d(2,0,0));require(enemy.getY()==100 && enemy.getX()<-.4,"Could walk onto pad");
            require(enemy.getVelocity().y<1,"Side contact launched");
            // Landing on the overhanging edge must be as safe as landing centrally.
            enemy.setHealth(20);enemy.setPosition(-.18,125,.5);enemy.setOnGround(false);enemy.fallDistance=25;
            enemy.move(MovementType.SELF,new Vec3d(0,-30,0));require(enemy.getHealth()==20 && enemy.getVelocity().y>1.9,"Rim catch failed");
            enemy.setPosition(5,100,5);enemy.setOnGround(true);
            require(owner.getEntityWorld().breakBlock(pad,false,enemy),"Enemy could not break flower");
        }
        if(t==60){
            require(!SpringbloomManager.hasPad(owner.getEntityWorld(),pad),"Broken pad remained active");
            require(SpringbloomManager.plant(owner,pad),"Expiry pad failed");
            owner.getEntityWorld().setBlockState(pad.down(),Blocks.AIR.getDefaultState());
        }
        if(t==64){require(owner.getEntityWorld().getBlockState(pad).isAir(),"Support loss left pad");owner.getEntityWorld().setBlockState(pad.down(),Blocks.STONE.getDefaultState());
            owner.getEntityWorld().setBlockState(pad.up(2),Blocks.STONE.getDefaultState());require(!SpringbloomManager.plant(owner,pad),"Planted under ceiling");
            owner.getEntityWorld().setBlockState(pad.up(2),Blocks.AIR.getDefaultState());require(SpringbloomManager.plant(owner,pad),"Expiry placement failed");}
        if(t==143)require(owner.getEntityWorld().getBlockState(pad).isOf(ModSpellBlocks.SPRINGBLOOM),"Pad expired early");
        if(t==146){require(owner.getEntityWorld().getBlockState(pad).isAir(),"Pad exceeded four seconds");
            owner.getEntityWorld().setBlockState(pad,ModSpellBlocks.SPRINGBLOOM.getDefaultState());}
        if(t==150){require(owner.getEntityWorld().getBlockState(pad).isAir(),"Orphan pad survived reload cleanup");
            require(SpringbloomManager.plant(owner,pad),"Owner exit fixture failed");owner.setAttached(EWAttachments.AFFINITY,"FIRE");}
        if(t==154){require(owner.getEntityWorld().getBlockState(pad).isAir(),"Affinity exit left pad");owner.setAttached(EWAttachments.AFFINITY,"NATURE");}
        if(t==155){
            var w=owner.getEntityWorld();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.setBlockState(forest.add(x,-1,z),Blocks.GRASS_BLOCK.getDefaultState());
            w.setBlockState(forest,Blocks.SHORT_GRASS.getDefaultState());w.setBlockState(forest.east(),Blocks.FERN.getDefaultState());
            w.setBlockState(forest.west(),Blocks.LEAF_LITTER.getDefaultState());
            require(SpringbloomManager.plant(owner,forest),"Grass/fern/litter blocked planting");
            require(w.getBlockState(forest.east()).isOf(Blocks.FERN)&&w.getBlockState(forest.west()).isOf(Blocks.LEAF_LITTER),"Adjacent plants changed");
            require(SpringbloomManager.plant(owner,pad),"Replacement fixture failed");
            require(w.getBlockState(forest).isOf(Blocks.SHORT_GRASS),"Covered grass not restored");
            w.setBlockState(forest,Blocks.LEAF_LITTER.getDefaultState());require(SpringbloomManager.plant(owner,forest),"Leaf litter blocked center");
            require(SpringbloomManager.plant(owner,pad),"Second replacement failed");require(w.getBlockState(forest).isOf(Blocks.LEAF_LITTER),"Leaf litter not restored");
            net.minecraft.block.TallPlantBlock.placeAt(w,Blocks.TALL_GRASS.getDefaultState(),forest,3);
            require(SpringbloomManager.plant(owner,forest),"Tall grass blocked center");
            require(w.getBlockState(forest.up()).isAir(),"Tall grass upper half still covers pad");
            require(SpringbloomManager.plant(owner,pad),"Tall grass restoration fixture failed");
            require(w.getBlockState(forest).isOf(Blocks.TALL_GRASS)&&w.getBlockState(forest.up()).isOf(Blocks.TALL_GRASS),"Tall grass pair not restored");
            w.setBlockState(forest.up(2),Blocks.OAK_LEAVES.getDefaultState());
            require(!SpringbloomManager.plant(owner,forest),"Solid leaf canopy allowed over pad");w.setBlockState(forest.up(2),Blocks.AIR.getDefaultState());
            require(SpringbloomManager.plant(owner,forest),"Tall grass expiry planting failed");
        }
        if(t==238){
            var w=owner.getEntityWorld();
            require(w.getBlockState(forest).isOf(Blocks.TALL_GRASS)&&w.getBlockState(forest.up()).isOf(Blocks.TALL_GRASS),"Tall grass lost on expiry");
            w.setBlockState(forest,Blocks.AIR.getDefaultState());w.setBlockState(forest.up(),Blocks.AIR.getDefaultState());
            w.setBlockState(forest,Blocks.WATER.getDefaultState());require(!SpringbloomManager.plant(owner,forest),"Water replaced");
            w.setBlockState(forest,Blocks.AIR.getDefaultState());
            w.setBlockState(forest,ModSpellBlocks.NATURE_ROOTS.getDefaultState());require(!SpringbloomManager.plant(owner,forest),"Existing Nature growth replaced");
        }
        if(t==229)require(!SpringbloomManager.cast(owner),"Cooldown ended before 200 ticks");
        if(t==231){owner.setPitch(-90);require(SpringbloomManager.cast(owner),"Cooldown failed to recover");}
        if(t==242){
            var w=owner.getEntityWorld();w.setBlockState(forest,Blocks.AIR.getDefaultState());
            w.setBlockState(forest.east(),Blocks.STONE.getDefaultState());w.setBlockState(forest.north(),Blocks.OAK_LOG.getDefaultState());
            require(SpringbloomManager.plant(owner,forest),"Neighbors prevented a partial flower");
            int cells=w.getBlockState(forest).get(com.anton.elementalwands.block.SpringbloomBlock.OPEN_CELLS);
            require((cells&SpringbloomFootprint.CENTER)!=0 && (cells&SpringbloomFootprint.bit(1,0))==0
                    && (cells&SpringbloomFootprint.bit(0,-1))==0,"Incorrect blocked-cell mask");
            require(!SpringbloomFootprint.overlaps(cells,forest,new Box(13.2,100,0.2,13.8,102,.8)),"Blocked edge still catches players");
            require(w.getBlockState(forest.east()).isOf(Blocks.STONE)&&w.getBlockState(forest.north()).isOf(Blocks.OAK_LOG),"Plant overwrote an obstacle");
            enemy.setHealth(20);enemy.setPosition(12.5,105,.5);enemy.setOnGround(false);enemy.fallDistance=20;
            enemy.move(MovementType.SELF,new Vec3d(0,-8,0));require(enemy.getHealth()==20&&enemy.getVelocity().y>1.9,"Partial center failed to catch falling player");
            enemy.setPosition(5,100,5);enemy.setOnGround(true);
            w.setBlockState(forest.east(),Blocks.AIR.getDefaultState());
            w.setBlockState(forest.west(),Blocks.STONE.getDefaultState());
        }
        if(t==245){
            int cells=owner.getEntityWorld().getBlockState(forest).get(com.anton.elementalwands.block.SpringbloomBlock.OPEN_CELLS);
            require((cells&SpringbloomFootprint.bit(1,0))!=0&&(cells&SpringbloomFootprint.bit(-1,0))==0,"Footprint did not follow obstacle edits");
            owner.getEntityWorld().setBlockState(forest.east(),Blocks.STONE_SLAB.getDefaultState());
        }
        if(t==248){
            enemy.setPosition(13.5,101.5,.5);enemy.setOnGround(false);enemy.setVelocity(0,-1,0);enemy.fallDistance=0;
            enemy.move(MovementType.SELF,new Vec3d(0,-3,0));
            require(Math.abs(enemy.getY()-100.5)<.01&&enemy.getVelocity().y<1,"Invisible clipped edge launched player above slab: "+enemy.getEntityPos());
        }
        if(t==251){

            Files.writeString(Path.of("HUB_PASSED.txt"),"Springbloom server passed: five Nature spells, ownership/purchase/equip, thrown pod planting, player-owned 200-tick cooldown, hostile high-fall catch, rim catch, ~20-block apex/~15-block travel, protected landing then ordinary fall damage, no walking/side activation, destruction, support/ceiling checks, exact 80-tick expiry, orphan and affinity cleanup, thrown pod through forest floor cover, grass/fern/leaf-litter placement, tall-plant restoration, canopy/fluid/growth rejection, partial placement beside obstacles, obstacle preservation, clipped collision, partial high-fall catch, and live footprint updates. Client physics needs its separate native fixture.\n");server.stop(false);}
    }
    private int pods(){return owner.getEntityWorld().getEntitiesByClass(SpringbloomEntity.class,new Box(-30,90,-30,30,140,30),e->!e.open()&&!e.isRemoved()).size();}
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception {
        var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);
    }
    private static void require(boolean c,String why){if(!c)throw new AssertionError(why);}
}
