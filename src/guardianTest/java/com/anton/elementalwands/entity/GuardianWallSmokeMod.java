package com.anton.elementalwands.entity;

import com.anton.elementalwands.item.StoneAbilityHandler;
import com.anton.elementalwands.registry.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual wall casts and Guardian attacks in an isolated server world. */
public final class GuardianWallSmokeMod implements ModInitializer {
    private int tick;
    private ServerPlayerEntity player,other;
    private FracturedGuardianEntity boss;
    private GuardianBossCombat combat;
    private GuardianBeamAttack beam;
    private List<BlockPos> wall=List.of();
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch(Throwable e) {
                e.printStackTrace();try { Files.writeString(Path.of("WALL_FAILED.txt"),e.toString()); }catch(Exception ignored){}
                server.stop(false);
            }
        });
    }
    private void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private void resetPlayer(ServerPlayerEntity p,double z) {
        p.setPosition(.5,81,z);p.setVelocity(Vec3d.ZERO);p.setHealth(200);p.timeUntilRegen=0;p.clearStatusEffects();
    }
    private void wall(ServerWorld world,double z) {
        resetPlayer(player,z);resetPlayer(other,z+1);
        player.setYaw(180);player.setHeadYaw(180);player.setBodyYaw(180);player.setPitch(0);
        StoneAbilityHandler.castSecondary(world,player,new ItemStack(ModItems.FRACTURED_WAND));
        wall=StoneAbilityHandler.guardianWallBlocks(world);
        require(wall.stream().allMatch(p -> p.getZ()<player.getZ()),"Fixture placed wall behind caster");
        require(wall.size()==12,"Wall fixture did not place 12 blocks: "+wall.size());
    }
    private void gone(ServerWorld world,String attack) {
        require(wall.stream().noneMatch(p -> world.getBlockState(p).isOf(ModSpellBlocks.STONE_WALL)),attack+" did not break entire wall");
        require(StoneAbilityHandler.guardianWallBlocks(world).isEmpty(),attack+" left active wall record");
    }
    private void safe(String attack) {
        require(player.getHealth()==200 && other.getHealth()==200,attack+" leaked through absorbed wall: "+player.getHealth()+" / "+other.getHealth());
    }
    private void rock(ServerWorld world) {
        var rock=new GuardianRockEntity(ModEntities.GUARDIAN_ROCK,world);
        rock.setOwner(boss);rock.setPosition(.5,82,5.5);rock.release(new Vec3d(.5,82,10.5));rock.setVelocity(0,0,1);
        for(int i=0;i<8 && !rock.isRemoved();i++)rock.tick();
        require(rock.isRemoved(),"Rock never impacted");
    }
    private void run(MinecraftServer server)throws Exception {
        tick++;ServerWorld world=server.getOverworld();require(tick<450,"Wall fixture timed out");
        if(player!=null) { player.playerTick();other.playerTick(); }
        if(beam!=null)beam.tick(world);
        if(tick==30) {
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunk(x,z);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=25;z++)world.setBlockState(new BlockPos(x,80,z),Blocks.STONE.getDefaultState());
            var factory=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"WallCaster",.5,81.,10.5);
            other=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"WallAlly",.5,81.,11.5);
            for(var p:List.of(player,other)) {
                p.onTeleportationDone();p.setLoaded(true);p.setNoGravity(true);p.getHungerManager().setFoodLevel(10);
                p.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);p.setHealth(200);
            }
            boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
            boss.setPosition(.5,81,.5);boss.setNoGravity(true);boss.stopReview();world.spawnEntity(boss);
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(boss);
            wall(world,10.5);combat.emitWave(world,boss.getEntityPos(),Set.of());
        }
        if(tick==65) {
            gone(world,"Wave");safe("Entire first traveling wave");
            combat.clearWaves();combat.emitWave(world,boss.getEntityPos(),Set.of());
        }
        if(tick==100) {
            require(player.getHealth()<200 && other.getHealth()<200,"Following wave stayed blocked by vanished wall");combat.clearWaves();
            wall(world,10.5);beam=new GuardianBeamAttack(boss);beam.begin(player);
        }
        if(tick==150) {
            gone(world,"Beam");safe("Entire 12-tick beam pulse");beam.cancel();beam.begin(player);
        }
        if(tick==200) {
            require(player.getHealth()<200,"Following beam stayed blocked by vanished wall");beam.cancel();beam=null;
            wall(world,10.5);rock(world);gone(world,"Rock");safe("Rock splash");
            rock(world);require(player.getHealth()<200,"Following rock stayed blocked by vanished wall");
        }
        if(tick==205) {
            wall(world,3.3);combat.leapLanded(world,new HashSet<>());gone(world,"Leap landing");safe("Leap landing");
            combat.leapLanded(world,new HashSet<>());require(player.getHealth()<200,"Next leap impact stayed blocked");
        }
        if(tick==210) {
            wall(world,10.5);
            // Natural cover in front stops the wave before it touches a player wall.
            for(int x=-4;x<=4;x++)for(int y=81;y<84;y++)world.setBlockState(new BlockPos(x,y,5),Blocks.STONE.getDefaultState());
            combat.emitWave(world,boss.getEntityPos(),Set.of());
        }
        if(tick==245) {
            require(wall.stream().allMatch(p -> world.getBlockState(p).isOf(ModSpellBlocks.STONE_WALL)),"Wave destroyed wall through natural cover");safe("Natural cover");
            require(StoneAbilityHandler.breakWallFromGuardian(world,new BlockPos(0,81,5)).isEmpty(),"Natural stone was treated as spell wall");
            combat.clearWaves();
            StoneAbilityHandler.breakWallFromGuardian(world,wall.getFirst());gone(world,"Tracked cleanup");
            for(int x=-4;x<=4;x++)for(int y=81;y<84;y++)require(world.getBlockState(new BlockPos(x,y,5)).isOf(Blocks.STONE),"Natural cover was damaged");
            wall(world,10.5);
            List<BlockPos> firstWall=List.copyOf(wall);
            other.setPosition(2.5,81,10.5);other.setYaw(180);other.setHeadYaw(180);other.setBodyYaw(180);other.setPitch(0);
            StoneAbilityHandler.castSecondary(world,other,new ItemStack(ModItems.FRACTURED_WAND));
            require(StoneAbilityHandler.guardianWallBlocks(world).size()==18,"Overlapping wall ownership contains duplicate or missing blocks");
            StoneAbilityHandler.breakWallFromGuardian(world,new BlockPos(3,82,8));
            require(firstWall.stream().allMatch(p -> world.getBlockState(p).isOf(ModSpellBlocks.STONE_WALL)),"Breaking adjacent wall consumed another caster's wall");
            require(StoneAbilityHandler.guardianWallBlocks(world).size()==12,"Adjacent wall left stale ownership");
            StoneAbilityHandler.breakWallFromGuardian(world,firstWall.getFirst());
            gone(world,"Adjacent wall cleanup");
            for(int x=-8;x<=8;x++)for(int z=-8;z<=25;z++)require(world.getBlockState(new BlockPos(x,80,z)).isOf(Blocks.STONE),"Breaking wall damaged original floor");
            Files.writeString(Path.of("WALL_PASSED.txt"),"Actual wall casts: first wave, entire beam pulse, rock splash and leap landing absorbed; whole wall and record removed; following hit reaches player. Two players protected consistently. Natural stone and walls behind it preserved. Overlapping casts retain separate owners; original floor survives.\n");
            System.out.println("GUARDIAN WALL CHECKS PASSED");server.stop(false);
        }
    }
}
