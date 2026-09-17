package com.anton.elementalwands.util;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.party.WandAllies;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/** Explicit source attribution; XP measures actual health, not nominal or rounded hit damage. */
public final class SpellCombat {
    private record Effect(UUID owner,WizardAffinity element,long until,int amplifier) {}
    private static final Map<Entity,Effect> BURNS=new WeakHashMap<>(),REGEN=new WeakHashMap<>();
    public static void clear(){BURNS.clear();REGEN.clear();}
    public static float multiplier(Entity owner,WizardAffinity affinity) {
        return owner instanceof PlayerEntity p && affinity!=WizardAffinity.NONE ? ElementLevels.multiplier(WandProgression.get(p,affinity).xp()) : 1;
    }
    public static boolean damage(Entity target,ServerWorld world,DamageSource source,float amount,Entity owner,WizardAffinity affinity) {
        if(!Float.isFinite(amount)||amount<=0||WandAllies.protectedFrom(owner,target))return false;
        float health=target instanceof LivingEntity living?living.getHealth():0;
        boolean accepted=target.damage(world,source,amount*multiplier(owner,affinity));
        if(accepted && target instanceof LivingEntity living) reward(living,owner,affinity,health);
        return accepted;
    }
    public static double targetRate(LivingEntity target) {
        return target instanceof PlayerEntity || target instanceof HostileEntity || target instanceof FracturedGuardianEntity
                || target instanceof MobEntity mob && mob.getTarget()!=null ? 1 : .1;
    }
    public static void reward(LivingEntity target,Entity owner,WizardAffinity affinity,float before) {
        if(!(owner instanceof ServerPlayerEntity player) || affinity==WizardAffinity.NONE || before<=0) return;
        double lost=Math.max(0,before-Math.max(0,target.getHealth()));
        if(lost<=0)return;
        double bonus=target.isDead() ? target.getMaxHealth()*.25 : 0;
        WandProgression.experience(player,affinity,(lost+bonus)*targetRate(target));
    }
    public static void heal(LivingEntity target,float amount,Entity owner,WizardAffinity affinity) {
        float before=target.getHealth();target.heal(amount);
        if(owner instanceof ServerPlayerEntity p && target.getHealth()>before)
            WandProgression.experience(p,affinity,(target.getHealth()-before)*(target==owner?.25:1));
    }
    public static void ignite(Entity target,Entity owner,int seconds) {
        target.setOnFireFor(seconds);trackBurn(target,owner,seconds*20);
    }
    public static void trackBurn(Entity target,Entity owner,int ticks){
        if(owner!=null && target.getEntityWorld() instanceof ServerWorld world && !WandAllies.protectedFrom(owner,target))
            BURNS.put(target,new Effect(owner.getUuid(),WizardAffinity.FIRE,world.getTime()+ticks,0));
    }
    public static Entity burnOwner(Entity target,DamageSource source){
        if(!source.isOf(DamageTypes.ON_FIRE)||!(target.getEntityWorld() instanceof ServerWorld world))return null;
        var effect=BURNS.get(target);
        if(effect==null)return null;
        if(!target.isOnFire()||world.getTime()>effect.until()){BURNS.remove(target);return null;}
        return world.getPlayerByUuid(effect.owner());
    }
    public static void regeneration(LivingEntity target,Entity owner,WizardAffinity affinity,int amplifier){
        if(owner!=null && target.getEntityWorld() instanceof ServerWorld world)
            REGEN.put(target,new Effect(owner.getUuid(),affinity,world.getTime()+60,amplifier));
    }
    public static void regenerationHeal(LivingEntity target,float amount){
        var effect=REGEN.get(target);var status=target.getStatusEffect(StatusEffects.REGENERATION);
        if(effect!=null && target.getEntityWorld() instanceof ServerWorld world && world.getTime()<=effect.until()
                && status!=null && status.getAmplifier()==effect.amplifier()) {
            heal(target,amount,world.getPlayerByUuid(effect.owner()),effect.element());
        } else {REGEN.remove(target);target.heal(amount);}
    }
    private SpellCombat(){}
}
