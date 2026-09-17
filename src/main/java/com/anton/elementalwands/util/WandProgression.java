package com.anton.elementalwands.util;

import java.util.*;
import com.anton.elementalwands.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-owned purchases. Legacy fields are read once and never used as live balances. */
public final class WandProgression {
    public static void migrate(PlayerEntity player) {
        if (player.getAttachedOrElse(EWAttachments.PROGRESSION_MIGRATED, false)) return;
        var affinity = EWAttachments.getAffinity(player);
        // A legacy unaffiliated player keeps pending progress until choosing an element.
        if (affinity == WizardAffinity.NONE) return;
        int skills = player.getAttachedOrElse(EWAttachments.UNLOCKED_SKILLS, 0);
        var owned = WandSpells.defaults(affinity).stream().map(WandSpells::find)
                .filter(s -> s.unlocked(skills)).map(WandSpells.Spell::id).toList();
        put(player, affinity, new ElementProgress(player.getAttachedOrElse(EWAttachments.ARCANE_FLUX, 0L), owned));
        player.setAttached(EWAttachments.PROGRESSION_MIGRATED, true);
    }
    public static ElementProgress get(PlayerEntity player, WizardAffinity affinity) {
        migrate(player);
        return player.getAttachedOrElse(EWAttachments.ELEMENT_PROGRESS, Map.of()).getOrDefault(affinity.name(), ElementProgress.EMPTY);
    }
    public static long flux(PlayerEntity player) { return get(player, EWAttachments.getAffinity(player)).flux(); }
    public static List<String> owned(PlayerEntity player) {
        var affinity = EWAttachments.getAffinity(player);
        var record = get(player, affinity);
        return WandSpells.forAffinity(affinity).stream().filter(s -> s.price() == 0 || record.spells().contains(s.id()))
                .map(WandSpells.Spell::id).toList();
    }
    public static boolean owns(PlayerEntity player, WandSpells.Spell spell) {
        return spell != null && (spell.price() == 0 || get(player, spell.affinity()).spells().contains(spell.id()));
    }
    /** Compatibility flags for current ability handlers; ownership itself uses spell IDs. */
    public static int skills(PlayerEntity player) {
        int bits = 0;
        for (var spell : WandSpells.forAffinity(EWAttachments.getAffinity(player))) if (owns(player, spell)) {
            if (spell.ultimate()) bits |= EWAttachments.SKILL_ULTIMATE;
            else if (spell.ability() == com.anton.elementalwands.item.AbstractWandItem.Ability.SECONDARY) bits |= EWAttachments.SKILL_SECONDARY;
        }
        return bits;
    }
    public static void earn(PlayerEntity player, WizardAffinity source, long amount) {
        if (source == WizardAffinity.NONE || amount <= 0) return;
        var old = get(player, source);
        put(player, source, new ElementProgress(old.flux() + Math.min(amount, Long.MAX_VALUE - old.flux()), old.spells(), old.xp()));
    }
    public static String purchase(ServerPlayerEntity player, String expectedAffinity, String id) {
        var affinity = EWAttachments.getAffinity(player);
        if (!affinity.name().equals(expectedAffinity)) return "Your element changed. Select the spell again.";
        if (!WandLoadouts.canEdit(player)) return "Buy spells outside combat.";
        var spell = WandSpells.find(id);
        if (spell == null || spell.affinity() != affinity || affinity == WizardAffinity.NONE) return "This spell belongs to another element.";
        if (owns(player, spell)) return "You already own this spell.";
        var old = get(player, affinity);
        int freeTier = SpellBooks.availableTier(player, spell);
        if (freeTier < 0 && old.flux() < spell.price()) return "Not enough " + affinity.name().toLowerCase(Locale.ROOT) + " Flux.";
        var ids = new ArrayList<>(old.spells()); ids.add(id);
        put(player, affinity, new ElementProgress(old.flux() - (freeTier>=0 ? 0 : spell.price()), ids, old.xp()));
        if (freeTier>=0) SpellBooks.spend(player, freeTier);
        WandLoadouts.get(player); // Fill newly available capacity without replacing chosen spells.
        return (freeTier>=0 ? "Learned for free: " : "Purchased ") + spell.name() + ".";
    }
    public static boolean adminMatches(WandSpells.Spell spell, int bits) {
        return spell.ability()==com.anton.elementalwands.item.AbstractWandItem.Ability.PRIMARY ? bits==3 : spell.unlocked(bits);
    }
    public static void grant(PlayerEntity player, int bits) {
        var affinity = EWAttachments.getAffinity(player);
        if (affinity == WizardAffinity.NONE) return;
        var old = get(player, affinity);
        var ids = new HashSet<>(old.spells());
        WandSpells.forAffinity(affinity).stream().filter(s -> adminMatches(s,bits)).forEach(s -> ids.add(s.id()));
        put(player, affinity, new ElementProgress(old.flux(), ids.stream().sorted().toList(), old.xp()));
    }
    public static void experience(ServerPlayerEntity player, WizardAffinity source, double amount) {
        if (source==WizardAffinity.NONE || !Double.isFinite(amount) || amount<=0) return;
        var old=get(player,source); int before=ElementLevels.level(old.xp());
        var next=new ElementProgress(old.flux(),old.spells(),old.xp()+amount);
        if(next.xp()==old.xp()) return;
        put(player,source,next);
        if(ElementLevels.level(next.xp())>before) player.sendMessage(net.minecraft.text.Text.literal(
                source.name()+" reached level "+ElementLevels.level(next.xp())+"! Damage +"+((ElementLevels.level(next.xp())-1)*10)+"%"),false);
        com.anton.elementalwands.network.ModNetworking.syncPlayerData(player);
    }
    private static void put(PlayerEntity player, WizardAffinity affinity, ElementProgress progress) {
        var saved = new HashMap<>(player.getAttachedOrElse(EWAttachments.ELEMENT_PROGRESS, Map.of()));
        saved.put(affinity.name(), progress);
        player.setAttached(EWAttachments.ELEMENT_PROGRESS, Map.copyOf(saved));
    }
    private WandProgression() {}
}
