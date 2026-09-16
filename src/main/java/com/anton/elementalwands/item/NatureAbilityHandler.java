package com.anton.elementalwands.item;


import java.util.List;

import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.NatureVfx;
import com.anton.elementalwands.util.SeedlingManager;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TendrilBloomManager;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class NatureAbilityHandler {

    private static final int PRIMARY_COOLDOWN_TICKS = 20;
    private static final int SECONDARY_COOLDOWN_TICKS = 300;

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
        int newPadFlourishes = 0;
        for (int dx = -VERDANT_STEP_RADIUS; dx <= VERDANT_STEP_RADIUS; dx++) {
            for (int dz = -VERDANT_STEP_RADIUS; dz <= VERDANT_STEP_RADIUS; dz++) {
                BlockPos padPos = base.add(dx, 0, dz);
                // A lily pad floats on the air block whose neighbour below is water.
                if (!world.getBlockState(padPos.down()).getFluidState().isOf(Fluids.WATER)) continue;
                BlockState padState = world.getBlockState(padPos);
                if (padState.isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_RAFT)
                        || (!padState.isAir() && !padState.isReplaceable())) continue;
                int placed = TemporaryBlockManager.placeTemporaryBlocks(world,
                        List.of(padPos),
                        com.anton.elementalwands.registry.ModSpellBlocks.NATURE_RAFT.getDefaultState(),
                        VERDANT_STEP_PAD_LIFESPAN,
                        s -> s.isAir() || s.isReplaceable());
                // Only newly unfurled pads receive a flourish, and cap the initial burst so
                // stepping into a large lake stays network-friendly.
                if (placed > 0 && newPadFlourishes++ < 10) {
                    Vec3d padCenter = Vec3d.ofCenter(padPos).add(0.0, -0.38, 0.0);
                    world.spawnParticles(ModParticles.NATURE_LEAF,
                            padCenter.x, padCenter.y + 0.06, padCenter.z,
                            2, 0.18, 0.04, 0.18, 0.01);
                    world.spawnParticles(ModParticles.NATURE_POLLEN,
                            padCenter.x, padCenter.y + 0.14, padCenter.z,
                            2, 0.14, 0.05, 0.14, 0.006);
                }
            }
        }

        int now = world.getServer().getTicks();
        if (newPadFlourishes > 0 && (now + player.getId()) % 6 == 0) {
            NatureVfx.fairyRipple(world,
                    player.getEntityPos().add(0.0, 0.08, 0.0), 0.72, now);
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

    public static void castThornLash(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                AbstractWandItem.Ability.PRIMARY, com.anton.elementalwands.util.ThornLashRules.COOLDOWN)) return;
        var lash = new com.anton.elementalwands.entity.ThornLashEntity(
                com.anton.elementalwands.registry.ModEntities.THORN_LASH, world);
        lash.initialize(caster);
        world.spawnEntity(lash);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, .8f, .75f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_AZALEA_LEAVES_BREAK,
                SoundCategory.PLAYERS, .7f, .7f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        var seedlings = SeedlingManager.getActiveSeedlingsForCaster(world, caster.getUuid());
        BlockPos knot = seedlings.isEmpty() ? TendrilBloomManager.findKnotPosition(world, caster) : null;
        if (seedlings.isEmpty() && knot == null) {
            caster.sendMessage(Text.literal("Stand near clear, solid ground to grow a root knot."), true);
            return;
        }
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks())) return;
        if (seedlings.isEmpty()) {
            TendrilBloomManager.startKnot(world, caster, knot);
        } else {
            for (var seedling : seedlings) {
                var targets = TendrilBloomManager.findTargets(world, caster, Vec3d.ofCenter(seedling.anchorPos()));
                if (!targets.isEmpty()) TendrilBloomManager.startTendril(world, caster,
                        seedling.seedlingId(), Vec3d.ofCenter(seedling.anchorPos()), targets.getFirst());
            }
            SeedlingManager.markSeedlingsForConsumption(world, caster.getUuid(), world.getServer().getTicks());
        }
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GRASS_BREAK,
                SoundCategory.PLAYERS, .6f, 1);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) return;
        var seed = new com.anton.elementalwands.entity.OvergrowthSeedEntity(
                com.anton.elementalwands.registry.ModEntities.OVERGROWTH_SEED, world);
        seed.launch(caster, stack);
        world.spawnEntity(seed);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_EGG_THROW,
                SoundCategory.PLAYERS, .9f, .6f);
    }
}
