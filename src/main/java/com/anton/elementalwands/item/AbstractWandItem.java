package com.anton.elementalwands.item;

import com.anton.elementalwands.party.WandAllies;

import java.util.Locale;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
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
    /** Every Basic-category cast also starts this short shared recovery, so two Basics
     * equipped together cannot alternate faster than two casts per second. Tune or zero
     * after multiplayer balance testing. */
    public static final int BASIC_SHARED_RECOVERY_TICKS = 10;
    public static final String BASIC_SHARED_ID = "basic_shared";

    private static final String NBT_LAST_GLOBAL    = "ew_last_global";
    /** Per-spell cooldown keys: last cast tick and the recovery that cast started. */
    private static final String NBT_COOLDOWN_PREFIX = "ew_cd_";
    private static final String NBT_DURATION_PREFIX = "ew_cdd_";
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
        // Casting uses explicit slot requests. Ordinary item use remains available
        // for interaction (sneak + right-click bypasses the bound spell input).
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

    public static String cooldownKey(String spellId) { return NBT_COOLDOWN_PREFIX + spellId; }
    public static String durationKey(String spellId) { return NBT_DURATION_PREFIX + spellId; }

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
     * means "global tap only": nothing is stored and other spells are never consulted.
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

        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();

        long lastGlobal = nbt.getLong(NBT_LAST_GLOBAL).orElse(-1_000_000_000L);
        long globalRemaining = GLOBAL_COOLDOWN_TICKS - (now - lastGlobal);
        if (globalRemaining > 0) {
            sendCooldownActionbar(player, Ability.GLOBAL, (int) globalRemaining);
            return false;
        }

        boolean entangled = com.anton.elementalwands.util.EntangleTracker.getStacks(player) > 0;
        var spell = com.anton.elementalwands.data.WandSpells.find(spellId);
        boolean basic = spell != null && spell.category() == com.anton.elementalwands.data.WandSpells.Category.BASIC;

        if (abilityCooldownTicks > 0) {
            long last    = nbt.getLong(cooldownKey(spellId)).orElse(-1_000_000_000L);
            long elapsed = now - last;
            if (entangled) elapsed /= 2;

            int recovery   = Math.max(abilityCooldownTicks, nbt.getInt(durationKey(spellId), 0));
            long remaining = recovery - elapsed;
            if (remaining > 0) {
                sendCooldownActionbar(player, spell == null ? ability(spellId).displayName : spell.name(), (int) remaining);
                return false;
            }
        }

        if (basic) {
            long elapsed = now - nbt.getLong(cooldownKey(BASIC_SHARED_ID)).orElse(-1_000_000_000L);
            if (entangled) elapsed /= 2;
            long remaining = BASIC_SHARED_RECOVERY_TICKS - elapsed;
            if (remaining > 0) {
                sendCooldownActionbar(player, "Basic recovery", (int) remaining);
                return false;
            }
        }

        if (!commit) return true;
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            if (abilityCooldownTicks > 0) {
                data.putLong(cooldownKey(spellId), now);
                data.putInt(durationKey(spellId), abilityCooldownTicks);
            }
            if (basic) data.putLong(cooldownKey(BASIC_SHARED_ID), now);
            data.putLong(NBT_LAST_GLOBAL, now);
        });
        return true;
    }

    /** Ticks left on the shared Basic recovery, halved-progress aware; zero when ready. */
    public static long basicSharedRemaining(NbtCompound nbt, long now, boolean entangled) {
        long elapsed = now - nbt.getLong(cooldownKey(BASIC_SHARED_ID)).orElse(-1_000_000_000L);
        if (entangled) elapsed /= 2;
        return Math.max(0, BASIC_SHARED_RECOVERY_TICKS - elapsed);
    }

    /** Puts a spell on cooldown outside the normal cast path (channel release, wall shatter). */
    public static void startCooldown(ServerWorld world, ItemStack stack, String spellId, int cooldownTicks, boolean tapGlobal) {
        long now = world.getTime();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putLong(cooldownKey(spellId), now);
            data.putInt(durationKey(spellId), cooldownTicks);
            if (tapGlobal) data.putLong(NBT_LAST_GLOBAL, now);
        });
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
    public static void onWandDamageDealt(Entity owner, float damageDealt, WizardAffinity source) {
        onWandDamageDealt(owner, damageDealt, 5, source);
    }

    public static void onWandDamageDealt(Entity owner, float damageDealt, int ultimateCharge, WizardAffinity source) {
        if (!(owner instanceof ServerPlayerEntity player) || !Float.isFinite(damageDealt) || damageDealt <= 0) return;
        com.anton.elementalwands.util.WandProgression.earn(player, source, Math.max(1L, Math.round(damageDealt)));
        ElementalWandsMod.refreshProgression(player);
        if (EWAttachments.getAffinity(player) == source) addUltimateCharge(player.getMainHandStack(), Math.max(0, ultimateCharge));
    }

    // -----------------------------------------------------------------------
    // Direct-damage helper (used by wands that raycast)
    // -----------------------------------------------------------------------

    public static boolean applyDamage(ServerWorld world, PlayerEntity caster, Entity target, float amount) {
        if (!(target instanceof LivingEntity living) || WandAllies.protectedFrom(caster, target))
            return false;

        DamageSource source = world.getDamageSources().playerAttack(caster);
        boolean damaged = living.damage(world, source, amount);
        if (damaged) {
            onWandDamageDealt(caster, amount, WizardAffinity.NONE);
        }
        return damaged;
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
