package com.anton.elementalwands.util;

import java.nio.file.*;
import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Real-server regression for independent recoveries, reloads, lethal hits and 60-block travel. */
public final class FireTuningServerSmoke implements ModInitializer {
    private int ticks;private ServerPlayerEntity p;private CreeperEntity creeper;
    private Vec3d landing;
    public void onInitialize() {ServerTickEvents.END_SERVER_TICK.register(s -> {
        try {run(s);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}s.stop(false);}
    });}
    private void run(MinecraftServer s)throws Exception {
        int t=++ticks;
        if(t==30) {
            var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            f.setAccessible(true);p=(ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),"FireTuning",.5,100,.5);
            p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);
            var w=p.getEntityWorld();for(int x=-4;x<=4;x++)for(int z=-4;z<=66;z++) {
                w.getChunk(new BlockPos(x,99,z));w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            }
            p.setAttached(EWAttachments.AFFINITY,"FIRE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            WandProgression.earn(p,WizardAffinity.FIRE,1000);WandProgression.purchase(p,"FIRE","flashover");WandLoadouts.equip(p,"FIRE",1,"flashover");
            creeper=EntityType.CREEPER.create(w,SpawnReason.COMMAND);creeper.setPosition(.5,100,4.5);creeper.setAiDisabled(true);creeper.setNoGravity(true);w.spawnEntity(creeper);
            p.setYaw(0);p.setHeadYaw(0);p.setPitch(10);require(FlashoverManager.toss(p),"First bomb unavailable");
        }
        if(t==50) {
            require(FlashoverManager.active(p).getFirst().attachedId()==creeper.getId(),"Direct-hit fixture missed creeper: bomb="+FlashoverManager.active(p).getFirst().getEntityPos()+" host="+FlashoverManager.active(p).getFirst().attachedId()+" creeper="+creeper.getEntityPos()+" alive="+creeper.isAlive()+" caster="+p.getEntityPos());
            require(FlashoverManager.detonate(p) && creeper.isAlive() && creeper.getHealth()==8,"One bomb should leave a full-health creeper alive with eight health");
            require(FlashoverManager.slotStates(p).equals(List.of(120,0,0)),"Single blast charged other slots");
            require(FlashoverManager.remaining(p)==0,"Single blast locked out spare bombs");
            creeper.discard();p.setPitch(35);require(FlashoverManager.toss(p),"Could not throw spare bomb during first recovery");
            require(!FlashoverManager.toss(p),"Throw pacing bypassed");
        }
        if(t==70) {
            require(FlashoverManager.detonate(p),"First bomb's recovery blocked a second detonation");
            require(FlashoverManager.slotStates(p).equals(List.of(100,120,0)),"Bomb timers are not independent");
            require(FlashoverManager.toss(p),"Third bomb unavailable during other recoveries");
        }
        if(t==90) {
            var bomb=FlashoverManager.active(p).getFirst();bomb.damage(p.getEntityWorld(),p.getDamageSources().playerAttack(p),1);
            require(FlashoverManager.slotStates(p).equals(List.of(80,100,40)),"Disarm must impose two seconds on only its slot");
            require(!FlashoverManager.toss(p),"All recovering slots allowed a throw");
            var before=FireBuildManager.state(p);
            var save=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,p.getRegistryManager());p.writeData(save);
            p.setAttached(EWAttachments.FIRE_BUILD_STATE,new net.minecraft.nbt.NbtCompound());
            p.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,p.getRegistryManager(),save.getNbt()));
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(before.equals(FireBuildManager.state(p)) && FlashoverManager.remaining(p)==40,"Save/load or wand replacement reset recovery");
        }
        if(t==129)require(!FlashoverManager.toss(p),"Lost bomb recovered early");
        if(t==130) {
            require(FlashoverManager.toss(p),"Lost bomb did not recover at two seconds");
            require(FlashoverManager.slotStates(p).equals(List.of(40,60,-1)),"Reusing a slot changed other cooldowns");
            // Simulate a persisted player whose intentionally unsaved bomb vanished on restart.
            var saved=FireBuildManager.state(p);FlashoverManager.clear(p);p.setAttached(EWAttachments.FIRE_BUILD_STATE,saved);
            require(FlashoverManager.slotStates(p).equals(List.of(40,60,40)),"Orphaned slot was refunded or stayed stuck on reload");
        }
        if(t==335) {
            require(FlashoverManager.slotStates(p).equals(List.of(0,0,0)),"Independent timers never recovered");
            WandProgression.purchase(p,"FIRE","fire_hop");WandLoadouts.equip(p,"FIRE",1,"fire_hop");
            p.setPosition(.5,100,.5);landing=new Vec3d(.5,100,60.5);
            Vec3d aim=landing.add(0,0,-.3).subtract(p.getEyePos());p.setYaw(0);p.setHeadYaw(0);p.setPitch((float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalLength())));
            var aimed=FireLeapRules.target(p);
            require(aimed!=null && aimed.distanceTo(landing)<.5 && FireLeapRules.validTarget(p,aimed),"Aiming ray still stops before 60 blocks: target="+aimed+" eye="+p.getEyePos()+" pitch="+p.getPitch()+" aim="+p.getRotationVec(1));
            require(FireLeapRules.validTarget(p,landing),"Clear 60-block route rejected");
            require(!FireLeapRules.validTarget(p,landing.add(0,0,.1)),"Beyond 60 blocks accepted");
            BlockPos obstruction=BlockPos.ofFloored(FireLeapRules.position(p.getEntityPos(),landing,.5));
            p.getEntityWorld().setBlockState(obstruction,Blocks.STONE.getDefaultState());
            require(!FireLeapRules.validTarget(p,landing),"Long route skipped a mid-arc obstruction");
            p.getEntityWorld().setBlockState(obstruction,Blocks.AIR.getDefaultState());
            require(FireLeapManager.commit(p,landing),"60-block leap would not commit");
        }
        if(t==345)require(p.hasVehicle() && p.getZ()>25 && p.getY()>104,"Long leap aborted or missed midpoint");
        if(t==360) {
            require(!p.hasVehicle() && p.getEntityPos().distanceTo(landing)<.1,"60-block leap did not land safely: "+p.getEntityPos());
            require(FireBuildManager.hopRemaining(p)>0,"Long leap lost cooldown");
            FireLeapServerCases.run(p);
            Files.writeString(Path.of("HUB_PASSED.txt"),"Passed: single bomb leaves a creeper alive; spare throws and detonations during recovery; independent 6s blast and 2s disarm timers; throw pacing/cap; player reload and orphan recovery; wand replacement; 60-block aim, full route collision, flight and landing; existing leap safety and wave protections.\n");s.stop(false);
        }
    }
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
