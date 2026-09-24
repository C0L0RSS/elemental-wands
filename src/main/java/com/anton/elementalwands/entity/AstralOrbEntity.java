package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.AstralDoubleManager;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** A short, harmless toss. Wall contacts remove horizontal motion so it drops to a floor. */
public final class AstralOrbEntity extends Entity {
    private final PositionInterpolator interpolator=new PositionInterpolator(this,2);
    private ServerPlayerEntity owner;
    public AstralOrbEntity(EntityType<? extends AstralOrbEntity> type,World world){super(type,world);setNoGravity(true);}
    public AstralOrbEntity(ServerWorld world,ServerPlayerEntity p){
        this(ModEntities.ASTRAL_ORB,world);owner=p;setPosition(p.getEyePos().add(0,-.12,0));
        setVelocity(p.getRotationVec(1).multiply(.65).add(0,.18,0));
    }
    @Override public PositionInterpolator getInterpolator(){return interpolator;}
    @Override protected void initDataTracker(DataTracker.Builder builder){}
    @Override public void tick(){
        super.tick();if(getEntityWorld().isClient()){interpolator.tick();return;}
        if(!(getEntityWorld() instanceof ServerWorld world))return;
        if(owner==null || !AstralDoubleManager.owns(owner,this)){discard();return;}
        Vec3d from=getEntityPos(),to=from.add(getVelocity());
        if(age>100 || !world.isChunkLoaded(BlockPos.ofFloored(to)) || !world.getWorldBorder().contains(BlockPos.ofFloored(to))
                || to.y<world.getBottomY() || to.y>world.getTopYInclusive()) {AstralDoubleManager.failed(owner,this);return;}
        var hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,this));
        if(hit.getType()!=HitResult.Type.MISS){
            if(!world.getFluidState(hit.getBlockPos()).isEmpty()){AstralDoubleManager.failed(owner,this);return;}
            if(hit.getSide()==Direction.UP){AstralDoubleManager.land(owner,this,hit.getPos().add(0,.01,0));return;}
            setPosition(hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(.34)));setVelocity(0,-.16,0);
        } else {setPosition(to);setVelocity(getVelocity().multiply(.99).add(0,-.06,0));}
        world.spawnParticles(ModParticles.SPACE_MOTE,getX(),getY(),getZ(),1,.025,.025,.025,.003);
    }
    @Override protected void readCustomData(ReadView view){owner=null;}
    @Override protected void writeCustomData(WriteView view){}
    @Override public boolean shouldSave(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean canBeHitByProjectile(){return false;}
    @Override public boolean damage(ServerWorld w,DamageSource s,float amount){return false;}
}
