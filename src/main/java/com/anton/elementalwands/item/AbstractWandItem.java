package com.anton.elementalwands.item;

import java.util.Locale;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.util.WandUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class AbstractWandItem extends Item {

    public static final double DEFAULT_RANGE = 25.0;

    public static final int DEFAULT_PRIMARY_COOLDOWN_TICKS   = 20;
    public static final int DEFAULT_SECONDARY_COOLDOWN_TICKS = 120;

    private static final int GLOBAL_COOLDOWN_TICKS = 6;

    private static final String NBT_LAST_GLOBAL    = "ew_last_global";
    private static final String NBT_LAST_PRIMARY   = "ew_last_primary";
    private static final String NBT_LAST_SECONDARY = "ew_last_secondary";
    // NOTE: NBT_LAST_ULTIMATE intentionally removed — replaced by charge system
    public static final String NBT_ULTIMATE_CHARGE = "elementalwands:ultimate_charge";

    protected AbstractWandItem(Settings settings) {
        super(settings);
    }

    public abstract void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack);
    public abstract void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack);
    public abstract void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack);

    public int getPrimaryCooldownTicks() {
        return DEFAULT_PRIMARY_COOLDOWN_TICKS;
    }

    public int getSecondaryCooldownTicks() {
        return DEFAULT_SECONDARY_COOLDOWN_TICKS;
    }

    /**
     * Legacy accessor kept for HUD rendering; returns a fixed display value since
     * the actual system now uses the charge reservoir instead of a timer.
     */
    public int getUltimateCooldownTicks() {
        return 0;
    }

    // -----------------------------------------------------------------------
    // Skill gate
    // -----------------------------------------------------------------------

    public boolean isAbilityUnlocked(PlayerEntity player, Ability ability) {
        if (this instanceof FracturedWandItem) {
            return ability == Ability.PRIMARY;
        }
        return switch (ability) {
            case SECONDARY -> (player.getAttachedOrElse(EWAttachments.UNLOCKED_SKILLS, 0) & EWAttachments.SKILL_SECONDARY) != 0;
            case ULTIMATE  -> (player.getAttachedOrElse(EWAttachments.UNLOCKED_SKILLS, 0) & EWAttachments.SKILL_ULTIMATE)  != 0;
            default        -> true;
        };
    }

    // -----------------------------------------------------------------------
    // use() — right-click / shift+right-click
    // -----------------------------------------------------------------------

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient())
            return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld serverWorld))
            return ActionResult.SUCCESS;
        if (user.isSpectator())
            return ActionResult.PASS;

        if (user.isSneaking()) {
            if (!isAbilityUnlocked(user, Ability.SECONDARY)) {
                user.sendMessage(Text.translatable("hud.elementalwands.locked"), true);
                return ActionResult.FAIL;
            }
            castSecondary(serverWorld, user, stack);
        } else {
            if (!isAbilityUnlocked(user, Ability.PRIMARY)) {
                user.sendMessage(Text.translatable("hud.elementalwands.locked"), true);
                return ActionResult.FAIL;
            }
            castPrimary(serverWorld, user, stack);
        }
        return ActionResult.SUCCESS;
    }

    // -----------------------------------------------------------------------
    // Raycast / particle helpers
    // -----------------------------------------------------------------------

    protected final HitResult raycast(ServerWorld world, Entity caster, double range) {
        return WandUtils.raycast(world, caster, range);
    }

    protected final void spawnParticleLine(ServerWorld world, Vec3d start, Vec3d end, ParticleEffect particle) {
        WandUtils.spawnBeam(world, start, end, particle);
    }

    // -----------------------------------------------------------------------
    // Cooldown system (PRIMARY / SECONDARY)
    // -----------------------------------------------------------------------

    protected final boolean tryStartCooldown(ServerWorld world, PlayerEntity player, ItemStack stack,
            Ability ability, int abilityCooldownTicks) {
        long now = world.getTime();

        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();

        long lastGlobal = nbt.getLong(NBT_LAST_GLOBAL).orElse(-1_000_000_000L);
        long globalRemaining = GLOBAL_COOLDOWN_TICKS - (now - lastGlobal);
        if (globalRemaining > 0) {
            sendCooldownActionbar(player, Ability.GLOBAL, (int) globalRemaining);
            return false;
        }

        String key = switch (ability) {
            case PRIMARY   -> NBT_LAST_PRIMARY;
            case SECONDARY -> NBT_LAST_SECONDARY;
            default        -> NBT_LAST_PRIMARY;
        };

        long last    = nbt.getLong(key).orElse(-1_000_000_000L);
        long elapsed = now - last;

        if (com.anton.elementalwands.util.ChillTracker.getStacks(player) > 0) {
            elapsed /= 2;
        }

        long remaining = abilityCooldownTicks - elapsed;
        if (remaining > 0) {
            sendCooldownActionbar(player, ability, (int) remaining);
            return false;
        }

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putLong(key, now);
            data.putLong(NBT_LAST_GLOBAL, now);
        });
        return true;
    }

    // -----------------------------------------------------------------------
    // Ultimate charge system
    // -----------------------------------------------------------------------

    /**
     * Attempts to spend 100 charge to fire the ultimate. Returns false (and
     * shows a message) if charge is insufficient or the ability is locked.
     */
    protected final boolean trySpendUltimateCharge(ServerWorld world, PlayerEntity player, ItemStack stack) {
        if (!isAbilityUnlocked(player, Ability.ULTIMATE)) {
            player.sendMessage(Text.translatable("hud.elementalwands.locked"), true);
            return false;
        }

        long now = world.getTime();
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();

        long lastGlobal = nbt.getLong(NBT_LAST_GLOBAL).orElse(-1_000_000_000L);
        if (now - lastGlobal < GLOBAL_COOLDOWN_TICKS) {
            return false;
        }

        int charge = nbt.getInt(NBT_ULTIMATE_CHARGE, 0);
        if (charge < 100) {
            player.sendMessage(Text.translatable("message.elementalwands.insufficient_charge"), true);
            return false;
        }

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putInt(NBT_ULTIMATE_CHARGE, 0);
            data.putLong(NBT_LAST_GLOBAL, now);
        });
        return true;
    }

    /**
     * Adds {@code amount} ultimate charge (capped at 100) to the given stack.
     */
    public static void addUltimateCharge(ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof AbstractWandItem)) return;
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            int current = data.getInt(NBT_ULTIMATE_CHARGE, 0);
            data.putInt(NBT_ULTIMATE_CHARGE, Math.min(100, current + amount));
        });
    }

    public static int getUltimateCharge(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getInt(NBT_ULTIMATE_CHARGE, 0);
    }

    // -----------------------------------------------------------------------
    // Arcane Flux & charge-on-hit helpers
    // -----------------------------------------------------------------------

    /**
     * Call this whenever a wand deals damage to a living entity (directly or via
     * a projectile entity). Grants +1 Arcane Flux per damage point and +5
     * ultimate charge to the owner's held wand.
     */
    public static void onWandDamageDealt(Entity owner, float damageDealt) {
        if (!(owner instanceof ServerPlayerEntity player)) return;

        long current = player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L);
        player.setAttached(EWAttachments.ARCANE_FLUX, current + Math.max(1L, Math.round(damageDealt)));

        ItemStack held = player.getMainHandStack();
        addUltimateCharge(held, 5);
    }

    // -----------------------------------------------------------------------
    // Direct-damage helper (used by wands that raycast)
    // -----------------------------------------------------------------------

    protected final boolean applyDamage(ServerWorld world, PlayerEntity caster, Entity target, float amount) {
        if (!(target instanceof LivingEntity living))
            return false;

        DamageSource source = world.getDamageSources().playerAttack(caster);
        boolean damaged = living.damage(world, source, amount);
        if (damaged) {
            onWandDamageDealt(caster, amount);
        }
        return damaged;
    }

    protected final void sendCooldownActionbar(PlayerEntity player, Ability ability, int remainingTicks) {
        double seconds = remainingTicks / 20.0;
        String label = ability.displayName;
        String msg = String.format(Locale.ROOT, "%s cooldown: %.1fs", label, seconds);
        player.sendMessage(Text.literal(msg), true);
    }

    // -----------------------------------------------------------------------
    // Ability enum
    // -----------------------------------------------------------------------

    public enum Ability {
        GLOBAL("GLOBAL"),
        PRIMARY("PRIMARY"),
        SECONDARY("SECONDARY"),
        ULTIMATE("ULTIMATE");

        private final String displayName;

        Ability(String displayName) {
            this.displayName = displayName;
        }
    }
}
