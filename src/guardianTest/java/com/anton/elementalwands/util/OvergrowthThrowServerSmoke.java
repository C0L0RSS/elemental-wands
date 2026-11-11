package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class OvergrowthThrowServerSmoke implements ModInitializer {
    private int ticks;
    private ServerPlayerEntity p, other;
    private BlockPos first, enhanced = new BlockPos(0,100,15), ordinary = new BlockPos(30,100,15);
    private CowEntity victim, enhancedVictim, ordinaryVictim;
    public void onInitialize() {ServerTickEvents.END_SERVER_TICK.register(server -> {
        try {run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}
    });}
    private void run(MinecraftServer server) throws Exception {
        int t=++ticks;
        if(t==30) {
            p=player(server,"OakCaster",-24.5,100,.5);other=player(server,"OtherGardener",-35,100,-5);
            var w=p.getEntityWorld();for(int x=-40;x<=40;x++)for(int z=-8;z<=35;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(var q:List.of(p,other)) {q.setLoaded(true);q.onTeleportationDone();q.setNoGravity(true);q.getHungerManager().setFoodLevel(10);q.setAttached(EWAttachments.AFFINITY,"NATURE");}
            WandProgression.grant(p,3);p.setAttached(EWAttachments.WAND_LOADOUTS,Map.of("NATURE",List.of("thorn_lash","tendril_bloom","overgrowth")));
        }
        if(t==35){prepare(-24.5,100,.5,0,20);throwAcorn();require(charge()==0,"Throw did not spend charge");}
        if(t==55) {
            var trees=trees();require(trees.size()==1,"Seedless throw did not grow exactly one tree: "+trees.size());
            first=trees.getFirst().getBlockPos();require(first.getX()==-25 && first.getZ()>0 && first.getZ()<10,"Unexpected throw landing: "+first);
            int left=OvergrowthManager.remainingTicks(p.getEntityWorld(),p.getUuid(),first);require(left>270 && left<=300,"Base duration wrong: "+left);
        }
        if(t==65) {
            enhancedVictim=cow(5.5,100,15.5);ordinaryVictim=cow(35.5,100,15.5);
            require(OvergrowthManager.startThrownOvergrowth(p.getEntityWorld(),p,enhanced),"Enhanced tree failed");
            require(OvergrowthManager.startThrownOvergrowth(p.getEntityWorld(),p,ordinary),"Ordinary tree failed");
            require(OvergrowthManager.remainingTicks(p.getEntityWorld(),p.getUuid(),enhanced)==300,"Second tree duration wrong");
            require(OvergrowthManager.remainingTicks(p.getEntityWorld(),p.getUuid(),ordinary)==300,"Tree duration changed");
            near(enhancedVictim.getHealth(),194,"Second burst changed");near(ordinaryVictim.getHealth(),194,"Base burst changed");
        }
        if(t==80) {
            for(int x=-27;x<=-22;x++)for(int y=100;y<=112;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,24),Blocks.STONE.getDefaultState());
            prepare(-24.5,100,20.5,0,0);throwAcorn();
        }
        if(t==105)require(trees().stream().anyMatch(tree->tree.getBlockX()==-25 && tree.getBlockZ()==23 && tree.getBlockY()==100),"Wall hit did not drop seed onto ground");
        if(t==110) {victim=cow(24.5,100,3.5);prepare(24.5,100,-.5,0,0);throwAcorn();}
        if(t==114)near(victim.getHealth(),200,"Projectile itself damaged enemy");
        if(t==130) {
            require(trees().stream().anyMatch(tree->tree.getBlockX()==24 && tree.getBlockZ()<4),"Enemy hit did not grow grounded tree");
            near(victim.getHealth(),194,"Enemy-hit tree burst wrong");
            p.getEntityWorld().setBlockState(new BlockPos(10,99,0),Blocks.WATER.getDefaultState());
            prepare(10.5,100,.5,0,90);throwAcorn();
        }
        if(t==137){require(charge()==100,"Water landing did not refund charge");require(OvergrowthManager.remainingTicks(p.getEntityWorld(),p.getUuid(),new BlockPos(10,100,0))==0,"Tree grew over water");
            p.getEntityWorld().setBlockState(new BlockPos(10,103,10),Blocks.STONE.getDefaultState());
            prepare(10.5,100,10.5,0,90);throwAcorn();}
        if(t==145) {
            require(charge()==100,"Cramped landing did not refund charge");
            prepare(-35.5,100,30.5,0,-30);throwAcorn();p.setAttached(EWAttachments.AFFINITY,"FIRE");
        }
        if(t==150) {
            require(p.getEntityWorld().getEntitiesByClass(OvergrowthSeedEntity.class,new Box(-100,80,-100,100,160,100),e->!e.isRemoved()).isEmpty(),"Affinity exit left projectile active");
            require(charge()==0,"Lifecycle cancellation refunded spent charge");p.setAttached(EWAttachments.AFFINITY,"NATURE");
        }
        if(t==370) {
            require(OvergrowthManager.remainingTicks(p.getEntityWorld(),p.getUuid(),ordinary)==0
                    && OvergrowthManager.remainingTicks(p.getEntityWorld(),p.getUuid(),enhanced)==0,"A tree outlived fifteen seconds");
            Files.writeString(Path.of("HUB_PASSED.txt"),"Throwable Overgrowth passed: real cast and charge spend, grounded landing, identical burst damage, fifteen-second expiry for every tree, wall and mob impacts, harmless projectile, water/ceiling rejection with refund, lifecycle cancellation without refund.\n");
            server.stop(false);
        }
    }
    private List<AwakenedTreeEntity> trees(){return p.getEntityWorld().getEntitiesByClass(AwakenedTreeEntity.class,new Box(-50,90,-10,50,130,40),e->!e.isRemoved());}
    private void prepare(double x,double y,double z,float yaw,float pitch){p.setPosition(x,y,z);p.setYaw(yaw);p.setPitch(pitch);var stack=new ItemStack(ModItems.FRACTURED_WAND);NbtComponent.set(DataComponentTypes.CUSTOM_DATA,stack,data->data.putInt("elementalwands:ultimate_charge",100));p.setStackInHand(Hand.MAIN_HAND,stack);}
    /** The oak is no longer any spell; the acorn is thrown through its kept entry point. */
    private void throwAcorn(){com.anton.elementalwands.item.NatureAbilityHandler.castOvergrowth((net.minecraft.server.world.ServerWorld)p.getEntityWorld(),p,p.getMainHandStack());}
    private int charge(){return p.getMainHandStack().getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getInt("elementalwands:ultimate_charge",0);}
    private CowEntity cow(double x,double y,double z){var cow=new CowEntity(EntityType.COW,p.getEntityWorld());cow.setAiDisabled(true);cow.setNoGravity(true);cow.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);cow.refreshPositionAndAngles(x,y,z,0,0);p.getEntityWorld().spawnEntity(cow);return cow;}
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception{var f=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);f.setAccessible(true);return(ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);}
    private static void near(float value,float expected,String why){require(Math.abs(value-expected)<.01,why+": "+value+" expected "+expected);}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
