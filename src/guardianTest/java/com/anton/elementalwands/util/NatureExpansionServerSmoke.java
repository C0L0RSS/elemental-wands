package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.ThornLashEntity;
import com.anton.elementalwands.item.NatureAbilityHandler;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Real combat and temporary-world lifecycle checks. No production world is touched. */
public final class NatureExpansionServerSmoke implements ModInitializer {
    private int ticks;
    private ServerPlayerEntity p, enemy, ally;
    private BlockPos knot;
    private final List<ZombieEntity> mobs = new ArrayList<>();
    private float mobBefore, enemyBefore, allyBefore;
    private BlockPos flower;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("HUB_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        int t = ++ticks - 80;
        if(ticks==30) {
            p=player(server,"NatureCaster",.5,100,.5);enemy=player(server,"NatureEnemy",.5,100,3.5);ally=player(server,"NatureAlly",1.2,100,3);
            var w=p.getEntityWorld();
            for(int x=-20;x<=20;x++)for(int z=-15;z<=25;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            for(var q:List.of(p,enemy,ally)) {q.setLoaded(true);q.onTeleportationDone();q.getHungerManager().setFoodLevel(10);q.setNoGravity(true);q.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);q.setHealth(200);}
            p.setHealth(10);p.setYaw(0);p.setPitch(0);p.setAttached(EWAttachments.AFFINITY,"NATURE");freshWand();
            var team=server.getScoreboard().addTeam("nature_allies");team.setFriendlyFireAllowed(true);
            server.getScoreboard().addScoreHolderToTeam(p.getName().getString(),team);server.getScoreboard().addScoreHolderToTeam(ally.getName().getString(),team);
            p.setAttached(EWAttachments.WAND_LOADOUTS,Map.of("NATURE",List.of("", "", "", "", "thorn_lash")));
            WandLoadouts.cast(p,4);require(lashes()==0,"Unowned bite cast");
            WandProgression.earn(p,WizardAffinity.NATURE,1000);
            WandProgression.purchase(p,"NATURE","thorn_lash");WandProgression.purchase(p,"NATURE","tendril_bloom");
            require(WandProgression.flux(p)==0 && WandProgression.owns(p,WandSpells.find("thorn_lash")),"Purchase incorrect");
            WandLoadouts.equip(p,"NATURE",0,"thorn_lash");
            WandLoadouts.equip(p,"NATURE",1,"tendril_bloom");
        }
        if(t==30) {p.setHealth(10);WandLoadouts.cast(p,0);WandLoadouts.cast(p,0);}
        if(t==31) {p.setYaw(90);p.setPitch(-30);}
        if(t==34) require(lashes()==1,"Cooldown bite count="+lashes()+" nbt="+p.getMainHandStack().get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA));
        if(t==42) {
            near(enemy.getHealth(),200-ThornLashRules.DAMAGE,"Committed forward bite damage");near(p.getHealth(),10+Math.min(ThornLashRules.DAMAGE*ThornLashRules.LIFESTEAL,ThornLashRules.HEAL_CAP),"Lifesteal ratio");near(ally.getHealth(),200,"Lash hit ally");
            require(FlowerTurretManager.marked((net.minecraft.server.world.ServerWorld)p.getEntityWorld(),p.getUuid())==enemy,"A landed bite must mark its target for the caster's flowers");
            require(EntangleTracker.getStacks(enemy)==0 && !enemy.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"The bite wound a vine or slowed");
            p.setYaw(0);p.setPitch(0);enemy.setPosition(.5,100,-3);freshWand();p.setHealth(10);WandLoadouts.cast(p,0);
        }
        if(t==54) {near(p.getHealth(),10,"Rear miss healed");near(enemy.getHealth(),200-ThornLashRules.DAMAGE,"Lash hit behind caster");
            enemy.setPosition(.5,100,3.5);enemy.setInvulnerable(true);freshWand();WandLoadouts.cast(p,0);}
        if(t==66) {near(p.getHealth(),10,"Invulnerable hit healed");enemy.setInvulnerable(false);
            for(int x=-2;x<=2;x++)for(int y=100;y<=103;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,2),Blocks.STONE.getDefaultState());
            freshWand();WandLoadouts.cast(p,0);}
        if(t==78) {
            near(enemy.getHealth(),200-ThornLashRules.DAMAGE,"Lash penetrated cover");near(p.getHealth(),10,"Covered hit healed");
            for(int x=-2;x<=2;x++)for(int y=100;y<=103;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,2),Blocks.AIR.getDefaultState());
            enemy.setHealth(1);freshWand();WandLoadouts.cast(p,0);
        }
        if(t==90) {
            near(p.getHealth(),10+ThornLashRules.LIFESTEAL,"Overkill gave extra healing");
            mobs.add(zombie(-1,100,3));mobs.add(zombie(.5,100,3.5));mobs.add(zombie(.5,100,4.4));mobs.add(zombie(2,100,3));
            p.setHealth(10);freshWand();WandLoadouts.cast(p,0);
        }
        if(t==102) {
            near(p.getHealth(),10+Math.min((200-mobs.get(1).getHealth())*ThornLashRules.LIFESTEAL,ThornLashRules.HEAL_CAP),"Single-target actual-damage healing");
            require(mobs.get(1).getHealth()<200,"Bite missed aimed target");
            for(int i:new int[]{0,2,3})near(mobs.get(i).getHealth(),200,"Bite swept sideways or pierced a target");
            mobs.forEach(Entity::discard);mobs.clear();
            mobs.add(zombie(-5,100,7));mobs.add(zombie(.5,100,10));mobs.add(zombie(8,100,7));
        }
        if(t==215) {
            // A knot left in a world by an older version has no owner and must clear itself.
            knot=new BlockPos(3,100,-3);
            p.getEntityWorld().setBlockState(knot,ModSpellBlocks.NATURE_ROOT_KNOT.getDefaultState());
        }
        if(t==240) {
            require(p.getEntityWorld().getBlockState(knot).isAir(),"Retired root knot did not remove itself");
        }
        if(t==245) {
            // Sentry Bloom through the real slot cast: a bud is thrown, and a second press inside the cooldown throws nothing.
            var w=(net.minecraft.server.world.ServerWorld)p.getEntityWorld();
            p.setYaw(0);p.setPitch(45);freshWand();WandLoadouts.cast(p,1);
            require(buds()==1 && FlowerTurretManager.count(w,p.getUuid())==0,"The slot cast did not throw one bud: "+buds());
            WandLoadouts.cast(p,1);
            require(buds()==1,"Sentry Bloom ignored its cooldown");
            enemyBefore=enemy.getHealth();allyBefore=ally.getHealth();
        }
        if(t==251) {
            // The bud lands two blocks ahead and the flower takes root there.
            var w=(net.minecraft.server.world.ServerWorld)p.getEntityWorld();
            require(buds()==0 && FlowerTurretManager.count(w,p.getUuid())==1,"The bud planted no flower");
            flower=FlowerTurretManager.positions(w,p.getUuid()).getFirst();
            require(flower.equals(new BlockPos(0,100,2)) && w.getBlockState(flower).isOf(ModSpellBlocks.NATURE_SEEDLING),"Flower not planted where the bud landed: "+flower);
        }
        if(t==275) {
            // Left to itself it shoots the nearest monster, and never a player.
            require(mobs.get(0).getHealth()<200,"The flower did not shoot the nearest monster");
            near(mobs.get(1).getHealth(),200,"The flower shot a farther monster");near(mobs.get(2).getHealth(),200,"The flower shot a farther monster");
            near(enemy.getHealth(),enemyBefore,"The flower shot an unmarked player");near(ally.getHealth(),allyBefore,"The flower shot an ally");
            FlowerTurretManager.mark(p,mobs.get(2));mobBefore=mobs.get(0).getHealth();
        }
        if(t==320) {
            require(mobs.get(2).getHealth()<200,"The flower ignored its owner's mark");
            near(mobs.get(0).getHealth(),mobBefore,"The flower kept its old target after a mark");
            // A mark on an ally is not an order: the flower goes back to the nearest monster.
            FlowerTurretManager.mark(p,ally);
        }
        if(t==360) {
            near(ally.getHealth(),allyBefore,"The flower shot a marked ally");
            require(mobs.get(0).getHealth()<mobBefore,"A protected mark left the flower idle");
            // Its hits healed nobody; the flower kept a share. The owner's slot-2 press, aimed at it
            // from close up, collects that through the real cast path and uses the flower up.
            var w=(net.minecraft.server.world.ServerWorld)p.getEntityWorld();
            float held=FlowerTurretManager.banked(w,flower);
            require(held>0 && held<=FlowerTurretManager.BANK_CAP,"The flower banked nothing from its hits: "+held);
            p.setHealth(10);p.setYaw(0);p.setPitch(36);freshWand();WandLoadouts.cast(p,1);
            near(p.getHealth(),10+held,"Collected flower health");
            require(buds()==0 && FlowerTurretManager.count(w,p.getUuid())==0 && w.getBlockState(flower).isAir(),"Collecting threw a bud or left the flower");
            // Another flower, for the slot-1 press that breaks it.
            require(FlowerTurretManager.plant(w,p,flower),"Could not replant after collecting");
        }
        if(t==362) {
            var w=(net.minecraft.server.world.ServerWorld)p.getEntityWorld();
            // A held repeat is not a click; a fresh slot-1 press on the flower breaks it and casts nothing.
            p.setHealth(10);freshWand();WandLoadouts.cast(p,0,false);
            require(FlowerTurretManager.count(w,p.getUuid())==1,"A held repeat broke a flower");
        }
        if(t==375) {
            var w=(net.minecraft.server.world.ServerWorld)p.getEntityWorld();
            freshWand();WandLoadouts.cast(p,0);
            require(FlowerTurretManager.count(w,p.getUuid())==0 && w.getBlockState(flower).isAir() && lashes()==0,"A close slot-1 press did not break the flower");
            // Whatever removes the block removes the flower.
            require(FlowerTurretManager.plant(w,p,flower),"Could not replant after breaking");
        }
        if(t==380)p.getEntityWorld().setBlockState(flower,Blocks.AIR.getDefaultState());
        if(t==382) {
            require(FlowerTurretManager.count((net.minecraft.server.world.ServerWorld)p.getEntityWorld(),p.getUuid())==0,"A flower outlived its block");
        }
        if(t==515) {
            mobs.forEach(Entity::discard);mobs.clear();p.setPosition(.5,100,.5);p.setYaw(0);p.setPitch(0);
            mobs.add(zombie(.5,100,2.5));mobs.add(zombie(.5,100,4));mobs.getFirst().setInvulnerable(true);
            p.setHealth(10);freshWand();WandLoadouts.cast(p,0);
        }
        if(t==527) {
            near(mobs.get(1).getHealth(),200,"Bite pierced invulnerable first target");near(p.getHealth(),10,"Blocked bite healed");
            mobs.forEach(Entity::discard);mobs.clear();mobs.add(zombie(.5,100,5.8));freshWand();WandLoadouts.cast(p,0);
        }
        if(t==539) {
            near(mobs.getFirst().getHealth(),200,"Bite exceeded range");
            freshWand();WandLoadouts.cast(p,0);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        }
        if(t==542) {
            require(lashes()==0,"Unequipping retained bite");
            Files.writeString(Path.of("HUB_PASSED.txt"),"Nature expansion passed: ownership, cooldown, committed single-target bite, allies, cover, miss/invulnerability/overkill healing, a landed bite marks its target and winds no vine, nearest-only contact, turning after cast, invulnerable obstruction, max range, unequip cleanup; retired knot self-removal; Sentry Bloom through the slot cast as a thrown bud that plants where it lands, with its cooldown, nearest-monster fire, players never shot unprompted, the owner's mark obeyed, a marked ally spared, banked health collected by a close slot-2 press that uses the flower up, a flower broken by a fresh close slot-1 press and not by a held repeat, and a flower ending with its block.\n");
            server.stop(false);
        }
    }
    private void freshWand() {p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));}
    private int buds() {return p.getEntityWorld().getEntitiesByClass(com.anton.elementalwands.entity.SentryBloomPodEntity.class,new Box(-10,90,-10,10,110,10),e->!e.isRemoved()).size();}
    private int lashes() {return p.getEntityWorld().getEntitiesByClass(ThornLashEntity.class,new Box(-10,90,-10,10,110,10),e->!e.isRemoved()).size();}
    private ZombieEntity zombie(double x,double y,double z) {
        var m=new ZombieEntity(EntityType.ZOMBIE,p.getEntityWorld());m.setAiDisabled(true);m.setNoGravity(true);
        m.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);m.setHealth(200);m.setInvulnerable(false);
        m.refreshPositionAndAngles(x,y,z,0,0);m.equipStack(EquipmentSlot.HEAD,new ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
        p.getEntityWorld().spawnEntity(m);return m;
    }
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception {
        var f=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
        f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);
    }
    private static void near(float value,float expected,String why) {require(Math.abs(value-expected)<.01,why+": "+value+" expected "+expected);}
    private static void require(boolean condition,String why) {if(!condition)throw new AssertionError(why);}
}
