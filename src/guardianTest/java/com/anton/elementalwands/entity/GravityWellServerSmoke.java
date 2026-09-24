package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Real flight, impact, recasts, attribution, friendly protection and lifecycle regression. */
public final class GravityWellServerSmoke implements ModInitializer {
    private int tick;
    private ServerPlayerEntity owner, target, ally, covered;
    private GravityBombEntity bomb;
    private AstralDoubleEntity clone;
    private FracturedGuardianEntity guardian;
    private long impact, flightLaunched;
    private float healthBefore;
    private double throwDistance;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch(Throwable e) { e.printStackTrace(); try { Files.writeString(Path.of("PRESSURE_FAILED.txt"),e.toString()); } catch(Exception ignored) {} server.stop(false); }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        int t=++tick;var w=server.getOverworld();
        if(t==25) {
            for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++){w.getChunk(x,z);w.setChunkForced(x,z,true);}
            for(int x=-12;x<=35;x++)for(int z=-12;z<=35;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            owner=player(server,"GravityCaster",.5,100,.5);target=player(server,"GravityTarget",30,100,30);
            ally=player(server,"GravityAlly",32,100,30);covered=player(server,"GravityCovered",34,100,30);
            for(var p:List.of(owner,target,ally,covered)) { p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.getHungerManager().setFoodLevel(10); }
            owner.setAttached(EWAttachments.AFFINITY,"SPACE");owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!GravityWellManager.cast(owner),"Unowned cast accepted");
            WandProgression.earn(owner,WizardAffinity.SPACE,3000);
            for(String id:List.of("gravity_well","astral_double","blink_rift"))WandProgression.purchase(owner,"SPACE",id);
            WandLoadouts.equip(owner,"SPACE",1,"blink_rift");WandLoadouts.equip(owner,"SPACE",3,"astral_double");WandLoadouts.equip(owner,"SPACE",4,"gravity_well");
            require(WandSpells.forAffinity(WizardAffinity.SPACE).size()==5,"Space lacks five spells");
            require(WandSpells.defaults(WizardAffinity.SPACE).get(1).equals("blink_rift"),"Default Technique changed");
            var team=server.getScoreboard().addTeam("gravity_allies");server.getScoreboard().addScoreHolderToTeam(owner.getNameForScoreboard(),team);server.getScoreboard().addScoreHolderToTeam(ally.getNameForScoreboard(),team);
            aim();
        }
        if(owner==null)return;
        if(t==26) { WandLoadouts.cast(owner,4);bomb=GravityWellManager.active(owner);require(bomb!=null&&!bomb.isWell(),"Throw missing");require(GravityWellManager.remaining(owner)==0,"Cooldown before impact");WandLoadouts.cast(owner,4,false);require(GravityWellManager.active(owner)==bomb,"Held cast duplicated bomb"); }
        if(t==44) {
            require(AstralDoubleManager.cast(owner),"Double setup failed");
            var orb=w.getEntitiesByClass(AstralOrbEntity.class,new Box(-20,95,-20,40,115,40),e->true).getFirst();
            AstralDoubleManager.land(owner,orb,new Vec3d(bomb.getX()-2,100,bomb.getZ()));
            clone=AstralDoubleManager.active(owner);require(clone!=null,"Double setup landing failed");
        }
        if(t==50) {
            require(bomb.isWell(),"Ground impact missing");throwDistance=bomb.getEntityPos().subtract(new Vec3d(.5,100,.5)).horizontalLength();
            require(throwDistance>=10&&throwDistance<=14,"Wrong throw range "+throwDistance);
            impact=GravityWellManager.state(owner).getLong("impact",0);require(GravityWellManager.remaining(owner)==320-(w.getTime()-impact),"Cooldown not from impact");
            Vec3d pos=bomb.getEntityPos();target.setPosition(pos.add(2,1,0));ally.setPosition(pos.add(-2,1,0));covered.setPosition(pos.add(0,1,2));
            // A real solid wall must block both pull and collapse damage.
            for(int x=-1;x<=1;x++)for(int y=100;y<=103;y++)w.setBlockState(new BlockPos((int)Math.floor(pos.x)+x,y,(int)Math.floor(pos.z)+1),Blocks.STONE.getDefaultState());
            target.setVelocity(Vec3d.ZERO);ally.setVelocity(Vec3d.ZERO);covered.setVelocity(Vec3d.ZERO);owner.setVelocity(Vec3d.ZERO);
        }
        if(t==52) {
            require(target.getVelocity().x<-.01,"Enemy not pulled");require(target.getHealth()==20,"Pull dealt damage");
            require(ally.getVelocity().lengthSquared()<.0001&&ally.getHealth()==20,"Ally pulled");
            require(covered.getVelocity().lengthSquared()<.0001,"Pulled through wall");require(!clone.isRemoved(),"Own double damaged");
            WandLoadouts.cast(owner,4,false);require(!bomb.isRemoved(),"Held input collapsed well");
            WandLoadouts.cast(owner,4,true);require(bomb.isCollapsing()&&!bomb.isBurst(),"Early collapse buildup missing");
            require(target.getHealth()==20,"Damage landed before buildup");
        }
        if(t==58){require(!GravityWellManager.cast(owner),"Recast restarted collapse");require(target.getHealth()==20,"Buildup damage landed early");}
        if(t==61) {
            require(bomb.isBurst()&&!bomb.isRemoved(),"Outward burst phase missing");require(Math.abs(target.getHealth()-14)<.01,"Burst not six damage "+target.getHealth());
            require(ally.getHealth()==20&&covered.getHealth()==20&&!clone.isRemoved(),"Protected burst target damaged");
            require(target.getVelocity().x<-.4,"Collapse lacks stronger tug: "+target.getVelocity());
            require(GravityWellManager.state(owner).getLong("impact",0)==impact,"Early collapse restarted recovery");
            require(WandProgression.get(owner,WizardAffinity.SPACE).xp()>0,"No Space XP");
            owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));require(!GravityWellManager.cast(owner),"Fresh wand bypassed recovery");
            for(int x=-1;x<=1;x++)for(int y=100;y<=103;y++)w.setBlockState(new BlockPos(x,y,(int)Math.floor(bomb.getZ())+1),Blocks.AIR.getDefaultState());clone.discard();
        }
        if(t==75){require(bomb.isRemoved(),"Cosmetic burst did not expire");require(Math.abs(target.getHealth()-14)<.01,"Burst damaged twice");}
        if(t==80) { reset();target.setPosition(.5,101,4.5);WandLoadouts.cast(owner,4);bomb=GravityWellManager.active(owner); }
        if(t==85) { require(bomb.isWell()&&bomb.getZ()<5,"Enemy impact did not activate immediately");impact=GravityWellManager.state(owner).getLong("impact",0);target.setPosition(20,100,20); }
        if(t==88)require(bomb.getZ()<5,"Well followed struck enemy");
        if(t==92) { owner.setPosition(10,100,.5);WandLoadouts.cast(owner,1);require(BlinkRiftManager.hasActiveRift(w,owner)&&!bomb.isRemoved(),"Blink canceled well"); }
        if(t==100) { WandLoadouts.cast(owner,1);require(!BlinkRiftManager.hasActiveRift(w,owner)&&!bomb.isRemoved(),"Rift return canceled well");owner.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY); }
        if(t==105)require(GravityWellManager.active(owner)==bomb,"Putting away wand canceled field");
        if(t==150) { target.setPosition(bomb.getEntityPos().add(2,0,0));target.setVelocity(Vec3d.ZERO);target.setHealth(20);target.timeUntilRegen=0;healthBefore=target.getHealth(); }
        if(t==185) { require(bomb.isRemoved()&&target.getHealth()<healthBefore,"Automatic collapse missing");require(GravityWellManager.state(owner).getLong("expires",0)-impact==80,"Well lifetime not 80 ticks"); }
        if(t==190) { reset();for(int y=100;y<=103;y++)w.setBlockState(new BlockPos(0,y,3),Blocks.STONE.getDefaultState());GravityWellManager.cast(owner);bomb=GravityWellManager.active(owner); }
        if(t==195) { require(bomb.isWell()&&bomb.getZ()<3,"Wall did not activate immediately");for(int y=100;y<=103;y++)w.setBlockState(new BlockPos(0,y,3),Blocks.AIR.getDefaultState());GravityWellManager.cancel(owner);require(bomb.isRemoved(),"Cancel missing"); }
        if(t==200) { reset();GravityWellManager.cast(owner);bomb=GravityWellManager.active(owner);owner.setAttached(EWAttachments.AFFINITY,"FIRE"); }
        if(t==202) { require(bomb.isRemoved()&&GravityWellManager.remaining(owner)>0,"Affinity cancellation skipped cleanup/recovery");owner.setAttached(EWAttachments.AFFINITY,"SPACE");reset();GravityWellManager.cast(owner);bomb=GravityWellManager.active(owner); }
        if(t==225) {
            require(bomb.isWell(),"Guardian setup missing");guardian=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,w);guardian.stopReview();guardian.setAiDisabled(true);guardian.setNoGravity(true);
            guardian.setPosition(bomb.getEntityPos().add(1,0,0));w.spawnEntity(guardian);guardian.setVelocity(Vec3d.ZERO);healthBefore=guardian.getHealth();
        }
        if(t==228) { require(guardian.getVelocity().lengthSquared()<.0001,"Guardian dragged by well");GravityWellManager.cast(owner);require(guardian.getHealth()==healthBefore,"Guardian damaged before burst"); }
        if(t==237) { require(guardian.getHealth()<healthBefore,"Guardian resisted damage too");require(guardian.getVelocity().lengthSquared()<.0001,"Guardian dragged by collapse");guardian.discard(); }
        if(t==245) { reset();GravityWellManager.cast(owner);bomb=GravityWellManager.active(owner);owner.setHealth(0); }
        if(t==247) { require(bomb.isRemoved(),"Death left bomb");owner.setHealth(20);reset();GravityWellManager.cast(owner);bomb=GravityWellManager.active(owner);GravityWellManager.cancel(owner);require(GravityWellManager.remaining(owner)==320,"Lost bomb did not cost recovery"); }
        if(t==566)require(!GravityWellManager.cast(owner),"Recovery expired early");
        if(t==567) {
            require(GravityWellManager.cast(owner),"Recovery exceeded 320 ticks");bomb=GravityWellManager.active(owner);
        }
        if(t==592) {
            require(bomb.isWell(),"Manager expiry setup missing");
            // Shorten only the manager deadline, leaving the entity's own deadline in the future.
            // This verifies expiry independently of entity ticking in a loaded chunk.
            var data=GravityWellManager.state(owner).copy();data.putLong("expires",w.getTime()+1);owner.setAttached(EWAttachments.GRAVITY_STATE,data);
        }
        if(t==594) {
            require(bomb.isCollapsing()&&!bomb.isBurst(),"Manager did not begin collapse independently of entity ticking");
            reset();require(GravityWellManager.cast(owner),"Flight timeout setup failed");bomb=GravityWellManager.active(owner);
            bomb.setPosition(.5,150,.5);flightLaunched=w.getTime();
        }
        if(t>594&&t<654) {
            require(!bomb.isRemoved(),"Flight timeout expired early");
            // Keep entity age from advancing and avoid surface impacts; world time must still expire it.
            bomb.age=0;bomb.setPosition(.5,150,.5);bomb.setVelocity(Vec3d.ZERO);
        }
        if(t==655) {
            require(bomb.isRemoved()&&GravityWellManager.state(owner).getLong("impact",0)-flightLaunched==60&&GravityWellManager.remaining(owner)==319,"World-time flight timeout missed cleanup/recovery: removed="+bomb.isRemoved()+" remaining="+GravityWellManager.remaining(owner)+" time="+w.getTime()+" state="+GravityWellManager.state(owner));
            Files.writeString(Path.of("PRESSURE_PASSED.txt"),"Gravity Well passed: five Space spells; purchase/equip; real throw distance="+throwDistance+"; swept floor/entity/wall impact; stationary core; 80-tick pull lifetime; eight-tick buildup then single burst damage; timed cosmetic cleanup; deliberate early and automatic collapse; six base damage and Space XP; cover/allies/own double protection; stronger collapse tug; Guardian displacement resistance with damage; independent Blink/return; held input and duplicate prevention; impact-based exact 320-tick recovery; fresh-wand protection; put-away, affinity/death/lost-flight cleanup; manager well/flight expiry independent of entity ticks.\n");server.stop(false);
        }
    }
    private void aim(){owner.setYaw(0);owner.setHeadYaw(0);owner.setPitch(0);}
    private void reset(){GravityWellManager.cancel(owner);owner.setAttached(EWAttachments.GRAVITY_STATE,new NbtCompound());owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));owner.setPosition(.5,100,.5);aim();for(var p:List.of(target,ally,covered)){p.setPosition(30,100,30);p.setHealth(20);p.timeUntilRegen=0;p.setVelocity(Vec3d.ZERO);}}
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception{var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);}
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
