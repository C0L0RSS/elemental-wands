package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.registry.ModEntities;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real client screenshot and synchronized burn-state check in a fresh disposable world. */
public final class GuardianBurnVisualClientSmoke {
    private int ticks,sceneTicks,floor;private boolean started,arranged,done;
    private volatile int bossId;private volatile FracturedGuardianEntity boss;
    public static void start(MinecraftClient client) {
        var fixture=new GuardianBurnVisualClientSmoke();
        ClientTickEvents.END_CLIENT_TICK.register(fixture::tick);
        client.options.pauseOnLostFocus=false;client.options.hudHidden=true;
    }
    private void tick(MinecraftClient client) {
        if(done)return;
        try {
            if(++ticks>2000)throw new AssertionError("Guardian burn visual world timed out");
            if(!started) {
                started=true;
                client.createIntegratedServerLoader().createAndStart("guardian-burn-"+System.currentTimeMillis(),
                        new LevelInfo("Guardian burn verification",GameMode.SPECTATOR,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(812L,false,false),WorldPresets::createTestOptions,null);
                return;
            }
            if(client.world==null || client.player==null || client.getServer()==null || client.getOverlay()!=null)return;
            var server=client.getServer();
            if(!arranged) {
                arranged=true;
                server.execute(()->{
                    var world=server.getOverworld();floor=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
                    for(int x=-12;x<=12;x++)for(int z=-14;z<=12;z++) {
                        world.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                        for(int y=0;y<12;y++)world.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());
                    }
                    world.setTimeOfDay(6000);
                    boss=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
                    boss.setPosition(.5,floor,.5);boss.setYaw(180);boss.setBodyYaw(180);boss.setHeadYaw(180);
                    boss.stopReview();world.spawnEntity(boss);bossId=boss.getId();boss.setFireTicks(600);
                    server.getPlayerManager().getPlayer(client.player.getUuid()).networkHandler.requestTeleport(.5,floor+3,-11.5,0,0);
                });return;
            }
            int scene=++sceneTicks;
            if(scene==90) {
                var entity=client.world.getEntityById(bossId);
                if(!(entity instanceof FracturedGuardianEntity guardian) || !guardian.isOnFire())throw new AssertionError("Burn state did not synchronize");
                var renderer=(FracturedGuardianRenderer)client.getEntityRenderDispatcher().getRenderer(guardian);
                var state=renderer.createRenderState(guardian,null);renderer.updateRenderState(guardian,state,0);
                if(!state.burning || state.onFire || Math.abs(state.width-guardian.getWidth())>.001 || Math.abs(state.height-guardian.getHeight())>.001)
                    throw new AssertionError("Live Guardian did not suppress only the automatic fire overlay");
                screenshot(client,"guardian-burning-front.png");
            }
            if(scene==110)server.execute(()->server.getPlayerManager().getPlayer(client.player.getUuid())
                    .networkHandler.requestTeleport(8.5,floor+3,-8.5,42,0));
            if(scene==150)screenshot(client,"guardian-burning-angle.png");
            if(scene==170)server.execute(()->boss.extinguish());
            if(scene==200) {
                var guardian=(FracturedGuardianEntity)client.world.getEntityById(bossId);
                var renderer=(FracturedGuardianRenderer)client.getEntityRenderDispatcher().getRenderer(guardian);
                var state=renderer.createRenderState(guardian,null);renderer.updateRenderState(guardian,state,0);
                if(state.burning || state.onFire)throw new AssertionError("Live extinguished Guardian retained fire cue");
                screenshot(client,"guardian-extinguished.png");
            }
            if(scene==225) {
                Files.writeString(Path.of("GUARDIAN_BURN_WORLD_PASSED.txt"),"Live Guardian fire synchronization, render-only suppression, unchanged render dimensions, extinguish cleanup, and three real Minecraft screenshots passed. Human combat readability remains a playtest check.\n");
                done=true;client.scheduleStop();
            }
        } catch(Throwable error) {
            done=true;error.printStackTrace();try{Files.writeString(Path.of("FLOOR_CLIENT_FAILED.txt"),error.toString());}catch(Exception ignored){}
            client.scheduleStop();
        }
    }
    private void screenshot(MinecraftClient client,String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory,name,client.getFramebuffer(),1,text->System.out.println("GUARDIAN BURN VISUAL: "+text.getString()));
    }
}
