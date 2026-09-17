package com.anton.elementalwands.util;

import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FlashoverEmberEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

public final class FlashoverManager {
    private static final Map<UUID,List<FlashoverEmberEntity>> EMBERS=new HashMap<>();
    private static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> DAMAGE_TYPE=
            net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE,net.minecraft.util.Identifier.of("elementalwands","flashover"));
    private static final class Sequence {
        final List<FlashoverEmberEntity> charges;
        final Map<UUID,Integer> contacts=new HashMap<>();
        final net.minecraft.server.world.ServerWorld world;
        long next;int index;
        Sequence(List<FlashoverEmberEntity> charges,ServerPlayerEntity p) { this.charges=charges;world=p.getEntityWorld();next=world.getTime(); }
    }
    private static final Map<UUID,Sequence> SEQUENCES=new HashMap<>();
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for(var p:server.getPlayerManager().getPlayerList()) {
                if(!p.isAlive() || p.isSpectator() || !equipped(p) || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(p))clear(p);
                var sequence=SEQUENCES.get(p.getUuid());
                if(sequence!=null) {
                    if(sequence.world!=p.getEntityWorld())clear(p);
                    else if(p.getEntityWorld().getTime()>=sequence.next)pop(p,sequence);
                }
                var list=embers(p);list.removeIf(e -> {
                    if(e.isRemoved())return true;
                    if(e.getEntityWorld()!=p.getEntityWorld() || !e.getEntityWorld().isChunkLoaded(e.getBlockPos())) { e.discard();return true; }
                    return false;
                });
                if(server.getTicks()%5==0 && (!list.isEmpty() || equipped(p)))sync(p);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { EMBERS.clear();SEQUENCES.clear(); });
    }
    public static boolean equipped(ServerPlayerEntity p) { return EWAttachments.getAffinity(p)==WizardAffinity.FIRE && WandLoadouts.get(p).contains("flashover"); }
    private static boolean canUse(ServerPlayerEntity p) {
        return equipped(p) && p.isAlive() && !p.isSpectator() && p.getMainHandStack().getItem() instanceof AbstractWandItem
                && WandProgression.owns(p,WandSpells.find("flashover")) && !FireLeapManager.flying(p)
                && !HollowPurpleChargeManager.isCharging(p.getEntityWorld(),p) && com.anton.elementalwands.arena.GuardianArenaManager.canCast(p);
    }
    private static List<FlashoverEmberEntity> embers(ServerPlayerEntity p) { return EMBERS.computeIfAbsent(p.getUuid(),id -> new ArrayList<>()); }
    public static List<FlashoverEmberEntity> active(ServerPlayerEntity p) { return embers(p).stream().filter(e -> !e.isRemoved() && e.getEntityWorld()==p.getEntityWorld()).toList(); }
    /** Three persistent slots: an entity UUID reserves a slot until that bomb is removed. */
    private static net.minecraft.nbt.NbtCompound slots(ServerPlayerEntity p) {
        var data=FireBuildManager.state(p);boolean changed=false;
        if(!data.getBoolean("flash_slots",false)) {
            // Preserve a pre-upgrade detonation recovery on one slot, without locking all three.
            if(data.getInt("flash_duration",0)>FlashoverRules.THROW_COOLDOWN) {
                data.putLong("flash_slot_0_cast",data.getLong("flash_cast",0L));
                data.putInt("flash_slot_0_duration",data.getInt("flash_duration",0));
                data.putInt("flash_duration",0);
            }
            data.putBoolean("flash_slots",true);changed=true;
        }
        for(int i=0;i<FlashoverRules.CAPACITY;i++) {
            String key="flash_slot_"+i, id=data.getString(key+"_entity","");
            // Entities are intentionally not saved. Restore orphaned slots as lost bombs on login.
            if(!id.isEmpty() && active(p).stream().noneMatch(e -> e.getUuidAsString().equals(id))) {
                data.remove(key+"_entity");data.putLong(key+"_cast",p.getEntityWorld().getTime());
                data.putInt(key+"_duration",FlashoverRules.LOST_COOLDOWN);changed=true;
            }
        }
        if(changed)p.setAttached(EWAttachments.FIRE_BUILD_STATE,data);
        return data;
    }
    private static int timer(ServerPlayerEntity p,net.minecraft.nbt.NbtCompound data,String key) {
        int duration=Math.max(0,data.getInt(key+"_duration",0));
        long elapsed=p.getEntityWorld().getTime()-data.getLong(key+"_cast",-1000000000L);
        if(EntangleTracker.getStacks(p)>0)elapsed/=2;
        return (int)Math.clamp(duration-elapsed,0,duration);
    }
    private static int readySlot(ServerPlayerEntity p,net.minecraft.nbt.NbtCompound data) {
        for(int i=0;i<FlashoverRules.CAPACITY;i++)
            if(data.getString("flash_slot_"+i+"_entity","").isEmpty() && timer(p,data,"flash_slot_"+i)==0)return i;
        return -1;
    }
    /** Negative values identify placed (-1) and armed (-2) bombs; others are recovery ticks. */
    public static List<Integer> slotStates(ServerPlayerEntity p) {
        var data=slots(p);var result=new ArrayList<Integer>();
        for(int i=0;i<FlashoverRules.CAPACITY;i++) {
            String id=data.getString("flash_slot_"+i+"_entity","");
            var ember=active(p).stream().filter(e -> e.getUuidAsString().equals(id)).findFirst().orElse(null);
            result.add(ember==null?timer(p,data,"flash_slot_"+i):ember.armed()?-2:-1);
        }
        return List.copyOf(result);
    }
    public static int remaining(ServerPlayerEntity p) {
        var data=slots(p);int gate=timer(p,data,"flash");
        if(readySlot(p,data)>=0)return gate;
        int next=slotStates(p).stream().filter(t -> t>0).mapToInt(Integer::intValue).min().orElse(0);
        return Math.max(gate,next);
    }
    private static int recoveryDuration(ServerPlayerEntity p) {
        var data=slots(p);int remaining=remaining(p);
        if(remaining==timer(p,data,"flash"))return FlashoverRules.THROW_COOLDOWN;
        for(int i=0;i<FlashoverRules.CAPACITY;i++) {
            String key="flash_slot_"+i;
            if(data.getString(key+"_entity","").isEmpty() && timer(p,data,key)==remaining)
                return Math.max(1,data.getInt(key+"_duration",FlashoverRules.DETONATE_COOLDOWN));
        }
        return FlashoverRules.DETONATE_COOLDOWN;
    }
    /** Removal, including disarm/expiry/lifecycle cleanup, releases exactly the owning slot. */
    public static void recover(ServerPlayerEntity p,FlashoverEmberEntity ember,boolean exploded) {
        var data=FireBuildManager.state(p);
        for(int i=0;i<FlashoverRules.CAPACITY;i++) {
            String key="flash_slot_"+i;
            if(!data.getString(key+"_entity","").equals(ember.getUuidAsString()))continue;
            data.remove(key+"_entity");data.putLong(key+"_cast",p.getEntityWorld().getTime());
            data.putInt(key+"_duration",exploded?FlashoverRules.DETONATE_COOLDOWN:FlashoverRules.LOST_COOLDOWN);
            p.setAttached(EWAttachments.FIRE_BUILD_STATE,data);break;
        }
    }
    /** Let a wand's Basic button perform a deliberate close-range disarm, just like a melee hit. */
    public static boolean tryDisarmAimed(ServerPlayerEntity p) {
        Vec3d eye=p.getEyePos(),end=eye.add(p.getRotationVec(1).multiply(3.5));
        var hit=net.minecraft.entity.projectile.ProjectileUtil.raycast(p,eye,end,p.getBoundingBox().stretch(end.subtract(eye)).expand(1),
                e -> e instanceof FlashoverEmberEntity && !e.isRemoved(),3.5*3.5);
        if(hit==null || p.getEntityWorld().raycast(new RaycastContext(eye,hit.getPos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)return false;
        return hit.getEntity().damage(p.getEntityWorld(),p.getDamageSources().playerAttack(p),1);
    }
    public static boolean toss(ServerPlayerEntity p) {
        if(!canUse(p))return false;
        var data=slots(p);int slot=readySlot(p,data);
        if(timer(p,data,"flash")>0)return false;
        if(slot<0) { p.sendMessage(Text.literal("No ready bombs. Detonate placed embers or wait for a bomb to recover."),true);return false; }
        if(!AbstractWandItem.tryStartCooldown(p.getEntityWorld(),p,p.getMainHandStack(),AbstractWandItem.Ability.SECONDARY,0))return false;
        var ember=new FlashoverEmberEntity(ModEntities.FLASHOVER_EMBER,p.getEntityWorld());ember.launch(p);
        if(!p.getEntityWorld().spawnEntity(ember))return false;
        embers(p).add(ember);data.putString("flash_slot_"+slot+"_entity",ember.getUuidAsString());
        data.putLong("flash_cast",p.getEntityWorld().getTime());data.putInt("flash_duration",FlashoverRules.THROW_COOLDOWN);
        p.setAttached(EWAttachments.FIRE_BUILD_STATE,data);WandLoadouts.markCombat(p);sync(p);
        p.getEntityWorld().playSound(null,p.getBlockPos(),SoundEvents.ITEM_FIRECHARGE_USE,SoundCategory.PLAYERS,.45f,1.5f);return true;
    }
    public static boolean detonate(ServerPlayerEntity p) {
        if(!canUse(p) || SEQUENCES.containsKey(p.getUuid()))return false;
        var charges=active(p).stream().filter(e -> e.refreshAttachment() && e.armed() && e.squaredDistanceTo(p)<=FlashoverRules.DETONATE_RANGE*FlashoverRules.DETONATE_RANGE).toList();
        if(charges.isEmpty()) { p.sendMessage(Text.literal("No armed embers within 32 blocks."),true);return false; }
        charges.forEach(FlashoverEmberEntity::prime);
        var sequence=new Sequence(charges,p);SEQUENCES.put(p.getUuid(),sequence);
        WandLoadouts.markCombat(p);
        pop(p,sequence);return true;
    }
    private static void pop(ServerPlayerEntity p,Sequence sequence) {
        var ember=sequence.charges.get(sequence.index++);
        if(!ember.isRemoved() && ember.refreshAttachment())explode(p,ember,sequence.contacts);
        if(sequence.index>=sequence.charges.size())SEQUENCES.remove(p.getUuid());
        else sequence.next=p.getEntityWorld().getTime()+FlashoverRules.POP_INTERVAL;
        sync(p);
    }
    private static void explode(ServerPlayerEntity p,FlashoverEmberEntity ember,Map<UUID,Integer> contacts) {
        var world=p.getEntityWorld();Vec3d at=ember.getEntityPos();
        var source=new net.minecraft.entity.damage.DamageSource(world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE),ember,p);
        for(var target:world.getEntitiesByClass(LivingEntity.class,ember.getBoundingBox().expand(FlashoverRules.RADIUS),
                t -> t.isAlive() && !t.isSpectator() && !WandAllies.protectedFrom(p,t))) {
            Box b=target.getBoundingBox();Vec3d nearest=new Vec3d(Math.clamp(at.x,b.minX,b.maxX),Math.clamp(at.y,b.minY,b.maxY),Math.clamp(at.z,b.minZ,b.maxZ));
            if(nearest.squaredDistanceTo(at)>FlashoverRules.RADIUS*FlashoverRules.RADIUS)continue;
            boolean visible=false;
            for(double h:new double[]{.15,.5,.85}) {
                Vec3d point=new Vec3d(target.getX(),target.getBodyY(h),target.getZ());
                if(world.raycast(new RaycastContext(at,point,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p)).getType()==HitResult.Type.MISS) { visible=true;break; }
            }
            if(!visible)continue;
            int count=contacts.merge(target.getUuid(),1,Integer::sum);
            float damage=FlashoverRules.damage(count)-FlashoverRules.damage(count-1);
            // This damage type bypasses only hurt cooldown, not armor/shields/invulnerability.
            // Otherwise vanilla would silently swallow the smaller blasts three ticks apart.
            boolean accepted=damage>0 && com.anton.elementalwands.util.SpellCombat.damage(target,world,source,damage,p,com.anton.elementalwands.data.WizardAffinity.FIRE);
            if(accepted)AbstractWandItem.onWandDamageDealt(p,damage,1,WizardAffinity.FIRE);
        }
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,at.x,at.y,at.z,1,0,0,0,0);
        world.spawnParticles(ModParticles.FIRE_EMBER,at.x,at.y,at.z,18,.7,.3,.7,.08);
        world.playSound(null,ember.getBlockPos(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.PLAYERS,.65f,1.3f);
        ember.finishExplosion();
    }
    public static void clear(ServerPlayerEntity p) { SEQUENCES.remove(p.getUuid());var list=EMBERS.remove(p.getUuid());if(list!=null)list.forEach(FlashoverEmberEntity::discard); }
    public static void sync(ServerPlayerEntity p) {
        var list=active(p);int armed=(int)list.stream().filter(FlashoverEmberEntity::armed).count();
        ServerPlayNetworking.send(p,new ModNetworking.FlashoverStatePayload(list.size(),armed,remaining(p),recoveryDuration(p),slotStates(p)));
    }
    private FlashoverManager() {}
}
