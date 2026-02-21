package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.TemporaryBlockManager;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalamityTornadoEntity extends ProjectileEntity {

    private static final double MOVEMENT_SPEED = 0.2;
    private static final int DESTRUCTION_RADIUS = 6;
    private static final int LIFETIME_TICKS = 200; // 10 seconds
    private static final double ENTITY_LIFT_VELOCITY = 0.5;
    private static final float MAX_BLOCK_HARDNESS_TO_DESTROY = 0.5f;

    private int age = 0;
    private Vec3d direction;
    private Set<BlockPos> destroyedBlocks = new HashSet<>();

    public CalamityTornadoEntity(EntityType<? extends CalamityTornadoEntity> type, World world) {
        super(type, world);
    }

    public CalamityTornadoEntity(World world, LivingEntity owner) {
        super(ModEntities.CALAMITY_TORNADO, world);
        setOwner(owner);
        setPosition(owner.getEntityPos().x, owner.getEntityPos().y, owner.getEntityPos().z);
        this.direction = owner.getRotationVec(1.0f).normalize().multiply(1.0, 0.0, 1.0).normalize(); // Horizontal only
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        // Required override - no custom data to track
    }

    @Override
    public void tick() {
        super.tick();

        if (!(getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        age++;

        // Expire after lifetime
        if (age >= LIFETIME_TICKS) {
            // Restore destroyed blocks before despawning
            restoreBlocks(serverWorld);
            discard();
            return;
        }

        // Move in direction
        if (direction != null) {
            Vec3d movement = direction.multiply(MOVEMENT_SPEED);
            setPosition(getX() + movement.x, getY(), getZ() + movement.z);
        }

        // Destroy soft blocks in radius
        destroySoftBlocks(serverWorld);

        // Lift entities in spiral
        liftEntities(serverWorld);

        // Spawn dense particles
        spawnTornadoParticles(serverWorld);

        // Play wind sounds periodically
        if (age % 20 == 0) {
            serverWorld.playSound(
                    null,
                    getBlockPos(),
                    SoundEvents.ENTITY_BREEZE_WIND_BURST.value(),
                    SoundCategory.PLAYERS,
                    1.5f, 0.8f);
        }
    }

    private void destroySoftBlocks(ServerWorld world) {
        BlockPos center = getBlockPos();

        for (int x = -DESTRUCTION_RADIUS; x <= DESTRUCTION_RADIUS; x++) {
            for (int y = -DESTRUCTION_RADIUS; y <= DESTRUCTION_RADIUS; y++) {
                for (int z = -DESTRUCTION_RADIUS; z <= DESTRUCTION_RADIUS; z++) {
                    BlockPos pos = center.add(x, y, z);

                    if (pos.getSquaredDistance(center) > DESTRUCTION_RADIUS * DESTRUCTION_RADIUS) {
                        continue;
                    }

                    BlockState state = world.getBlockState(pos);
                    // Check if soft block (low hardness)
                    if (state.isAir() || destroyedBlocks.contains(pos)) {
                        continue;
                    }

                    float hardness = state.getHardness(world, pos);
                    if (hardness >= 0 && hardness <= MAX_BLOCK_HARDNESS_TO_DESTROY) {
                        // Track destroyed block for restoration
                        destroyedBlocks.add(pos.toImmutable());

                        // Use TemporaryBlockManager to remove block (restore it later)
                        Set<BlockPos> positions = new HashSet<>();
                        positions.add(pos);

                        // Store original state, place air temporarily
                        TemporaryBlockManager.placeTemporaryBlocks(
                                world,
                                positions,
                                net.minecraft.block.Blocks.AIR.getDefaultState(),
                                LIFETIME_TICKS - age, // Restore when tornado expires
                                s -> !s.isAir());

                        // Spawn break particles
                        world.spawnParticles(
                                new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                6, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }
    }

    private void liftEntities(ServerWorld world) {
        Box box = new Box(
                getX() - DESTRUCTION_RADIUS, getY() - DESTRUCTION_RADIUS, getZ() - DESTRUCTION_RADIUS,
                getX() + DESTRUCTION_RADIUS, getY() + DESTRUCTION_RADIUS, getZ() + DESTRUCTION_RADIUS);

        List<Entity> entities = world.getOtherEntities(this, box, entity -> entity instanceof LivingEntity);

        for (Entity entity : entities) {
            // Grace period for owner (3 seconds)
            if (entity.equals(getOwner()) && this.age < 60) {
                continue;
            }

            // Calculate helix motion
            double dx = entity.getX() - getX();
            double dz = entity.getZ() - getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < DESTRUCTION_RADIUS) {
                // Spiral motion (perpendicular to radius)
                double spiralX = -dz / (distance + 0.1);
                double spiralZ = dx / (distance + 0.1);

                // Add upward and spiral velocity
                entity.addVelocity(
                        spiralX * 0.3,
                        ENTITY_LIFT_VELOCITY,
                        spiralZ * 0.3);
                entity.velocityModified = true;
                entity.fallDistance = 0; // Prevent fall damage while lifted
            }
        }
    }

    private void spawnTornadoParticles(ServerWorld world) {
        // Dense cloud particles forming tornado shape
        for (int i = 0; i < 15; i++) {
            double angle = (age + i) * 0.3;
            double radius = 3.0 + random.nextDouble() * 2.0;
            double height = random.nextDouble() * 10.0;

            double px = getX() + Math.cos(angle) * radius;
            double py = getY() + height;
            double pz = getZ() + Math.sin(angle) * radius;

            world.spawnParticles(
                    ParticleTypes.CLOUD,
                    px, py, pz,
                    1, 0.1, 0.1, 0.1, 0.02);
        }

        // Gust particles
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = random.nextDouble() * DESTRUCTION_RADIUS;
            double height = random.nextDouble() * 8.0;

            double px = getX() + Math.cos(angle) * radius;
            double py = getY() + height;
            double pz = getZ() + Math.sin(angle) * radius;

            world.spawnParticles(
                    ParticleTypes.GUST,
                    px, py, pz,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void restoreBlocks(ServerWorld world) {
        // TemporaryBlockManager handles restoration automatically
    }

    @Override
    public void remove(RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            restoreBlocks(serverWorld);
        }
        super.remove(reason);
    }
}
