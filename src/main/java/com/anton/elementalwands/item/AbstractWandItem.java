package com.anton.elementalwands.item;

import java.util.Locale;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.util.WandCooldowns;
import com.anton.elementalwands.util.WandUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
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

    /** Spell IDs currently being dispatched, so ability-keyed handlers resolve the right spell. */
    private static final java.util.Map<java.util.UUID, String> CASTING = new java.util.HashMap<>();
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

    /** Charge updates should not replay the equip animation. */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    // -----------------------------------------------------------------------
    // Skill gate
    // -----------------------------------------------------------------------

    public boolean isAbilityUnlocked(PlayerEntity player, Ability ability) {
        if (this instanceof UniversalWandItem && EWAttachments.getAffinity(player) == WizardAffinity.NONE) {
            return ability == Ability.PRIMARY;
        }
        return switch (ability) {
            case SECONDARY -> (com.anton.elementalwands.util.WandProgression.skills(player) & EWAttachments.SKILL_SECONDARY) != 0;
            case ULTIMATE  -> (com.anton.elementalwands.util.WandProgression.skills(player) & EWAttachments.SKILL_ULTIMATE)  != 0;
            default        -> true;
        };
    }

    // -----------------------------------------------------------------------
    // use() — right-click / shift+right-click
    // -----------------------------------------------------------------------

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // Casting uses explicit slot requests. Ordinary item use remains available for
        // interaction: Sneak + Use on a block or a usable entity in reach skips the spell
        // input (WandControls.sneakInteraction).
        return ActionResult.PASS;
    }

    // -----------------------------------------------------------------------
    // Raycast / particle helpers (public static so ability handlers can call them)
    // -----------------------------------------------------------------------

    public static HitResult raycast(ServerWorld world, Entity caster, double range) {
        return WandUtils.raycast(world, caster, range);
    }

    public static void spawnParticleLine(ServerWorld world, Vec3d start, Vec3d end, ParticleEffect particle) {
        WandUtils.spawnBeam(world, start, end, particle);
    }

    // -----------------------------------------------------------------------
    // Cooldown system (per spell)
    // -----------------------------------------------------------------------

    /** Marks the spell a dispatch is casting; handlers that only know their Ability resolve it here. */
    public static void beginCast(PlayerEntity player, String spellId) { CASTING.put(player.getUuid(), spellId); }
    public static void endCast(PlayerEntity player) { CASTING.remove(player.getUuid()); }

    /** The spell behind an ability-keyed cast: the active dispatch, else the first equipped spell of that ability. */
    public static String castingSpell(PlayerEntity player, Ability ability) {
        String active = CASTING.get(player.getUuid());
        if (active != null) return active;
        for (String id : com.anton.elementalwands.util.WandLoadouts.get(player)) {
            var spell = com.anton.elementalwands.data.WandSpells.find(id);
            if (spell != null && spell.ability() == ability) return id;
        }
        return ability.name().toLowerCase(Locale.ROOT);
    }

    public static boolean tryStartCooldown(ServerWorld world, PlayerEntity player, ItemStack stack,
            Ability ability, int abilityCooldownTicks) {
        return tryStartCooldown(world, player, stack, castingSpell(player, ability), abilityCooldownTicks);
    }

    /**
     * Checks the short global tap, then this spell's own recovery. A zero cooldown
     * means the global tap and applicable Basic recovery still apply.
     * Stone's Gathered Mass passes a variable duration; the longer of the requested
     * and previously stored recovery is authoritative.
     */
    public static boolean tryStartCooldown(ServerWorld world, PlayerEntity player, ItemStack stack,
            String spellId, int abilityCooldownTicks) {
        return checkCooldown(world, player, stack, spellId, abilityCooldownTicks, true);
    }

    /** Check recovery without consuming it; preparation uses this before the global tap. */
    public static boolean canStartCooldown(ServerWorld world, PlayerEntity player, ItemStack stack,
            String spellId, int ticks) {
        return checkCooldown(world, player, stack, spellId, ticks, false);
    }
    private static boolean checkCooldown(ServerWorld world, PlayerEntity player, ItemStack stack,
            String spellId, int abilityCooldownTicks, boolean commit) {
        long now = world.getTime();
        WandCooldowns.migrateInventory(player, stack);
        NbtCompound data = WandCooldowns.state(player);
        long globalRemaining = WandCooldowns.globalRemaining(data, now);
        if (globalRemaining > 0) {
            sendCooldownActionbar(player, Ability.GLOBAL, (int) globalRemaining);
            return false;
        }

        boolean entangled = com.anton.elementalwands.util.EntangleTracker.getStacks(player) > 0;
        var spell = com.anton.elementalwands.data.WandSpells.find(spellId);
        boolean basic = spell != null && spell.category() == com.anton.elementalwands.data.WandSpells.Category.BASIC;

        if (abilityCooldownTicks > 0) {
            long remaining = WandCooldowns.spellRemaining(data, spellId, now, abilityCooldownTicks, entangled);
            if (remaining > 0) {
                sendCooldownActionbar(player, spell == null ? ability(spellId).displayName : spell.name(), (int) remaining);
                return false;
            }
        }

        if (basic) {
            long remaining = WandCooldowns.basicRemaining(data, now, entangled);
            if (remaining > 0) {
                sendCooldownActionbar(player, "Basic recovery", (int) remaining);
                return false;
            }
        }

        if (commit) WandCooldowns.start(player, now, spellId, abilityCooldownTicks, true, basic);
        return true;
    }

    /** Puts a spell on cooldown outside the normal cast path (channel release, wall shatter). */
    public static void startCooldown(ServerWorld world, PlayerEntity player, String spellId, int cooldownTicks, boolean tapGlobal) {
        WandCooldowns.start(player, world.getTime(), spellId, cooldownTicks, tapGlobal, false);
    }

    private static Ability ability(String spellId) {
        var spell = com.anton.elementalwands.data.WandSpells.find(spellId);
        return spell == null ? Ability.PRIMARY : spell.ability();
    }

    // -----------------------------------------------------------------------
    // Ultimate charge system
    // -----------------------------------------------------------------------

    /**
     * Attempts to spend 100 charge to fire the ultimate. Returns false (and
     * shows a message) if charge is insufficient. The unlock check is now the
     * caller's responsibility (performed in UniversalWandItem before dispatch).
     */
    public static boolean trySpendUltimateCharge(ServerWorld world, PlayerEntity player, ItemStack stack) {
        long now = world.getTime();
        WandCooldowns.migrateInventory(player, stack);
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();

        if (WandCooldowns.globalRemaining(WandCooldowns.state(player), now) > 0) {
            return false;
        }

        int charge = nbt.getInt(NBT_ULTIMATE_CHARGE, 0);
        if (charge < 100) {
            player.sendMessage(Text.translatable("message.elementalwands.insufficient_charge"), true);
            return false;
        }

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> data.putInt(NBT_ULTIMATE_CHARGE, 0));
        WandCooldowns.tapGlobal(player, now);
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
    // Direct-damage helper (used by wands that raycast)
    // -----------------------------------------------------------------------

    public static boolean applyDamage(ServerWorld world, PlayerEntity caster, Entity target, float amount) {
        return target instanceof LivingEntity && SpellCombat.damageAndReward(target, world,
                world.getDamageSources().playerAttack(caster), amount, caster, WizardAffinity.NONE);
    }

    public static void sendCooldownActionbar(PlayerEntity player, Ability ability, int remainingTicks) {
        sendCooldownActionbar(player, ability.displayName, remainingTicks);
    }

    public static void sendCooldownActionbar(PlayerEntity player, String label, int remainingTicks) {
        double seconds = remainingTicks / 20.0;
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
