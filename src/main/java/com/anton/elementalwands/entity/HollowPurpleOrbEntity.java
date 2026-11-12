package com.anton.elementalwands.entity;

import com.anton.elementalwands.crypt.HollowCryptRealm;
import com.anton.elementalwands.party.WandAllies;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModSounds;

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

    public HollowPurpleOrbEntity(ServerWorld world, LivingEntity owner, Vec3d spawnPos, Vec3d direction) {
        super(ModEntities.HOLLOW_PURPLE_ORB, world);
        setOwner(owner);
        setNoGravity(true);

        Vec3d dir = direction.lengthSquared() > 0.0001 ? direction.normalize() : owner.getRotationVec(1.0f).normalize();

        setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
        setVelocity(dir.multiply(ORB_SPEED));
        startPos = spawnPos;
    }

    private boolean protectsAlly(Entity target) {
        return WandAllies.protectedFrom(getOwner(), target)
                || (getEntityWorld() instanceof ServerWorld world && owner != null
                    && WandAllies.protectedFrom(world, owner.getUuid(), target));
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

        if (ageTicks % 10 == 0) {
            world.playSound(null, getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_LOOP, SoundCategory.PLAYERS, 0.7f,
                    1.0f);
        }

        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    private void erasePath(ServerWorld world, Vec3d center) {
        if (HollowCryptRealm.keepsTerrain(world) || com.anton.elementalwands.arena.ShatteredNave.keepsTerrain(world)
                || com.anton.elementalwands.summit.StormSummit.keepsTerrain(world) || com.anton.elementalwands.hall.PyreHall.keepsTerrain(world)) {
            return;
        }
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
                entity -> entity.isAlive() && !entity.isSpectator() && !protectsAlly(entity));

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

                boolean damaged = com.anton.elementalwands.util.SpellCombat.damage(living, world, source, MASSIVE_DAMAGE, getOwner(), com.anton.elementalwands.data.WizardAffinity.SPACE);
                if (damaged) {
                    com.anton.elementalwands.item.AbstractWandItem.onWandDamageDealt(getOwner(), MASSIVE_DAMAGE, com.anton.elementalwands.data.WizardAffinity.SPACE);
                }
                living.velocityModified = true;
                living.fallDistance = 0.0f;

                Vec3d targetCenter = living.getEntityPos().add(0.0, living.getHeight() * 0.5, 0.0);
                com.anton.elementalwands.util.BlockSpaceEvents.send(world, com.anton.elementalwands.util.BlockSpaceEvents.POOF, living.getId(), targetCenter);
            } else {
                entity.discard();
            }
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

        com.anton.elementalwands.util.BlockSpaceEvents.send(world, com.anton.elementalwands.util.BlockSpaceEvents.COLLAPSE, getId(), getEntityPos());
        world.playSound(null, getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_COLLAPSE_IMPLODE, SoundCategory.PLAYERS, 1.2f, 1.0f);
    }

    private void tickFinalCollapse(ServerWorld world) {
        collapseAge++;
        // Clients draw the collapse from the COLLAPSE message; only its sound is timed here.
        if (collapseAge == 7) {
            world.playSound(null, getBlockPos(), ModSounds.SPELL_SPACE_HOLLOW_PURPLE_COLLAPSE_BOOM,
                    SoundCategory.PLAYERS, 1.45f, 1.0f);
        }
        if (collapseAge >= COLLAPSE_TICKS) {
            discard();
        }
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
