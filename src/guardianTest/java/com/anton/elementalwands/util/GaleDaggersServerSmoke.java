package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

public final class GaleDaggersServerSmoke implements ModInitializer {
    private int tick;
    private ServerPlayerEntity owner,target;
    private List<GaleDaggerEntity> aimed;
    private long firstLaunch;
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("PRESSURE_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});}
    private void run(MinecraftServer s)throws Exception{
        int t=++tick;
        if(t==25){
            var w=s.getOverworld();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){w.getChunk(x,z);w.setChunkForced(x,z,true);}
            owner=player(s,"DaggerCaster",.5,100,.5);target=player(s,"DaggerTarget",.5,100,20.5);
            for(var p:List.of(owner,target)){p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.setHealth(20);p.getHungerManager().setFoodLevel(10);}
            owner.setAttached(EWAttachments.AFFINITY,"WIND");owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!GaleDaggers.cast(owner),"Unowned spell cast");
            WandProgression.earn(owner,WizardAffinity.WIND,500);WandProgression.purchase(owner,"WIND",GaleDaggers.ID);
            require(WandProgression.owns(owner,WandSpells.find(GaleDaggers.ID)),"Purchase failed");
            WandLoadouts.equip(owner,"WIND",3,GaleDaggers.ID);owner.setYaw(0);owner.setPitch(0);
            require(WandSpells.forAffinity(WizardAffinity.WIND).size()==4,"Wrong Wind spell count");
            require(WindFanRules.RANGE==7 && WindFanRules.damage(0)==7 && WindFanRules.damage(7)==4,"Primary range/falloff mismatch");
        }
        if(owner==null)return;
        owner.setVelocity(Vec3d.ZERO);target.setVelocity(Vec3d.ZERO);target.setPosition(.5,100,20.5);
        if(t==26){WandLoadouts.cast(owner,3);require(prepared()==3,"Not three prepared daggers");require(GaleDaggers.remaining(owner)==0,"Arming started cooldown");}
        if(t==28){WandLoadouts.cast(owner,0,false);require(prepared()==3,"Held primary auto-fired");}
        if(t==35){
            require(GaleDaggers.remaining(owner)==0,"Prepared state began recovery");
            WandLoadouts.cast(owner,0);firstLaunch=owner.getEntityWorld().getTime();
            require(prepared()==2 && flying()==1,"Primary did not launch exactly one first dagger");
            require(GaleDaggers.remaining(owner)==240,"Cooldown not 12 seconds from first launch");
            require(owner.getMainHandStack().getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt()
                    .getLong(AbstractWandItem.cooldownKey("sky_shear")).isEmpty(),"Primary also cast Sky Shear");
            WandLoadouts.cast(owner,3);require(prepared()==2,"Repeated input duplicated volley");
        }
        if(t==37)require(prepared()==2,"Second dagger fired before three ticks");
        if(t==38)require(prepared()==1,"Second dagger did not fire at three ticks");
        if(t==41)require(prepared()==0 && !GaleDaggers.active(owner),"Third dagger did not fire at six ticks");
        if(t==54){
            require(Math.abs(target.getHealth()-5)<.01,"Three real hits should deal 15; health="+target.getHealth());
            require(WandProgression.get(owner,WizardAffinity.WIND).xp()>0,"Damage lost Wind XP attribution");
            owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!GaleDaggers.cast(owner),"Fresh wand bypassed player cooldown");
        }
        if(t==60){reset();require(GaleDaggers.cast(owner),"Could not prepare aim test");aimed=daggers();}
        if(t==70){owner.setYaw(0);require(GaleDaggers.fire(owner),"Second press failed");}
        if(t==71)owner.setYaw(-90);
        if(t==74)owner.setYaw(90);
        if(t==78){
            var ordered=aimed.stream().sorted(Comparator.comparingInt(GaleDaggerEntity::index)).toList();
            require(ordered.get(0).getVelocity().z>2.8,"First aim wrong");
            require(ordered.get(1).getVelocity().x>2.8,"Second launch did not sample new aim: "+ordered.stream().map(d->d.index()+":"+d.getVelocity()).toList()+" yaw="+owner.getYaw());
            require(ordered.get(2).getVelocity().x< -2.8,"Third launch did not sample new aim");
        }
        if(t==92){require(daggers().isEmpty(),"Projectiles exceeded range/lifetime");reset();require(GaleDaggers.cast(owner),"Cancellation setup failed");owner.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);}
        if(t==94){require(daggers().isEmpty()&&!GaleDaggers.active(owner),"Put-away left prepared daggers");require(GaleDaggers.remaining(owner)==0,"Cancellation started recovery");reset();require(GaleDaggers.cast(owner),"Affinity setup failed");owner.setAttached(EWAttachments.AFFINITY,"FIRE");}
        if(t==96){require(daggers().isEmpty(),"Affinity change left daggers");owner.setAttached(EWAttachments.AFFINITY,"WIND");reset();require(GaleDaggers.cast(owner),"Cover setup failed");owner.setYaw(0);}
        if(t==103){
            var w=owner.getEntityWorld();for(int x=-1;x<=1;x++)for(int y=100;y<=104;y++)w.setBlockState(new BlockPos(x,y,5),Blocks.STONE.getDefaultState());
            require(GaleDaggers.fire(owner),"Cover cast failed");
        }
        if(t==120){
            require(target.getHealth()==20,"Daggers penetrated cover");require(daggers().isEmpty(),"Cover left projectiles");
            var w=owner.getEntityWorld();for(int x=-1;x<=1;x++)for(int y=100;y<=104;y++)w.setBlockState(new BlockPos(x,y,5),Blocks.AIR.getDefaultState());
            reset();require(GaleDaggers.cast(owner),"Ally setup failed");
            var team=s.getScoreboard().addTeam("dagger_allies");s.getScoreboard().addScoreHolderToTeam(owner.getNameForScoreboard(),team);s.getScoreboard().addScoreHolderToTeam(target.getNameForScoreboard(),team);
        }
        if(t==127)require(GaleDaggers.fire(owner),"Ally fire failed");
        if(t==148){require(target.getHealth()==20,"Allied target damaged");reset();require(GaleDaggers.cast(owner),"Death setup failed");owner.setHealth(0);}
        if(t==150){require(daggers().isEmpty(),"Death left prepared daggers");owner.setHealth(20);reset();require(GaleDaggers.cast(owner),"Final setup failed");}
        if(t==157){GaleDaggers.fire(owner);firstLaunch=owner.getEntityWorld().getTime();}
        if(t==170){owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));require(!GaleDaggers.cast(owner),"Fresh wand reset cooldown");}
        if(t==396)require(!GaleDaggers.cast(owner),"Cooldown ended before 240 ticks");
        if(t==397){require(GaleDaggers.cast(owner),"Cooldown did not recover at 240 ticks");require(prepared()==3,"Recovered cast did not arm");GaleDaggers.cancel(owner);
            Files.writeString(Path.of("PRESSURE_PASSED.txt"),"Gale Daggers passed: purchase/equip, three prepared entities, deferred cooldown, primary replacement, held-input safety, 3-tick spacing, three 5-damage hits on one target, fresh per-shot aim, Wind XP, range/lifetime, item/affinity/death cleanup, solid cover, allies, fresh-wand cooldown protection and exact 240-tick recovery.\n");s.stop(false);}
    }
    private void reset(){GaleDaggers.cancel(owner);owner.setAttached(EWAttachments.GALE_LAST_LAUNCH,-1_000_000_000L);owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));owner.setYaw(0);owner.setPitch(0);target.setHealth(20);target.timeUntilRegen=0;}
    private List<GaleDaggerEntity> daggers(){return owner.getEntityWorld().getEntitiesByClass(GaleDaggerEntity.class,new Box(-70,90,-70,70,115,70),e->!e.isRemoved());}
    private long flying(){return daggers().stream().filter(GaleDaggerEntity::fired).count();}
    private long prepared(){return daggers().stream().filter(e->!e.fired()).count();}
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception{
        var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);
    }
    private static void require(boolean c,String why){if(!c)throw new AssertionError(why);}
}
