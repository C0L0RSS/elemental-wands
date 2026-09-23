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
public final class NatureExpansionClientSmoke implements ClientModInitializer {
    private int ticks, scene, floor;
    private boolean started, arranged, done, sawLash, sawKnot;
    private volatile boolean ready;
    private BlockPos knot;
    private int originalFov;
    private final java.util.Set<String> frames = new java.util.HashSet<>();
    public void onInitializeClient() {ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c) {
        if(done || c.getOverlay()!=null)return;
        try {
            if(++ticks>1600)throw new AssertionError("Client fixture timed out");
            if(!started) {
                started=true;c.options.pauseOnLostFocus=false;originalFov=c.options.getFov().getValue();
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
                    for(int x=-12;x<=12;x++)for(int z=-12;z<=20;z++) {
                        w.setBlockState(new BlockPos(x,floor-1,z),Blocks.STONE_BRICKS.getDefaultState());
                        for(int y=0;y<12;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());
                    }
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);
                    p.setAttached(EWAttachments.AFFINITY,"NATURE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                    WandProgression.earn(p,WizardAffinity.NATURE,1000);WandProgression.purchase(p,"NATURE","thorn_lash");
                    WandProgression.purchase(p,"NATURE","tendril_bloom");WandLoadouts.equip(p,"NATURE",0,"thorn_lash");WandLoadouts.equip(p,"NATURE",1,"tendril_bloom");
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
            if(t==45){validateBitePath();GLFW.glfwFocusWindow(c.getWindow().getHandle());WandControls.reset();}
            if(t>=45 && t<=90){c.player.setYaw(0);c.player.setPitch(0);}
            if(t==50) {
                require(ClientPlayerData.loadout().getFirst().equals("thorn_lash"),"Lash loadout not synchronized");
                require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Basic input unhandled");
            }
            if(t==51)WandControls.input(true,0,GLFW.GLFW_RELEASE);
            if(t>50 && t<85)for(var entity:c.world.getEntities()) {
                if(entity instanceof ThornLashEntity lash && lash.elapsed(0)>=1 && !sawLash){sawLash=true;screenshot(c,"thornbite-first-person.png");}
            }
            if(t==85){require(sawLash,"No synchronized Lash entity");require(c.player.getHealth()>10,"Lash did not heal client");}
            if(t==95) {GLFW.glfwFocusWindow(c.getWindow().getHandle());require(ClientPlayerData.loadout().get(1).equals("tendril_bloom"),"Bloom slot wrong");require(WandControls.input(true,1,GLFW.GLFW_PRESS),"Bloom input unhandled");knot=c.player.getBlockPos();}
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
                for (String id : new String[]{"thorn_lash","tendril_bloom"}) {
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
            if(t==180) {c.setScreen(null);c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                server.execute(()->{
                    server.getPlayerManager().getPlayer(c.player.getUuid()).networkHandler.requestTeleport(.5,floor,.5,0,0);
                    server.getOverworld().getEntitiesByClass(ZombieEntity.class,new Box(-15,floor-2,-15,15,floor+8,25),e->true).forEach(Entity::discard);
                });}
            if(t>=185 && t<=215){c.player.setYaw(0);c.player.setPitch(0);}
            if(t==195){GLFW.glfwFocusWindow(c.getWindow().getHandle());require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Third-person bite input unhandled");}
            if(t==196)WandControls.input(true,0,GLFW.GLFW_RELEASE);
            if(t==220){c.options.getFov().setValue(110);c.setCameraEntity(c.player);c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                c.player.setMainArm(net.minecraft.util.Arm.LEFT);
                server.execute(()->server.getPlayerManager().getPlayer(c.player.getUuid()).setMainArm(net.minecraft.util.Arm.LEFT));}
            if(t==235){require(c.player.getMainArm()==net.minecraft.util.Arm.LEFT,"Left hand did not apply");GLFW.glfwFocusWindow(c.getWindow().getHandle());require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Left bite input unhandled");}
            if(t==236)WandControls.input(true,0,GLFW.GLFW_RELEASE);
            for(var entity:c.world.getEntities()) if(entity instanceof ThornLashEntity lash) {
                String phase=t<180?"right":t<220?"third":t<270?"left":"moving";
                String frame="thornbite-"+phase+"-"+(int)lash.elapsed(0)+".png";
                if(frames.add(frame))screenshot(c,frame);
            }
            if(t==260){c.options.getFov().setValue(originalFov);c.player.setMainArm(net.minecraft.util.Arm.RIGHT);
                server.execute(()->server.getPlayerManager().getPlayer(c.player.getUuid()).setMainArm(net.minecraft.util.Arm.RIGHT));}
            if(t==280){c.player.setYaw(0);c.player.setPitch(0);GLFW.glfwFocusWindow(c.getWindow().getHandle());require(WandControls.input(true,0,GLFW.GLFW_PRESS),"Moving bite input unhandled");}
            if(t==281)WandControls.input(true,0,GLFW.GLFW_RELEASE);
            if(t>=283 && t<=287)c.player.setYaw((t-282)*9);
            if(t==300) {
                require(frames.stream().anyMatch(f->f.startsWith("thornbite-moving-")),"Missing moving-wand preview");
                Files.writeString(Path.of("HUB_PASSED.txt"),"Native client passed: purchased/equipped Lash, mouse casting, synchronized bite entity and health gain, secondary input, custom knot block model, source destruction, wand-tip emergence/return invariants, right/left/third-person and moving-wand screenshots, wide-FOV left hand. Human combat feel pending.\n");
                done=true;c.scheduleStop();
            }
        } catch(Throwable e) {
            done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();
        }
    }
    private static void validateBitePath() {
        // Rendering must meet the aimed contact while both ends of the animation live at the wand.
        Vec3d contact=new Vec3d(0,1.62,4.5),direction=new Vec3d(0,0,1);
        for(int hand:new int[]{-1,1}) for(float hitTime:new float[]{1.2f,3f}) {
            Vec3d root=new Vec3d(hand*.65,1.3,.5),outward=new Vec3d(hand,0,0);
            var initial=new com.anton.elementalwands.client.renderer.ThornbiteVisual.Snapshot(contact,direction,0,hitTime,false);
            var start=com.anton.elementalwands.client.renderer.ThornbiteVisual.pose(initial,root,outward,null);
            require(start.tip().distanceTo(root)<1E-6,"Flytrap starts at eye instead of wand");
            var strike=new com.anton.elementalwands.client.renderer.ThornbiteVisual.Snapshot(contact,direction,hitTime,hitTime,false);
            require(com.anton.elementalwands.client.renderer.ThornbiteVisual.pose(strike,root,outward,null).tip().distanceTo(contact)<1E-6,"Visual bite misses authoritative contact");
            Vec3d movedRoot=root.add(hand*.4,.3,-.2);
            var finished=new com.anton.elementalwands.client.renderer.ThornbiteVisual.Snapshot(contact,direction,hitTime+5,hitTime,false);
            require(com.anton.elementalwands.client.renderer.ThornbiteVisual.pose(finished,movedRoot,outward,null).tip().distanceTo(movedRoot)<1E-6,"Bite returns to old origin instead of moving wand");
            var returning=new com.anton.elementalwands.client.renderer.ThornbiteVisual.Snapshot(contact,direction,hitTime+4,hitTime,false);
            var pose=com.anton.elementalwands.client.renderer.ThornbiteVisual.pose(returning,root,outward,null);
            require(pose.tip().x*hand>.45,"Retracting jaw occupies center of view");
        }
    }
    private void screenshot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{});}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
