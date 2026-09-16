package com.anton.elementalwands.entity;

import com.anton.elementalwands.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Invisible, server-authoritative carrier; both sides sample the same committed arc. */
public final class FireLeapEntity extends Entity {
    private static final TrackedData<BlockPos> ANCHOR=DataTracker.registerData(FireLeapEntity.class,TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Vector3f> FROM=DataTracker.registerData(FireLeapEntity.class,TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Vector3f> TO=DataTracker.registerData(FireLeapEntity.class,TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Long> START=DataTracker.registerData(FireLeapEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Boolean> LOCKED=DataTracker.registerData(FireLeapEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private boolean loaded;
    private ServerPlayerEntity owner;
    private final java.util.Set<java.util.UUID> hits=new java.util.HashSet<>();
    public FireLeapEntity(EntityType<? extends FireLeapEntity> type,World world) { super(type,world);setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder b) {
        b.add(ANCHOR,BlockPos.ORIGIN);b.add(FROM,new Vector3f());b.add(TO,new Vector3f());b.add(START,0L);b.add(LOCKED,true);
    }
    public void begin(ServerPlayerEntity player,Vec3d target) {
        owner=player;setYaw(player.getYaw());setPitch(player.getPitch());BlockPos anchor=player.getBlockPos();dataTracker.set(ANCHOR,anchor);
        Vec3d base=Vec3d.of(anchor);
        dataTracker.set(FROM,player.getEntityPos().subtract(base).toVector3f());dataTracker.set(TO,target.subtract(base).toVector3f());
        dataTracker.set(START,getEntityWorld().getTime());setPosition(origin());
    }
    public Vec3d origin() { return Vec3d.of(dataTracker.get(ANCHOR)).add(new Vec3d(dataTracker.get(FROM))); }
    public Vec3d destination() { return Vec3d.of(dataTracker.get(ANCHOR)).add(new Vec3d(dataTracker.get(TO))); }
    public double elapsed(float partial) { return getEntityWorld().getTime()-dataTracker.get(START)+partial; }
    public boolean locksPassenger() { return dataTracker.get(LOCKED) && !loaded && !isRemoved(); }
    private Vec3d sample(double age) { return FireLeapRules.position(origin(),destination(),age/FireLeapRules.FLIGHT); }
    @Override public void tick() {
        super.tick();
        if(getEntityWorld().isClient()) { if(locksPassenger()) advance(sample(elapsed(0))); return; }
        if(loaded || owner==null || !owner.isAlive() || owner.isSpectator() || owner.isDisconnected()
                || owner.getEntityWorld()!=getEntityWorld() || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(owner)) { abort();return; }
        double age=elapsed(0);
        if(locksPassenger()) {
            if(owner.getVehicle()!=this || owner.getEntityPos().squaredDistanceTo(getEntityPos())>4) { abort();return; }
            Vec3d next=sample(age);
            if(!FireLeapRules.segment(getEntityWorld(),owner,getEntityPos(),next)
                    || !com.anton.elementalwands.arena.GuardianArenaManager.canTeleport(owner,(ServerWorld)getEntityWorld(),next)) { abort();return; }
            advance(next);
            if(age>=FireLeapRules.FLIGHT) {
                if(!FireLeapRules.supported(getEntityWorld(),owner,destination(),.15)) { abort();return; }
                release();FireLeapManager.landed(owner,destination());
            }
        }
        if(!locksPassenger()) {
            FireLeapManager.wave(owner,destination(),(int)age-FireLeapRules.FLIGHT,hits);
            if(age>=FireLeapRules.FLIGHT+Math.ceil(FireLeapRules.WAVE_RANGE/FireLeapRules.WAVE_SPEED)-1) discard();
        }
    }
    private void advance(Vec3d next) {
        Vec3d previous=sample(elapsed(0)-1);
        setPosition(next);lastX=lastRenderX=previous.x;lastY=lastRenderY=previous.y;lastZ=lastRenderZ=previous.z;
        setVelocity(Vec3d.ZERO);
        for(Entity passenger:getPassengerList()) { updatePassengerPosition(passenger);passenger.fallDistance=0;passenger.setVelocity(Vec3d.ZERO); }
    }
    private void release() { dataTracker.set(LOCKED,false);removeAllPassengers(); }
    public void abort() { release();discard(); }
    @Override public Vec3d getPassengerRidingPos(Entity passenger) { return getEntityPos(); }
    @Override public Vec3d updatePassengerForDismount(LivingEntity passenger) { return getEntityPos(); }
    @Override protected void updatePassengerPosition(Entity passenger,PositionUpdater updater) {
        if(hasPassenger(passenger)) {
            updater.accept(passenger,getX(),getY(),getZ());Vec3d previous=sample(elapsed(0)-1);
            passenger.lastX=passenger.lastRenderX=previous.x;passenger.lastY=passenger.lastRenderY=previous.y;passenger.lastZ=passenger.lastRenderZ=previous.z;
        }
    }
    @Override public boolean shouldRender(double distance) { return false; }
    @Override public boolean damage(ServerWorld world,DamageSource source,float amount) { return false; }
    @Override protected void readCustomData(ReadView view) { loaded=true; }
    @Override protected void writeCustomData(WriteView view) {}
}
