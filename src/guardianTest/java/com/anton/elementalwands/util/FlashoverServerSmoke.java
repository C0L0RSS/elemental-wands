package com.anton.elementalwands.util;

import java.nio.file.*;
import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FlashoverEmberEntity;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

public final class FlashoverServerSmoke implements ModInitializer {
    private int ticks;private ServerPlayerEntity p,enemy,ally;private net.minecraft.entity.passive.CowEntity covered;
    public void onInitialize() { ServerTickEvents.END_SERVER_TICK.register(server -> {
        try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}
    }); }
    private void run(MinecraftServer s)throws Exception {
        int t=++ticks;
        if(t==30) {
            p=player(s,"EmberCaster",.5,100,.5);enemy=player(s,"EmberEnemy",.5,100,2.7);ally=player(s,"EmberAlly",1.5,100,2.7);
            var w=p.getEntityWorld();for(int x=-8;x<=8;x++)for(int z=-5;z<=12;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(var q:List.of(p,enemy,ally)){q.setNoGravity(true);q.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);q.setHealth(200);}
            p.setYaw(0);p.setPitch(35);p.setAttached(EWAttachments.AFFINITY,"FIRE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!FlashoverManager.toss(p),"Unowned spell could be cast");WandProgression.earn(p,WizardAffinity.FIRE,500);
            WandProgression.purchase(p,"FIRE","flashover");WandLoadouts.equip(p,"FIRE",1,"flashover");require(WandProgression.flux(p)==0,"Purchase price wrong");
            var team=s.getScoreboard().addTeam("flash_allies");team.setFriendlyFireAllowed(true);
            s.getScoreboard().addScoreHolderToTeam(p.getName().getString(),team);s.getScoreboard().addScoreHolderToTeam(ally.getName().getString(),team);
            covered=FireLeapServerCases.cow(p,.5,100,5.55);
            for(int x=-2;x<=2;x++)for(int y=100;y<=102;y++)w.setBlockState(new BlockPos(x,y,4),Blocks.STONE.getDefaultState());
            require(FlashoverManager.toss(p),"First toss failed");require(!FlashoverManager.toss(p),"Placement cooldown bypassed");
            require(!FlashoverManager.detonate(p),"Unarmed detonation accepted");
        }
        if(t==55 || t==80)require(FlashoverManager.toss(p),"Subsequent toss failed");
        if(t==108) {
            var list=FlashoverManager.active(p);require(list.size()==3 && list.stream().allMatch(FlashoverEmberEntity::armed),"Three charges did not settle/arm");
            require(!FlashoverManager.toss(p),"Fourth charge accepted");require(enemy.getHealth()==200,"Placement dealt passive damage");
            require(!list.getFirst().damage(p.getEntityWorld(),ally.getDamageSources().playerAttack(ally),1),"Ally accidentally disarmed charge");
            enemy.attack(list.getFirst());require(list.getFirst().isRemoved(),"Enemy melee could not disarm charge");
            require(FlashoverManager.active(p).size()==2 && enemy.getHealth()==200,"Disarm exploded or chain-reacted");
        }
        if(t==112) {
            require(covered.getHealth()==200,"Cover fixture damaged target before detonation");
            require(FlashoverManager.detonate(p),"Armed detonation failed");
            require(enemy.getHealth()==188,"First staggered blast should deal twelve: "+enemy.getHealth());
            require(ally.getHealth()==200 && p.getHealth()==200 && covered.getHealth()==200,"Explosion hit ally/caster/cover: "+ally.getHealth()+" / "+p.getHealth()+" / "+covered.getHealth());
            require(FlashoverManager.active(p).size()==1 && FlashoverManager.remaining(p)==36,"First blast did not queue the second/start recovery");
            require(!FlashoverManager.toss(p),"Detonation recovery bypassed");
            var save=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,p.getRegistryManager());p.writeData(save);
            p.setAttached(EWAttachments.FIRE_BUILD_STATE,new net.minecraft.nbt.NbtCompound());
            p.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,p.getRegistryManager(),save.getNbt()));
            require(FlashoverManager.remaining(p)==36,"Recovery did not survive save/reload");
        }
        if(t==114)require(enemy.getHealth()==188,"Second blast happened before three ticks");
        if(t==118)require(enemy.getHealth()==180 && FlashoverManager.active(p).isEmpty(),"Three-tick follow-up did not apply the remaining eight damage");
        if(t==240)require(FlashoverManager.toss(p),"Recovery never expired");
        if(t==270) {
            var ember=FlashoverManager.active(p).getFirst();p.setPosition(50,100,50);
            require(!FlashoverManager.detonate(p) && !ember.isRemoved(),"Remote range limit failed");p.setPosition(.5,100,.5);
            p.setAttached(EWAttachments.WAND_LOADOUTS,Map.of("FIRE",WandSpells.defaults(WizardAffinity.FIRE)));
        }
        if(t==274) {require(FlashoverManager.active(p).isEmpty(),"Unequip retained traps");p.setAttached(EWAttachments.WAND_LOADOUTS,Map.of("FIRE",List.of("inferno_wave","flashover","meteor")));}
        if(t==280)require(FlashoverManager.toss(p),"Cleanup prevented later toss");
        if(t==305) {
            var ember=FlashoverManager.active(p).getFirst();Vec3d delta=ember.getEntityPos().add(0,.15,0).subtract(enemy.getEyePos());
            enemy.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));enemy.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalLength())));
            enemy.setHeadYaw(enemy.getYaw());
            require(FlashoverManager.tryDisarmAimed(enemy),"Wand close-range disarm failed");
        }
        if(t==310)require(FlashoverManager.toss(p),"Lifetime test toss failed");
        if(t==915) {
            require(FlashoverManager.active(p).isEmpty(),"Thirty-second lifetime did not expire");
            require(FlashoverRules.damage(3)==24 && FlashoverRules.damage(20)==24,"Stack damage cap wrong");
            require(WandProgression.owns(p,WandSpells.find("flashover")),"Ownership lost");
            Files.writeString(Path.of("HUB_PASSED.txt"),"Flashover passed: purchase/equip, toss physics, harmless placement, arming delay, cap, enemy melee/wand disarm, allied protection, cover, capped combined damage, cooldown persistence, range limit, unequip cleanup and 30-second expiry.\n");s.stop(false);
        }
    }
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception {
        var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
        f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);
    }
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
