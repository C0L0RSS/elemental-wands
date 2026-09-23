package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.projectile.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;

/** Prepared decoration becomes a straight, non-piercing projectile on release. */
public final class GaleDaggerEntity extends ProjectileEntity {
    public static final RegistryKey<DamageType> DAMAGE_TYPE=RegistryKey.of(RegistryKeys.DAMAGE_TYPE,Identifier.of("elementalwands","gale_dagger"));
    private static final TrackedData<Integer> OWNER=DataTracker.registerData(GaleDaggerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> INDEX=DataTracker.registerData(GaleDaggerEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> FIRED=DataTracker.registerData(GaleDaggerEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private double travelled, trailTravelled, nextRing=1.5;
    private int flyingTicks;
    public GaleDaggerEntity(EntityType<? extends GaleDaggerEntity> type,World world){super(type,world);setNoGravity(true);}
    public GaleDaggerEntity(ServerWorld world,ServerPlayerEntity p,int index){
        this(ModEntities.GALE_DAGGER,world);setOwner(p);dataTracker.set(OWNER,p.getId());dataTracker.set(INDEX,index);
        setPosition(GaleDaggers.anchor(p,index));setYaw(p.getYaw());
    }
    @Override protected void initDataTracker(DataTracker.Builder b){b.add(OWNER,-1);b.add(INDEX,0);b.add(FIRED,false);}
    public boolean fired(){return dataTracker.get(FIRED);}
    public int index(){return dataTracker.get(INDEX);}
    public Entity visualOwner(){return getEntityWorld().getEntityById(dataTracker.get(OWNER));}
    @Override public boolean shouldSave(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean canBeHitByProjectile(){return false;}
    public void launch(ServerPlayerEntity p){
        Vec3d origin=GaleDaggers.anchor(p,index()),eye=p.getEyePos(),look=Vec3d.fromPolar(p.getPitch(),p.getYaw()).normalize();
        // Prevent the elevated formation from firing through a low ceiling or wall.
        var cover=p.getEntityWorld().raycast(new RaycastContext(eye,origin,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(cover.getType()!=HitResult.Type.MISS){discard();return;}
        // Converge on the crosshair hit point; each launch samples fresh aim, with no later guidance.
        Vec3d end=eye.add(look.multiply(GaleDaggers.RANGE));
        var block=p.getEntityWorld().raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        var target=ProjectileUtil.raycast(p,eye,end,p.getBoundingBox().stretch(end.subtract(eye)).expand(1),
                e->e.isAlive()&&e.canBeHitByProjectile()&&!WandAllies.protectedFrom(p,e),eye.squaredDistanceTo(end));
        if(target!=null)end=target.getPos();
        Vec3d direction=end.subtract(origin).normalize();
        setPosition(origin);setVelocity(direction.multiply(GaleDaggers.SPEED));
        setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        setPitch((float)Math.toDegrees(-Math.asin(direction.y)));dataTracker.set(FIRED,true);velocityDirty=true;
        p.getEntityWorld().playSound(null,getBlockPos(),SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,SoundCategory.PLAYERS,.55f,1.7f);
    }
    @Override public void tick(){
        super.tick();
        if(!fired()){
            if(getEntityWorld() instanceof ServerWorld){
                if(!(getOwner() instanceof ServerPlayerEntity p)||!GaleDaggers.ownsPrepared(p,this)){discard();return;}
                setPosition(GaleDaggers.anchor(p,index()));setYaw(p.getYaw());
            }
            return;
        }
        if(getEntityWorld() instanceof ServerWorld world){
            if(!(getOwner() instanceof ServerPlayerEntity p)||!p.isAlive()||p.isRemoved()||p.getEntityWorld()!=world
                    ||!com.anton.elementalwands.arena.GuardianArenaManager.canCast(p)){discard();return;}
            double remaining=GaleDaggers.RANGE-travelled;
            if(remaining<=1e-6||++flyingTicks>30){discard();return;}
            Vec3d step=getVelocity();if(step.length()>remaining)step=step.normalize().multiply(remaining);
            Vec3d from=getEntityPos();
            setVelocity(step);var hit=ProjectileUtil.getCollision(this,this::canHit);
            if(hit.getType()!=HitResult.Type.MISS){trail(world,from,hit.getPos());setPosition(hit.getPos());onCollision(hit);return;}
            setPosition(getEntityPos().add(step));travelled+=step.length();
            trail(world,from,getEntityPos());
            if(travelled>=GaleDaggers.RANGE-1e-6)discard();
        }else setPosition(getEntityPos().add(getVelocity()));
    }
    private void trail(ServerWorld world,Vec3d from,Vec3d to){
        Vec3d delta=to.subtract(from);double distance=delta.length();
        if(distance<1e-6)return;
        Vec3d axis=delta.normalize();
        // Distance-based spacing stays even across ticks and ends exactly at cover.
        while(nextRing<=trailTravelled+distance+1e-6){
            Vec3d at=from.add(axis.multiply(nextRing-trailTravelled));
            world.spawnParticles(ModParticles.GALE_DAGGER_TRAIL,at.x,at.y,at.z,0,axis.x,axis.y,axis.z,1);
            nextRing+=1.5;
        }
        trailTravelled+=distance;
    }

    @Override protected boolean canHit(Entity e){return fired()&&super.canHit(e)&&!WandAllies.protectedFrom(getOwner(),e);}
    @Override protected void onEntityHit(EntityHitResult hit){
        if(getEntityWorld() instanceof ServerWorld world){
            var source=new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE),this,getOwner());
            if(SpellCombat.damage(hit.getEntity(),world,source,GaleDaggers.DAMAGE,getOwner(),WizardAffinity.WIND))
                AbstractWandItem.onWandDamageDealt(getOwner(),GaleDaggers.DAMAGE,WizardAffinity.WIND);
        }
        discard();
    }
    @Override protected void onBlockHit(BlockHitResult hit){discard();}
}
