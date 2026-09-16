package com.anton.elementalwands.util;

import java.nio.file.*;
import java.util.*;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;

/** Real player/world/attachment checks; this class is never packaged in the mod. */
public final class WandHubServerSmoke implements ModInitializer {
    private int tick;
    private ServerPlayerEntity player;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); } catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("HUB_FAILED.txt"), e.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        if (++tick == 30) {
            var factory = com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player", MinecraftServer.class,
                    UUID.class, String.class, double.class, double.class, double.class);
            factory.setAccessible(true);
            var legacy = (ServerPlayerEntity)factory.invoke(null, server, UUID.randomUUID(), "LegacyTester", 4.5, 100.0, .5);
            legacy.setNoGravity(true);
            legacy.setAttached(EWAttachments.AFFINITY, "SPACE");
            legacy.setAttached(EWAttachments.UNLOCKED_SKILLS, 3);
            legacy.setAttached(EWAttachments.ARCANE_FLUX, 777L);
            require(WandProgression.flux(legacy) == 777 && WandProgression.owned(legacy).contains("hollow_purple"), "Legacy unlocks were not migrated");
            com.anton.elementalwands.ElementalWandsMod.handleAffinitySet(legacy.getCommandSource(), WizardAffinity.WIND);
            require(WandProgression.flux(legacy) == 0 && WandProgression.skills(legacy) == 0, "Migration replayed into a second element");
            com.anton.elementalwands.ElementalWandsMod.handleAffinitySet(legacy.getCommandSource(), WizardAffinity.SPACE);
            require(WandProgression.flux(legacy) == 777 && WandProgression.skills(legacy) == 3, "Legacy progress lost on return");
            var pending = (ServerPlayerEntity)factory.invoke(null, server, UUID.randomUUID(), "PendingTester", 8.5, 100.0, .5);
            pending.setNoGravity(true);
            pending.setAttached(EWAttachments.ARCANE_FLUX, 90L);
            pending.setAttached(EWAttachments.UNLOCKED_SKILLS, 1);
            WandProgression.migrate(pending);
            com.anton.elementalwands.ElementalWandsMod.handleAffinitySet(pending.getCommandSource(), WizardAffinity.STONE);
            require(WandProgression.flux(pending) == 90 && WandProgression.skills(pending) == 1, "Unaffiliated legacy progress was discarded");
            player = (ServerPlayerEntity)factory.invoke(null, server, UUID.randomUUID(), "HubTester", .5, 100.0, .5);
            player.setNoGravity(true);
            player.setAttached(EWAttachments.AFFINITY, "FIRE");
            player.setAttached(EWAttachments.ARCANE_FLUX, 500L);
            var wand = new ItemStack(ModItems.FRACTURED_WAND);
            player.setStackInHand(Hand.MAIN_HAND, wand);
            require(WandLoadouts.get(player).equals(WandSpells.defaults(WizardAffinity.FIRE)), "Existing players did not receive the default loadout");
            var defaults = WandLoadouts.get(player);
            WandLoadouts.equip(player, "FIRE", 0, "dragons_pyre");
            require(WandLoadouts.get(player).equals(defaults), "Locked spell equipped");
            WandLoadouts.cast(player, 1);
            require(!wand.contains(DataComponentTypes.CUSTOM_DATA), "Locked cast changed cooldowns");
            player.experienceLevel = 15;
            require(WandProgression.flux(player) == 500, "Legacy Flux was not migrated");
            require(com.anton.elementalwands.ElementalWandsMod.handleSkillUnlock(player.getCommandSource(), "secondary") == 1, "Existing unlock flow failed");
            require(WandProgression.flux(player) == 0 && player.experienceLevel == 15,
                    "Purchase must spend Flux and preserve vanilla XP");
            require(com.anton.elementalwands.ElementalWandsMod.handleSkillUnlock(player.getCommandSource(), "ultimate") == 0,
                    "Insufficient resources still unlocked ultimate");
            player.experienceLevel = 0;
            WandProgression.earn(player, WizardAffinity.FIRE, 1500);
            var hub = com.anton.elementalwands.network.ModNetworking.class.getDeclaredMethod("handleHub", ServerPlayerEntity.class,
                    com.anton.elementalwands.network.ModNetworking.HubActionPayload.class);
            hub.setAccessible(true);
            hub.invoke(null, player, new com.anton.elementalwands.network.ModNetworking.HubActionPayload("unlock", "SPACE", 0, "meteor"));
            require(WandProgression.flux(player) == 1500 && WandProgression.skills(player) == 1, "Stale purchase packet changed progress");
            hub.invoke(null, player, new com.anton.elementalwands.network.ModNetworking.HubActionPayload("unlock", "FIRE", 0, "meteor"));
            require(WandProgression.flux(player) == 0 && WandProgression.skills(player) == 3 && player.experienceLevel == 0,
                    "Store purchase failed with zero XP");
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, wand, n -> {
                n.putLong("ew_last_primary", 345); n.putLong("ew_last_secondary", 678);
                n.putInt(AbstractWandItem.NBT_ULTIMATE_CHARGE, 74);
            });
            require(!player.getAttachedOrElse(EWAttachments.WELCOME_SEEN, false), "New player guide already acknowledged");
            hub.invoke(null, player, new com.anton.elementalwands.network.ModNetworking.HubActionPayload("welcome_seen", "FIRE", 0, ""));
            require(player.getAttachedOrElse(EWAttachments.WELCOME_SEEN, false), "Guide acknowledgement was not saved");
            var oldGuide = new ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);
            oldGuide.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new net.minecraft.component.type.WrittenBookContentComponent(
                    net.minecraft.text.RawFilteredPair.of("The Wizard's Path"), "The Ancients", 0, List.of(), true));
            var userBook = oldGuide.copy();
            userBook.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new net.minecraft.component.type.WrittenBookContentComponent(
                    net.minecraft.text.RawFilteredPair.of("The Wizard's Path"), "HubTester", 0, List.of(), true));
            player.getInventory().setStack(20, oldGuide);
            player.getInventory().setStack(21, userBook);
            player.getInventory().setStack(22, new ItemStack(net.minecraft.item.Items.ENCHANTED_BOOK));
            WandGuide.removeLegacyBooks(player.getInventory());
            require(player.getInventory().getStack(20).isEmpty() && !player.getInventory().getStack(21).isEmpty()
                    && !player.getInventory().getStack(22).isEmpty(), "Guide cleanup removed non-mod books");
            var original = wand.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
            WandLoadouts.equip(player, "FIRE", 0, "dragons_pyre");
            require(WandLoadouts.get(player).equals(defaults), "Technique entered Basic slot");
            player.setAttached(EWAttachments.WAND_LOADOUTS, Map.of("FIRE", List.of("dragons_pyre", "inferno_wave", "meteor")));
            require(WandLoadouts.get(player).equals(defaults), "Legacy swapped loadout was not reordered");
            for (var element : WizardAffinity.values()) if (element != WizardAffinity.NONE) {
                var ids = WandSpells.defaults(element);
                for (int slot = 0; slot < 3; slot++) for (int other = 0; other < 3; other++) if (slot != other)
                    require(WandSpells.equip(element, ids, slot, ids.get(other)).equals(ids), "Category validation allowed a wrong slot");
            }
            require(wand.get(DataComponentTypes.CUSTOM_DATA).copyNbt().equals(original), "Equip reset cooldown or charge");
            var equipped = WandLoadouts.get(player);
            for (String id : List.of("meteor", "blink_rift", "missing")) WandLoadouts.equip(player, "FIRE", 0, id);
            WandLoadouts.equip(player, "SPACE", 1, "inferno_wave");
            WandLoadouts.equip(player, "FIRE", -1, "inferno_wave");
            WandLoadouts.equip(player, "FIRE", 3, "inferno_wave");
            require(WandLoadouts.get(player).equals(equipped), "Malformed/stale/cross-affinity equip changed loadout");
            var save = NbtWriteView.create(ErrorReporter.EMPTY, player.getRegistryManager()); player.writeData(save);
            player.setAttached(EWAttachments.WAND_LOADOUTS, Map.of());
            player.readData(NbtReadView.create(ErrorReporter.EMPTY, player.getRegistryManager(), save.getNbt()));
            require(WandLoadouts.get(player).equals(equipped), "Loadout failed player save/reload");
            require(player.getAttachedOrElse(EWAttachments.WELCOME_SEEN, false), "Guide acknowledgement lost on save/reload");
            WandProgression.earn(player, WizardAffinity.FIRE, 123);
            var fireRecord = WandProgression.get(player, WizardAffinity.FIRE);
            WandProgression.purchase(player, "FIRE", "dragons_pyre");
            WandProgression.purchase(player, "NATURE", "dragons_pyre");
            WandProgression.purchase(player, "FIRE", "blink_rift");
            WandProgression.purchase(player, "FIRE", "missing");
            require(fireRecord.equals(WandProgression.get(player, WizardAffinity.FIRE)), "Invalid/duplicate purchases charged Flux");
            require(com.anton.elementalwands.ElementalWandsMod.handleAffinitySet(player.getCommandSource(), WizardAffinity.NATURE) == 1, "Element switch failed");
            require(WandProgression.flux(player) == 0 && WandProgression.skills(player) == 0, "Nature inherited Fire currency or purchases");
            AbstractWandItem.onWandDamageDealt(player, 7, WizardAffinity.FIRE);
            require(WandProgression.flux(player) == 0 && WandProgression.get(player, WizardAffinity.FIRE).flux() == 130, "Lingering Fire damage credited Nature");
            AbstractWandItem.onWandDamageDealt(player, 15, WizardAffinity.NATURE);
            require(WandProgression.flux(player) == 15, "Nature damage did not earn Nature Flux");
            require(WandLoadouts.get(player).equals(WandSpells.defaults(WizardAffinity.NATURE)), "Other affinity inherited Fire choices");
            com.anton.elementalwands.ElementalWandsMod.handleAffinitySet(player.getCommandSource(), WizardAffinity.FIRE);
            require(WandProgression.flux(player) == 130 && WandProgression.skills(player) == 3, "Switch did not restore Fire progress");
            var progressSave = NbtWriteView.create(ErrorReporter.EMPTY, player.getRegistryManager()); player.writeData(progressSave);
            player.setAttached(EWAttachments.ELEMENT_PROGRESS, Map.of());
            player.readData(NbtReadView.create(ErrorReporter.EMPTY, player.getRegistryManager(), progressSave.getNbt()));
            require(WandProgression.flux(player) == 130 && WandProgression.get(player, WizardAffinity.NATURE).flux() == 15,
                    "Per-element balances failed save/reload");
            require(WandProgression.skills(player) == 3, "Permanent purchases failed save/reload");
            require(WandLoadouts.get(player).equals(equipped), "Fire loadout was lost on affinity change");
            wand = player.getMainHandStack();
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, wand, n -> { n.putLong("ew_last_primary", -10000); n.putLong("ew_last_global", -10000); });
            WandLoadouts.cast(player, 0); // Basic slot casts Inferno Wave.
            var castData = wand.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
            require(castData.getLong("ew_last_primary").orElseThrow() == player.getEntityWorld().getTime(), "Basic slot dispatched wrong spell");
            require(castData.getLong("ew_last_secondary").orElseThrow() == 678, "Basic cast touched technique cooldown");
            WandLoadouts.equip(player, "FIRE", 0, "inferno_wave");
            require(WandLoadouts.get(player).equals(equipped), "Combat lock bypassed");
            require(com.anton.elementalwands.ElementalWandsMod.handleAffinitySet(player.getCommandSource(), WizardAffinity.NATURE) == 0,
                    "Element switching bypassed combat lock");
            player.setSneaking(true);
            require(!player.shouldCancelInteraction(), "Sneak interaction with wand still bypasses chests");
            player.setSneaking(false);
            player.setHealth(0);
            var before = player.getMainHandStack().copy(); WandLoadouts.cast(player, 2);
            require(ItemStack.areEqual(before, player.getMainHandStack()), "Dead player could cast");
            player.setHealth(20);
            // Real Fabric copy-on-death attachment transfer is exercised by respawnPlayer.
            var id = player.getUuid();
            player = server.getPlayerManager().respawnPlayer(player, false, net.minecraft.entity.Entity.RemovalReason.KILLED);
            require(player.getUuid().equals(id) && WandLoadouts.get(player).equals(equipped), "Death/respawn lost saved loadout");
            require(WandProgression.flux(player) == 130 && WandProgression.skills(player) == 3
                    && WandProgression.get(player, WizardAffinity.NATURE).flux() == 15, "Respawn lost permanent progression");
            require(player.getAttachedOrElse(EWAttachments.WELCOME_SEEN, false), "Guide acknowledgement lost on respawn");
            player.setNoGravity(true);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FRACTURED_WAND));
        }
        if (tick == 235) {
            require(WandLoadouts.canEdit(player), "Combat lock did not expire");
            WandLoadouts.equip(player, "FIRE", 0, "inferno_wave");
            require(WandLoadouts.get(player).equals(WandSpells.defaults(WizardAffinity.FIRE)), "Post-combat category loadout changed");
            Files.writeString(Path.of("HUB_PASSED.txt"), "Legacy migration, element balances, permanent purchases, duplicate/stale purchase rejection, XP preservation, damage-source currency attribution, switch restrictions, unlock gates, invalid/stale payloads, cooldown/charge retention, category restrictions and Basic dispatch, player serialization, respawn persistence, interaction and combat lock checks passed.\n");
            server.stop(false);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
