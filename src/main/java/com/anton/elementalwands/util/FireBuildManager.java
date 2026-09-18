package com.anton.elementalwands.util;

import com.anton.elementalwands.party.WandAllies;

import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModParticles;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Held input has a short lease; heat/cooldown persist independently of held item and menus. */
public final class FireBuildManager {
    private record Channel(ItemStack wand, ServerWorld world, int hotbar, int expires, int started) {}
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> { for (var player : server.getPlayerManager().getPlayerList()) tick(player); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { stop(handler.player); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { CHANNELS.clear(); });
    }
    public static NbtCompound state(ServerPlayerEntity player) {
        return player.getAttachedOrElse(EWAttachments.FIRE_BUILD_STATE, new NbtCompound()).copy();
    }
    public static float heat(ServerPlayerEntity player) { return state(player).getFloat("heat",0); }
    public static boolean overheated(ServerPlayerEntity player) { return state(player).getBoolean("overheated",false); }
    public static int hopRemaining(ServerPlayerEntity player) {
        var data=state(player);if (!data.contains("hop_ready")) return 0;
        long elapsed=player.getEntityWorld().getTime()-(data.getLong("hop_ready",0L)-FireBuildRules.HOP_COOLDOWN);
        if (EntangleTracker.getStacks(player)>0) elapsed/=2;
        return (int)Math.clamp(FireBuildRules.HOP_COOLDOWN-elapsed,0,FireBuildRules.HOP_COOLDOWN);
    }
    private static boolean canUse(ServerPlayerEntity player, String id) {
        return player.isAlive() && !player.isSpectator() && !FireLeapManager.flying(player) && EWAttachments.getAffinity(player)==WizardAffinity.FIRE
                && player.getMainHandStack().getItem() instanceof AbstractWandItem
                && com.anton.elementalwands.arena.GuardianArenaManager.canCast(player)
                && !HollowPurpleChargeManager.isCharging(player.getEntityWorld(),player)
                && WandLoadouts.get(player).contains(id) && WandProgression.owns(player,WandSpells.find(id));
    }
    public static void hold(ServerPlayerEntity player) {
        if (!canUse(player,"flamethrower") || overheated(player)) { stop(player); return; }
        var stack=player.getMainHandStack(); int now=player.getEntityWorld().getServer().getTicks();
        var existing=CHANNELS.get(player.getUuid());
        if (existing!=null && (existing.world()!=player.getEntityWorld() || existing.wand()!=stack
                || existing.hotbar()!=player.getInventory().getSelectedSlot() || existing.expires()<now)) {
            stop(player);existing=null;
        }
        if (existing==null && !AbstractWandItem.tryStartCooldown(player.getEntityWorld(), player,stack,"flamethrower",AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS)) return;
        CHANNELS.put(player.getUuid(),new Channel(stack,player.getEntityWorld(),player.getInventory().getSelectedSlot(),
                now+FireBuildRules.LEASE_TICKS,existing==null ? now : existing.started()));
    }
    public static void stop(ServerPlayerEntity player) {
        var channel=CHANNELS.remove(player.getUuid());
        if(channel!=null) AbstractWandItem.startCooldown(player.getEntityWorld(),channel.wand(),"flamethrower",AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS,false);
    }
    public static boolean spraying(ServerPlayerEntity player) { return CHANNELS.containsKey(player.getUuid()); }
    private static void tick(ServerPlayerEntity player) {
        var world=player.getEntityWorld(); int now=world.getServer().getTicks();
        var channel=CHANNELS.get(player.getUuid());
        if (channel!=null && (channel.expires()<now || channel.world()!=world || channel.wand()!=player.getMainHandStack()
                || channel.hotbar()!=player.getInventory().getSelectedSlot() || !canUse(player,"flamethrower"))) { stop(player);channel=null; }
        var data=state(player); float old=data.getFloat("heat",0); boolean locked=data.getBoolean("overheated",false);
        float heat=old;
        if (channel!=null && !locked) {
            heat=(float)Math.min(100,old+FireBuildRules.HEAT_PER_TICK);
            WandLoadouts.markCombat(player);
            if (now%2==0) flameVisual(player);
            int sprayTicks=now-channel.started();
            if (sprayTicks%FireBuildRules.DAMAGE_INTERVAL==1) flameDamage(player,FireBuildRules.flameDamage(sprayTicks));
            if (now%8==0) world.playSound(null,player.getBlockPos(),SoundEvents.BLOCK_FIRE_AMBIENT,SoundCategory.PLAYERS,0.45f,1.1f+heat/180);
            if (heat>=100) {
                locked=true;stop(player);
                world.playSound(null,player.getBlockPos(),SoundEvents.BLOCK_FIRE_EXTINGUISH,SoundCategory.PLAYERS,0.65f,1.2f);
            }
        } else {
            heat=(float)Math.max(0,old-FireBuildRules.COOL_PER_TICK);
            if (heat<=FireBuildRules.UNLOCK_HEAT) locked=false;
        }
        if (heat!=old || locked!=data.getBoolean("overheated",false)) {
            data.putFloat("heat",heat);data.putBoolean("overheated",locked);player.setAttached(EWAttachments.FIRE_BUILD_STATE,data);
            if (now%2==0 || heat==0 || heat==100) ModNetworking.syncFireBuild(player);
        } else if (hopRemaining(player)>0 && now%4==0) ModNetworking.syncFireBuild(player);
    }
    private static boolean visible(ServerPlayerEntity player, Vec3d from, Vec3d to) {
        return player.getEntityWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()==HitResult.Type.MISS;
    }
    private static boolean enemy(ServerPlayerEntity player, LivingEntity target) {
        return target!=player && target.isAlive() && !target.isSpectator() && !WandAllies.protectedFrom(player, target)
                && !(target instanceof net.minecraft.entity.passive.TameableEntity pet && pet.isOwner(player));
    }
    private static void flameDamage(ServerPlayerEntity player, float damage) {
        var eye=player.getEyePos();var aim=player.getRotationVec(1).normalize();boolean hit=false;
        for (var target : player.getEntityWorld().getEntitiesByClass(LivingEntity.class,player.getBoundingBox().expand(FireBuildRules.RANGE), t -> enemy(player,t))) {
            boolean touches=false;
            for (double height : new double[]{0.15,0.5,0.85}) {
                var point=new Vec3d(target.getX(),target.getBodyY(height),target.getZ());
                if (FireBuildRules.inCone(eye,aim,point) && visible(player,eye,point)) { touches=true;break; }
            }
            if (touches) {
                SpellCombat.ignite(target,player,FireBuildRules.IGNITE_SECONDS);
                if (com.anton.elementalwands.util.SpellCombat.damage(target,player.getEntityWorld(),player.getDamageSources().playerAttack(player),damage,player,com.anton.elementalwands.data.WizardAffinity.FIRE)) {
                    AbstractWandItem.onWandDamageDealt(player,damage,0,WizardAffinity.FIRE);hit=true;
                }
            }
        }
        if (hit) AbstractWandItem.addUltimateCharge(player.getMainHandStack(),2);
    }
    private static void flameVisual(ServerPlayerEntity player) {
        var world=player.getEntityWorld(); var eye=player.getEyePos(); var aim=player.getRotationVec(1).normalize();
        var right=aim.crossProduct(new Vec3d(0,1,0)); if (right.lengthSquared()<0.01) right=new Vec3d(1,0,0); right=right.normalize();
        var up=right.crossProduct(aim).normalize();
        for (int ray=0;ray<5;ray++) {
            double angle=ray*Math.PI*2/5+world.getTime()*0.13;
            var direction=aim.add(right.multiply(Math.cos(angle)*0.22)).add(up.multiply(Math.sin(angle)*0.22)).normalize();
            var end=eye.add(direction.multiply(FireBuildRules.RANGE));
            var obstruction=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
            double length=obstruction.getType()==HitResult.Type.MISS ? FireBuildRules.RANGE : Math.max(0,eye.distanceTo(obstruction.getPos())-0.2);
            for (double d=0.8;d<=length;d+=0.65) {
                var point=eye.add(direction.multiply(d));
                world.spawnParticles(ModParticles.FIRE_FLAME_RIBBON,point.x,point.y-0.12,point.z,1,0.04,0.04,0.04,0.02);
            }
        }
    }
    private FireBuildManager() {}
}
