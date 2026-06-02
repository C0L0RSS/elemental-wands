package com.anton.elementalwands.item;

import java.util.List;
import java.util.UUID;

import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.util.OvergrowthManager;
import com.anton.elementalwands.util.SeedlingManager;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TendrilBloomManager;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class NatureAbilityHandler {

    private static final int PRIMARY_COOLDOWN_TICKS = 25;
    private static final int SECONDARY_COOLDOWN_TICKS = 300;
    private static final double TENDRIL_TARGET_RANGE = 15.0;

    // Verdant Step: lily pads bloom across water as the wizard walks, letting them cross ponds.
    // The wider the area, the more headroom a sprinting (or boost-jumping) player has before they
    // can outrun the growth.
    private static final int VERDANT_STEP_RADIUS = 4;
    // Velocity multiplier used to bias the pad area in the direction the player is moving. At sprint
    // speed (~0.28 b/t) this leads ~1 block; with bigger boosts (riptide, elytra) it scales up to
    // keep pads ahead of the player.
    private static final double VERDANT_STEP_LEAD_TICKS = 4.0;
    private static final int VERDANT_STEP_MAX_LEAD = 4;
    private static final int VERDANT_STEP_PAD_LIFESPAN = 100;

    private NatureAbilityHandler() {
    }

    public static int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        if (!(entity instanceof PlayerEntity player) || player.isSpectator()) return;

        // Bias the pad area in the player's horizontal travel direction so lily pads form ahead of
        // a sprinting player and they don't briefly drop into water at the leading edge.
        Vec3d vel = player.getVelocity();
        int leadX = clampLead((int) Math.round(vel.x * VERDANT_STEP_LEAD_TICKS));
        int leadZ = clampLead((int) Math.round(vel.z * VERDANT_STEP_LEAD_TICKS));

        BlockPos base = player.getBlockPos().add(leadX, 0, leadZ);
        for (int dx = -VERDANT_STEP_RADIUS; dx <= VERDANT_STEP_RADIUS; dx++) {
            for (int dz = -VERDANT_STEP_RADIUS; dz <= VERDANT_STEP_RADIUS; dz++) {
                BlockPos padPos = base.add(dx, 0, dz);
                // A lily pad floats on the air block whose neighbour below is water.
                if (!world.getBlockState(padPos.down()).getFluidState().isOf(Fluids.WATER)) continue;
                BlockState padState = world.getBlockState(padPos);
                if (!padState.isAir() && !padState.isReplaceable()) continue;
                TemporaryBlockManager.placeTemporaryBlocks(world,
                        List.of(padPos),
                        Blocks.LILY_PAD.getDefaultState(),
                        VERDANT_STEP_PAD_LIFESPAN,
                        s -> s.isAir() || s.isReplaceable());
            }
        }
    }

    private static int clampLead(int v) {
        if (v > VERDANT_STEP_MAX_LEAD) return VERDANT_STEP_MAX_LEAD;
        if (v < -VERDANT_STEP_MAX_LEAD) return -VERDANT_STEP_MAX_LEAD;
        return v;
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        SeedProjectileEntity seed = new SeedProjectileEntity(world, caster);
        world.spawnEntity(seed);

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_EGG_THROW,
                SoundCategory.PLAYERS, 0.7f, 0.9f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        UUID casterUuid = caster.getUuid();
        List<SeedlingManager.SeedlingSnapshot> seedlings = SeedlingManager.getActiveSeedlingsForCaster(world, casterUuid);
        if (seedlings.isEmpty()) {
            caster.sendMessage(Text.literal("No active seedlings."), true);
            return;
        }

        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks())) {
            return;
        }

        int now = world.getServer().getTicks();

        for (SeedlingManager.SeedlingSnapshot s : seedlings) {
            LivingEntity target = findNearestTarget(world, caster, s.anchorPos());
            if (target != null) {
                Vec3d anchorVec = Vec3d.ofCenter(s.anchorPos());
                TendrilBloomManager.startTendril(world, caster, s.seedlingId(), anchorVec, target);
            }
        }

        SeedlingManager.markSeedlingsForConsumption(world, casterUuid, now);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GRASS_BREAK,
                SoundCategory.PLAYERS, 0.6f, 1.0f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) {
            return;
        }
        OvergrowthManager.startOvergrowth(world, caster);
    }

    private static LivingEntity findNearestTarget(ServerWorld world, PlayerEntity caster, BlockPos anchor) {
        Vec3d anchorCenter = Vec3d.ofCenter(anchor);
        double rangeSq = TENDRIL_TARGET_RANGE * TENDRIL_TARGET_RANGE;

        Box box = new Box(
                anchorCenter.x - TENDRIL_TARGET_RANGE, anchorCenter.y - TENDRIL_TARGET_RANGE, anchorCenter.z - TENDRIL_TARGET_RANGE,
                anchorCenter.x + TENDRIL_TARGET_RANGE, anchorCenter.y + TENDRIL_TARGET_RANGE, anchorCenter.z + TENDRIL_TARGET_RANGE);

        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(caster.getUuid()));

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = e.getEntityPos().squaredDistanceTo(anchorCenter);
            if (d > rangeSq) continue;
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
