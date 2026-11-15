package com.anton.elementalwands.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;

/**
 * One column of the Scar's spike wall. It is solid to players and to nothing else: the Guardian walks
 * and leaps through its own stone, and no shot, spell or line of sight is stopped by it. It places no
 * blocks and is never saved. {@link GuardianBossCombat} owns the damage.
 */
public final class GuardianSpikeEntity extends Entity {
    /** Ticks it takes to come up, and to crumble at the end of its life. */
    public static final int RISE = 4, CRUMBLE = 8;
    private static final TrackedData<Long> START = DataTracker.registerData(GuardianSpikeEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(GuardianSpikeEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public GuardianSpikeEntity(EntityType<? extends GuardianSpikeEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    /** Stands for {@code life} ticks from now. */
    void begin(int life) {
        dataTracker.set(START, getEntityWorld().getTime());
        dataTracker.set(LIFE, life);
    }
    public float elapsed(float delta) { return getEntityWorld().getTime() - dataTracker.get(START) + delta; }
    public int life() { return dataTracker.get(LIFE); }
    /** Up and whole: it blocks the way and hurts to touch. */
    public boolean standing() { return elapsed(0) < life() - CRUMBLE; }

    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(START, 0L); builder.add(LIFE, 0); }
    @Override public void tick() {
        super.tick();
        if (!getEntityWorld().isClient() && elapsed(0) >= life()) discard();
    }
    @Override protected void readCustomData(ReadView view) { discard(); }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isCollidable(Entity other) { return other instanceof PlayerEntity && standing(); }
    @Override public boolean isPushable() { return false; }
    @Override public void pushAwayFrom(Entity entity) {}
    @Override public void addVelocity(double x, double y, double z) {}
    @Override public boolean canHit() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld world, DamageSource source, float amount) { return false; }
}
