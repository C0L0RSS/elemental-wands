package com.anton.elementalwands.item;

import java.util.Optional;

import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.util.BlinkRiftManager;
import com.anton.elementalwands.util.HollowPurpleChargeManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class SpaceWandItem extends AbstractWandItem {

    private static final int PRIMARY_COOLDOWN_TICKS = 20;
    private static final int SECONDARY_COOLDOWN_TICKS = 120;
    private static final int ULTIMATE_COOLDOWN_TICKS = 800;

    private static final double BLINK_RANGE = 10.0;
    private static final int RIFT_DURATION_TICKS = SECONDARY_COOLDOWN_TICKS;

    public SpaceWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    @Override
    public int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
    }

    @Override
    public int getUltimateCooldownTicks() {
        return ULTIMATE_COOLDOWN_TICKS;
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        SingularityBoltEntity bolt = new SingularityBoltEntity(world, caster);
        world.spawnEntity(bolt);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS,
                0.8f, 1.4f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.PLAYERS, 0.6f,
                1.6f);
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
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
            caster.sendMessage(Text.literal("No safe blink destination."), true);
            return;
        }

        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, getSecondaryCooldownTicks())) {
            return;
        }

        Vec3d from = caster.getEntityPos();
        Vec3d to = destination.get();

        caster.requestTeleport(to.x, to.y, to.z);
        caster.fallDistance = 0.0f;
        caster.velocityModified = true;

        BlinkRiftManager.createRift(world, caster, from, RIFT_DURATION_TICKS);

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 35, 0.45, 0.8, 0.45, 0.15);
        world.spawnParticles(ParticleTypes.PORTAL, to.x, to.y + 1.0, to.z, 45, 0.45, 0.8, 0.45, 0.22);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f,
                1.25f);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        if (!trySpendUltimateCharge(world, caster, stack)) {
            return;
        }

        HollowPurpleChargeManager.startCharge(world, caster);
    }

    private Optional<Vec3d> findSafeBlinkDestination(ServerWorld world, PlayerEntity caster, double range) {
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

    private boolean isSafeTeleportLocation(ServerWorld world, PlayerEntity caster, Vec3d targetFeetPos) {
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
