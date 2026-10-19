package com.anton.elementalwands.entity.roc;

import com.anton.elementalwands.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Synchronized encounter presentation. Only StormRocCombat applies damage or movement. */
public final class RocEffectEntity extends Entity {
    /**
     * New kinds go at the end: clients read the ordinal. LIVE_GROUND is a large strike's lingering
     * circle; CHARGE_BOLT is the huge bolt that charges the Roc; STORM_BOLT falls from phase two's
     * storm ceiling and its radius is the bolt's length. STORM draws nothing: it marks phase two's
     * storm at the summit's centre, which every challenger's client tracks wherever the Roc is.
     */
    public enum Kind { SMALL_STRIKE, LARGE_STRIKE, TORNADO, DIVE_MARKER, SHOCKWAVE, SCREAM, DISTANT_BOLT, LIVE_GROUND, CHARGE_BOLT, STORM_BOLT, STORM, CALL_BOLT }

    private static final TrackedData<Integer> KIND = DataTracker.registerData(RocEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DURATION = DataTracker.registerData(RocEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WARNING = DataTracker.registerData(RocEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> RADIUS = DataTracker.registerData(RocEffectEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Long> START = DataTracker.registerData(RocEffectEntity.class, TrackedDataHandlerRegistry.LONG);
    private StormRocEntity owner;

    public RocEffectEntity(EntityType<? extends RocEffectEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(KIND, 0);
        builder.add(DURATION, 1);
        builder.add(WARNING, 0);
        builder.add(RADIUS, 1f);
        builder.add(START, 0L);
    }

    public static RocEffectEntity spawn(StormRocEntity owner, Kind kind, Vec3d pos, double radius, int warning, int duration) {
        var e = new RocEffectEntity(ModEntities.ROC_EFFECT, owner.getEntityWorld());
        e.owner = owner;
        e.setPosition(pos);
        e.dataTracker.set(KIND, kind.ordinal());
        e.dataTracker.set(RADIUS, (float) radius);
        e.dataTracker.set(WARNING, warning);
        e.dataTracker.set(DURATION, duration);
        e.dataTracker.set(START, owner.getEntityWorld().getTime());
        owner.getEntityWorld().spawnEntity(e);
        return e;
    }

    public Kind kind() { return Kind.values()[Math.clamp(dataTracker.get(KIND), 0, Kind.values().length - 1)]; }
    public float radius() { return dataTracker.get(RADIUS); }
    void resize(double radius) { dataTracker.set(RADIUS, (float) radius); }
    public int duration() { return dataTracker.get(DURATION); }
    public int warningTicks() { return dataTracker.get(WARNING); }
    public float effectTicks(float partial) { return getEntityWorld().getTime() - dataTracker.get(START) + partial; }

    @Override public void tick() {
        super.tick();
        if (getEntityWorld() instanceof ServerWorld && (owner == null || owner.isRemoved() || !owner.isAlive() || effectTicks(0) > duration()))
            discard();
    }

    /** A warning's tiny non-colliding entity box must not hide arena-sized presentation. */
    @Override public boolean shouldRender(double squaredDistance) { return squaredDistance < 256.0 * 256.0; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
    @Override protected void readCustomData(ReadView view) {}
    @Override protected void writeCustomData(WriteView view) {}
}
