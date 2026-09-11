package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.block.StoneSpikeBlock;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.*;
import net.minecraft.world.*;
import java.nio.file.*;
import java.util.*;

/** Reproduces primary-spell cover in the real protected arena; retains wall/tree defenses. */
public final class GuardianCoverSmokeMod implements ModInitializer {
    private int tick,phase,started,y;
    private ServerPlayerEntity player;
    private FracturedGuardianEntity boss;
    private GuardianBossCombat combat;
    private Vec3d origin;
    private BlockPos flower,near,protectedFlower,spike;
    private AwakenedTreeEntity tree;
    private final StringBuilder report=new StringBuilder();
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("COVER_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
    }
    private void run(MinecraftServer server)throws Exception {
        tick++;ServerWorld world=server.getOverworld();
        require(tick<1500,"Cover fixture timed out: "+phase+" "+GuardianArenaManager.status());
        if(tick==30) {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var factory=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"CoverTester",8.5,(double)ground,.5);player.onTeleportationDone();
            boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);boss.setPosition(.5,ground,.5);boss.stopReview();world.spawnEntity(boss);
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(boss);
            require(GuardianArenaManager.start(player,boss).startsWith("Arena sealed"),"Could not start arena");phase=1;
        }
        if(phase==1 && GuardianArenaManager.isFighting(boss)) {
            boss.stopReview();y=player.getBlockY();origin=new Vec3d(.5,y,.5);boss.setPosition(origin);boss.setVelocity(Vec3d.ZERO);
            near=new BlockPos(2,y,0);flower=new BlockPos(0,y,6);spike=new BlockPos(0,y,9);protectedFlower=new BlockPos(14,y,0);
            plant(world,near);plant(world,flower);
            Vec3d behind=new Vec3d(.5,y,12.5);
            require(!GuardianBossCombat.clearLine(bossWorld(),boss,origin.add(0,.7,0),behind.add(0,.7,0)),"Fixture does not reproduce flower shielding with old collider ray");
            require(GuardianWaveSurface.visible(world,boss,origin,behind),"Flower still shields wave");
            require(Math.abs(GuardianWaveSurface.ground(world,boss,origin,.5,6.5).y-y)<.01,"Wave rides on top of flower");
            for(int stage=0;stage<=3;stage++) {
                world.setBlockState(spike,ModSpellBlocks.STONE_SPIKE.getDefaultState().with(StoneSpikeBlock.STAGE,stage),3);
                require(GuardianWaveSurface.visible(world,boss,origin,behind),"Spike stage "+stage+" shields wave");
                require(Math.abs(GuardianWaveSurface.ground(world,boss,origin,.5,9.5).y-y)<.01,"Wave rises on spike stage "+stage);
            }
            combat.crushGrowth(world,origin,4.5,false,6);
            require(world.getBlockState(near).isAir(),"Local fist impact did not clear flower");
            for(int z=-2;z<=2;z++)for(int h=0;h<3;h++)world.setBlockState(new BlockPos(12,y+h,z),ModSpellBlocks.STONE_WALL.getDefaultState(),3);
            plant(world,protectedFlower);
            require(!GuardianWaveSurface.visible(world,boss,origin,new Vec3d(16.5,y,.5)),"Stone secondary wall lost cover");
            world.setBlockState(new BlockPos(0,y,-8),Blocks.STONE.getDefaultState(),3);
            require(!GuardianWaveSurface.visible(world,boss,origin,new Vec3d(.5,y,-12.5)),"Ordinary stone lost cover");
            world.setBlockState(new BlockPos(0,y,-8),Blocks.AIR.getDefaultState(),3);
            player.setPosition(behind);player.setHealth(20);player.timeUntilRegen=0;
            combat.emitWave(world,origin,Set.of());started=tick;phase=2;
        }
        if(phase==2 && tick-started==45) {
            require(player.getHealth()<20,"Player behind flower/spike avoided the actual wave");
            require(world.getBlockState(flower).isAir(),"Traveling wave did not uproot flower");
            require(SeedlingManager.getActiveSeedlingsForCaster(world,player.getUuid()).stream().noneMatch(s->s.anchorPos().equals(flower)),"Destroyed seedling still active");
            require(world.getBlockState(spike).isOf(ModSpellBlocks.STONE_SPIKE),"Wave deleted Stone primary instead of passing through");
            require(world.getBlockState(protectedFlower).isOf(Blocks.FLOWERING_AZALEA),"Wave erased flower behind defensive wall");
            require(world.getBlockState(new BlockPos(0,y-1,6)).isOf(ModBlocks.ARENA_LIGHT)
                    || world.getBlockState(new BlockPos(0,y-1,6)).isOf(ModBlocks.ARENA_DARK)
                    || world.getBlockState(new BlockPos(0,y-1,6)).isOf(ModBlocks.ARENA_STONE),"Flower cleanup damaged arena floor");
            report.append("Real arena: old collider reproduces flower shielding; every spike stage and flowers now share floor-level, permeable wave geometry. Local smash and traveling wave clear live seedlings; player behind primaries is hit. Primary spikes survive.\n");
            player.setPosition(16.5,y,.5);player.setHealth(20);player.timeUntilRegen=0;
            combat.emitWave(world,origin,Set.of());started=tick;phase=3;
        }
        if(phase==3 && tick-started==45) {
            require(player.getHealth()==20,"Stone wall no longer protects player from actual wave");
            require(world.getBlockState(protectedFlower).isOf(Blocks.FLOWERING_AZALEA),"Wall no longer protects plants");
            report.append("Stone secondary wall still blocks actual wave damage and protects growth behind it; ordinary stone remains cover.\n");
            OvergrowthManager.startOvergrowth(world,player,new BlockPos(-8,y,0),1);
            tree=world.getEntitiesByClass(AwakenedTreeEntity.class,new Box(new BlockPos(-8,y,0)).expand(6),e->e.isAlive()).getFirst();
            started=tick;phase=4;
        }
        if(phase==4 && tick-started==50) {
            require(!GuardianWaveSurface.visible(world,boss,origin,new Vec3d(-16.5,y,.5)),"Real Nature ultimate tree lost cover");
            player.setPosition(-16.5,y,.5);player.setHealth(20);player.timeUntilRegen=0;
            combat.emitWave(world,origin,Set.of());started=tick;phase=5;
        }
        if(phase==5 && tick-started==45) {
            require(player.getHealth()==20 && tree.isAlive(),"Nature ultimate tree failed to shield player/survive wave");
            report.append("Actual grown Nature ultimate remains alive and shields the player from the wave.\n");
            OvergrowthManager.destroyTree(world,tree);boss.stopReview();GuardianArenaManager.stop();phase=6;
        }
        if(phase==6 && !GuardianArenaManager.hasActiveArena()) {
            Files.writeString(Path.of("COVER_PASSED.txt"),report);System.out.println("GUARDIAN COVER PASSED\n"+report);server.stop(false);
        }
    }
    private ServerWorld bossWorld(){return (ServerWorld)boss.getEntityWorld();}
    private void plant(ServerWorld world,BlockPos pos){require(SeedlingManager.tryPlantSeedling(world,player,new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0,.5,0),Direction.UP,pos.down(),false)),"Could not plant "+pos);}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
