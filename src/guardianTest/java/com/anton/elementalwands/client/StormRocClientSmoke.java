package com.anton.elementalwands.client;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.roc.*;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.summit.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** Hidden native screenshot rehearsal on the authored summit, with a scripted dummy challenger. */
public final class StormRocClientSmoke implements ClientModInitializer {
    private boolean started, entered, arranged, done;
    private int ticks, scene=-1;
    private volatile String failure;
    private volatile boolean ready;
    private volatile UUID bossId;
    private StormRocEntity serverBoss;
    private ServerPlayerEntity target;
    private BlockPos centre;
    private final AtomicInteger completedShots=new AtomicInteger();
    private int requestedShots;
    private boolean sawBoss,sawEffects,partsChecked;
    private final Map<Integer,Vec3d> serverParts=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> poses=new HashSet<>();
    private final Set<RocEffectEntity.Kind> effects=EnumSet.noneOf(RocEffectEntity.Kind.class);
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>6500)throw new AssertionError("Roc client timeout at scene "+scene);
            if(!started){
                started=true;c.options.pauseOnLostFocus=false;c.options.tutorialStep=net.minecraft.client.tutorial.TutorialStep.NONE;
                c.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);c.getNarratorManager().clear();
                c.options.getViewDistance().setValue(16);c.options.getFov().setValue(70);
                GLFW.glfwSetWindowSize(c.getWindow().getHandle(),1280,720);GLFW.glfwHideWindow(c.getWindow().getHandle());
                c.createIntegratedServerLoader().createAndStart("roc-"+System.currentTimeMillis(),
                    new LevelInfo("Storm Roc native rehearsal",GameMode.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES),DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(4011L,true,false),WorldPresets::createDemoOptions,null);return;
            }
            if(c.world==null||c.player==null||c.getServer()==null)return;
            if(failure!=null)throw new AssertionError(failure);
            var server=c.getServer();UUID id=c.player.getUuid();
            c.setScreen(null);WandWelcome.reset();c.options.hudHidden=true;
            if(!entered){entered=true;onServer(server,()->{
                var player=server.getPlayerManager().getPlayer(id);player.setAttached(EWAttachments.WELCOME_SEEN,true);
                server.getCommandManager().parseAndExecute(player.getCommandSource(),"ew summit enter");
            });return;}
            if(c.world.getRegistryKey()!=StormSummit.WORLD)return;
            if(!arranged){arranged=true;onServer(server,()->arrange(server,id));return;}
            if(!ready)return;
            scene++;
            if(scene>5&&scene<760&&scene%20==0)onServer(server,()->require(serverBoss.fighting(),"Native combat timeline cancelled at scene"+scene+": "+serverBoss.status()));
            if(scene==0)onServer(server,()->camera(server,id,72,140,42,0,138,-88));
            if(scene==65)onServer(server,()->camera(server,id,45,118,35,8,101,4));
            if(scene==70)onServer(server,()->serverBoss.testStrike(centre.toCenterPos().add(8,1,4),false));
            if(scene==100)onServer(server,()->serverBoss.testStrike(centre.toCenterPos().add(-10,1,8),true));
            if(scene==110)onServer(server,()->{camera(server,id,65,145,75,0,120,0);serverBoss.testDive(centre.toCenterPos().add(0,1,0));});
            if(scene==220)onServer(server,()->camera(server,id,60,124,60,0,108,0));
            // Melee aims at the client's copy of the head/neck hurtboxes, so it must follow the
            // server's (they spawned at the far perch; a frozen copy would still be there).
            if(scene==258)onServer(server,()->{serverParts.clear();for(var part:server.getWorld(StormSummit.WORLD).getEntitiesByClass(RocBodyPartEntity.class,new Box(centre).expand(200),p->p.owner()==serverBoss))serverParts.put(part.getId(),part.getEntityPos());});
            if(scene==262){
                require(serverParts.size()==2,"Expected the head and neck hurtboxes on the server, found "+serverParts.size());
                for(var entry:serverParts.entrySet()){var part=c.world.getEntityById(entry.getKey());
                    require(part instanceof RocBodyPartEntity&&part.getEntityPos().distanceTo(entry.getValue())<4,"Client Roc hurtbox is not where the server has it: "+(part==null?"missing":part.getEntityPos())+" vs "+entry.getValue());}
                partsChecked=true;
            }
            if(scene>=280&&(scene-280)%64==0){int action=(scene-280)/64;if(action<StormRocRules.Action.values().length)onServer(server,()->{
                target.setPosition(centre.getX()+.5,101,centre.getZ()+12.5);target.setHealth(target.getMaxHealth());target.onTeleportationDone();
                serverBoss.testAction(target,StormRocRules.Action.values()[action]);
                if(action==0){serverBoss.testTornado(centre.toCenterPos().add(-18,1,0));serverBoss.testStrike(centre.toCenterPos().add(12,1,8),true);}
                camera(server,id,55,125,60,0,108,0);
            });}
            if(scene==760)onServer(server,()->{serverBoss.setHealth(serverBoss.getMaxHealth()*.5f);camera(server,id,60,140,70,0,125,0);});
            var clientBoss=c.world.getEntities().iterator();while(clientBoss.hasNext()){
                var entity=clientBoss.next();if(entity instanceof StormRocEntity roc){sawBoss=true;poses.add(roc.poseName());}if(entity instanceof RocEffectEntity effect){sawEffects=true;effects.add(effect.kind());require(effect.shouldRender(100*100),"Tracked Roc effect was distance-culled at100blocks: "+effect.kind());}
            }
            if(scene==55)shot(c,"roc-perched.png");
            if(scene==75)shot(c,"roc-small-warning.png");if(scene==92)shot(c,"roc-small-bolt.png");
            if(scene==105)shot(c,"roc-large-warning.png");if(scene==132)shot(c,"roc-large-bolt.png");
            if(scene==145)shot(c,"roc-dive-warning.png");if(scene==180)shot(c,"roc-dive-impact.png");if(scene==195)shot(c,"roc-stuck.png");
            for(int i=0;i<StormRocRules.Action.values().length;i++){
                var action=StormRocRules.Action.values()[i];
                if(scene==280+i*64+action.impact+2)shot(c,"roc-"+action.pose+".png");
            }
            if(scene==790)shot(c,"roc-takeoff.png");
            if(scene>=820&&completedShots.get()==requestedShots){
                require(sawBoss,"No synchronized Roc reached the native client");require(sawEffects,"No synchronized Roc effects reached native client");require(poses.containsAll(Set.of("perched","flight","dive","stuck","peck","swing_left","swing_right","scream","wing","pounce","stampede","takeoff")),"Requested tracked poses missing from native client: "+poses);
                require(effects.containsAll(Set.of(RocEffectEntity.Kind.SMALL_STRIKE,RocEffectEntity.Kind.LARGE_STRIKE,RocEffectEntity.Kind.TORNADO,RocEffectEntity.Kind.DIVE_MARKER,RocEffectEntity.Kind.SHOCKWAVE,RocEffectEntity.Kind.SCREAM)),"Required tracked effect kinds missing: "+effects);
                require(requestedShots==16,"Expected16 native screenshots, requested"+requestedShots);
                require(partsChecked,"Client hurtbox positions were never compared");
                Files.writeString(Path.of("ROC_CLIENT_PASSED.txt"),"Scripted native summit rehearsal: "+completedShots.get()+" completed screenshots; synchronized Roc, effects and poses "+poses+"; client head/neck hurtboxes follow the server's. Not human input or Lunar balance approval.\n");
                done=true;c.scheduleStop();
            }
        }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("ROC_CLIENT_FAILED.txt"),error+"\n");}catch(Exception ignored){}done=true;c.scheduleStop();}
    }
    private void arrange(MinecraftServer server,UUID observer)throws Exception{
        var camera=server.getPlayerManager().getPlayer(observer);centre=StormSummit.nearestCentre(camera.getEntityPos());var world=server.getWorld(StormSummit.WORLD);
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){world.getChunk((centre.getX()>>4)+x,z);world.setChunkForced((centre.getX()>>4)+x,z,true);}
        var helper=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,ServerWorld.class,UUID.class,String.class,double.class,double.class,double.class);
        helper.setAccessible(true);target=(ServerPlayerEntity)helper.invoke(null,server,world,UUID.randomUUID(),"RocRehearsal",centre.getX()+.5,101,12.5);
        target.setLoaded(true);target.onTeleportationDone();target.setNoGravity(true);target.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);target.getAbilities().invulnerable=true;target.sendAbilitiesUpdate();
        target.changeGameMode(GameMode.SURVIVAL);target.getAbilities().invulnerable=true;target.sendAbilitiesUpdate();
        serverBoss=new StormRocEntity(ModEntities.STORM_ROC,world);serverBoss.startFight(centre,List.of(target));require(serverBoss.eligible(target),"Scripted survival target was not eligible");world.spawnEntity(serverBoss);bossId=serverBoss.getUuid();
        camera.changeGameMode(GameMode.SPECTATOR);camera(server,observer,72,140,42,0,138,-88);ready=true;
    }
    private void camera(MinecraftServer server,UUID id,double x,double y,double z,double tx,double ty,double tz){
        var player=server.getPlayerManager().getPlayer(id);double dx=tx-x,dz=tz-z,dy=ty-y;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        player.networkHandler.requestTeleport(centre.getX()+x,y,centre.getZ()+z,yaw,pitch);player.setHeadYaw(yaw);player.setNoGravity(true);
    }
    private void onServer(MinecraftServer server,Checked action){server.execute(()->{try{action.run();}catch(Throwable error){failure=error.toString();}});}
    private interface Checked{void run()throws Exception;}
    private void shot(MinecraftClient c,String name){requestedShots++;ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,message->completedShots.incrementAndGet());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
