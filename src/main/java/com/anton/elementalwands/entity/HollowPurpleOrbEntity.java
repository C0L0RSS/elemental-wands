package com.anton.elementalwands.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.particle.DustParticleEffect;

public class HollowPurpleOrbEntity extends ProjectileEntity {

    private static final double ORB_SPEED = 1.3;
    private static final double ORB_RADIUS = 5.0; // 10 blocks diameter
    private static final int MAX_LIFETIME_TICKS = 65;
    private static final int MAX_TRAVEL_DISTANCE = 90;
    private static final float MASSIVE_DAMAGE = 60.0f; // Slightly stronger

    private final Set<UUID> damagedUuids = new HashSet<>();
    private Vec3d startPos;
    private int ageTicks;

    public HollowPurpleOrbEntity(EntityType<? extends HollowPurpleOrbEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public HollowPurpleOrbEntity(ServerWorld world, LivingEntity owner, Vec3d direction) {
        this(world, owner, owner.getEyePos().add(direction.normalize().multiply(3.0)), direction);
    }

    public HollowPurpleOrbEntity(ServerWorld world, LivingEntity owner, Vec3d spawnPos, Vec3d direction) {
        super(ModEntities.HOLLOW_PURPLE_ORB, world);
        setOwner(owner);
        setNoGravity(true);

        Vec3d dir = direction.lengthSquared() > 0.0001 ? direction.normalize() : owner.getRotationVec(1.0f).normalize();

        setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        setVelocity(dir.multiply(ORB_SPEED));
        startPos = spawnPos;
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        // no tracked data
    }

    @Override
    public void tick() {
        super.tick();

        if (!(getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        ageTicks++;
        if (startPos == null) {
            startPos = getEntityPos();
        }

        if (ageTicks > MAX_LIFETIME_TICKS || getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
            discard();
            return;
        }

        erasePath(world, getEntityPos());
        damageTouchedTargets(world, getEntityPos());
        spawnOrbParticles(world);

        if (ageTicks % 6 == 0) {
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.7f,
                    0.7f);
        }

        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    private void erasePath(ServerWorld world, Vec3d center) {
        int minX = MathHelper.floor(center.x - ORB_RADIUS);
        int maxX = MathHelper.floor(center.x + ORB_RADIUS);
        int minY = MathHelper.floor(center.y - ORB_RADIUS);
        int maxY = MathHelper.floor(center.y + ORB_RADIUS);
        int minZ = MathHelper.floor(center.z - ORB_RADIUS);
        int maxZ = MathHelper.floor(center.z + ORB_RADIUS);

        double radiusSq = ORB_RADIUS * ORB_RADIUS;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (y < world.getBottomY() || y > world.getTopYInclusive()) {
                    continue;
                }
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - center.x;
                    double dy = (y + 0.5) - center.y;
                    double dz = (z + 0.5) - center.z;
                    if ((dx * dx + dy * dy + dz * dz) > radiusSq) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    float hardness = state.getHardness(world, pos);
                    if (hardness < 0.0f) {
                        continue;
                    }

                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    if (world.random.nextInt(4) == 0) {
                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), x + 0.5, y + 0.5,
                                z + 0.5, 2, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
        }
    }

    private void damageTouchedTargets(ServerWorld world, Vec3d center) {
        Entity owner = getOwner();
        double radiusSq = ORB_RADIUS * ORB_RADIUS;
        Box box = Box.of(center, ORB_RADIUS * 2.0, ORB_RADIUS * 2.0, ORB_RADIUS * 2.0);

        List<Entity> entities = world.getOtherEntities(this, box,
                entity -> entity.isAlive() && !entity.isSpectator() && !entity.equals(owner));

        DamageSource source = owner instanceof LivingEntity livingOwner
                ? world.getDamageSources().thrown(this, livingOwner)
                : world.getDamageSources().magic();

        for (Entity entity : entities) {
            if (entity.squaredDistanceTo(center) > radiusSq) {
                continue;
            }

            if (entity instanceof LivingEntity living) {
                if (!damagedUuids.add(entity.getUuid())) {
                    continue;
                }

                living.damage(world, source, MASSIVE_DAMAGE);
                living.velocityModified = true;
                living.fallDistance = 0.0f;

                world.spawnParticles(ParticleTypes.WITCH, living.getX(), living.getBodyY(0.5), living.getZ(),
                        20, 0.5, 0.5, 0.5, 0.02);
            } else {
                entity.discard();
            }
        }
    }

    private void spawnOrbParticles(ServerWorld world) {
        Vec3d center = getEntityPos();

        // Massive dense purple singularity
        DustParticleEffect purpleDust = new DustParticleEffect(0x8A2BE2, 4.0f);

        for (int i = 0; i < 250; i++) { // Massive particle count
            double theta = world.random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos((world.random.nextDouble() * 2.0) - 1.0);

            // Concentrate heavily on the outer shell of the sphere
            double radius = ORB_RADIUS * (0.85 + world.random.nextDouble() * 0.15);

            double sinPhi = Math.sin(phi);
            double px = center.x + radius * sinPhi * Math.cos(theta);
            double py = center.y + radius * Math.cos(phi);
            double pz = center.z + radius * sinPhi * Math.sin(theta);

            world.spawnParticles(purpleDust, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);

            if (i % 4 == 0) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, px, py, pz, 1, 0.1, 0.1, 0.1, 0.05);
            }
        }

        world.spawnParticles(ParticleTypes.WITCH, center.x, center.y, center.z, 40, 2.5, 2.5, 2.5, 0.02);
        world.spawnParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 20, 2.0, 2.0, 2.0, 0.08);
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult blockHitResult) {
        // This orb tunnels through terrain by design.
    }

    @Override
    protected void onEntityHit(net.minecraft.util.hit.EntityHitResult entityHitResult) {
        // Entity contact is handled via AoE touch checks each tick.
    }

    @Override
    protected boolean canHit(Entity entity) {
        return false;
    }
}
