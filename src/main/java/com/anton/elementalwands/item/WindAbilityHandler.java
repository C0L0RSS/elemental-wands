package com.anton.elementalwands.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import com.anton.elementalwands.entity.VacuumBladeEntity;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.WaylayDashVfxManager;
import com.anton.elementalwands.util.ZephyrStrikeManager;

public final class WindAbilityHandler {

    // Primary: Vacuum Blades
    private static final double VACUUM_BLADE_OFFSET = 0.5; // Distance between two blades

    // Secondary: Waylay Dash
    private static final int DASH_MAX_CHARGES = 2;
    private static final int DASH_RECHARGE_TICKS = 80;
    private static final int DASH_CHAIN_WINDOW_TICKS = 30; // 1.5 seconds
    private static final float DASH_BASE_STRENGTH = 2.0f;

    private static final int SLOW_FALLING_DURATION_TICKS = 40; // 2 seconds

    private static final String NBT_DASH_CHARGES = "DashCharges";
    private static final String NBT_LAST_DASH_TICK = "LastDashTick";
    private static final String NBT_CHAIN_COUNT = "ChainCount";
    private static final String NBT_RECHARGE_TICKS = "RechargeTicks";

    private WindAbilityHandler() {}

    public static int getPrimaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_SECONDARY_COOLDOWN_TICKS;
    }

    public static int getDashMaxCharges() {
        return DASH_MAX_CHARGES;
    }

    public static int getDashRechargeDurationTicks() {
        return DASH_RECHARGE_TICKS;
    }

    public static int getDashCharges(ItemStack stack) {
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound data = nbtComponent.copyNbt();
        if (!data.contains(NBT_DASH_CHARGES)) {
            return DASH_MAX_CHARGES;
        }

        return clamp(data.getInt(NBT_DASH_CHARGES, DASH_MAX_CHARGES), 0, DASH_MAX_CHARGES);
    }

    public static int getDashRechargeTicks(ItemStack stack) {
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound data = nbtComponent.copyNbt();
        if (!data.contains(NBT_RECHARGE_TICKS)) {
            return 0;
        }

        return clamp(data.getInt(NBT_RECHARGE_TICKS, 0), 0, DASH_RECHARGE_TICKS);
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        // Get perpendicular offset to spawn two blades side-by-side
        Vec3d forward = caster.getRotationVec(1.0f).normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x).normalize(); // Perpendicular horizontal vector

        Vec3d offset1 = right.multiply(VACUUM_BLADE_OFFSET);
        Vec3d offset2 = right.multiply(-VACUUM_BLADE_OFFSET);

        // Spawn two vacuum blades
        VacuumBladeEntity blade1 = new VacuumBladeEntity(world, caster, offset1, false);
        VacuumBladeEntity blade2 = new VacuumBladeEntity(world, caster, offset2, true);

        world.spawnEntity(blade1);
        world.spawnEntity(blade2);

        // Sound effects
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f,
                1.3f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS,
                0.6f, 1.5f);

        Vec3d origin = caster.getEyePos().add(forward.multiply(0.7));
        world.spawnParticles(ModParticles.WIND_BURST_RING,
                origin.x, origin.y, origin.z,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.WIND_AIR_RIBBON,
                origin.x, origin.y, origin.z,
                7, 0.28, 0.2, 0.28, 0.055);
        world.spawnParticles(ModParticles.WIND_MOTE,
                origin.x, origin.y, origin.z,
                9, 0.32, 0.22, 0.32, 0.045);
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        NbtCompound data = getDashData(stack);
        int charges = data.getInt(NBT_DASH_CHARGES, 0);
        int rechargeTicks = data.getInt(NBT_RECHARGE_TICKS, 0);

        // Initialize if new
        if (!data.contains(NBT_DASH_CHARGES)) {
            charges = DASH_MAX_CHARGES;
            data.putInt(NBT_DASH_CHARGES, charges);
        }

        // Passive Recharge
        if (charges < DASH_MAX_CHARGES) {
            rechargeTicks++;
            if (rechargeTicks >= DASH_RECHARGE_TICKS) { // 4 seconds
                charges++;
                rechargeTicks = 0;

                // Play a subtle sound when a charge returns? Optional found sound.
                // For now just logic.
            }
            data.putInt(NBT_DASH_CHARGES, charges);
            data.putInt(NBT_RECHARGE_TICKS, rechargeTicks);
            saveDashData(stack, data);
        } else if (rechargeTicks > 0) {
            // Reset ticks if full
            data.putInt(NBT_RECHARGE_TICKS, 0);
            saveDashData(stack, data);
        }

    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        // Get or initialize dash data
        // Disable Waylay Dash during Zephyr Strike
        if (ZephyrStrikeManager.isActive(caster)) {
            return;
        }

        NbtCompound data = getDashData(stack);

        int charges = data.getInt(NBT_DASH_CHARGES, 0);
        int lastDashTick = data.getInt(NBT_LAST_DASH_TICK, 0);
        int chainCount = data.getInt(NBT_CHAIN_COUNT, 0);
        int currentTick = world.getServer().getTicks();

        // Initialize charges if first use
        if (!data.contains(NBT_DASH_CHARGES)) {
            charges = DASH_MAX_CHARGES;
            data.putInt(NBT_DASH_CHARGES, charges);
        }

        // Check if we have charges
        if (charges <= 0) {
            return; // Do nothing if no charges
        }

        // Check if chaining (dashed within window)
        boolean isChaining = (currentTick - lastDashTick) <= DASH_CHAIN_WINDOW_TICKS;
        if (isChaining) {
            chainCount++;
        } else {
            chainCount = 0;
        }

        // Calculate dash strength with additive bonus
        // Base (2.0) + (0.5 * chainCount)
        // Chain 0: 2.0
        // Chain 1: 2.5
        // Chain 2: 3.0
        float dashStrength = DASH_BASE_STRENGTH + (chainCount * 0.5f);

        // Execute dash
        Vec3d look = caster.getRotationVec(1.0f).normalize();
        caster.addVelocity(look.x * dashStrength, look.y * dashStrength * 0.5, look.z * dashStrength); // Less vertical
        caster.setOnGround(false);
        caster.fallDistance = 0;

        // Apply Slow Falling
        caster.addStatusEffect(
                new StatusEffectInstance(StatusEffects.SLOW_FALLING, SLOW_FALLING_DURATION_TICKS, 0, false,
                        false, true));

        // Update velocity on client
        if (caster instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.networkHandler
                    .sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(caster));
            WaylayDashVfxManager.start(serverPlayer, chainCount);
        }
        caster.velocityModified = true;

        // Use one charge
        charges--;
        data.putInt(NBT_DASH_CHARGES, charges);
        data.putInt(NBT_LAST_DASH_TICK, currentTick);
        data.putInt(NBT_CHAIN_COUNT, chainCount);
        saveDashData(stack, data);

        // Sound and particles (more intense with chains)
        float pitch = 1.0f + (chainCount * 0.1f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS,
                1.0f, pitch);
        world.spawnParticles(ModParticles.WIND_BURST_RING,
                caster.getX(), caster.getBodyY(0.45), caster.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.WIND_AIR_RIBBON,
                caster.getX(), caster.getBodyY(0.45), caster.getZ(),
                12 + (chainCount * 5), 0.42, 0.24, 0.42, 0.055 + chainCount * 0.012);
        world.spawnParticles(ModParticles.WIND_MOTE,
                caster.getX(), caster.getBodyY(0.5), caster.getZ(),
                8 + (chainCount * 4), 0.48, 0.28, 0.48, 0.045 + chainCount * 0.008);

        // Display charge count and chain info to player
        if (caster instanceof ServerPlayerEntity) {
            String message = charges > 0
                    ? String.format("§fDashes: %d/2 §7| §fChain: x%d", charges, chainCount + 1)
                    : "§cDash charges depleted!";
            caster.sendMessage(net.minecraft.text.Text.literal(message), true); // action bar
        }
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!(caster instanceof ServerPlayerEntity serverPlayer) || ZephyrStrikeManager.isActive(caster))
            return;

        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack))
            return;

        ZephyrStrikeManager.begin(serverPlayer, stack, world.getServer().getTicks());

        Vec3d look = caster.getRotationVec(1.0f).normalize();
        Vec3d horizontal = new Vec3d(look.x, 0, look.z).normalize();

        Vec3d launchVel = new Vec3d(horizontal.x * 3.52, 3.52, horizontal.z * 3.52);

        caster.addVelocity(launchVel.x, launchVel.y, launchVel.z);
        caster.velocityModified = true;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.5f,
                0.8f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS,
                2.0f, 0.6f);
        world.spawnParticles(ModParticles.WIND_BURST_RING,
                caster.getX(), caster.getBodyY(0.45), caster.getZ(),
                2, 0.2, 0.15, 0.2, 0.0);
        world.spawnParticles(ModParticles.WIND_AIR_RIBBON,
                caster.getX(), caster.getBodyY(0.5), caster.getZ(),
                24, 0.9, 0.75, 0.9, 0.11);
        world.spawnParticles(ModParticles.WIND_MOTE,
                caster.getX(), caster.getBodyY(0.55), caster.getZ(),
                20, 0.85, 0.7, 0.85, 0.1);
    }

    private static NbtCompound getDashData(ItemStack stack) {
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        return nbtComponent.copyNbt();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void saveDashData(ItemStack stack, NbtCompound data) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }
}
