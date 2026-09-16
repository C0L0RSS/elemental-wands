package com.anton.elementalwands.entity;

import com.anton.elementalwands.util.*;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.ModParticles;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.joml.Vector3f;

/** A harmless thrown coal that sticks to solid surfaces and arms for remote detonation. */
public final class FlashoverEmberEntity extends Entity {
    private static final TrackedData<Boolean> SETTLED=DataTracker.registerData(FlashoverEmberEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ARMED=DataTracker.registerData(FlashoverEmberEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> HOST=DataTracker.registerData(FlashoverEmberEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> OFFSET=DataTracker.registerData(FlashoverEmberEntity.class,TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Boolean> PRIMED=DataTracker.registerData(FlashoverEmberEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private final PositionInterpolator interpolator=new PositionInterpolator(this, 2);
    private boolean exploded;
    private LivingEntity attached;
    private ServerPlayerEntity caster;
    private BlockPos anchor;
    private long created,settledAt;
    public FlashoverEmberEntity(EntityType<? extends FlashoverEmberEntity> type,World world) { super(type,world);setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder b) { b.add(SETTLED,false);b.add(ARMED,false);b.add(HOST,-1);b.add(OFFSET,new Vector3f());b.add(PRIMED,false); }
    public void launch(ServerPlayerEntity p) {
        caster=p;created=p.getEntityWorld().getTime();setPosition(p.getEyePos().add(0,-.12,0));
        setVelocity(p.getRotationVec(1).multiply(FlashoverRules.SPEED));
    }
    @Override public PositionInterpolator getInterpolator() { return interpolator; }
    public void finishExplosion() { exploded=true;discard(); }
    @Override public void remove(RemovalReason reason) {
        if(!isRemoved() && caster!=null && getEntityWorld() instanceof ServerWorld) FlashoverManager.recover(caster,this,exploded);
        super.remove(reason);
    }
    public int attachedId() { return dataTracker.get(HOST); }
    public boolean primed() { return dataTracker.get(PRIMED); }
    public void prime() { dataTracker.set(PRIMED,true); }
    public Vec3d attachedPosition(float partial) {
        Entity found=getEntityWorld().getEntityById(attachedId());
        if(!(found instanceof LivingEntity host))return getLerpedPos(partial);
        Vec3d local=new Vec3d(dataTracker.get(OFFSET));
        float yaw=MathHelper.lerpAngleDegrees(partial,host.lastBodyYaw,host.bodyYaw);
        return host.getLerpedPos(partial).add(new Vec3d(local.x*host.getWidth()/2,local.y*host.getHeight(),local.z*host.getWidth()/2).rotateY((float)-Math.toRadians(yaw)));
    }
    private void stick(LivingEntity host,Vec3d point) {
        attached=host;Vec3d offset=point.subtract(host.getEntityPos()).rotateY((float)Math.toRadians(host.bodyYaw));
        dataTracker.set(OFFSET,new Vector3f((float)(offset.x/(host.getWidth()/2)),(float)(offset.y/host.getHeight()),(float)(offset.z/(host.getWidth()/2))));
        dataTracker.set(HOST,host.getId());dataTracker.set(SETTLED,true);settledAt=getEntityWorld().getTime();setVelocity(Vec3d.ZERO);velocityDirty=true;
        setPosition(attachedPosition(1));
    }
    /** Also called immediately before a delayed blast so its position and lifetime are current. */
    public boolean refreshAttachment() {
        if(!(getEntityWorld() instanceof ServerWorld w) || isRemoved())return false;
        if(caster==null || !caster.isAlive() || caster.isDisconnected() || caster.getEntityWorld()!=w
                || !FlashoverManager.equipped(caster) || w.getTime()-created>=FlashoverRules.LIFETIME) { discard();return false; }
        if(attachedId()>=0) {
            if(attached==null || !attached.isAlive() || attached.isRemoved() || attached.isSpectator() || attached.getEntityWorld()!=w
                    || attached.isTouchingWater() || WandAllies.protectedFrom(caster,attached)) { discard();return false; }
            setPosition(attachedPosition(1));
        }
        if(!w.isChunkLoaded(getBlockPos())) { discard();return false; }
        if(attachedId()<0 && settled() && (anchor==null || w.getBlockState(anchor).getCollisionShape(w,anchor).isEmpty())) { discard();return false; }
        if(!w.isChunkLoaded(getBlockPos()) || !w.getFluidState(getBlockPos()).isEmpty()) { discard();return false; }
        return true;
    }
    public boolean armed() { return dataTracker.get(ARMED); }
    public boolean settled() { return dataTracker.get(SETTLED); }
    public ServerPlayerEntity caster() { return caster; }
    @Override public void tick() {
        super.tick();
        if(getEntityWorld().isClient()) { interpolator.tick();return; }
        if(!(getEntityWorld() instanceof ServerWorld w))return;
        if(!refreshAttachment())return;
        if(settled()) {
            if(w.getTime()-settledAt>=FlashoverRules.ARM_TICKS)dataTracker.set(ARMED,true);
            if(armed() && age%12==0)w.spawnParticles(ModParticles.FIRE_EMBER,getX(),getY()+.12,getZ(),1,.035,.04,.035,.002);
            return;
        }
        Vec3d from=getEntityPos(),to=from.add(getVelocity());
        if(!w.isChunkLoaded(BlockPos.ofFloored(to)) || !w.getWorldBorder().contains(BlockPos.ofFloored(to))
                || to.y<w.getBottomY() || to.y>w.getTopYInclusive()) { discard();return; }
        var hit=w.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,this));
        Vec3d collisionEnd=hit.getType()==HitResult.Type.MISS?to:hit.getPos();
        var actor=net.minecraft.entity.projectile.ProjectileUtil.raycast(this,from,collisionEnd,getBoundingBox().stretch(getVelocity()).expand(1),
                e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && !WandAllies.protectedFrom(caster,living),from.squaredDistanceTo(collisionEnd));
        if(actor!=null) {
            // Keep the ember just outside the body, where another player can see and hit it.
            Vec3d point=actor.getPos().subtract(getVelocity().normalize().multiply(.12));
            stick((LivingEntity)actor.getEntity(),point);return;
        }
        if(hit.getType()!=HitResult.Type.MISS) {
            if(!w.getFluidState(hit.getBlockPos()).isEmpty()) { discard();return; }
            anchor=hit.getBlockPos();setPosition(hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(.18)));
            setVelocity(Vec3d.ZERO);dataTracker.set(SETTLED,true);settledAt=w.getTime();velocityDirty=true;
        } else {
            setPosition(to);setVelocity(getVelocity().multiply(.99).add(0,-FlashoverRules.GRAVITY,0));
        }
    }
    @Override public boolean canHit() { return !isRemoved(); }
    @Override public boolean isAttackable() { return !isRemoved(); }
    @Override public boolean damage(ServerWorld w,DamageSource source,float amount) {
        if(isRemoved() || amount<=0 || !(source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity attacker))return false;
        if(caster!=null && attacker!=caster && WandAllies.protectedFrom(caster,attacker))return false;
        // Disarming never detonates or damages anything, including when several traps overlap.
        w.spawnParticles(ModParticles.FIRE_EMBER,getX(),getY(),getZ(),4,.09,.05,.09,.01);discard();return true;
    }
    @Override protected void readCustomData(ReadView view) { discard(); }
    @Override protected void writeCustomData(WriteView view) {}
}
