package com.anton.elementalwands.entity;

import com.anton.elementalwands.item.WindAbilityHandler;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Real projectile collisions, shared cast hits, dash recharge, fan timing and guard interruption. */
public final class WindPressureSmokeMod implements ModInitializer {
    private int tick,y;
    private FracturedGuardianEntity boss;
    private ServerPlayerEntity player;
    private ZombieEntity zombie;
    private ItemStack wand;
    private float health,guard,lockedPitch;
    private final Set<UUID> shards=new HashSet<>();
    private java.util.Set<UUID> wallHits;
    private GuardianWallImpact sharedWall;
    private Vec3d chaseStart;
    private final StringBuilder report=new StringBuilder();
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("PRESSURE_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
    }
    private void run(MinecraftServer server)throws Exception {
        tick++;ServerWorld world=server.getOverworld();
        if(tick==25) {
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            y=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var factory=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"WindPressure",.5,(double)y,.5);player.onTeleportationDone();player.setInvulnerable(true);player.setYaw(0);player.setPitch(0);
            wand=new ItemStack(ModItems.FRACTURED_WAND);
            WindAbilityHandler.castSecondary(world,player,wand);WindAbilityHandler.castSecondary(world,player,wand);
            require(WindAbilityHandler.getDashCharges(wand)==0,"Two dashes did not consume two charges");
            for(int i=0;i<99;i++)WindAbilityHandler.inventoryTick(wand,world,player,EquipmentSlot.MAINHAND);
            require(WindAbilityHandler.getDashCharges(wand)==0,"Dash recharged before five seconds");
            WindAbilityHandler.inventoryTick(wand,world,player,EquipmentSlot.MAINHAND);
            require(WindAbilityHandler.getDashCharges(wand)==1,"Dash failed to recharge at five seconds");
            player.setVelocity(Vec3d.ZERO);player.setPosition(.5,y,.5);
            boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);boss.setPosition(.5,y,4.5);boss.stopReview();world.spawnEntity(boss);
            report.append("Two dash charges retained; recharge occurs exactly at 100 ticks.\n");
        }
        if(boss==null)return;
        if(tick==30) {health=boss.getHealth();guard=boss.getGuard();WindAbilityHandler.castPrimary(world,player,wand);}
        if(tick==36) {
            float loss=health-boss.getHealth();require(loss>1.5 && loss<=2.8,"Fan damage stacked or center missed: "+loss);
            require(guard-boss.getGuard()>4 && guard-boss.getGuard()<=7,"Three blades stacked guard damage");
            require(Math.abs(boss.getVelocity().y)<.1 && boss.getY()<y+.1,"Wind lifted resistant boss");
            boss.addVelocity(0,8,0);require(boss.getVelocity().y<.1,"External launch bypassed boss resistance");
            boss.testAttack(player,GuardianCombatRules.Attack.SHOCKWAVE);
        }
        if(tick==50 || tick==70 || tick==90)WindAbilityHandler.castPrimary(world,player,wand);
        if(tick>36 && tick<=105)require(boss.getY()<y+.15,"Stored impulse launched Guardian after attack: "+boss.getY());
        if(tick==106) {
            report.append("Real three-blade cast hits centered boss once; repeated fire through slam/recovery never launches it.\n");
            boss.discard();
            zombie=EntityType.ZOMBIE.create(world,SpawnReason.COMMAND);zombie.setPosition(.5,y,13.5);zombie.setAiDisabled(true);zombie.equipStack(EquipmentSlot.HEAD,new ItemStack(net.minecraft.item.Items.CARVED_PUMPKIN));zombie.setInvulnerable(false);world.spawnEntity(zombie);zombie.setOnGround(true);
            health=zombie.getHealth();WindAbilityHandler.castPrimary(world,player,wand);
        }
        if(tick==115)require(zombie.getHealth()==health,"Wind projectile exceeded strict 12-block range");
        if(tick==130) {zombie.setPosition(.5,y,8.5);zombie.setVelocity(Vec3d.ZERO);health=zombie.getHealth();WindAbilityHandler.castPrimary(world,player,wand);}
        if(tick==140) {
            require(zombie.getHealth()<health,"Centered ordinary mob missed the new fan");
            zombie.discard();
            var ordinary=EntityType.ZOMBIE.create(world,SpawnReason.COMMAND);ordinary.setOnGround(true);
            var blade=new VacuumBladeEntity(world,player,new Vec3d(0,0,1),false,new HashSet<>());
            blade.onEntityHit(new EntityHitResult(ordinary));
            require(ordinary.getVelocity().y>0,"Ordinary mobs lost knockback");
            float after=ordinary.getHealth();Vec3d recoil=ordinary.getVelocity();
            var rejected=new VacuumBladeEntity(world,player,new Vec3d(0,0,1),false,new HashSet<>());
            rejected.onEntityHit(new EntityHitResult(ordinary));
            require(ordinary.getHealth()==after && ordinary.getVelocity().equals(recoil),"Rejected damage still adds knockback");
            report.append("12-block cutoff rejects distant target; center blade hits close mob; accepted hits push ordinary mobs, rejected hits do not.\n");
            boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);boss.setPosition(.5,y,.5);boss.stopReview();world.spawnEntity(boss);
            player.setPosition(.5,y,12.5);player.setVelocity(Vec3d.ZERO);
        }
        if(tick==145){boss.testAttack(player,GuardianCombatRules.Attack.FAN);shards.clear();}
        if(tick>=145 && tick<205) {
            collect(world);
            if(tick==162)require(shards.isEmpty(),"Barrage released before windup ended");
            if(tick==163)require(shards.size()==3,"First burst did not release at 0.9 seconds");
            if(tick==171)require(shards.size()==6,"Second burst did not release after 0.4 seconds");
            if(tick==179)require(shards.size()==9,"Third burst did not release after 0.4 seconds");
            if(tick==160) {lockedPitch=boss.getFanPitch();player.setPosition(8.5,y+6,12.5);player.setNoGravity(true);}
            if(tick==162)require(boss.getFanPitch()==lockedPitch,"Fan changed aim after lock");
        }
        if(tick==205) {
            require(shards.size()==9,"Phase-one fan released "+shards.size()+" projectiles");
            require(boss.getFanTime(0)<0,"Fan telegraph did not end");
            boss.stopReview();boss.beginPhase();boss.finishPhase();shards.clear();
            player.setPosition(.5,y,12.5);boss.testAttack(player,GuardianCombatRules.Attack.FAN);
        }
        if(tick>205 && tick<320)collect(world);
        if(tick==220) {lockedPitch=boss.getFanPitch();player.setPosition(8.5,y+6,12.5);}
        if(tick==222)require(boss.getFanPitch()==lockedPitch,"Phase-two first volley retargeted in flight");
        if(tick==229)require(boss.getFanPitch()<lockedPitch-5,"Second volley did not visibly aim up at elevated target");
        if(tick==320) {
            require(shards.size()==9,"Phase-two fan released "+shards.size()+" projectiles");
            report.append("Barrage releases nine real projectiles in each phase; each burst commits aim, and second burst reacquires elevated target.\n");
            boss.stopReview();boss.startFight();player.setPosition(.5,y,12.5);player.setNoGravity(false);
        }
        if(tick==325) {
            var f=FracturedGuardianEntity.class.getDeclaredField("combat");f.setAccessible(true);
            var combat=(GuardianBossCombat)f.get(boss);
            var interrupt=GuardianBossCombat.class.getDeclaredMethod("interruptAction");interrupt.setAccessible(true);interrupt.invoke(combat);
            combat.testAttack(player,GuardianCombatRules.Attack.FAN);
        }
        if(tick==333) {
            boss.clearGuardHurtWindows();boss.timeUntilRegen=0;
            boss.damage(world,world.getDamageSources().playerAttack(player),400);
        }
        if(tick==337) {
            require(boss.isGuardOpening() && boss.getFanTime(0)<0,"Guard break did not cancel fan windup");
            require(world.getEntitiesByClass(GuardianRockEntity.class,boss.getBoundingBox().expand(60),r->r.isAlive()).isEmpty(),"Guard cancellation left fan projectiles");
            report.append("Earned guard break cancels the fan and leaves no pending projectile hazard.\n");
            boss.stopReview();boss.setPosition(.5,y,.5);boss.setVelocity(Vec3d.ZERO);
            player.setPosition(.5,y,10.5);player.setYaw(180);player.setHeadYaw(180);player.setBodyYaw(180);player.setPitch(0);player.setInvulnerable(false);player.setHealth(20);player.timeUntilRegen=0;
            com.anton.elementalwands.item.StoneAbilityHandler.castSecondary(world,player,new ItemStack(ModItems.FRACTURED_WAND));
            require(!com.anton.elementalwands.item.StoneAbilityHandler.guardianWallBlocks(world).isEmpty(),"Fan wall fixture failed");
            require(com.anton.elementalwands.item.StoneAbilityHandler.guardianWallBlocks(world).stream().allMatch(p->p.getZ()<player.getZ()),"Wall fixture faces wrong way");
            wallHits=new HashSet<>();sharedWall=new GuardianWallImpact();fireShard(world);
        }
        if(tick==351)fireShard(world); // Delayed member of the same volley still sees the absorbed wall.
        if(tick==367) {
            require(player.getHealth()==20,"A later shard leaked through its own volley's absorbed wall: "+player.getHealth());
            require(com.anton.elementalwands.item.StoneAbilityHandler.guardianWallBlocks(world).isEmpty(),"Fan failed to consume wall");
            sharedWall=new GuardianWallImpact();wallHits=new HashSet<>();fireShard(world);
        }
        if(tick==381) {
            require(player.getHealth()<20,"Next volley stayed blocked by the consumed wall");
            health=player.getHealth();player.timeUntilRegen=0;fireShard(world);
        }
        if(tick==397) {
            require(player.getHealth()==health,"Multiple shards from one volley stacked damage");
            report.append("Fan consumes Stone Wall once; delayed shards retain that volley's cover, next volley hits, and one volley cannot stack damage.\n");
            player.setInvulnerable(true);player.setPosition(.5,y,22.5);boss.startFight();chaseStart=boss.getEntityPos();
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);var combat=(GuardianBossCombat)field.get(boss);
            var readyField=GuardianBossCombat.class.getDeclaredField("ready");readyField.setAccessible(true);
            @SuppressWarnings("unchecked") var ready=(Map<GuardianCombatRules.Attack,Long>)readyField.get(combat);
            for(var attack:GuardianCombatRules.Attack.values())ready.put(attack,world.getTime()+1000);
        }
        if(tick==430) {
            require(boss.getEntityPos().distanceTo(chaseStart)>1,"Guardian did not approach: pos="+boss.getEntityPos()+" ai="+boss.isAiDisabled()+" navIdle="+boss.getNavigation().isIdle()+" speed="+boss.getMovementSpeed()+" velocity="+boss.getVelocity()+" onGround="+boss.isOnGround()+" path="+boss.getNavigation().getCurrentPath());
            report.append("Guardian advances toward distant player while attacks cool down.\n");
            player.setNoGravity(true);player.setOnGround(false);player.setPosition(.5,y+1.2,22.5);
        }
        if(tick==460) {
            require(hoverTicks()==0,"Normal jump height is treated as hovering");
            player.setPosition(.5,y+5,22.5);player.setOnGround(false);
        }
        if(tick==486) {
            require(hoverTicks()>=GuardianFanRules.HOVER_TICKS,"Sustained hovering not recognized");
            var f=FracturedGuardianEntity.class.getDeclaredField("combat");f.setAccessible(true);var combat=(GuardianBossCombat)f.get(boss);
            var r=GuardianBossCombat.class.getDeclaredField("ready");r.setAccessible(true);
            @SuppressWarnings("unchecked") var ready=(Map<GuardianCombatRules.Attack,Long>)r.get(combat);ready.put(GuardianCombatRules.Attack.FAN,world.getTime());
        }
        if(tick==490) {
            require(boss.getFanTime(0)>=0,"Available aerial fan was not selected against hovering");
            report.append("Normal jump height never counts as hovering; sustained airborne target selects an available fan.\n");
            boss.stopReview(); boss.discard();
            boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
            boss.setPosition(.5,y,.5);world.spawnEntity(boss);boss.startFight();
            player.setPosition(.5,y+5,12.5);player.setNoGravity(true);player.setOnGround(false);
        }
        if(tick==515 || tick==535) {
            if(tick==535){boss.beginPhase();boss.finishPhase();}
            forceDueBeam(world);
        }
        // Allow normal gravity to restore support after interrupting the frozen awakening.
        if(tick==519 || tick==539) {
            require(boss.getBeamTime(0)>=0 && boss.getFanTime(0)<0,
                    "Ready laser was overridden by aerial/pending fan or Nature clearing in phase "+(boss.isUnstable()?2:1)+rotationState());
        }
        if(tick==540) {
            report.append("Actual director reserves a ready laser after two other attacks in BOTH phases, ahead of hover/pending fan and Nature clearing.\n");
            boss.stopReview();Files.writeString(Path.of("PRESSURE_PASSED.txt"),report);System.out.println("WIND PRESSURE PASSED\n"+report);server.stop(false);
        }
        if(tick>550)throw new AssertionError("Wind/pressure fixture timed out");
    }
    private String rotationState()throws Exception {
        var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);var combat=field.get(boss);
        String state=" pos="+boss.getEntityPos()+" grounded="+boss.isOnGround()+" visible="+boss.canSee(player)+" player="+player.getEntityPos()+" guard="+boss.getGuard()+" beam="+boss.getBeamTime(0);
        for(String key:List.of("active","last","engaged","waking","attacksSinceBeam","nextAction","pendingFan","approachUntil")) {
            var f=GuardianBossCombat.class.getDeclaredField(key);f.setAccessible(true);state+=" "+key+"="+f.get(combat);
        }
        return state;
    }
    private void forceDueBeam(ServerWorld world)throws Exception {
        var f=FracturedGuardianEntity.class.getDeclaredField("combat");f.setAccessible(true);
        var combat=(GuardianBossCombat)f.get(boss);
        var interrupt=GuardianBossCombat.class.getDeclaredMethod("interruptAction");interrupt.setAccessible(true);interrupt.invoke(combat);
        for(String name:List.of("waking","attacksSinceBeam")) {
            var field=GuardianBossCombat.class.getDeclaredField(name);field.setAccessible(true);field.setInt(combat,name.equals("waking")?0:2);
        }
        for(String name:List.of("nextAction","approachUntil")) {
            var field=GuardianBossCombat.class.getDeclaredField(name);field.setAccessible(true);field.setLong(combat,0);
        }
        var readyField=GuardianBossCombat.class.getDeclaredField("ready");readyField.setAccessible(true);
        @SuppressWarnings("unchecked") var ready=(Map<GuardianCombatRules.Attack,Long>)readyField.get(combat);ready.clear();
        var last=GuardianBossCombat.class.getDeclaredField("last");last.setAccessible(true);last.set(combat,GuardianCombatRules.Attack.FAN);
        var pending=GuardianBossCombat.class.getDeclaredField("pendingFan");pending.setAccessible(true);pending.setBoolean(combat,true);
        var natureField=GuardianBossCombat.class.getDeclaredField("nature");natureField.setAccessible(true);
        var response=(GuardianNatureResponse)natureField.get(combat);response.reset();
        response.thorn(world.getTime()-40);response.thorn(world.getTime());
        require(response.wantsClear(world.getTime()),"Nature clearing override fixture was not armed");
        boss.setPosition(.5,y,.5);boss.setVelocity(Vec3d.ZERO);boss.setOnGround(true);
    }
    private int hoverTicks()throws Exception {
        var f=FracturedGuardianEntity.class.getDeclaredField("combat");f.setAccessible(true);
        var h=GuardianBossCombat.class.getDeclaredField("hovering");h.setAccessible(true);
        @SuppressWarnings("unchecked") var counts=(Map<UUID,Integer>)h.get(f.get(boss));return counts.getOrDefault(player.getUuid(),0);
    }
    private void fireShard(ServerWorld world) {
        var shard=new GuardianRockEntity(ModEntities.GUARDIAN_ROCK,world);shard.setOwner(boss);shard.setPosition(.5,y+1,.5);
        shard.releaseShard(new Vec3d(0,0,1),wallHits,sharedWall);world.spawnEntity(shard);
    }
    private void collect(ServerWorld world){for(var r:world.getEntitiesByClass(GuardianRockEntity.class,boss.getBoundingBox().expand(70),r->r.isShard()))shards.add(r.getUuid());}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
