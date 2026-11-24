package com.anton.elementalwands.util;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WandSpells;
import com.anton.elementalwands.item.AbstractWandItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Shared, world-clock recovery state. Only the server writes the attachment. */
public final class WandCooldowns {
    public static final int GLOBAL_TICKS = 6;
    public static final int BASIC_TICKS = 10;
    public static final String GLOBAL_KEY = "ew_last_global";
    public static final String BASIC_ID = "basic_shared";
    private static final NbtCompound EMPTY = new NbtCompound();

    private WandCooldowns() {}

    public static String cooldownKey(String spellId) { return "ew_cd_" + spellId; }
    public static String durationKey(String spellId) { return "ew_cdd_" + spellId; }

    /** Read-only snapshot, including on the local player's client. */
    public static NbtCompound state(PlayerEntity player) {
        return player.getAttachedOrElse(EWAttachments.WAND_COOLDOWNS, EMPTY);
    }

    public static long globalRemaining(NbtCompound data, long now) {
        return remaining(data, GLOBAL_KEY, now, GLOBAL_TICKS, false);
    }

    public static long basicRemaining(NbtCompound data, long now, boolean entangled) {
        return remaining(data, cooldownKey(BASIC_ID), now, BASIC_TICKS, entangled);
    }

    public static int spellDuration(NbtCompound data, String spellId, int baseTicks) {
        return Math.max(baseTicks, data.getInt(durationKey(spellId), 0));
    }

    public static long spellRemaining(NbtCompound data, String spellId, long now, int baseTicks, boolean entangled) {
        return remaining(data, cooldownKey(spellId), now, spellDuration(data, spellId, baseTicks), entangled);
    }

    private static long remaining(NbtCompound data, String key, long now, int ticks, boolean entangled) {
        var started = data.getLong(key);
        if (started.isEmpty()) return 0;
        long elapsed = Math.max(0, now - started.get());
        return Math.max(0, ticks - (entangled ? elapsed / 2 : elapsed));
    }

    public static void start(PlayerEntity player, long now, String spellId, int ticks, boolean tapGlobal, boolean basic) {
        var data = state(player).copy();
        if (ticks > 0) {
            data.putLong(cooldownKey(spellId), now);
            data.putInt(durationKey(spellId), ticks);
        }
        if (basic) data.putLong(cooldownKey(BASIC_ID), now);
        if (tapGlobal) data.putLong(GLOBAL_KEY, now);
        player.setAttached(EWAttachments.WAND_COOLDOWNS, data);
    }

    public static void tapGlobal(PlayerEntity player, long now) {
        start(player, now, "", 0, true, false);
    }

    /** Read every carried legacy wand before a cast can commit on a fresh stack. */
    public static void migrateInventory(PlayerEntity player, ItemStack castingStack) {
        long now = player.getEntityWorld().getTime();
        for (int i = 0; i < player.getInventory().size(); i++) {
            migrateLegacy(player, player.getInventory().getStack(i), now);
        }
        migrateLegacy(player, castingStack, now);
    }

    public static void migrateLegacy(PlayerEntity player, ItemStack stack, long now) {
        if (!(stack.getItem() instanceof AbstractWandItem)) return;
        var component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return;
        var legacy = component.copyNbt();
        if (legacy.getKeys().stream().noneMatch(WandCooldowns::isLegacyKey)) return;

        var prior = state(player);
        var merged = prior.copy();
        mergeTap(merged, legacy, GLOBAL_KEY, now, GLOBAL_TICKS);
        mergeTap(merged, legacy, cooldownKey(BASIC_ID), now, BASIC_TICKS * 2);
        for (var spell : WandSpells.ALL) mergeSpell(merged, legacy, spell.id(), now);
        if (!merged.equals(prior)) player.setAttached(EWAttachments.WAND_COOLDOWNS, merged);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data ->
                data.getKeys().stream().filter(WandCooldowns::isLegacyKey).toList().forEach(data::remove));
    }

    private static boolean isLegacyKey(String key) {
        return key.equals(GLOBAL_KEY) || key.startsWith("ew_cd_") || key.startsWith("ew_cdd_");
    }

    private static void mergeTap(NbtCompound state, NbtCompound legacy, String key, long now, int window) {
        long tick = legacy.getLong(key, -1);
        if (tick >= 0 && tick <= now && now - tick < window && tick > state.getLong(key, -1)) {
            state.putLong(key, tick);
        }
    }

    private static void mergeSpell(NbtCompound state, NbtCompound legacy, String spellId, long now) {
        String key = cooldownKey(spellId);
        String duration = durationKey(spellId);
        long tick = legacy.getLong(key, -1);
        int ticks = legacy.getInt(duration, 0);
        if (tick < 0 || tick > now || ticks <= 0 || (now - tick) / 2 >= ticks) return;

        // Keep both the latest cast and the longer still-recovering duration. This
        // cannot shorten either legacy timer, even if Entangle changes mid-recovery.
        if (remaining(state, key, now, state.getInt(duration, 0), true) > 0) {
            tick = Math.max(tick, state.getLong(key, -1));
            ticks = Math.max(ticks, state.getInt(duration, 0));
        }
        state.putLong(key, tick);
        state.putInt(duration, ticks);
    }
}
