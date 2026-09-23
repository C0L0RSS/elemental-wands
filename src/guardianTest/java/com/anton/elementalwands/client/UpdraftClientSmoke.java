package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Actual client physics and real spell inputs in a disposable world. */
public final class UpdraftClientSmoke implements ClientModInitializer {
    private boolean started,arranged,done,rose,landed;
    private volatile boolean ready;
    private int ticks,scene,floor;
    private double startY,top,startZ;
    private final boolean record = Boolean.getBoolean("updraft.record");
    private final java.util.concurrent.atomic.AtomicInteger recorded = new java.util.concurrent.atomic.AtomicInteger();
    private String recordingDirectory;
    private static final int RECORD_START = 40, RECORD_END = 240;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>1200)throw new AssertionError("Updraft client timeout");
            if(!started){started=true;c.options.pauseOnLostFocus=false;
                c.options.tutorialStep=net.minecraft.client.tutorial.TutorialStep.NONE;
                if(record) {
                    GLFW.glfwSetWindowSize(c.getWindow().getHandle(),1280,720);
                    GLFW.glfwHideWindow(c.getWindow().getHandle());
                    recordingDirectory = "updraft-recording-" + System.currentTimeMillis();
                    var directory = c.runDirectory.toPath().resolve("screenshots").resolve(recordingDirectory);
                    Files.createDirectories(directory);
                    Files.writeString(Path.of("RECORDING_DIR.txt"), directory.toAbsolutePath().toString());
                }
                c.createIntegratedServerLoader().createAndStart("updraft-"+System.currentTimeMillis(),
                        new LevelInfo("Updraft verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L,false,false),WorldPresets::createTestOptions,null);return;}
            if(c.world==null||c.player==null||c.getServer()==null)return;
            var server=c.getServer();var uuid=c.player.getUuid();
            if(!arranged){arranged=true;server.execute(()->{
                var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                for(int x=-12;x<=12;x++)for(int z=-12;z<=65;z++){
                    w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                    for(int y=0;y<10;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());}
                w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);p.setAttached(EWAttachments.AFFINITY,"WIND");
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                WandProgression.earn(p,WizardAffinity.WIND,3500);
                for(String id:new String[]{"updraft","gale_daggers","waylay_dash","zephyr_strike"})WandProgression.purchase(p,"WIND",id);
                WandLoadouts.equip(p,"WIND",4,"updraft");WandLoadouts.equip(p,"WIND",3,"gale_daggers");WandLoadouts.equip(p,"WIND",1,"waylay_dash");ModNetworking.syncPlayerData(p);
                p.networkHandler.requestTeleport(.5,floor,.5,0,0);ready=true;
            });return;}
            if(!ready)return;int t=++scene;
            if(record) {
                c.setScreen(null);
                c.player.setYaw(0);c.player.setPitch(t<110?-65:t<160?0:Math.min(48,(t-160)*2.4f));
            }
            if(t<=50){if(!record)GLFW.glfwFocusWindow(c.getWindow().getHandle());WandWelcome.reset();c.setScreen(null);c.player.setYaw(0);c.player.setPitch(-65);}
            if(t==45){WandControls.reset();c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);}
            if(t==50){require(ClientPlayerData.loadout().get(4).equals("updraft"),"Slot five missing");startY=top=c.player.getY();startZ=c.player.getZ();key(GLFW.GLFW_KEY_V,true);}
            if(t==51)key(GLFW.GLFW_KEY_V,false);
            if(t>=50&&t<=100){top=Math.max(top,c.player.getY());if(c.player.getY()>startY+1)rose=true;if(rose&&c.player.isOnGround())landed=true;}
            if(t==53)c.options.forwardKey.setPressed(true);
            if(t==53)shot(c,"updraft-start.png");
            if(t==54)shot(c,"updraft-ring.png");
            if(t==55)shot(c,"updraft-rise.png");
            if(t==65)shot(c,"updraft-apex.png");
            if(t==72)c.options.forwardKey.setPressed(false);
            if(t==100){
                require(rose&&landed,"Did not complete native jump");require(top-startY>9.7&&top-startY<10.3,"Wrong native apex: "+(top-startY));
                require(c.player.getZ()>startZ+1,"Vanilla horizontal input suppressed");require(c.player.getHealth()==20,"First landing caused damage");
                require(c.player.getAttachedOrElse(EWAttachments.UPDRAFT_LAST_CAST,-1_000_000_000L)>0,"Cooldown not synchronized");
                shot(c,"updraft-landed.png");
            }
            if(t==110){c.options.setPerspective(Perspective.FIRST_PERSON);key(GLFW.GLFW_KEY_Z,true);}
            if(t==111)key(GLFW.GLFW_KEY_Z,false);
            if(t==125){require(c.player.getAttachedOrElse(EWAttachments.GALE_PREPARED,0)==3,"Could not prepare combo daggers");server.execute(()->{
                var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.UPDRAFT_LAST_CAST,-1_000_000_000L);
                net.minecraft.component.type.NbtComponent.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,p.getMainHandStack(),n->n.remove(com.anton.elementalwands.item.AbstractWandItem.cooldownKey("updraft")));
                // Keep the prepared wand reference while resetting only this fixture cooldown.
                p.networkHandler.requestTeleport(.5,floor+16,.5,0,0);
            });}
            if(t==132){require(c.player.getVelocity().y<0&&!c.player.isOnGround(),"Airborne setup was not falling");key(GLFW.GLFW_KEY_V,true);c.options.forwardKey.setPressed(true);}
            if(t==133)key(GLFW.GLFW_KEY_V,false);
            if(t==137)require(c.player.getVelocity().y>0,"Midair Updraft did not reverse fall");
            if(t==140){require(input(true,1,true),"Dash input rejected: focused="+c.isWindowFocused()+" screen="+c.currentScreen);}
            if(t==141)input(true,1,false);
            if(t==148){require(c.player.getAttachedOrElse(EWAttachments.GALE_PREPARED,0)==3,"Movement canceled prepared daggers");require(input(true,0,true),"Dagger fire rejected");}
            if(t==149)input(true,0,false);
            if(t==157){require(c.player.getAttachedOrElse(EWAttachments.GALE_PREPARED,0)==0,"Combo did not fire daggers");shot(c,"updraft-combo.png");c.options.forwardKey.setPressed(false);}
            if(t>=160 && t<=180)c.player.setPitch((t-160)*2.4f);
            // Capture the game's own framebuffer once per 20 Hz simulation tick.
            // These are contiguous gameplay frames, encoded at the same rate afterward.
            if(record && t>=RECORD_START && t<RECORD_END)
                ScreenshotRecorder.saveScreenshot(c.runDirectory,
                        recordingDirectory + "/frame-" + String.format(java.util.Locale.ROOT,"%04d",t-RECORD_START) + ".png",
                        c.getFramebuffer(),1,message->recorded.incrementAndGet());
            if(t>=240){
                if(record && recorded.get()<RECORD_END-RECORD_START)return;
                require(c.player.isOnGround()&&c.player.getHealth()==20,"Combo landing was unsafe");
                var spell=WandSpells.find("updraft");require(c.textRenderer.getWidth(spell.reach())<=148,"Hub stats overflow");
                require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(spell.description()),148).size()<=4,"Hub description overflow");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Updraft native client passed: "+(record?"scripted spell packets during hidden-window recording":"real V input")+", fifth equipped slot, actual jump rise="+(top-startY)+", vanilla horizontal input, safe landing, synchronized cooldown, midair launch, dash and prepared-dagger combo, safe combo landing, hub text fit. Human feel pending.\n");done=true;c.scheduleStop();}
        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private void key(int key,boolean press){boolean result=input(false,key,press);if(press)require(result,"Key rejected: "+key);}
    private boolean input(boolean mouse,int code,boolean press) {
        if(!record)return WandControls.input(mouse,code,press?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE);
        if(press) {
            int slot=mouse?code:code==GLFW.GLFW_KEY_V?4:3;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ModNetworking.CastSlotPayload(slot));
        }
        return true;
    }
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
