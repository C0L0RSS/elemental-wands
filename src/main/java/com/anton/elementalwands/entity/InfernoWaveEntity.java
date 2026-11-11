package com.anton.elementalwands.entity;

import com.anton.elementalwands.party.WandAllies;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModSounds;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.BlockFireEvents;
import com.anton.elementalwands.util.TemporaryBlockManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class InfernoWaveEntity extends ProjectileEntity {

    private static final float DAMAGE = 6.0f;
    private static final int MAX_TRAVEL_DISTANCE = 15; // blocks
    private static final double PROJECTILE_SPEED = 1.5;
    private static final int FIRE_TRAIL_DURATION_TICKS = 40; // 2 seconds
    private static final int MAX_AGE_TICKS = 60; // backstop if startPos is ever lost

    private Vec3d startPos;
    private Set<Integer> hitEntities = new HashSet<>();

    public InfernoWaveEntity(EntityType<? extends InfernoWaveEntity> type, World world) {
        super(type, world);
    }

    public InfernoWaveEntity(World world, LivingEntity owner) {
        super(ModEntities.INFERNO_WAVE, world);
        setOwner(owner);
        setPosition(owner.getEyePos().x, owner.getEyePos().y, owner.getEyePos().z);
        this.startPos = getEntityPos();

        // Set velocity in owner's facing direction
        Vec3d direction = owner.getRotationVec(1.0f).normalize();
        setVelocity(direction.multiply(PROJECTILE_SPEED));
    }

    private boolean protectsAlly(Entity target) {
        return WandAllies.protectedFrom(getOwner(), target)
                || (getEntityWorld() instanceof ServerWorld world && owner != null
                    && WandAllies.protectedFrom(world, owner.getUuid(), target));
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        // Required override - no custom data to track
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            // Check if traveled too far
            if (age > MAX_AGE_TICKS || (startPos != null && getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE)) {
                // Out of range in open air: the fireball breaks apart where it is.
                BlockFireEvents.send(serverWorld, BlockFireEvents.INFERNO_BREAK, getEntityPos(), getVelocity().normalize());
                discard();
                return;
            }

            // Leave fire trail on ground
            BlockPos groundPos = getBlockPos().down();
            BlockPos firePos = getBlockPos();

            if (serverWorld.getBlockState(firePos).isAir() &&
                    serverWorld.getBlockState(groundPos).isSolidBlock(serverWorld, groundPos)) {

                Set<BlockPos> positions = new HashSet<>();
                positions.add(firePos);

                TemporaryBlockManager.placeTrackedTemporaryBlocks(
                        serverWorld,
                        positions,
                        ModSpellBlocks.INFERNO_FLAME.getDefaultState(),
                        FIRE_TRAIL_DURATION_TICKS,
                        state -> state.isAir(), owner == null ? null : owner.getUuid());
            }

            // Check for entity collisions; the fireball and its trail are drawn by the client.
            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            if (hitResult.getType() != HitResult.Type.MISS) {
                onCollision(hitResult);
                if (isRemoved()) {
                    return;
                }
            }
        }

        // Update position
        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Entity target = entityHitResult.getEntity();
        if (protectsAlly(target)) return;

        // Pierce through - only damage each entity once
        if (hitEntities.contains(target.getId())) {
            return;
        }

        hitEntities.add(target.getId());

        Entity owner = getOwner();
        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? serverWorld.getDamageSources().thrown(this, livingOwner)
                : serverWorld.getDamageSources().generic();

        boolean damaged = com.anton.elementalwands.util.SpellCombat.damage(target,serverWorld,source,DAMAGE,getOwner(),com.anton.elementalwands.data.WizardAffinity.FIRE);
        if (damaged) {
            com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(getOwner(), DAMAGE, com.anton.elementalwands.data.WizardAffinity.FIRE);
        }

        // Set target on fire
        com.anton.elementalwands.util.SpellCombat.ignite(target,getOwner(),3); // 3 seconds

        BlockFireEvents.send(serverWorld, BlockFireEvents.INFERNO_HIT,
                new Vec3d(target.getX(), target.getBodyY(0.63), target.getZ()), getVelocity().normalize());

        serverWorld.playSound(
                null,
                target.getBlockPos(),
                ModSounds.SPELL_FIRE_INFERNO_WAVE_HIT,
                SoundCategory.PLAYERS,
                0.5f, 1.2f);

        // DON'T discard - we pierce through enemies
    }

    @Override
    protected boolean canHit(Entity entity) {
        // Don't hit owner or already-hit entities
        return super.canHit(entity) && !protectsAlly(entity) && !hitEntities.contains(entity.getId());
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        // Inferno Wave stops on block collision
        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            BlockFireEvents.send(serverWorld, 96, BlockFireEvents.INFERNO_BREAK, -1, blockHitResult.getPos(),
                    getVelocity().normalize(), Vec3d.of(blockHitResult.getSide().getVector()), 0);
        }
        discard();
    }
}
