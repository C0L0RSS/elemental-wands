package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.*;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Real entities, authoritative loadout casts, real collision/damage, and Guardian geometry. */
public final class AstralDoubleServerSmoke implements ModInitializer {
    private int tick;
    private ServerPlayerEntity owner,target;
    private AstralDoubleEntity clone;
    private FracturedGuardianEntity guardian;
    private GuardianBossCombat combat;
    private GuardianBeamAttack beam;
    private Vec3d anchor,returnPoint;
    private long expiration;
    private double throwDistance;
    public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("PRESSURE_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});}
    private void run(MinecraftServer server)throws Exception {
        int t=++tick;var w=server.getOverworld();
        if(t==25){
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){w.getChunk(x,z);w.setChunkForced(x,z,true);}
            for(int x=-10;x<=75;x++)for(int z=-10;z<=40;z++)w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());
            owner=player(server,"AstralCaster",.5,100,.5);target=player(server,"AstralTarget",.5,100,18.5);
            for(var p:List.of(owner,target)){p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.getHungerManager().setFoodLevel(10);}
            owner.setAttached(EWAttachments.AFFINITY,"SPACE");owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
            require(!AstralDoubleManager.cast(owner),"Unowned cast accepted");
            WandProgression.earn(owner,WizardAffinity.SPACE,2000);
            WandProgression.purchase(owner,"SPACE","blink_rift");WandProgression.purchase(owner,"SPACE",AstralDoubleManager.ID);
            WandLoadouts.equip(owner,"SPACE",1,"blink_rift");WandLoadouts.equip(owner,"SPACE",3,AstralDoubleManager.ID);
            require(WandSpells.defaults(WizardAffinity.SPACE).get(1).equals("blink_rift"),"Changed old default Technique");
            owner.setYaw(0);owner.setHeadYaw(0);owner.setPitch(0);
        }
        if(owner==null)return;
        owner.setVelocity(Vec3d.ZERO);target.setVelocity(Vec3d.ZERO);
        if(t==26){WandLoadouts.cast(owner,3);require(orbs().size()==1,"Toss missing");require(AstralDoubleManager.remaining(owner)==0,"Toss started cooldown");WandLoadouts.cast(owner,3,false);require(orbs().size()==1,"Held input threw twice");}
        if(t==50){clone=AstralDoubleManager.active(owner);require(clone!=null,"Orb never formed a double");anchor=clone.getEntityPos();throwDistance=anchor.distanceTo(owner.getEntityPos());require(throwDistance>=6 && throwDistance<=8,"Wrong short throw distance "+throwDistance);require(AstralDoubleManager.remaining(owner)==0,"Live double started cooldown");require(clone.ownerUuid().equals(owner.getUuid()),"Skin owner missing");expiration=AstralDoubleManager.state(owner).getLong("expires",0);}
        if(t==60){WandLoadouts.cast(owner,0);require(bolts().size()==2,"Primary not mirrored");WandLoadouts.cast(owner,0);require(bolts().size()==2,"Rejected primary echoed");}
        if(t==90){require(Math.abs(target.getHealth()-6)<.01,"Mirrored primaries should deal 14; health="+target.getHealth());require(WandProgression.get(owner,WizardAffinity.SPACE).xp()>0,"Lost Space XP");require(clone.getEntityPos().squaredDistanceTo(anchor)<.001,"Double moved");
            target.setHealth(20);target.timeUntilRegen=0;
            for(int i=0;i<2;i++){var bolt=new SingularityBoltEntity(w,owner,new Vec3d(.5,101.5,17),new Vec3d(0,0,1));bolt.setAstralPair();w.spawnEntity(bolt);}
        }
        if(t==96)require(Math.abs(target.getHealth()-6)<.01,"Simultaneous paired impacts lost damage to immunity");
        if(t==100){owner.setPosition(8.5,100,.5);returnPoint=owner.getEntityPos();WandLoadouts.cast(owner,1);require(BlinkRiftManager.hasActiveRift(w,owner),"Blink did not leave rift");require(AstralDoubleManager.active(owner)==clone,"Blink consumed double");}
        if(t==108){WandLoadouts.cast(owner,3);require(owner.getEntityPos().squaredDistanceTo(anchor)<.001,"Double teleport failed");require(clone.isRemoved(),"Teleport did not consume double");require(AstralDoubleManager.remaining(owner)==300,"Full recovery did not start on teleport");require(BlinkRiftManager.hasActiveRift(w,owner),"Double consumed Blink Rift");WandLoadouts.cast(owner,1);require(owner.getEntityPos().squaredDistanceTo(returnPoint)<.001,"Immediate rift return failed");require(!BlinkRiftManager.hasActiveRift(w,owner),"Rift not consumed by return");}
        if(t==110){owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));require(!AstralDoubleManager.cast(owner),"Fresh wand erased recovery");}
        if(t==120){reset();clone=place(new Vec3d(4.5,100,.5));require(!clone.damage(w,w.getDamageSources().playerAttack(owner),1),"Owner destroyed own clone");var team=server.getScoreboard().addTeam("astral_allies");server.getScoreboard().addScoreHolderToTeam(owner.getNameForScoreboard(),team);server.getScoreboard().addScoreHolderToTeam(target.getNameForScoreboard(),team);require(!clone.damage(w,w.getDamageSources().playerAttack(target),1),"Ally destroyed clone");server.getScoreboard().removeScoreHolderFromTeam(target.getNameForScoreboard(),team);require(clone.damage(w,w.getDamageSources().playerAttack(target),.1f)&&clone.isRemoved(),"Single hostile hit failed");require(AstralDoubleManager.remaining(owner)==300,"Destruction recovery missing");}
        if(t==130){reset();require(AstralDoubleManager.cast(owner),"Invalid setup failed");var orb=orbs().getFirst();AstralDoubleManager.land(owner,orb,new Vec3d(3,100.8,3));require(AstralDoubleManager.active(owner)==null&&orbs().isEmpty(),"Unsupported landing formed clone");require(AstralDoubleManager.remaining(owner)==60,"Invalid landing not three seconds");}
        if(t==189)require(!AstralDoubleManager.cast(owner),"Failed landing recovered early");
        if(t==190){require(AstralDoubleManager.cast(owner),"Failed landing recovery lasted longer than three seconds");AstralDoubleManager.cancel(owner);}
        if(t==195){reset();clone=place(new Vec3d(4.5,100,.5));owner.setPosition(74.5,100,.5);}
        if(t==202){require(!AstralDoubleManager.cast(owner)&&!clone.isRemoved(),"Out-of-range swap consumed double");owner.setPosition(.5,100,.5);}
        if(t==210){w.setBlockState(new BlockPos(4,101,0),Blocks.STONE.getDefaultState());require(!AstralDoubleManager.cast(owner),"Swapped inside solid cover");}
        if(t==212){require(clone.isRemoved(),"Blocked body left unusable clone");w.setBlockState(new BlockPos(4,101,0),Blocks.AIR.getDefaultState());reset();clone=place(new Vec3d(4.5,100,.5));owner.setAttached(EWAttachments.AFFINITY,"FIRE");}
        if(t==214){require(clone.isRemoved()&&AstralDoubleManager.remaining(owner)>0,"Affinity exit lost cleanup/recovery");owner.setAttached(EWAttachments.AFFINITY,"SPACE");reset();clone=place(new Vec3d(4.5,100,.5));owner.setHealth(0);}
        if(t==216){require(clone.isRemoved(),"Owner death left clone");owner.setHealth(20);reset();clone=place(new Vec3d(4.5,100,.5));owner.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);}
        if(t==220){require(AstralDoubleManager.active(owner)==clone,"Putting away wand erased escape beacon");reset();clone=place(new Vec3d(4.5,100,.5));var field=AstralDoubleManager.class.getDeclaredField("CASTS");field.setAccessible(true);Object c=((Map<?,?>)field.get(null)).get(owner.getUuid());var expires=c.getClass().getDeclaredField("expires");expires.setAccessible(true);require(expires.getLong(c)-w.getTime()==900,"Lifetime not 45 seconds");expires.setLong(c,w.getTime()+2);}
        if(t==224){require(clone.isRemoved()&&AstralDoubleManager.remaining(owner)>0,"Expiry skipped recovery");reset();clone=place(new Vec3d(.5,100,6.5));
            guardian=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,w);guardian.setPosition(.5,100,.5);guardian.stopReview();guardian.setAiDisabled(true);guardian.setNoGravity(true);w.spawnEntity(guardian);
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(guardian);
            combat.emitWave(w,guardian.getEntityPos(),Set.of());}
        if(t==250){require(clone.isRemoved(),"Guardian wave failed to destroy clone");guardian.stopReview();reset();clone=place(new Vec3d(2.5,100,.5));combat.crushGrowth(w,guardian.getEntityPos(),4.5,false,6);require(clone.isRemoved(),"Guardian slam failed");}
        if(t==260){reset();clone=place(new Vec3d(.5,100,10.5));var rock=new GuardianRockEntity(ModEntities.GUARDIAN_ROCK,w);rock.setOwner(guardian);rock.setPosition(.5,101,5);rock.releaseShard(new Vec3d(0,0,1),new HashSet<>(),new GuardianWallImpact());w.spawnEntity(rock);}
        if(t==266){require(clone.isRemoved(),"Guardian shard failed");reset();clone=place(new Vec3d(.5,100,18.5));owner.setPosition(.5,100,21.5);beam=new GuardianBeamAttack(guardian);beam.begin(owner);}
        if(t>=267&&t<=310)beam.tick(w);
        if(t==310){require(clone.isRemoved(),"Guardian beam failed");beam.cancel();guardian.discard();reset();clone=place(new Vec3d(4.5,100,.5));
            // Player attachment is persisted independently of the retained wand.
            var codec=EWAttachments.ASTRAL_STATE;require(AstralDoubleManager.state(owner).getBoolean("active",false),"Active crash recovery marker missing");
            AstralDoubleManager.cancel(owner);NbtCompound saved=AstralDoubleManager.state(owner).copy();owner.setAttached(codec,saved);owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));require(!AstralDoubleManager.cast(owner),"Persisted recovery bypassed");
            Files.writeString(Path.of("PRESSURE_PASSED.txt"),"Astral Double passed: purchase/equip/defaults; real short throw distance="+throwDistance+"; stationary owner-backed double; deferred cooldown; primary mirroring; simultaneous 14-damage paired hits; Space XP; Blink -> double -> immediate rift return; one-use consumption; fresh-wand recovery; owner/allied protection; one-hit hostile destruction; invalid landing and exact 60-tick recovery; range and solid destination safety; affinity/death cleanup; retained beacon with wand put away; 900-tick lifespan and expiry recovery; real Guardian wave, slam, shard and beam contact.\n");server.stop(false);}
    }
    private void reset(){AstralDoubleManager.cancel(owner);owner.setAttached(EWAttachments.ASTRAL_STATE,new NbtCompound());owner.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));owner.setPosition(.5,100,.5);owner.setYaw(0);owner.setHeadYaw(0);owner.setPitch(0);target.setPosition(30.5,100,30.5);target.setHealth(20);target.timeUntilRegen=0;for(var b:bolts())b.discard();}
    private AstralDoubleEntity place(Vec3d at){require(AstralDoubleManager.cast(owner),"Placement setup cast failed");AstralDoubleManager.land(owner,orbs().getFirst(),at);var d=AstralDoubleManager.active(owner);require(d!=null,"Placement rejected "+at);return d;}
    private List<AstralOrbEntity> orbs(){return owner.getEntityWorld().getEntitiesByClass(AstralOrbEntity.class,new Box(-80,90,-80,90,120,80),e->!e.isRemoved());}
    private List<SingularityBoltEntity> bolts(){return owner.getEntityWorld().getEntitiesByClass(SingularityBoltEntity.class,new Box(-80,90,-80,90,120,80),e->!e.isRemoved());}
    private static ServerPlayerEntity player(MinecraftServer s,String name,double x,double y,double z)throws Exception{var f=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);f.setAccessible(true);return (ServerPlayerEntity)f.invoke(null,s,UUID.randomUUID(),name,x,y,z);}
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
