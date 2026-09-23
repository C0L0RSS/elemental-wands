package com.anton.elementalwands.data;

import java.util.List;
import java.util.Locale;
import com.anton.elementalwands.item.AbstractWandItem.Ability;

/** Stable spell IDs are independent of the slot or physical input used to cast. */
public final class WandSpells {
    public enum Category {
        BASIC("Basic", 0), TECHNIQUE("Technique", 1), ULTIMATE("Ultimate", 2);
        private final String label; private final int slot;
        Category(String label, int slot) { this.label = label; this.slot = slot; }
        public String label() { return label; }
        public int slot() { return slot; }
        public static Category forSlot(int slot) { return values()[slot]; }
    }
    public record Spell(String id, WizardAffinity affinity, Ability ability, String name, String description, long price) {
        public Category category() { return switch (ability) {
            case PRIMARY -> Category.BASIC; case SECONDARY -> Category.TECHNIQUE; default -> Category.ULTIMATE;
        }; }
        public boolean ultimate() { return ability == Ability.ULTIMATE; }
        public String iconPath() { return "textures/gui/ability/" + affinity.name().toLowerCase(Locale.ROOT)
                + "_" + ability.name().toLowerCase(Locale.ROOT) + ".png"; }
        public String timing() {
            if (id.equals("updraft")) return "Cooldown: 8s";
            if (id.equals("gale_daggers")) return "Cooldown: 12s after firing";
            if (id.equals("faultline")) return "Cooldown: 9s";
            if (id.equals("stone_charge")) return "Hold to run / Recovery: 8s";
            if (id.equals("springbloom")) return "Cooldown: 10s";
            if (id.equals("thorn_lash")) return "Cooldown: 1s";
            if (id.equals("flamethrower")) return "Heat: 4s to overheat";
            if (id.equals("flashover")) return "Per bomb: blast 6s / lost 2s";
            if (id.equals("fire_hop")) return "Cooldown: 6s";
            if (ultimate()) return "Requires 100 charge";
            if (ability == Ability.PRIMARY) return switch (affinity) {
                case STONE -> "Recovery: 1.5s / 2.5s";
                case SPACE -> "Cooldown: " + com.anton.elementalwands.item.SpaceAbilityHandler.getPrimaryCooldownTicks()/20.0 + "s";
                case NATURE -> "Cooldown: " + com.anton.elementalwands.item.NatureAbilityHandler.getPrimaryCooldownTicks()/20.0 + "s";
                default -> "Cooldown: 1s";
            };
            return switch (affinity) {
                case FIRE -> "Cooldown: 10s"; case WIND -> "2 dashes / 5s recharge";
                case SPACE -> "Cooldown: " + com.anton.elementalwands.item.SpaceAbilityHandler.getSecondaryCooldownTicks()/20.0 + "s";
                case NATURE -> "Cooldown: " + com.anton.elementalwands.item.NatureAbilityHandler.getSecondaryCooldownTicks()/20.0 + "s";
                default -> "Cooldown: " + com.anton.elementalwands.item.AbstractWandItem.DEFAULT_SECONDARY_COOLDOWN_TICKS/20.0 + "s";
            };
        }
        public String reach() {
            return switch(id) {
                case "updraft" -> "Rise: ~10 blocks";
                case "gale_daggers" -> "40 blocks / 3 x 5 damage";
                case "sky_shear" -> "Range: 7 blocks";
                case "faultline" -> "Range: 20 / Launch + 0.6s interrupt";
                case "stone_charge" -> "Full speed: 2.5s / Max run: 5s";
                case "overgrowth" -> "Tree: 15s / with flower: 20s";
                case "springbloom" -> "4s pad / ~20 up, 15 forward";
                case "thorn_lash" -> "Range 4.5 / Max heal 1 heart";
                case "flamethrower" -> "Range: 6 blocks";
                case "flashover" -> "3 embers / blast: 4 blocks";
                case "fire_hop" -> "Range: 60 / wave: 5 blocks";
                default -> "";
            };
        }
        public boolean unlocked(int skills) {
            return ability == Ability.PRIMARY || (skills & (ultimate() ? EWAttachments.SKILL_ULTIMATE : EWAttachments.SKILL_SECONDARY)) != 0;
        }
    }
    private static Spell spell(WizardAffinity affinity, Ability ability, String id, String name, String description) {
        return new Spell(id, affinity, ability, name, description, ability == Ability.PRIMARY ? 0 : ability == Ability.ULTIMATE ? 1500 : 500);
    }
    public static final List<Spell> ALL = List.of(
        spell(WizardAffinity.NONE, Ability.PRIMARY, "fractured_beam", "Fractured Beam", "A simple arcane beam. Choose an affinity to awaken your wand."),
        spell(WizardAffinity.FIRE, Ability.PRIMARY, "inferno_wave", "Inferno Wave", "Send a stream of fire forward, burning enemies along its path."),
        spell(WizardAffinity.FIRE, Ability.SECONDARY, "dragons_pyre", "Dragon's Pyre", "Send a wall of fire across the ground. Flames linger behind it."),
        spell(WizardAffinity.FIRE, Ability.ULTIMATE, "meteor", "Meteor", "Call down a huge burning meteor to strike the battlefield."),
        new Spell("flamethrower", WizardAffinity.FIRE, Ability.PRIMARY, "Flamethrower",
                "Ignite foes with a gentle stream that grows stronger as you keep spraying. Release to cool.", 500),
        new Spell("fire_hop", WizardAffinity.FIRE, Ability.SECONDARY, "Fire Leap",
                "Hold to aim; release to leap. Land with a fire shockwave. Closer enemies take more damage.", 500),
        new Spell("flashover", WizardAffinity.FIRE, Ability.SECONDARY, "Flashover",
                "Stick bombs to foes or walls. Alternate: 0.15s blasts. Throw every 1s; expires in 30s.", 500),
        spell(WizardAffinity.WIND, Ability.PRIMARY, "sky_shear", "Sky Shear", "Release three cutting crescents in a narrow fan ahead of you."),
        spell(WizardAffinity.WIND, Ability.SECONDARY, "waylay_dash", "Waylay Dash", "Dash to reposition. Two charges recover individually over time."),
        spell(WizardAffinity.WIND, Ability.ULTIMATE, "zephyr_strike", "Zephyr Strike", "Take flight with temporary wings, then deliver a powerful landing impact."),
        new Spell("gale_daggers", WizardAffinity.WIND, Ability.SECONDARY, "Gale Daggers",
                "Ready 3 daggers. Press again or primary fire to burst. Aim each shot; recovery starts on firing.", 500),
        new Spell("updraft", WizardAffinity.WIND, Ability.SECONDARY, "Updraft",
                "Launch upward with normal air control. Cast on the ground or in midair; land safely.", 500),
        spell(WizardAffinity.STONE, Ability.PRIMARY, "gathered_mass", "Gathered Mass", "Aim down to gather stone. Aim forward to throw your reserve."),
        spell(WizardAffinity.STONE, Ability.SECONDARY, "stone_wall", "Stone Wall", "Raise cover. Cast again near your active wall to shatter it forward."),
        spell(WizardAffinity.STONE, Ability.ULTIMATE, "titan_dome", "Titan Dome", "Invoke the Titan Dome and its protective stone power."),
        new Spell("faultline", WizardAffinity.STONE, Ability.SECONDARY, "Faultline",
                "Send a fast wave of stone spikes forward. Launch enemies and briefly interrupt movement as the spikes crumble.", 500),
        new Spell("stone_charge", WizardAffinity.STONE, Ability.SECONDARY, "Juggernaut",
                "Hold to charge with heavy steering. Build speed for a crushing impact and a long running leap. Release to brake.", 500),
        spell(WizardAffinity.NATURE, Ability.PRIMARY, "seed", "Seed", "Launch a winged seed to strike an enemy or plant a growing flower."),
        new Spell("thorn_lash", WizardAffinity.NATURE, Ability.PRIMARY, "Thornbite",
                "Snap a flytrap forward at one foe. Heal 50% of health damage, up to one heart.", 500),
        spell(WizardAffinity.NATURE, Ability.SECONDARY, "tendril_bloom", "Tendril Bloom", "Send vines from flowers, or three from a root knot when none exist. Break a source to end its brambles."),
        spell(WizardAffinity.NATURE, Ability.SECONDARY, "springbloom", "Springbloom",
                "Throw a jump flower. Land on it to launch safely. Anyone can use or break it."),
        spell(WizardAffinity.NATURE, Ability.ULTIMATE, "overgrowth", "Overgrowth", "Throw an acorn to grow a healing oak. One nearby flower extends its duration."),
        spell(WizardAffinity.SPACE, Ability.PRIMARY, "singularity_bolt", "Singularity Bolt", "Launch a black star with subtle guidance and a damaging impact burst."),
        spell(WizardAffinity.SPACE, Ability.SECONDARY, "blink_rift", "Blink Rift", "Blink to a safe location, leaving a rift you can use to return."),
        spell(WizardAffinity.SPACE, Ability.ULTIMATE, "hollow_purple", "Hollow Purple", "Commit to a charged release of spatial energy. Keep aiming as its power gathers.")
    );
    public static Spell find(String id) { return ALL.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null); }
    public static List<Spell> forAffinity(WizardAffinity affinity) { return ALL.stream().filter(s -> s.affinity() == affinity).toList(); }
    public static List<String> defaults(WizardAffinity affinity) {
        if (affinity == WizardAffinity.NONE) return List.of("fractured_beam");
        var spells = forAffinity(affinity);
        var result = new java.util.ArrayList<String>();
        for (var category : Category.values()) spells.stream().filter(s -> s.category() == category).findFirst().ifPresent(s -> result.add(s.id()));
        return List.copyOf(result);
    }
    /** Five numbered slots. Any owned spell may sit in any slot; categories are store metadata only. */
    public static final int SLOT_COUNT=5;
    public static boolean fits(Spell spell,int slot) { return spell!=null && slot>=0 && slot<SLOT_COUNT; }
    public static String slotLabel(int slot) { return "Slot "+(slot+1); }
    public static boolean slotOpen(List<String> owned,int slot) { return slot>=0 && slot<SLOT_COUNT; }
    public static List<Integer> visibleSlots(List<String> owned) { return List.of(0,1,2,3,4); }
    public static boolean valid(WizardAffinity affinity,List<String> ids) {
        if(affinity==WizardAffinity.NONE) return ids.equals(defaults(affinity));
        if(ids.size()!=SLOT_COUNT) return false;
        var seen=new java.util.HashSet<String>();
        for(int i=0;i<ids.size();i++) {if(ids.get(i).isEmpty())continue;var spell=find(ids.get(i));
            if(spell==null||spell.affinity()!=affinity||!fits(spell,i)||!seen.add(spell.id()))return false;}
        return true;
    }
    /** Preserves old selections by index, with blank added slots. */
    public static List<String> normalize(WizardAffinity affinity,List<String> ids) {
        if(affinity==WizardAffinity.NONE)return defaults(affinity);
        var result=new java.util.ArrayList<>(java.util.Collections.nCopies(SLOT_COUNT,""));
        if(ids!=null) for(int i=0;i<ids.size();i++) {
            var spell=find(ids.get(i));if(spell==null||spell.affinity()!=affinity||result.contains(spell.id()))continue;
            int target=i<SLOT_COUNT?i:result.indexOf("");
            if(target>=0&&result.get(target).isEmpty())result.set(target,spell.id());
        }
        return List.copyOf(result);
    }
    public static List<String> reconcile(WizardAffinity affinity,List<String> current,List<String> owned) {
        if(affinity==WizardAffinity.NONE)return defaults(affinity);
        var result=new java.util.ArrayList<>(normalize(affinity,current));
        for(int i=0;i<SLOT_COUNT;i++)if(!slotOpen(owned,i)||!owned.contains(result.get(i)))result.set(i,"");
        // Newly owned spells fill the first empty slots so a purchase is usable at once.
        for(int i=0;i<SLOT_COUNT;i++)if(slotOpen(owned,i)&&result.get(i).isEmpty())
            for(String id:owned){var spell=find(id);if(spell!=null&&spell.affinity()==affinity&&fits(spell,i)&&!result.contains(id)){result.set(i,id);break;}}
        return List.copyOf(result);
    }
    public static List<String> equip(WizardAffinity affinity,List<String> current,int slot,String id) {
        var spell=find(id);
        if(!valid(affinity,current)||slot<0||slot>=SLOT_COUNT||spell==null||spell.affinity()!=affinity||!fits(spell,slot))return current;
        var next=new java.util.ArrayList<>(current);int oldSlot=next.indexOf(id);
        if(oldSlot>=0&&oldSlot!=slot){String displaced=next.get(slot);next.set(oldSlot,fits(find(displaced),oldSlot)?displaced:"");}
        next.set(slot,id);return valid(affinity,next)?List.copyOf(next):current;
    }
    private WandSpells() {}
}
