package com.anton.elementalwands.entity;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** Production phase, independent hazards, volleys and jump avoidance in a disposable server world. */
public final class GuardianPhaseSmokeMod implements ModInitializer {
    private int tick, ground, waveCount, hits;
    private FracturedGuardianEntity boss;
    private GuardianBossCombat combat;
    private ServerPlayerEntity target, standing, jumping;
    private final Set<UUID> launched = new HashSet<>();
    private final StringBuilder report = new StringBuilder();
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch(Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("PHASE_FAILED.txt"),e.toString()); } catch(Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        tick++; ServerWorld world=server.getOverworld();
        if(tick==25) {
            for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            target=player(server,"PhaseTarget",.5,ground,20.5);target.setInvulnerable(true);
            standing=player(server,"Standing",40.5,ground,.5);
            jumping=player(server,"Jumping",-39.5,ground+1.1,.5);jumping.setNoGravity(true);
            boss=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            boss.setPosition(.5,ground,.5);boss.startFight();world.spawnEntity(boss);
            var field=FracturedGuardianEntity.class.getDeclaredField("combat");field.setAccessible(true);combat=(GuardianBossCombat)field.get(boss);
        }
        if(boss==null)return;
        if(tick>360 && tick<=530) {
            if(standing.getHealth()<20)hits++;
            require(jumping.getHealth()==20,"Jumping player hit by low wave");
        }
        target.setHealth(20);standing.setHealth(20);jumping.setHealth(20);
        if(tick==125) {
            boss.stopReview(); boss.startFight();
            boss.setHealth(boss.getMaxHealth()*.59f);boss.requestPhase();boss.openGuard();
        }
        if(tick==200) {
            require(boss.isGuardOpening() && !boss.isUnstable(),"Phase transition stole an earned guard opening");
        }
        if(tick==325) {
            require(boss.isUnstable() && boss.getPhaseTime(0)>=0,"Phase did not start after guard recovery");
            var output=net.minecraft.storage.NbtWriteView.create(net.minecraft.util.ErrorReporter.EMPTY,world.getRegistryManager());
            boss.writeCustomData(output);
            var copy=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            copy.readCustomData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,world.getRegistryManager(),output.getNbt()));
            require(copy.isUnstable() && Math.abs(copy.getPhaseTime(0)-boss.getPhaseTime(0))<.01,"Phase save/load lost transition clock");
            require(Math.abs(copy.getHealth()-boss.getHealth())<.01,"Phase save/load healed boss");
            report.append("60% threshold waits for full earned opening; phase and transition clock survive save/load without healing.\n");
        }
        if(tick==330) {
            float health=boss.getHealth();
            for(int i=0;i<10 && boss.getGuard()>0;i++) {
                boss.clearGuardHurtWindows();boss.timeUntilRegen=0;
                boss.damage(world,world.getDamageSources().playerAttack(target),100);
            }
            require(boss.getHealth()<health,"Transition made boss invulnerable");
        }
        if(tick==333) {
            require(boss.isGuardOpening() && boss.getPhaseTime(0)<0 && boss.isUnstable(),"Breaking guard during transition did not preserve phase and opening");
            report.append("Transition remains damageable and can be interrupted by an earned guard break.\n");
        }
        if(tick==360) {
            reset(); target.setPosition(.5,ground,10.5);
            standing.setPosition(40.5,ground,.5);jumping.setPosition(-39.5,ground+1.1,.5);
            boss.testAttack(target,GuardianCombatRules.Attack.SHOCKWAVE);waveCount=0;hits=0;
        }
        if(tick>360 && tick<=530) {
            standing.setPosition(40.5,ground,.5);standing.setVelocity(Vec3d.ZERO);
            jumping.setPosition(-39.5,ground+1.1,.5);jumping.setVelocity(Vec3d.ZERO);
            countWaves();
            // Damage happened during the world's tick before this callback; health is reset below.
            require(jumping.getHealth()==20,"Jumping player hit by low wave");
        }
        if(tick==522) {
            require(waveCount==3,"Triple slam emitted "+waveCount+" waves");
            require(boss.getWaveTime(0,0)>=26 || boss.getWaveTime(0,1)>=26,"Last wave disappeared with attack recovery");
        }
        if(tick==530) {
            require(hits==3,"Standing player at 40 blocks received "+hits+" wave hits");
            report.append("Three independent wide waves hit a grounded player 40 blocks away three times; airborne player avoids all three. Last wave survives action recovery.\n");
        }
        if(tick==540) {reset();target.setPosition(.5,ground,20.5);boss.testAttack(target,GuardianCombatRules.Attack.THROW);}
        if(tick>540 && tick<690) {
            for(var rock:world.getEntitiesByClass(GuardianRockEntity.class,boss.getBoundingBox().expand(70),r->r.getVelocity().lengthSquared()>.001))launched.add(rock.getUuid());
        }
        if(tick==690) {
            require(launched.size()==3,"Volley released "+launched.size()+" rocks");
            report.append("Production volley releases exactly three physical projectiles.\n");
        }
        if(tick==700) {reset();target.setPosition(.5,ground,20.5);boss.testAttack(target,GuardianCombatRules.Attack.LEAP);waveCount=0;}
        if(tick>700 && tick<880)countWaves();
        if(tick==880) {
            require(waveCount==3,"Phase-two leap/follow-up emitted "+waveCount+" waves: "+boss.leapStatus());
            require(!boss.hasNoGravity() && boss.getY()<ground+.1,"Leap chain left unsafe gravity/position");
            boss.stopReview();
            require(boss.getWaveTime(0,0)<0 && boss.getWaveTime(0,1)<0 && !boss.isHoldingRock(),"Stop left visible hazards");
            require(world.getEntitiesByClass(GuardianRockEntity.class,boss.getBoundingBox().expand(90),r->r.isAlive()).isEmpty(),"Stop left projectiles");
            report.append("Leap stays committed, lands safely and adds its third wave through a follow-up slam; stop cleans hazards.\n");
            Files.writeString(Path.of("PHASE_PASSED.txt"),report);System.out.println("GUARDIAN PHASE PASSED\n"+report);server.stop(false);
        }
        if(tick>950)throw new AssertionError("Phase fixture timed out");
    }
    private void countWaves(){for(int slot=0;slot<2;slot++)if(boss.getWaveTime(0,slot)==GuardianCombatRules.SLAM_IMPACT)waveCount++;}
    private void reset(){boss.stopReview();boss.setPosition(.5,ground,.5);boss.setVelocity(Vec3d.ZERO);boss.setHealth(boss.getMaxHealth());boss.setNoGravity(false);}
    private static ServerPlayerEntity player(MinecraftServer server,String name,double x,double y,double z)throws Exception {
        var method=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);method.setAccessible(true);
        var player=(ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),name,x,y,z);player.onTeleportationDone();return player;
    }
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
