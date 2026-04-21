package com.anton.elementalwands.item;

import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TitanDomeManager;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.entity.Entity;

public final class StoneAbilityHandler {

    private static class WallData {
        public final BlockPos center;
        public final int creationTick;
        public final List<BlockPos> blockPositions;

        public WallData(BlockPos center, int tick, List<BlockPos> blockPositions) {
            this.center = center;
            this.creationTick = tick;
            this.blockPositions = List.copyOf(blockPositions);
        }
    }

    private static final Map<UUID, WallData> ACTIVE_WALLS = new HashMap<>();
    private static final int TECTONIC_LENGTH = 15;
    private static final float TECTONIC_DAMAGE = 9.0f;
    private static final double TECTONIC_VERTICAL_KNOCKBACK = 1.0;
    private static final int TECTONIC_BLOCK_DURATION = 40;
    private static final int WALL_DURATION = 70; // 3.5 seconds
    private static final int TECTONIC_TERRAIN_SCAN_RANGE = 3;
    private static final double TECTONIC_HITBOX_EXPAND_XZ = 0.7;
    private static final int TECTONIC_VERTICAL_SCAN_DOWN = 5;
    private static final int TECTONIC_VERTICAL_SCAN_UP = 5;

    private static final double SHATTER_TRIGGER_DISTANCE = 2.0;
    private static final double SHATTER_CONE_LENGTH = 8.0;
    private static final double SHATTER_CONE_HALF_ANGLE_COS = Math.cos(Math.toRadians(30.0));
    private static final float SHATTER_DAMAGE = 7.0f;
    private static final double SHATTER_HORIZONTAL_KNOCKBACK = 0.5;
    private static final double SHATTER_VERTICAL_KNOCKBACK = 0.1;

    private StoneAbilityHandler() {}

    public static int getPrimaryCooldownTicks() {
        return 40; // Reduced to 2s
    }

    public static int getSecondaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_SECONDARY_COOLDOWN_TICKS;
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity,
            net.minecraft.entity.EquipmentSlot slot) {
        if (!world.isClient() && entity instanceof PlayerEntity player &&
                (slot == net.minecraft.entity.EquipmentSlot.MAINHAND
                        || slot == net.minecraft.entity.EquipmentSlot.OFFHAND)) {

            WallData wall = ACTIVE_WALLS.get(player.getUuid());
            if (wall != null) {
                int currentTick = world.getServer().getTicks();
                if (currentTick - wall.creationTick > TECTONIC_BLOCK_DURATION) {
                    ACTIVE_WALLS.remove(player.getUuid());
                } else {
                    double dist = player.getEntityPos().distanceTo(Vec3d.ofCenter(wall.center));
                    if (dist <= 3.5) {
                        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.RESISTANCE, 20, 2, false, false, true));
                    }
                }
            }
        }
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        Vec3d forward = horizontalForward(caster);
        List<BlockPos> spikes = new ArrayList<>(new LinkedHashSet<>(buildTectonicSpikePath(world, caster, forward)));
        if (spikes.isEmpty())
            return;

        TectonicSchedulerEntity scheduler = new TectonicSchedulerEntity(world, caster, spikes);
        world.spawnEntity(scheduler);

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.PLAYERS,
                1.0f, 1.0f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        WallData existing = ACTIVE_WALLS.get(caster.getUuid());
        if (existing != null
                && world.getServer().getTicks() - existing.creationTick <= WALL_DURATION) {
            Vec3d casterPos = caster.getEntityPos();
            double nearest = Double.POSITIVE_INFINITY;
            for (BlockPos pos : existing.blockPositions) {
                double d = casterPos.distanceTo(Vec3d.ofCenter(pos));
                if (d < nearest) nearest = d;
            }
            if (nearest <= SHATTER_TRIGGER_DISTANCE) {
                executeShatter(world, caster, stack, existing);
            }
            return;
        }

        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        // Place 4-wide x 3-tall vertical wall 2 blocks in front
        Vec3d forward = horizontalForward(caster);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        BlockPos center = BlockPos.ofFloored(caster.getX() + forward.x * 2, caster.getY() + 1,
                caster.getZ() + forward.z * 2);

        List<BlockPos> wallBlocks = new ArrayList<>();
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                wallBlocks.add(center.add((int) Math.round(right.x * x), y, (int) Math.round(right.z * x)));
            }
        }

        TemporaryBlockManager.placeTemporaryBlocks(world, wallBlocks, Blocks.STONE.getDefaultState(),
                WALL_DURATION, state -> state.isAir() || state.isReplaceable());
        ACTIVE_WALLS.put(caster.getUuid(), new WallData(center, world.getServer().getTicks(), wallBlocks));

        world.playSound(null, center, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.CLOUD, center.getX(), center.getY(), center.getZ(), 25, 1.0, 1.0, 1.0, 0.02);
    }

    private static void executeShatter(ServerWorld world, PlayerEntity caster, ItemStack stack, WallData wall) {
        BlockState stoneState = Blocks.STONE.getDefaultState();
        for (BlockPos pos : wall.blockPositions) {
            if (world.getBlockState(pos).isOf(Blocks.STONE)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
        }

        ACTIVE_WALLS.remove(caster.getUuid());

        Vec3d coneOrigin = Vec3d.ofCenter(wall.center);
        Vec3d lookDir = caster.getRotationVec(1.0f).normalize();

        Box searchBox = new Box(coneOrigin, coneOrigin).expand(SHATTER_CONE_LENGTH);
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e.isAlive() && !e.isSpectator() && e != caster);

        for (LivingEntity target : candidates) {
            Vec3d toTarget = target.getEntityPos().subtract(coneOrigin);
            double distance = toTarget.length();
            if (distance > SHATTER_CONE_LENGTH || distance < 1.0e-4)
                continue;
            if (toTarget.normalize().dotProduct(lookDir) < SHATTER_CONE_HALF_ANGLE_COS)
                continue;

            boolean damaged = target.damage(world, world.getDamageSources().playerAttack(caster), SHATTER_DAMAGE);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(caster, SHATTER_DAMAGE);
            }
            target.addVelocity(lookDir.x * SHATTER_HORIZONTAL_KNOCKBACK,
                    SHATTER_VERTICAL_KNOCKBACK,
                    lookDir.z * SHATTER_HORIZONTAL_KNOCKBACK);
            target.velocityModified = true;
        }

        long now = world.getTime();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putLong("ew_last_secondary", now);
            data.putLong("ew_last_global", now);
        });

        BlockStateParticleEffect stoneParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, stoneState);

        for (BlockPos pos : wall.blockPositions) {
            world.spawnParticles(stoneParticle,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.05);
        }

        // Perpendicular basis for cone-streamer spread
        Vec3d perpA;
        if (Math.abs(lookDir.y) < 0.99) {
            perpA = lookDir.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
        } else {
            perpA = lookDir.crossProduct(new Vec3d(1.0, 0.0, 0.0)).normalize();
        }
        Vec3d perpB = lookDir.crossProduct(perpA).normalize();
        net.minecraft.util.math.random.Random rng = world.getRandom();

        for (double d = 1.0; d <= 7.0; d += 0.5) {
            Vec3d axisPoint = coneOrigin.add(lookDir.multiply(d));
            for (int i = 0; i < 3; i++) {
                double offA = (rng.nextDouble() - 0.5) * 0.6;
                double offB = (rng.nextDouble() - 0.5) * 0.6;
                Vec3d p = axisPoint.add(perpA.multiply(offA)).add(perpB.multiply(offB));
                world.spawnParticles(stoneParticle,
                        p.x, p.y, p.z,
                        1,
                        lookDir.x * 0.15, lookDir.y * 0.15, lookDir.z * 0.15,
                        0.1);
            }
        }

        for (int i = 0; i < 20; i++) {
            double t = (i / 19.0) * 7.0 + 0.5;
            Vec3d p = coneOrigin.add(lookDir.multiply(t));
            world.spawnParticles(ParticleTypes.CLOUD,
                    p.x, p.y, p.z,
                    1, 0.2, 0.2, 0.2, 0.02);
        }

        world.playSound(null, wall.center, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.2f, 1.0f);
        world.playSound(null, wall.center, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.9f, 0.6f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack))
            return;

        TitanDomeManager.startDome(world, caster);
    }

    private static Vec3d horizontalForward(PlayerEntity caster) {
        Vec3d look = caster.getRotationVec(1.0f);
        Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
        if (horizontal.lengthSquared() > 0.0001) {
            return horizontal.normalize();
        }

        float yawRad = caster.getYaw() * (float) (Math.PI / 180.0);
        return new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad)).normalize();
    }

    private static List<BlockPos> buildTectonicSpikePath(ServerWorld world, PlayerEntity caster, Vec3d forward) {
        List<BlockPos> positions = new ArrayList<>(TECTONIC_LENGTH);
        double startX = caster.getX();
        double startZ = caster.getZ();
        int groundY = caster.getBlockY() - 1;

        for (int step = 1; step <= TECTONIC_LENGTH; step++) {
            int x = MathHelper.floor(startX + forward.x * step);
            int z = MathHelper.floor(startZ + forward.z * step);
            int sampledY = findGroundYNear(world, x, z, groundY);
            if (sampledY != Integer.MIN_VALUE) {
                groundY = sampledY;
            }

            positions.add(new BlockPos(x, groundY + 1, z));
        }
        return positions;
    }

    private static int findGroundYNear(ServerWorld world, int x, int z, int referenceY) {
        int minY = world.getBottomY();
        int maxY = world.getTopYInclusive() - 1;
        int clampedReferenceY = MathHelper.clamp(referenceY, minY, maxY);

        for (int delta = 0; delta <= TECTONIC_TERRAIN_SCAN_RANGE; delta++) {
            int up = clampedReferenceY + delta;
            if (up <= maxY && isGroundCandidate(world, x, up, z))
                return up;

            if (delta == 0)
                continue;

            int down = clampedReferenceY - delta;
            if (down >= minY && isGroundCandidate(world, x, down, z))
                return down;
        }

        for (int y = clampedReferenceY; y >= minY; y--) {
            if (isGroundCandidate(world, x, y, z)) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean isGroundCandidate(ServerWorld world, int x, int y, int z) {
        BlockPos groundPos = new BlockPos(x, y, z);
        BlockState ground = world.getBlockState(groundPos);

        // Count as ground if it has a solid collision shape, or if it's already a solid
        // block
        boolean isSolidGround = !ground.getCollisionShape(world, groundPos).isEmpty()
                || ground.isSolidBlock(world, groundPos);
        if (!isSolidGround)
            return false;

        BlockState above = world.getBlockState(groundPos.up());
        return (above.getCollisionShape(world, groundPos.up()).isEmpty() || above.isReplaceable())
                && above.getFluidState().isEmpty();
    }

    private static void applyDamageAtSpike(ServerWorld world, PlayerEntity caster, BlockPos spikePos,
            Set<UUID> hitTargets) {
        Box hitBox = new Box(
                spikePos.getX() - TECTONIC_HITBOX_EXPAND_XZ,
                spikePos.getY() - TECTONIC_VERTICAL_SCAN_DOWN,
                spikePos.getZ() - TECTONIC_HITBOX_EXPAND_XZ,
                spikePos.getX() + 1.0 + TECTONIC_HITBOX_EXPAND_XZ,
                spikePos.getY() + 1.0 + TECTONIC_VERTICAL_SCAN_UP,
                spikePos.getZ() + 1.0 + TECTONIC_HITBOX_EXPAND_XZ);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, hitBox,
                e -> e.isAlive() && !e.isSpectator() && e != caster);

        for (LivingEntity target : targets) {
            if (!hitTargets.add(target.getUuid()))
                continue;

            boolean damaged = target.damage(world, world.getDamageSources().magic(), TECTONIC_DAMAGE);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(caster, TECTONIC_DAMAGE);
            }
            target.addVelocity(0.0, TECTONIC_VERTICAL_KNOCKBACK, 0.0);
            target.velocityModified = true;
        }
    }

    // Scheduler for Stone Wand Earthen Maw
    public static class TectonicSchedulerEntity extends net.minecraft.entity.decoration.ArmorStandEntity {
        private final List<BlockPos> path;
        private final PlayerEntity caster;
        private int tickCounter = 0;
        private final Set<UUID> hitTargets = new HashSet<>();

        public TectonicSchedulerEntity(ServerWorld world, PlayerEntity caster, List<BlockPos> path) {
            super(net.minecraft.entity.EntityType.ARMOR_STAND, world);
            this.caster = caster;
            this.path = path;

            this.setPosition(caster.getX(), caster.getY(), caster.getZ());
            this.setInvisible(true);
            this.setNoGravity(true);
            this.setInvulnerable(true);
        }

        @Override
        public void tick() {
            super.tick();
            if (!(getEntityWorld() instanceof ServerWorld sw))
                return;

            int maxTick = path.size() / 3 + 5;
            if (tickCounter > maxTick) {
                discard();
                return;
            }

            // Process 3 logic steps per tick for an almost instant wave
            for (int step = 0; step < 3; step++) {
                int logicalIndex = tickCounter * 3 + step;

                int windUpIndex = logicalIndex;
                if (windUpIndex < path.size()) {
                    BlockPos pos = path.get(windUpIndex);
                    sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                            pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.05);
                    sw.playSound(null, pos, SoundEvents.BLOCK_STONE_STEP, SoundCategory.PLAYERS, 0.8f, 0.6f);
                }

                int eruptIndex = logicalIndex - 2; // Very slight stagger
                if (eruptIndex >= 0 && eruptIndex < path.size()) {
                    BlockPos pos = path.get(eruptIndex);

                    TemporaryBlockManager.placeTemporaryBlocks(sw, List.of(pos), Blocks.STONE.getDefaultState(),
                            TECTONIC_BLOCK_DURATION,
                            state -> (state.getCollisionShape(sw, pos).isEmpty() || state.isReplaceable())
                                    && state.getFluidState().isEmpty());

                    applyDamageAtSpike(sw, caster, pos, hitTargets);

                    sw.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                    sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.15);
                }
            }

            tickCounter++;
        }
    }
}
