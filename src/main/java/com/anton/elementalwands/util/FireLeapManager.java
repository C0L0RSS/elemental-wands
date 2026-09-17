package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

public final class FireLeapManager {
    public static boolean flying(ServerPlayerEntity p) { return p.getVehicle() instanceof FireLeapEntity; }
    public static boolean commit(ServerPlayerEntity p,Vec3d target) {
        var world=p.getEntityWorld();
        if(!p.isAlive() || p.isSpectator() || p.hasVehicle() || p.isGliding() || p.isTouchingWater()
                || EWAttachments.getAffinity(p)!=WizardAffinity.FIRE || !(p.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandLoadouts.get(p).contains("fire_hop") || !WandProgression.owns(p,WandSpells.find("fire_hop"))
                || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(p) || HollowPurpleChargeManager.isCharging(world,p)
                || FireBuildManager.hopRemaining(p)>0) return false;
        // A normal sprint jump is allowed, but a high fall cannot be converted into another flight.
        if(!FireLeapRules.supported(world,p,p.getEntityPos(),1.5) || !FireLeapRules.validTarget(p,target)) {
            p.sendMessage(Text.literal("No clear leap path. Aim toward open ground or a reachable ledge."),true);return false;
        }
        if(!AbstractWandItem.tryStartCooldown(world,p,p.getMainHandStack(),AbstractWandItem.Ability.SECONDARY,0)) return false;
        var leap=new FireLeapEntity(ModEntities.FIRE_LEAP,world);leap.begin(p,target);
        if(!world.spawnEntity(leap) || !p.startRiding(leap,true,true)) { leap.abort();return false; }
        FireBuildManager.stop(p);p.fallDistance=0;p.setVelocity(Vec3d.ZERO);
        var data=FireBuildManager.state(p);data.putLong("hop_ready",world.getTime()+FireBuildRules.HOP_COOLDOWN);p.setAttached(EWAttachments.FIRE_BUILD_STATE,data);
        WandLoadouts.markCombat(p);ModNetworking.syncFireBuild(p);
        world.spawnParticles(ModParticles.FIRE_EMBER,p.getX(),p.getY()+.15,p.getZ(),12,.3,.1,.3,.03);
        world.playSound(null,p.getBlockPos(),SoundEvents.ITEM_FIRECHARGE_USE,SoundCategory.PLAYERS,.9f,.9f);
        return true;
    }
    public static void landed(ServerPlayerEntity p,Vec3d at) {
        var world=p.getEntityWorld();
        world.spawnParticles(ModParticles.FIRE_EMBER,at.x,at.y+.2,at.z,24,.6,.15,.6,.06);
        world.playSound(null,BlockPos.ofFloored(at),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.PLAYERS,.85f,1.3f);
    }
    public static void wave(ServerPlayerEntity p,Vec3d origin,int age,java.util.Set<java.util.UUID> hits) {
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
