package com.anton.elementalwands.client;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.ThornLashEntity;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.ZombieEntity;
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

/** Actual native input, network entity/model rendering, healing and source removal. */
public final class NatureOvergrowthClientSmoke implements ClientModInitializer {
    private int ticks, scene, floor;
    private boolean started, arranged, done, sawLash, sawKnot, sawAcorn;
    private volatile boolean ready;
    private BlockPos knot;
    public void onInitializeClient() {ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c) {
        if(done || c.getOverlay()!=null)return;
        try {
            if(++ticks>1600)throw new AssertionError("Client fixture timed out");
            if(!started) {
                started=true;c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("nature-expansion-"+System.currentTimeMillis(),
                        new LevelInfo("Nature expansion verification",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,
                                new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(813L,false,false),WorldPresets::createTestOptions,null);return;
            }
            if(c.world==null || c.player==null || c.getServer()==null)return;
            var server=c.getServer();
            if(!arranged) {
                arranged=true;var uuid=c.player.getUuid();
                server.execute(()->{
                    var w=server.getOverworld();floor=w.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0)+2;
                    for(int x=-12;x<=12;x++)for(int z=-20;z<=40;z++) {
                        w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                        for(int y=0;y<12;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());
                    }
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);
                    p.setAttached(EWAttachments.AFFINITY,"NATURE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                    WandProgression.earn(p,WizardAffinity.NATURE,1000);WandProgression.purchase(p,"NATURE","thorn_lash");
                    WandProgression.purchase(p,"NATURE","tendril_bloom");WandLoadouts.equip(p,"NATURE",0,"thorn_lash");
                    ModNetworking.syncPlayerData(p);p.setHealth(10);p.getHungerManager().setFoodLevel(10);p.networkHandler.requestTeleport(.5,floor,.5,0,0);
                    var zombie=new ZombieEntity(EntityType.ZOMBIE,w);zombie.setAiDisabled(true);zombie.setSilent(true);
                    zombie.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);
                    zombie.equipStack(EquipmentSlot.HEAD,new ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
                    zombie.refreshPositionAndAngles(.5,floor,3.5,180,0);w.spawnEntity(zombie);
                    for (int side : new int[]{-1,1}) {
                        var other = new ZombieEntity(EntityType.ZOMBIE,w);other.setAiDisabled(true);other.setSilent(true);
                        other.equipStack(EquipmentSlot.HEAD,new ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
                        other.refreshPositionAndAngles(.5+side*5,floor,8.5,180,0);w.spawnEntity(other);
                    }
                    ready=true;
                });return;
            }
            if(!ready)return;
            int t=++scene;
            if(t<45){WandWelcome.reset();c.setScreen(null);}
            if(t==45){GLFW.glfwFocusWindow(c.getWindow().getHandle());WandControls.reset();}
            if(t>=45 && t<=90){c.player.setYaw(0);c.player.setPitch(0);}
            if(t==50) {
                require(ClientPlayerData.loadout().getFirst().equals("thorn_lash"),"Lash loadout not synchronized");
                require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Basic input unhandled");
            }
            if(t==51)WandControls.input(true,0,GLFW.GLFW_RELEASE);
            if(t>50 && t<85)for(var entity:c.world.getEntities()) {
                if(entity instanceof ThornLashEntity && entity.age>=3 && !sawLash){sawLash=true;screenshot(c,"thorn-lash-sweep.png");}
            }
            if(t==85){require(sawLash,"No synchronized Lash entity");require(c.player.getHealth()>10,"Lash did not heal client");}
            if(t==95) {WandControls.input(true,1,GLFW.GLFW_PRESS);knot=c.player.getBlockPos();}
            if(t==96)WandControls.input(true,1,GLFW.GLFW_RELEASE);
            if(t==105) {
                require(c.world.getBlockState(knot).isOf(ModSpellBlocks.NATURE_ROOT_KNOT),"No synchronized knot block");
                sawKnot=true;var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5,floor,-3.5,0,18));
            }
            if(t>=110 && t<=150){c.player.setYaw(0);c.player.setPitch(18);}
            if(t==125)screenshot(c,"nature-root-knot.png");
            if(t==145){server.execute(()->server.getOverworld().breakBlock(knot,false));}
            if(t==160) {
                require(sawKnot && c.world.getBlockState(knot).isAir(),"Knot destruction not synchronized");
                screenshot(c,"nature-root-knot-destroyed.png");
                c.setScreen(new com.anton.elementalwands.client.screen.WandHubScreen());
            }
            if(t==165) {
                for (String id : new String[]{"thorn_lash","tendril_bloom","overgrowth"}) {
                    var spell=WandSpells.find(id);
                    require(c.textRenderer.getWidth(spell.timing())<=148 && c.textRenderer.getWidth(spell.reach())<=148,"Spell stats exceed detail panel: "+id);
                    require(c.textRenderer.wrapLines(net.minecraft.text.Text.literal(spell.description()),148).size()<=4,"Spell description overlaps stats: "+id);
                }
                int w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();
                float scale=Math.min(1f,Math.min((w-8f)/360,(h-8f)/266));
                int ox=Math.round((w-360*scale)/2),oy=Math.round((h-266*scale)/2);
                c.currentScreen.mouseClicked(new net.minecraft.client.gui.Click(ox+100*scale,oy+201*scale,new net.minecraft.client.input.MouseInput(0,0)),false);
            }
            if(t==170)screenshot(c,"nature-lash-loadout.png");
            if(t==180) {
                c.setScreen(null);var uuid=c.player.getUuid();
                server.execute(()->{
                    var p=server.getPlayerManager().getPlayer(uuid);WandProgression.grant(p,3);
                    net.minecraft.component.type.NbtComponent.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,p.getMainHandStack(),
                            data->data.putInt("elementalwands:ultimate_charge",100));
                    ModNetworking.syncPlayerData(p);p.networkHandler.requestTeleport(.5,floor,-3.5,0,-12);
                });
            }
            if(t>=190 && t<222){c.player.setYaw(0);c.player.setPitch(-12);}
            if(t==200)require(WandControls.input(false,GLFW.GLFW_KEY_X,GLFW.GLFW_PRESS),"Ultimate input unhandled");
            if(t==201)WandControls.input(false,GLFW.GLFW_KEY_X,GLFW.GLFW_RELEASE);
            if(t>200 && t<240)for(var entity:c.world.getEntities()) {
                if(entity instanceof com.anton.elementalwands.entity.OvergrowthSeedEntity && entity.age>=5 && !sawAcorn) {
                    sawAcorn=true;screenshot(c,"overgrowth-acorn-flight.png");
                }
            }
            if(t==235) {
                var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5,floor,-12.5,0,-15));
            }
            if(t==250) {
                require(sawAcorn,"No synchronized thrown ultimate seed");
                boolean tree=false;for(var entity:c.world.getEntities())if(entity instanceof com.anton.elementalwands.entity.AwakenedTreeEntity)tree=true;
                require(tree,"Seedless ultimate did not grow tree on client");
                c.player.setPitch(-15);screenshot(c,"overgrowth-seedless-tree.png");
            }
            if(t==270) {
                Files.writeString(Path.of("HUB_PASSED.txt"),"Combined Nature native client passed: Lash purchase/equip/input/heal and model, three-tendril knot input/model/removal, spell panel text bounds, X input without seedlings, synchronized acorn flight and grown tree. Screenshots captured; human combat feel pending.\n");
                done=true;c.scheduleStop();
            }
        } catch(Throwable e) {
            done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();
        }
    }
    private void screenshot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{});}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
