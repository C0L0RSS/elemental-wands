package com.anton.elementalwands.util;

import java.nio.file.*;
import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Disposable real-world checks for channel lifecycle, targeting and directional movement. */
public final class FireBuildServerSmoke implements ModInitializer {
    private int ticks; private ServerPlayerEntity player; private CowEntity near,covered,far,hopVictim;
    private net.minecraft.entity.passive.PigEntity pig;
    private float heldHeat,healthAtRelease; private Vec3d launch;
    public void onInitialize() { ServerTickEvents.END_SERVER_TICK.register(server -> {
        try { run(server); } catch (Throwable t) { t.printStackTrace();try { Files.writeString(Path.of("HUB_FAILED.txt"),t.toString()); }catch(Exception ignored){}server.stop(false); }
    }); }
    private void run(MinecraftServer server) throws Exception {
        int tick=++ticks;
        if (tick==30) {
            var factory=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            factory.setAccessible(true);player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"FireBuildTester",0.5,100.0,0.5);
            player.setNoGravity(true);player.setYaw(0);player.setPitch(10);player.setAttached(EWAttachments.AFFINITY,"FIRE");
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!WandProgression.owns(player,WandSpells.find("flamethrower")),"New paid Basic was granted by legacy migration");
            WandProgression.earn(player,WizardAffinity.FIRE,1000);
            WandProgression.purchase(player,"FIRE","flamethrower");WandProgression.purchase(player,"FIRE","fire_hop");
            require(WandProgression.flux(player)==0,"New spells did not spend exactly 1000 Fire Flux");
            WandLoadouts.equip(player,"FIRE",0,"flamethrower");WandLoadouts.equip(player,"FIRE",1,"fire_hop");
            require(WandLoadouts.get(player).equals(List.of("flamethrower","fire_hop","meteor")),"New spell IDs did not equip independently");
            pig=EntityType.PIG.create(player.getEntityWorld(),SpawnReason.COMMAND);
            pig.setPosition(0.5,100,3);pig.setNoGravity(true);pig.setAiDisabled(true);player.getEntityWorld().spawnEntity(pig);
            near=cow(0.5,100,2);covered=cow(0.5,100,5.5);far=cow(0.5,100,8);
            for (int x=-3;x<=3;x++) for(int y=100;y<=104;y++) player.getEntityWorld().setBlockState(new BlockPos(x,y,4),Blocks.STONE.getDefaultState());

        }
        if (tick>=31 && tick<=60) WandLoadouts.cast(player,0);
        if (tick==52) require(pig.isAlive() && pig.isOnFire(),"Early pig health="+pig.getHealth()+" fire="+pig.isOnFire()+" pos="+pig.getEntityPos());
        if (tick==61) {
            require(near.isOnFire(),"Flamethrower did not ignite a touched target");
            require(FireBuildRules.flameDamage(0)==0.5f && FireBuildRules.flameDamage(60)==2.5f
                    && FireBuildRules.flameDamage(200)==2.5f,"Damage ramp endpoints/cap are wrong");
            require(FireBuildManager.heat(player)>30 && near.getHealth()<200,"Held flame did not heat or damage nearby target");
            require(covered.getHealth()==200 && far.getHealth()==200,"Flame hit behind cover or beyond range");
            FireBuildManager.stop(player);heldHeat=FireBuildManager.heat(player);healthAtRelease=near.getHealth();
        }
        if (tick==66) {
            require(FireBuildManager.heat(player)<heldHeat && !FireBuildManager.spraying(player) && near.getHealth()>=healthAtRelease-2,"Release failed to stop damage and cool");
            WandLoadouts.cast(player,0);
        }
        if (tick==75) require(!FireBuildManager.spraying(player),"Held-input lease did not expire");
        if (tick==76) { WandLoadouts.cast(player,0); player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY); }
        if (tick==78) {
            require(!FireBuildManager.spraying(player) && FireBuildManager.heat(player)>20,"Putting away the wand did not stop spray or reset heat");
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
        }
        if (tick>=80 && tick<=160) WandLoadouts.cast(player,0);
        if (tick==150) require(FireBuildManager.overheated(player),"Continuous fire never overheated");
        if (tick==161) {
            require(FireBuildManager.overheated(player) && !FireBuildManager.spraying(player),"Overheat allowed spraying");
            var state=FireBuildManager.state(player);
            var save=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,player.getRegistryManager());player.writeData(save);
            player.setAttached(EWAttachments.FIRE_BUILD_STATE,new net.minecraft.nbt.NbtCompound());
            player.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,player.getRegistryManager(),save.getNbt()));
            require(FireBuildManager.state(player).equals(state),"Heat state lost on save/reload");
            require(WandLoadouts.get(player).getFirst().equals("flamethrower"),"New build lost on reload");
        }
        if (tick==280) {
            require(!FireBuildManager.overheated(player),"Overheat did not recover");
            player.setPosition(20,100,0);player.setOnGround(false);
            for(int x=13;x<=27;x++)for(int z=-6;z<=18;z++)player.getEntityWorld().setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            hopVictim=cow(20,100,10);
            require(!FireLeapManager.commit(player,new Vec3d(Double.NaN,100,10)),"NaN destination accepted");
            require(!FireLeapManager.commit(player,new Vec3d(20,100,61)),"Out of range accepted");
            require(FireBuildManager.hopRemaining(player)==0,"Invalid aim spent cooldown");
            require(FireLeapManager.commit(player,new Vec3d(20,100,10)),"Valid leap rejected");
            require(player.hasVehicle() && FireBuildManager.hopRemaining(player)>0,"Leap did not start flight/cooldown");
            require(hopVictim.getHealth()==200,"Leap damaged on takeoff");
            require(!FireLeapManager.commit(player,new Vec3d(20,100,10)),"Repeated cast bypassed cooldown");
            player.stopRiding();require(player.hasVehicle(),"Sneaking escaped the locked arc");
        }
        if(tick==290) require(player.hasVehicle() && player.getY()>104,"Flight missed apex");
        if(tick==312) {
            require(!player.hasVehicle() && player.getEntityPos().distanceTo(new Vec3d(20,100,10))<.1,"Leap missed landing");
            require(hopVictim.getHealth()==192,"Center did not take exactly one hit");
            var old=player;player=server.getPlayerManager().respawnPlayer(player,false,net.minecraft.entity.Entity.RemovalReason.KILLED);
            require(FireBuildManager.hopRemaining(player)>0,"Respawn reset leap cooldown");
            require(WandProgression.owns(player,WandSpells.find("fire_hop")),"Respawn lost purchase");
            player.setNoGravity(true);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
        }
        if(tick==420) FireLeapServerCases.run(player);
        if(tick==430) {
            player.setPosition(20,100,0);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(FireLeapManager.commit(player,new Vec3d(20,100,10)),"Second leap did not start");
        }
        if(tick==435) {
            var obstacle=FireLeapRules.position(new Vec3d(20,100,0),new Vec3d(20,100,10),.4);
            player.getEntityWorld().setBlockState(BlockPos.ofFloored(obstacle),Blocks.STONE.getDefaultState());
        }
        if(tick==441) {
            require(!player.hasVehicle(),"New obstruction did not abort flight");
            require(FireBuildManager.hopRemaining(player)>0,"Aborted flight refunded cooldown");
        }
        if(tick==460) {
            require(FireBuildManager.hopRemaining(player)>0,"Committed abort lost its cooldown");
            require(hopVictim.getHealth()==192,"Leap damaged twice");
            for(int x=-3;x<=3;x++)for(int z=0;z<=6;z++)for(int y=99;y<=102;y++)
                require(!player.getEntityWorld().getBlockState(new BlockPos(x,y,z)).isOf(Blocks.FIRE),"Flamethrower placed ground fire");
            Files.writeString(Path.of("HUB_PASSED.txt"),"Fire build passed: paid spell ownership/equip, cone damage and cover/range, held heat, release, heartbeat timeout, overheat/recovery, save/reload, committed leap arc, invalid destination rejection, no takeoff damage, single landing hit, cooldown and respawn persistence, full-body ceiling/water clearance, wave jump/cover/allies, midflight obstruction, orphan reload cleanup.\n");server.stop(false);
        }
    }
    private CowEntity cow(double x,double y,double z) {
        var world=player.getEntityWorld();var cow=EntityType.COW.create(world,SpawnReason.COMMAND);
        cow.setPosition(x,y,z);cow.setNoGravity(true);cow.setAiDisabled(true);cow.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);cow.setHealth(200);world.spawnEntity(cow);return cow;
    }
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
}
