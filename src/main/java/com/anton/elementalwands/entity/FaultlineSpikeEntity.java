package com.anton.elementalwands.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.world.World;

/** Visual-only, non-solid spike. The owning wave performs all authoritative hit tests. */
public final class FaultlineSpikeEntity extends Entity {
    public static final int LIFE = 9;
    private static final TrackedData<Long> START = DataTracker.registerData(FaultlineSpikeEntity.class, TrackedDataHandlerRegistry.LONG);
    public FaultlineSpikeEntity(EntityType<? extends FaultlineSpikeEntity> type, World world) {
        super(type, world); setNoGravity(true); noClip = true;
    }
    public void begin() { dataTracker.set(START, getEntityWorld().getTime()); }
    public float elapsed(float delta) { return getEntityWorld().getTime() - dataTracker.get(START) + delta; }
    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(START, 0L); }
    @Override public void tick() { super.tick(); if (!getEntityWorld().isClient() && elapsed(0) >= LIFE) discard(); }
    @Override protected void readCustomData(ReadView view) { discard(); }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
