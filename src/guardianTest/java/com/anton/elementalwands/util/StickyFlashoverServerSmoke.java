package com.anton.elementalwands.util;

import java.nio.file.*;
import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FlashoverEmberEntity;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Real-server moving attachments, delayed target sampling, disarming and cancellation. */
public final class StickyFlashoverServerSmoke implements ModInitializer {
    private int ticks;private ServerPlayerEntity p,host,ally;private CowEntity bystander,mob;
    private net.minecraft.entity.mob.CreeperEntity creeper;
    private List<FlashoverEmberEntity> charges;private Vec3d before;
    public void onInitialize() {ServerTickEvents.END_SERVER_TICK.register(s -> {
        try {run(s);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}s.stop(false);}
    });}
    private void run(MinecraftServer s)throws Exception {
        int t=++ticks;
        if(t==30) {
            p=player(s,"StickyCaster",.5,100,.5);host=player(s,"StickyTarget",.5,100,4.5);ally=player(s,"StickyAlly",.5,100,2);
            var w=p.getEntityWorld();for(int x=-6;x<=24;x++)for(int z=-6;z<=12;z++){w.getChunk(new BlockPos(x,99,z));w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());}
            for(var q:List.of(p,host,ally)) {q.setLoaded(true);q.onTeleportationDone();q.setNoGravity(true);q.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);q.setHealth(200);}
            p.setAttached(EWAttachments.AFFINITY,"FIRE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            WandProgression.earn(p,WizardAffinity.FIRE,500);WandProgression.purchase(p,"FIRE","flashover");WandLoadouts.equip(p,"FIRE",1,"flashover");
            var team=s.getScoreboard().addTeam("sticky_friends");s.getScoreboard().addScoreHolderToTeam(p.getName().getString(),team);s.getScoreboard().addScoreHolderToTeam(ally.getName().getString(),team);
            aim(p,0,10);for(int i=0;i<3;i++){ready();require(FlashoverManager.toss(p),"Initial throw failed");}
        }
        if(t==66) {
            charges=FlashoverManager.active(p);require(charges.size()==3 && charges.stream().allMatch(e -> e.armed() && e.attachedId()==host.getId()),"Embers did not pass ally and attach/arm on enemy player");
            before=charges.getFirst().getEntityPos();host.setPosition(6.5,100,4.5);host.setYaw(90);host.setBodyYaw(90);host.setHeadYaw(90);
        }
        if(t==68)require(charges.getFirst().getEntityPos().distanceTo(before)>5 && charges.getFirst().getEntityPos().distanceTo(host.getEntityPos())<2,"Attachment failed to follow movement/turn");
        if(t==70) {
            require(FlashoverManager.detonate(p),"Sequence failed to start");require(host.getHealth()==188 && FlashoverManager.active(p).size()==2,"First blast wrong: health="+host.getHealth()+" active="+FlashoverManager.active(p).size()+" at="+charges.getFirst().getEntityPos()+" host="+host.getEntityPos());
            host.setPosition(12.5,100,4.5);bystander=FireLeapServerCases.cow(p,16.1,100,4.5);
            creeper=net.minecraft.entity.EntityType.CREEPER.create(p.getEntityWorld(),net.minecraft.entity.SpawnReason.COMMAND);
            creeper.setPosition(15.5,100,4.5);creeper.setAiDisabled(true);creeper.setNoGravity(true);p.getEntityWorld().spawnEntity(creeper);
        }
        if(t==72)require(host.getHealth()==188 && bystander.getHealth()==200,"Blast occurred before three-tick gap");
        if(t==74)require(host.getHealth()==180 && bystander.getHealth()==188,"Second blast did not follow host/use four-block radius: "+host.getHealth()+" / "+bystander.getHealth()+" active="+FlashoverManager.active(p).stream().map(e -> e.getEntityPos()+":"+e.isRemoved()).toList()+" loaded="+host.isLoaded());
        if(t==74)require(creeper.isAlive() && creeper.getHealth()==8,"First bomb should wound, not kill, a creeper");
        if(t==77)require(!creeper.isAlive(),"Two bombs should kill a full-health creeper");
        if(t==77)require(host.getHealth()==176 && bystander.getHealth()==180 && FlashoverManager.active(p).isEmpty(),"Third blast timing or per-victim cap wrong");
        if(t==95) {
            resetScene();mob=FireLeapServerCases.cow(p,.5,100,4.5);aim(p,0,15);ready();require(FlashoverManager.toss(p),"Mob throw failed");
        }
        if(t==112) {
            charges=FlashoverManager.active(p);require(charges.size()==1 && charges.getFirst().attachedId()==mob.getId(),"Did not stick to a mob");
            mob.setPosition(5.5,100,4.5);
        }
        if(t==114){require(charges.getFirst().getEntityPos().distanceTo(mob.getEntityPos())<2,"Mob attachment did not follow");mob.discard();}
        if(t==116)require(FlashoverManager.active(p).isEmpty(),"Removed host retained an ember");
        if(t==125) {
            resetScene();aim(p,0,0);
            for(int x=-2;x<=2;x++)for(int y=100;y<=103;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,3),Blocks.STONE.getDefaultState());
            host.setPosition(.5,100,4.5);ready();FlashoverManager.toss(p);
        }
        if(t==141) {
            charges=FlashoverManager.active(p);require(charges.size()==1 && charges.getFirst().settled() && charges.getFirst().attachedId()<0,"Wall did not block entity attachment");
            FlashoverManager.clear(p);
            for(int x=-2;x<=2;x++)for(int y=100;y<=103;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,3),Blocks.AIR.getDefaultState());
        }
        if(t==150) {resetScene();host.setPosition(.5,100,4.5);aim(p,0,10);for(int i=0;i<3;i++){ready();FlashoverManager.toss(p);}}
        if(t==167) {
            charges=FlashoverManager.active(p);require(charges.size()==3 && FlashoverManager.detonate(p),"Disarm sequence failed");
            host.attack(charges.get(1));require(charges.get(1).isRemoved(),"Queued charge could not be disarmed");
        }
        if(t==172)require(host.getHealth()==188,"Disarmed queued charge still exploded");
        if(t==178)require(host.getHealth()==180,"Remaining third charge did not explode after skipped second");
        if(t==185) {resetScene();host.setPosition(.5,100,4.5);aim(p,0,10);for(int i=0;i<3;i++){ready();FlashoverManager.toss(p);}}
        if(t==202){FlashoverManager.detonate(p);FlashoverManager.clear(p);}
        if(t==215) {
            require(host.getHealth()==188 && FlashoverManager.active(p).isEmpty(),"Cleanup retained queued explosions");
            resetScene();host.setPosition(.5,100,4.5);aim(p,0,10);ready();FlashoverManager.toss(p);
        }
        if(t==232) {
            require(FlashoverManager.active(p).size()==1 && FlashoverManager.active(p).getFirst().attachedId()==host.getId(),"Water fixture did not attach");
            for(int x=-1;x<=1;x++)for(int y=100;y<=101;y++)for(int z=4;z<=5;z++)p.getEntityWorld().setBlockState(new BlockPos(x,y,z),Blocks.WATER.getDefaultState());
        }
        if(t==240) {
            require(FlashoverManager.active(p).isEmpty(),"Water failed to extinguish attached ember");
            Files.writeString(Path.of("HUB_PASSED.txt"),"Sticky Flashover passed: ignore allies, attach to player/mob, follow translation/turn, wall occlusion, current-position blasts at 0/3/6 ticks, four-block splash, 12+8+4 cap through hurt immunity, one bomb spares and two bombs kill a creeper, mob removal, queued disarming, sequence cancellation and water extinguishing.\n");s.stop(false);
        }
    }
    private void resetScene() {FlashoverManager.clear(p);p.setPosition(.5,100,.5);host.setPosition(20,100,4.5);host.setHealth(200);host.timeUntilRegen=0;host.setVelocity(Vec3d.ZERO);if(bystander!=null)bystander.discard();}
    private void ready() {var state=FireBuildManager.state(p);state.putInt("flash_duration",0);for(int i=0;i<3;i++)state.putInt("flash_slot_"+i+"_duration",0);p.setAttached(EWAttachments.FIRE_BUILD_STATE,state);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));}
    private static void aim(ServerPlayerEntity p,float yaw,float pitch){p.setYaw(yaw);p.setHeadYaw(yaw);p.setPitch(pitch);}
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception {
        var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);f.setAccessible(true);
        return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);
    }
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
