package com.anton.elementalwands.entity;

import com.anton.elementalwands.data.*;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Harmless thrown pod, or a synchronized rendering of the pad's exact authored mesh. */
public final class SpringbloomEntity extends Entity {
    private static final TrackedData<Boolean> OPEN=DataTracker.registerData(SpringbloomEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> START=DataTracker.registerData(SpringbloomEntity.class,TrackedDataHandlerRegistry.LONG);
    private final PositionInterpolator interpolator=new PositionInterpolator(this,2);
    private ServerPlayerEntity owner;
    public SpringbloomEntity(EntityType<? extends SpringbloomEntity> type,World world){super(type,world);setNoGravity(true);}
    public void launch(ServerPlayerEntity caster){
        owner=caster;setPosition(caster.getEyePos().add(0,-.10,0));
        // A falling caster's downward speed lets a last-second throw reach the ground first.
        setVelocity(caster.getRotationVec(1).multiply(1.7).add(0,caster.isOnGround()?0:Math.min(0,SpringbloomManager.motion(caster).y),0));
    }
    public void open(BlockPos pos){setPosition(Vec3d.ofBottomCenter(pos));dataTracker.set(OPEN,true);dataTracker.set(START,getEntityWorld().getTime());}
    public int openCells(){
        var state=getEntityWorld().getBlockState(getBlockPos());
        return state.isOf(ModSpellBlocks.SPRINGBLOOM)?state.get(com.anton.elementalwands.block.SpringbloomBlock.OPEN_CELLS):0;
    }
    public boolean open(){return dataTracker.get(OPEN);}
    public float elapsed(float delta){return (float)(getEntityWorld().getTime()-dataTracker.get(START))+delta;}
    @Override public PositionInterpolator getInterpolator(){return interpolator;}
    @Override protected void initDataTracker(DataTracker.Builder builder){builder.add(OPEN,false);builder.add(START,0L);}
    @Override public void tick(){
        super.tick();
        if(getEntityWorld().isClient()){interpolator.tick();return;}
        if(!(getEntityWorld() instanceof ServerWorld world))return;
        if(open()) {if(!SpringbloomManager.hasPad(world,getBlockPos()))discard();return;}
        if(owner==null || !owner.isAlive() || owner.isRemoved() || owner.getEntityWorld()!=world
                || world.getPlayerByUuid(owner.getUuid())!=owner || EWAttachments.getAffinity(owner)!=WizardAffinity.NATURE
                || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(owner)){discard();return;}
        Vec3d from=getEntityPos(),to=from.add(getVelocity());
        if(age>SpringbloomRules.FLIGHT_LIMIT || !world.isChunkLoaded(BlockPos.ofFloored(to))
                || !world.getWorldBorder().contains(BlockPos.ofFloored(to)) || to.y<world.getBottomY() || to.y>world.getTopYInclusive()){discard();return;}
        var hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,this));
        if(hit.getType()!=HitResult.Type.MISS){
            if(hit.getSide()==Direction.UP && world.getFluidState(hit.getBlockPos()).isEmpty())
                SpringbloomManager.plant(owner,BlockPos.ofFloored(hit.getPos().add(0,.01,0)));
            else if(world.getFluidState(hit.getBlockPos()).isEmpty()){
                setPosition(hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(.04)));setVelocity(0,-.2,0);return;
            }
            world.spawnParticles(ModParticles.NATURE_LEAF,hit.getPos().x,hit.getPos().y+.1,hit.getPos().z,6,.1,.1,.1,.01);
            discard();return;
        }
        setPosition(to);setVelocity(getVelocity().multiply(.99).add(0,-.09,0));
    }
    @Override protected void readCustomData(ReadView view){owner=null;}
    @Override protected void writeCustomData(WriteView view){}
    @Override public boolean shouldSave(){return false;}
    @Override public boolean isAttackable(){return false;}
    @Override public boolean canBeHitByProjectile(){return false;}
    @Override public boolean damage(ServerWorld world,DamageSource source,float amount){return false;}
}
