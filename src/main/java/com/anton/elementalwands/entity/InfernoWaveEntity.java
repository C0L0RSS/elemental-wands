package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.TemporaryBlockManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class InfernoWaveEntity extends ProjectileEntity {

    private static final float DAMAGE = 8.0f;
    private static final int MAX_TRAVEL_DISTANCE = 15; // blocks
    private static final double PROJECTILE_SPEED = 1.5;
    private static final int FIRE_TRAIL_DURATION_TICKS = 40; // 2 seconds
    private static final double WAKE_SPACING = 0.42;

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

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        // Required override - no custom data to track
    }

    @Override
    public void tick() {
        super.tick();

        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            // Check if traveled too far
            if (startPos != null && getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
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

                TemporaryBlockManager.placeTemporaryBlocks(
                        serverWorld,
                        positions,
                        ModSpellBlocks.INFERNO_FLAME.getDefaultState(),
                        FIRE_TRAIL_DURATION_TICKS,
                        state -> state.isAir());
            }

            // Check for entity collisions
            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            Vec3d segmentStart = getEntityPos();
            Vec3d segmentEnd = hitResult.getType() == HitResult.Type.BLOCK
                    ? hitResult.getPos()
                    : segmentStart.add(getVelocity());

            // Fill the distance between server ticks so the vanilla flame wake
            // reads as one moving front instead of disconnected particle clumps.
            // Collision still uses the original projectile path and mechanics.
            emitInterpolatedWake(serverWorld, segmentStart, segmentEnd);

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

    private void emitInterpolatedWake(ServerWorld world, Vec3d start, Vec3d end) {
        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance / WAKE_SPACING));
        for (int i = 0; i <= samples; i++) {
            Vec3d point = start.lerp(end, (double) i / samples);
            world.spawnParticles(ParticleTypes.FLAME,
                    point.x, point.y, point.z,
                    1, 0.08, 0.06, 0.08, 0.008);
            if ((i + age) % 2 == 0) {
                world.spawnParticles(ParticleTypes.SMALL_FLAME,
                        point.x, point.y, point.z,
                        1, 0.1, 0.08, 0.1, 0.018);
            }
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Entity target = entityHitResult.getEntity();

        // Pierce through - only damage each entity once
        if (hitEntities.contains(target.getId())) {
            return;
        }

        hitEntities.add(target.getId());

        Entity owner = getOwner();
        DamageSource source = (owner instanceof LivingEntity livingOwner)
                ? serverWorld.getDamageSources().thrown(this, livingOwner)
                : serverWorld.getDamageSources().generic();

        boolean damaged = target.damage(serverWorld, source, DAMAGE);
        if (damaged) {
            com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(getOwner(), DAMAGE);
        }

        // Set target on fire
        target.setOnFireFor(3); // 3 seconds

        serverWorld.spawnParticles(
                ModParticles.FIRE_INFERNO_FLAME,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                12, 0.42, 0.38, 0.42, 0.08);
        serverWorld.spawnParticles(
                ParticleTypes.LAVA,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                6, 0.35, 0.35, 0.35, 0.08);
        serverWorld.spawnParticles(
                ParticleTypes.SMOKE,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                3, 0.3, 0.3, 0.3, 0.035);

        serverWorld.playSound(
                null,
                target.getBlockPos(),
                SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                0.5f, 1.2f);

        // DON'T discard - we pierce through enemies
    }

    @Override
    protected boolean canHit(Entity entity) {
        // Don't hit owner or already-hit entities
        return super.canHit(entity) && !entity.equals(getOwner()) && !hitEntities.contains(entity.getId());
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        // Inferno Wave stops on block collision
        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            Vec3d impact = blockHitResult.getPos();
            serverWorld.spawnParticles(
                    ModParticles.FIRE_INFERNO_FLAME,
                    impact.x, impact.y, impact.z,
                    18, 0.5, 0.4, 0.5, 0.09);
            serverWorld.spawnParticles(
                    ParticleTypes.LAVA,
                    impact.x, impact.y, impact.z,
                    8, 0.45, 0.35, 0.45, 0.09);
            serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    impact.x, impact.y, impact.z,
                    4, 0.4, 0.3, 0.4, 0.04);
        }
        discard();
    }
}
