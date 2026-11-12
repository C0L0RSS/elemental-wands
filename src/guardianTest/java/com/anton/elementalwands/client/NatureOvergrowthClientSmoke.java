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
    private boolean started, arranged, done, sawLash;
    private volatile boolean ready;
    private boolean sawSpit, sawKick;
    /** Block-nature effect kinds this client has run, read from BlockNatureEffects without injecting any. */
    private final java.util.Set<String> effects=new java.util.HashSet<>();
    private int healSeen=-1;
    public void onInitializeClient() {ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c) {
        if(done || c.getOverlay()!=null)return;
        try {
            if(++ticks>1600)throw new AssertionError("Client fixture timed out");
            if(!started) {
                started=true;c.options.pauseOnLostFocus=false;c.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(0.0);
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
                        w.setBlockState(new BlockPos(x,floor-1,z),(z<0?Blocks.GRASS_BLOCK:Blocks.STONE_BRICKS).getDefaultState()); // A lawn behind the start for Full Bloom.
                        for(int y=0;y<12;y++)w.setBlockState(new BlockPos(x,floor+y,z),Blocks.AIR.getDefaultState());
                    }
                    w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);
                    var p=server.getPlayerManager().getPlayer(uuid);p.setAttached(EWAttachments.WELCOME_SEEN,true);
                    p.setAttached(EWAttachments.AFFINITY,"NATURE");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.FRACTURED_WAND));
                    WandProgression.earn(p,WizardAffinity.NATURE,1000);WandProgression.purchase(p,"NATURE","thorn_lash");
                    WandProgression.purchase(p,"NATURE","tendril_bloom");WandLoadouts.equip(p,"NATURE",0,"thorn_lash");WandLoadouts.equip(p,"NATURE",1,"tendril_bloom");
                    // Loadouts lock once combat starts, so the ultimate takes its X slot now.
                    WandProgression.grant(p,3);WandLoadouts.equip(p,"NATURE",2,"overgrowth");
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
            observe();
            if(healSeen<0 && effects.contains("Heal"))healSeen=t;
            if(healSeen>0 && t==healSeen+6)screenshot(c,"nature-heal-cue.png");
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
            if(t==85){require(sawLash,"No synchronized Lash entity");require(c.player.getHealth()>10,"Lash did not heal client");
                require(effects.contains("Heal") && effects.contains("Pop"),"The bite drew no pollen or heal cue: "+effects);}
            // The bite at t=50 marked the zombie: one seed turns above it, for this player only.
            if(t==90){require(markedZombie(c),"No synchronized seed mark on the bitten zombie");screenshot(c,"thornbite-mark.png");}
            // Sentry Bloom, aimed at the floor in front: the flower grows there and spits at the marked zombie.
            if(t>90 && t<=96){c.player.setYaw(0);c.player.setPitch(35);}
            if(t==95) {GLFW.glfwFocusWindow(c.getWindow().getHandle());require(ClientPlayerData.loadout().get(1).equals("tendril_bloom"),"Bloom slot wrong");require(WandControls.input(true,1,GLFW.GLFW_PRESS),"Bloom input unhandled");}
            if(t==96)WandControls.input(true,1,GLFW.GLFW_RELEASE);
            if(t>96 && t<=110){c.player.setYaw(0);c.player.setPitch(18);}
            // Step to the side: the head has turned from straight up to face the marked zombie.
            if(t==111){var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(-3.5,floor,2.5,-90,12));}
            if(t>114 && t<=132){c.player.setYaw(-90);c.player.setPitch(12);}
            // Then stand in the line of fire and look back: this is the face a target sees.
            if(t==133){var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5,floor,5.5,180,6));}
            if(t>136 && t<=156){c.player.setYaw(180);c.player.setPitch(6);}
            if(t==142)screenshot(c,"sentry-bloom-face.png");
            if(t==130) {
                var head=bloomHead(c);
                require(head!=null && c.world.getEntityById(head.targetId()) instanceof ZombieEntity,"The flower's head has no synchronized target");
                require(head.headTilt(1)>1.2f && Math.abs(head.headYaw(1))<.35f,"The flower's head has not turned to face the zombie: tilt "+head.headTilt(1)+" yaw "+head.headYaw(1));
                screenshot(c,"sentry-bloom-aimed.png");
            }
            if(t>140 && t<156 && !sawKick){var head=bloomHead(c);if(head!=null && head.recoil(1)>.5f){sawKick=true;screenshot(c,"sentry-bloom-recoil.png");}}
            if(t==156){require(sawKick,"The flower's head never kicked back on a shot");
                var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5,floor,.5,0,0));}
            // Step the marked zombie back so the flower's seed has a flight a client can see.
            if(t==97)server.execute(()->server.getOverworld().getEntitiesByClass(ZombieEntity.class,new net.minecraft.util.math.Box(-1,floor-1,2,2,floor+3,5),e->true).forEach(z->z.setPosition(.5,floor,9.5)));
            if(t==106) {
                var bloom=c.world.getBlockState(new BlockPos(0,floor,2));
                require(bloom.isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_SEEDLING),"No synchronized flower where the cast aimed: "+bloom);
                require(bloom.getCollisionShape(c.world,new BlockPos(0,floor,2)).isEmpty(),"The flower is solid on the client");
                screenshot(c,"sentry-bloom-planted.png");
            }
            if(t>96 && t<150 && !sawSpit)for(var entity:c.world.getEntities()) {
                if(entity instanceof com.anton.elementalwands.entity.SeedProjectileEntity seed && seed.fromFlower() && seed.age>=2){sawSpit=true;screenshot(c,"sentry-bloom-spit.png");break;}
            }
            if(t==150){require(sawSpit,"The flower never spat a synchronized seed at the marked zombie");
                require(effects.contains("SeedTrail"),"The flower's seed drew no trail: "+effects);}
            if(t==160)c.setScreen(new com.anton.elementalwands.client.screen.WandHubScreen());
            if(t==165) {
                for (String id : new String[]{"seed","thorn_lash","tendril_bloom","springbloom","overgrowth"}) {
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
            // Full Bloom, cast on a lawn eight blocks behind the flower.
            if(t==180) {
                c.setScreen(null);var uuid=c.player.getUuid();
                server.execute(()->{
                    var p=server.getPlayerManager().getPlayer(uuid);WandProgression.grant(p,3);
                    net.minecraft.component.type.NbtComponent.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,p.getMainHandStack(),
                            data->data.putInt("elementalwands:ultimate_charge",100));
                    p.setHealth(8);ModNetworking.syncPlayerData(p);p.networkHandler.requestTeleport(.5,floor,-6.5,0,10);
                });
            }
            if(t>=190 && t<=216){c.player.setYaw(0);c.player.setPitch(18);}
            if(t==196)c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            if(t==200)require(WandControls.input(false,GLFW.GLFW_KEY_X,GLFW.GLFW_PRESS),"Ultimate input unhandled");
            if(t==201)WandControls.input(false,GLFW.GLFW_KEY_X,GLFW.GLFW_RELEASE);
            if(t==207)screenshot(c,"full-bloom-wave.png");
            if(t==216) {
                c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                require(c.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION)
                        && c.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.ABSORPTION),"Full Bloom did not bless the caster on the client");
                int sprouted=0;
                for(int x=-10;x<=10;x++)for(int z=-17;z<0;z++){var state=c.world.getBlockState(new BlockPos(x,floor,z));
                    if(state.isOf(Blocks.SHORT_GRASS)||state.isIn(net.minecraft.registry.tag.BlockTags.SMALL_FLOWERS))sprouted++;}
                require(sprouted>=10,"The petal wave left no growth on the lawn: "+sprouted);
                require(effects.contains("Bloom"),"Full Bloom drew no petal wave: "+effects);
                for(int x=-10;x<=10;x++)for(int z=0;z<=6;z++)require(c.world.getBlockState(new BlockPos(x,floor,z)).isAir()
                        || c.world.getBlockState(new BlockPos(x,floor,z)).isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_SEEDLING),"The wave planted on stone");
            }
            if(t==218)screenshot(c,"full-bloom-lawn.png");
            // Beside the flower: it has grown a forked stem and a second head, each on its own zombie.
            if(t==219){var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(-3.5,floor,2.5,-90,6));}
            if(t>222 && t<=246){c.player.setYaw(-90);c.player.setPitch(6);}
            if(t==240) {
                var grown=c.world.getBlockState(new BlockPos(0,floor,2));
                require(grown.isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_SEEDLING) && grown.get(com.anton.elementalwands.block.NatureSeedlingBlock.GROWN),"The flower's base is not grown on the client: "+grown);
                java.util.List<com.anton.elementalwands.entity.SentryBloomEntity> heads=new java.util.ArrayList<>();
                for(var entity:c.world.getEntities())if(entity instanceof com.anton.elementalwands.entity.SentryBloomEntity head)heads.add(head);
                require(heads.size()==2 && heads.stream().anyMatch(h->h.mountPoint().squaredDistanceTo(com.anton.elementalwands.entity.SentryBloomEntity.MAIN_STEM_TOP)<1e-6)
                        && heads.stream().anyMatch(h->h.mountPoint().squaredDistanceTo(com.anton.elementalwands.entity.SentryBloomEntity.BRANCH_TOP)<1e-6),"The grown flower does not have two synchronized heads on its stem and branch: "+heads.size());
                screenshot(c,"full-bloom-twin-side.png");
            }
            if(t==247){var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5,floor,6.0,180,4));}
            if(t>250 && t<=266){c.player.setYaw(180);c.player.setPitch(4);}
            if(t==262)screenshot(c,"full-bloom-twin-face.png");
            if(t==268){var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(.5,floor,-3.5,0,0));}
            // Twelve seconds after the cast it is one head again.
            if(t==460) {
                int heads=0;for(var entity:c.world.getEntities())if(entity instanceof com.anton.elementalwands.entity.SentryBloomEntity)heads++;
                require(heads==1 && !c.world.getBlockState(new BlockPos(0,floor,2)).get(com.anton.elementalwands.block.NatureSeedlingBlock.GROWN),"The flower stayed grown after Full Bloom: "+heads);
            }
            // Back beside the flower for its last two seconds: it hangs its head and stops shooting.
            if(t==756){var uuid=c.player.getUuid();server.execute(()->server.getPlayerManager().getPlayer(uuid).networkHandler.requestTeleport(-3.5,floor,2.5,-90,12));}
            if(t>760 && t<=806){c.player.setYaw(-90);c.player.setPitch(12);}
            if(t==800) {
                var head=bloomHead(c);
                require(head!=null && head.wilting() && head.headTilt(1)>1.2f,"The flower is not wilting in its last two seconds");
                screenshot(c,"sentry-bloom-wilting.png");
            }
            if(t==790)screenshot(c,"sentry-bloom-wilt-petals.png");
            if(t==806) {
                Files.writeString(Path.of("HUB_PASSED.txt"),"Combined Nature native client passed: Lash purchase/equip/input/heal and model, synchronized seed mark, Sentry Bloom input with a synchronized pass-through flower whose head turns to the marked zombie, kicks back as it spits and wilts at the end, spell panel text bounds, X input for Full Bloom with its synchronized blessing, growth on grass only, a grown flower with two heads on its forks and one head again afterwards; block-nature bite pollen, heal cue, seed trail and petal wave ran. Screenshots captured; human combat feel pending.\n");
                done=true;c.scheduleStop();
            }
        } catch(Throwable e) {
            done=true;e.printStackTrace();try{Files.writeString(Path.of("HUB_FAILED.txt"),e.toString());}catch(Exception ignored){}c.scheduleStop();
        }
    }
    private void observe() throws ReflectiveOperationException {
        var field=com.anton.elementalwands.client.blocknature.BlockNatureEffects.class.getDeclaredField("EFFECTS");
        field.setAccessible(true);
        for(Object fx:(java.util.List<?>)field.get(null))effects.add(fx.getClass().getSimpleName());
    }
    private void screenshot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,text->{});}
    private static com.anton.elementalwands.entity.SentryBloomEntity bloomHead(MinecraftClient c){
        for(var entity:c.world.getEntities())if(entity instanceof com.anton.elementalwands.entity.SentryBloomEntity head)return head;
        return null;
    }
    private static boolean markedZombie(MinecraftClient c){return c.world.getEntityById(ClientPlayerData.seedMarkEntity()) instanceof ZombieEntity && ClientPlayerData.seedMarkUntil()>c.world.getTime();}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
