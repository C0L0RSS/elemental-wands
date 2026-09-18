package com.anton.elementalwands.client;

import com.anton.elementalwands.client.wand.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Native model baking, per-holder color, every display context and real held/GUI screenshots. */
public final class WandModelClientSmoke implements ClientModInitializer {
    private int ticks, scene, floor;
    private boolean started,arranged,done,reloading;
    private volatile boolean ready;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private final String[] elements={"FIRE","WIND","STONE","NATURE","SPACE","NONE"};
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c) {
        if(done||c.getOverlay()!=null)return;
        try {
            if(++ticks>1800)throw new AssertionError("Wand client verification timed out");
            if(!started){started=true;c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("wand-model-"+System.currentTimeMillis(),
                    new LevelInfo("Wand model verification",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,
                        new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(813L,false,false),WorldPresets::createTestOptions,null);return;}
            if(c.world==null||c.player==null||c.getServer()==null)return;
            if(!arranged){arranged=true;var uuid=c.player.getUuid();c.getServer().execute(()->{
                var w=c.getServer().getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                for(int x=-8;x<=8;x++)for(int z=-8;z<=12;z++){
                    w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                    for(int y=0;y<8;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());}
                w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                var p=c.getServer().getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);
                p.setAttached(EWAttachments.AFFINITY,"FIRE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                ModNetworking.syncPlayerData(p);p.networkHandler.requestTeleport(.5,floor,.5,0,0);ready=true;
            });return;}
            if(!ready)return;
            if(reloading){if(!reload.isDone())return;reload.join();validate(c);shot(c,"wand-after-reload.png");finish(c);return;}
            int t=++scene;
            if(t<30){WandWelcome.reset();c.setScreen(null);}
            if(t==30)validate(c);
            if(t>=30&&t<265){c.player.setYaw(0);c.player.setPitch(0);}
            for(int i=0;i<elements.length;i++){
                if(t==35+i*35)setElement(c,elements[i]);
                if(t==60+i*35){require(ClientPlayerData.getAffinity().name().equals(elements[i]),"Local palette did not sync");
                    require(EWAttachments.getAffinity(c.player).name().equals(elements[i]),"Visible affinity attachment did not sync");
                    shot(c,"wand-held-"+elements[i].toLowerCase()+".png");}
            }
            if(t==250){setElement(c,"STONE");var uuid=c.player.getUuid();c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(uuid);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);p.setStackInHand(Hand.OFF_HAND,new ItemStack(ModItems.FRACTURED_WAND));});}
            if(t==275)shot(c,"wand-offhand.png");
            if(t==280){var uuid=c.player.getUuid();c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(uuid);p.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));});c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);}
            if(t==305)shot(c,"wand-third-person.png");
            if(t==310){c.options.setPerspective(Perspective.FIRST_PERSON);c.getServer().execute(()->{var w=c.getServer().getOverworld();var item=new ItemEntity(w,.5,floor+1.4,2.5,new ItemStack(ModItems.FRACTURED_WAND));item.setNoGravity(true);item.setVelocity(Vec3d.ZERO);w.spawnEntity(item);});}
            if(t==335)shot(c,"wand-dropped-neutral.png");
            if(t==340)c.setScreen(new PaletteScreen(c));
            if(t==360)shot(c,"wand-palettes-a.png");
            if(t==400)shot(c,"wand-palettes-b.png");
            if(t==410){reload=c.reloadResources();reloading=true;}
        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(Path.of("WAND_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();}
    }
    private void validate(MinecraftClient c){
        var stack=new ItemStack(ModItems.FRACTURED_WAND);
        for(var display:ItemDisplayContext.values()){
            var state=new ItemRenderState();c.getItemModelManager().clearAndUpdate(state,stack,display,c.world,c.player,0);
            require(!state.isEmpty()&&state.isAnimated(),"Missing custom wand in "+display);
            require(state.getModelBoundingBox().getLengthX()>0,"Empty bounds in "+display);
        }
        var remote=new OtherClientPlayerEntity(c.world,new GameProfile(UUID.randomUUID(),"WandViewer"));
        for(String name:elements){remote.setAttached(EWAttachments.AFFINITY,name);require(WandItemModel.affinity(remote,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).name().equals(name),"Remote color used viewer affinity");}
        require(WandItemModel.affinity(null,ItemDisplayContext.GROUND)==WizardAffinity.NONE,"Dropped wand used viewer color");
        require(WandMesh.DATA.wood().length==464,"Joined wood shell changed unexpectedly");
        for(var v:WandMesh.GUI_BOUNDS)require(v.x>=0&&v.x<=1&&v.y>=0&&v.y<=1,"Top-only icon escapes its slot");
        for(var q:WandMesh.DATA.guiWood())for(int v=0;v<4;v++)require(q[v*3+1]>=3f,"GUI still includes lower shaft");
        // Particle cube circumsphere fits inside the shell across the complete slow motion cycle.
        for(int t=0;t<4000;t++)for(int i=0;i<8;i++){
            var p=WandRenderer.mote(i,t*.2);require(Math.max(Math.abs(p.x),Math.max(Math.abs(p.y),Math.abs(p.z)))+Math.sqrt(3)*.0375<.89,"Particle exits glass");
        }
    }
    private void setElement(MinecraftClient c,String name){var uuid=c.player.getUuid();c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.AFFINITY,name);ModNetworking.syncPlayerData(p);});}
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});}
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void finish(MinecraftClient c)throws Exception{done=true;Files.writeString(Path.of("WAND_PASSED.txt"),"Native wand model passed: all display contexts, six synchronized affinities, remote-holder isolation, neutral drops, bounded particles, resource reload, first/off/third-person and animated GUI screenshots.\n");c.scheduleStop();}
    private static final class PaletteScreen extends Screen {
        private final OtherClientPlayerEntity[] holders=new OtherClientPlayerEntity[6];
        private final ItemStack wand=new ItemStack(ModItems.FRACTURED_WAND);
        PaletteScreen(MinecraftClient c){super(Text.literal("Wand palettes"));for(int i=0;i<6;i++){holders[i]=new OtherClientPlayerEntity(c.world,new GameProfile(UUID.randomUUID(),"Palette"+i));holders[i].setAttached(EWAttachments.AFFINITY,WandMesh.DATA.elementOrder()[i]);}}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
            draw.fill(0,0,width,height,0xff252b31);draw.drawTextWithShadow(textRenderer,"Wand palette / native item rendering",12,12,0xffe5e8eb);
            float scale=Math.min((width-24)/6f/18f,(height-60)/18f);
            for(int i=0;i<6;i++){int x=12+(width-24)*i/6;draw.drawTextWithShadow(textRenderer,WandMesh.DATA.elementOrder()[i],x,34,0xffe5e8eb);
                var m=draw.getMatrices();m.pushMatrix();m.translate(x,54);m.scale(scale,scale);draw.drawItem(holders[i],wand,0,0,i);m.popMatrix();}
        }
    }
}
