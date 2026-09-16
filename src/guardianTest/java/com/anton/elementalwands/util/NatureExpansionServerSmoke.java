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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Real combat and temporary-world lifecycle checks. No production world is touched. */
public final class NatureExpansionServerSmoke implements ModInitializer {
    private int ticks;
    private ServerPlayerEntity p, enemy, ally;
    private BlockPos knot;
    private final List<ZombieEntity> mobs = new ArrayList<>();
    private float before;
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
            p.setAttached(EWAttachments.WAND_LOADOUTS,Map.of("NATURE",List.of("thorn_lash","tendril_bloom","overgrowth")));
            WandLoadouts.cast(p,0);require(lashes()==0,"Unowned Lash cast");
            WandProgression.earn(p,WizardAffinity.NATURE,1000);
            WandProgression.purchase(p,"NATURE","thorn_lash");WandProgression.purchase(p,"NATURE","tendril_bloom");
            require(WandProgression.flux(p)==0 && WandProgression.owns(p,WandSpells.find("thorn_lash")),"Purchase incorrect");
        }
        if(t==30) {p.setHealth(10);WandLoadouts.cast(p,0);WandLoadouts.cast(p,0);}
        if(t==34) require(lashes()==1,"Cooldown sweep count="+lashes()+" nbt="+p.getMainHandStack().get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA));
        if(t==42) {
            near(enemy.getHealth(),194,"Single sweep damage");near(p.getHealth(),11.5f,"Lifesteal ratio");near(ally.getHealth(),200,"Lash hit ally");
            require(SeedlingManager.getActiveSeedlingsForCaster(p.getEntityWorld(),p.getUuid()).isEmpty(),"Lash planted seed");
            enemy.setPosition(.5,100,-3);freshWand();p.setHealth(10);WandLoadouts.cast(p,0);
        }
        if(t==54) {near(p.getHealth(),10,"Rear miss healed");near(enemy.getHealth(),194,"Lash hit behind caster");
            enemy.setPosition(.5,100,3.5);enemy.setInvulnerable(true);freshWand();WandLoadouts.cast(p,0);}
        if(t==66) {near(p.getHealth(),10,"Invulnerable hit healed");enemy.setInvulnerable(false);
            for(int x=-2;x<=2;x++)for(int y=100;y<=103;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,2),Blocks.STONE.getDefaultState());
            freshWand();WandLoadouts.cast(p,0);}
        if(t==78) {
            near(enemy.getHealth(),194,"Lash penetrated cover");near(p.getHealth(),10,"Covered hit healed");
            for(int x=-2;x<=2;x++)for(int y=100;y<=103;y++)p.getEntityWorld().setBlockState(new BlockPos(x,y,2),Blocks.AIR.getDefaultState());
            enemy.setHealth(1);freshWand();WandLoadouts.cast(p,0);
        }
        if(t==90) {
            near(p.getHealth(),10.25f,"Overkill gave extra healing");
            mobs.add(zombie(-1,100,3));mobs.add(zombie(.5,100,3.5));mobs.add(zombie(2,100,3));
            p.setHealth(10);freshWand();WandLoadouts.cast(p,0);
        }
        if(t==102) {
            near(p.getHealth(),12,"Group healing cap");
            for(var mob:mobs)require(mob.getHealth()<200,"Sweep missed front target");
            mobs.forEach(Entity::discard);mobs.clear();
            mobs.add(zombie(-5,100,7));mobs.add(zombie(.5,100,10));mobs.add(zombie(6,100,7));
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            knot=p.getBlockPos();require(TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"No knot at casting position");
            require(TendrilBloomManager.activeTendrils(p.getEntityWorld(),p.getUuid())==3,"Knot did not launch three tendrils");
            p.setPosition(-8,100,0);p.damage(p.getEntityWorld(),p.getDamageSources().generic(),1);
        }
        if(t==132) {
            require(TendrilBloomManager.activeBlooms(p.getEntityWorld(),p.getUuid())==3,"Expected three completed blooms");
            require(TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Movement/damage interrupted knot");
            require(mobs.stream().allMatch(m->m.getHealth()<200),"Did not acquire three distinct enemies");
            p.getEntityWorld().breakBlock(knot,false);before=mobs.getFirst().getHealth();
        }
        if(t==138) {
            require(TendrilBloomManager.activeBlooms(p.getEntityWorld(),p.getUuid())==0,"Broken knot retained blooms");
            require(TendrilBloomManager.activeTendrils(p.getEntityWorld(),p.getUuid())==0,"Broken knot retained tendrils");
            near(mobs.getFirst().getHealth(),before,"Destroyed knot continued damage");
            p.setPosition(.5,100,.5);freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            p.getEntityWorld().breakBlock(knot,false);
        }
        if(t==143) {
            require(TendrilBloomManager.activeTendrils(p.getEntityWorld(),p.getUuid())==0,"Early break retained traveling tendrils");
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            TendrilBloomManager.crushGrowth(p.getEntityWorld(),pos->pos.equals(knot));
            require(!TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Guardian contact did not destroy knot");
        }
        if(t==148) {
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            ally.setPosition(.5,100,-2);
            aim(ally,Vec3d.ofCenter(knot));
            require(!TendrilBloomManager.tryBreakKnotAimed(ally),"Allied Basic destroyed knot");
            var breaker=player(server,"RootBreaker",.5,100,-2);
            breaker.setNoGravity(true);aim(breaker,Vec3d.ofCenter(knot));
            require(TendrilBloomManager.tryBreakKnotAimed(breaker),"Enemy Basic could not destroy knot");
            require(!TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Disarmed knot retained source");
        }
        if(t==150) {
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            p.getEntityWorld().setBlockState(knot.down(),Blocks.AIR.getDefaultState());
        }
        if(t==155) {
            require(!TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Support loss retained knot");
            p.getEntityWorld().setBlockState(knot.down(),Blocks.STONE.getDefaultState());
            plant(new BlockPos(-4,99,1));plant(new BlockPos(4,99,1));
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            require(!TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Flower cast also created knot");
            require(TendrilBloomManager.activeTendrils(p.getEntityWorld(),p.getUuid())==2,"Flower source count changed");
        }
        if(t==188) {
            require(TendrilBloomManager.activeBlooms(p.getEntityWorld(),p.getUuid())==2,"Flower blooms did not arrive");
            SeedlingManager.destroySeedlingAtAnchor(p.getEntityWorld(),new BlockPos(-4,100,1));
            require(TendrilBloomManager.activeBlooms(p.getEntityWorld(),p.getUuid())==1,"Flower cleanup affected unrelated source or retained own bloom");
            SeedlingManager.destroySeedlingAtAnchor(p.getEntityWorld(),new BlockPos(4,100,1));
            require(TendrilBloomManager.activeBlooms(p.getEntityWorld(),p.getUuid())==0,"Second source cleanup failed");
        }
        if(t==195) {
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
            p.setAttached(EWAttachments.AFFINITY,"FIRE");
        }
        if(t==200) {
            require(!TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Affinity exit retained knot");
            p.setAttached(EWAttachments.AFFINITY,"NATURE");
            freshWand();NatureAbilityHandler.castSecondary(p.getEntityWorld(),p,p.getMainHandStack());
        }
        if(t==487) {
            require(!TendrilBloomManager.hasKnot(p.getEntityWorld(),knot),"Knot never expired");
            require(TendrilBloomManager.activeBlooms(p.getEntityWorld(),p.getUuid())==0,"Expiry retained bloom");
            // Orphaned block simulates a chunk loaded after loss of ephemeral source state.
            p.getEntityWorld().setBlockState(knot,ModSpellBlocks.NATURE_ROOT_KNOT.getDefaultState());
        }
        if(t==510) {
            require(p.getEntityWorld().getBlockState(knot).isAir(),"Orphan knot did not remove itself");
            Files.writeString(Path.of("HUB_PASSED.txt"),"Nature expansion passed: ownership, cooldown, aimed sweep, allies, cover, miss/invulnerability/overkill healing, group cap, no planting, three targets, moving/damaged caster, early/completed knot destruction, Guardian crush, enemy/allied wand disarm, support loss, source-specific flower cleanup, affinity exit, expiry and orphan cleanup.\n");
            server.stop(false);
        }
    }
    private static void aim(ServerPlayerEntity player,Vec3d target) {
        Vec3d delta=target.subtract(player.getEyePos());
        player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalLength())));
    }
    private void plant(BlockPos floor) {require(SeedlingManager.tryPlantSeedling(p.getEntityWorld(),p,new BlockHitResult(Vec3d.ofCenter(floor).add(0,.5,0),Direction.UP,floor,false)),"Could not plant fixture flower");}
    private void freshWand() {p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));}
    private int lashes() {return p.getEntityWorld().getEntitiesByClass(ThornLashEntity.class,new Box(-10,90,-10,10,110,10),e->!e.isRemoved()).size();}
    private ZombieEntity zombie(double x,double y,double z) {
        var m=new ZombieEntity(EntityType.ZOMBIE,p.getEntityWorld());m.setAiDisabled(true);m.setNoGravity(true);
        m.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);m.setHealth(200);m.setInvulnerable(false);
        m.refreshPositionAndAngles(x,y,z,0,0);m.equipStack(EquipmentSlot.HEAD,new ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
        p.getEntityWorld().spawnEntity(m);return m;
    }
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception {
        var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
        f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);
    }
    private static void near(float value,float expected,String why) {require(Math.abs(value-expected)<.01,why+": "+value+" expected "+expected);}
    private static void require(boolean condition,String why) {if(!condition)throw new AssertionError(why);}
}
