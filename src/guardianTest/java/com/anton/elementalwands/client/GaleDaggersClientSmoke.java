package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.GaleDaggerEntity;
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

/** Real key/mouse packets, synchronized prepared models, native rendering and HUD. */
public final class GaleDaggersClientSmoke implements ClientModInitializer {
    private boolean started,arranged,done,sawFlight;
    private volatile boolean ready;
    private int ticks,scene;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>1200)throw new AssertionError("Gale Daggers client timeout");
            if(!started){started=true;c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("gale-daggers-"+System.currentTimeMillis(),
                        new LevelInfo("Gale Daggers verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L,false,false),WorldPresets::createTestOptions,null);return;}
            if(c.world==null||c.player==null||c.getServer()==null)return;
            var server=c.getServer();var uuid=c.player.getUuid();
            if(!arranged){arranged=true;server.execute(()->{
                var w=server.getOverworld();int floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                for(int x=-12;x<=12;x++)for(int z=-12;z<=45;z++){
                    w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                    for(int y=0;y<8;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());}
                w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);p.setAttached(EWAttachments.AFFINITY,"WIND");
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                WandProgression.earn(p,WizardAffinity.WIND,500);WandProgression.purchase(p,"WIND",GaleDaggers.ID);
                WandLoadouts.equip(p,"WIND",3,GaleDaggers.ID);ModNetworking.syncPlayerData(p);
                p.networkHandler.requestTeleport(.5,floor,.5,0,0);ready=true;
            });return;}
            if(!ready)return;int t=++scene;
            if(t<=50){GLFW.glfwFocusWindow(c.getWindow().getHandle());WandWelcome.reset();c.setScreen(null);c.player.setYaw(0);c.player.setPitch(0);}
            if(t==45)WandControls.reset();
            if(t==50){require(ClientPlayerData.loadout().get(3).equals(GaleDaggers.ID),"Spell not equipped");require(WandControls.input(false,GLFW.GLFW_KEY_Z,GLFW.GLFW_PRESS),"Prepare key rejected");}
            if(t==51)WandControls.input(false,GLFW.GLFW_KEY_Z,GLFW.GLFW_RELEASE);
            if(t==65){require(count(c,false)==3,"Prepared entities not synchronized");require(c.player.getAttachedOrElse(EWAttachments.GALE_PREPARED,0)==3,"Ready HUD not synchronized");c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);}
            if(t==74)shot(c,"gale-daggers-front.png");
            if(t==78){c.player.setYaw(65);c.player.setPitch(-12);}
            if(t==85)shot(c,"gale-daggers-angle.png");
            if(t==88){c.options.setPerspective(Perspective.FIRST_PERSON);c.player.setYaw(0);c.player.setPitch(0);}
            if(t==90){GLFW.glfwFocusWindow(c.getWindow().getHandle());require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Primary fire rejected");}
            if(t==114){
                var nbt=c.player.getMainHandStack().getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
                require(nbt.getLong(com.anton.elementalwands.item.AbstractWandItem.cooldownKey("sky_shear")).isEmpty(),"Held volley press also fired Sky Shear");
                WandControls.input(true,0,GLFW.GLFW_RELEASE);
            }
            if(t>=90&&t<=112 && count(c,true)>0)sawFlight=true;
            if(t==96)shot(c,"gale-daggers-burst.png");
            if(t==102)shot(c,"gale-daggers-rings.png");
            if(t==116){require(sawFlight,"No networked dagger flight");require(count(c,false)==0,"Primary did not consume prepared daggers");require(c.player.getAttachedOrElse(EWAttachments.GALE_LAST_LAUNCH,-1_000_000_000L)>0,"Cooldown not synchronized");shot(c,"gale-daggers-cooldown.png");}
            if(t==125){
                var spell=WandSpells.find(GaleDaggers.ID);require(c.textRenderer.getWidth(spell.reach())<=148,"Hub stats overflow");
                require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(spell.description()),148).size()<=4,"Hub description overflow");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Gale Daggers native client passed: purchase/equip, actual Z prepare and primary-fire packets, three tracked dagger models, ready attachment, projectile flight, consumed volley, cooldown synchronization, and hub text fit. Native screenshots captured; human feel pending.\n");done=true;c.scheduleStop();}
        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private static int count(MinecraftClient c,boolean fired){int n=0;for(var e:c.world.getEntities())if(e instanceof GaleDaggerEntity d&&d.fired()==fired)n++;return n;}
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});}
    private static void require(boolean c,String why){if(!c)throw new AssertionError(why);}
}
