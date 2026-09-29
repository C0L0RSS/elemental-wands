package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Client synchronization/rendering plus an optional contiguous native framebuffer recording. */
public final class GravityWellClientSmoke implements ClientModInitializer {
    private boolean started,arranged,done,sawOrb,sawCollapse,sawBurst;
    private volatile boolean ready;
    private volatile String serverFailure;
    private int ticks,scene,floor;
    private final boolean record=Boolean.getBoolean("gravity.record");
    private final java.util.concurrent.atomic.AtomicInteger recorded=new java.util.concurrent.atomic.AtomicInteger();
    private String recordingDirectory;
    private Vec3d anchor;
    private net.minecraft.entity.mob.HuskEntity target;
    private Vec3d targetStart;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>1500)throw new AssertionError("Gravity client timeout");
            if(!started){started=true;c.options.pauseOnLostFocus=false;c.options.tutorialStep=net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(),1280,720);
                if(record){GLFW.glfwHideWindow(c.getWindow().getHandle());recordingDirectory="gravity-recording-"+System.currentTimeMillis();var dir=c.runDirectory.toPath().resolve("screenshots").resolve(recordingDirectory);Files.createDirectories(dir);Files.writeString(Path.of("RECORDING_DIR.txt"),dir.toAbsolutePath().toString());}
                c.createIntegratedServerLoader().createAndStart("gravity-"+System.currentTimeMillis(),new LevelInfo("Gravity Well verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                        new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),new GeneratorOptions(813L,false,false),WorldPresets::createTestOptions,null);return;}
            if(c.world==null||c.player==null||c.getServer()==null)return;
            var server=c.getServer();var uuid=c.player.getUuid();
            if(!arranged){arranged=true;server.execute(()->{
                try{
                    var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                    for(int x=-12;x<=14;x++)for(int z=-12;z<=35;z++){
                        w.setBlockState(new BlockPos(x,floor-1,z),((x+z)%2==0?Blocks.STONE_BRICKS:Blocks.SMOOTH_STONE).getDefaultState());
                        for(int y=0;y<9;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());}
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);p.setAttached(EWAttachments.AFFINITY,"SPACE");
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));WandProgression.earn(p,WizardAffinity.SPACE,3000);
                    for(String id:new String[]{"blink_rift","hollow_purple","astral_double","gravity_well"})WandProgression.purchase(p,"SPACE",id);
                    WandLoadouts.equip(p,"SPACE",1,"blink_rift");WandLoadouts.equip(p,"SPACE",3,"astral_double");WandLoadouts.equip(p,"SPACE",4,"gravity_well");ModNetworking.syncPlayerData(p);
                    target=EntityType.HUSK.create(w,SpawnReason.COMMAND);target.setPosition(2.5,floor,11.5);target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED).setBaseValue(0);target.setSilent(true);w.spawnEntity(target);targetStart=target.getEntityPos();
                    for(double x:new double[]{-1.5,3.5}){var mob=EntityType.HUSK.create(w,SpawnReason.COMMAND);mob.setPosition(x,floor,13);mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED).setBaseValue(0);mob.setSilent(true);w.spawnEntity(mob);}
                    p.networkHandler.requestTeleport(.5,floor,.5,0,0);ready=true;
                }catch(Throwable e){serverFailure=e.toString();}
            });return;}
            if(serverFailure!=null)throw new AssertionError(serverFailure);
            if(!ready)return;int t=++scene;
            c.setScreen(null);WandWelcome.reset();
            if(t<=50){if(!record)GLFW.glfwFocusWindow(c.getWindow().getHandle());c.player.setYaw(0);c.player.setPitch(0);}
            if(t==45)WandControls.reset();
            if(t==50){require(ClientPlayerData.loadout().get(4).equals("gravity_well"),"Gravity not in slot five");cast(4,true);}
            if(t==51)cast(4,false);
            if(t>=51&&t<=70)for(var e:c.world.getEntities())if(e instanceof GravityBombEntity b&&!b.isWell())sawOrb=true;
            if(t==54)shot(c,"gravity-well-flight.png");
            if(t==66)shot(c,"gravity-well-opening.png");
            if(t==75){var b=bomb(c);require(sawOrb&&b!=null&&b.isWell(),"Bomb/field missing on client");anchor=b.getEntityPos();
                require(GravityWellManager.state(c.player).getBoolean("active",false),"Active HUD missing");
                var renderer=c.getEntityRenderDispatcher().getRenderer(b);var state=renderer.getAndUpdateRenderState(b,0);
                require(state instanceof com.anton.elementalwands.client.renderer.GravityBombRenderer.State,"Missing gravity render state");
                shot(c,"gravity-well-impact.png");
                server.execute(()->{if(target.getEntityPos().squaredDistanceTo(targetStart)<.05)serverFailure="No visible enemy pull: start="+targetStart+" pos="+target.getEntityPos()+" velocity="+target.getVelocity();});
            }
            if(t==77)server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(5.5,floor+2,5.5,40,24));
            if(t>=80&&t<=110){c.player.setYaw(40);c.player.setPitch(24);}
            if(t==88)shot(c,"gravity-well-pull.png");
            if(t==95)cast(4,true);
            if(t==96)cast(4,false);
            if(t>=96&&t<=115){var b=bomb(c);if(b!=null){if(b.isCollapsing()&&!b.isBurst())sawCollapse=true;if(b.isBurst())sawBurst=true;}}
            if(t==100)shot(c,"gravity-well-buildup.png");
            if(t==108)shot(c,"gravity-well-collapse.png");
            if(t==120){require(sawCollapse&&sawBurst&&bomb(c)==null,"Buildup/burst/removal sequence failed on client");require(GravityWellManager.remaining(GravityWellManager.state(c.player),c.world.getTime(),false)>0,"Recovery HUD missing");}
            if(t==125)server.execute(()->{
                var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.GRAVITY_STATE,new NbtCompound());p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));p.networkHandler.requestTeleport(.5,floor,.5,0,0);
                target.setHealth(20);target.setPosition(targetStart);target.setVelocity(Vec3d.ZERO);
            });
            if(t>=129&&t<=170){c.player.setYaw(0);c.player.setPitch(0);}
            if(t==140)cast(4,true);
            if(t==141)cast(4,false);
            if(t==168){require(bomb(c)!=null&&bomb(c).isWell(),"Second well missing");shot(c,"gravity-well-auto-pull.png");}
            if(t==171)server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(5.5,floor+2,5.5,40,24));
            if(t>=175){c.player.setYaw(40);c.player.setPitch(24);}
            if(t==215)shot(c,"gravity-well-late-pull.png");
            if(t==245)shot(c,"gravity-well-auto-collapse.png");
            if(t==270)require(bomb(c)==null,"Automatic collapse failed on client");
            if(record&&t>=40&&t<280)ScreenshotRecorder.saveScreenshot(c.runDirectory,recordingDirectory+"/frame-"+String.format(java.util.Locale.ROOT,"%04d",t-40)+".png",c.getFramebuffer(),1,message->recorded.incrementAndGet());
            if(t>=280){if(record&&recorded.get()<240)return;
                var spell=WandSpells.find("gravity_well");require(c.textRenderer.getWidth(spell.reach())<=148,"Hub reach overflow");require(c.textRenderer.getWidth(spell.timing())<=148,"Hub timing overflow");require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(spell.description()),148).size()<=3,"Hub description overflow");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Gravity Well native client passed: "+(record?"scripted cast packets, hidden-window recording":"real bound input")+", bomb/field synchronization and render state, visible enemy displacement, visible accelerating buildup and outward burst, early and automatic collapse, active/recovery HUD synchronization, hub text fit. Human Lunar playtest pending.\n");done=true;c.scheduleStop();}
        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private void cast(int slot,boolean press){
        if(record){if(press)ClientPlayNetworking.send(new ModNetworking.CastSlotPayload(slot));return;}
        int key=slot==4?GLFW.GLFW_KEY_V:slot;boolean accepted=WandControls.input(slot<2,key,press?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE);if(press)require(accepted,"Input rejected for slot "+slot);
    }
    private static GravityBombEntity bomb(MinecraftClient c){for(var e:c.world.getEntities())if(e instanceof GravityBombEntity b&&!b.isRemoved())return b;return null;}
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});}
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
