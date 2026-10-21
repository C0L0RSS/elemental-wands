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
import net.minecraft.sound.SoundCategory;
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
    private final Set<String> phaseShots=new HashSet<>();
    private boolean stormChecked;
    private boolean sawBoss,sawEffects,partsChecked;
    private boolean sawFlightScan,sawFlightFocus,sawFlightRelease;
    private int flightCameraAt=-1,spireCameraAt=-1,landingCameraAt=-1,focusedAt=-1,focusedPlayer=-1,releaseTicks;
    private String focusedPose;
    private long focusedPoseAt;
    private float spireHopTick=-1,spireLandTick=-1;
    private final Map<Integer,Vec3d> serverParts=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> poses=new HashSet<>();
    private final Set<RocEffectEntity.Kind> effects=EnumSet.noneOf(RocEffectEntity.Kind.class);
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(done||c.getOverlay()!=null)return;
        try{
            if(++ticks>9000)throw new AssertionError("Roc client timeout at scene "+scene);
            if(!started){
                // Hidden rehearsals must be silent too; this run has its own options file.
                c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
                c.getSoundManager().stopAll();
                c.options.write();
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
            // The first spire visit: its idle, then its call (tick 34) raises a bolt (44) and the
            // large strikes it calls land at 74. The camera then looks up at them from the rock,
            // under the cap cloud.
            if(scene==0)onServer(server,()->camera(server,id,24,142,-60,2,140,-88));
            if(scene==56)onServer(server,()->camera(server,id,22,102,40,0,140,8));
            if(scene==80)onServer(server,()->camera(server,id,45,118,35,8,101,4));
            if(scene==82)onServer(server,()->serverBoss.testStrike(centre.toCenterPos().add(8,1,4),false));
            if(scene==110)onServer(server,()->{camera(server,id,36,122,42,0,108,0);serverBoss.testDive(centre.toCenterPos().add(0,1,0));});
            if(scene==220)onServer(server,()->camera(server,id,32,112,36,0,104,-6));
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
                camera(server,id,28,112,34,0,104,0);
            });}
            // A close look at the textured tornado, between the bite and the first sweep.
            if(scene==328)onServer(server,()->camera(server,id,-4,104,16,-18,110,0));
            if(scene==336)shot(c,"roc-tornado.png");
            if(scene==760)onServer(server,()->{serverBoss.setHealth(serverBoss.getMaxHealth()*.5f);camera(server,id,60,140,70,0,125,0);});
            if(scene==865)onServer(server,()->camera(server,id,30,112,36,0,145,0));
            if(scene==990)onServer(server,()->camera(server,id,62,123,70,0,157,-30));
            if(scene==1000){require(SummitStormClient.level(1)>.95,"Charged summit storm did not roll in: "+SummitStormClient.level(1));stormChecked=true;shot(c,"roc-storm-sky.png");}
            if(scene==1010)onServer(server,()->camera(server,id,58,133,62,0,106,0));
            if(scene==1030)shot(c,"roc-storm-tornadoes.png");
            if(scene==1080)onServer(server,()->{require(serverBoss.phaseTwo(),"Chain rehearsal began before phase two");serverBoss.testChain();camera(server,id,45,134,55,0,108,12);});
            if(scene==1300)onServer(server,()->camera(server,id,35,101,25,-25,106,-20));
            if(scene==1310)shot(c,"roc-storm-ground.png");
            if(scene==1330)onServer(server,()->{serverBoss.testAction(target,StormRocRules.Action.BITE);serverBoss.testStrike(centre.toCenterPos().add(28,0,12),true);camera(server,id,46,115,36,28,101,12);});
            var clientBoss=c.world.getEntities().iterator();while(clientBoss.hasNext()){
                var entity=clientBoss.next();if(entity instanceof StormRocEntity roc){sawBoss=true;poses.add(roc.poseName());if(roc.poseName().equals("flight")||roc.poseName().equals("spire_flight"))observeFlight(c,roc);if(roc.poseName().equals("land"))observeLanding(c,roc);}if(entity instanceof RocEffectEntity effect){sawEffects=true;effects.add(effect.kind());require(effect.shouldRender(100*100),"Tracked Roc effect was distance-culled at100blocks: "+effect.kind());}
            }
            // Use synchronized effect ages for brief phase-two shots, allowing the server camera
            // teleport to reach the client before the framebuffer is captured.
            for(var entity:c.world.getEntities()){
                if(entity instanceof RocEffectEntity e){
                    // Its call's bolt and the first of the large bolts it calls down, timed by their own ages.
                    if(e.kind()==RocEffectEntity.Kind.CALL_BOLT&&scene<110&&e.effectTicks(0)>=6&&e.effectTicks(0)<8&&phaseShots.add("call"))shot(c,"roc-call.png");
                    if(e.kind()==RocEffectEntity.Kind.LARGE_STRIKE&&scene>56&&scene<110&&e.effectTicks(0)>=e.warningTicks()+1&&e.effectTicks(0)<e.warningTicks()+3&&phaseShots.add("large"))shot(c,"roc-large-bolt.png");
                    if(e.kind()==RocEffectEntity.Kind.CHARGE_BOLT&&e.effectTicks(0)>=3&&e.effectTicks(0)<6&&scene<1000&&phaseShots.add("charge"))shot(c,"roc-charge-bolt.png");
                    if(e.kind()==RocEffectEntity.Kind.DIVE_MARKER&&scene>1080&&scene<1140){
                        if(e.effectTicks(0)<5)onServer(server,()->camera(server,id,e.getX()-centre.getX()+35,128,e.getZ()-centre.getZ()+38,e.getX()-centre.getX(),102,e.getZ()-centre.getZ()));
                        if(e.effectTicks(0)>=8&&phaseShots.add("mark"))shot(c,"roc-chain-mark.png");
                    }
                    if(e.kind()==RocEffectEntity.Kind.SHOCKWAVE&&scene>1140&&scene<1170&&e.effectTicks(0)>=5&&phaseShots.add("slam"))shot(c,"roc-chain-slam.png");
                    if(e.kind()==RocEffectEntity.Kind.LIVE_GROUND&&scene>1380&&e.effectTicks(0)>=12&&phaseShots.add("live"))shot(c,"roc-live-ground.png");
                }
                if(entity instanceof StormRocEntity roc&&roc.poseName().equals("hop")&&scene>1148&&scene<1230){
                    if(scene<1153)onServer(server,()->camera(server,id,roc.getX()-centre.getX()+38,125,roc.getZ()-centre.getZ()+45,roc.getX()-centre.getX(),roc.getY()+5,roc.getZ()-centre.getZ()));
                    if(scene>=1158&&phaseShots.add("hop"))shot(c,"roc-chain-hop.png");
                }
            }
            if(scene==20){require(c.world.getPlayerByUuid(target.getUuid())!=null,"Scripted gaze target never spawned on the client");shot(c,"roc-perched.png");}if(scene==66)shot(c,"roc-large-warning.png");
            if(scene==90)shot(c,"roc-small-warning.png");if(scene==104)shot(c,"roc-small-bolt.png");
            if(scene==145)shot(c,"roc-dive-warning.png");if(scene==180)shot(c,"roc-dive-impact.png");if(scene==195)shot(c,"roc-stuck.png");
            for(int i=0;i<StormRocRules.Action.values().length;i++){
                var action=StormRocRules.Action.values()[i];
                if(scene==280+i*64+action.impact+2)shot(c,"roc-"+action.pose+".png");
            }
            if(scene==790)shot(c,"roc-takeoff.png");
            if(scene>=1450&&completedShots.get()==requestedShots){
                require(sawBoss,"No synchronized Roc reached the native client");require(sawEffects,"No synchronized Roc effects reached native client");require(poses.containsAll(Set.of("perched","call","land","flight","spire_flight","dive","stuck","bite","swing_left","swing_right","scream","wing","pounce","stampede","takeoff","hop")),"Requested tracked poses missing from native client: "+poses);
                require(effects.containsAll(Set.of(RocEffectEntity.Kind.SMALL_STRIKE,RocEffectEntity.Kind.LARGE_STRIKE,RocEffectEntity.Kind.TORNADO,RocEffectEntity.Kind.DIVE_MARKER,RocEffectEntity.Kind.SHOCKWAVE,RocEffectEntity.Kind.SCREAM,RocEffectEntity.Kind.LIVE_GROUND,RocEffectEntity.Kind.CHARGE_BOLT,RocEffectEntity.Kind.STORM_BOLT,RocEffectEntity.Kind.CALL_BOLT)),"Required tracked effect kinds missing: "+effects);
                require(sawFlightScan,"Authored flying Roc clip never looked down toward the ground");
                require(sawFlightFocus,"Flying Roc never focused a player below beyond its authored scan");
                require(sawFlightRelease,"Flying Roc never released its player focus back to the authored scan within the same clip");
                require(phaseShots.containsAll(Set.of("charge","flight-look","spire-hop","spire-land")),"Required charge/flight/spire screenshots missing: "+phaseShots);
                require(requestedShots==29,"Expected29 native screenshots, requested"+requestedShots);
                require(stormChecked&&SummitStormClient.level(1)>.95,"Storm level did not remain above.95");
                require(partsChecked,"Client hurtbox positions were never compared");
                Files.writeString(Path.of("ROC_CLIENT_PASSED.txt"),"Scripted native summit rehearsal: "+completedShots.get()+" completed screenshots; phase two storm level="+SummitStormClient.level(1)+"; synchronized Roc, effects "+effects+" and poses "+poses+"; authored flight scan, player focus and release checked; spire hop clip tick="+spireHopTick+", landing clip tick="+spireLandTick+"; client head/neck hurtboxes follow the server's. Not human input or Lunar balance approval.\n");
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
        // The server-only helper bypasses normal login. Advertise it before its spawn so
        // the native client can render it and the Roc can actually watch this player.
        camera.networkHandler.sendPacket(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.entryFromPlayer(List.of(target)));
        camera.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(target.getId(),target.getUuid(),target.getX(),target.getY(),target.getZ(),target.getPitch(),target.getYaw(),target.getType(),0,target.getVelocity(),target.getHeadYaw()));
        serverBoss=new StormRocEntity(ModEntities.STORM_ROC,world);serverBoss.startFight(centre,List.of(target));require(serverBoss.eligible(target),"Scripted survival target was not eligible");world.spawnEntity(serverBoss);bossId=serverBoss.getUuid();
        camera.changeGameMode(GameMode.SPECTATOR);camera(server,observer,24,142,-60,2,140,-88);ready=true;
    }
    private void camera(MinecraftServer server,UUID id,double x,double y,double z,double tx,double ty,double tz){
        var player=server.getPlayerManager().getPlayer(id);double dx=tx-x,dz=tz-z,dy=ty-y;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        player.networkHandler.requestTeleport(centre.getX()+x,y,centre.getZ()+z,yaw,pitch);player.setHeadYaw(yaw);player.setNoGravity(true);
    }
    private void observeFlight(MinecraftClient c,StormRocEntity roc){
        var forward=StormRocMotion.bill(StormRocMotion.frame(roc,1).channels());
        var plain=StormRocMotion.bill(StormRocPose.pose(roc.poseName(),roc.poseTicks(1)));
        sawFlightScan|=plain.y<-.20;
        if(roc.poseName().equals("spire_flight")&&!phaseShots.contains("spire-hop")){
            float tick=roc.poseTicks(1);
            if(spireCameraAt<0&&tick<=3){
                spireCameraAt=scene;
                double x=roc.getX()-centre.getX(),z=roc.getZ()-centre.getZ(),y=roc.getY()+4;
                onServer(c.getServer(),()->camera(c.getServer(),c.player.getUuid(),x+24,101.6,z+38,x,y,z));
            }else if(spireCameraAt>=0&&scene>=spireCameraAt+4&&tick>=10&&tick<=16){
                spireHopTick=tick;phaseShots.add("spire-hop");shot(c,"roc-spire-hop.png");
            }
        }
        boolean released=false;
        for(var player:c.world.getPlayers()){
            if(!player.isAlive()||player.isSpectator()||player.isCreative())continue;
            Vec3d d=player.getEyePos().subtract(roc.socket("head"));if(d.y>=-4)continue;
            double a=Math.toRadians(roc.getYaw()),cs=Math.cos(a),sn=Math.sin(a);
            var aim=new org.joml.Vector3d(d.x*cs+d.z*sn,d.y,d.x*sn-d.z*cs).normalize();
            // A later matching authored direction only proves release when that direction
            // is still clearly different from the player it had focused, in this same clip.
            if(player.getId()==focusedPlayer&&roc.poseName().equals(focusedPose)&&roc.poseStart()==focusedPoseAt&&scene>focusedAt&&forward.dot(plain)>.995&&plain.dot(aim)<.93)released=true;
            if(forward.dot(aim)>.97&&forward.dot(aim)>plain.dot(aim)+.04){
                sawFlightFocus=true;
                if(focusedAt<0||!roc.poseName().equals(focusedPose)||roc.poseStart()!=focusedPoseAt){
                    focusedAt=scene;focusedPlayer=player.getId();focusedPose=roc.poseName();focusedPoseAt=roc.poseStart();
                }
                if(scene>800&&scene<1000&&!phaseShots.contains("flight-look")){
                    if(flightCameraAt<0){
                        flightCameraAt=scene;
                        double x=roc.getX()-centre.getX(),z=roc.getZ()-centre.getZ(),y=roc.getY()+4;
                        onServer(c.getServer(),()->camera(c.getServer(),c.player.getUuid(),x+16,101.6,z+24,x,y,z));
                    }else if(scene>=flightCameraAt+4){
                        phaseShots.add("flight-look");shot(c,"roc-flight-look-down.png");
                    }
                }
            }
        }
        releaseTicks=released?releaseTicks+1:0;
        sawFlightRelease|=releaseTicks>=3;
    }
    private void observeLanding(MinecraftClient c,StormRocEntity roc){
        if(phaseShots.contains("spire-land"))return;
        float tick=roc.poseTicks(1);
        if(landingCameraAt<0&&tick<=3){
            landingCameraAt=scene;
            double x=roc.getX()-centre.getX(),z=roc.getZ()-centre.getZ(),y=roc.getY()+4;
            onServer(c.getServer(),()->camera(c.getServer(),c.player.getUuid(),x+24,y,z+38,x,y,z));
        }else if(landingCameraAt>=0&&scene>=landingCameraAt+4&&tick>=10&&tick<=15){
            spireLandTick=tick;phaseShots.add("spire-land");shot(c,"roc-spire-land.png");
        }
    }
    private void onServer(MinecraftServer server,Checked action){server.execute(()->{try{action.run();}catch(Throwable error){failure=error.toString();}});}
    private interface Checked{void run()throws Exception;}
    private void shot(MinecraftClient c,String name){requestedShots++;ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,message->completedShots.incrementAndGet());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
