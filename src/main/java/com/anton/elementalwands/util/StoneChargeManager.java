package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.church.GuardianChurchManager;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModParticles;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Authoritative charge lifetime, observed movement, swept impacts and bounded destruction. */
public final class StoneChargeManager {
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Integer> READY = new HashMap<>(), INTERRUPT_READY = new HashMap<>();
    private static final class Run {
        final ServerPlayerEntity player; final ServerWorld world; final ItemStack wand;
        Vec3d previous; float yaw; double progress, speed = StoneTechniqueRules.START_SPEED;
        int age, lastHeld, brake; boolean released, wasGround, announced;
        Run(ServerPlayerEntity p) {
            player=p;world=p.getEntityWorld();wand=p.getMainHandStack();previous=p.getEntityPos();yaw=p.getYaw();
            lastHeld=world.getServer().getTicks();wasGround=grounded(p);
        }
    }
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Run run : List.copyOf(RUNS.values())) tick(run);
            int now=server.getTicks();READY.values().removeIf(t -> t<=now);INTERRUPT_READY.values().removeIf(t -> t<=now);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> stop(handler.player,false));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for(Run run:List.copyOf(RUNS.values()))finish(run);
            RUNS.clear();READY.clear();INTERRUPT_READY.clear();
        });
    }
    public static boolean active(ServerPlayerEntity p) { return RUNS.containsKey(p.getUuid()); }
    public static double speed(ServerPlayerEntity p) { Run r=RUNS.get(p.getUuid());return r==null?0:r.speed; }
    public static void hold(ServerPlayerEntity p) { Run r=RUNS.get(p.getUuid());if(r!=null && !r.released)r.lastHeld=r.world.getServer().getTicks(); }
    public static void start(ServerPlayerEntity p) {
        if (!p.isAlive() || p.isSpectator() || EWAttachments.getAffinity(p)!=WizardAffinity.STONE
                || !(p.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandProgression.owns(p,WandSpells.find(StoneTechniqueRules.CHARGE))
                || !WandLoadouts.get(p).contains(StoneTechniqueRules.CHARGE) || !GuardianArenaManager.canCast(p)
                || HollowPurpleChargeManager.isCharging(p.getEntityWorld(),p)
                || active(p) || !grounded(p) || p.hasVehicle() || p.isGliding() || p.isTouchingWater() || p.isInLava()
                || p.getAbilities().flying || ((StoneMotionAccess)p).elementalwands$stoneInterrupted()
                || rooted(p) || READY.getOrDefault(p.getUuid(),0)>p.getEntityWorld().getServer().getTicks()) return;
        if (!AbstractWandItem.tryStartCooldown(p.getEntityWorld(),p,p.getMainHandStack(),StoneTechniqueRules.CHARGE,
                StoneTechniqueRules.CHARGE_COOLDOWN)) return;
        Run run=new Run(p);RUNS.put(p.getUuid(),run);sync(run,1);
        p.getEntityWorld().playSound(null,p.getBlockPos(),SoundEvents.ENTITY_IRON_GOLEM_STEP,SoundCategory.PLAYERS,1,.7f);
    }
    public static void stop(ServerPlayerEntity p, boolean slide) {
        Run run=RUNS.get(p.getUuid());if(run==null)return;
        if(slide && !run.released) {
            run.released=true;run.brake=StoneTechniqueRules.BRAKE;
            cooldown(run);
            // Natural airborne momentum takes over immediately. There is no airborne attack after release.
            if(!p.isOnGround()) { finish(run);return; }
        } else if(!slide) finish(run);
    }
    /** Validate support from world collision, not just the movement packet's on-ground flag. */
    private static boolean grounded(ServerPlayerEntity p) {
        if (!p.isOnGround()) return false;
        Box body=p.getBoundingBox();
        Box feet=new Box(body.minX+.02,body.minY-.12,body.minZ+.02,body.maxX-.02,body.minY+.001,body.maxZ-.02);
        return p.getEntityWorld().getBlockCollisions(p,feet).iterator().hasNext();
    }
    private static boolean rooted(LivingEntity p) {
        var effect=p.getStatusEffect(StatusEffects.SLOWNESS);return effect!=null && effect.getAmplifier()>=6;
    }
    public static void interrupt(LivingEntity target) {
        if (!(target.getEntityWorld() instanceof ServerWorld world) || target instanceof FracturedGuardianEntity) return;
        int now=world.getServer().getTicks();
        if(INTERRUPT_READY.getOrDefault(target.getUuid(),0)>now)return;
        INTERRUPT_READY.put(target.getUuid(),now+StoneTechniqueRules.INTERRUPT_GRACE);
        if(target instanceof ServerPlayerEntity player)stop(player,false);
        ((StoneMotionAccess)target).elementalwands$stoneMotion(0,0,3);
        target.setSprinting(false);target.setVelocity(0,.24,0);target.velocityModified=true;
        var packet=new ModNetworking.StoneMotionPayload(target.getId(),0,0,3);
        for(var player:PlayerLookup.tracking(target))ServerPlayNetworking.send(player,packet);
        if(target instanceof ServerPlayerEntity player)ServerPlayNetworking.send(player,packet);
    }
    private static void tick(Run r) {
        var p=r.player;var world=r.world;
        if(!p.isAlive() || p.isDisconnected() || p.isSpectator() || p.getEntityWorld()!=world
                || p.getMainHandStack()!=r.wand || EWAttachments.getAffinity(p)!=WizardAffinity.STONE
                || !WandLoadouts.get(p).contains(StoneTechniqueRules.CHARGE) || p.hasVehicle() || p.isGliding()
                || p.getAbilities().flying || p.isTouchingWater() || p.isInLava() || !GuardianArenaManager.canCast(p)
                || rooted(p) || ((StoneMotionAccess)p).elementalwands$stoneInterrupted()) { finish(r);return; }
        if(r.released) {
            if(--r.brake<=0 || !p.isOnGround() || p.horizontalCollision) { finish(r);return; }
            r.speed*=.60;sync(r,2);return;
        }
        if(++r.age>StoneTechniqueRules.MAX_RUN || world.getServer().getTicks()-r.lastHeld>8) { stop(p,true);return; }
        Vec3d at=p.getEntityPos(), delta=at.subtract(r.previous);
        // Reject discontinuities before awarding momentum or checking damage along the path.
        if(delta.horizontalLength()>2.5 || Math.abs(delta.y)>3) { finish(r);return; }
        double angle=Math.toRadians(r.yaw);Vec3d direction=new Vec3d(-Math.sin(angle),0,Math.cos(angle));
        double forward=Math.max(0,delta.dotProduct(direction));
        boolean onGround=grounded(p);
        if(onGround && r.wasGround && forward>.03) r.progress=Math.min(StoneTechniqueRules.RAMP,
                r.progress+Math.min(1,forward/Math.max(.1,r.speed)));
        if(onGround && r.age>8 && forward<.015) r.progress=Math.max(0,r.progress-3);
        var slow=p.getStatusEffect(StatusEffects.SLOWNESS);
        double factor=slow==null?1:Math.max(.15,1-.15*(slow.getAmplifier()+1));
        // Airborne movement keeps takeoff strength; no falling/jump-spam acceleration.
        double desired=StoneTechniqueRules.speed(r.progress,factor);
        r.speed=onGround?desired:Math.min(r.speed,desired);
        r.yaw=StoneTechniqueRules.turn(r.yaw,p.getYaw(),r.speed,onGround);
        angle=Math.toRadians(r.yaw);direction=new Vec3d(-Math.sin(angle),0,Math.cos(angle));
        Vec3d to=at.add(direction.multiply(r.speed));
        if(!world.isChunkLoaded(BlockPos.ofFloored(to)) || !world.getWorldBorder().contains(BlockPos.ofFloored(to))
                || !GuardianArenaManager.canTeleport(p,world,to)) { finish(r);return; }
        // Sweep the observed path and the next body's movement. Choose the first wall/entity contact.
        Vec3d from=r.previous;Vec3d end=to;
        Contact obstacle=firstBlock(world,p,from,end);
        LivingEntity hit=null;Vec3d contact=obstacle==null?null:obstacle.point;
        double nearest=contact==null?Double.POSITIVE_INFINITY:from.squaredDistanceTo(contact);
        Box broad=p.getBoundingBox().stretch(end.subtract(at)).stretch(from.subtract(at)).expand(.4);
        for(var target:world.getEntitiesByClass(LivingEntity.class,broad,
                e -> e!=p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity))) {
            Box b=target.getBoundingBox();
            Box swept=new Box(b.minX-p.getWidth()/2,b.minY-p.getHeight()+.1,b.minZ-p.getWidth()/2,
                    b.maxX+p.getWidth()/2,b.maxY-.05,b.maxZ+p.getWidth()/2);
            Vec3d point=swept.contains(from)?from:swept.raycast(from,end).orElse(null);
            if(point!=null && from.squaredDistanceTo(point)<nearest) { hit=target;contact=point;nearest=from.squaredDistanceTo(point); }
        }
        if(contact!=null) {
            if(hit==null && obstacle.ceiling) { finish(r);return; }
            if(hit!=null && WandAllies.protectedFrom(p,hit)) { finish(r);return; }
            impact(r,contact,direction,hit);finish(r);return;
        }
        if(p.verticalCollision && !p.isOnGround()) { finish(r);return; }
        r.previous=at;r.wasGround=grounded(p);sync(r,1);
        WandLoadouts.markCombat(p);
        if(r.age%3==0) {
            float power=StoneTechniqueRules.power(r.speed);
            world.spawnParticles(ModParticles.STONE_DUST,p.getX(),p.getY()+.15,p.getZ(),3+(int)(power*4),.3,.1,.3,.02);
            if(power>.3)world.spawnParticles(ModParticles.STONE_SHARD,p.getX(),p.getY()+1.1,p.getZ(),2,.4,.25,.4,.015);
        }
        if(r.age%8==0 && p.isOnGround())world.playSound(null,p.getBlockPos(),SoundEvents.ENTITY_IRON_GOLEM_STEP,
                SoundCategory.PLAYERS,.6f+(float)r.progress/100,.8f);
        if(r.progress>=StoneTechniqueRules.RAMP && !r.announced) {
            r.announced=true;
            world.playSound(null,p.getBlockPos(),SoundEvents.BLOCK_ANVIL_LAND,SoundCategory.PLAYERS,.25f,1.5f);
        }
    }
    private record Contact(Vec3d point, boolean ceiling) {}
    private static Contact firstBlock(ServerWorld world,ServerPlayerEntity p,Vec3d from,Vec3d to) {
        Vec3d nearest=null;double dist=Double.POSITIVE_INFINITY;boolean ceiling=false;
        Box area=new Box(from,to).expand(p.getWidth()/2+.05).stretch(0,p.getHeight(),0);
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(area.minX,area.minY,area.minZ),BlockPos.ofFloored(area.maxX,area.maxY,area.maxZ))) {
            if(!world.isChunkLoaded(pos))continue;
            for(Box shape:world.getBlockState(pos).getCollisionShape(world,pos).getBoundingBoxes()) {
                Box b=shape.offset(pos);
                // Ordinary slab/stair step height remains available; full-block hills are not auto-climbed.
                if(b.maxY<=Math.min(from.y,to.y)+(p.isOnGround()?.6:.03))continue;
                Box expanded=new Box(b.minX-p.getWidth()/2,b.minY-p.getHeight()+.05,b.minZ-p.getWidth()/2,
                        b.maxX+p.getWidth()/2,b.maxY-.03,b.maxZ+p.getWidth()/2);
                Vec3d point=expanded.contains(from)?from:expanded.raycast(from,to).orElse(null);
                if(point!=null && from.squaredDistanceTo(point)<dist) {
                    nearest=point;dist=from.squaredDistanceTo(point);
                    ceiling=to.y>from.y && Math.abs(point.y-expanded.minY)<.001;
                }
            }
        }
        return nearest==null?null:new Contact(nearest,ceiling);
    }
    private static void impact(Run run,Vec3d feet,Vec3d direction,LivingEntity direct) {
        var p=run.player;var world=run.world;float power=StoneTechniqueRules.power(run.speed);
        Vec3d origin=feet.add(0,p.getHeight()*.5,0);
        double depth=StoneTechniqueRules.depth(power);
        for(var target:world.getEntitiesByClass(LivingEntity.class,new Box(origin,origin).expand(depth+1,2,depth+1),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity) && !WandAllies.protectedFrom(p,e))) {
            Vec3d relative=target.getBoundingBox().getCenter().subtract(origin);
            double ahead=relative.dotProduct(direction),side=relative.subtract(direction.multiply(ahead)).horizontalLength();
            if(target!=direct && (power<.3 || ahead<0 || ahead>depth || side>.65+ahead*.5 || Math.abs(relative.y)>1.25))continue;
            if(world.raycast(new RaycastContext(origin,target.getBoundingBox().getCenter(),RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            float amount=StoneTechniqueRules.damage(power)*(target==direct?1:(float)(.65-.25*ahead/depth));
            if(SpellCombat.damage(target,world,p.getDamageSources().playerAttack(p),amount,p,WizardAffinity.STONE)) {
                AbstractWandItem.onWandDamageDealt(p,amount,WizardAffinity.STONE);
                if(!(target instanceof FracturedGuardianEntity)) {
                    target.addVelocity(direction.x*(.35+power),.12+power*.12,direction.z*(.35+power));target.velocityModified=true;
                }
            }
        }
        breakCone(p,origin,direction,power,feet.y);
        world.spawnParticles(ModParticles.STONE_SHOCKWAVE,origin.x,origin.y-.6,origin.z,3,.4,.1,.4,0);
        for(int i=0;i<20;i++) {
            double distance=world.random.nextDouble()*depth,side=(world.random.nextDouble()-.5)*(1+distance);
            Vec3d point=origin.add(direction.multiply(distance)).add(-direction.z*side,world.random.nextDouble()-.5,direction.x*side);
            world.spawnParticles(ModParticles.STONE_SHARD,point.x,point.y,point.z,1,.1,.1,.1,.07);
        }
        world.playSound(null,BlockPos.ofFloored(feet),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.PLAYERS,.6f+power,.75f);
        p.setVelocity(0,p.getVelocity().y,0);p.velocityModified=true;
    }
    /** Explicit material tiers intentionally exclude ores, machinery, containers and magical blocks. */
    public static int materialCost(BlockState state) {
        if(state.hasBlockEntity())return 0;
        if(state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.GLASS) || state.isOf(Blocks.GLASS_PANE))return 1;
        if(state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_FENCES))return 2;
        if(state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE)
                || state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.GRANITE))return 6;
        return 0;
    }
    public static int breakCone(ServerPlayerEntity p,Vec3d origin,Vec3d direction,float power,double feetY) {
        int budget=StoneTechniqueRules.budget(power),broken=0;double depth=StoneTechniqueRules.depth(power);
        if(budget==0 || !p.canModifyBlocks())return 0;
        var world=p.getEntityWorld();List<BlockPos> candidates=new ArrayList<>();
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(origin.add(-depth,-1,-depth)),BlockPos.ofFloored(origin.add(depth,1,depth)))) {
            if(pos.getY()<Math.floor(feetY) || !world.isChunkLoaded(pos))continue;
            Vec3d delta=pos.toCenterPos().subtract(origin);double along=delta.dotProduct(direction);
            if(along<0 || along>depth || delta.subtract(direction.multiply(along)).horizontalLength()>.65+along*.5)continue;
            candidates.add(pos.toImmutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.toCenterPos().squaredDistanceTo(origin)));
        for(BlockPos pos:candidates) {
            var state=world.getBlockState(pos);int cost=materialCost(state);
            if(cost==0 || cost>budget || (cost>=6 && (power<.98f || pos.toCenterPos().subtract(origin).dotProduct(direction)>1.8))
                    || state.getHardness(world,pos)<0 || world.getBlockEntity(pos)!=null || !world.getWorldBorder().contains(pos)
                    || world.getServer().isSpawnProtected(world,pos,p) || GuardianArenaManager.protectedBlock(world,pos)
                    || GuardianChurchManager.protectedBlock(world,pos) || TemporaryBlockManager.isTracked(world,pos))continue;
            var ray=world.raycast(new RaycastContext(origin,pos.toCenterPos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
            if(ray.getType()!=HitResult.Type.MISS && !ray.getBlockPos().equals(pos))continue;
            if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,p,pos,state,null))continue;
            if(world.breakBlock(pos,true,p)) {budget-=cost;broken++;}
        }
        return broken;
    }
    private static void cooldown(Run run) {
        AbstractWandItem.startCooldown(run.world,run.wand,StoneTechniqueRules.CHARGE,StoneTechniqueRules.CHARGE_COOLDOWN,false);
        READY.put(run.player.getUuid(),run.world.getServer().getTicks()+StoneTechniqueRules.CHARGE_COOLDOWN);
    }
    private static void finish(Run run) {
        if(RUNS.remove(run.player.getUuid())==null)return;
        if(!run.released)cooldown(run);
        sync(run,0);
    }
    private static void sync(Run run,int mode) {
        ((StoneMotionAccess)run.player).elementalwands$stoneMotion(run.yaw,(float)run.speed,mode);
        if(!run.player.isDisconnected())ServerPlayNetworking.send(run.player,
                new ModNetworking.StoneMotionPayload(run.player.getId(),run.yaw,(float)run.speed,mode));
    }
    private StoneChargeManager() {}
}
