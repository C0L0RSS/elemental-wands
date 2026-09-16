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
public final class FireLeapClientSmoke implements ClientModInitializer {
    private int ticks,scene;private boolean started,arranged,done;private volatile boolean ready;
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
                    for(int x=-9;x<=9;x++)for(int z=-8;z<=68;z++) {
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
            if(t>=45 && t<=85) { c.player.setYaw(0);c.player.setPitch((float)Math.toDegrees(Math.atan2(c.player.getEyeY()-floor,59.8))); }
            if(t==45)GLFW.glfwFocusWindow(c.getWindow().getHandle());
            if(t==50) {
                WandControls.reset();require(c.isWindowFocused(),"Test window not focused");
                require(WandControls.input(true,1,GLFW.GLFW_PRESS),"Technique press unhandled");
                require(WandControls.aimingLeap(),"Aim did not start");
                target=FireLeapRules.target(c.player);require(FireLeapRules.validTarget(c.player,target),"Client destination invalid "+target+" position="+c.player.getEntityPos()+" floor="+floor+" pitch="+c.player.getPitch());
            }
            if(t==51)require(target.subtract(c.player.getEntityPos()).horizontalLength()>59,"Client aim did not reach 60 blocks");
            if(t==65) screenshot(c,"fire-leap-aim.png");
            if(t==70) { WandControls.clear();require(!WandControls.aimingLeap(),"Cancel left preview active"); }
            if(t==75) {
                require(!c.player.hasVehicle(),"Cancel launched the player");
                WandControls.input(true,1,GLFW.GLFW_PRESS);
            }
            if(t==85) WandControls.input(true,1,GLFW.GLFW_RELEASE);
            if(t>=86 && t<120) {
                c.player.setPitch(45);
                if(c.player.getVehicle() instanceof FireLeapEntity) {
                    sawFlight=true;peak=Math.max(peak,c.player.getY()-floor);
                    if(t==93) screenshot(c,"fire-leap-flight.png");
                }
                if(t==109)screenshot(c,"fire-leap-wave.png");
            }
            if(t==130) {
                require(sawFlight && peak>3.8,"Client did not receive full flight "+peak);
                require(!c.player.hasVehicle() && c.player.getEntityPos().distanceTo(target)<.5,"Client missed destination "+c.player.getEntityPos()+" vs "+target);
                require(c.player.getHealth()==20,"Ordinary landing hurt caster");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Actual integrated client: hold/preview, cancellation, release packet, synchronized carrier flight, apex, destination, no landing fall damage and screenshots passed. Human combat feel pending.\n");
                done=true;c.scheduleStop();
            }
        }catch(Throwable e) { done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop(); }
    }
    private void screenshot(MinecraftClient c,String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{}); }
    private static void require(boolean b,String message) { if(!b)throw new AssertionError(message); }
}
