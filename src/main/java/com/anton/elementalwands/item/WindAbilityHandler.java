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
import net.minecraft.util.math.Vec3d;

import com.anton.elementalwands.entity.VacuumBladeEntity;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.registry.ModSounds;
import com.anton.elementalwands.util.BlockWindEvents;
import com.anton.elementalwands.util.ZephyrStrikeManager;

public final class WindAbilityHandler {

    // Primary: Vacuum Blades

    // Secondary: Waylay Dash
    private static final int DASH_MAX_CHARGES = 2;
    private static final int DASH_RECHARGE_TICKS = com.anton.elementalwands.entity.WindFanRules.DASH_RECHARGE_TICKS;
    private static final int DASH_CHAIN_WINDOW_TICKS = 30; // 1.5 seconds
    private static final float DASH_BASE_STRENGTH = 2.0f;

    private static final int SLOW_FALLING_DURATION_TICKS = 40; // 2 seconds

    private static final String NBT_DASH_CHARGES = "DashCharges";
    private static final String NBT_LAST_DASH_TICK = "LastDashTick";
    private static final String NBT_CHAIN_COUNT = "ChainCount";
    private static final String NBT_RECHARGE_TICKS = "RechargeTicks";
    private static final String NBT_LAST_RECHARGE_TICK = "last_recharge_tick";
    private static final NbtCompound EMPTY_DASH_STATE = new NbtCompound();

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

    public static int getDashCharges(PlayerEntity player) {
        return dashCharges(dashData(player));
    }

    public static int getDashRechargeTicks(PlayerEntity player) {
        return rechargeTicks(dashData(player));
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        // One center blade and two narrow wings, all sharing one accepted hit per enemy.
        Vec3d forward = caster.getRotationVec(1.0f).normalize();
        java.util.Set<java.util.UUID> hits = new java.util.HashSet<>();
        for (int wing=-1; wing<=1; wing++) {
            VacuumBladeEntity blade = new VacuumBladeEntity(world,caster,
                    com.anton.elementalwands.entity.WindFanRules.direction(forward,wing),wing<0,hits);
            world.spawnEntity(blade);
        }

        // Sound effects
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_WIND_SKY_SHEAR_CAST, SoundCategory.PLAYERS, 0.9f,
                1.0f);

        // The cast bloom is drawn by each client's block-wind blades (client/blockwind/ShearFx).
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof PlayerEntity player)) return;
        migrateDash(player, stack, world.getTime());
        var state = dashData(player);
        long now = world.getTime();
        int charges = dashCharges(state);
        // Vanilla ticks every carried wand. A player recharges only once per tick.
        if (charges >= DASH_MAX_CHARGES || state.getLong(NBT_LAST_RECHARGE_TICK, -1) == now) return;
        var data = state.copy();
        int progress = rechargeTicks(data) + 1;
        if (progress >= DASH_RECHARGE_TICKS) {
            charges++;
            progress = 0;
        }
        data.putInt(NBT_DASH_CHARGES, charges);
        data.putInt(NBT_RECHARGE_TICKS, progress);
        data.putLong(NBT_LAST_RECHARGE_TICK, now);
        player.setAttached(EWAttachments.WAYLAY_DASH, data);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        // Disable Waylay Dash during Zephyr Strike
        if (ZephyrStrikeManager.isActive(caster)) {
            return;
        }

        long currentTick = world.getTime();
        for (int i = 0; i < caster.getInventory().size(); i++) {
            migrateDash(caster, caster.getInventory().getStack(i), currentTick);
        }
        migrateDash(caster, stack, currentTick);
        NbtCompound data = dashData(caster).copy();
        int charges = dashCharges(data);
        long lastDashTick = data.getLong(NBT_LAST_DASH_TICK, 0L);
        int chainCount = data.getInt(NBT_CHAIN_COUNT, 0);

        // Check if we have charges
        if (charges <= 0) {
            return; // Do nothing if no charges
        }

        // Check if chaining (dashed within window)
        boolean isChaining = lastDashTick > 0 && (currentTick - lastDashTick) <= DASH_CHAIN_WINDOW_TICKS;
        if (isChaining) {
            chainCount++;
        } else {
            chainCount = 0;
        }

        // Scale the entire impulse, including the chain bonus, to 75% of its original strength.
        float dashStrength = (DASH_BASE_STRENGTH + chainCount * 0.5f) * .75f;

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
        data.putLong(NBT_LAST_DASH_TICK, currentTick);
        data.putInt(NBT_CHAIN_COUNT, chainCount);
        caster.setAttached(EWAttachments.WAYLAY_DASH, data);

        // Sound and particles (more intense with chains)
        float pitch = 1.0f + (chainCount * 0.1f);
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_WIND_WAYLAY_DASH_CAST, SoundCategory.PLAYERS,
                1.0f, pitch);
        // Clients draw the launch ring and the cloud wake from the dasher's own movement (client/blockwind/DashFx).
        BlockWindEvents.send(world, BlockWindEvents.DASH, caster.getId(),
                new Vec3d(caster.getX(), caster.getBodyY(0.48), caster.getZ()), look, chainCount);

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

        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_WIND_ZEPHYR_STRIKE_CAST, SoundCategory.PLAYERS, 2.0f,
                1.0f);
        BlockWindEvents.send(world, BlockWindEvents.ZEPHYR_LAUNCH, caster.getId(), caster.getEntityPos(), look, 0);
    }

    private static NbtCompound dashData(PlayerEntity player) {
        return player.getAttachedOrElse(EWAttachments.WAYLAY_DASH, EMPTY_DASH_STATE);
    }

    private static int dashCharges(NbtCompound data) {
        return Math.clamp(data.getInt(NBT_DASH_CHARGES, DASH_MAX_CHARGES), 0, DASH_MAX_CHARGES);
    }

    private static int rechargeTicks(NbtCompound data) {
        return Math.clamp(data.getInt(NBT_RECHARGE_TICKS, 0), 0, DASH_RECHARGE_TICKS - 1);
    }

    private static int ticksToFull(NbtCompound data) {
        return Math.max(0, (DASH_MAX_CHARGES - dashCharges(data)) * DASH_RECHARGE_TICKS - rechargeTicks(data));
    }

    private static void migrateDash(PlayerEntity player, ItemStack stack, long now) {
        if (!(stack.getItem() instanceof AbstractWandItem)) return;
        var component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return;
        var legacy = component.copyNbt();
        if (!legacy.contains(NBT_DASH_CHARGES)) return;
        var prior = dashData(player);
        var merged = prior.copy();
        if (ticksToFull(legacy) > ticksToFull(prior)) {
            merged.putInt(NBT_DASH_CHARGES, dashCharges(legacy));
            merged.putInt(NBT_RECHARGE_TICKS, rechargeTicks(legacy));
        }
        long legacyCast = legacy.getLong(NBT_LAST_DASH_TICK, 0);
        if (legacyCast <= now && legacyCast > prior.getLong(NBT_LAST_DASH_TICK, 0)) {
            merged.putLong(NBT_LAST_DASH_TICK, legacyCast);
            merged.putInt(NBT_CHAIN_COUNT, legacy.getInt(NBT_CHAIN_COUNT, 0));
        }
        if (!merged.equals(prior)) player.setAttached(EWAttachments.WAYLAY_DASH, merged);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.remove(NBT_DASH_CHARGES);
            data.remove(NBT_RECHARGE_TICKS);
            data.remove(NBT_LAST_DASH_TICK);
            data.remove(NBT_CHAIN_COUNT);
        });
    }
}
