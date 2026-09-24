package com.anton.elementalwands.entity.necromancer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Raised archer fodder: no daylight burning, stray conversion, loot or saving. */
public class SpectralSkeletonEntity extends SkeletonEntity implements NecromancerMinion {
    private final State minion = new State();

    public SpectralSkeletonEntity(EntityType<? extends SpectralSkeletonEntity> type, World world) {
        super(type, world);
        experiencePoints = 0;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        updateAttackType();
    }

    @Override public State minionState() { return minion; }
    @Override public void tick() {
        super.tick();
        setConverting(false);
        NecromancerMinion.tick(this);
    }
    @Override protected boolean isInSameTeam(Entity other) { return NecromancerMinion.sameSide(this, other) || super.isInSameTeam(other); }
    @Override public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
        return NecromancerMinion.ignores(this, source) || super.isInvulnerableTo(world, source);
    }
    @Override public boolean isPushable() { return !NecromancerMinion.rising(this) && super.isPushable(); }
    @Override protected boolean isAffectedByDaylight() { return false; }
    @Override public boolean canFreeze() { return false; }
    @Override protected boolean shouldDropLoot(ServerWorld world) { return false; }
    @Override protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean canImmediatelyDespawn(double distance) { return false; }
}
