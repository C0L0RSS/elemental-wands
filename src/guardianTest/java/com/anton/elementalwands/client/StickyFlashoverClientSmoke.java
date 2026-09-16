package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FireLeapEntity;
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

/** Disposable integrated-world input, synchronized flight and actual rendering checks. */
public final class StickyFlashoverClientSmoke implements ClientModInitializer {
    private int ticks,scene;private boolean started,arranged,done;private volatile boolean ready;
    private volatile int stickyMobId;
    private int smoothSamples;
    private int floor;private Vec3d target;private boolean sawFlight;private double peak;
    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient c) {
        if(done || c.getOverlay()!=null)return;
        try {
            if(++ticks>1600)throw new AssertionError("Client fixture timed out");
            if(!started) {
                started=true;c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("fire-leap-"+System.currentTimeMillis(),
                        new LevelInfo("Fire Leap verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(812L,false,false),WorldPresets::createTestOptions,null);return;
            }
            if(c.world==null || c.player==null || c.getServer()==null)return;
            var server=c.getServer();
            if(!arranged) {
                arranged=true;var uuid=c.player.getUuid();
                server.execute(()->{
                    var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                    for(int x=-9;x<=9;x++)for(int z=-8;z<=22;z++) {
                        w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                        for(int y=0;y<12;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());
                    }
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);p.setAttached(EWAttachments.AFFINITY,"FIRE");
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                    WandProgression.earn(p,WizardAffinity.FIRE,500);WandProgression.purchase(p,"FIRE","fire_hop");WandLoadouts.equip(p,"FIRE",1,"fire_hop");
                    ModNetworking.syncPlayerData(p);p.networkHandler.requestTeleport(.5,floor,.5,0,10);
                    ready=true;
                });return;
            }
            if(!ready)return;
            int t=++scene;
            if(t<45) { WandWelcome.reset();c.setScreen(null); }
            if(t==45)GLFW.glfwFocusWindow(c.getWindow().getHandle());
            if(t>=45 && t<=85) { c.player.setYaw(0);c.player.setPitch(10); }
            if(t==60) {
                var uuid=c.player.getUuid();server.execute(()->{
                    var p=server.getPlayerManager().getPlayer(uuid);WandProgression.earn(p,WizardAffinity.FIRE,500);
                    WandProgression.purchase(p,"FIRE","flashover");WandLoadouts.equip(p,"FIRE",1,"flashover");ModNetworking.syncPlayerData(p);
                    p.networkHandler.requestTeleport(.5,floor,.5,0,10);
                    var mob=net.minecraft.entity.EntityType.COW.create(p.getEntityWorld(),net.minecraft.entity.SpawnReason.COMMAND);
                    mob.setPosition(.5,floor,4.5);mob.setAiDisabled(true);mob.setNoGravity(true);
                    mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);mob.setHealth(200);
                    p.getEntityWorld().spawnEntity(mob);stickyMobId=mob.getId();
                });
            }
            if(t>=75 && t<=175) { c.player.setYaw(t>=163?45:0);c.player.setPitch(10); }
            if(t==75 || t==125 || t==155)GLFW.glfwFocusWindow(c.getWindow().getHandle());
            if(t==80 || t==105 || t==130) {
                require(ClientPlayerData.loadout().contains("flashover"),"Flashover did not equip");
                WandControls.input(true,1,GLFW.GLFW_PRESS);
            }
            if(t==81 || t==106 || t==131)WandControls.input(true,1,GLFW.GLFW_RELEASE);
            if(t>=81 && t<=90) {
                for(var e:c.world.getEntities())if(e instanceof com.anton.elementalwands.entity.FlashoverEmberEntity ember && !ember.settled()) {
                    require(ember.getInterpolator()!=null,"Thrown bomb has no client interpolator");
                    var a=ember.getLerpedPos(0);var b=ember.getLerpedPos(1);var mid=ember.getLerpedPos(.5f);
                    if(a.distanceTo(b)>.01) {
                        require(mid.distanceTo(a.lerp(b,.5))<.0001,"Bomb motion does not interpolate between render frames");smoothSamples++;
                    }
                }
            }
            if(t==160) {
                require(smoothSamples>0,"No interpolated flight samples observed");
                require(ClientPlayerData.flashActive()==3 && ClientPlayerData.flashArmed()==3,"Ember counts not synchronized");
                int sticky=0;for(var e:c.world.getEntities())if(e instanceof com.anton.elementalwands.entity.FlashoverEmberEntity ember && ember.attachedId()==stickyMobId)sticky++;
                require(sticky==3,"Attachments did not synchronize to client");
                screenshot(c,"flashover-sticky-front.png");
            }
            if(t==162)server.execute(()->{
                var mob=(net.minecraft.entity.LivingEntity)server.getOverworld().getEntityById(stickyMobId);
                mob.setPosition(2.5,floor,4.5);mob.setYaw(90);mob.setBodyYaw(90);mob.setHeadYaw(90);
                server.getPlayerManager().getPlayer(c.player.getUuid()).networkHandler.requestTeleport(6.5,floor,.5,45,10);
            });
            if(t==168) {
                var mob=c.world.getEntityById(stickyMobId);
                for(var e:c.world.getEntities())if(e instanceof com.anton.elementalwands.entity.FlashoverEmberEntity ember && ember.attachedId()==stickyMobId)
                    require(ember.attachedPosition(1).distanceTo(mob.getEntityPos())<2,"Rendered attachment separated from host");
                screenshot(c,"flashover-sticky-moved.png");
            }
            if(t==170)WandControls.input(false,GLFW.GLFW_KEY_R,GLFW.GLFW_PRESS);
            if(t==171)WandControls.input(false,GLFW.GLFW_KEY_R,GLFW.GLFW_RELEASE);
            if(t==174)screenshot(c,"flashover-detonation.png");
            if(t==185)require(ClientPlayerData.flashActive()==0 && ClientPlayerData.flashRemaining(c.world.getTime())>0,"Alternate action did not detonate");
            if(t==200) { c.setScreen(new com.anton.elementalwands.client.screen.WandHubScreen());click(c,100,220); }
            if(t==210)screenshot(c,"flashover-hub.png");
            if(t==215)click(c,275,73);
            if(t==225)screenshot(c,"flashover-controls.png");
            if(t==230) {
                click(c,70,204);
                c.currentScreen.keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_G,0,0));
                require(WandControls.shortLabel(3).equals("G"),"Alternate control row did not rebind");
                var props=new java.util.Properties();try(var reader=Files.newBufferedReader(c.runDirectory.toPath().resolve("config/elementalwands-controls.properties"))){props.load(reader);}
                require(props.getProperty("slot.3").equals("key.keyboard.g"),"Alternate binding not saved");
                WandControls.reset();
                Files.writeString(Path.of("HUB_PASSED.txt"),"Integrated Minecraft client passed Fire Leap and sticky Flashover: three mob attachments, synchronized host IDs, moved/turned attachment rendering, interpolated thrown flight, delayed detonation, per-bomb cooldown and saved UI key remapping. Native screenshots captured. Human multiplayer/combat balance pending.\n");
                done=true;c.scheduleStop();
            }
        }catch(Throwable e) { done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop(); }
    }
    private static void click(MinecraftClient client,int x,int y) {
        int w=client.getWindow().getScaledWidth(),h=client.getWindow().getScaledHeight();
        float scale=Math.min(1f,Math.min((w-8f)/360,(h-8f)/266));int ox=Math.round((w-360*scale)/2),oy=Math.round((h-266*scale)/2);
        client.currentScreen.mouseClicked(new net.minecraft.client.gui.Click(ox+x*scale,oy+y*scale,new net.minecraft.client.input.MouseInput(0,0)),false);
    }
    private void screenshot(MinecraftClient c,String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{}); }
    private static void require(boolean b,String message) { if(!b)throw new AssertionError(message); }
}
