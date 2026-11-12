package com.anton.elementalwands.item;

import java.util.Optional;

import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.registry.ModSounds;
import com.anton.elementalwands.util.HollowPurpleChargeManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class SpaceAbilityHandler {

    private static final int PRIMARY_COOLDOWN_TICKS = 20;
    private static final int SECONDARY_COOLDOWN_TICKS = 200;

    private static final double BLINK_RANGE = 10.0;

    private SpaceAbilityHandler() {}

    public static int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        SingularityBoltEntity bolt = new SingularityBoltEntity(world, caster);
        if (caster instanceof net.minecraft.server.network.ServerPlayerEntity p
                && com.anton.elementalwands.util.AstralDoubleManager.mirror(p)) bolt.setAstralPair();
        world.spawnEntity(bolt);

        // Clients draw the cast bloom at the wand tip when they first see the bolt.
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_SINGULARITY_BOLT_CAST, SoundCategory.PLAYERS,
                0.8f, 1.0f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (HollowPurpleChargeManager.isCharging(world, caster)) {
            return;
        }
        Optional<Vec3d> destination = findSafeBlinkDestination(world, caster, BLINK_RANGE);
        if (destination.isEmpty()) {
            spawnFailedBlink(world, caster);
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

        com.anton.elementalwands.util.BlockSpaceEvents.send(world, com.anton.elementalwands.util.BlockSpaceEvents.BLINK, caster.getId(), from, to, 0);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_SPACE_BLINK_RIFT_CAST, SoundCategory.PLAYERS, 1.0f,
                1.0f);
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

    private static void spawnFailedBlink(ServerWorld world, PlayerEntity caster) {
        Vec3d feetPos = caster.getEntityPos();
        com.anton.elementalwands.util.BlockSpaceEvents.send(world, com.anton.elementalwands.util.BlockSpaceEvents.BLINK_FAIL, caster.getId(), feetPos, caster.getRotationVec(1.0f), 0);
        world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(feetPos),
                ModSounds.SPELL_SPACE_BLINK_RIFT_FAIL, SoundCategory.PLAYERS, 0.55f, 1.0f);
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
        if (!com.anton.elementalwands.arena.GuardianArenaManager.canTeleport(caster,world,targetFeetPos)) return false;
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
