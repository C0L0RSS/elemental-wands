package com.anton.elementalwands.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import com.anton.elementalwands.entity.CalamityTornadoEntity;
import com.anton.elementalwands.entity.VacuumBladeEntity;

public class WindWandItem extends AbstractWandItem {

    // Primary: Vacuum Blades
    private static final double VACUUM_BLADE_OFFSET = 0.5; // Distance between two blades

    // Secondary: Waylay Dash
    private static final int DASH_MAX_CHARGES = 3;
    private static final int DASH_RECHARGE_TICKS = 80;
    private static final int DASH_CHAIN_WINDOW_TICKS = 30; // 1.5 seconds
    private static final float DASH_BASE_STRENGTH = 2.0f;

    private static final int SLOW_FALLING_DURATION_TICKS = 40; // 2 seconds

    private static final String NBT_DASH_CHARGES = "DashCharges";
    private static final String NBT_LAST_DASH_TICK = "LastDashTick";
    private static final String NBT_CHAIN_COUNT = "ChainCount";
    private static final String NBT_RECHARGE_TICKS = "RechargeTicks";

    // Ultimate: Zephyr Strike
    private static final String NBT_ZEPHYR_ACTIVE = "ZephyrStrikeActive";
    private static final String NBT_ZEPHYR_TICK = "ZephyrStrikeTick";
    private static final int ZEPHYR_MAX_DURATION = 100; // 5 seconds maximum flight time before auto-reset

    public WindWandItem(Settings settings) {
        super(settings);
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

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        // Get perpendicular offset to spawn two blades side-by-side
        Vec3d forward = caster.getRotationVec(1.0f).normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x).normalize(); // Perpendicular horizontal vector

        Vec3d offset1 = right.multiply(VACUUM_BLADE_OFFSET);
        Vec3d offset2 = right.multiply(-VACUUM_BLADE_OFFSET);

        // Spawn two vacuum blades
        VacuumBladeEntity blade1 = new VacuumBladeEntity(world, caster, offset1);
        VacuumBladeEntity blade2 = new VacuumBladeEntity(world, caster, offset2);

        world.spawnEntity(blade1);
        world.spawnEntity(blade2);

        // Sound effects
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f,
                1.3f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS,
                0.6f, 1.5f);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, world, entity, slot);

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

        // Zephyr Strike Logic
        if (entity instanceof PlayerEntity player && !world.isClient()
                && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) {
            if (data.getBoolean(NBT_ZEPHYR_ACTIVE).orElse(false)) {
                player.fallDistance = 0; // Immune to fall damage

                int startTick = data.getInt(NBT_ZEPHYR_TICK, 0);
                int currentTick = world.getServer().getTicks();

                if (currentTick - startTick > 10) {
                    if (player.isOnGround() || player.horizontalCollision) {
                        // Impact!
                        Vec3d vel = player.getVelocity();
                        float power = 3.0f + (float) vel.length() * 2.0f;
                        if (power < 3.0f)
                            power = 4.0f;

                        world.createExplosion(player, player.getX(), player.getY(), player.getZ(), power, false,
                                net.minecraft.world.World.ExplosionSourceType.NONE);

                        // Reset
                        data.putBoolean(NBT_ZEPHYR_ACTIVE, false);
                        unquipElytra(player);
                        saveDashData(stack, data);
                    } else if (currentTick - startTick > ZEPHYR_MAX_DURATION) {
                        // Timeout
                        data.putBoolean(NBT_ZEPHYR_ACTIVE, false);
                        unquipElytra(player);
                        saveDashData(stack, data);
                    }
                }
            }
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        // Get or initialize dash data
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
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, caster.getX(), caster.getY(), caster.getZ(),
                15 + (chainCount * 5), 0.4, 0.2, 0.4, 0.03);

        // Display charge count and chain info to player
        if (caster instanceof ServerPlayerEntity) {
            String message = charges > 0
                    ? String.format("§bDashes: %d/3 §7| §eChain: x%d", charges, chainCount + 1)
                    : "§cDash charges depleted!";
            caster.sendMessage(net.minecraft.text.Text.literal(message), true); // action bar
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, getUltimateCooldownTicks()))
            return;

        NbtCompound data = getDashData(stack);
        data.putBoolean(NBT_ZEPHYR_ACTIVE, true);
        data.putInt(NBT_ZEPHYR_TICK, world.getServer().getTicks());
        saveDashData(stack, data);

        equipElytra(caster);

        Vec3d look = caster.getRotationVec(1.0f).normalize();
        Vec3d horizontal = new Vec3d(look.x, 0, look.z).normalize();

        Vec3d launchVel = new Vec3d(horizontal.x * 3.52, 3.52, horizontal.z * 3.52);

        caster.addVelocity(launchVel.x, launchVel.y, launchVel.z);
        caster.velocityModified = true;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.5f,
                0.8f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS,
                2.0f, 0.6f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.GUST_EMITTER_LARGE, caster.getX(),
                caster.getBodyY(0.5), caster.getZ(), 3, 1.0, 1.0, 1.0, 0.0);
    }

    private void equipElytra(PlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isEmpty() && !chest.isOf(Items.ELYTRA)) {
            // Check if player has Elytra tracker tag
            if (!player.getCommandTags().contains("has_zephyr_elytra")) {
                if (!player.getInventory().insertStack(chest.copy())) {
                    player.dropItem(chest.copy(), false);
                }
                player.getCommandTags().add("has_zephyr_elytra");
            }
        }
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
    }

    private void unquipElytra(PlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.isOf(Items.ELYTRA) && player.getCommandTags().contains("has_zephyr_elytra")) {
            player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.getCommandTags().remove("has_zephyr_elytra");
        }
    }

    private NbtCompound getDashData(ItemStack stack) {
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        return nbtComponent.copyNbt();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void saveDashData(ItemStack stack, NbtCompound data) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }
}
