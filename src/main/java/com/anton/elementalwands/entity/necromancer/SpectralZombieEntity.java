package com.anton.elementalwands.entity.necromancer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Raised melee fodder: no daylight burning, drowning conversion, reinforcements, loot or saving. */
public class SpectralZombieEntity extends ZombieEntity implements NecromancerMinion {
    private final State minion = new State();

    public SpectralZombieEntity(EntityType<? extends SpectralZombieEntity> type, World world) {
        super(type, world);
        experiencePoints = 0;
        var reinforcements = getAttributeInstance(EntityAttributes.SPAWN_REINFORCEMENTS);
        if (reinforcements != null) { reinforcements.clearModifiers(); reinforcements.setBaseValue(0); }
    }

    @Override public State minionState() { return minion; }
    @Override public void tick() {
        super.tick();
        NecromancerMinion.tick(this);
    }
    @Override protected boolean isInSameTeam(Entity other) { return NecromancerMinion.sameSide(this, other) || super.isInSameTeam(other); }
    @Override public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
        return NecromancerMinion.ignores(this, source) || super.isInvulnerableTo(world, source);
    }
    @Override public boolean isPushable() { return !NecromancerMinion.rising(this) && super.isPushable(); }
    @Override public boolean isBaby() { return false; }
    @Override protected boolean burnsInDaylight() { return false; }
    @Override protected boolean canConvertInWater() { return false; }
    @Override protected boolean shouldDropLoot(ServerWorld world) { return false; }
    @Override protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
}
