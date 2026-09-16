package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Server authority for loadouts; swapping never touches wand cooldown/charge NBT. */
public final class WandLoadouts {
    private static final Map<UUID, Integer> COMBAT_UNTIL = new HashMap<>();
    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayerEntity p) markCombat(p);
            if (source.getAttacker() instanceof ServerPlayerEntity p) markCombat(p);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> COMBAT_UNTIL.clear());
    }
    public static List<String> get(PlayerEntity player) {
        var affinity = EWAttachments.getAffinity(player);
        var ids = player.getAttachedOrElse(EWAttachments.WAND_LOADOUTS, Map.of()).get(affinity.name());
        var normalized = WandSpells.normalize(affinity, ids);
        if (ids != null && !ids.equals(normalized)) {
            var saved = new HashMap<>(player.getAttachedOrElse(EWAttachments.WAND_LOADOUTS, Map.of()));
            saved.put(affinity.name(), normalized);
            player.setAttached(EWAttachments.WAND_LOADOUTS, Map.copyOf(saved));
        }
        return normalized;
    }
    public static void markCombat(ServerPlayerEntity player) {
        COMBAT_UNTIL.put(player.getUuid(), player.getEntityWorld().getServer().getTicks() + 200);
    }
    public static int combatTicks(ServerPlayerEntity player) {
        return Math.max(0, COMBAT_UNTIL.getOrDefault(player.getUuid(), 0) - player.getEntityWorld().getServer().getTicks());
    }
    public static boolean canEdit(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && combatTicks(player) == 0
                && !HollowPurpleChargeManager.isCharging(player.getEntityWorld(), player)
                && !com.anton.elementalwands.arena.GuardianArenaManager.isParticipant(player);
    }
    public static String equip(ServerPlayerEntity player, String expectedAffinity, int slot, String id) {
        var affinity = EWAttachments.getAffinity(player);
        if (!affinity.name().equals(expectedAffinity)) return "Your affinity changed. Reopen the hub.";
        if (!(player.getMainHandStack().getItem() instanceof AbstractWandItem)) return "Hold your wand to change spells.";
        if (!canEdit(player)) return "You can change spells outside combat.";
        var spell = WandSpells.find(id);
        if (spell == null || !WandProgression.owns(player, spell)) return "Unlock this spell first.";
        if (slot < 0 || slot > 2 || spell.category().slot() != slot) return "This spell fits the " + spell.category().label() + " slot.";
        var current = get(player);
        var next = WandSpells.equip(affinity, current, slot, id);
        if (next.equals(current)) return "This spell is already equipped or does not fit that slot.";
        var saved = new HashMap<>(player.getAttachedOrElse(EWAttachments.WAND_LOADOUTS, Map.of()));
        saved.put(affinity.name(), next);
        player.setAttached(EWAttachments.WAND_LOADOUTS, Map.copyOf(saved));
        return "Equipped " + spell.name() + " in slot " + (slot + 1) + ".";
    }
    public static void cast(ServerPlayerEntity player, int slot) {
        if (!player.isAlive() || player.isSpectator() || !com.anton.elementalwands.arena.GuardianArenaManager.canCast(player)) return;
        ServerWorld world = player.getEntityWorld();
        if (HollowPurpleChargeManager.isCharging(world, player) || FireLeapManager.flying(player)) return;
        var stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof AbstractWandItem wand)) return;
        if(slot==0 && (FlashoverManager.tryDisarmAimed(player) || TendrilBloomManager.tryBreakKnotAimed(player)))return;
        var ids = get(player);
        if (slot < 0 || slot >= ids.size()) return;
        var spell = WandSpells.find(ids.get(slot));
        if (!WandProgression.owns(player, spell)) {
            player.sendMessage(Text.translatable("hud.elementalwands.locked"), true); return;
        }
        if (spell.id().equals("flamethrower")) { FireBuildManager.hold(player); return; }
        if (spell.id().equals("flashover")) { FlashoverManager.toss(player);return; }
        if (spell.id().equals("fire_hop")) return; // Aimed release uses FireLeapCommitPayload.
        FireBuildManager.stop(player);
        markCombat(player);
        if (spell.id().equals("thorn_lash")) {
            com.anton.elementalwands.item.NatureAbilityHandler.castThornLash(world, player, stack); return;
        }
        switch (spell.ability()) {
            case PRIMARY -> wand.castPrimary(world, player, stack);
            case SECONDARY -> wand.castSecondary(world, player, stack);
            case ULTIMATE -> wand.castUltimate(world, player, stack);
            default -> { }
        }
    }
    private WandLoadouts() {}
}
