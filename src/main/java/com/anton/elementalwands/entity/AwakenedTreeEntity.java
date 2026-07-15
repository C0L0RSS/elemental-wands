package com.anton.elementalwands.entity;

import java.util.UUID;

import com.anton.elementalwands.util.OvergrowthManager;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AwakenedTreeEntity extends HostileEntity {

    private UUID casterUuid;

    public AwakenedTreeEntity(EntityType<? extends AwakenedTreeEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 60.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0);
    }

    public void initialize(UUID casterUuid) {
        this.casterUuid = casterUuid;
        setHealth(getMaxHealth());
    }

    public UUID getCasterUuid() {
        return casterUuid;
    }

    @Override
    protected void initGoals() {
        // Stationary damageable core; all behavior is managed by OvergrowthManager.
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setVelocity(Vec3d.ZERO);
    }

    @Override
    public void travel(Vec3d movementInput) {
        setVelocity(Vec3d.ZERO);
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        // The tree is rooted in place.
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean damaged = super.damage(world, source, amount);
        if (damaged) {
            OvergrowthManager.onTreeDamaged(world, this);
        }
        return damaged;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        if (getEntityWorld() instanceof ServerWorld world) {
            OvergrowthManager.destroyTree(world, this);
        }
        super.onDeath(damageSource);
    }
}
