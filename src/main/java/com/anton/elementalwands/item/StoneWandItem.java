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

public class StoneWandItem extends AbstractWandItem {

    private static final int PRIMARY_COOLDOWN_TICKS = 60;
    private static final int TECTONIC_LENGTH = 15;
    private static final float TECTONIC_DAMAGE = 6.0f;
    private static final double TECTONIC_VERTICAL_KNOCKBACK = 0.5;
    private static final int TECTONIC_BLOCK_DURATION = 40;
    private static final int TECTONIC_TERRAIN_SCAN_RANGE = 3;
    private static final double TECTONIC_HITBOX_EXPAND_XZ = 0.7;
    private static final int TECTONIC_VERTICAL_SCAN_DOWN = 5;
    private static final int TECTONIC_VERTICAL_SCAN_UP = 5;

    public StoneWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        Vec3d forward = horizontalForward(caster);
        List<BlockPos> spikes = new ArrayList<>(new LinkedHashSet<>(buildTectonicSpikePath(world, caster, forward)));
        if (spikes.isEmpty())
            return;

        TemporaryBlockManager.placeTemporaryBlocks(
                world,
                spikes,
                Blocks.STONE.getDefaultState(),
                TECTONIC_BLOCK_DURATION,
                state -> (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty());

        applyTectonicSpikeHits(world, caster, spikes);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.9f, 0.8f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                caster.getX(), caster.getBodyY(0.6), caster.getZ(), 16, 0.6, 0.3, 0.6, 0.10);
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        TitanDomeManager.startAegis(world, caster);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.CLOUD, caster.getX(), caster.getBodyY(0.5), caster.getZ(), 25, 1.0, 0.6, 1.0,
                0.02);
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
        if (ground.isAir() || !ground.isSolidBlock(world, groundPos))
            return false;

        BlockState above = world.getBlockState(groundPos.up());
        return (above.isAir() || above.isReplaceable()) && above.getFluidState().isEmpty();
    }

    private void applyTectonicSpikeHits(ServerWorld world, PlayerEntity caster, List<BlockPos> spikes) {
        Set<UUID> hitTargets = new HashSet<>();
        for (BlockPos spikePos : spikes) {
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
                applyDamage(world, caster, target, TECTONIC_DAMAGE);
                target.addVelocity(0.0, TECTONIC_VERTICAL_KNOCKBACK, 0.0);
                target.velocityModified = true;
            }
        }
    }
}
