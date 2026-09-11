package com.anton.elementalwands.entity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Stationary live targets verify each authored attack's real server damage path. */
public final class GuardianCombatSmokeMod implements ModInitializer {
    private int tick,started,action=-1,ground;
    private float dealt;
    private FracturedGuardianEntity guardian;
    private ServerPlayerEntity target;
    private final Map<String,Float> results=new LinkedHashMap<>();
    private static final GuardianCombatRules.Attack[] ATTACKS={GuardianCombatRules.Attack.BEAM,GuardianCombatRules.Attack.THROW,GuardianCombatRules.Attack.SLAM,GuardianCombatRules.Attack.SHOCKWAVE,GuardianCombatRules.Attack.LEAP};
    private static final double[] DISTANCE={18,18,3,8,20};
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {try{run(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("COMBAT_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
    }
    private void run(MinecraftServer server) throws Exception {
        tick++;var world=server.getOverworld();
        if(tick==25) {
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var method=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);method.setAccessible(true);
            target=(ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),"AttackTarget",.5,(double)ground,18.5);
            guardian=new FracturedGuardianEntity(com.anton.elementalwands.registry.ModEntities.FRACTURED_GUARDIAN,world);
            guardian.setPosition(.5,ground,.5);guardian.stopReview();world.spawnEntity(guardian);
            target.onTeleportationDone();
        }
        if(tick==125)next(world); // Allow normal join protection to expire before measuring attacks.
        if(action<0)return;
        dealt+=Math.max(0,target.getMaxHealth()-target.getHealth());target.setHealth(target.getMaxHealth());
        target.setVelocity(net.minecraft.util.math.Vec3d.ZERO);target.setPosition(.5,ground,DISTANCE[action]+.5);
        if(tick-started>=ATTACKS[action].duration+30) {
            if(dealt<=0)throw new AssertionError(ATTACKS[action]+" never damaged an unobstructed stationary Survival target; leap="+guardian.leapStatus());
            results.put(ATTACKS[action].toString(),dealt);
            System.out.println("GUARDIAN COMBAT DAMAGE: "+ATTACKS[action]+" = "+dealt);
            if(guardian.hasNoGravity())throw new AssertionError("Attack left gravity disabled");
            if(action==ATTACKS.length-1) {
                guardian.stopReview();
                if(!world.getEntitiesByClass(GuardianRockEntity.class,new Box(-80,ground-5,-80,80,ground+80,80),e -> !e.isRemoved()).isEmpty())throw new AssertionError("Stopping left rocks behind");
                Files.writeString(Path.of("COMBAT_PASSED.txt"),results.toString()+"\nAll five attacks damaged stationary targets; gravity and projectile cleanup verified.\n");server.stop(false);return;
            }
            next(world);
        }
    }
    private void next(ServerWorld world) {
        action++;dealt=0;started=tick;
        guardian.stopReview();guardian.setPosition(.5,ground,.5);guardian.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        target.setPosition(.5,ground,DISTANCE[action]+.5);target.setHealth(target.getMaxHealth());
        guardian.testAttack(target,ATTACKS[action]);
    }
}
