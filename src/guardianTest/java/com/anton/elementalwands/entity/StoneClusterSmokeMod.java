package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.item.StoneAbilityHandler;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.StoneClusterManager;
import com.anton.elementalwands.util.StoneClusterRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.nio.file.*;
import java.util.UUID;

/** Real casts, damage hooks, packet construction, water flight and collision in a disposable world. */
public final class StoneClusterSmokeMod implements ModInitializer {
    private int tick,hitAt=-1;
    private ServerPlayerEntity caster,victim;
    private ItemStack wand;
    private float health;
    private boolean heavyHit,waterHit;
    private StoneClusterEntity blocked;
    private static void require(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch(Throwable error) { error.printStackTrace();try{Files.writeString(Path.of("STONE_FAILED.txt"),error.toString());}catch(Exception ignored){}server.stop(false); }
        });
    }
    private void cast(ServerWorld world) { StoneAbilityHandler.castPrimary(world,caster,wand); }
    private void run(MinecraftServer server) throws Exception {
        tick++;ServerWorld world=server.getOverworld();
        require(tick<400,"Stone fixture timed out");
        if(caster!=null){caster.playerTick();victim.playerTick();}
        if(tick==30) {
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunk(x,z);
            for(int x=-5;x<=10;x++)for(int z=-5;z<=22;z++)world.setBlockState(new BlockPos(x,80,z),Blocks.STONE.getDefaultState());
            caster=player(server,"StoneCaster",.5,81,.5);victim=player(server,"StoneTarget",.5,81,12.5);
            caster.setNoGravity(true);victim.setNoGravity(true);
            caster.onTeleportationDone();victim.onTeleportationDone();
            caster.setLoaded(true);victim.setLoaded(true);
            caster.getHungerManager().setFoodLevel(10);victim.getHungerManager().setFoodLevel(10);
            victim.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);victim.setHealth(200);
            caster.setAttached(EWAttachments.AFFINITY,"STONE");
            wand=new ItemStack(ModItems.FRACTURED_WAND);caster.equipStack(EquipmentSlot.MAINHAND,wand);
            caster.setYaw(0);caster.setPitch(65);cast(world);
            require(StoneClusterManager.mass(caster)==25,"Ground click did not gather 25 material");
            require(!StoneClusterManager.held(caster).shouldSave(),"Held cluster would survive world reload");
            cast(world);require(StoneClusterManager.mass(caster)==25,"Click spam bypassed gathering interval");
        }
        if(tick==42){cast(world);require(StoneClusterManager.mass(caster)==50,"Second gather failed");}
        if(tick==43) {
            caster.setHealth(20);caster.setAbsorptionAmount(0);
            require(caster.damage(world,world.getDamageSources().genericKill(),4),"Damage fixture rejected");
            require(StoneClusterManager.mass(caster)==38,"Real health damage did not shed proportional material");
            caster.damage(world,world.getDamageSources().genericKill(),1);
            require(StoneClusterManager.mass(caster)==38,"Rapid damage bypassed chip grace");caster.setHealth(20);
        }
        if(tick==54 || tick==66 || tick==78)cast(world);
        if(tick==79) {
            require(StoneClusterManager.mass(caster)==100,"Reserve did not reach cap");
            victim.setPosition(.5,81,1.2);
            require(StoneClusterManager.gatheringTarget(world,caster)==null,"Close downward target was mistaken for gathering ground");
            victim.setPosition(.5,81,12.5);
            require(Math.abs(caster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)
                    .getModifier(StoneClusterManager.WEIGHT).value()+.25)<1e-6,"Wrong maximum weight");
            for(int x=-5;x<=10;x++)for(int z=-5;z<=22;z++)require(world.getBlockState(new BlockPos(x,80,z)).isOf(Blocks.STONE),"Gathering damaged floor");
        }
        if(tick==90){caster.setPitch(-14);victim.setPosition(.5,84,12.5);victim.setOnGround(false);victim.setSprinting(true);health=victim.getHealth();cast(world);require(StoneClusterManager.mass(caster)==0,"Throw did not spend reserve");require(caster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getModifier(StoneClusterManager.WEIGHT)==null,"Throw left weight attached");}
        if(tick==91){cast(world);require(StoneClusterManager.remaining(caster)==49,"Spent heavy shot switched to small cooldown");}
        if(tick>90 && tick<135 && victim.getHealth()<health && !heavyHit) {
            heavyHit=true;hitAt=tick;
            require(Math.abs(health-victim.getHealth()-32)<.01,"Wrong maximum direct damage");
            require(!victim.isSprinting() && victim.getVelocity().y<0,"Heavy airborne hit did not stagger/downward-shove: sprint="+victim.isSprinting()+" ground="+victim.isOnGround()+" velocity="+victim.getVelocity());
            victim.setSprinting(true);require(!victim.isSprinting(),"Sprint reactivation escaped stagger");
        }
        if(hitAt>0 && tick==hitAt+12){victim.setSprinting(true);require(victim.isSprinting(),"Stagger did not expire");victim.setSprinting(false);}
        if(tick==140) {
            require(heavyHit,"Charged throw missed stationary visible target");
            victim.setPosition(.5,81,12.5);victim.setVelocity(Vec3d.ZERO);victim.timeUntilRegen=0;
            for(int z=4;z<=9;z++)for(int y=81;y<=84;y++)world.setBlockState(new BlockPos(0,y,z),Blocks.WATER.getDefaultState());
            health=victim.getHealth();caster.setPosition(.5,81,.5);caster.setPitch(0);cast(world);
            require(StoneClusterManager.remaining(caster)==30,"Small projectile cooldown is not 1.5 seconds");
        }
        if(tick>140 && tick<170 && victim.getHealth()<health)waterHit=true;
        if(tick==171) {
            require(waterHit && Math.abs(health-victim.getHealth()-5)<.01,"Small rock failed to cross water for exactly 5 damage: hit="+waterHit+" delta="+(health-victim.getHealth())+" pos="+victim.getEntityPos());
            caster.setPosition(6.5,81,.5);victim.setPosition(6.5,87,12.5);victim.setVelocity(Vec3d.ZERO);
            caster.setPitch(-27);victim.timeUntilRegen=0;health=victim.getHealth();cast(world);
        }
        if(tick==201) {
            require(victim.getHealth()<health,"Aimed rock could not hit elevated target");
            caster.setPosition(6.5,81,.5);caster.setPitch(0);victim.setPosition(6.5,81,12.5);victim.setVelocity(Vec3d.ZERO);
            for(int x=4;x<=8;x++)for(int y=81;y<=89;y++)world.setBlockState(new BlockPos(x,y,5),Blocks.STONE.getDefaultState());
            health=victim.getHealth();cast(world);
        }
        if(tick==231) {
            require(victim.getHealth()==health,"Rock passed through solid wall");
            caster.setPitch(65);cast(world);require(StoneClusterManager.mass(caster)==25,"Could not gather after cooldown");
            StoneClusterManager.onDamage(caster,1000);
            require(StoneClusterManager.mass(caster)==1,"Damage destroyed the entire reserve");
            StoneClusterManager.clear(caster);
            require(StoneClusterManager.mass(caster)==0 && caster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getModifier(StoneClusterManager.WEIGHT)==null,"Cleanup leaked cluster/weight");
            blocked=new StoneClusterEntity(ModEntities.STONE_CLUSTER,world);blocked.hold(caster);blocked.gather(100,0,caster.getBlockPos().down());
            blocked.setPosition(6.5,84,.5);world.setBlockState(new BlockPos(6,84,0),Blocks.STONE.getDefaultState());
            blocked.release(caster,100);world.spawnEntity(blocked);
        }
        if(tick==234) {
            checkVegetation(world);
            require(blocked.isRemoved(),"Embedded overhead release escaped ceiling collision");
            require(StoneClusterRules.afterDamage(25,0)==25 && StoneClusterRules.afterDamage(25,1000)==1,"Reserve erosion edge cases failed");
        }
        if(tick==238) {
            cast(world);require(StoneClusterManager.mass(caster)==25,"Death fixture could not regather");
            var held=StoneClusterManager.held(caster);
            caster.damage(world,world.getDamageSources().genericKill(),1000);
            require(StoneClusterManager.mass(caster)==0 && held.isRemoved(),"Actual death did not remove held cluster");
            require(caster.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getModifier(StoneClusterManager.WEIGHT)==null,"Actual death leaked weight modifier");
            Files.writeString(Path.of("STONE_PASSED.txt"),"Real-server gathering, anti-spam, proportional health erosion, chip grace, non-destruction, weight cleanup, heavy cooldown, 32 damage, airborne stagger/sprint expiry, 5-damage water shot, elevated target, solid cover, embedded launch and lifecycle checks passed.\n");
            System.out.println("STONE CLUSTER SERVER CHECKS PASSED");server.stop(false);
        }
    }
    private void checkVegetation(ServerWorld world) {
        var obstacle=new BlockPos(-2,92,18);
        var plants=new net.minecraft.block.Block[]{Blocks.SHORT_GRASS,Blocks.TALL_GRASS,
                Blocks.DANDELION,Blocks.POPPY,Blocks.FERN,Blocks.AZALEA,Blocks.FLOWERING_AZALEA,
                Blocks.MOSS_CARPET,Blocks.LILY_PAD,Blocks.SWEET_BERRY_BUSH};
        for(var block:plants)for(int mass:new int[]{0,100})for(boolean embedded:new boolean[]{false,true}) {
            world.setBlockState(obstacle,block.getDefaultState(),2);
            var shot=new StoneClusterEntity(ModEntities.STONE_CLUSTER,world);
            // Full mass only grazes the plant with its outer edge; small rocks cross its centre.
            shot.setPosition(mass==100?-2.5:-1.5,92.05,embedded?18.5:16.5);
            shot.release(caster,mass);shot.setVelocity(0,0,.9);
            for(int step=0;step<5 && !shot.isRemoved();step++)shot.tick();
            require(!shot.isRemoved(),"Vegetation stopped mass="+mass+" embedded="+embedded+" block="+block);
            require(world.getBlockState(obstacle).isOf(block),"Shot destroyed vegetation");shot.discard();
        }
        for(var block:new net.minecraft.block.Block[]{Blocks.STONE,Blocks.OAK_LOG,Blocks.OAK_LEAVES,
                Blocks.STONE_SLAB,Blocks.OAK_FENCE,com.anton.elementalwands.registry.ModSpellBlocks.STONE_WALL}) {
            world.setBlockState(obstacle,block.getDefaultState(),2);
            var shot=new StoneClusterEntity(ModEntities.STONE_CLUSTER,world);
            shot.setPosition(-1.5,92.2,16.5);shot.release(caster,100);shot.setVelocity(0,0,.9);
            for(int step=0;step<5 && !shot.isRemoved();step++)shot.tick();
            require(shot.isRemoved(),"Solid cover failed to stop rock: "+block);
        }
        world.setBlockState(obstacle,Blocks.AIR.getDefaultState(),2);
    }
    private static ServerPlayerEntity player(MinecraftServer server,String name,double x,double y,double z)throws Exception {
        var method=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
        method.setAccessible(true);return (ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),name,x,y,z);
    }
}
