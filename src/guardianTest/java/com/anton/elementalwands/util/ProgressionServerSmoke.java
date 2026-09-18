package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ErrorReporter;
import net.minecraft.storage.*;
import java.util.*;
import java.nio.file.*;

/** Real server coverage for new persistence, credits, slots, combat accounting and mixins. */
public final class ProgressionServerSmoke implements ModInitializer {
    private int tick; private ServerPlayerEntity player,ally;
    private double regenStart;
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("PROGRESSION_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double a,double b,String message){check(Math.abs(a-b)<.001,message+": "+a+" != "+b);}
    private static ServerPlayerEntity player(MinecraftServer server,String name,double x)throws Exception{
        var m=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
        m.setAccessible(true);var p=(ServerPlayerEntity)m.invoke(null,server,UUID.randomUUID(),name,x,100.,.5);p.setNoGravity(true);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));return p;
    }
    private ZombieEntity zombie(){var z=new ZombieEntity(EntityType.ZOMBIE,player.getEntityWorld());z.setPosition(30,100,0);z.setAiDisabled(true);z.setNoGravity(true);z.getAttributeInstance(EntityAttributes.ARMOR).setBaseValue(0);return z;}
    private double xp(WizardAffinity a){return WandProgression.get(player,a).xp();}
    private void run(MinecraftServer server)throws Exception{
        if(++tick==30){
            player=player(server,"ProgressTester",.5);ally=player(server,"HealTester",8.5);
            player.setAttached(EWAttachments.AFFINITY,"FIRE");ally.setAttached(EWAttachments.AFFINITY,"NATURE");
            var starter=WandLoadouts.get(player);
            check(starter.equals(List.of("inferno_wave","","","","")),"Starter must have just one Basic");
            var old=com.mojang.serialization.JsonOps.INSTANCE;
            var decoded=ElementProgress.CODEC.parse(old,com.google.gson.JsonParser.parseString("{\"flux\":77,\"spells\":[\"meteor\"]}")).getOrThrow();
            check(decoded.flux()==77&&decoded.spells().contains("meteor")&&decoded.xp()==0,"Old codec migration lost progress");
            WandProgression.earn(player,WizardAffinity.FIRE,500);
            WandProgression.purchase(player,"FIRE","flamethrower");
            check(WandLoadouts.get(player).get(1).equals("flamethrower"),"Second Basic did not fill the next free slot");
            var book=new ItemStack(ModItems.SECONDARY_SPELL_BOOK);player.getInventory().setStack(10,book);
            check(SpellBooks.use(player,1,book)&&book.isEmpty(),"Book did not grant and consume once");
            check(!SpellBooks.use(player,1,book),"Empty book reused");
            WandProgression.purchase(player,"FIRE","meteor");check(!WandProgression.owns(player,WandSpells.find("meteor"))&&SpellBooks.credits(player).get(1)==1,"Tier bypass spent book");
            WandProgression.purchase(player,"FIRE","flashover");
            check(WandLoadouts.get(player).get(2).equals("flashover")&&SpellBooks.credits(player).get(1)==0&&WandProgression.flux(player)==0,"Free learning/slot/price failed");
            WandProgression.purchase(player,"FIRE","flashover");check(SpellBooks.credits(player).get(1)==0,"Duplicate purchase changed credits");
            check(SpellBooks.availableTier(List.of(1,0,1),WandSpells.Category.BASIC)==0,"Wasted higher credit");
            WandProgression.earn(player,WizardAffinity.FIRE,500);WandProgression.purchase(player,"FIRE","fire_hop");
            check(WandLoadouts.get(player).get(3).equals("fire_hop"),"Second Technique did not fill the next free slot");
            WandLoadouts.equip(player,"FIRE",3,"flamethrower");check(WandLoadouts.get(player).get(3).equals("flamethrower")&&WandLoadouts.get(player).get(1).equals("fire_hop"),"Free-slot swap failed");
            WandLoadouts.equip(player,"FIRE",1,"flamethrower");check(WandLoadouts.get(player).get(3).equals("fire_hop"),"Swap back lost spell");
            SpellBooks.use(player,2,new ItemStack(ModItems.ULTIMATE_SPELL_BOOK));
            WandProgression.purchase(player,"FIRE","meteor");check(WandLoadouts.get(player).get(4).equals("meteor"),"Ultimate book failed");
            // Credits and XP round-trip with actual Fabric player serialization.
            SpellBooks.use(player,0,new ItemStack(ModItems.BASIC_SPELL_BOOK));
            WandProgression.experience(player,WizardAffinity.FIRE,200);
            var save=NbtWriteView.create(ErrorReporter.EMPTY,player.getRegistryManager());player.writeData(save);
            player.setAttached(EWAttachments.ELEMENT_PROGRESS,Map.of());player.removeAttached(EWAttachments.SPELL_BOOKS);
            player.readData(NbtReadView.create(ErrorReporter.EMPTY,player.getRegistryManager(),save.getNbt()));
            near(xp(WizardAffinity.FIRE),200,"XP serialization");check(SpellBooks.credits(player).get(0)==1&&WandLoadouts.get(player).get(4).equals("meteor"),"Book/loadout serialization");
            player.setAttached(EWAttachments.AFFINITY,"STONE");near(xp(WizardAffinity.STONE),0,"Cross-element XP leak");
            check(WandLoadouts.get(player).get(0).equals("gathered_mass"),"Element loadout leak");player.setAttached(EWAttachments.AFFINITY,"FIRE");
            WandProgression.experience(player,WizardAffinity.FIRE,100000);near(xp(WizardAffinity.FIRE),3000,"XP cap");
            var z=zombie();float health=z.getHealth();SpellCombat.damage(z,player.getEntityWorld(),player.getDamageSources().playerAttack(player),6,player,WizardAffinity.FIRE);near(health-z.getHealth(),9,"Level-six damage");
            var kill=zombie();SpellCombat.damage(kill,player.getEntityWorld(),player.getDamageSources().playerAttack(player),999,player,WizardAffinity.WIND);near(xp(WizardAffinity.WIND),25,"Overkill and kill bonus");
            SpellCombat.damage(kill,player.getEntityWorld(),player.getDamageSources().playerAttack(player),999,player,WizardAffinity.WIND);near(xp(WizardAffinity.WIND),25,"Dead target credited twice");
            var armored=zombie();armored.getAttributeInstance(EntityAttributes.ARMOR).setBaseValue(20);float beforeArmor=armored.getHealth();double beforeXp=xp(WizardAffinity.WIND);
            SpellCombat.damage(armored,player.getEntityWorld(),player.getDamageSources().playerAttack(player),4,player,WizardAffinity.WIND);near(xp(WizardAffinity.WIND)-beforeXp,beforeArmor-armored.getHealth(),"XP ignored armor");
            var pig=new PigEntity(EntityType.PIG,player.getEntityWorld());float hp=pig.getHealth();SpellCombat.damage(pig,player.getEntityWorld(),player.getDamageSources().playerAttack(player),2,player,WizardAffinity.STONE);near(xp(WizardAffinity.STONE),(hp-pig.getHealth())*.1,"Passive reduction");
            var fraction=zombie();for(int i=0;i<5;i++){fraction.timeUntilRegen=0;SpellCombat.damage(fraction,player.getEntityWorld(),player.getDamageSources().playerAttack(player),.1f,player,WizardAffinity.SPACE);}near(xp(WizardAffinity.SPACE),.5,"Fractional hit XP");
            player.setHealth(10);SpellCombat.heal(player,2,player,WizardAffinity.NATURE);near(xp(WizardAffinity.NATURE),.5,"Self heal rate");
            ally.setHealth(10);SpellCombat.heal(ally,2,player,WizardAffinity.NATURE);near(xp(WizardAffinity.NATURE),2.5,"Ally heal rate");
            ally.setHealth(20);SpellCombat.heal(ally,5,player,WizardAffinity.NATURE);near(xp(WizardAffinity.NATURE),2.5,"Overheal XP");
            // Personal inventory receipt, not a shared book anyone can take.
            check(SpellBooks.claim(player,"test-site")&&!SpellBooks.claim(player,"test-site"),"Personal reward duplicated");
            check(SpellBooks.claim(ally,"test-site"),"Second participant got no reward");
            var regen=player(server,"RegenTester",12.5);regen.setAttached(EWAttachments.AFFINITY,"NATURE");regen.setHealth(10);
            // Keep caster alive and target stationary for actual regeneration callback.
            ally.setHealth(10);SpellBuffs.regeneration(ally,player,WizardAffinity.NATURE,1);regenStart=xp(WizardAffinity.NATURE);
            // Actual explosion redirect and fire mixin must load; target no-XP case doesn't alter current source.
            var burning=zombie();burning.timeUntilRegen=0;SpellCombat.ignite(burning,player,5);
            health=burning.getHealth();burning.damage(player.getEntityWorld(),player.getDamageSources().onFire(),1);near(health-burning.getHealth(),1.5,"Burn multiplier");
            player.setHealth(20);
        }
        if(tick>30&&tick<100)ally.playerTick();
        if(tick==100){
            check(xp(WizardAffinity.NATURE)>regenStart,"Actual regeneration ticks did not award XP");
            var owned=WandProgression.owned(player);var xp=xp(WizardAffinity.FIRE);var credits=SpellBooks.credits(player);
            player.setHealth(0);player=server.getPlayerManager().respawnPlayer(player,false,Entity.RemovalReason.KILLED);
            check(WandProgression.owned(player).equals(owned)&&xp(WizardAffinity.FIRE)==xp&&SpellBooks.credits(player).equals(credits),"Respawn lost progression");
            Files.writeString(Path.of("PROGRESSION_PASSED.txt"),"PASS: legacy codec, five slots, free credits and tiers, duplicate validation, serialization, death persistence, source separation, six-level cap, actual damage/armor/overkill/fractional XP, passive rate, actual healing/regeneration and burn mixins, personal reward claims.\n");server.stop(false);
        }
    }
}
