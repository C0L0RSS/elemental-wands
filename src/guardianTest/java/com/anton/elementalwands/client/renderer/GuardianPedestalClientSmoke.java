package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.church.*;
import com.anton.elementalwands.client.*;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Real native models, targeting hint, rotation and placement packet in a disposable world. */
public final class GuardianPedestalClientSmoke {
    private int ticks,sceneTicks,floor;private boolean started,arranged,done;
    private volatile boolean ready;private volatile GuardianChurchManager.Site site;
    public static void start(MinecraftClient client) {
        var fixture=new GuardianPedestalClientSmoke();ClientTickEvents.END_CLIENT_TICK.register(fixture::tick);
        client.options.pauseOnLostFocus=false;client.options.hudHidden=false;
    }
    private void tick(MinecraftClient c) {
        if(done || c.getOverlay()!=null)return;
        try {
            if(++ticks>1500)throw new AssertionError("Pedestal visual fixture timed out");
            if(!started) {
                started=true;c.createIntegratedServerLoader().createAndStart("guardian-pedestal-"+System.currentTimeMillis(),
                        new LevelInfo("Guardian pedestal verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(812L,false,false),WorldPresets::createTestOptions,null);return;
            }
            if(c.world==null || c.player==null || c.getServer()==null)return;
            var server=c.getServer();var uuid=c.player.getUuid();
            if(!arranged) {
                arranged=true;server.execute(()->{
                    var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
                    for(int x=-3;x<=3;x++)for(int z=-2;z<=4;z++){w.getChunk(x,z);w.setChunkForced(x,z,true);}
                    var anchor=new BlockPos(0,floor+1,0);
                    for(var e:ChurchLayout.blocks(false).entrySet())w.setBlockState(anchor.add(e.getKey()),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                    var pos=anchor.add(0,0,-6);((GuardianSocketEntity)w.getBlockEntity(pos)).layoutVersion=2;
                    GuardianChurchManager.discover(w,pos,w.getBlockState(pos));
                    site=GuardianChurchManager.sites().stream().filter(s->s.anchor().equals(anchor)).findFirst().orElseThrow();
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);
                    p.networkHandler.requestTeleport(-1.1,floor,-8.1,0,0);
                    p.setSneaking(true);GuardianChurchManager.interact(p,pos);p.setSneaking(false);
                    ready=true;
                });return;
            }
            if(!ready)return;
            int t=++sceneTicks;
            if(t<45){WandWelcome.reset();c.setScreen(null);}
            if(t<125)aim(c,Vec3d.ofCenter(site.socket()).add(0,-.28,0));
            if(t==70) {
                require(c.player.getMainHandStack().isOf(ModItems.GUARDIAN_HEART),"Recalled heart did not sync to client");
                require(c.world.getBlockState(site.socket()).get(GuardianSocketBlock.PEDESTAL),"Pedestal property missing on client");
                require(GuardianOfferingHint.message(c)!=null,"Aiming with the heart did not show the native hint; hit="+c.crosshairTarget);
                screenshot(c,"pedestal-empty-hint.png");
            }
            if(t==85)server.execute(()->server.getPlayerManager().getPlayer(uuid).setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY));
            if(t==95)require(GuardianOfferingHint.message(c)==null,"Empty hand retained the placement hint");
            if(t==100)server.execute(()->{var p=server.getPlayerManager().getPlayer(uuid);p.setSneaking(true);GuardianChurchManager.interact(p,site.socket().down());p.setSneaking(false);});
            if(t==115) {
                require(c.crosshairTarget instanceof BlockHitResult,"No real native bowl hit");
                c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,(BlockHitResult)c.crosshairTarget);
            }
            if(t==122) {
                require(c.world.getBlockState(site.socket()).get(GuardianSocketBlock.RITUAL)==1,"Native use packet did not seat the heart");
                require(!c.player.getMainHandStack().isOf(ModItems.GUARDIAN_HEART),"Native placement failed to consume the heart");
                require(GuardianOfferingHint.message(c)==null,"Active offering retained the use hint");
                screenshot(c,"pedestal-heart-accepted.png");
            }
            // Let the existing ritual begin, then verify a returned empty pedestal after abort.
            if(t==160)server.execute(()->com.anton.elementalwands.arena.GuardianArenaManager.stop());
            if(t==400)server.execute(()->{
                var p=server.getPlayerManager().getPlayer(uuid);p.changeGameMode(GameMode.SPECTATOR);
                p.networkHandler.requestTeleport(-4,floor+4,-13,0,0);
                server.getOverworld().setTimeOfDay(13000);
            });
            if(t>=405 && t<450)aim(c,Vec3d.ofCenter(site.at(0,2,-4)));
            if(t==440){c.options.hudHidden=true;screenshot(c,"pedestal-statue-dusk.png");}
            if(t==465) {
                require(c.world.getBlockState(site.socket()).get(GuardianSocketBlock.RITUAL)==0,"Abort left a seated heart on client");
                Files.writeString(Path.of("GUARDIAN_PEDESTAL_WORLD_PASSED.txt"),"Native pedestal, empty-hand hint suppression, heart hint, actual block use packet, consumed heart, synchronized seated model, abort reset and Minecraft screenshots passed.\n");
                done=true;c.scheduleStop();
            }
        } catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("FLOOR_CLIENT_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private static void aim(MinecraftClient c,Vec3d target) {
        var delta=target.subtract(c.player.getEyePos());c.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        c.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalLength())));
    }
    private static void screenshot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{});}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
