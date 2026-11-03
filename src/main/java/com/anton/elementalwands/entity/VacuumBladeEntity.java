package com.anton.elementalwands.entity;

import com.anton.elementalwands.party.WandAllies;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.BlockWindEvents;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class VacuumBladeEntity extends ProjectileEntity {

    private static final TrackedData<Boolean> MIRRORED = DataTracker.registerData(
            VacuumBladeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final double PROJECTILE_SPEED = 2.5;
    private static final double MAX_TRAVEL_DISTANCE = WindFanRules.RANGE;
    private java.util.Set<java.util.UUID> castHits = new java.util.HashSet<>();

    private Vec3d startPos;

    public VacuumBladeEntity(EntityType<? extends VacuumBladeEntity> type, World world) {
        super(type, world);
    }

    public VacuumBladeEntity(World world, LivingEntity owner, Vec3d direction, boolean mirrored,
            java.util.Set<java.util.UUID> castHits) {
        super(ModEntities.VACUUM_BLADE, world);
        setOwner(owner); this.castHits=castHits;
        dataTracker.set(MIRRORED, mirrored);
        startPos = owner.getEyePos(); setPosition(startPos);
        setVelocity(direction.normalize().multiply(PROJECTILE_SPEED));
    }

    private boolean protectsAlly(Entity target) {
        return WandAllies.protectedFrom(getOwner(), target)
                || (getEntityWorld() instanceof ServerWorld world && owner != null
                    && WandAllies.protectedFrom(world, owner.getUuid(), target));
    }

    @Override public boolean shouldSave() { return false; }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        builder.add(MIRRORED, false);
    }

    public boolean isMirrored() {
        return dataTracker.get(MIRRORED);
    }

    @Override
    public void tick() {
        super.tick();
        if (startPos == null) startPos = getEntityPos();
        double remaining = MAX_TRAVEL_DISTANCE-startPos.distanceTo(getEntityPos());
        if (remaining <= 1e-6 || age>20) { discard(); return; }
        Vec3d step=getVelocity();
        if (step.length()>remaining) step=step.normalize().multiply(remaining);
        setVelocity(step); // Collision and movement share the clipped final segment.
        if (getEntityWorld() instanceof ServerWorld) {
            HitResult hit=ProjectileUtil.getCollision(this,this::canHit);
            if (hit.getType()!=HitResult.Type.MISS) {
                setPosition(hit.getPos());
                onCollision(hit); return;
            }
        }
        setPosition(getEntityPos().add(step));
        if (remaining <= step.length()+1e-6) discard();
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Entity target = entityHitResult.getEntity();
        if (protectsAlly(target)) return;
        Entity owner = getOwner();

        // Deal damage with linear falloff: MAX_DAMAGE at point-blank, MIN_DAMAGE at MAX_TRAVEL_DISTANCE
        double traveled = startPos != null ? getEntityPos().distanceTo(startPos) : 0.0;
        if (!castHits.add(target.getUuid())) { discard(); return; }
        float damage = WindFanRules.damage(traveled);

        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? serverWorld.getDamageSources().thrown(this, livingOwner)
                : serverWorld.getDamageSources().generic();

        boolean damaged = com.anton.elementalwands.util.SpellCombat.damage(target,serverWorld,source,damage,owner,com.anton.elementalwands.data.WizardAffinity.WIND);
        if (damaged) {
            com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(owner, damage, com.anton.elementalwands.data.WizardAffinity.WIND);
        }

        // Respect resistance and rejected damage. A resistant boss must never bank upward impulses.
        if (damaged && target instanceof LivingEntity living) {
            Vec3d direction=getVelocity().normalize();
            living.takeKnockback(.5,-direction.x,-direction.z);
        }

        spawnImpact(serverWorld, entityHitResult.getPos());

        serverWorld.playSound(
                null,
                target.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS,
                0.5f, 1.5f);

        // Despawn after hitting entity (doesn't pierce)
        discard();
    }

    @Override
    protected boolean canHit(Entity entity) {
        // Don't hit owner
        return super.canHit(entity) && !protectsAlly(entity);
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult blockHitResult) {
        // Despawn on block collision
        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            spawnImpact(serverWorld, blockHitResult.getPos());
        }
        discard();
    }

    /** Clients draw the blade, its echoes and this burst as block wind (client/blockwind/ShearFx). */
    private void spawnImpact(ServerWorld world, Vec3d impactPos) {
        BlockWindEvents.send(world, BlockWindEvents.HIT, getId(), impactPos, getVelocity(), 0);
    }
}
