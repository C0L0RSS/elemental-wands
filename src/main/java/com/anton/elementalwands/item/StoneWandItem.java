package com.anton.elementalwands.item;

import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TitanDomeManager;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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

public class StoneWandItem extends AbstractWandItem {

    private static final int PRIMARY_COOLDOWN_TICKS = 60;

    private static class WallData {
        public final BlockPos center;
        public final int creationTick;

        public WallData(BlockPos center, int tick) {
            this.center = center;
            this.creationTick = tick;
        }
    }

    private static final Map<UUID, WallData> ACTIVE_WALLS = new HashMap<>();
    private static final int TECTONIC_LENGTH = 15;
    private static final float TECTONIC_DAMAGE = 6.0f;
    private static final double TECTONIC_VERTICAL_KNOCKBACK = 0.5;
    private static final int TECTONIC_BLOCK_DURATION = 40;
    private static final int WALL_DURATION = 70; // 3.5 seconds
    private static final int TECTONIC_TERRAIN_SCAN_RANGE = 3;
    private static final double TECTONIC_HITBOX_EXPAND_XZ = 0.7;
    private static final int TECTONIC_VERTICAL_SCAN_DOWN = 5;
    private static final int TECTONIC_VERTICAL_SCAN_UP = 5;

    public StoneWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getPrimaryCooldownTicks() {
        return 40; // Reduced to 2s
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity,
            net.minecraft.entity.EquipmentSlot slot) {
        super.inventoryTick(stack, world, entity, slot);
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

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks()))
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

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        // Place 4x4 vertical wall 2 blocks in front
        Vec3d forward = horizontalForward(caster);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        BlockPos center = BlockPos.ofFloored(caster.getX() + forward.x * 2, caster.getY() + 1,
                caster.getZ() + forward.z * 2);

        List<BlockPos> wallBlocks = new ArrayList<>();
        // Make it 4 blocks wide (-1 to 2) and 4 blocks high (-1 to 2)
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                wallBlocks.add(center.add((int) Math.round(right.x * x), y, (int) Math.round(right.z * x)));
            }
        }

        TemporaryBlockManager.placeTemporaryBlocks(world, wallBlocks, Blocks.STONE.getDefaultState(),
                WALL_DURATION, state -> state.isAir() || state.isReplaceable());
        ACTIVE_WALLS.put(caster.getUuid(), new WallData(center, world.getServer().getTicks()));

        world.playSound(null, center, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.CLOUD, center.getX(), center.getY(), center.getZ(), 25, 1.0, 1.0, 1.0, 0.02);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, getUltimateCooldownTicks()))
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

            target.damage(world, world.getDamageSources().magic(), TECTONIC_DAMAGE);
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
