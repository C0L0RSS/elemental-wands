package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModParticles;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Owns the entire toss -> double -> recovery lifecycle independently of Blink Rift. */
public final class AstralDoubleManager {
    public static final String ID = "astral_double";
    public static final int LIFETIME = 900, COOLDOWN = 300, FAILED_COOLDOWN = 60;
    public static final double SWAP_RANGE = 64;
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static final class Cast {
        final ServerPlayerEntity player;
        final ServerWorld world;
        final ItemStack wand;
        AstralOrbEntity orb;
        AstralDoubleEntity clone;
        long expires;
        Cast(ServerPlayerEntity p) { player=p; world=p.getEntityWorld(); wand=p.getMainHandStack(); }
    }
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Cast c : List.copyOf(CASTS.values())) {
                if (!valid(c)) { finish(c, COOLDOWN); continue; }
                if (c.clone != null && (c.clone.isRemoved() || c.world.getTime() >= c.expires
                        || !safe(c.player,c.world,c.clone.getEntityPos()))) finish(c, COOLDOWN);
                else if (c.clone == null && (c.orb == null || c.orb.isRemoved())) finish(c, FAILED_COOLDOWN);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) cancel(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> cancel(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            // A crash/reload cannot restore transient entities or erase their recovery.
            if (state(handler.player).getBoolean("active",false)) recover(handler.player,COOLDOWN);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Cast c : List.copyOf(CASTS.values())) finish(c,COOLDOWN);
        });
    }
    public static NbtCompound state(PlayerEntity p) { return p.getAttachedOrElse(EWAttachments.ASTRAL_STATE,new NbtCompound()); }
    public static long remaining(PlayerEntity p) {
        return remaining(state(p),p.getEntityWorld().getTime(),EntangleTracker.getStacks(p)>0);
    }
    public static long remaining(NbtCompound state,long now,boolean entangled) {
        long elapsed=Math.max(0,now-state.getLong("ended",-1_000_000_000L));
        if (entangled) elapsed/=2;
        return Math.max(0,state.getInt("recovery",0)-elapsed);
    }
    private static boolean valid(Cast c) {
        var p=c.player;
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.getEntityWorld()==c.world
                && c.world.getPlayerByUuid(p.getUuid())==p && EWAttachments.getAffinity(p)==WizardAffinity.SPACE
                && GuardianArenaManager.canCast(p) && WandProgression.owns(p,WandSpells.find(ID))
                && WandLoadouts.get(p).contains(ID);
    }
    public static AstralDoubleEntity active(ServerPlayerEntity p) {
        Cast c=CASTS.get(p.getUuid());
        return c!=null && valid(c) && c.clone!=null && !c.clone.isRemoved() && c.world.getTime()<c.expires ? c.clone : null;
    }
    public static boolean owns(ServerPlayerEntity p,AstralOrbEntity orb) {
        Cast c=CASTS.get(p.getUuid()); return c!=null && c.orb==orb && valid(c);
    }
    public static boolean cast(ServerPlayerEntity p) {
        var world=p.getEntityWorld();
        if (!p.isAlive() || p.isSpectator() || p.hasVehicle() || !GuardianArenaManager.canCast(p)
                || EWAttachments.getAffinity(p)!=WizardAffinity.SPACE || HollowPurpleChargeManager.isCharging(world,p)
                || !(p.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandProgression.owns(p,WandSpells.find(ID)) || !WandLoadouts.get(p).contains(ID)) return false;
        Cast c=CASTS.get(p.getUuid());
        if (c!=null) {
            if (!valid(c)) { finish(c,COOLDOWN); return false; }
            if (c.clone==null) return false; // Flying orb: another press cannot swap or throw again.
            Vec3d destination=c.clone.getEntityPos();
            if (c.clone.isRemoved() || world.getTime()>=c.expires) { finish(c,COOLDOWN); return false; }
            if (p.squaredDistanceTo(destination)>SWAP_RANGE*SWAP_RANGE || !safe(p,world,destination)) {
                p.sendMessage(Text.literal("Astral Double is out of range or its destination is blocked."),true); return false;
            }
            if (!AbstractWandItem.tryStartCooldown(world,p,p.getMainHandStack(),ID,0)) return false;
            Vec3d from=p.getEntityPos();
            p.requestTeleport(destination.x,destination.y,destination.z);
            p.setVelocity(Vec3d.ZERO); p.fallDistance=0; p.velocityModified=true;
            finish(c,COOLDOWN); poof(world,from.add(0,1,0));
            world.playSound(null,p.getBlockPos(),SoundEvents.ENTITY_ENDERMAN_TELEPORT,SoundCategory.PLAYERS,.9f,1.3f);
            WandLoadouts.markCombat(p); return true;
        }
        if (state(p).getBoolean("active",false)) recover(p,COOLDOWN);
        long remaining=remaining(p);
        if (remaining>0) { AbstractWandItem.sendCooldownActionbar(p,"Astral Double",(int)remaining); return false; }
        var itemData=p.getMainHandStack().getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
        int itemRecovery=Math.max(1,itemData.getInt(AbstractWandItem.durationKey(ID),0));
        if (!AbstractWandItem.canStartCooldown(world,p,p.getMainHandStack(),ID,itemRecovery)
                || !AbstractWandItem.tryStartCooldown(world,p,p.getMainHandStack(),ID,0)) return false;
        c=new Cast(p); c.orb=new AstralOrbEntity(world,p); CASTS.put(p.getUuid(),c);
        NbtCompound state=new NbtCompound();state.putBoolean("active",true);state.putLong("expires",0);p.setAttached(EWAttachments.ASTRAL_STATE,state);
        if (!world.spawnEntity(c.orb)) { finish(c,FAILED_COOLDOWN); return false; }
        world.playSound(null,p.getBlockPos(),SoundEvents.ENTITY_ENDER_PEARL_THROW,SoundCategory.PLAYERS,.7f,1.3f);
        WandLoadouts.markCombat(p); return true;
    }
    public static void land(ServerPlayerEntity p,AstralOrbEntity orb,Vec3d pos) {
        Cast c=CASTS.get(p.getUuid()); if(c==null || c.orb!=orb)return;
        if (!valid(c) || !safe(p,c.world,pos)) { finish(c,FAILED_COOLDOWN); return; }
        c.clone=new AstralDoubleEntity(c.world,p,pos);
        if (!c.world.spawnEntity(c.clone)) { finish(c,FAILED_COOLDOWN); return; }
        c.expires=c.world.getTime()+LIFETIME; c.orb.discard(); c.orb=null;
        var state=state(p).copy();state.putLong("expires",c.expires);p.setAttached(EWAttachments.ASTRAL_STATE,state);
        poof(c.world,pos.add(0,1,0));
    }
    public static void failed(ServerPlayerEntity p,AstralOrbEntity orb) {
        Cast c=CASTS.get(p.getUuid());if(c!=null && c.orb==orb)finish(c,FAILED_COOLDOWN);
    }
    public static void destroyed(AstralDoubleEntity clone) {
        Cast c=CASTS.get(clone.ownerUuid()); if(c!=null && c.clone==clone)finish(c,COOLDOWN);else clone.discard();
    }
    public static void cancel(ServerPlayerEntity p) { Cast c=CASTS.get(p.getUuid());if(c!=null)finish(c,COOLDOWN); }
    private static void finish(Cast c,int recovery) {
        if (!CASTS.remove(c.player.getUuid(),c)) return;
        if (c.orb!=null) { poof(c.world,c.orb.getEntityPos()); c.orb.discard(); }
        if (c.clone!=null) { poof(c.world,c.clone.getEntityPos().add(0,1,0)); c.clone.discard(); }
        AbstractWandItem.startCooldown(c.world,c.wand,ID,recovery,false);
        recover(c.player,recovery);
    }
    private static void recover(ServerPlayerEntity p,int duration) {
        var state=new NbtCompound();state.putLong("ended",p.getEntityWorld().getTime());state.putInt("recovery",duration);
        p.setAttached(EWAttachments.ASTRAL_STATE,state);
    }
    public static boolean safe(ServerPlayerEntity p,ServerWorld world,Vec3d feet) {
        if (!Double.isFinite(feet.x)||!Double.isFinite(feet.y)||!Double.isFinite(feet.z)
                || feet.y<=world.getBottomY() || feet.y>world.getTopYInclusive()-2
                || !GuardianArenaManager.canTeleport(p,world,feet)) return false;
        Box body=new Box(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3);
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(body.minX,body.minY-.05,body.minZ),BlockPos.ofFloored(body.maxX,body.maxY,body.maxZ)))
            if(!world.isChunkLoaded(pos)||!world.getWorldBorder().contains(pos)||!world.getFluidState(pos).isEmpty())return false;
        if(!world.isSpaceEmpty(p,body))return false;
        var support=world.raycast(new RaycastContext(feet.add(0,.05,0),feet.add(0,-.12,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,p));
        if(support.getType()==HitResult.Type.MISS || support.getSide()!=Direction.UP)return false;
        var floor=world.getBlockState(support.getBlockPos());
        return !floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK) && !floor.isOf(net.minecraft.block.Blocks.CAMPFIRE)
                && !floor.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE) && !floor.isOf(net.minecraft.block.Blocks.CACTUS);
    }
    /** Sample the caster's crosshair once per successful primary, then converge from the clone. */
    public static boolean mirror(ServerPlayerEntity p) {
        AstralDoubleEntity clone=active(p);if(clone==null)return false;
        Vec3d eye=p.getEyePos(),end=eye.add(p.getRotationVec(1).multiply(24));
        var block=p.getEntityWorld().raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        var hit=ProjectileUtil.raycast(p,eye,end,p.getBoundingBox().stretch(end.subtract(eye)).expand(1),
                e->e.isAlive()&&e.canBeHitByProjectile()&&!WandAllies.protectedFrom(p,e),eye.squaredDistanceTo(end));
        if(hit!=null)end=hit.getPos();
        Vec3d origin=clone.getEntityPos().add(0,1.62,0),direction=end.subtract(origin).normalize();
        if(direction.lengthSquared()<.001)return false;
        clone.face(direction);clone.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        var bolt=new SingularityBoltEntity(p.getEntityWorld(),p,origin,direction);bolt.setAstralPair();bolt.setEcho();bolt.useCastVisuals(clone);
        return p.getEntityWorld().spawnEntity(bolt);
    }
    public static void poof(ServerWorld w,Vec3d p) {
        w.spawnParticles(ModParticles.SPACE_MOTE,p.x,p.y,p.z,20,.3,.6,.3,.045);
        w.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,p.x,p.y,p.z,1,0,0,0,0);
        w.playSound(null,p.x,p.y,p.z,SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,.5f,1.7f);
    }
    private AstralDoubleManager() {}
}
