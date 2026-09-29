package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

/**
 * The server validates and commits the arc, owns cooldown, fall protection and the landing wave.
 * The caster's client flies the committed arc with real collision, like any vanilla player movement;
 * the server ends the leap without a wave if the reported body leaves the arc or never arrives.
 */
public final class FireLeapManager {
    /** How far a reported position may sit from the arc (takeoff drift, latency), and how close a landing must be. */
    private static final double STRAY=2.5, ARRIVAL=1.5;
    /** Extra ticks allowed for the caster's landing to reach the server. */
    private static final int GRACE=40;
    private record Leap(ServerPlayerEntity player,ServerWorld world,Vec3d from,Vec3d to,long started,int duration) {}
    private record Wave(ServerPlayerEntity owner,ServerWorld world,Vec3d origin,long started,Set<UUID> hits) {}
    private static final Map<UUID,Leap> LEAPS=new HashMap<>();
    private static final List<Wave> WAVES=new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for(var leap:List.copyOf(LEAPS.values())) tick(leap);
            WAVES.removeIf(FireLeapManager::tick);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> end(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { LEAPS.clear();WAVES.clear(); });
    }
    public static boolean flying(ServerPlayerEntity p) { return LEAPS.containsKey(p.getUuid()); }
    /** Fall damage never applies to a committed arc, including a landing packet that arrives before the server tick. */
    public static boolean protectedFall(ServerPlayerEntity p) { var leap=LEAPS.get(p.getUuid());return leap!=null && leap.player==p; }
    public static boolean commit(ServerPlayerEntity p,Vec3d target) {
        var world=p.getEntityWorld();
        if(!p.isAlive() || p.isSpectator() || p.hasVehicle() || p.isGliding() || p.isTouchingWater() || flying(p)
                || EWAttachments.getAffinity(p)!=WizardAffinity.FIRE || !(p.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandLoadouts.get(p).contains("fire_hop") || !WandProgression.owns(p,WandSpells.find("fire_hop"))
                || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(p) || HollowPurpleChargeManager.isCharging(world,p)
                || FireBuildManager.hopRemaining(p)>0) return false;
        // A normal sprint jump is allowed, but a high fall cannot be converted into another flight.
        if(!FireLeapRules.supported(world,p,p.getEntityPos(),1.5) || !FireLeapRules.validTarget(p,target)) {
            p.sendMessage(Text.literal("No clear leap path. Aim toward open ground or a reachable ledge."),true);return false;
        }
        if(!AbstractWandItem.tryStartCooldown(world,p,p.getMainHandStack(),AbstractWandItem.Ability.SECONDARY,0)) return false;
        Vec3d from=p.getEntityPos();
        LEAPS.put(p.getUuid(),new Leap(p,world,from,target,world.getTime(),FireLeapRules.duration(from,target)));
        FireBuildManager.stop(p);p.fallDistance=0;
        var data=FireBuildManager.state(p);data.putLong("hop_ready",world.getTime()+FireBuildRules.HOP_COOLDOWN);p.setAttached(EWAttachments.FIRE_BUILD_STATE,data);
        WandLoadouts.markCombat(p);ModNetworking.syncFireBuild(p);
        broadcast(p,new ModNetworking.FireLeapPayload(p.getId(),from,target,true));
        world.spawnParticles(ModParticles.FIRE_EMBER,p.getX(),p.getY()+.15,p.getZ(),16,.35,.1,.35,.05);
        world.spawnParticles(ModParticles.FIRE_FLAME_RIBBON,p.getX(),p.getY()+.1,p.getZ(),8,.25,.05,.25,.02);
        world.playSound(null,p.getBlockPos(),SoundEvents.ITEM_FIRECHARGE_USE,SoundCategory.PLAYERS,.9f,.8f);
        world.playSound(null,p.getBlockPos(),SoundEvents.ENTITY_BLAZE_SHOOT,SoundCategory.PLAYERS,.45f,.7f);
        return true;
    }
    private static void tick(Leap leap) {
        var p=leap.player;long age=leap.world.getTime()-leap.started;
        if(!p.isAlive() || p.isRemoved() || p.isSpectator() || p.isDisconnected() || p.getEntityWorld()!=leap.world
                || p.hasVehicle() || p.isGliding() || p.isTouchingWater() || p.isInLava()
                || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(p)
                || FireLeapRules.offPath(leap.from,leap.to,p.getEntityPos())>STRAY || age>leap.duration+GRACE) { end(p);return; }
        p.fallDistance=0;
        if(age<leap.duration) leap.world.spawnParticles(ModParticles.FIRE_EMBER,p.getX(),p.getY()+.4,p.getZ(),2,.18,.25,.18,.01);
        // onGround comes from the caster's movement packets and can flip a tick before the body arrives;
        // only a late touchdown with real footing counts.
        if(age>=leap.duration/2 && p.isOnGround() && FireLeapRules.supported(leap.world,p,p.getEntityPos(),.1)) {
            Vec3d at=p.getEntityPos();
            boolean arrived=at.subtract(leap.to).horizontalLength()<=ARRIVAL && Math.abs(at.y-leap.to.y)<=1;
            end(p);
            if(arrived) landed(p,leap.world,leap.to);
        }
    }
    /** Ends a leap for everyone; an unfinished leap also stops the caster's scripted flight. */
    private static void end(ServerPlayerEntity p) {
        var leap=LEAPS.get(p.getUuid());
        if(leap==null || leap.player!=p) return;
        LEAPS.remove(p.getUuid());p.fallDistance=0;
        if(!p.isDisconnected()) broadcast(p,new ModNetworking.FireLeapPayload(p.getId(),leap.from,leap.to,false));
    }
    private static void broadcast(ServerPlayerEntity p,ModNetworking.FireLeapPayload payload) {
        if(ServerPlayNetworking.canSend(p,ModNetworking.FireLeapPayload.ID)) ServerPlayNetworking.send(p,payload);
        for(var viewer:PlayerLookup.tracking(p))
            if(viewer!=p && ServerPlayNetworking.canSend(viewer,ModNetworking.FireLeapPayload.ID)) ServerPlayNetworking.send(viewer,payload);
    }
    private static void landed(ServerPlayerEntity p,ServerWorld world,Vec3d at) {
        world.spawnParticles(ModParticles.FIRE_EMBER,at.x,at.y+.2,at.z,28,.7,.15,.7,.07);
        world.spawnParticles(ModParticles.FIRE_FLAME_RIBBON,at.x,at.y+.15,at.z,14,.5,.05,.5,.04);
        world.playSound(null,BlockPos.ofFloored(at),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.PLAYERS,.85f,1.3f);
        WAVES.add(new Wave(p,world,at,world.getTime(),new HashSet<>()));
    }
    /** Advances one landing wave; true when it has finished. */
    private static boolean tick(Wave wave) {
        int age=(int)(wave.world.getTime()-wave.started);
        if(wave.owner.isRemoved() || wave.owner.getEntityWorld()!=wave.world || age>=Math.ceil(FireLeapRules.WAVE_RANGE/FireLeapRules.WAVE_SPEED)) return true;
        wave(wave.owner,wave.origin,age,wave.hits);return false;
    }
    public static void wave(ServerPlayerEntity p,Vec3d origin,int age,Set<UUID> hits) {
        if(age<0 || age>=Math.ceil(FireLeapRules.WAVE_RANGE/FireLeapRules.WAVE_SPEED)) return;
        double outer=Math.min(FireLeapRules.WAVE_RANGE,(age+1)*FireLeapRules.WAVE_SPEED),inner=Math.max(0,age*FireLeapRules.WAVE_SPEED);
        var world=p.getEntityWorld();
        for(var target:world.getEntitiesByClass(LivingEntity.class,new Box(origin,origin).expand(FireLeapRules.WAVE_RANGE,3,FireLeapRules.WAVE_RANGE),
                t -> t!=p && t.isAlive() && !t.isSpectator() && !WandAllies.protectedFrom(p,t) && !hits.contains(t.getUuid()))) {
            var surface=GuardianWaveSurface.ground(world,p,origin,target.getX(),target.getZ());
            if(surface==null || !GuardianWaveSurface.visible(world,p,origin,surface)) continue;
            double distance=surface.subtract(origin).horizontalLength(),half=target.getWidth()/2.;
            if(distance-half>outer || distance+half<inner || target.getBoundingBox().minY>surface.y+FireLeapRules.WAVE_HEIGHT
                    || target.getBoundingBox().maxY<surface.y) continue;
            hits.add(target.getUuid());float damage=FireLeapRules.damage(distance);
            if(com.anton.elementalwands.util.SpellCombat.damage(target,world,p.getDamageSources().playerAttack(p),damage,p,com.anton.elementalwands.data.WizardAffinity.FIRE)) AbstractWandItem.onWandDamageDealt(p,damage,1,WizardAffinity.FIRE);
        }
        // The moving crest uses the exact same surface and cover rules as damage.
        int count=Math.max(16,(int)Math.ceil(outer*2*Math.PI/.4));
        for(int i=0;i<count;i++) {
            double angle=i*2*Math.PI/count;
            var surface=GuardianWaveSurface.ground(world,p,origin,origin.x+Math.cos(angle)*outer,origin.z+Math.sin(angle)*outer);
            if(surface!=null && GuardianWaveSurface.visible(world,p,origin,surface))
                world.spawnParticles(ModParticles.FIRE_FLAME_RIBBON,surface.x,surface.y+.15,surface.z,1,.02,.02,.02,.01);
        }
    }
    private FireLeapManager() {}
}
