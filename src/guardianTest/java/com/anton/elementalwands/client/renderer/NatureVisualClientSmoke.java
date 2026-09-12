package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.block.NatureSeedlingBlock;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.block.Blocks;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import java.nio.file.*;
import java.util.*;

/** Disposable integrated-world visual fixture; never touches the user's worlds or Lunar profile. */
public final class NatureVisualClientSmoke {
    private int ticks,sceneTicks;
    private boolean started,arranged,done;
    private final List<LivingEntity> targets=new ArrayList<>();
    private int floor;
    public static void start(MinecraftClient client){
        var fixture=new NatureVisualClientSmoke();
        ClientTickEvents.END_CLIENT_TICK.register(fixture::tick);
        client.options.pauseOnLostFocus=false;
        client.options.hudHidden=true;
    }
    private void tick(MinecraftClient client){
        if(done)return;
        try{
            if(++ticks>2400)throw new AssertionError("Nature visual world timed out");
            if(!started){
                started=true;
                client.createIntegratedServerLoader().createAndStart("nature-visual-"+System.currentTimeMillis(),
                        new LevelInfo("Nature visual verification",GameMode.SPECTATOR,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(812L,false,false),WorldPresets::createTestOptions,null);
                return;
            }
            if(client.world==null || client.player==null || client.getServer()==null || client.getOverlay()!=null)return;
            var server=client.getServer();
            if(!arranged){
                arranged=true;
                server.execute(()->{
                    var world=server.getOverworld();
                    floor=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
                    for(int x=-16;x<=16;x++)for(int z=-16;z<=20;z++){
                        world.setBlockState(new BlockPos(x,floor-1,z),Blocks.GRASS_BLOCK.getDefaultState(),3);
                        for(int y=0;y<=10;y++)world.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState(),3);
                    }
                    world.setTimeOfDay(6000);
                    for(var cell:NatureTreeLayout.cells())world.setBlockState(new BlockPos(cell.x(),floor+cell.y(),cell.z()+10),cell.state(),3);
                    for(int stage=0;stage<4;stage++)world.setBlockState(new BlockPos(-6+stage*2,floor,0),
                            ModSpellBlocks.NATURE_SEEDLING.getDefaultState().with(NatureSeedlingBlock.STAGE,stage),3);
                    for(int x=-6;x<=0;x++)for(int z=1;z<=3;z++)world.setBlockState(new BlockPos(x,floor,z),ModSpellBlocks.NATURE_ROOTS.getDefaultState(),3);
                    for(int x=-6;x<=-3;x++)for(int z=-4;z<=-2;z++){
                        world.setBlockState(new BlockPos(x,floor-1,z),Blocks.WATER.getDefaultState(),3);
                        world.setBlockState(new BlockPos(x,floor,z),ModSpellBlocks.NATURE_RAFT.getDefaultState(),3);
                    }
                    var pig=EntityType.PIG.create(world,SpawnReason.COMMAND);pig.setPosition(4.5,floor,1.5);pig.setAiDisabled(true);world.spawnEntity(pig);targets.add(pig);
                    var spider=EntityType.SPIDER.create(world,SpawnReason.COMMAND);spider.setPosition(7.5,floor,1.5);spider.setAiDisabled(true);world.spawnEntity(spider);targets.add(spider);
                    var boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);boss.setPosition(6.5,floor,8.5);boss.stopReview();world.spawnEntity(boss);targets.add(boss);
                    var player=server.getPlayerManager().getPlayer(client.player.getUuid());
                    player.networkHandler.requestTeleport(10.5,floor+6,-12.5,26,16);
                });
                return;
            }
            sceneTicks++;
            if(sceneTicks%15==0)server.execute(()->{for(var target:targets)for(int i=0;i<5;i++)EntangleTracker.addStack(server.getOverworld(),target);});
            if(sceneTicks==120)screenshot(client,"nature-01-grove.png");
            if(sceneTicks==140)camera(client,-2.5,2.6,-5.5,0,18);
            if(sceneTicks==200)screenshot(client,"nature-02-flower.png");
            if(sceneTicks==220)camera(client,6.5,2.2,-4.5,0,9);
            if(sceneTicks==280)screenshot(client,"nature-03-entangle.png");
            if(sceneTicks==300)camera(client,12.5,5,1,35,5);
            if(sceneTicks==360)screenshot(client,"nature-04-tree.png");
            if(sceneTicks==400){
                Files.writeString(Path.of("NATURE_WORLD_PASSED.txt"),"Integrated-world rendering completed: custom flowers, roots, water rafts, block tree and synced pig/spider/Guardian wraps. Four Minecraft screenshots saved. Human combat playtest still pending.\n");
                done=true;client.scheduleStop();
            }
        }catch(Throwable error){
            done=true;error.printStackTrace();
            try{Files.writeString(Path.of("FLOOR_CLIENT_FAILED.txt"),error.toString());}catch(Exception ignored){}
            client.scheduleStop();
        }
    }
    private void camera(MinecraftClient client,double x,double y,double z,float yaw,float pitch){
        client.getServer().execute(()->client.getServer().getPlayerManager().getPlayer(client.player.getUuid())
                .networkHandler.requestTeleport(x,floor+y,z,yaw,pitch));
    }
    private void screenshot(MinecraftClient client,String name){
        ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,text->System.out.println("NATURE VISUAL: "+text.getString()));
    }
}
