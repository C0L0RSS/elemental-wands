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

/** Reproduces primary-spell cover on the real protected nave floor; retains wall/tree defenses. */
public final class GuardianCoverSmokeMod implements ModInitializer {
    private int tick,phase,started,y;
    private ServerPlayerEntity player;
    private FracturedGuardianEntity boss;
    private GuardianBossCombat combat;
    private Vec3d origin;
    private BlockPos spike;
    private AwakenedTreeEntity tree;
    private final StringBuilder report=new StringBuilder();
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("COVER_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
    }
    private void run(MinecraftServer server)throws Exception {
        tick++;ServerWorld world=player!=null && GuardianArenaManager.inRealm(player)?(ServerWorld)player.getEntityWorld():server.getOverworld();
        require(tick<2000,"Cover fixture timed out: "+phase+" "+GuardianArenaManager.status());
        if(tick==30) {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var factory=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"CoverTester",8.5,(double)ground,.5);player.onTeleportationDone();
            // Slot 0 of a fresh nave is centred on x=0, z=0, like the fixture's authored positions.
            GuardianArenaManager.enter(player,0);phase=10;
        }
        if(phase==10 && GuardianArenaManager.inRealm(player)) {
            // A real client confirms the move into the nave; until then a player cannot be hurt.
            player.onTeleportationDone();
            require(GuardianArenaManager.summon(player).startsWith("The Guardian wakes"),"Could not summon the nave's Guardian");
            // Simulated players do not keep chunks ticking as a real client does; hold the floor loaded like the Overworld's.
            for(int x=-5;x<=4;x++)for(int z=-5;z<=4;z++)((ServerWorld)player.getEntityWorld()).setChunkForced(x,z,true);phase=11;
        }
        if(phase==11) {
            boss=world.getEntitiesByClass(FracturedGuardianEntity.class,player.getBoundingBox().expand(80),e->e.isAlive()).stream().findFirst().orElse(null);
            // The nave fixture plays the intro; here it is skipped.
            if(boss!=null && boss.inIntro()) GuardianIntro.skip(player);
            if(boss!=null && GuardianArenaManager.isFighting(boss)) {
                var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(boss);phase=1;
            }
        }
        if(phase==1 && GuardianArenaManager.isFighting(boss)) {
            boss.stopReview();y=player.getBlockY();origin=new Vec3d(.5,y,.5);boss.setPosition(origin);boss.setVelocity(Vec3d.ZERO);
            spike=new BlockPos(0,y,9);
            Vec3d behind=new Vec3d(.5,y,12.5);
            for(int stage=0;stage<=3;stage++) {
                world.setBlockState(spike,ModSpellBlocks.STONE_SPIKE.getDefaultState().with(StoneSpikeBlock.STAGE,stage),3);
                require(GuardianWaveSurface.visible(world,boss,origin,behind),"Spike stage "+stage+" shields wave");
                require(Math.abs(GuardianWaveSurface.ground(world,boss,origin,.5,9.5).y-y)<.01,"Wave rises on spike stage "+stage);
            }
            for(int z=-2;z<=2;z++)for(int h=0;h<3;h++)world.setBlockState(new BlockPos(12,y+h,z),ModSpellBlocks.STONE_WALL.getDefaultState(),3);
            require(!GuardianWaveSurface.visible(world,boss,origin,new Vec3d(16.5,y,.5)),"Stone secondary wall lost cover");
            world.setBlockState(new BlockPos(0,y,-8),Blocks.STONE.getDefaultState(),3);
            require(!GuardianWaveSurface.visible(world,boss,origin,new Vec3d(.5,y,-12.5)),"Ordinary stone lost cover");
            world.setBlockState(new BlockPos(0,y,-8),Blocks.AIR.getDefaultState(),3);
            player.setPosition(behind);player.setHealth(20);player.timeUntilRegen=0;
            combat.emitWave(world,origin,Set.of());started=tick;phase=2;
        }
        if(phase==2 && tick-started==45) {
            require(player.getHealth()<20,"Player behind a spike avoided the actual wave");
            require(world.getBlockState(spike).isOf(ModSpellBlocks.STONE_SPIKE),"Wave deleted Stone primary instead of passing through");
            report.append("Real nave floor: every spike stage shares floor-level, permeable wave geometry; the player behind a primary spike is hit and the spike survives.\n");
            player.setPosition(16.5,y,.5);player.setHealth(20);player.timeUntilRegen=0;
            combat.emitWave(world,origin,Set.of());started=tick;phase=3;
        }
        if(phase==3 && tick-started==45) {
            require(player.getHealth()==20,"Stone wall no longer protects player from actual wave");
            report.append("Stone secondary wall still blocks actual wave damage; ordinary stone remains cover.\n");
            OvergrowthManager.startOvergrowth(world,player,new BlockPos(-8,y,0));
            tree=world.getEntitiesByClass(AwakenedTreeEntity.class,new Box(new BlockPos(-8,y,0)).expand(6),e->e.isAlive()).getFirst();
            started=tick;phase=4;
        }
        if(phase==4 && tick-started==50) {
            // The oak's trunk is cover under the same rules as terrain or a Stone Wall.
            require(!GuardianWaveSurface.visible(world,boss,origin,new Vec3d(-16.5,y,.5)),"Real Nature ultimate tree lost cover");
            player.setPosition(-16.5,y,.5);player.setHealth(20);player.timeUntilRegen=0;
            combat.emitWave(world,origin,Set.of());started=tick;phase=5;
        }
        if(phase==5 && tick-started==45) {
            require(player.getHealth()==20,"Nature ultimate tree failed to shield the player from the wave");
            require(tree.isAlive() && tree.getHealth()==tree.getMaxHealth(),"The wave damaged the Nature ultimate tree");
            report.append("Actual grown Nature ultimate shields the player from the wave and is untouched by it.\n");
            OvergrowthManager.destroyTree(world,tree);boss.stopReview();GuardianArenaManager.leave(player);phase=6;
        }
        if(phase==6 && !GuardianArenaManager.status().contains("fight:")) {
            Files.writeString(Path.of("COVER_PASSED.txt"),report);System.out.println("GUARDIAN COVER PASSED\n"+report);server.stop(false);
        }
    }
    private ServerWorld bossWorld(){return (ServerWorld)boss.getEntityWorld();}
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
