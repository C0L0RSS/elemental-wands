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
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.IronGolemEntity;
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
public final class AstralDoubleClientSmoke implements ClientModInitializer {
    private boolean started,arranged,done,sawOrb,sawPair;
    private volatile boolean ready,comboPassed;
    private volatile String serverFailure;
    private int ticks,scene,floor;
    private final boolean record=Boolean.getBoolean("astral.record");
    private final java.util.concurrent.atomic.AtomicInteger recorded=new java.util.concurrent.atomic.AtomicInteger();
    private String recordingDirectory;
    private Vec3d anchor;
    private IronGolemEntity target;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>1500)throw new AssertionError("Astral client timeout");
            if(!started){started=true;c.options.pauseOnLostFocus=false;c.options.tutorialStep=net.minecraft.client.tutorial.TutorialStep.NONE;
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(),1280,720);
                if(record){GLFW.glfwHideWindow(c.getWindow().getHandle());recordingDirectory="astral-recording-"+System.currentTimeMillis();var dir=c.runDirectory.toPath().resolve("screenshots").resolve(recordingDirectory);Files.createDirectories(dir);Files.writeString(Path.of("RECORDING_DIR.txt"),dir.toAbsolutePath().toString());}
                c.createIntegratedServerLoader().createAndStart("astral-"+System.currentTimeMillis(),new LevelInfo("Astral Double verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
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
                    p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));WandProgression.earn(p,WizardAffinity.SPACE,2500);
                    for(String id:new String[]{"blink_rift","hollow_purple","astral_double"})WandProgression.purchase(p,"SPACE",id);
                    WandLoadouts.equip(p,"SPACE",1,"blink_rift");WandLoadouts.equip(p,"SPACE",3,"astral_double");ModNetworking.syncPlayerData(p);
                    target=EntityType.IRON_GOLEM.create(w,SpawnReason.COMMAND);target.setPosition(.5,floor,20.5);target.setAiDisabled(true);target.setSilent(true);w.spawnEntity(target);
                    p.networkHandler.requestTeleport(.5,floor,.5,0,0);ready=true;
                }catch(Throwable e){serverFailure=e.toString();}
            });return;}
            if(serverFailure!=null)throw new AssertionError(serverFailure);
            if(!ready)return;int t=++scene;
            c.setScreen(null);WandWelcome.reset();
            if(t<=50){if(!record)GLFW.glfwFocusWindow(c.getWindow().getHandle());c.player.setYaw(0);c.player.setPitch(0);}
            if(t==45)WandControls.reset();
            if(t==50){require(ClientPlayerData.loadout().get(3).equals("astral_double"),"Double not in slot four");cast(3,true);}
            if(t==51)cast(3,false);
            if(t>=51&&t<=70)for(var e:c.world.getEntities())if(e instanceof AstralOrbEntity)sawOrb=true;
            if(t==75){var clone=clone(c);require(sawOrb&&clone!=null,"Orb/double missing on client");anchor=clone.getEntityPos();require(clone.ownerUuid().equals(uuid),"Skin owner not synchronized");require(AstralDoubleManager.state(c.player).getBoolean("active",false),"Active HUD missing");
                var renderer=c.getEntityRenderDispatcher().getRenderer(clone);var rs=renderer.getAndUpdateRenderState(clone,0);require(rs instanceof com.anton.elementalwands.client.renderer.AstralDoubleRenderer.State,"Missing clone render state");
                require(c.getEntityRenderDispatcher().getRenderer(rs)==renderer,"Clone state routed to vanilla player renderer");
                var skin=((com.anton.elementalwands.client.renderer.AstralDoubleRenderer.State)rs).appearance.skinTextures;require(skin.equals(c.player.getSkin()),"Double did not use owner's real skin");
                shot(c,"astral-double-rear.png");
            }
            if(t==80)server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(anchor.x+2,floor,anchor.z+3,145,8));
            if(t>=84&&t<=108){c.player.setYaw(145);c.player.setPitch(8);}
            if(t==96)shot(c,"astral-double-skin.png");
            if(t==110)server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(4.5,floor,.5,11.31f,0));
            if(t>=114&&t<=165){c.player.setYaw(11.31f);c.player.setPitch(0);}
            if(t==120)cast(0,true);
            if(t==121)cast(0,false);
            if(t>=121&&t<=132){int n=0;for(var e:c.world.getEntities())if(e instanceof SingularityBoltEntity)n++;if(n>=2)sawPair=true;}
            if(t==128)shot(c,"astral-double-crossfire.png");
            if(t==155){require(sawPair,"Two projectiles never synchronized");server.execute(()->{if(target.getHealth()>100-7*(1+SingularityBoltEntity.ECHO_STRENGTH)+.01)serverFailure="Mirrored damage missing: "+target.getHealth();});}
            if(t==170)server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(8.5,floor,.5,0,0));
            if(t>=174&&t<=225){c.player.setYaw(0);c.player.setPitch(0);}
            if(t==180)cast(1,true);
            if(t==181)cast(1,false);
            if(t==195)cast(3,true);
            if(t==196)cast(3,false);
            if(t==200)shot(c,"astral-double-teleport.png");
            if(t==203){require(clone(c)==null,"Teleport left clone");cast(1,true);}
            if(t==204)cast(1,false);
            if(t==218)server.execute(()->{var p=server.getPlayerManager().getPlayer(uuid);comboPassed=p.getEntityPos().squaredDistanceTo(anchor)<.1&&AstralDoubleManager.remaining(p)>0;});
            if(t==225){require(comboPassed,"Blink/double combo failed or Blink recast during its cooldown");shot(c,"astral-double-blink-cooldown.png");}
            if(t==235)server.execute(()->{var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.ASTRAL_STATE,new NbtCompound());p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));p.networkHandler.requestTeleport(.5,floor,.5,0,0);});
            if(t>=239){c.player.setYaw(0);c.player.setPitch(0);}
            if(t==245)cast(3,true);
            if(t==246)cast(3,false);
            if(t==275){require(clone(c)!=null,"Second double missing");shot(c,"astral-double-shimmer.png");}
            if(t==285)server.execute(()->{var p=server.getPlayerManager().getPlayer(uuid);var d=AstralDoubleManager.active(p);if(d==null){serverFailure="Destruction setup missing";return;}d.damage(p.getEntityWorld(),p.getEntityWorld().getDamageSources().generic(),1);});
            if(t==288)shot(c,"astral-double-poof.png");
            if(t==300){require(clone(c)==null,"Destroyed double remained visible");require(AstralDoubleManager.remaining(AstralDoubleManager.state(c.player),c.world.getTime(),false)>0,"Recovery HUD missing");}
            if(record&&t>=40&&t<340)ScreenshotRecorder.saveScreenshot(c.runDirectory,recordingDirectory+"/frame-"+String.format(java.util.Locale.ROOT,"%04d",t-40)+".png",c.getFramebuffer(),1,message->recorded.incrementAndGet());
            if(t>=340){if(record&&recorded.get()<300)return;
                var spell=WandSpells.find("astral_double");require(c.textRenderer.getWidth(spell.reach())<=148,"Hub reach overflow");require(c.textRenderer.getWidth(spell.timing())<=148,"Hub timing overflow");require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(spell.description()),148).size()<=3,"Hub description overflow");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Astral Double native client passed: "+(record?"scripted cast packets, hidden-window recording":"real bound input")+", orb/double synchronization, exact owner skin in player render state, stationary model/wand/shimmer screenshots, two networked bolts and mirrored damage, Blink -> consume double with no Blink return during its cooldown, active and recovery HUD synchronization, destruction, and hub text fit. Human Lunar playtest pending.\n");done=true;c.scheduleStop();}
        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private void cast(int slot,boolean press){
        if(record){if(press)ClientPlayNetworking.send(new ModNetworking.CastSlotPayload(slot));return;}
        int key=slot==3?GLFW.GLFW_KEY_Z:slot;boolean accepted=WandControls.input(slot<2,key,press?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE);if(press)require(accepted,"Input rejected for slot "+slot);
    }
    private static AstralDoubleEntity clone(MinecraftClient c){for(var e:c.world.getEntities())if(e instanceof AstralDoubleEntity d&&!d.isRemoved())return d;return null;}
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});}
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
}
