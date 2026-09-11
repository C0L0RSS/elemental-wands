package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaMotion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Invisible cinematic carrier. Vanilla passenger interpolation replaces per-tick player teleports. */
public final class GuardianLiftEntity extends Entity {
    private static final TrackedData<Float> FROM=DataTracker.registerData(GuardianLiftEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TO=DataTracker.registerData(GuardianLiftEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Long> START=DataTracker.registerData(GuardianLiftEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> DURATION=DataTracker.registerData(GuardianLiftEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> LOCKED=DataTracker.registerData(GuardianLiftEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> FALLING=DataTracker.registerData(GuardianLiftEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private boolean loaded;
    public GuardianLiftEntity(EntityType<? extends GuardianLiftEntity> type,World world) {
        super(type,world); setNoGravity(true); noClip=true;
    }
    @Override protected void initDataTracker(DataTracker.Builder b) {
        b.add(FROM,0f); b.add(TO,0f); b.add(START,0L); b.add(DURATION,0); b.add(LOCKED,true); b.add(FALLING,false);
    }
    public void animate(double from,double to,long start,int duration) {
        dataTracker.set(FALLING,false);
        dataTracker.set(FROM,(float)from); dataTracker.set(TO,(float)to);
        dataTracker.set(START,start); dataTracker.set(DURATION,duration);
        advance();
    }
    public void drop(double from,double to,long start,int duration) {
        animate(from,to,start,duration);dataTracker.set(FALLING,true);advance();
    }
    private double height(long tick) {
        if(dataTracker.get(FALLING)) {
            double t=Math.clamp((tick-dataTracker.get(START))/(double)Math.max(1,dataTracker.get(DURATION)),0,1);
            return dataTracker.get(FROM)+(dataTracker.get(TO)-dataTracker.get(FROM))*t*t;
        }
        return GuardianArenaMotion.height(dataTracker.get(FROM),dataTracker.get(TO),dataTracker.get(START),dataTracker.get(DURATION),tick);
    }
    public void advance() {
        long tick=getEntityWorld().getTime();
        setPosition(getX(),height(tick),getZ());
        lastX=lastRenderX=getX(); lastY=lastRenderY=height(tick-1); lastZ=lastRenderZ=getZ();
        setVelocity(Vec3d.ZERO);
        for (Entity passenger:getPassengerList()) { updatePassengerPosition(passenger); passenger.fallDistance=0; }
    }
    @Override public void tick() {
        super.tick();
        if (loaded && !getEntityWorld().isClient()) { release(); return; }
        advance();
    }
    public boolean locksPassenger() { return dataTracker.get(LOCKED) && !loaded && !isRemoved(); }
    public void release() { dataTracker.set(LOCKED,false); removeAllPassengers(); discard(); }
    @Override public Vec3d getPassengerRidingPos(Entity passenger) { return getEntityPos(); }
    @Override public Vec3d updatePassengerForDismount(LivingEntity passenger) { return getEntityPos(); }
    @Override protected void updatePassengerPosition(Entity passenger,PositionUpdater updater) {
        if (hasPassenger(passenger)) {
            updater.accept(passenger,getX(),getY(),getZ());
            // ClientWorld resets riders after ticking the vehicle. Restore BOTH the camera
            // and entity-render samples here, after tickRiding, to keep them on the floor.
            passenger.lastX=passenger.lastRenderX=getX();
            passenger.lastY=passenger.lastRenderY=height(getEntityWorld().getTime()-1);
            passenger.lastZ=passenger.lastRenderZ=getZ();
        }
    }
    @Override public boolean shouldRender(double distance) { return false; }
    @Override public boolean damage(ServerWorld world,DamageSource source,float amount) { return false; }
    @Override protected void readCustomData(ReadView view) { loaded=true; }
    @Override protected void writeCustomData(WriteView view) {}
}
