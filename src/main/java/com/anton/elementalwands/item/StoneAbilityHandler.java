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
        public final Vec3d forward;

        public WallData(BlockPos center, int tick, List<BlockPos> blockPositions, Vec3d forward) {
            this.center = center;
            this.creationTick = tick;
            this.blockPositions = List.copyOf(blockPositions);
            this.forward = forward;
        }
    }

    private static final Map<UUID, WallData> ACTIVE_WALLS = new HashMap<>();
    private static final int TECTONIC_LENGTH = 15;
    private static final float TECTONIC_DAMAGE = 9.0f;
    private static final double TECTONIC_VERTICAL_KNOCKBACK = 1.0;
    private static final int TECTONIC_BLOCK_DURATION = 40;
    private static final int WALL_DURATION = 70; // 3.5 seconds
    private static final int WALL_GROUND_SEARCH_MAX = 16;
    private static final int TECTONIC_TERRAIN_SCAN_RANGE = 3;
    private static final double TECTONIC_HITBOX_EXPAND_XZ = 0.7;
    private static final int TECTONIC_VERTICAL_SCAN_DOWN = 5;
    private static final int TECTONIC_VERTICAL_SCAN_UP = 5;

    private static final double SHATTER_TRIGGER_DISTANCE = 2.0;
    private static final double SHATTER_BLAST_DEPTH = 5.0;
    private static final double SHATTER_LATERAL_PADDING = 1.5;
    private static final double SHATTER_VERTICAL_PADDING = 1.0;
    private static final float SHATTER_DAMAGE = 7.0f;
    private static final double SHATTER_HORIZONTAL_KNOCKBACK = 1.2;
    private static final double SHATTER_VERTICAL_KNOCKBACK = 0.35;

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
                double dx = Math.max(0.0, Math.max(pos.getX() - casterPos.x, casterPos.x - (pos.getX() + 1)));
                double dy = Math.max(0.0, Math.max(pos.getY() - casterPos.y, casterPos.y - (pos.getY() + 1)));
                double dz = Math.max(0.0, Math.max(pos.getZ() - casterPos.z, casterPos.z - (pos.getZ() + 1)));
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < nearest) nearest = d;
            }
            if (nearest <= SHATTER_TRIGGER_DISTANCE) {
                executeShatter(world, caster, stack, existing);
            }
            return;
        }

        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        // Place 4-wide x 3-tall vertical wall 2 blocks in front, snapped to ground
        Vec3d forward = horizontalForward(caster);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        int centerX = (int) Math.floor(caster.getX() + forward.x * 2);
        int centerZ = (int) Math.floor(caster.getZ() + forward.z * 2);
        int bottomY = resolveWallBottomY(world, centerX, centerZ,
                (int) Math.floor(caster.getY()), WALL_GROUND_SEARCH_MAX);
        BlockPos center = new BlockPos(centerX, bottomY + 1, centerZ);

        List<BlockPos> wallBlocks = new ArrayList<>();
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                wallBlocks.add(center.add((int) Math.round(right.x * x), y, (int) Math.round(right.z * x)));
            }
        }

        TemporaryBlockManager.placeTemporaryBlocks(world, wallBlocks, Blocks.STONE.getDefaultState(),
                WALL_DURATION, state -> state.isAir() || state.isReplaceable());
        ACTIVE_WALLS.put(caster.getUuid(), new WallData(center, world.getServer().getTicks(), wallBlocks, forward));

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

        Vec3d forward = wall.forward;
        Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);

        // True centroid of the wall (block centers averaged), and half-extents along right/up axes
        double cx = 0.0, cy = 0.0, cz = 0.0;
        for (BlockPos pos : wall.blockPositions) {
            cx += pos.getX() + 0.5;
            cy += pos.getY() + 0.5;
            cz += pos.getZ() + 0.5;
        }
        int n = wall.blockPositions.size();
        Vec3d wallCenter = new Vec3d(cx / n, cy / n, cz / n);

        double halfRight = 0.0, halfUp = 0.0;
        for (BlockPos pos : wall.blockPositions) {
            Vec3d rel = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).subtract(wallCenter);
            halfRight = Math.max(halfRight, Math.abs(rel.dotProduct(right)) + 0.5);
            halfUp = Math.max(halfUp, Math.abs(rel.y) + 0.5);
        }
        double blastHalfRight = halfRight + SHATTER_LATERAL_PADDING;
        double blastHalfUp = halfUp + SHATTER_VERTICAL_PADDING;

        Box searchBox = new Box(wallCenter, wallCenter)
                .expand(SHATTER_BLAST_DEPTH + blastHalfRight + 1.0,
                        blastHalfUp + 1.0,
                        SHATTER_BLAST_DEPTH + blastHalfRight + 1.0);
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e.isAlive() && !e.isSpectator() && e != caster);

        for (LivingEntity target : candidates) {
            Vec3d targetCenter = target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0);
            Vec3d delta = targetCenter.subtract(wallCenter);
            double fwd = delta.dotProduct(forward);
            double side = delta.dotProduct(right);
            double vert = delta.y;

            if (fwd < 0.0 || fwd > SHATTER_BLAST_DEPTH) continue;
            if (Math.abs(side) > blastHalfRight) continue;
            if (Math.abs(vert) > blastHalfUp) continue;

            boolean damaged = target.damage(world, world.getDamageSources().playerAttack(caster), SHATTER_DAMAGE);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(caster, SHATTER_DAMAGE);
            }
            target.addVelocity(forward.x * SHATTER_HORIZONTAL_KNOCKBACK,
                    SHATTER_VERTICAL_KNOCKBACK,
                    forward.z * SHATTER_HORIZONTAL_KNOCKBACK);
            target.velocityModified = true;
        }

        long now = world.getTime();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putLong("ew_last_secondary", now);
            data.putLong("ew_last_global", now);
        });

        // ---------- FX ----------
        BlockStateParticleEffect stoneParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, stoneState);
        net.minecraft.util.math.random.Random rng = world.getRandom();

        // 1) Directional stone block-break burst from every wall cube
        for (BlockPos pos : wall.blockPositions) {
            for (int i = 0; i < 14; i++) {
                double speed = 0.35 + rng.nextDouble() * 0.45;
                double vx = forward.x * speed + (rng.nextDouble() - 0.5) * 0.3;
                double vy = (rng.nextDouble() - 0.2) * 0.5;
                double vz = forward.z * speed + (rng.nextDouble() - 0.5) * 0.3;
                world.spawnParticles(stoneParticle,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, vx, vy, vz, 0.6);
            }
        }

        // 2) A couple of pop-explosion puffs across the wall + one bigger one at centre
        for (BlockPos pos : wall.blockPositions) {
            if (rng.nextFloat() < 0.45f) {
                world.spawnParticles(ParticleTypes.EXPLOSION,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                wallCenter.x, wallCenter.y, wallCenter.z, 1, 0.0, 0.0, 0.0, 0.0);

        // 3) Expanding shockwave rings on the outward side of the wall
        Vec3d up = new Vec3d(0.0, 1.0, 0.0);
        for (double d = 1.0; d <= SHATTER_BLAST_DEPTH; d += 1.0) {
            Vec3d ringCenter = wallCenter.add(forward.multiply(d));
            int ringCount = 24;
            double radius = 1.2 + d * 0.4;
            for (int i = 0; i < ringCount; i++) {
                double angle = (i / (double) ringCount) * Math.PI * 2.0;
                Vec3d offset = right.multiply(Math.cos(angle) * radius)
                        .add(up.multiply(Math.sin(angle) * radius));
                Vec3d p = ringCenter.add(offset);
                world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y, p.z,
                        1, forward.x * 0.08, 0.0, forward.z * 0.08, 0.02);
            }
        }

        // 4) Thick dust cloud hugging the wall plane
        for (int i = 0; i < 45; i++) {
            double lat = (rng.nextDouble() - 0.5) * (halfRight * 2.0 + 2.0);
            double vert = (rng.nextDouble() - 0.5) * (halfUp * 2.0 + 1.5);
            double depth = (rng.nextDouble() - 0.2) * 1.5;
            Vec3d p = wallCenter.add(right.multiply(lat))
                    .add(0.0, vert, 0.0)
                    .add(forward.multiply(depth));
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z,
                    1, 0.05, 0.05, 0.05, 0.015);
        }

        // 5) Sweep-attack slashes across the wall face for the "wham" read
        int sweeps = (int) Math.ceil(halfRight * 2.0);
        for (int i = 0; i < sweeps; i++) {
            double lat = -halfRight + (i + 0.5) * (halfRight * 2.0 / sweeps);
            Vec3d p = wallCenter.add(right.multiply(lat));
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // ---------- Sound ----------
        world.playSound(null, wall.center, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.4f, 0.8f);
        world.playSound(null, wall.center, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.0f, 0.6f);
        world.playSound(null, wall.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack))
            return;

        TitanDomeManager.startDome(world, caster);
    }

    private static int resolveWallBottomY(ServerWorld world, int x, int z, int startY, int maxDepth) {
        for (int y = startY; y >= startY - maxDepth; y--) {
            BlockState state = world.getBlockState(new BlockPos(x, y - 1, z));
            if (!state.isAir() && !state.isReplaceable()) {
                return y;
            }
        }
        return startY;
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
