package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Real-world damage, movement state, terrain permissions and lifecycle regression fixture. */
public final class StoneTechniqueSmokeMod implements ModInitializer {
    private int tick;private ServerPlayerEntity caster,victim;private float health;private boolean chargedHit;
    private double airSpeed, launchedPeak;
    private net.minecraft.entity.mob.ZombieEntity farMob;
    private ServerPlayerEntity beyond;
    private int farHitTick=-1;
    private ServerPlayerEntity rooted,swapped,dead,ceiling;
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch(Throwable e) { e.printStackTrace();try{Files.writeString(Path.of("STONE_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false); }
        });
    }
    private void run(MinecraftServer server)throws Exception {
        ++tick;var world=server.getOverworld();require(tick<520,"Timed out");
        if(caster!=null) { caster.playerTick();victim.playerTick(); }
        if(tick==30) {
            for(int x=-2;x<=2;x++)for(int z=-2;z<=8;z++)world.getChunk(x,z);
            for(int x=-10;x<=20;x++)for(int z=-5;z<=110;z++)world.setBlockState(new BlockPos(x,100,z),Blocks.STONE.getDefaultState());
            caster=player(server,"StoneTechnique",.5,101,.5);victim=player(server,"StoneTarget",.5,101,5.5);
            for(var p:List.of(caster,victim)) { p.setNoGravity(true);p.onTeleportationDone();p.setLoaded(true);p.getHungerManager().setFoodLevel(10); }
            victim.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);victim.setHealth(200);health=victim.getHealth();
            caster.setAttached(EWAttachments.AFFINITY,"STONE");freshWand();
            WandProgression.grant(caster,3);WandLoadouts.get(caster);caster.setYaw(0);caster.setOnGround(true);
            require(WandSpells.forAffinity(WizardAffinity.STONE).size()==5,"Stone catalog must contain five spells");
            require(WandLoadouts.get(caster).containsAll(List.of("faultline","stone_charge")),"New spells did not fill free slots");
            WandLoadouts.cast(caster,WandLoadouts.get(caster).indexOf("faultline"));
            require(StoneTechniqueRules.power(.3)==0 && StoneTechniqueRules.power(.82)>.999,"Wrong charge power bounds");
            require(Math.abs(StoneTechniqueRules.turn(0,90,.82,true))<2,"Full-speed turn not constrained");
            surfaceChecks(world);
            farMob=new net.minecraft.entity.mob.ZombieEntity(net.minecraft.entity.EntityType.ZOMBIE,world);
            farMob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0);farMob.refreshPositionAndAngles(.5,101,19.5,0,0);
            farMob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);farMob.setHealth(200);
            farMob.equipStack(EquipmentSlot.HEAD,new ItemStack(net.minecraft.item.Items.CARVED_PUMPKIN));
            world.spawnEntity(farMob);
            beyond=player(server,"BeyondFaultline",.5,101,22.5);beyond.setNoGravity(true);beyond.setLoaded(true);
        }
        if(farMob!=null && tick>30 && tick<65) {
            launchedPeak=Math.max(launchedPeak,farMob.getY()-101);
            if(farMob.getHealth()<200 && farHitTick<0) {
                farHitTick=tick;
                require(((StoneMotionAccess)farMob).elementalwands$stoneInterrupted(),"Distant mob was not interrupted");
                require(farMob.getVelocity().y>.2,"Distant mob did not receive upward velocity: "+farMob.getVelocity());
            }
            if(farHitTick>0 && tick==farHitTick+10)
                require(((StoneMotionAccess)farMob).elementalwands$stoneInterrupted(),"Interrupt ended prematurely");
            if(farHitTick>0 && tick==farHitTick+12)
                require(!((StoneMotionAccess)farMob).elementalwands$stoneInterrupted(),"Interrupt exceeded 0.6 seconds");
        }
        if(tick==65) {
            require(farHitTick>30 && farHitTick<=42,"20-block shockwave too slow or missed: "+farHitTick);
            require(launchedPeak>1 && launchedPeak<1.8,"Mob launch height not noticeable/controlled: "+launchedPeak);
            require(Math.abs(200-farMob.getHealth()-4)<.1,"Distant mob took repeated or unexpected damage");
            require(beyond.getHealth()==20,"Faultline exceeded its range");
            farMob.discard();beyond.setPosition(15,101,100);
            require(Math.abs(health-victim.getHealth()-4)<.1,"Wave did not damage exactly once: "+(health-victim.getHealth()));
            require(!((StoneMotionAccess)victim).elementalwands$stoneInterrupted(),"Movement interrupt failed to expire");
            require(world.getEntitiesByClass(FaultlineSpikeEntity.class,new Box(-15,95,-10,20,110,20),e->true).isEmpty(),"Spikes failed to crumble");
            for(int z=0;z<21;z++)require(world.getBlockState(new BlockPos(0,100,z)).isOf(Blocks.STONE),"Wave changed terrain");
            destructionChecks(world);
        }
        if(tick==75) {
            victim.setPosition(15,101,100);caster.setPosition(.5,101,.5);freshWand();caster.setOnGround(true);
            WandLoadouts.cast(caster,WandLoadouts.get(caster).indexOf("stone_charge"));require(StoneChargeManager.active(caster),"Charge did not start");
        }
        if(tick>75 && tick<134) {
            double speed=StoneChargeManager.speed(caster);require(speed>0,"Charge ended on open floor at "+tick);
            StoneChargeManager.hold(caster);caster.setOnGround(true);caster.setPosition(caster.getEntityPos().add(0,0,speed));
            if(tick==90) {
                caster.damage(world,world.getDamageSources().generic(),1);
                require(StoneChargeManager.active(caster),"Ordinary damage cancelled charge");
            }
            if(tick==132) {
                require(speed>=.80,"Grounded running did not reach full speed: "+speed);
                caster.setVelocity(Vec3d.ZERO);caster.takeKnockback(1,1,0);
                require(caster.getVelocity().horizontalLength()<.001,"Full-speed ordinary knockback was not resisted");
            }
        }
        if(tick==134) {
            victim.setPosition(caster.getEntityPos().add(0,0,.85));victim.timeUntilRegen=0;health=victim.getHealth();
            StoneChargeManager.hold(caster);
        }
        if(tick==136) {
            require(!StoneChargeManager.active(caster),"Enemy collision did not end charge");
            require(health-victim.getHealth()>=13.9,"Full-speed hit too weak: "+(health-victim.getHealth()));chargedHit=true;
            freshWand();StoneChargeManager.start(caster);require(!StoneChargeManager.active(caster),"Fresh wand bypassed charge recovery");
        }
        if(tick==310) {
            victim.setPosition(15,101,100);caster.setPosition(5.5,101,.5);freshWand();caster.setOnGround(true);StoneChargeManager.start(caster);
            require(StoneChargeManager.active(caster),"Charge did not recover");
        }
        if(tick>310 && tick<322) {
            StoneChargeManager.hold(caster);caster.setOnGround(true);caster.setPosition(caster.getEntityPos().add(0,0,StoneChargeManager.speed(caster)));
        }
        if(tick==322) { airSpeed=StoneChargeManager.speed(caster);caster.setOnGround(false);caster.setPosition(caster.getEntityPos().add(0,1,0)); }
        if(tick>=322 && tick<330) { StoneChargeManager.hold(caster);caster.setOnGround(tick>=326);caster.setPosition(caster.getEntityPos().add(0,0,airSpeed)); }
        if(tick==330) {
            require(StoneChargeManager.speed(caster)<=airSpeed+.001,"Airborne time built charge");
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,40,1));StoneChargeManager.hold(caster);
        }
        if(tick==332) {
            require(StoneChargeManager.speed(caster)<airSpeed,"Slowness did not reduce impact speed");
            StoneChargeManager.interrupt(caster);require(!StoneChargeManager.active(caster),"Explicit interrupt failed");
            require(((StoneMotionAccess)caster).elementalwands$stoneInterrupted(),"Interrupt state absent");
            caster.setVelocity(0,0,0);StoneChargeManager.interrupt(caster);
            require(caster.getVelocity().y==0,"Repeat interrupt bypassed immunity window");
        }
        if(tick==345)require(!((StoneMotionAccess)caster).elementalwands$stoneInterrupted(),"Interrupt remained active");
        if(tick==495) {
            caster.setPosition(5.5,101,.5);caster.setOnGround(true);freshWand();StoneChargeManager.start(caster);
            require(StoneChargeManager.active(caster),"Release fixture start failed");StoneChargeManager.stop(caster,true);
        }
        if(tick==503) {
            require(!StoneChargeManager.active(caster),"Braking did not end");require(chargedHit,"Missing full charge coverage");
            rooted=readyPlayer(server,"RootedRunner",-5.5,30.5);
            swapped=readyPlayer(server,"SwappedRunner",-5.5,40.5);
            dead=readyPlayer(server,"DeadRunner",-5.5,50.5);
            ceiling=readyPlayer(server,"CeilingRunner",-5.5,60.5);
            for(var p:List.of(rooted,swapped,dead,ceiling)) {
                StoneChargeManager.start(p);require(StoneChargeManager.active(p),"Lifecycle setup failed for "+p.getName().getString());
            }
            rooted.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,40,6));
            swapped.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);dead.setHealth(0);
            for(int z=59;z<64;z++)world.setBlockState(new BlockPos(-6,103,z),Blocks.OAK_PLANKS.getDefaultState());
            ceiling.setPosition(ceiling.getEntityPos().add(0,.4,.1));ceiling.setOnGround(false);
        }
        if(tick==506) {
            require(!StoneChargeManager.active(rooted) && !StoneChargeManager.active(swapped) && !StoneChargeManager.active(dead),"Root, item change or death leaked charge");
            require(!StoneChargeManager.active(ceiling),"Ceiling collision did not end charge");
            require(world.getBlockState(new BlockPos(-6,103,60)).isOf(Blocks.OAK_PLANKS),"Ceiling collision broke blocks");
            Files.writeString(Path.of("STONE_PASSED.txt"),"PASS: five-slot catalog, 20-block fast Faultline, single damage/crumble, real mob upward launch and 0.6s interrupt, water/leaves/cover, material budget and shielded blocks, containers and tracked terrain, grounded acceleration, direct impact, damage continuation, fresh-wand recovery, airborne pause, slowness, explicit interrupt/grace braking, full-speed knockback resistance, root/item/death cleanup and ceiling stop.\n");
            System.out.println("STONE TECHNIQUES SERVER CHECKS PASSED; Faultline launch peak="+launchedPeak+", distant hit after "+(farHitTick-30)+" ticks");server.stop(false);
        }
    }
    private ServerPlayerEntity readyPlayer(MinecraftServer server,String name,double x,double z)throws Exception {
        var p=player(server,name,x,101,z);p.setLoaded(true);p.onTeleportationDone();p.setNoGravity(true);p.setOnGround(true);
        p.setAttached(EWAttachments.AFFINITY,"STONE");p.equipStack(EquipmentSlot.MAINHAND,new ItemStack(ModItems.FRACTURED_WAND));
        WandProgression.grant(p,3);WandLoadouts.get(p);return p;
    }
    private void freshWand() { caster.equipStack(EquipmentSlot.MAINHAND,new ItemStack(ModItems.FRACTURED_WAND)); }
    private void surfaceChecks(ServerWorld w) {
        w.setBlockState(new BlockPos(10,100,0),Blocks.WATER.getDefaultState());
        require(FaultlineManager.surface(w,caster,new Vec3d(9.5,101,.5),10.5,.5)!=null,"Water surface rejected");
        w.setBlockState(new BlockPos(10,100,1),Blocks.OAK_LEAVES.getDefaultState());
        require(FaultlineManager.surface(w,caster,new Vec3d(9.5,101,1.5),10.5,1.5)!=null,"Leaf canopy rejected");
        for(int y=101;y<105;y++)w.setBlockState(new BlockPos(10,y,2),Blocks.STONE.getDefaultState());
        require(FaultlineManager.surface(w,caster,new Vec3d(9.5,101,2.5),10.5,2.5)==null,"Tall wall not blocking wave");
    }
    private void destructionChecks(ServerWorld w) {
        require(StoneChargeManager.materialCost(Blocks.STONE.getDefaultState())>StoneChargeManager.materialCost(Blocks.OAK_PLANKS.getDefaultState()),"Stone must cost more than wood");
        require(StoneChargeManager.materialCost(Blocks.CHEST.getDefaultState())==0,"Container breakable");
        int dirt=wall(w,Blocks.DIRT),wood=wall(w,Blocks.OAK_PLANKS),stone=wall(w,Blocks.STONE);
        require(dirt>wood && wood>stone && stone>0,"Budget tiers wrong: "+dirt+" / "+wood+" / "+stone);
        for(int x=-3;x<=3;x++)for(int z=1;z<=4;z++)for(int y=120;y<=122;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        w.setBlockState(new BlockPos(0,121,1),Blocks.OBSIDIAN.getDefaultState());w.setBlockState(new BlockPos(0,121,2),Blocks.DIRT.getDefaultState());
        StoneChargeManager.breakCone(caster,new Vec3d(.5,121.5,.2),new Vec3d(0,0,1),1,121);
        require(w.getBlockState(new BlockPos(0,121,2)).isOf(Blocks.DIRT),"Destruction passed through obsidian");
        w.setBlockState(new BlockPos(0,121,1),Blocks.AIR.getDefaultState());
        var pos=new BlockPos(0,121,1);
        TemporaryBlockManager.placeTrackedTemporaryBlocks(w,List.of(pos),Blocks.DIRT.getDefaultState(),100,s->s.isAir());
        StoneChargeManager.breakCone(caster,new Vec3d(.5,121.5,.2),new Vec3d(0,0,1),1,121);
        require(w.getBlockState(pos).isOf(Blocks.DIRT),"Anonymous temporary placement destroyed");
    }
    private int wall(ServerWorld w,net.minecraft.block.Block block) {
        for(int x=-3;x<=3;x++)for(int z=1;z<=4;z++)for(int y=120;y<=122;y++)w.setBlockState(new BlockPos(x,y,z),block.getDefaultState());
        return StoneChargeManager.breakCone(caster,new Vec3d(.5,121.5,.2),new Vec3d(0,0,1),1,121);
    }
    private static ServerPlayerEntity player(MinecraftServer server,String name,double x,double y,double z)throws Exception {
        var method=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
        method.setAccessible(true);return (ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),name,x,y,z);
    }
}
