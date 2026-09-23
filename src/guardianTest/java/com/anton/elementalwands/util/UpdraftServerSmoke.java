package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.WindAbilityHandler;
import com.anton.elementalwands.registry.ModItems;
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

public final class UpdraftServerSmoke implements ModInitializer {
    private int tick;
    private ServerPlayerEntity p;
    public void onInitialize() { ServerTickEvents.END_SERVER_TICK.register(s -> {
        try { run(s); } catch(Throwable e) { e.printStackTrace(); try { Files.writeString(Path.of("PRESSURE_FAILED.txt"),e.toString()); } catch(Exception ignored) {} s.stop(false); }
    }); }
    private void run(MinecraftServer s) throws Exception {
        int t=++tick;
        if(t==25) {
            var w=s.getOverworld();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){w.getChunk(x,z);w.setChunkForced(x,z,true);}
            for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            f.setAccessible(true);p=(ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),"UpdraftCaster",.5,100,.5);
            p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.setHealth(20);p.getHungerManager().setFoodLevel(10);
            p.setAttached(EWAttachments.AFFINITY,"WIND");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!UpdraftManager.cast(p),"Unowned cast accepted");
            WandProgression.earn(p,WizardAffinity.WIND,2000);
            for(String id:List.of("updraft","gale_daggers","waylay_dash"))WandProgression.purchase(p,"WIND",id);
            WandLoadouts.equip(p,"WIND",4,"updraft");WandLoadouts.equip(p,"WIND",3,"gale_daggers");WandLoadouts.equip(p,"WIND",1,"waylay_dash");
            require(WandSpells.forAffinity(WizardAffinity.WIND).size()>=5,"Missing fifth spell");
            require(WandLoadouts.get(p).get(4).equals("updraft"),"Equip failed");
        }
        if(p==null)return;
        if(t==26) {
            p.setVelocity(.28,-2,-.1);p.setOnGround(false);p.fallDistance=14;
            WandLoadouts.cast(p,4,false);require(!UpdraftManager.protectedFall(p),"Held repeat launched");
            WandLoadouts.cast(p,4);require(UpdraftManager.protectedFall(p),"Slot cast failed");
            require(p.getVelocity().equals(new Vec3d(.28,1.343,-.1)),"Horizontal momentum changed or falling cast failed");
            require(p.fallDistance==0 && UpdraftManager.remaining(p)==160,"Recovery or fall reset incorrect");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));require(!UpdraftManager.cast(p),"Fresh wand reset cooldown");
            require(UpdraftManager.protectedFall(p),"Wand swap removed safe landing");
            p.damage(p.getEntityWorld(),p.getDamageSources().magic(),2);require(p.getHealth()==18,"Updraft granted combat immunity");p.setHealth(20);
            double top=100;Vec3d v=p.getVelocity();p.setPosition(.5,100,.5);
            for(int i=0;i<70;i++) {
                p.setVelocity(v);p.move(MovementType.SELF,v);top=Math.max(top,p.getY());if(p.isOnGround())break;
                v=new Vec3d(v.x*.91,(v.y-.08)*.98,v.z*.91);
            }
            require(top>109.8 && top<110.2,"Wrong apex: "+top);
            p.handleFallDamage(20,1,p.getDamageSources().fall());require(p.getHealth()==20,"Protected landing damaged caster");
            require(!UpdraftManager.protectedFall(p),"Protection survived landing");
            p.timeUntilRegen=0;p.handleFallDamage(6,1,p.getDamageSources().fall());require(p.getHealth()<20,"Later fall protected");
        }
        if(t==185)require(!UpdraftManager.cast(p),"Recovery ended before 160 ticks");
        if(t==186) {
            require(UpdraftManager.cast(p),"Recovery failed at 160 ticks");
            p.setPosition(.5,100,.5);p.setVelocity(0,1.343,0);
            p.getEntityWorld().setBlockState(new BlockPos(0,102,0),Blocks.STONE.getDefaultState());
            p.move(MovementType.SELF,p.getVelocity());require(p.getY()<100.21,"Updraft passed through ceiling");
            p.getEntityWorld().setBlockState(new BlockPos(0,102,0),Blocks.AIR.getDefaultState());
        }
        if(t==190){reset();require(GaleDaggers.cast(p),"Dagger prepare failed");}
        if(t==197) {
            require(UpdraftManager.cast(p),"Prepared daggers blocked Updraft");require(GaleDaggers.active(p),"Updraft canceled daggers");
            p.setYaw(0);p.setHeadYaw(0);p.setPitch(0);p.setVelocity(0,1.343,0);
            WindAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            require(Math.abs(p.getVelocity().z-1.5)<1E-5 && Math.abs(p.getVelocity().y-1.343)<1E-5,"Dash base not 75% or erased lift: "+p.getVelocity());
            WindAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            require(Math.abs(p.getVelocity().z-3.375)<1E-5,"Chain bonus not scaled to 75%: "+p.getVelocity());
            require(WindAbilityHandler.getDashCharges(p.getMainHandStack())==0,"Charge behavior changed");
        }
        if(t==204){require(GaleDaggers.fire(p),"Could not fire after Updraft/dash");p.setAttached(EWAttachments.AFFINITY,"FIRE");require(!UpdraftManager.protectedFall(p),"Affinity exit retained protection");}
        if(t==208){reset();require(UpdraftManager.cast(p),"Death setup failed");p.setHealth(0);require(!UpdraftManager.protectedFall(p),"Death retained protection");}
        if(t==211){reset();require(UpdraftManager.cast(p),"Ground cleanup setup failed");p.setOnGround(true);}
        if(t==218){require(!UpdraftManager.protectedFall(p),"Ground contact retained protection");reset();require(UpdraftManager.cast(p),"World exit setup failed");
            p.teleport(s.getWorld(net.minecraft.world.World.NETHER),.5,110,.5,Set.of(),0,0,true);require(!UpdraftManager.protectedFall(p),"World exit retained protection");}
        if(t==224){
            Files.writeString(Path.of("PRESSURE_PASSED.txt"),"Updraft server passed: purchase/fifth slot, deliberate input, midair launch and preserved X/Z, ~10-block arc, cooldown starts immediately and lasts exactly 160 ticks, fresh-wand protection, combat damage, safe first landing then ordinary fall damage, ceiling collision, dagger/dash combo, 75% base and chain impulses, ground/death/affinity/world cleanup. Native client physics checked separately.\n");s.stop(false);
        }
    }
    private void reset(){UpdraftManager.finish(p);GaleDaggers.cancel(p);p.setHealth(20);p.setAttached(EWAttachments.AFFINITY,"WIND");p.setAttached(EWAttachments.UPDRAFT_LAST_CAST,-1_000_000_000L);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));p.setPosition(.5,100,.5);p.setVelocity(Vec3d.ZERO);p.setOnGround(false);}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
