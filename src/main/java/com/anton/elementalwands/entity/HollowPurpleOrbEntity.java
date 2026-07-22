package com.anton.elementalwands.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;

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

public class HollowPurpleOrbEntity extends ProjectileEntity {

    private static final double ORB_SPEED = 1.3;
    private static final double ORB_RADIUS = 5.0; // 10 blocks diameter
    private static final int MAX_LIFETIME_TICKS = 65;
    private static final int MAX_TRAVEL_DISTANCE = 90;
    private static final float MASSIVE_DAMAGE = 60.0f; // Slightly stronger
    private static final int COLLAPSE_TICKS = 12;

    private final Set<UUID> damagedUuids = new HashSet<>();
    private Vec3d startPos;
    private int ageTicks;
    private boolean collapsing;
    private int collapseAge;

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

        if (collapsing) {
            tickFinalCollapse(world);
            return;
        }

        if (ageTicks > MAX_LIFETIME_TICKS || getEntityPos().distanceTo(startPos) > MAX_TRAVEL_DISTANCE) {
            beginFinalCollapse(world);
            return;
        }

        erasePath(world, getEntityPos());
        damageTouchedTargets(world, getEntityPos());
        spawnOrbParticles(world);

        if (ageTicks % 10 == 0) {
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.7f,
                    0.48f);
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
                    if (world.random.nextInt(6) == 0) {
                        Vec3d fragmentPos = new Vec3d(x + 0.5, y + 0.5, z + 0.5);
                        Vec3d inward = center.subtract(fragmentPos);
                        if (inward.lengthSquared() > 0.0001) {
                            inward = inward.normalize().multiply(0.22 + world.random.nextDouble() * 0.12);
                        }
                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                                true, true,
                                fragmentPos.x, fragmentPos.y, fragmentPos.z,
                                0, inward.x, inward.y, inward.z, 1.0);
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

                boolean damaged = living.damage(world, source, MASSIVE_DAMAGE);
                if (damaged) {
                    com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(getOwner(), MASSIVE_DAMAGE);
                }
                living.velocityModified = true;
                living.fallDistance = 0.0f;

                Vec3d targetCenter = living.getEntityPos().add(0.0, living.getHeight() * 0.5, 0.0);
                world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                        targetCenter.x, targetCenter.y, targetCenter.z, 2, 0.08, 0.08, 0.08, 0.0);
                world.spawnParticles(ModParticles.SPACE_CONSUMPTION,
                        targetCenter.x, targetCenter.y, targetCenter.z, 18, 0.5, 0.5, 0.5, 0.08);
            } else {
                entity.discard();
            }
        }
    }

    private void spawnOrbParticles(ServerWorld world) {
        Vec3d center = getEntityPos();
        Vec3d velocity = getVelocity();

        if (ageTicks % 4 == 1) {
            spawnDirected(world, ModParticles.SPACE_ECLIPSE, center, velocity);
        }
        if (ageTicks % 6 == 1) {
            spawnDirected(world, ModParticles.SPACE_GRAVITY_LENS, center, velocity);
        }

        // Sparse shell fragments preserve the ten-block volume without turning the
        // horizon into an opaque cloud. Every fragment is visibly pulled inward.
        for (int i = 0; i < 12; i++) {
            double theta = world.random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(world.random.nextDouble() * 2.0 - 1.0);
            double radius = ORB_RADIUS * (0.92 + world.random.nextDouble() * 0.52);
            Vec3d point = center.add(
                    radius * Math.sin(phi) * Math.cos(theta),
                    radius * Math.cos(phi),
                    radius * Math.sin(phi) * Math.sin(theta));
            Vec3d inward = center.subtract(point).normalize().multiply(0.11 + world.random.nextDouble() * 0.09);
            spawnDirected(world, i % 3 == 0 ? ModParticles.SPACE_CONSUMPTION : ModParticles.SPACE_MOTE,
                    point, inward);
        }

        Vec3d direction = getVelocity().lengthSquared() > 0.0001
                ? getVelocity().normalize()
                : new Vec3d(0.0, 0.0, 1.0);
        for (int i = 0; i < 10; i++) {
            double distance = 2.0 + i * 0.72;
            double phase = ageTicks * 0.31 + i * 1.71;
            Vec3d trail = center.subtract(direction.multiply(distance))
                    .add(Math.cos(phase) * (1.2 + i * 0.08),
                            Math.sin(phase * 1.3) * (1.2 + i * 0.06),
                            Math.sin(phase) * (1.2 + i * 0.08));
            Vec3d inward = center.subtract(trail).normalize().multiply(0.19 + i * 0.006);
            spawnDirected(world, ModParticles.SPACE_CONSUMPTION, trail, inward);
        }
    }

    private void beginFinalCollapse(ServerWorld world) {
        if (collapsing) {
            return;
        }
        collapsing = true;
        collapseAge = 0;
        setVelocity(Vec3d.ZERO);
        velocityModified = true;

        Vec3d center = getEntityPos();
        world.spawnParticles(ModParticles.SPACE_FINAL_COLLAPSE,
                true, true,
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.2f, 0.42f);
    }

    private void tickFinalCollapse(ServerWorld world) {
        collapseAge++;
        Vec3d center = getEntityPos();

        if (collapseAge <= 4) {
            world.spawnParticles(ModParticles.SPACE_CONSUMPTION,
                    true, true,
                    center.x, center.y, center.z, 18, 3.8, 3.8, 3.8, 0.16);
        }
        if (collapseAge == 5) {
            world.spawnParticles(ModParticles.SPACE_PINPOINT,
                    true, true,
                    center.x, center.y, center.z, 6, 0.12, 0.12, 0.12, 0.0);
        }
        if (collapseAge == 7) {
            world.spawnParticles(ModParticles.SPACE_GRAVITY_LENS,
                    true, true,
                    center.x, center.y, center.z, 3, 0.08, 0.08, 0.08, 0.0);
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                    SoundCategory.PLAYERS, 1.45f, 1.7f);
        }
        if (collapseAge >= COLLAPSE_TICKS) {
            discard();
        }
    }

    private static void spawnDirected(ServerWorld world, net.minecraft.particle.SimpleParticleType particle,
            Vec3d position, Vec3d velocity) {
        world.spawnParticles(particle,
                position.x, position.y, position.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0);
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

    @Override
    public boolean shouldSave() {
        // This is a short-lived spell effect with transient collapse and hit state.
        // Saving it mid-flight could reload a zero-velocity orb with reset damage data.
        return false;
    }
}
