package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FaultlineSpikeEntity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Native held input, movement, jump, release and spike-rendering test in a disposable world. */
public final class StoneTechniqueClientSmoke implements ClientModInitializer {
    private int ticks,scene,floor;private boolean started,arranged,done,sawSpikes,airborne,landed;
    private volatile boolean ready,chargeStarted,chargeFull,chargeEnded,wallImpact;
    private double startZ,jumpZ,jumpDistance,peak,slideStart,slideEnd;
    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient c) {
        if(done || c.getOverlay()!=null)return;
        try {
            if(++ticks>1800)throw new AssertionError("Stone client timed out");
            if(!started) {
                started=true;c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("stone-technique-"+System.currentTimeMillis(),
                        new LevelInfo("Stone techniques verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(812L,false,false),WorldPresets::createTestOptions,null);return;
            }
            if(c.world==null || c.player==null || c.getServer()==null)return;
            var server=c.getServer();var uuid=c.player.getUuid();
            if(!arranged) {
                arranged=true;
                server.execute(()->{
                    var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                    for(int x=-10;x<=10;x++)for(int z=-8;z<=110;z++) {
                        w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE.getDefaultState());
                        for(int y=floor;y<floor+5;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
                    }
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);p.setAttached(EWAttachments.AFFINITY,"STONE");
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));WandProgression.grant(p,3);
                    p.setAttached(EWAttachments.WAND_LOADOUTS,java.util.Map.of("STONE",java.util.List.of("gathered_mass","stone_wall","titan_dome","faultline","stone_charge")));
                    ModNetworking.syncPlayerData(p);p.networkHandler.requestTeleport(.5,floor,.5,0,10);ready=true;
                });return;
            }
            if(!ready)return;
            int t=++scene;
            if(t<45) { WandWelcome.reset();c.setScreen(null); }
            if(t==45){GLFW.glfwFocusWindow(c.getWindow().getHandle());WandControls.reset();}
            if(t>=45 && t<150){c.player.setYaw(0);c.player.setPitch(8);}
            if(t==50) {
                startZ=c.player.getZ();require(c.isWindowFocused(),"Window not focused");
                require(WandControls.input(false,GLFW.GLFW_KEY_V,GLFW.GLFW_PRESS),"Charge input unhandled");
            }
            if(t==60)server.execute(()->chargeStarted=StoneChargeManager.active(server.getPlayerManager().getPlayer(uuid)));
            if(t==75)require(chargeStarted && c.player.getZ()>startZ+4,"Native client charge did not move: "+c.player.getEntityPos());
            if(t==108)server.execute(()->chargeFull=StoneChargeManager.speed(server.getPlayerManager().getPlayer(uuid))>=.80);
            if(t==110){require(chargeFull,"Native charge never reached full speed");jumpZ=c.player.getZ();c.options.jumpKey.setPressed(true);}
            if(t==112)c.options.jumpKey.setPressed(false);
            if(t>=110 && t<137) {
                peak=Math.max(peak,c.player.getY()-floor);
                if(!c.player.isOnGround())airborne=true;
                if(airborne && c.player.isOnGround() && !landed){landed=true;jumpDistance=c.player.getZ()-jumpZ;}
            }
            if(t==116)screenshot(c,"stone-charge-leap.png");
            if(t==137) {
                require(airborne && landed && peak>1 && peak<1.5,"Jump height/landing changed: "+peak);
                require(jumpDistance>7 && jumpDistance<12,"Leap distance outside expected range: "+jumpDistance);
                slideStart=c.player.getZ();WandControls.input(false,GLFW.GLFW_KEY_V,GLFW.GLFW_RELEASE);
            }
            if(t==147){slideEnd=c.player.getZ();server.execute(()->chargeEnded=!StoneChargeManager.active(server.getPlayerManager().getPlayer(uuid)));}
            if(t==150) {
                require(chargeEnded,"Native release did not end charge");require(slideEnd>slideStart && slideEnd-slideStart<3,"Braking slide unbounded: "+(slideEnd-slideStart));
                server.execute(()->{var p=server.getPlayerManager().getPlayer(uuid);p.networkHandler.requestTeleport(.5,floor,.5,0,15);});
            }
            if(t>=165 && t<200){c.player.setYaw(0);c.player.setPitch(15);}
            if(t==170)WandControls.input(false,GLFW.GLFW_KEY_Z,GLFW.GLFW_PRESS);
            if(t==171)WandControls.input(false,GLFW.GLFW_KEY_Z,GLFW.GLFW_RELEASE);
            if(t>=172 && t<204) {
                for(var entity:c.world.getEntities())if(entity instanceof FaultlineSpikeEntity)sawSpikes=true;
                if(t==183)screenshot(c,"stone-faultline-wave.png");
            }
            if(t==211) {
                require(sawSpikes,"Native client never received spike models");
                for(var entity:c.world.getEntities())require(!(entity instanceof FaultlineSpikeEntity),"Spike persisted after wave");
            }
            if(t==315) server.execute(()->{
                var p=server.getPlayerManager().getPlayer(uuid);var w=p.getEntityWorld();
                p.networkHandler.requestTeleport(.5,floor,.5,0,10);
                for(int x=-2;x<=2;x++)for(int y=floor;y<floor+3;y++)w.setBlockState(new BlockPos(x,y,42),Blocks.STONE.getDefaultState());
            });
            if(t>=325 && t<425){c.player.setYaw(0);c.player.setPitch(10);}
            if(t==330)WandControls.input(false,GLFW.GLFW_KEY_V,GLFW.GLFW_PRESS);
            if(t==420) {
                WandControls.input(false,GLFW.GLFW_KEY_V,GLFW.GLFW_RELEASE);
                server.execute(()->{
                    var p=server.getPlayerManager().getPlayer(uuid);var w=p.getEntityWorld();int removed=0;
                    for(int x=-2;x<=2;x++)for(int y=floor;y<floor+3;y++)if(w.getBlockState(new BlockPos(x,y,42)).isAir())removed++;
                    wallImpact=!StoneChargeManager.active(p) && removed>0 && removed<=2
                            && w.getBlockState(new BlockPos(0,floor-1,42)).isOf(Blocks.STONE) && p.getZ()<42;
                });
            }
            if(t==430) {
                require(wallImpact,"Native full-speed collision failed its stone budget, stop position, or floor preservation");
                Files.writeString(Path.of("HUB_PASSED.txt"),"PASS: native held charge, server full-speed confirmation, normal-height long leap (height="+peak+", distance="+jumpDistance+"), release/braking, independent Faultline key, synchronized rendered spikes and crumble; native full-speed stone wall impact, limited destruction and intact floor.\n");
                done=true;c.scheduleStop();
            }
        } catch(Throwable e) {done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private static void screenshot(MinecraftClient c,String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{}); }
    private static void require(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
}
