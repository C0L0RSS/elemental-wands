package com.anton.elementalwands.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

/** Synchronized, non-colliding visual front; the existing scheduler owns all combat. */
public final class PyreFrontEntity extends Entity {
    private final PositionInterpolator interpolator = new PositionInterpolator(this, 2);
    private int lifetime;
    private boolean loaded;
    public PyreFrontEntity(EntityType<? extends PyreFrontEntity> type, World world) {
        super(type, world); setNoGravity(true); noClip = true;
    }
    @Override public PositionInterpolator getInterpolator() { return interpolator; }
    @Override public void tick() {
        super.tick();
        if (getEntityWorld().isClient()) interpolator.tick();
        else if (loaded || ++lifetime > 48) discard();
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) {}
    @Override protected void readCustomData(ReadView view) { loaded = true; }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
