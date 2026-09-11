package com.anton.elementalwands.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

/** One synchronized architectural shell; moving stone never overwrites the original terrain. */
public final class GuardianArenaEntity extends Entity {
    private static final TrackedData<Float> FLOOR = DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> WALL = DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> DRAW_FLOOR = DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> MOTION_FROM=DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> MOTION_TO=DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Long> MOTION_START=DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> MOTION_DURATION=DataTracker.registerData(GuardianArenaEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private float previousFloor, previousRadius, previousWall;
    private boolean initialized, loaded;
    private long motionTick;

    public GuardianArenaEntity(EntityType<? extends GuardianArenaEntity> type, World world) {
        super(type,world); setNoGravity(true);
    }
    @Override protected void initDataTracker(DataTracker.Builder b) {
        b.add(MOTION_FROM,0f); b.add(MOTION_TO,0f); b.add(MOTION_START,0L); b.add(MOTION_DURATION,0);
        b.add(FLOOR,0f); b.add(RADIUS,0f); b.add(WALL,0f); b.add(DRAW_FLOOR,true);
    }
    public void update(float floor, float radius, float wall, boolean drawFloor) {
        if (dataTracker.get(MOTION_DURATION)==0) dataTracker.set(FLOOR,floor); dataTracker.set(RADIUS,radius); dataTracker.set(WALL,wall);
        dataTracker.set(DRAW_FLOOR,drawFloor);
    }
    public void animateFloor(float from,float to,long start,int duration) {
        dataTracker.set(MOTION_FROM,from); dataTracker.set(MOTION_TO,to);
        dataTracker.set(MOTION_START,start); dataTracker.set(MOTION_DURATION,duration);
    }
    @Override public void tick() {
        super.tick();
        if (loaded && !getEntityWorld().isClient()) { discard(); return; }
        motionTick=getEntityWorld().getTime();
        previousFloor=dataTracker.get(FLOOR); previousRadius=dataTracker.get(RADIUS); previousWall=dataTracker.get(WALL);
        initialized=true;
    }
    public float floor(float delta) {
        if (dataTracker.get(MOTION_DURATION)>0)
            return (float)com.anton.elementalwands.arena.GuardianArenaMotion.renderedHeight(dataTracker.get(MOTION_FROM),dataTracker.get(MOTION_TO),
                    dataTracker.get(MOTION_START),dataTracker.get(MOTION_DURATION),initialized?motionTick:getEntityWorld().getTime(),delta);
        return initialized ? net.minecraft.util.math.MathHelper.lerp(delta,previousFloor,dataTracker.get(FLOOR)) : dataTracker.get(FLOOR);
    }
    public float radius(float delta) { return initialized ? net.minecraft.util.math.MathHelper.lerp(delta,previousRadius,dataTracker.get(RADIUS)) : dataTracker.get(RADIUS); }
    public float wall(float delta) { return initialized ? net.minecraft.util.math.MathHelper.lerp(delta,previousWall,dataTracker.get(WALL)) : dataTracker.get(WALL); }
    public boolean drawFloor() { return dataTracker.get(DRAW_FLOOR); }
    @Override public boolean shouldRender(double distance) { return distance < 512*512; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
    @Override protected void readCustomData(ReadView view) { loaded=true; }
    @Override protected void writeCustomData(WriteView view) {}
}
