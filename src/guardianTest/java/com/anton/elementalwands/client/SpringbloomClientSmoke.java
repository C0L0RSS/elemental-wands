package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.SpringbloomEntity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.*;
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
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Actual native mouse cast, synchronized model, launch physics and protected return. */
public final class SpringbloomClientSmoke implements ClientModInitializer {
    private boolean started,arranged,done,launched,landed,clutchThrown,clutchLaunched;
    private int clutchTick;
    private boolean horizontalLaunched,horizontalLanded;
    private double horizontalStart,lastZ,horizontalDistance;
    private volatile boolean ready;
    private int ticks,scene,floor;
    private double apex;
    private BlockPos pad;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>1500)throw new AssertionError("Client timeout");
            if(!started){started=true;c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("springbloom-"+System.currentTimeMillis(),
                        new LevelInfo("Springbloom verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L,false,false),WorldPresets::createTestOptions,null);return;}
            if(c.world==null||c.player==null||c.getServer()==null)return;
            var server=c.getServer();var uuid=c.player.getUuid();
            if(!arranged){arranged=true;server.execute(()->{
                var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                for(int x=-20;x<=20;x++)for(int z=-20;z<=25;z++){
                    w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                    for(int y=0;y<30;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());}
                // Forest-floor cover along the actual thrown pod's landing corridor.
                for(int x=-2;x<=2;x++)for(int z=-5;z<=2;z++){
                    w.setBlockState(new BlockPos(x,floor-1,z),Blocks.GRASS_BLOCK.getDefaultState());
                    w.setBlockState(new BlockPos(x,floor,z),((x+z)%2==0?Blocks.FERN:Blocks.LEAF_LITTER).getDefaultState());
                }
                w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);p.setAttached(EWAttachments.AFFINITY,"NATURE");
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                WandProgression.earn(p,WizardAffinity.NATURE,500);WandProgression.purchase(p,"NATURE","springbloom");WandLoadouts.equip(p,"NATURE",0,"springbloom");
                ModNetworking.syncPlayerData(p);p.setHealth(20);p.getHungerManager().setFoodLevel(10);p.networkHandler.requestTeleport(.5,floor,-2.5,0,65);ready=true;
            });return;}
            if(!ready)return;int t=++scene;
            if(t<=50){GLFW.glfwFocusWindow(c.getWindow().getHandle());WandWelcome.reset();c.setScreen(null);c.player.setYaw(0);c.player.setPitch(65);}
            if(t==45){GLFW.glfwFocusWindow(c.getWindow().getHandle());WandControls.reset();}
            if(t==50){require(ClientPlayerData.loadout().getFirst().equals("springbloom"),"Spell did not equip");require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Mouse cast failed: focused="+c.isWindowFocused()+" screen="+c.currentScreen);}
            if(t==51)WandControls.input(true,0,GLFW.GLFW_RELEASE);
            if(t>=52&&t<65)for(var e:c.world.getEntities())if(e instanceof SpringbloomEntity flower&&flower.open())pad=flower.getBlockPos();
            if(t==66){require(pad!=null,"No synchronized pad");server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(pad.getX()+.5,floor,pad.getZ()-3.5,0,18));}
            if(t>=70&&t<=79){c.player.setYaw(0);c.player.setPitch(18);}
            if(t==76){
                boolean visible=false;
                for(var e:c.world.getEntities())if(e instanceof SpringbloomEntity flower&&flower.open()){
                    require(e.getEntityPos().distanceTo(Vec3d.ofBottomCenter(pad))<.1,"Visual pad drifted from collision block");
                    visible=true;
                }
                require(visible,"Visual pad vanished before screenshot");shot(c,"springbloom-native.png");
            }
            if(t==80){server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(pad.getX()+.5,floor+10,pad.getZ()+.5,0,65));}
            if(t>82&&t<190){
                if(c.player.getVelocity().y>1){launched=true;require(c.player.getHealth()==20,"Catch damaged player");}
                if(launched){apex=Math.max(apex,c.player.getY()-floor-SpringbloomRules.HEIGHT);if(c.player.isOnGround()&&t>130)landed=true;}
            }
            if(t==120)shot(c,"springbloom-flight.png");
            if(t==190){require(launched,"No native launch");require(apex>18&&apex<23,"Native apex wrong: "+apex);require(landed,"Never returned to ground");require(c.player.getHealth()==20,"Fall protection failed");require(c.world.getBlockState(pad).isOf(Blocks.FERN)||c.world.getBlockState(pad).isOf(Blocks.LEAF_LITTER),"Covered foliage did not return on expiry");}
            if(t==205){server.execute(()->{
                var p=server.getPlayerManager().getPlayer(uuid);p.networkHandler.requestTeleport(pad.getX()+.5,floor,pad.getZ()-3.5,0,18);
                require(SpringbloomManager.plant(p,pad),"Break preview plant failed");});}
            if(t==218)shot(c,"springbloom-ready.png");
            if(t==225)server.execute(()->server.getOverworld().breakBlock(pad,false));
            if(t==235){require(c.world.getBlockState(pad).isAir(),"Broken pad still exists");
                var spell=WandSpells.find("springbloom");require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(spell.description()),148).size()<=4,"Description too long");
                require(c.textRenderer.getWidth(spell.reach())<=148,"Stats too wide");
            }
            if(t==250)server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(8.5,floor+15,8.5,0,90));
            if(t>=250 && t<330){
                if(t<280)GLFW.glfwFocusWindow(c.getWindow().getHandle());
                c.player.setYaw(0);c.player.setPitch(90);
                if(!clutchThrown && t>252 && c.player.getY()<floor+4.5 && c.player.getY()>floor+1.5){
                    GLFW.glfwFocusWindow(c.getWindow().getHandle());WandControls.reset();
                    if(WandControls.input(true,0,GLFW.GLFW_PRESS)){clutchThrown=true;clutchTick=t;}
                }
                if(clutchThrown && t==clutchTick+1)WandControls.input(true,0,GLFW.GLFW_RELEASE);
                if(clutchThrown && c.player.getVelocity().y>1)clutchLaunched=true;
            }
            if(t==375){require(clutchThrown&&clutchLaunched,"Last-second pod catch failed: thrown="+clutchThrown+" launched="+clutchLaunched+" health="+c.player.getHealth()+" tick="+clutchTick);require(c.player.getHealth()==20,"Clutch catch or landing damaged player");}
            if(t==395)server.execute(()->{
                var p=server.getPlayerManager().getPlayer(uuid);
                require(SpringbloomManager.plant(p,new BlockPos(0,floor,10)),"Horizontal test pad failed");
                p.networkHandler.requestTeleport(.5,floor+2,8.65,0,0);
                p.setVelocity(0,-.3,.55);p.velocityModified=true;
                p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
            });
            if(t>=396&&t<480){
                c.player.setYaw(0);c.player.setPitch(0);c.options.forwardKey.setPressed(!horizontalLanded);
                if(!horizontalLaunched&&c.player.getVelocity().y>1){horizontalLaunched=true;horizontalStart=lastZ;}
                if(horizontalLaunched&&!horizontalLanded){
                    horizontalDistance=c.player.getZ()-horizontalStart;
                    if(c.player.isOnGround()){horizontalLanded=true;c.options.forwardKey.setPressed(false);}
                }
                lastZ=c.player.getZ();
            }
            if(t==480){c.options.forwardKey.setPressed(false);require(horizontalLaunched&&horizontalLanded,"No complete horizontal native launch");
                require(horizontalDistance>13&&horizontalDistance<17,"Horizontal travel exceeded target with held input: "+horizontalDistance);
                require(c.player.getHealth()==20,"Horizontal flight landing caused damage");
            }
            if(t==485)server.execute(()->{
                var w=server.getOverworld();var p=server.getPlayerManager().getPlayer(uuid);var at=new BlockPos(0,floor,0);
                w.setBlockState(at.east(),Blocks.OAK_LOG.getDefaultState());w.setBlockState(at.south(),Blocks.STONE_BRICKS.getDefaultState());
                require(SpringbloomManager.plant(p,at),"Partial flower rejected in native world");
                p.networkHandler.requestTeleport(-2.5,floor,-3.5,-35,18);
            });
            if(t>=490&&t<=505){c.player.setYaw(-35);c.player.setPitch(18);}
            if(t==500){
                var at=new BlockPos(0,floor,0);int expected=c.world.getBlockState(at).get(com.anton.elementalwands.block.SpringbloomBlock.OPEN_CELLS);
                require(expected!=511 && (expected&SpringbloomFootprint.CENTER)!=0,"No synchronized partial mask");
                for(var e:c.world.getEntities())if(e instanceof SpringbloomEntity flower&&flower.open()&&flower.getBlockPos().equals(at))require(flower.openCells()==expected,"Renderer/collision masks differ");
                shot(c,"springbloom-partial.png");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Springbloom native client passed: mouse cast, purchased/equipped spell, approved model, high-fall catch, actual apex="+apex+", protected landing, expiry, break, last-second thrown-pod catch, forest cover, horizontal distance="+horizontalDistance+", and synchronized partial flower beside solid obstacles. Human multiplayer feel pending.\n");done=true;c.scheduleStop();}


        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});}
    private static void require(boolean c,String why){if(!c)throw new AssertionError(why);}
}
