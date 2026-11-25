package com.anton.elementalwands.util;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.WandBoss;
import com.anton.elementalwands.item.AbstractWandItem;
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
import java.util.function.Consumer;

/** Applies attributed spell damage and measures the resulting health loss once. */
public final class SpellCombat {
    private record Effect(UUID owner,WizardAffinity element,long until,int amplifier) {}
    private static final Map<Entity,Effect> BURNS=new WeakHashMap<>(),REGEN=new WeakHashMap<>();
    public record DamageResult(boolean accepted, float healthLost) {}
    private static final DamageResult REJECTED = new DamageResult(false, 0);

    @FunctionalInterface
    public interface ApplyDamage {
        boolean apply(Entity target, DamageSource source, float amount);
    }
    /**
     * Damage from something a caster left behind to fight beside them (a flower's seed). It is
     * not one of the caster's own hits, so a boss's per-attacker hurt window neither swallows it
     * nor is opened by it, and it cannot shave the caster's next spell.
     */
    public static final net.minecraft.registry.tag.TagKey<net.minecraft.entity.damage.DamageType> NO_HURT_WINDOW =
            net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,
                    net.minecraft.util.Identifier.of("elementalwands","no_hurt_window"));
    public static void clear(){BURNS.clear();REGEN.clear();}
    public static float multiplier(Entity owner,WizardAffinity affinity) {
        return owner instanceof PlayerEntity p && affinity!=WizardAffinity.NONE ? ElementLevels.multiplier(WandProgression.get(p,affinity).xp()) : 1;
    }
    public static boolean damage(Entity target,ServerWorld world,DamageSource source,float amount,Entity owner,WizardAffinity affinity) {
        return damageWithResult(target, world, source, amount, owner, affinity).accepted();
    }
    public static boolean damage(Entity target,ServerWorld world,DamageSource source,float amount,Entity owner,
            WizardAffinity affinity, ApplyDamage applyDamage) {
        return damageWithResult(target, source, amount, owner, affinity, applyDamage).accepted();
    }
    public static DamageResult damageWithResult(Entity target, ServerWorld world, DamageSource source, float amount,
            Entity owner, WizardAffinity affinity) {
        return damageWithResult(target, source, amount, owner, affinity,
                (victim, attributedSource, scaled) -> victim.damage(world, attributedSource, scaled));
    }
    private static DamageResult damageWithResult(Entity target, DamageSource source, float amount, Entity owner,
            WizardAffinity affinity, ApplyDamage applyDamage) {
        target = damageTarget(target);
        if(!Float.isFinite(amount)||amount<=0||WandAllies.protectedFrom(owner,target))return REJECTED;
        // Environmental spell sources (coals and flame floors) still have an explicit
        // caster. Preserve the damage type while presenting that owner to Roc admission.
        if(target instanceof com.anton.elementalwands.entity.roc.StormRocEntity
                && owner instanceof ServerPlayerEntity && source.getAttacker()!=owner)
            source=new DamageSource(source.getTypeRegistryEntry(),source.getSource(),owner);
        float health=target instanceof LivingEntity living?living.getHealth():0;
        if (!applyDamage.apply(target, source, amount * multiplier(owner, affinity))) return REJECTED;
        float lost = 0;
        if (target instanceof LivingEntity living) {
            lost = healthLost(living, health);
            rewardExperience(living, owner, affinity, lost);
        }
        return new DamageResult(true, lost);
    }
    public static boolean damageAndReward(Entity target, ServerWorld world, DamageSource source, float amount,
            Entity owner, WizardAffinity affinity) {
        return damageAndReward(target, world, source, amount, owner, affinity, 5);
    }
    public static boolean damageAndReward(Entity target, ServerWorld world, DamageSource source, float amount,
            Entity owner, WizardAffinity affinity, int charge) {
        var hit = damageWithResult(target, world, source, amount, owner, affinity);
        rewardDamage(hit, owner, affinity, charge);
        return hit.accepted();
    }
    public static void rewardDamage(DamageResult hit, Entity owner, WizardAffinity affinity, int charge) {
        if (!(owner instanceof ServerPlayerEntity player) || hit.healthLost() <= 0) return;
        WandProgression.earn(player, affinity, Math.max(1L, Math.round(hit.healthLost())));
        ElementalWandsMod.refreshProgression(player);
        if (charge > 0 && EWAttachments.getAffinity(player) == affinity) {
            AbstractWandItem.addUltimateCharge(player.getMainHandStack(), charge);
        }
    }
    private static float healthLost(LivingEntity target, float before) {
        float lost = before - Math.max(0, target.getHealth());
        return Float.isFinite(lost) ? Math.max(0, lost) : 0;
    }
    public static double targetRate(LivingEntity target) {
        return target instanceof PlayerEntity || target instanceof HostileEntity || target instanceof WandBoss
                || target instanceof MobEntity mob && mob.getTarget()!=null ? 1 : .1;
    }
    public static void reward(LivingEntity target,Entity owner,WizardAffinity affinity,float before) {
        rewardExperience(target, owner, affinity, healthLost(target, before));
    }
    private static void rewardExperience(LivingEntity target, Entity owner, WizardAffinity affinity, float lost) {
        if (!(owner instanceof ServerPlayerEntity player) || affinity == WizardAffinity.NONE || lost <= 0) return;
        double bonus=target.isDead() ? target.getMaxHealth()*.25 : 0;
        WandProgression.experience(player,affinity,(lost+bonus)*targetRate(target));
    }
    public static void heal(LivingEntity target,float amount,Entity owner,WizardAffinity affinity) {
        float before=target.getHealth();target.heal(amount);
        rewardHealing(target, owner, affinity, before);
    }
    private static void rewardHealing(LivingEntity target, Entity owner, WizardAffinity affinity, float before) {
        if(owner instanceof ServerPlayerEntity p && target.getHealth()>before)
            WandProgression.experience(p,affinity,(target.getHealth()-before)*(target==owner?.25:1));
    }
    public static void ignite(Entity target,Entity owner,int seconds) {
        target=damageTarget(target);
        target.setOnFireFor(seconds);trackBurn(target,owner,seconds*20);
    }
    public static void trackBurn(Entity target,Entity owner,int ticks){
        target=damageTarget(target);
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
        regenerationHeal(target, amount, target::heal);
    }
    public static void regenerationHeal(LivingEntity target, float amount, Consumer<Float> applyHeal) {
        var effect=REGEN.get(target);var status=target.getStatusEffect(StatusEffects.REGENERATION);
        if(effect!=null && target.getEntityWorld() instanceof ServerWorld world && world.getTime()<=effect.until()
                && status!=null && status.getAmplifier()==effect.amplifier()) {
            Entity owner = world.getPlayerByUuid(effect.owner());
            float before = target.getHealth();
            applyHeal.accept(amount);
            rewardHealing(target, owner, effect.element(), before);
        } else {
            REGEN.remove(target);
            applyHeal.accept(amount);
        }
    }
    /** A multipart hit keeps its visible contact point but shares one health and spell owner. */
    public static Entity damageTarget(Entity target){
        return target instanceof com.anton.elementalwands.entity.roc.RocBodyPartEntity part && part.owner()!=null ? part.owner() : target;
    }
    private SpellCombat(){}
}
