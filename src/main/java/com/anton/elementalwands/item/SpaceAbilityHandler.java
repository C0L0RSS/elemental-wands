package com.anton.elementalwands.item;

import java.util.Optional;

import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.BlinkRiftManager;
import com.anton.elementalwands.util.HollowPurpleChargeManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class SpaceAbilityHandler {

    private static final int PRIMARY_COOLDOWN_TICKS = 20;
    private static final int SECONDARY_COOLDOWN_TICKS = 120;
    private static final int ULTIMATE_COOLDOWN_TICKS = 800;

    private static final double BLINK_RANGE = 10.0;
    private static final int RIFT_DURATION_TICKS = SECONDARY_COOLDOWN_TICKS;

    private SpaceAbilityHandler() {}

    public static int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
    }

    public static int getUltimateCooldownTicks() {
        return ULTIMATE_COOLDOWN_TICKS;
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        SingularityBoltEntity bolt = new SingularityBoltEntity(world, caster);
        world.spawnEntity(bolt);

        Vec3d castCenter = caster.getEyePos().add(caster.getRotationVec(1.0f).normalize().multiply(0.7));
        world.spawnParticles(ModParticles.SPACE_SINGULARITY,
                castCenter.x, castCenter.y, castCenter.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_BROKEN_ORBIT,
                castCenter.x, castCenter.y, castCenter.z, 2, 0.08, 0.08, 0.08, 0.0);
        world.spawnParticles(ModParticles.SPACE_MOTE,
                castCenter.x, castCenter.y, castCenter.z, 8, 0.32, 0.32, 0.32, 0.03);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS,
                0.8f, 1.4f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.PLAYERS, 0.6f,
                1.6f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        BlinkRiftManager.SwapResult swapResult = BlinkRiftManager.trySwapWithRift(world, caster);
        if (swapResult == BlinkRiftManager.SwapResult.SWAPPED) {
            return;
        }
        if (swapResult == BlinkRiftManager.SwapResult.BLOCKED) {
            caster.sendMessage(Text.literal("Rift swap blocked."), true);
            return;
        }

        Optional<Vec3d> destination = findSafeBlinkDestination(world, caster, BLINK_RANGE);
        if (destination.isEmpty()) {
            spawnFailedBlink(world, caster.getEntityPos());
            caster.sendMessage(Text.literal("No safe blink destination."), true);
            return;
        }

        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks())) {
            return;
        }

        Vec3d from = caster.getEntityPos();
        Vec3d to = destination.get();

        caster.requestTeleport(to.x, to.y, to.z);
        caster.fallDistance = 0.0f;
        caster.velocityModified = true;

        BlinkRiftManager.createRift(world, caster, from, RIFT_DURATION_TICKS);

        spawnBlinkFold(world, from, false);
        spawnBlinkFold(world, to, true);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f,
                1.25f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) {
            return;
        }

        HollowPurpleChargeManager.startCharge(world, caster);
    }

    private static void spawnBlinkFold(ServerWorld world, Vec3d feetPos, boolean unfolding) {
        Vec3d center = feetPos.add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_RIFT,
                center.x, center.y, center.z, 2, 0.04, 0.14, 0.04, 0.0);
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);

        for (int i = 0; i < 14; i++) {
            double angle = (Math.PI * 2.0 * i / 14.0) + (unfolding ? 0.28 : 0.0);
            double radius = 0.55 + (i % 3) * 0.16;
            Vec3d point = center.add(Math.cos(angle) * radius,
                    ((i % 5) - 2) * 0.22,
                    Math.sin(angle) * radius);
            Vec3d velocity = unfolding
                    ? point.subtract(center).normalize().multiply(0.09)
                    : center.subtract(point).normalize().multiply(0.11);
            world.spawnParticles(ModParticles.SPACE_CONSUMPTION,
                    point.x, point.y, point.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
        }
    }

    private static void spawnFailedBlink(ServerWorld world, Vec3d feetPos) {
        Vec3d center = feetPos.add(0.0, 1.0, 0.0);
        world.spawnParticles(ModParticles.SPACE_RIFT,
                center.x, center.y, center.z, 1, 0.015, 0.06, 0.015, 0.0);
        world.spawnParticles(ModParticles.SPACE_IMPLOSION_RING,
                center.x, center.y, center.z, 2, 0.035, 0.035, 0.035, 0.0);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(feetPos),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.55f, 0.42f);
    }

    private static Optional<Vec3d> findSafeBlinkDestination(ServerWorld world, PlayerEntity caster, double range) {
        Vec3d eyePos = caster.getEyePos();
        Vec3d feetPos = caster.getEntityPos();
        Vec3d direction = caster.getRotationVec(1.0f).normalize();

        Vec3d end = eyePos.add(direction.multiply(range));
        BlockHitResult blockHit = world.raycast(new RaycastContext(eyePos, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, caster));

        double maxDistance = range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistance = Math.max(1.0, eyePos.distanceTo(blockHit.getPos()) - 0.6);
        }

        for (double distance = maxDistance; distance >= 1.0; distance -= 0.5) {
            Vec3d candidate = feetPos.add(direction.multiply(distance));
            if (isSafeTeleportLocation(world, caster, candidate)) {
                return Optional.of(candidate);
            }

            Vec3d raisedCandidate = candidate.add(0.0, 1.0, 0.0);
            if (isSafeTeleportLocation(world, caster, raisedCandidate)) {
                return Optional.of(raisedCandidate);
            }
        }

        return Optional.empty();
    }

    private static boolean isSafeTeleportLocation(ServerWorld world, PlayerEntity caster, Vec3d targetFeetPos) {
        double minY = world.getBottomY() + 1;
        double maxY = world.getTopYInclusive() - 2;
        if (targetFeetPos.y < minY || targetFeetPos.y > maxY) {
            return false;
        }

        Box targetBox = caster.getBoundingBox().offset(targetFeetPos.subtract(caster.getEntityPos()));
        if (!world.isSpaceEmpty(caster, targetBox)) {
            return false;
        }

        // Allow blinking into air, but avoid teleporting to impossible NaN/overflow values.
        return Double.isFinite(targetFeetPos.x)
                && Double.isFinite(targetFeetPos.y)
                && Double.isFinite(targetFeetPos.z);
    }
}
